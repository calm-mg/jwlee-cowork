package io.autocrypt.jwlee.cowork.agents.morningbriefing;

import com.embabel.agent.api.annotation.AchievesGoal;
import com.embabel.agent.api.annotation.Action;
import com.embabel.agent.api.annotation.Agent;
import com.embabel.agent.api.annotation.State;
import com.embabel.agent.api.common.Ai;
import com.embabel.common.ai.model.LlmOptions;
import io.autocrypt.jwlee.cowork.agents.weekly.dto.JiraIssueInfo;
import io.autocrypt.jwlee.cowork.agents.weekly.dto.MeetingInfo;
import io.autocrypt.jwlee.cowork.agents.weekly.service.ConfluenceService;
import io.autocrypt.jwlee.cowork.agents.weekly.service.JiraExcelService;
import io.autocrypt.jwlee.cowork.core.hitl.ApplicationContextHolder;
import io.autocrypt.jwlee.cowork.core.hitl.NotificationEvent;
import io.autocrypt.jwlee.cowork.core.prompts.PromptProvider;
import io.autocrypt.jwlee.cowork.core.tools.CoreFileTools;
import io.autocrypt.jwlee.cowork.core.tools.CoreWorkspaceProvider;
import io.autocrypt.jwlee.cowork.core.tools.CoworkLogger;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Agent(description = "Analyzes yesterday's Jira and Confluence updates to generate a morning briefing.")
@Component
public class MorningBriefingAgent {

    public record JiraChange(String issueKey, String summary, String oldStatus, String newStatus, String assignee) {}
    public record MeetingNote(String title, String rawContent, String author) {}
    public record MeetingSummary(String title, List<String> keyDecisions, List<String> actionItems) {}
    public record MeetingSummaryList(List<MeetingSummary> summaries) {}
    
    public record MorningBriefingReport(String content) {}
    public record BriefingRequest(String targetDate) {}

    @State
    public interface Stage {}

    @State
    public record BriefingPreparationState(String targetDate, List<JiraChange> jiraChanges, List<MeetingNote> rawMeetingNotes) implements Stage {}

    @State
    public record SynthesisState(String targetDate, List<JiraChange> jiraChanges, List<MeetingSummary> meetingSummaries) implements Stage {}

    private final JiraExcelService jiraExcelService;
    private final ConfluenceService confluenceService;
    private final PromptProvider promptProvider;
    private final CoreWorkspaceProvider workspaceProvider;
    private final CoreFileTools fileTools;
    private final CoworkLogger logger;

    public MorningBriefingAgent(JiraExcelService jiraExcelService, ConfluenceService confluenceService, 
                               PromptProvider promptProvider, CoreWorkspaceProvider workspaceProvider, 
                               CoreFileTools fileTools, CoworkLogger logger) {
        this.jiraExcelService = jiraExcelService;
        this.confluenceService = confluenceService;
        this.promptProvider = promptProvider;
        this.workspaceProvider = workspaceProvider;
        this.fileTools = fileTools;
        this.logger = logger;
    }

    @Action
    public BriefingPreparationState gatherYesterdayData(BriefingRequest req) throws IOException {
        String targetDate = req.targetDate() == null || req.targetDate().isEmpty() 
            ? LocalDate.now(ZoneId.of("Asia/Seoul")).minusDays(1).toString() 
            : req.targetDate();
        
        logger.info("MorningBriefing", "데이터 수집 시작 (Target: " + targetDate + ")");

        List<JiraIssueInfo> rawIssues = jiraExcelService.readIssues();
        List<JiraChange> jiraChanges = rawIssues.stream()
                .limit(20)
                .map(i -> new JiraChange(i.key(), i.summary(), "UNKNOWN", i.status(), i.assignee()))
                .collect(Collectors.toList());

        List<MeetingInfo> meetingUrls = confluenceService.getRecentMeetingUrls();
        List<MeetingNote> rawNotes = meetingUrls.stream()
                .limit(3)
                .map(m -> {
                    String content = "";
                    try { content = confluenceService.getPageStorage(m.id()); } catch (Exception ignored) {}
                    return new MeetingNote(m.title(), content, "System");
                })
                .collect(Collectors.toList());

        return new BriefingPreparationState(targetDate, jiraChanges, rawNotes);
    }

    @Action
    public SynthesisState summarizeMeetings(BriefingPreparationState state, Ai ai) {
        if (state.rawMeetingNotes().isEmpty()) {
            return new SynthesisState(state.targetDate(), state.jiraChanges(), List.of());
        }

        logger.info("MorningBriefing", "회의록 요약 중...");
        String prompt = promptProvider.getPrompt("agents/morningbriefing/summarize_meetings.jinja", Map.of(
            "targetDate", state.targetDate(),
            "notes", state.rawMeetingNotes()
        ));

         MeetingSummaryList summarized = ai.withLlm(LlmOptions.withLlmForRole("normal").withoutThinking())
                .creating(MeetingSummaryList.class)
                .fromPrompt(prompt);

        return new SynthesisState(state.targetDate(), state.jiraChanges(), summarized.summaries());
    }

    @AchievesGoal(description = "Generate final morning briefing report")
    @Action
    public MorningBriefingReport generateBriefingAndTasks(SynthesisState state, Ai ai) {
        logger.info("MorningBriefing", "최종 마크다운 리포트 생성 중...");
        
        String prompt = promptProvider.getPrompt("agents/morningbriefing/generate_tasks.jinja", Map.of(
            "reportDate", state.targetDate(),
            "jiraChanges", state.jiraChanges(),
            "meetingSummaries", state.meetingSummaries()
        ));

        String markdown = ai.withLlm(LlmOptions.withLlmForRole("performant").withoutThinking())
                .generateText(prompt);

        // 알림으로 리포트 전문 전송
        ApplicationContextHolder.getPublisher().publishEvent(
            new NotificationEvent("Morning Briefing (" + state.targetDate() + ")", markdown)
        );
        
        logger.info("MorningBriefing", "브리핑 생성 및 알림 완료");
        return new MorningBriefingReport(markdown);
    }
}
