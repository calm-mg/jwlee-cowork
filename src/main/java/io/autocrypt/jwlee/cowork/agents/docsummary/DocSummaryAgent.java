package io.autocrypt.jwlee.cowork.agents.docsummary;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import org.jline.terminal.Terminal;
import org.springframework.stereotype.Component;

import com.embabel.agent.api.annotation.AchievesGoal;
import com.embabel.agent.api.annotation.Action;
import com.embabel.agent.api.annotation.Agent;
import com.embabel.agent.api.annotation.State;
import com.embabel.agent.api.common.ActionContext;
import com.embabel.common.ai.model.LlmOptions;

import io.autocrypt.jwlee.cowork.core.tools.CoreFileTools;
import io.autocrypt.jwlee.cowork.core.tools.CoreWorkspaceProvider;
import io.autocrypt.jwlee.cowork.core.tools.LocalRagTools;
import io.autocrypt.jwlee.cowork.core.tools.PdfParser;
import io.autocrypt.jwlee.cowork.core.workaround.JsonSafeToolishRag;

@Agent(description = "Extracts key terms and concepts from documents and provides summaries.")
@Component
public class DocSummaryAgent {

    private final PdfParser pdfParser;
    private final CoreFileTools fileTools;
    private final LocalRagTools localRagTools;
    private final DocSummaryWorkspace workspace;
    private final Terminal terminal;

    public DocSummaryAgent(PdfParser pdfParser, CoreFileTools fileTools, LocalRagTools localRagTools, 
                     DocSummaryWorkspace workspace, Terminal terminal) {
        this.pdfParser = pdfParser;
        this.fileTools = fileTools;
        this.localRagTools = localRagTools;
        this.workspace = workspace;
        this.terminal = terminal;
    }

    private void logToTerminal(String message) {
        terminal.writer().println("[DocSummaryAgent] " + message);
        terminal.writer().flush();
    }

    // --- DTOs ---

    public record DocSummaryRequest(Path filePath, String workspaceName, int maxTerms) {}
    public record DocSummaryResumeRequest(String workspaceName, int maxTerms) {}

    public record DocSummaryOverview(String summary, List<String> initialTerms) {}

    public record ScoredTerm(String term, double score) {}

    public record RawTerms(List<ScoredTerm> terms) {}

    public record DefinedTerm(String term, String definition) {}

    public record TermList(List<DefinedTerm> terms) {}

    public record DocSummaryResult(String summary, List<DefinedTerm> terms) {}

    @State
    public record InitialState(DocSummaryRequest request, Path wsPath, String fullMarkdown, DocSummaryOverview overview, List<ScoredTerm> accumulatedTerms) {}

    @State
    public record ExtractedState(String workspaceName, Path wsPath, int maxTerms, String fullMarkdown, DocSummaryOverview overview, List<ScoredTerm> finalTerms) {}

    // --- Actions ---

    @Action
    public InitialState start(DocSummaryRequest req, ActionContext ctx) throws IOException {
        Path filePath = req.filePath().toAbsolutePath().normalize();
        if (!java.nio.file.Files.exists(filePath)) {
            throw new java.io.FileNotFoundException("Source file not found: " + filePath);
        }

        Path wsPath = workspace.initWorkspace(req.workspaceName());
        
        String markdown;
        if (filePath.toString().toLowerCase().endsWith(".pdf")) {
            logToTerminal("Parsing PDF: " + filePath);
            Path imagesPath = wsPath.resolve(CoreWorkspaceProvider.SubCategory.ARTIFACTS.getDirName()).resolve("images");
            if (!java.nio.file.Files.exists(imagesPath)) java.nio.file.Files.createDirectories(imagesPath);
            markdown = pdfParser.parsePdfToMarkdown(filePath.toFile(), imagesPath);
        } else {
            markdown = fileTools.readFile(filePath.toString()).content();
        }

        logToTerminal("Extracting overview and initial terms...");
        
        String sample = markdown.length() > 5000 ? markdown.substring(0, 5000) : markdown;
        
        DocSummaryOverview overview = ctx.ai().withLlm(LlmOptions.withLlmForRole("simple").withoutThinking())
                .creating(DocSummaryOverview.class)
                .fromPrompt(String.format("""
                        Analyze the following document and provide a high-level summary and 5-10 core technical terms.
                        
                        # DOCUMENT CONTENT (PARTIAL)
                        %s
                        
                        # OUTPUT INSTRUCTIONS
                        1. Provide a summary for LLM reference.
                        2. Extract 5-10 core technical terms. 
                           - **LANGUAGE**: English only.
                           - **FORMAT**: Title Case (e.g., "Digital Signature", "Asymmetric Encryption").
                        """, sample));

        List<ScoredTerm> initialScored = overview.initialTerms().stream()
                .map(t -> new ScoredTerm(t.trim(), 1.0))
                .collect(Collectors.toList());

        return new InitialState(req, wsPath, markdown, overview, initialScored);
    }

