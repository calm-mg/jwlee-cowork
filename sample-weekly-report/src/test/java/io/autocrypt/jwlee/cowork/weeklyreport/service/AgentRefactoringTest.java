package io.autocrypt.jwlee.cowork.weeklyreport.service;

import io.autocrypt.jwlee.cowork.weeklyreport.dto.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

@SpringBootTest
public class AgentRefactoringTest {

    @Autowired
    private ConfluenceService confluenceService;

    @Autowired
    private JiraExcelService jiraService;

    @Test
    void testPureJavaExtraction() {
        // 1. 실데이터 로드
        OkrInfo okr = confluenceService.getOkr();
        List<MeetingInfo> meetings = confluenceService.getRecentMeetingUrls();
        if (meetings.isEmpty()) return;
        
        List<TeamReportInfo> teamReports = confluenceService.getTeamReports(meetings.get(0).id());
        List<JiraIssueInfo> jiraIssues = jiraService.readIssues();

        System.out.println("\n=== PURE JAVA EXTRACTION RESULT (NO AI) ===");
        List<String> targetTeams = List.of("EE팀", "BE팀", "PKI팀", "PnC팀", "FE팀", "Engineering팀");

        for (String team : targetTeams) {
            // 2. Jira 기계적 분류
            String jiraComponentName = team.equals("Engineering팀") ? "Eng" : team.replace("팀", "");
            long jiraCount = jiraIssues.stream()
                    .filter(i -> i.component().equalsIgnoreCase(jiraComponentName) || i.component().equalsIgnoreCase(team))
                    .count();

            // 3. 회의록 기계적 분류 (이미 Service에서 Jsoup 파싱됨)
            String teamMeetingContent = teamReports.stream()
                    .filter(r -> r.teamName().equalsIgnoreCase(team))
                    .map(TeamReportInfo::content)
                    .findFirst()
                    .orElse("N/A");

            System.out.println("--------------------------------------------------");
            System.out.println("팀명: " + team);
            System.out.println("[Meeting Content]\n" + teamMeetingContent);
            System.out.println("[Jira Issue Count] " + jiraCount);
        }
        System.out.println("--------------------------------------------------");
    }
}
