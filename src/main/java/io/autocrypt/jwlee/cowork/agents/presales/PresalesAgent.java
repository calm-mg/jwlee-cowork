package io.autocrypt.jwlee.cowork.agents.presales;

import java.io.IOException;
import java.nio.file.Path;

import org.springframework.stereotype.Component;

import com.embabel.agent.api.annotation.AchievesGoal;
import com.embabel.agent.api.annotation.Action;
import com.embabel.agent.api.annotation.Agent;
import com.embabel.agent.api.common.ActionContext;
import com.embabel.agent.api.common.workflow.loop.RepeatUntilAcceptableBuilder;
import com.embabel.agent.api.common.workflow.loop.TextFeedback;

import io.autocrypt.jwlee.cowork.core.tools.LocalRagTools;
import io.autocrypt.jwlee.cowork.core.workaround.JsonSafeToolishRag;

@Agent(description = "Presales Engineering Agent for requirement analysis and gap assessment")
@Component
public class PresalesAgent {

    private final LocalRagTools localRagTools;

    public PresalesAgent(LocalRagTools localRagTools) {
        this.localRagTools = localRagTools;
    }

    public record RequirementRequest(String sourceContent, Path techRagPath) {}

    public record GapAnalysisRequest(String crsContent, String originalLanguage, Path productRagPath) {}

    public record AnalysisResult(String gapAnalysis, String questions, String finalReport) {}

    /**
     * Phase 1: Refine customer inquiry (email, chat, transcript) into a technical CRS using technical reference RAG.
     */
    @AchievesGoal(description = "Refined CRS in Markdown")
    @Action
    public String refineRequirements(RequirementRequest req, ActionContext ctx) throws IOException {
        var techSearch = localRagTools.getOrOpenInstance("tech-ref", req.techRagPath());
        var techRag = new JsonSafeToolishRag("tech_knowledge", "Standard technical specifications and industry knowledge", techSearch);

        var simpleAi = ctx.ai().withLlmByRole("simple").withReference(techRag);
        var normalAi = ctx.ai().withLlmByRole("normal");

        // 1. Gatherer-Critic Loop to collect sufficient technical context
        String techContext = RepeatUntilAcceptableBuilder
                .returning(String.class)
                .withMaxIterations(10)
                .withScoreThreshold(0.8)
                .repeating(loopCtx -> {
                    var lastAttempt = loopCtx.lastAttempt();
                    String lastFindings = lastAttempt != null ? lastAttempt.getResult() : "No previous findings.";
                    String feedback = lastAttempt != null ? lastAttempt.getFeedback().toString() : "Initial search.";
                    
                    String prompt = String.format("""
                        # TASK
                        Search 'tech_knowledge' to find technical specifications and industry standards related to the customer inquiry.
                        
                        # OBJECTIVE
                        Analyze the provided inquiry (email, chat log, or meeting transcript) and gather specific technical protocols, security standards, and terms (e.g., IEEE 1609.2, V2X).
                        Accumulate knowledge from each iteration. Do NOT discard previous findings.
                        
                        # CUSTOMER INQUIRY
                        %s
                        
                        # PREVIOUS FINDINGS
                        %s
                        
                        # CRITIC FEEDBACK
                        %s
                        
                        # OUTPUT
                        Return a comprehensive, aggregated technical summary including both previous findings and new discoveries.
                        """, req.sourceContent(), lastFindings, feedback);
                    
                    return simpleAi.generateText(prompt);
                })
                .withEvaluator(loopCtx -> {
                    String prompt = String.format("""
                        Review the gathered technical context against the customer inquiry.
                        Is the information sufficient to draft a detailed Customer Requirements Specification (CRS)?
                        
                        # Customer Inquiry:
                        %s
                        
                        # Gathered Context:
                        %s
                        
                        Return a score and specific feedback on what technical details are still missing.
                        """, req.sourceContent(), loopCtx.getResultToEvaluate());
                    
                    return normalAi.createObject(prompt, TextFeedback.class);
                })
                .build()
                .asSubProcess(ctx, String.class);

        // 2. Normal AI Worker drafts the final CRS
        String finalPrompt = String.format("""
            You are a Senior Solutions Architect. Using the provided technical context, refine the customer inquiry into a detailed Customer Requirements Specification (CRS) in Markdown format.
            
            # Customer Inquiry:
            %s
            
            # Technical Context:
            %s
            
            # Instructions:
            1. Structure the CRS clearly with sections: Introduction, Functional Requirements, Non-Functional Requirements, and Technical Constraints.
            2. Use industry-standard terminology found in the context.
            3. Do NOT include any analysis of product capabilities or gaps.
            4. Output ONLY the Markdown content.
            """, req.sourceContent(), techContext);

        return normalAi.generateText(finalPrompt);
    }

