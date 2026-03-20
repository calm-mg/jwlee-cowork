package io.autocrypt.jwlee.cowork.agents.weekly;

import com.embabel.agent.api.annotation.*;
import com.embabel.agent.api.common.ActionContext;
import com.embabel.agent.api.common.Ai;
import com.embabel.agent.core.hitl.WaitFor;
import com.embabel.agent.prompt.persona.RoleGoalBackstory;
import io.autocrypt.jwlee.cowork.agents.weekly.dto.*;
import io.autocrypt.jwlee.cowork.core.hitl.ApprovalDecision;
import io.autocrypt.jwlee.cowork.core.hitl.ApprovalRequestedEvent;
import io.autocrypt.jwlee.cowork.core.hitl.ApplicationContextHolder;
import io.autocrypt.jwlee.cowork.core.tools.CoreFileTools;
import jakarta.validation.constraints.Size;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

@Agent(description = "주간보고서 생성 및 검토 에이전트")
@Component
public class WeeklyReportAgent {

    private final RoleGoalBackstory analystPersona;
    private final RoleGoalBackstory collectorPersona;
    private final CoreFileTools fileTools;

    public WeeklyReportAgent(CoreFileTools fileTools) {
        this.fileTools = fileTools;
        this.analystPersona = new RoleGoalBackstory(
            "Research Director / Director",
            "Draft a comprehensive weekly report by analyzing OKRs, team reports, and Jira issues.",
            "You manage a 30-person SW development team. You are meticulous, strategic, and focused on OKR alignment. You provide deep insights rather than superficial summaries."
        );
        this.collectorPersona = new RoleGoalBackstory(
            "Administrative Assistant",
            "Accurately extract and summarize relevant data for specific teams.",
            "You are a meticulous administrative assistant. Your job is to accurately extract and summarize team data without adding any personal judgment or evaluation. You strictly follow instructions."
        );
    }

    private static final FinalWeeklyReport REPORT_EXAMPLE = new FinalWeeklyReport(
        """
        ### 개발그룹
        #### 사업 지원
        - **[하만/볼보 트럭 (V2X-EE)]** LCM 유럽향 CMI 모듈 마이그레이션 : ~02/27 (20%)
        - **[이동의자유 (BE, FE)]** 기사앱 통합 테스트 및 CD 구성 : ~03/13 (70%)
        #### 내부 개발
        - **[PKI-Vehicles (PKI)]** KMS 연동 및 성능 테스트 : ~03/06 (30%)
        ### 엔지니어링팀
        - **[LX공사 (Eng)]** OEM PKI 및 키 주입 시스템 SaaS 제공 : ~2028/11/30 (3년)
        """,
        "- N/A"
    );

    private static final String TEAM_RNR_INFO = """
        # 팀별 역할 및 책임범위 (RnR)
        - EE팀: V2X 단말기 보안 계층 라이브러리 개발, 보안 표준 준수
        - BE팀: 서비스 백엔드 설계·개발·운영, 인프라 및 CI/CD
        - PKI팀: X.509, IEEE1609.2.1 표준 PKI 솔루션 개발
        - PnC팀: V2G PnC 서비스 및 현대오토에버 PnC 유지보수
        - FE팀: 모바일/웹 앱 및 관리자 시스템 개발, UI/UX
        - Eng팀: 고객사 기술지원, 장애 대응, VOC 원인 분석
        """;

    @State
    public interface Stage {}

    public record TeamOpinions(
        String eeTeamOpinion,
        String beTeamOpinion,
        String pkiTeamOpinion,
        String pncTeamOpinion,
        String feTeamOpinion,
        String engTeamOpinion
    ) {
        public String getOpinion(String teamName) {
            return switch (teamName) {
                case "EE팀" -> eeTeamOpinion;
                case "BE팀" -> beTeamOpinion;
                case "PKI팀" -> pkiTeamOpinion;
                case "PnC팀" -> pncTeamOpinion;
                case "FE팀" -> feTeamOpinion;
                case "Eng팀" -> engTeamOpinion;
                default -> "분석 의견을 생성하지 못했습니다.";
            };
        }
    }

