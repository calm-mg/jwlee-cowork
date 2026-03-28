package io.autocrypt.jwlee.cowork.presalesagent;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.embabel.agent.api.annotation.AchievesGoal;
import com.embabel.agent.api.annotation.Action;
import com.embabel.agent.api.annotation.Agent;
import com.embabel.agent.api.common.ActionContext;
import com.embabel.agent.api.common.OperationContext;
import com.embabel.agent.api.common.workflow.loop.RepeatUntilAcceptableBuilder;
import com.embabel.agent.api.common.workflow.loop.TextFeedback;
import com.embabel.agent.core.AgentProcess;
import com.embabel.agent.core.LlmInvocation;
import com.embabel.agent.rag.tools.ResultsEvent;
import com.embabel.common.ai.model.LlmOptions;

import io.autocrypt.jwlee.cowork.core.prompts.PromptProvider;
import io.autocrypt.jwlee.cowork.core.tools.CoworkLogger;
import io.autocrypt.jwlee.cowork.core.tools.LocalRagTools;
import io.autocrypt.jwlee.cowork.core.workaround.JsonSafeToolishRag;

@Agent(description = "Presales Engineering Agent for requirement analysis and gap assessment")
@Component
public class PresalesAgent {

    private final LocalRagTools localRagTools;
    private final PromptProvider promptProvider;
    private final CoworkLogger logger;

    public PresalesAgent(LocalRagTools localRagTools, PromptProvider promptProvider, CoworkLogger logger) {
        this.localRagTools = localRagTools;
        this.promptProvider = promptProvider;
        this.logger = logger;
    }

    public record RequirementRequest(String sourceContent, Path techRagPath) {}

    public record GapAnalysisRequest(String crsContent, String originalLanguage, Path productRagPath) {}

    public record AnalysisResult(String finalReport) {}

    // 플래닝 혼선을 방지하기 위한 고유 결과 타입
    public record CrsResult(String content) {}

    /**
     * Phase 1: Refine customer inquiry (email, chat, transcript) into a technical CRS using technical reference RAG.
     */
    @AchievesGoal(description = "Refined CRS in Markdown")
    @Action
    public CrsResult refineRequirements(RequirementRequest req, ActionContext ctx) throws IOException {
        var techSearch = localRagTools.getOrOpenInstance("tech-ref", req.techRagPath());
        var techRag = new JsonSafeToolishRag("tech_knowledge", "Standard technical specifications and industry knowledge", techSearch)
                .withListener(event -> ctx.addObject(event));

        var simpleAi = ctx.ai().withLlmByRole("simple").withReference(techRag);
        var normalAi = ctx.ai().withLlm(LlmOptions.withLlmForRole("normal").withMaxTokens(65536));

        // 1. Gatherer-Critic Loop to collect sufficient technical context
        String techContext = RepeatUntilAcceptableBuilder
                .returning(String.class)
                .withMaxIterations(3)
                .withScoreThreshold(0.7)
                .repeating(loopCtx -> {
                    var lastAttempt = loopCtx.lastAttempt();
                    String lastFindings = lastAttempt != null ? lastAttempt.getResult() : "No previous findings.";
                    String feedback = lastAttempt != null ? lastAttempt.getFeedback().toString() : "Initial search.";
                    
                    String prompt = promptProvider.getPrompt("agents/presales/refine-requirements-search.jinja", Map.of(
                        "sourceContent", req.sourceContent(),
                        "lastFindings", lastFindings,
                        "feedback", feedback
                    ));
                    
                    return simpleAi.generateText(prompt);
                })
                .withEvaluator(loopCtx -> {
                    String prompt = promptProvider.getPrompt("agents/presales/refine-requirements-eval.jinja", Map.of(
                        "sourceContent", req.sourceContent(),
                        "contextToEvaluate", loopCtx.getResultToEvaluate()
                    ));
                    
                    return normalAi.createObject(prompt, TextFeedback.class);
                })
                .build()
                .asSubProcess(ctx, String.class);

        // 2. Normal AI Worker drafts the final CRS
        String finalPrompt = promptProvider.getPrompt("agents/presales/refine-requirements-final.jinja", Map.of(
            "sourceContent", req.sourceContent(),
            "techContext", techContext
        ));

        String markdown = normalAi.generateText(finalPrompt);
        logImmediateMetrics(ctx, "RefineRequirements");
        return new CrsResult(markdown);
    }