    @Action
    public ExtractedState resume(DocSummaryResumeRequest req) throws IOException {
        Path wsPath = workspace.initWorkspace(req.workspaceName());
        logToTerminal("Resuming from workspace: " + wsPath);
        
        ExtractedState saved = workspace.loadState(wsPath);
        return new ExtractedState(saved.workspaceName(), saved.wsPath(), 
                                  req.maxTerms(), saved.fullMarkdown(), saved.overview(), saved.finalTerms());
    }

    @Action
    public ExtractedState extractSequentially(InitialState state, ActionContext ctx) throws IOException {
        int chunkSize = 15000;
        List<String> chunks = splitText(state.fullMarkdown(), chunkSize);
        logToTerminal(String.format("Scanning document in %d segments...", chunks.size()));
        
        List<ScoredTerm> allScoredTerms = new ArrayList<>(state.accumulatedTerms());

        for (int i = 0; i < chunks.size(); i++) {
            final int chunkIdx = i;
            String chunk = chunks.get(i);
            
            String existingTermsSample = allScoredTerms.stream()
                    .filter(st -> st.score() >= 0.8)
                    .skip(Math.max(0, (long) (allScoredTerms.stream().filter(st -> st.score() >= 0.8).count() - 50)))
                    .map(ScoredTerm::term)
                    .collect(Collectors.joining(", "));

            RawTerms newScoredTerms = ctx.ai().withLlm(LlmOptions.withLlmForRole("simple").withoutThinking())
                    .creating(RawTerms.class)
                    .fromPrompt(String.format("""
                            # TASK
                            Extract technical terms, acronyms, and core concepts from this segment and assign an **Importance Score (0.0 to 1.0)** to each.

                            # SCORING CRITERIA
                            - **0.9 - 1.0 (Critical):** Terms that are fundamental to the document's core subject. This includes primary protocols, system architectures, or concepts without which the document cannot be understood.
                            - **0.7 - 0.8 (Important):** Key supporting concepts, specific security mechanisms, major data structures, or essential operational procedures. These are important for a deep understanding but are not the absolute foundation.
                            - **0.4 - 0.6 (Secondary):** Specific parameters, minor fields, optional sub-components, or niche implementation details.
                            - **0.1 - 0.3 (Trivial):** Generic IT terminology, common English words used in a technical context, repetitive boilerplate, or irrelevant details.

                            # DOCUMENT SUMMARY (Use this for scoring context)
                            %s

                            # DOCUMENT SEGMENT (%d/%d)
                            %s

                            # PREVIOUSLY EXTRACTED HIGH-IMPORTANCE TERMS (Do not repeat these)
                            %s

                            # INSTRUCTIONS
                            1. Extract **at most 25** high-quality terms strictly in **English**.
                            2. **Only extract terms that merit a score HIGHER than 0.6**. If a term is less important, ignore it.
                            3. Use **Title Case** for all terms (e.g., "Advanced Encryption Standard").
                            4. Do not include Korean translations in this phase.
                            5. <example>{"term": "Digital Signature", "score": 0.95}</example>
                            """, state.overview().summary(), chunkIdx + 1, chunks.size(), chunk, existingTermsSample));            
            if (newScoredTerms.terms() != null) {
                int addedCount = 0;
                for (ScoredTerm st : newScoredTerms.terms()) {
                    String normalizedTerm = st.term().trim();
                    if (st.score() > 0.6 && allScoredTerms.stream().noneMatch(e -> e.term().equalsIgnoreCase(normalizedTerm))) {
                        allScoredTerms.add(new ScoredTerm(normalizedTerm, st.score()));
                        addedCount++;
                    }
                }
                logToTerminal(String.format("Segment %d/%d: Added %d terms. (Cumulative: %d)", 
                        chunkIdx + 1, chunks.size(), addedCount, allScoredTerms.size()));
            }
        }

        ExtractedState finalState = new ExtractedState(
                state.request().workspaceName(), state.wsPath(), 
                state.request().maxTerms(), state.fullMarkdown(), state.overview(), allScoredTerms);
        
        workspace.saveAllTerms(state.wsPath(), allScoredTerms);
        workspace.saveState(state.wsPath(), finalState);

        return finalState;
    }

