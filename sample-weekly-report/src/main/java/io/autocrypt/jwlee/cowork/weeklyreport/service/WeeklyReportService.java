package io.autocrypt.jwlee.cowork.weeklyreport.service;

import com.embabel.agent.core.AgentPlatform;
import com.embabel.agent.core.AgentProcess;
import com.embabel.agent.core.AgentProcessStatusCode;
import com.embabel.agent.core.ProcessOptions;
import io.autocrypt.jwlee.cowork.weeklyreport.agent.WeeklyReportAgent;
import io.autocrypt.jwlee.cowork.weeklyreport.domain.WeeklyReportEntity;
import io.autocrypt.jwlee.cowork.weeklyreport.dto.*;
import io.autocrypt.jwlee.cowork.weeklyreport.repository.WeeklyReportRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class WeeklyReportService {

    private static final Logger log = LoggerFactory.getLogger(WeeklyReportService.class);

    private final ConfluenceService confluenceService;
    private final JiraExcelService jiraExcelService;
    private final WeeklyReportRepository repository;
    private final AgentPlatform agentPlatform;
    private final WeeklyReportAgent weeklyReportAgent;
    
    private final ConcurrentHashMap<String, AgentProcess> activeProcesses = new ConcurrentHashMap<>();

    public WeeklyReportService(ConfluenceService confluenceService, 
                               JiraExcelService jiraExcelService, 
                               WeeklyReportRepository repository, 
                               AgentPlatform agentPlatform,
                               WeeklyReportAgent weeklyReportAgent) {
        this.confluenceService = confluenceService;
        this.jiraExcelService = jiraExcelService;
        this.repository = repository;
        this.agentPlatform = agentPlatform;
        this.weeklyReportAgent = weeklyReportAgent;
    }

    public String startGeneration(String meetingPageId) {
        // 1. Confluence에서 원본 HTML(Storage Format) 그대로 가져오기
        String okrHtml = confluenceService.getPageStorage(((RealConfluenceService)confluenceService).getOkrPageId());
        String meetingHtml = confluenceService.getPageStorage(meetingPageId);
        
        RawWeeklyData rawData = new RawWeeklyData(okrHtml, meetingHtml);
        
        // 2. Jira 이슈는 그대로 가져오기 (기계적 분류 용도)
        List<JiraIssueInfo> jiraIssues = jiraExcelService.readIssues();

        // Get the Embabel agent instance corresponding to the Spring Bean
        com.embabel.agent.core.Agent agent = agentPlatform.agents().stream()
                .filter(a -> a.getName().equals("WeeklyReportAgent"))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("WeeklyReportAgent not found in platform"));

        // Create the process first so we get an ID immediately
        AgentProcess process = agentPlatform.createAgentProcessFrom(
                agent, 
                ProcessOptions.DEFAULT, 
                rawData, 
                new JiraIssueList(jiraIssues)
        );
        
        String processId = process.getId();
        activeProcesses.put(processId, process);
        log.info("Created AgentProcess: {}", processId);

        // Run the process asynchronously so the HTTP request can return immediately
        CompletableFuture.runAsync(() -> {
            try {
                log.info("Starting process run: {}", processId);
                process.run();
                log.info("Process run finished or paused: {}", processId);
            } catch (Exception e) {
                log.error("Error during agent process execution", e);
            }
        });

        return processId;
    }

    public void provideFeedback(String processId, boolean approved, String comments) {
        AgentProcess process = activeProcesses.get(processId);
        if (process != null && process.getStatus() == AgentProcessStatusCode.WAITING) {
            process.getBlackboard().addObject(new HumanFeedback(approved, comments));
            
            // AI 처리가 길어질 수 있으므로 비동기로 실행하여 HTTP 응답(로딩바)을 즉시 반환
            CompletableFuture.runAsync(() -> {
                try {
                    log.info("Resuming process run after feedback: {}", processId);
                    process.run();
                    log.info("Process run paused or completed: {}", processId);
                } catch (Exception e) {
                    log.error("Error during agent process execution after feedback", e);
                }
            });
        }
    }

    public AgentProcess getProcess(String processId) {
        return activeProcesses.get(processId);
    }

    @Transactional
    public WeeklyReportEntity saveFinalReport(String processId) {
        AgentProcess process = activeProcesses.get(processId);
        if (process != null && (process.getStatus() == AgentProcessStatusCode.COMPLETED)) {
            io.autocrypt.jwlee.cowork.weeklyreport.agent.WeeklyReportAgent.FinishedState finishedState = 
                process.getBlackboard().last(io.autocrypt.jwlee.cowork.weeklyreport.agent.WeeklyReportAgent.FinishedState.class);
            
            FinalWeeklyReport report = finishedState.finalReport();
            
            StringBuilder contentBuilder = new StringBuilder();
            contentBuilder.append("<div class='weekly-report-final'>")
                          .append(report.noticeHtml())
                          .append("<hr class='my-4'>")
                          .append(report.requestHtml())
                          .append("</div>");

            // 1차 HITL에서 승인된 팀별 상세 분석 내역을 증적으로 포함
            if (finishedState.analyses() != null && !finishedState.analyses().isEmpty()) {
                contentBuilder.append("<hr><h3 class=\"mt-5 text-secondary\">[참고] 팀별 상세 분석 내역 (1단계 승인 데이터)</h3>");
                for (TeamAnalysis analysis : finishedState.analyses()) {
                    contentBuilder.append("<div class=\"card mb-3 border-light\"><div class=\"card-header bg-light fw-bold text-dark\">")
                                  .append(analysis.teamName()).append("</div><div class=\"card-body bg-white text-muted\"><ul>");
                    
                    contentBuilder.append("<li><b>현재 OKR:</b><br/><small>").append(analysis.currentOkr().replace("\n", "<br/>")).append("</small></li>");
                    contentBuilder.append("<li><b>진행중인 이슈(회의록):</b><br/><small>").append(analysis.currentMeetingIssues().replace("\n", "<br/>")).append("</small></li>");
                    contentBuilder.append("<li><b>Jira 이슈:</b><br/><small>").append(analysis.currentJiraIssues().replace("\n", "<br/>")).append("</small></li>");
                    contentBuilder.append("<li class=\"mt-2\"><b>AI 분석 의견:</b><br/><small class='text-primary'>").append(analysis.aiOpinion().replace("\n", "<br/>")).append("</small></li>");
                    
                    contentBuilder.append("</ul></div></div>");
                }
            }
            
            WeeklyReportEntity entity = WeeklyReportEntity.builder()
                    .title("최종 주간보고 - " + LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")))
                    .content(contentBuilder.toString())
                    .createdAt(LocalDateTime.now())
                    .build();
            
            activeProcesses.remove(processId);
            return repository.save(entity);
        }
        return null;
    }

    public WeeklyReportEntity getReportById(Long id) {
        return repository.findById(id).orElseThrow(() -> new IllegalArgumentException("Invalid report Id:" + id));
    }

    public List<WeeklyReportEntity> getAllReports() {
        return repository.findAllByOrderByCreatedAtDesc();
    }

    @Transactional
    public void deleteReport(Long id) {
        repository.deleteById(id);
    }
}
