package io.autocrypt.jwlee.cowork.agents.presales;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.embabel.agent.api.annotation.AchievesGoal;
import com.embabel.agent.api.annotation.Action;
import com.embabel.agent.api.annotation.Agent;
import com.embabel.agent.api.common.ActionContext;
import com.embabel.agent.api.common.workflow.loop.RepeatUntilAcceptableBuilder;
import com.embabel.agent.api.common.workflow.loop.TextFeedback;

import io.autocrypt.jwlee.cowork.core.prompts.PromptProvider;
import io.autocrypt.jwlee.cowork.core.tools.LocalRagTools;
import io.autocrypt.jwlee.cowork.core.workaround.JsonSafeToolishRag;

@Agent(description = "Presales Engineering Agent for requirement analysis and gap assessment")
@Component
public class PresalesAgent {

    private final LocalRagTools localRagTools;
    private final PromptProvider promptProvider;

    public PresalesAgent(LocalRagTools localRagTools, PromptProvider promptProvider) {
        this.localRagTools = localRagTools;
        this.promptProvider = promptProvider;
    }

    public record RequirementRequest(String sourceContent, Path techRagPath) {}

    public record GapAnalysisRequest(String crsContent, String originalLanguage, Path productRagPath) {}

    public record AnalysisResult(String gapAnalysis, String questions, String finalReport) {}

    // 플래닝 혼선을 방지하기 위한 고유 결과 타입
    public record CrsResult(String content) {}

    /**
     * Phase 1: Refine customer inquiry (email, chat, transcript) into a technical CRS using technical reference RAG.
     */
    @AchievesGoal(description = "Refined CRS in Markdown")
    @Action
    public CrsResult refineRequirements(RequirementRequest req, ActionContext ctx) throws IOException {
        var techSearch = localRagTools.getOrOpenInstance("tech-ref", req.techRagPath());
        var techRag = new JsonSafeToolishRag("tech_knowledge", "Standard technical specifications and industry knowledge", techSearch);

        var simpleAi = ctx.ai().withLlmByRole("simple").withReference(techRag);
        var normalAi = ctx.ai().withLlmByRole("normal");

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
        return new CrsResult(markdown);
    }

    /**
     * Phase 2: Perform gap analysis and generate an internal technical review report.
     */
    @AchievesGoal(description = "Internal Technical Review Report completed")
    @Action
    public AnalysisResult analyzeGapAndFinalize(GapAnalysisRequest req, ActionContext ctx) throws IOException {
        var productSearch = localRagTools.getOrOpenInstance("product-spec", req.productRagPath());
        var productRag = new JsonSafeToolishRag("product_knowledge", "Internal product features, roadmap, and technical specifications", productSearch);

        var simpleAi = ctx.ai().withLlmByRole("simple").withReference(productRag);
        var normalAi = ctx.ai().withLlmByRole("normal");

        // 1. Gatherer-Critic Loop to collect product capability information
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

        // 2. Worker: Generate the final Internal Review Report
        String analysisPrompt = promptProvider.getPrompt("agents/presales/gap-analysis-worker.jinja", Map.of(
            "crsContent", req.crsContent(),
            "productContext", productContext
        ));

        String rawAnalysis = normalAi.generateText(analysisPrompt);

        String finalReportPrompt = promptProvider.getPrompt("agents/presales/final-report.jinja", Map.of(
            "rawAnalysis", rawAnalysis
        ));

        String finalReport = normalAi.generateText(finalReportPrompt);

        String questionPrompt = promptProvider.getPrompt("agents/presales/extract-questions.md") + "\n\n" + finalReport;
        String questions = normalAi.generateText(questionPrompt);

        return new AnalysisResult(rawAnalysis, questions, finalReport);
    }
}