    @Action
    public RawTerms filterByRank(ExtractedState state) {
        List<ScoredTerm> topTerms = state.finalTerms().stream()
                .sorted(Comparator.comparingDouble(ScoredTerm::score).reversed())
                .limit(state.maxTerms())
                .collect(Collectors.toList());
        return new RawTerms(topTerms);
    }

    @Action
    public TermList finalizeDefinitions(RawTerms refinedTerms, ExtractedState state, ActionContext ctx) throws IOException {
        logToTerminal(String.format("Defining %d terms using RAG...", refinedTerms.terms().size()));

        var searchOps = localRagTools.getOrOpenMemoryInstance(state.workspaceName());
        var rag = new JsonSafeToolishRag("doc_knowledge", "Knowledge base from the source document", searchOps);

        List<DefinedTerm> finalTerms = new ArrayList<>();
        
        int batchSize = 5;
        for (int i = 0; i < refinedTerms.terms().size(); i += batchSize) {
            int end = Math.min(i + batchSize, refinedTerms.terms().size());
            List<String> batch = refinedTerms.terms().subList(i, end).stream().map(ScoredTerm::term).toList();
            
            try {
                TermList definedBatch = ctx.ai().withLlm(LlmOptions.withLlmForRole("simple").withoutThinking())
                        .withReference(rag)
                        .creating(TermList.class)
                        .fromPrompt(String.format("""
                        # TASK
                        Provide Korean translations and concise Korean definitions for the provided English terms.

                        # DOCUMENT OVERVIEW
                        %s
                        
                        # ENGLISH TERMS
                        %s
                        
                        # INSTRUCTIONS
                        1. For each term, provide:
                           - **term**: "English Term (Korean Translation)"
                           - **definition**: A concise Korean explanation.
                        2. <example>
                           Term: Digital Signature (디지털 서명)
                           Definition: 메시지의 무결성과 발신자의 신원을 증명하기 위해 사용되는 전자적 서명 기술.
                           </example>
                        """, state.overview().summary(), String.join(", ", batch)));
                
                if (definedBatch.terms() != null) {
                    finalTerms.addAll(definedBatch.terms());
                }
            } catch (Exception e) {
                logToTerminal("Batch failed: " + e.getMessage());
            }
        }

        return new TermList(finalTerms);
    }

    @AchievesGoal(description = "Document summary and terms extracted")
    @Action
    public DocSummaryResult finalizeResult(TermList terms, ExtractedState state) {
        return new DocSummaryResult(state.overview().summary(), terms.terms());
    }

    private List<String> splitText(String text, int chunkSize) {
        List<String> chunks = new ArrayList<>();
        int length = text.length();
        if (length == 0) return chunks;
        for (int i = 0; i < length; i += chunkSize) {
            chunks.add(text.substring(i, Math.min(length, i + chunkSize)));
        }
        return chunks;
    }
}
