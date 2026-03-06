package io.autocrypt.jwlee.cowork.weeklyreport.service;

import io.autocrypt.jwlee.cowork.weeklyreport.dto.MeetingInfo;
import io.autocrypt.jwlee.cowork.weeklyreport.dto.OkrInfo;
import io.autocrypt.jwlee.cowork.weeklyreport.dto.TeamReportInfo;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.stream.Collectors;

@Service
@Primary
public class RealConfluenceService implements ConfluenceService {

    private final RestTemplate restTemplate;

    @Value("${app.confluence.baseUrl}")
    private String baseUrl;

    @Value("${app.confluence.email}")
    private String email;

    @Value("${app.confluence.apiToken}")
    private String apiToken;

    @Value("${app.confluence.okr-page-id}")
    private String okrPageId;

    @Value("${app.confluence.meeting-root-id}")
    private String meetingRootId;

    public RealConfluenceService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public String getOkrPageId() {
        return okrPageId;
    }

    private HttpHeaders createAuthHeaders() {
        String auth = email + ":" + apiToken;
        byte[] encodedAuth = Base64.getEncoder().encode(auth.getBytes());
        String authHeader = "Basic " + new String(encodedAuth);

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", authHeader);
        return headers;
    }

    @Override
    @SuppressWarnings("unchecked")
    public OkrInfo getOkr() {
        if (okrPageId == null || okrPageId.isBlank()) {
            return new OkrInfo("N/A", List.of("OKR Page ID is not configured."), "N/A");
        }
        
        String url = baseUrl + "/api/v2/pages/" + okrPageId + "?body-format=storage";
        HttpEntity<String> entity = new HttpEntity<>(createAuthHeaders());
        
        try {
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);
            Map<String, Object> responseBody = response.getBody();
            if (responseBody == null) return new OkrInfo("N/A", List.of(), "Empty Response");
            
            String title = (String) responseBody.get("title");
            Map<String, Object> body = (Map<String, Object>) responseBody.get("body");
            String htmlContent = (String) ((Map<String, Object>) body.get("storage")).get("value");
            
            Document doc = Jsoup.parse(htmlContent);
            List<String> contents = new ArrayList<>();
            
            // 1. 목록 추출 (중복 제거를 위해 직접 자식만 탐색하거나 텍스트 정제)
            doc.select("li").forEach(li -> {
                // li 내부의 중복된 태그(p, span 등)를 무시하고 실제 텍스트만 추출
                String text = li.text().trim();
                if (!text.isEmpty() && !contents.contains(text)) {
                    contents.add(text);
                }
            });
            
            // 2. 테이블 데이터 정제
            Elements tables = doc.select("table");
            for (Element table : tables) {
                Elements rows = table.select("tr");
                for (Element row : rows) {
                    String rowText = row.select("th, td").stream()
                            .map(Element::text)
                            .map(String::trim)
                            .filter(t -> !t.isEmpty())
                            .collect(Collectors.joining(" | "));
                    if (!rowText.isEmpty() && !contents.contains(rowText)) {
                        contents.add(rowText);
                    }
                }
            }
            
            return new OkrInfo("Current Quarter", contents, title);
        } catch (Exception e) {
            return new OkrInfo("Error", List.of("Failed to fetch OKR: " + e.getMessage()), "Error");
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<TeamReportInfo> getTeamReports(String meetingPageId) {
        String url = baseUrl + "/api/v2/pages/" + meetingPageId + "?body-format=storage";
        HttpEntity<String> entity = new HttpEntity<>(createAuthHeaders());

        try {
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);
            Map<String, Object> body = (Map<String, Object>) response.getBody().get("body");
            String htmlContent = (String) ((Map<String, Object>) body.get("storage")).get("value");

            Document doc = Jsoup.parseBodyFragment(htmlContent);
            List<TeamReportInfo> reports = new ArrayList<>();

            Elements h4Elements = doc.select("h4");
            for (Element h4 : h4Elements) {
                String teamName = h4.text().trim();
                if (teamName.endsWith("팀")) {
                    Element parentSection = h4.parent();
                    while (parentSection != null && !parentSection.tagName().equalsIgnoreCase("ac:layout-section")) {
                        parentSection = parentSection.parent();
                    }

                    if (parentSection != null) {
                        Element contentSection = parentSection.nextElementSibling();
                        if (contentSection != null && contentSection.tagName().equalsIgnoreCase("ac:layout-section")) {
                            StringBuilder formattedContent = new StringBuilder();
                            
                            // li만 추출하여 중복 방지 (p 태그는 li의 텍스트와 중복되는 경우가 많음)
                            Elements lis = contentSection.select("li");
                            if (lis.isEmpty()) {
                                // 리스트가 없는 경우에만 p 태그 사용
                                for (Element p : contentSection.select("p")) {
                                    String text = p.text().trim();
                                    if (!text.isEmpty()) formattedContent.append(text).append("\n");
                                }
                            } else {
                                for (Element li : lis) {
                                    String text = li.text().trim();
                                    if (!text.isEmpty()) formattedContent.append("- ").append(text).append("\n");
                                }
                            }
                            reports.add(new TeamReportInfo(teamName, formattedContent.toString()));
                        }
                    }
                }
            }

            // 만약 파싱된 팀이 하나도 없다면 (구조가 다를 경우) 폴백으로 전체 텍스트 반환
            if (reports.isEmpty()) {
                reports.add(new TeamReportInfo("ALL_TEAMS", doc.body().text()));
            }

            return reports;
        } catch (Exception e) {
            return List.of(new TeamReportInfo("Error", "Failed to fetch meeting: " + e.getMessage()));
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
            Map<String, Object> body = (Map<String, Object>) response.getBody().get("body");
            return (String) ((Map<String, Object>) body.get("storage")).get("value");
        } catch (Exception e) {
            return "Error fetching page " + pageId + ": " + e.getMessage();
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<MeetingInfo> getRecentMeetingUrls() {
        if (meetingRootId == null || meetingRootId.isBlank()) {
            return List.of();
        }
        
        String url = baseUrl + "/api/v2/pages/" + meetingRootId + "/children";
        HttpEntity<String> entity = new HttpEntity<>(createAuthHeaders());

        try {
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);
            Map<String, Object> responseBody = response.getBody();
            if (responseBody == null) return List.of();
            
            List<Map<String, Object>> results = (List<Map<String, Object>>) responseBody.get("results");
            if (results == null) return List.of();
            
            List<MeetingInfo> meetings = results.stream()
                    .map(m -> new MeetingInfo((String) m.get("id"), (String) m.get("title")))
                    .collect(Collectors.toList());
            java.util.Collections.reverse(meetings);
            return meetings;
        } catch (Exception e) {
            System.err.println("Failed to fetch child pages from parent ID " + meetingRootId + ": " + e.getMessage());
            return List.of();
        }
    }
}
