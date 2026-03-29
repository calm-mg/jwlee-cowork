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

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

@Service
@Primary
public class RealConfluenceService implements ConfluenceService {

    private final RestTemplate restTemplate;
    private final String baseUrl;
    private final String email;
    private final String apiToken;
    private final String spaceKey;

    public RealConfluenceService(RestTemplate restTemplate,
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
     * LLM이 구조를 파악하는 데 필요한 핵심 태그(표, 리스트, 문단, 제목 등)만 남기고,
     * 불필요한 컨플루언스 매크로 껍데기나 스타일/ID 속성은 모두 제거하되, 내부 텍스트는 보존합니다.
     */
    private String cleanHtmlForLlm(String storageHtml) {
        if (storageHtml == null || storageHtml.isBlank()) {
            return "";
        }

        // LLM에게 필요한 핵심 시맨틱 태그만 허용 (모든 속성 제거)
        Safelist safelist = Safelist.none()
                .addTags(
                        "h1", "h2", "h3", "h4", "h5", "h6",
                        "p", "br", "hr",
                        "b", "i", "strong", "em", "code", "pre",
                        "ul", "ol", "li",
                        "table", "thead", "tbody", "tr", "th", "td"
                );

        // Jsoup.clean은 허용되지 않은 태그의 껍데기를 벗기고 내부 텍스트를 위로 올리며,
        // 허용된 태그라 하더라도 안전 목록에 지정되지 않은 속성(class, style, id 등)은 모두 지웁니다.
        String cleaned = Jsoup.clean(storageHtml, safelist);

        // LLM이 하나의 문서 단위로 인식하도록 명시적인 루트 태그로 감싸서 반환
        return "<confluence>\n" + cleaned + "\n</confluence>";
    }

    @Override
    @SuppressWarnings("unchecked")
    public ConfluencePageInfo getCurrentOkr() {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));
        int year = today.getYear();
        int month = today.getMonthValue();
        int quarter = (month - 1) / 3 + 1;
        
        String targetQuarterStr = String.format("[%d-%dQ]", year, quarter);

        // CQL을 사용하여 제목에 [2026-1Q]가 포함되고 "OKR"이 포함되며 "회고"가 아닌 페이지를 검색 (지정된 스페이스 한정)
        String cql = "title ~ \"\\\"" + targetQuarterStr + "\\\"\" AND title ~ \"OKR\" AND title !~ \"회고\" AND type = page AND space = \"" + spaceKey + "\"";
        String url = baseUrl + "/rest/api/content/search?cql=" + cql + "&limit=1";
        HttpEntity<String> entity = new HttpEntity<>(createAuthHeaders());

        try {
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);
            Map<String, Object> responseBody = response.getBody();
            if (responseBody == null) return new ConfluencePageInfo("", "", "");

            List<Map<String, Object>> results = (List<Map<String, Object>>) responseBody.get("results");
            if (results == null || results.isEmpty()) return new ConfluencePageInfo("", "", "");

            // 첫 번째 검색 결과의 페이지 ID 및 제목 추출
            String pageId = (String) results.get(0).get("id");
            String title = (String) results.get(0).get("title");
            
            // 찾은 페이지의 본문을 정제하여 반환
            return new ConfluencePageInfo(pageId, title, getPageStorage(pageId));
        } catch (Exception e) {
            e.printStackTrace();
            return new ConfluencePageInfo("", "", "");
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public ConfluencePageInfo getCurrentWeeklyReport() {
        // "주간 팀장회의록"이라는 제목이 포함된 문서 중, 제목 내림차순(최근 날짜순) 또는 생성일 내림차순 정렬 (지정된 스페이스 한정)
        // 보통 'created desc'를 사용하여 최신에 만들어진 회의록을 우선으로 가져옵니다.
        String cql = "title ~ \"\\\"주간 팀장회의록\\\"\" AND type = page AND space = \"" + spaceKey + "\" order by created desc";
        String url = baseUrl + "/rest/api/content/search?cql=" + cql + "&limit=1";
        HttpEntity<String> entity = new HttpEntity<>(createAuthHeaders());

        try {
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);
            Map<String, Object> responseBody = response.getBody();
            if (responseBody == null) return new ConfluencePageInfo("", "", "");

            List<Map<String, Object>> results = (List<Map<String, Object>>) responseBody.get("results");
            if (results == null || results.isEmpty()) return new ConfluencePageInfo("", "", "");

            // 첫 번째 검색 결과(가장 최근 회의록)의 페이지 ID 및 제목 추출
            String pageId = (String) results.get(0).get("id");
            String title = (String) results.get(0).get("title");

            // 찾은 페이지의 본문을 정제하여 반환
            return new ConfluencePageInfo(pageId, title, getPageStorage(pageId));
        } catch (Exception e) {
            e.printStackTrace();
            return new ConfluencePageInfo("", "", "");
        }
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
            
            // LLM용으로 HTML을 경량화하여 반환
            return cleanHtmlForLlm(rawHtml);
        } catch (Exception e) {
            return "";
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<ConfluencePageInfo> searchForRag(RagSearchRequest request) {
        if (request == null || request.keyword() == null || request.keyword().isBlank()) {
            return List.of();
        }

        int limit = request.limit() > 0 ? request.limit() : 3;
        if (limit > 5) limit = 5; // LLM 토큰 보호를 위해 최대 5개로 강제

        // 1. 기본 검색 조건
        StringBuilder cqlBuilder = new StringBuilder();
        cqlBuilder.append("type = page AND space = \"").append(spaceKey).append("\"");
        
        // 특정 페이지의 하위 페이지만 검색할 경우 (ancestor 필터)
        if (request.ancestorId() != null && !request.ancestorId().isBlank()) {
            cqlBuilder.append(" AND ancestor = ").append(request.ancestorId());
        }

        cqlBuilder.append(" AND text ~ \"\\\"").append(request.keyword()).append("\\\"\"");

        // 2. 제외 키워드 (선택)
        if (request.excludeKeyword() != null && !request.excludeKeyword().isBlank()) {
            cqlBuilder.append(" AND text !~ \"\\\"").append(request.excludeKeyword()).append("\\\"\"");
        }

        // 3. 날짜 필터 (선택)
        if (request.fromDate() != null && !request.fromDate().isBlank()) {
            cqlBuilder.append(" AND lastModified >= \"").append(request.fromDate()).append("\"");
        }

        // 최신순 정렬
        cqlBuilder.append(" order by lastModified desc");

        // 4. URI 객체 사용 (이중 인코딩 방지)
        java.net.URI uri = org.springframework.web.util.UriComponentsBuilder.fromHttpUrl(baseUrl + "/rest/api/content/search")
                .queryParam("cql", cqlBuilder.toString())
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
                
                // 찾은 페이지의 본문을 정제하여 리스트에 추가 (개별 페이지마다 cleanHtmlForLlm이 호출됨)
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
}
