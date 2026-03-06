package io.autocrypt.jwlee.cowork.weeklyreport.service;

import io.autocrypt.jwlee.cowork.weeklyreport.dto.MeetingInfo;
import io.autocrypt.jwlee.cowork.weeklyreport.dto.TeamReportInfo;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

public class DataExtractionTest {
    private RealConfluenceService confluenceService;
    private RestTemplate restTemplate;

    @BeforeEach
    void setUp() {
        restTemplate = new RestTemplateBuilder().build();
        confluenceService = new RealConfluenceService(restTemplate);
        
        String apiToken = System.getenv("CONFLUENCE_API_TOKEN");
        ReflectionTestUtils.setField(confluenceService, "baseUrl", "https://auto-jira.atlassian.net/wiki");
        ReflectionTestUtils.setField(confluenceService, "email", "jwlee@autocrypt.io");
        ReflectionTestUtils.setField(confluenceService, "apiToken", apiToken);
        ReflectionTestUtils.setField(confluenceService, "okrPageId", "2781544496");
        ReflectionTestUtils.setField(confluenceService, "meetingRootId", "1778647765");
    }

    @Test
    void testJsoupExtraction() {
        List<MeetingInfo> meetings = confluenceService.getRecentMeetingUrls();
        if (meetings.isEmpty()) return;
        String meetingId = meetings.get(0).id();
        
        String url = "https://auto-jira.atlassian.net/wiki/api/v2/pages/" + meetingId + "?body-format=storage";
        
        String auth = "jwlee@autocrypt.io:" + System.getenv("CONFLUENCE_API_TOKEN");
        byte[] encodedAuth = Base64.getEncoder().encode(auth.getBytes());
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Basic " + new String(encodedAuth));
        HttpEntity<String> entity = new HttpEntity<>(headers);
        
        ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);
        Map<String, Object> body = (Map<String, Object>) response.getBody().get("body");
        String htmlContent = (String) ((Map<String, Object>) body.get("storage")).get("value");
        
        // Jsoup Parsing Logic
        Document doc = Jsoup.parseBodyFragment(htmlContent);
        List<TeamReportInfo> reports = new ArrayList<>();
        
        Elements h4Elements = doc.select("h4");
        for (Element h4 : h4Elements) {
            String teamName = h4.text().trim();
            // 팀 섹션인 경우
            if (teamName.endsWith("팀")) {
                // 부모 ac:layout-section을 찾음
                Element parentSection = h4.parent();
                while (parentSection != null && !parentSection.tagName().equals("ac:layout-section")) {
                    parentSection = parentSection.parent();
                }
                
                if (parentSection != null) {
                    // 다음 형제 ac:layout-section이 내용임
                    Element contentSection = parentSection.nextElementSibling();
                    if (contentSection != null && contentSection.tagName().equals("ac:layout-section")) {
                        String contentText = contentSection.text(); // 줄바꿈을 유지하려면 text()보다 낫지만 일단 확인용
                        
                        // 좀 더 예쁘게 포맷팅 (ul/li 구조 유지)
                        StringBuilder formattedContent = new StringBuilder();
                        for (Element el : contentSection.select("li, p")) {
                            if (el.tagName().equals("li")) {
                                formattedContent.append("- ").append(el.ownText()).append("\n");
                            } else {
                                formattedContent.append(el.text()).append("\n");
                            }
                        }
                        
                        reports.add(new TeamReportInfo(teamName, formattedContent.toString()));
                    }
                }
            }
        }
        
        System.out.println("=== 추출된 팀 개수: " + reports.size() + " ===");
        for (TeamReportInfo report : reports) {
            System.out.println("팀명: " + report.teamName());
            System.out.println("내용:\n" + report.content());
            System.out.println("---------------------------------");
        }
    }
}