    @Action
    public AnalyzeTeamsState start(RawWeeklyData rawData, JiraIssueList jiraIssueList, Ai ai, ActionContext ctx) {
        List<String> targetTeams = List.of("EE팀", "BE팀", "PKI팀", "PnC팀", "FE팀", "Eng팀");
        
        List<TeamAnalysis> collectedData = targetTeams.parallelStream().map(team -> {
            String teamKey = team.replace("팀", "");
            var teamIssuesList = jiraIssueList.issues().stream()
                    .filter(i -> {
                        String comp = i.component().toUpperCase();
                        return comp.contains(teamKey.toUpperCase()) || comp.contains(team.toUpperCase()) || (teamKey.equals("Engineering") && comp.contains("ENG"));
                    })
                    .filter(i -> !"To Do".equalsIgnoreCase(i.status()))
                    .map(i -> String.format("[%s] %s (담당자: %s, 상태: %s)", i.key(), i.summary(), i.assignee(), i.status()))
                    .collect(Collectors.toList());
            
            String filteredJiraIssues = teamIssuesList.isEmpty() ? "N/A" : String.join("\n", teamIssuesList);

            String collectPrompt = String.format("""
                당신은 [%s] 전담 행정 총무입니다. 제공된 데이터에서 [%s]과 명시적으로 관련된 내용만 발췌하세요.
                
                # 절대 엄수 사항:
                - 다른 팀의 업무는 절대로 포함하지 마세요. (예: %s가 아닌 팀의 내용 배제)
                - 모호한 내용은 버리고, 핵심 업무명, 일정, 진행률만 추출하세요.
                
                # 원본 데이터:
                <OKR>%s</OKR>
                <MEETING>%s</MEETING>
                
                # 출력 형식 (JSON):
                {
                  "currentOkr": "이 팀의 목표만 요약",
                  "currentMeetingIssues": "이 팀의 보고사항만 요약"
                }
                """, team, team, team, rawData.okrHtml(), rawData.meetingHtml());

            TeamSummary summary = ai.withLlmByRole("simple").withPromptContributor(collectorPersona)
                    .creating(TeamSummary.class).fromPrompt(collectPrompt);

            return new TeamAnalysis(team, summary.currentOkr(), summary.currentMeetingIssues(), filteredJiraIssues, "");
        }).toList();

        List<TeamAnalysis> analyses = evaluateTeams(collectedData, null, "performant", ai, this.analystPersona);
        analyses.forEach(ctx::addObject);
        return new AnalyzeTeamsState(rawData, jiraIssueList, analyses, this.analystPersona, this.fileTools);
    }

    private static List<TeamAnalysis> evaluateTeams(List<TeamAnalysis> extractedData, String feedback, String role, Ai ai, RoleGoalBackstory analystPersona) {
        String dataText = extractedData.stream()
            .map(d -> String.format("팀명: [%s]\n- OKR: %s\n- 회의록: %s\n- Jira: %s\n", d.teamName(), d.currentOkr(), d.currentMeetingIssues(), d.currentJiraIssues()))
            .collect(Collectors.joining("\n---\n"));

        String prompt = String.format("""
            연구소장으로서 각 팀의 주간 성과를 진단하세요. 형식적인 멘트보다는 실질적인 통찰을 제공해야 합니다.
            
            %s
            
            # 팀별 데이터:
            %s
            %s
            
            # 진단 포인트:
            1. **전략적 정합성**: 팀의 활동이 OKR 달성에 실질적으로 기여하고 있는가?
            2. **실행력 및 병목**: 지연되는 과제의 근본 원인은 무엇이며, 어떤 지원이 필요한가?
            3. **기술 부채 및 리스크**: 리팩토링이나 내부 개발에 매몰되어 비즈니스 기회를 놓치고 있지는 않은가?
            
            # 출력 지침:
            - 각 팀별로 충분한 깊이의 분석 의견을 작성하세요. (글자 수 제한 없음)
            - 제공된 JSON 스키마 형식에 맞게 각 팀의 의견(eeTeamOpinion, beTeamOpinion 등)을 정확한 필드에 작성하세요. 누락되는 팀이 없도록 하세요.
            """, TEAM_RNR_INFO, dataText, feedback != null ? "\n# 이전 피드백 반영 지시:\n" + feedback : "");

        TeamOpinions opinions = ai.withLlmByRole(role).withPromptContributor(analystPersona)
                .creating(TeamOpinions.class).fromPrompt(prompt);

        return extractedData.stream().map(d -> {
            String op = opinions.getOpinion(d.teamName());
            if (op == null || op.isBlank()) {
                System.err.println("[BUG] 분석 의견 누락 발생! 팀명: " + d.teamName());
                System.err.println("[DEBUG] LLM Raw Response: " + opinions);
                op = "분석 의견을 생성하지 못했습니다.";
            }
            return new TeamAnalysis(d.teamName(), d.currentOkr(), d.currentMeetingIssues(), d.currentJiraIssues(), op);
        }).toList();
    }

    @State
    public static record AnalyzeTeamsState(RawWeeklyData rawData, JiraIssueList jiraIssues, List<TeamAnalysis> analyses, RoleGoalBackstory analystPersona, CoreFileTools fileTools) implements Stage {
        @Action
        public ApprovalDecision waitForApproval(ActionContext ctx) {
            String processId = ctx.getProcessContext().getAgentProcess().getId();
            StringBuilder sb = new StringBuilder();
            analyses.forEach(a -> sb.append("팀: ").append(a.teamName()).append("\n- 의견: ").append(a.aiOpinion()).append("\n\n"));
            
            ApplicationContextHolder.getPublisher().publishEvent(
                new ApprovalRequestedEvent(processId, "팀별 분석 내용을 검토하고 승인해주세요.", sb.toString())
            );
            return WaitFor.formSubmission("Approval Event Published", ApprovalDecision.class);
        }

