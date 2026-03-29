package io.autocrypt.jwlee.cowork.core.tools;

import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

@Service
@Primary
public class ConfluenceServiceImpl implements ConfluenceService {

    private final RestTemplate restTemplate;
    private final String baseUrl;
    private final String email;
    private final String apiToken;
    private final String spaceKey;

    public ConfluenceServiceImpl(RestTemplate restTemplate,
                                 @Value("${app.confluence.baseUrl:https://auto-jira.atlassian.net/wiki}") String baseUrl,
                                 @Value("${app.confluence.email:jwlee@autocrypt.io}") String email,
                                 @Value("${app.confluence.apiToken:}") String apiToken,
                                 @Value("${app.confluence.space-key:camlab}") String spaceKey) {
        this.restTemplate = restTemplate;
        this.baseUrl = baseUrl;
        this.email = email;
        this.apiToken = apiToken;
        this.spaceKey = spaceKey;
    }

    private HttpHeaders createAuthHeaders() {
        String auth = email + ":" + apiToken;
        byte[] encodedAuth = Base64.getEncoder().encode(auth.getBytes());
        String authHeader = "Basic " + new String(encodedAuth);

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", authHeader);
        return headers;
    }

    /**
     * RAG 성능 향상 및 토큰 절약을 위해 Confluence Storage XML을 경량화된 HTML로 정제합니다.
     */
    private String cleanHtmlForLlm(String storageHtml) {
        if (storageHtml == null || storageHtml.isBlank()) {
            return "";
        }

        Safelist safelist = Safelist.none()
                .addTags(
                        "h1", "h2", "h3", "h4", "h5", "h6",
                        "p", "br", "hr",
                        "b", "i", "strong", "em", "code", "pre",
                        "ul", "ol", "li",
                        "table", "thead", "tbody", "tr", "th", "td"
                );

        String cleaned = Jsoup.clean(storageHtml, safelist);
        return "<confluence>\n" + cleaned + "\n</confluence>";
    }

    @SuppressWarnings("unchecked")
    private List<ConfluencePageInfo> searchPages(String cql, int limit) {
        URI uri = UriComponentsBuilder.fromHttpUrl(baseUrl + "/rest/api/content/search")
                .queryParam("cql", cql)
                .queryParam("limit", limit)
                .build()
                .toUri();

        HttpEntity<String> entity = new HttpEntity<>(createAuthHeaders());
        List<ConfluencePageInfo> resultPages = new ArrayList<>();

        try {
            ResponseEntity<Map> response = restTemplate.exchange(uri, HttpMethod.GET, entity, Map.class);
            Map<String, Object> responseBody = response.getBody();
            if (responseBody == null) return List.of();

            List<Map<String, Object>> results = (List<Map<String, Object>>) responseBody.get("results");
            if (results == null || results.isEmpty()) return List.of();

            for (Map<String, Object> r : results) {
                String pageId = (String) r.get("id");
                String title = (String) r.get("title");
                String cleanedContent = getPageStorage(pageId);
                if (cleanedContent != null && !cleanedContent.isEmpty()) {
                    resultPages.add(new ConfluencePageInfo(pageId, title, cleanedContent));
                }
            }
            return resultPages;
        } catch (Exception e) {
            e.printStackTrace();
            return List.of();
        }
    }

    @Override
    public ConfluencePageInfo getCurrentOkr() {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));
        int year = today.getYear();
        int month = today.getMonthValue();
        int quarter = (month - 1) / 3 + 1;
        
        String targetQuarterStr = String.format("[%d-%dQ]", year, quarter);
        String cql = "title ~ \"\\\"" + targetQuarterStr + "\\\"\" AND title ~ \"OKR\" AND title !~ \"회고\" AND type = page AND space = \"" + spaceKey + "\"";
        
        List<ConfluencePageInfo> results = searchPages(cql, 1);
        return results.isEmpty() ? new ConfluencePageInfo("", "", "") : results.get(0);
    }

    @Override
    public ConfluencePageInfo getCurrentWeeklyReport() {
        String cql = "title ~ \"\\\"주간 팀장회의록\\\"\" AND type = page AND space = \"" + spaceKey + "\" order by created desc";
        List<ConfluencePageInfo> results = searchPages(cql, 1);
        return results.isEmpty() ? new ConfluencePageInfo("", "", "") : results.get(0);
    }

    @Override
    @SuppressWarnings("unchecked")
    public String getPageStorage(String pageId) {
        if (pageId == null || pageId.isBlank()) return "";
        
        String url = baseUrl + "/api/v2/pages/" + pageId + "?body-format=storage";
        HttpEntity<String> entity = new HttpEntity<>(createAuthHeaders());

        try {
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);
            Map<String, Object> responseBody = response.getBody();
            if (responseBody == null) return "";

            Map<String, Object> body = (Map<String, Object>) responseBody.get("body");
            Map<String, Object> storage = (Map<String, Object>) body.get("storage");
            String rawHtml = (String) storage.get("value");
            
            return cleanHtmlForLlm(rawHtml);
        } catch (Exception e) {
            return "";
        }
    }

    @Override
    public List<ConfluencePageInfo> searchForRag(RagSearchRequest request) {
        if (request == null || request.keyword() == null || request.keyword().isBlank()) {
            return List.of();
        }

        int limit = request.limit() > 0 ? Math.min(request.limit(), 5) : 3;

        StringBuilder cqlBuilder = new StringBuilder();
        cqlBuilder.append("type = page AND space = \"").append(spaceKey).append("\"");
        
        if (request.ancestorId() != null && !request.ancestorId().isBlank()) {
            cqlBuilder.append(" AND ancestor = ").append(request.ancestorId());
        }

        cqlBuilder.append(" AND text ~ \"\\\"").append(request.keyword()).append("\\\"\"");

        if (request.excludeKeyword() != null && !request.excludeKeyword().isBlank()) {
            cqlBuilder.append(" AND text !~ \"\\\"").append(request.excludeKeyword()).append("\\\"\"");
        }

        if (request.fromDate() != null && !request.fromDate().isBlank()) {
            cqlBuilder.append(" AND lastModified >= \"").append(request.fromDate()).append("\"");
        }

        cqlBuilder.append(" order by lastModified desc");

        return searchPages(cqlBuilder.toString(), limit);
    }
}