    /**
     * Phase 2: Perform gap analysis and estimate effort using product specification RAG.
     */
    @AchievesGoal(description = "Gap analysis and customer report")
    @Action
    public AnalysisResult analyzeGapAndFinalize(GapAnalysisRequest req, ActionContext ctx) throws IOException {
        var productSearch = localRagTools.getOrOpenInstance("product-spec", req.productRagPath());
        var productRag = new JsonSafeToolishRag("product_knowledge", "Internal product features, roadmap, and technical specifications", productSearch);

        var simpleAi = ctx.ai().withLlmByRole("simple").withReference(productRag);
        var normalAi = ctx.ai().withLlmByRole("normal");

        // 1. Gatherer-Critic Loop to collect product capability information
        String productContext = RepeatUntilAcceptableBuilder
                .returning(String.class)
                .withMaxIterations(10)
                .withScoreThreshold(0.8)
                .repeating(loopCtx -> {
                    var lastAttempt = loopCtx.lastAttempt();
                    String lastFindings = lastAttempt != null ? lastAttempt.getResult() : "No previous findings.";
                    String feedback = lastAttempt != null ? lastAttempt.getFeedback().toString() : "Initial search.";

                    String prompt = String.format("""
                        # TASK
                        Search 'product_knowledge' to find internal product capabilities matching the CRS.
                        
                        # OBJECTIVE
                        You must accumulate knowledge. Do NOT discard previous findings.
                        Address the critic's feedback to investigate specific gaps or features more deeply.
                        
                        # CRS
                        %s
                        
                        # PREVIOUS FINDINGS (Preserve and Expand this)
                        %s
                        
                        # CRITIC FEEDBACK
                        %s
                        
                        # OUTPUT
                        Return a comprehensive, aggregated capability summary including both previous findings and new discoveries.
                        """, req.crsContent(), lastFindings, feedback);

                    return simpleAi.generateText(prompt);
                })
                .withEvaluator(loopCtx -> {
                    String prompt = String.format("""
                        Review the gathered product context against the CRS.
                        Is the information sufficient to perform a detailed gap analysis (Supported/Partial/Unsupported) and effort estimation?
                        
                        # CRS:
                        %s
                        
                        # Gathered Product Context:
                        %s
                        """, req.crsContent(), loopCtx.getResultToEvaluate());

                    return normalAi.createObject(prompt, TextFeedback.class);
                })
                .build()
                .asSubProcess(ctx, String.class);

        // 2. Normal AI Worker performs gap analysis and generates final report
        String analysisPrompt = String.format("""
            Analyze the CRS against the provided product context.
            
            # Tasks:
            1. **Gap Analysis**: Categorize each requirement as 'Supported', 'Partially Supported', or 'Unsupported'.
            2. **Effort Estimation**: Estimate M/M for gaps/customizations with technical justification.
            3. **Customer Questions**: List ambiguous points.
            
            # CRS:
            %s
            
            # Product Context:
            %s
            """, req.crsContent(), productContext);

        String analysis = normalAi.generateText(analysisPrompt);

        String finalReportPrompt = String.format("""
            Draft a professional final response to the customer in Korean.
            
            # Original Language: %s
            # Analysis Result:
            %s
            
            # Instructions:
            1. Summarize the proposal and highlight value.
            2. Be professional and helpful.
            3. Address clarifications politely.
            4. Do NOT include detailed M/M unless necessary.
            """, req.originalLanguage(), analysis);

        String finalReport = normalAi.generateText(finalReportPrompt);

        String questionPrompt = "Extract ONLY the customer questions from the analysis result into a bulleted list: \n\n" + analysis;
        String questions = normalAi.generateText(questionPrompt);

        return new AnalysisResult(analysis, questions, finalReport);
    }
}