    /**
     * Phase 2: Perform gap analysis on CRS using the product specifications RAG.
     */
    @AchievesGoal(description = "Internal Technical Review Report completed")
    @Action
    public AnalysisResult analyzeGapAndFinalize(GapAnalysisRequest req, ActionContext ctx) throws IOException {
        var productSearch = localRagTools.getOrOpenInstance("product-spec", req.productRagPath());
        var productRag = new JsonSafeToolishRag("product_knowledge", "Internal product features, limits, and integration specs", productSearch)
                .withListener(event -> ctx.addObject(event));

        var simpleAi = ctx.ai().withLlmByRole("simple").withReference(productRag);
        var normalAi = ctx.ai().withLlm(LlmOptions.withLlmForRole("normal").withMaxTokens(65536));

        // 1. Gatherer-Critic Loop for gap analysis search
        String productContext = RepeatUntilAcceptableBuilder
                .returning(String.class)
                .withMaxIterations(3)
                .withScoreThreshold(0.7)
                .repeating(loopCtx -> {
                    var lastAttempt = loopCtx.lastAttempt();
                    String lastFindings = lastAttempt != null ? lastAttempt.getResult() : "No previous findings.";
                    String feedback = lastAttempt != null ? lastAttempt.getFeedback().toString() : "Initial search.";

                    String prompt = promptProvider.getPrompt("agents/presales/gap-analysis-search.jinja", Map.of(
                        "crsContent", req.crsContent(),
                        "lastFindings", lastFindings,
                        "feedback", feedback
                    ));

                    return simpleAi.generateText(prompt);
                })
                .withEvaluator(loopCtx -> {
                    String prompt = promptProvider.getPrompt("agents/presales/gap-analysis-eval.jinja", Map.of(
                        "crsContent", req.crsContent(),
                        "contextToEvaluate", loopCtx.getResultToEvaluate()
                    ));

                    return normalAi.createObject(prompt, TextFeedback.class);
                })
                .build()
                .asSubProcess(ctx, String.class);

        // 2. Normal AI Worker writes the formal final report directly
        String finalReportPrompt = promptProvider.getPrompt("agents/presales/gap-analysis-combined.jinja", Map.of(
            "crsContent", req.crsContent(),
            "productContext", productContext
        ));

        String finalReport = normalAi.generateText(finalReportPrompt);

        logImmediateMetrics(ctx, "GapAnalysis");
        return new AnalysisResult(finalReport);
    }

    private void logImmediateMetrics(OperationContext ctx, String phase) {
        AgentProcess process = ctx.getAgentProcess();
        
        List<LlmInvocation> invocations = process.getLlmInvocations();
        if (!invocations.isEmpty()) {
            LlmInvocation last = invocations.get(invocations.size() - 1);
            String coreLlmLog = String.format("Last Core LLM Invocation:\n- Model: %s\n- Latency: %.1fs\n- Tokens: %d (P: %d, C: %d)\n- Cost: $%.6f",
                    last.getLlmMetadata().getName(),
                    last.getRunningTime().toMillis() / 1000.0,
                    last.getUsage().getTotalTokens(),
                    last.getUsage().getPromptTokens(),
                    last.getUsage().getCompletionTokens(),
                    last.cost());
            logger.info(phase, "[Core LLM Info]\n" + coreLlmLog);
        }

        List<ResultsEvent> ragEvents = ctx.objectsOfType(ResultsEvent.class);
        if (!ragEvents.isEmpty()) {
            String ragLog = ragEvents.stream()
                    .map(e -> String.format("- Query: %s\n  Results: %d items", e.getQuery(), e.getResults().size()))
                    .collect(Collectors.joining("\n"));
            logger.info(phase, "[RAG Usage Info]\n" + ragLog);
        }
    }
}
