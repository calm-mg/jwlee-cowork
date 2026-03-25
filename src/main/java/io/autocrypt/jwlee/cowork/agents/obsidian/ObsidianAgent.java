package io.autocrypt.jwlee.cowork.agents.obsidian;

import com.embabel.agent.api.annotation.AchievesGoal;
import com.embabel.agent.api.annotation.Action;
import com.embabel.agent.api.annotation.Agent;
import com.embabel.agent.api.common.ActionContext;
import com.embabel.agent.api.common.Ai;

import io.autocrypt.jwlee.cowork.core.hitl.ApplicationContextHolder;
import io.autocrypt.jwlee.cowork.core.hitl.NotificationEvent;
import io.autocrypt.jwlee.cowork.core.prompts.PromptProvider;
import io.autocrypt.jwlee.cowork.core.tools.CoreFileTools;
import io.autocrypt.jwlee.cowork.core.tools.CoworkLogger;
import io.autocrypt.jwlee.cowork.core.tools.GitTools;
import io.autocrypt.jwlee.cowork.core.tools.GoogleServiceTools;
import io.autocrypt.jwlee.cowork.core.tools.ObsidianTools;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.time.temporal.WeekFields;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Agent(description = "Manages Obsidian Daily and Weekly notes with Git synchronization.")
@Component
public class ObsidianAgent {

    public record DailyRequest() {}
    public record WeeklyRequest() {}
    public record ObsidianResult(String message) {}

    private final GitTools gitTools;
    private final ObsidianTools obsidianTools;
    private final GoogleServiceTools googleServiceTools;
    private final CoreFileTools fileTools;
    private final CoworkLogger logger;
    private final PromptProvider promptProvider;

    private static final DateTimeFormatter DAILY_FORMAT = DateTimeFormatter.ofPattern("'🗓'yyyy-MM-dd");
    private static final DateTimeFormatter WEEKLY_FORMAT = DateTimeFormatter.ofPattern("'🗓'yyyy-'W'ww");

    public ObsidianAgent(GitTools gitTools, ObsidianTools obsidianTools, GoogleServiceTools googleServiceTools, 
                        CoreFileTools fileTools, CoworkLogger logger, PromptProvider promptProvider) {
        this.gitTools = gitTools;
        this.obsidianTools = obsidianTools;
        this.googleServiceTools = googleServiceTools;
        this.fileTools = fileTools;
        this.logger = logger;
        this.promptProvider = promptProvider;
    }

    private void log(String message) {
        logger.info("Obsidian", message);
    }

    @AchievesGoal(description = "Daily note created")
    @Action
    public ObsidianResult createDailyNote(DailyRequest req, Ai ai, ActionContext ctx) throws IOException, InterruptedException {
        log("Starting daily note creation...");
        gitTools.syncVault();

        LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));
        String relativePath = String.format("Calendar/Daily notes/%d/%02d/%s.md", 
                today.getYear(), today.getMonthValue(), today.format(DAILY_FORMAT));
        
        if (obsidianTools.checkNoteExists(relativePath)) {
            log("Daily note already exists: " + relativePath);
            return new ObsidianResult("이미 오늘 문서가 존재합니다: " + relativePath);
        }

        String recentNotePath = obsidianTools.findMostRecentDailyNote();
        log("Recent daily note: " + recentNotePath);
        
        List<String> unfinishedTasks = obsidianTools.extractUnfinishedTasks(recentNotePath);
        log("Extracted " + unfinishedTasks.size() + " unfinished tasks.");

        log("Fetching external data (Google Tasks)...");
        String googleTasks = googleServiceTools.fetchGoogleTasks();

        LocalDate yesterday = today.minus(1, ChronoUnit.DAYS);
        
        WeekFields weekFields = WeekFields.of(Locale.getDefault());
        String thisWeek = today.format(WEEKLY_FORMAT);
        String previousWeek = today.minus(1, ChronoUnit.WEEKS).format(WEEKLY_FORMAT);

        log("Generating daily note content with LLM...");
        String prompt = promptProvider.getPrompt("agents/obsidian/daily.jinja", Map.of(
            "unfinishedTasks", String.join("\n", unfinishedTasks),
            "googleTasks", googleTasks,
            "today", today.format(DAILY_FORMAT),
            "yesterday", yesterday.format(DAILY_FORMAT),
            "thisWeek", thisWeek,
            "previousWeek", previousWeek,
            "tasksToInclude", String.join("\n", unfinishedTasks)
        ));

        String content = ai.withDefaultLlm().generateText(prompt);

        obsidianTools.writeVaultNote(relativePath, content);
        log("Daily note written to vault.");

        gitTools.commitAndPush("Auto-generated daily note for " + today);

        ApplicationContextHolder.getPublisher().publishEvent(
            new NotificationEvent("Daily note 생성 완료", "오늘의 Obsidian 문서를 생성했습니다: " + relativePath)
        );

        return new ObsidianResult("Successfully created and pushed daily note: " + relativePath);
    }

    @AchievesGoal(description = "Weekly note created")
    @Action
    public ObsidianResult createWeeklyNote(WeeklyRequest req, Ai ai, ActionContext ctx) throws IOException, InterruptedException {
        log("Starting weekly note creation...");
        gitTools.syncVault();

        LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));
        String thisWeek = today.format(WEEKLY_FORMAT);
        String relativePath = String.format("Calendar/Weekly notes/%s.md", thisWeek);
        
        if (obsidianTools.checkNoteExists(relativePath)) {
            log("Weekly note already exists: " + relativePath);
            return new ObsidianResult("이미 이번주 문서가 존재합니다: " + relativePath);
        }

        WeekFields weekFields = WeekFields.of(Locale.getDefault());
        LocalDate monday = today.with(weekFields.dayOfWeek(), 2); // Monday
        
        // Ensure we get the correct Monday for the current week
        if (monday.isAfter(today)) {
            monday = monday.minusWeeks(1);
        }

        StringBuilder dailyNotesContent = new StringBuilder();
        log("Reading daily notes for the week starting from " + monday);

        for (int i = 0; i < 5; i++) { // Monday to Friday
            LocalDate day = monday.plusDays(i);
            String dailyPath = String.format("Calendar/Daily notes/%d/%02d/%s.md", 
                    day.getYear(), day.getMonthValue(), day.format(DAILY_FORMAT));
            
            if (obsidianTools.checkNoteExists(dailyPath)) {
                log("Including daily note: " + dailyPath);
                String content = fileTools.readFile("obsidian-vault/" + dailyPath).content();
                dailyNotesContent.append("### ").append(day.format(DAILY_FORMAT)).append("\n\n");
                dailyNotesContent.append(content).append("\n\n---\n\n");
            }
        }

        String previousWeek = today.minus(1, ChronoUnit.WEEKS).format(WEEKLY_FORMAT);

        log("Generating weekly note content with LLM...");
        String prompt = promptProvider.getPrompt("agents/obsidian/weekly.jinja", Map.of(
            "dailyNotes", dailyNotesContent.toString(),
            "thisWeek", thisWeek,
            "previousWeek", previousWeek
        ));

        String content = ai.withDefaultLlm().generateText(prompt);

        obsidianTools.writeVaultNote(relativePath, content);
        log("Weekly note written to vault.");

        gitTools.commitAndPush("Auto-generated weekly note for " + thisWeek);

        ApplicationContextHolder.getPublisher().publishEvent(
            new NotificationEvent("Weekly note 생성 완료", "이번주 Obsidian 문서를 생성했습니다: " + relativePath)
        );

        return new ObsidianResult("Successfully created weekly note: " + relativePath);
    }
}