        @Action(clearBlackboard = true)
        public Stage processFeedback(ApprovalDecision decision, Ai ai, ActionContext ctx) {
            if (decision.approved()) {
                String prompt = """
                    승인된 팀별 데이터를 통합하여 최종 주간보고서를 Markdown 형식으로 작성하세요.
                    
                    # 작성 지침 (중요):
                    1. **통합 및 중복 제거**: 여러 팀에서 보고한 동일 프로젝트(예: 이동의자유, KGM)는 하나의 항목으로 합치고 팀명을 병기하세요.
                       - 예: - **[이동의자유 (BE, FE)]** 기사앱 통합 테스트 및 CD 구성 : ~03/13 (70%)
                    2. **조직 구조 준수 (Markdown 헤더 사용)**:
                       - `### 개발그룹` 하위에 `#### 사업 지원`, `#### 내부 개발` 섹션만 둡니다. (BE, FE, EE, PKI, PnC 통합)
                       - `### 엔지니어링팀` 하위에 해당 팀 업무를 기술합니다.
                    3. **형식**: `- **[프로젝트명 (팀명)]** 내용 : 일정 (진행률)`
                    
                    # 데이터:
                    """ + analyses;

                FinalWeeklyReport finalReport = ai.withLlmByRole("performant").withPromptContributor(analystPersona)
                        .creating(FinalWeeklyReport.class).withExample("최종 주간보고 구조 예시", REPORT_EXAMPLE).fromPrompt(prompt);
                return new FinalizeReportState(finalReport, analyses, analystPersona, fileTools);
            } else {
                List<TeamAnalysis> reEvaluated = evaluateTeams(analyses, decision.comment(), "normal", ai, analystPersona);
                reEvaluated.forEach(ctx::addObject);
                return new AnalyzeTeamsState(rawData, jiraIssues, reEvaluated, analystPersona, fileTools);
            }
        }
    }

    @State
    public static record FinalizeReportState(FinalWeeklyReport report, List<TeamAnalysis> analyses, RoleGoalBackstory analystPersona, CoreFileTools fileTools) implements Stage {
        @Action
        public ApprovalDecision waitForFinalApproval(ActionContext ctx) {
            String processId = ctx.getProcessContext().getAgentProcess().getId();
            String preview = "Notice (Markdown):\n" + report.noticeHtml() + "\n\nRequest (Markdown):\n" + report.requestHtml();
            ApplicationContextHolder.getPublisher().publishEvent(
                new ApprovalRequestedEvent(processId, "최종 보고서 초안을 검토해주세요.", preview)
            );
            return WaitFor.formSubmission("Approval Event Published", ApprovalDecision.class);
        }

        @Action(clearBlackboard = true)
        public Stage finalize(ApprovalDecision decision, Ai ai) {
            if (decision.approved()) {
                return new FinishedState(report, analyses, fileTools);
            } else {
                String prompt = String.format("사용자 피드백을 반영하여 Markdown 보고서를 수정하세요.\n\n# 피드백: %s\n\n# 현재 내용: %s", decision.comment(), report);
                FinalWeeklyReport revised = ai.withLlmByRole("normal").withPromptContributor(analystPersona)
                        .creating(FinalWeeklyReport.class).withExample("주간보고 형식 유지 예시", REPORT_EXAMPLE).fromPrompt(prompt);
                return new FinalizeReportState(revised, analyses, analystPersona, fileTools);
            }
        }
    }

    @State
    public static record FinishedState(FinalWeeklyReport finalReport, List<TeamAnalysis> analyses, CoreFileTools fileTools) implements Stage {
        @Action
        @AchievesGoal(description = "주간보고서가 최종 승인됨")
        public FinalWeeklyReport done() {
            String dateStr = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
            String filename = "weekly-report." + dateStr + ".md";
            
            StringBuilder sb = new StringBuilder();
            sb.append("# 주간보고서 (").append(LocalDate.now()).append(")\n\n");
            
            sb.append("## 1. 최종 요약\n\n");
            sb.append("### 공지/공유사항\n").append(finalReport.noticeHtml()).append("\n\n");
            sb.append("### 요청/대기사항\n").append(finalReport.requestHtml()).append("\n\n");
            
            sb.append("---\n\n## 2. 팀별 상세 분석 및 중간 데이터\n\n");
            for (TeamAnalysis a : analyses) {
                sb.append("### [").append(a.teamName()).append("]\n");
                sb.append("**1. OKR 요약**\n").append(a.currentOkr()).append("\n\n");
                sb.append("**2. 회의록 요약**\n").append(a.currentMeetingIssues()).append("\n\n");
                sb.append("**3. Jira 이슈 현황**\n").append(a.currentJiraIssues()).append("\n\n");
                sb.append("**4. 연구소장 진단 의견**\n> ").append(a.aiOpinion().replace("\n", "\n> ")).append("\n\n");
            }
            
            try {
                fileTools.saveGeneratedContent(filename, sb.toString());
            } catch (Exception e) {
                System.err.println("보고서 저장 실패: " + e.getMessage());
            }
            return finalReport;
        }
    }
}