package io.autocrypt.jwlee.cowork.agents.anki;

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

import io.autocrypt.jwlee.cowork.core.hitl.ApplicationContextHolder;
import io.autocrypt.jwlee.cowork.core.hitl.NotificationEvent;
import io.autocrypt.jwlee.cowork.core.tools.CoreFileTools;
import io.autocrypt.jwlee.cowork.core.tools.LocalRagTools;
import io.autocrypt.jwlee.cowork.core.tools.PdfParser;
import io.autocrypt.jwlee.cowork.core.workaround.JsonSafeToolishRag;

@Agent(description = "Extracts key terms and concepts from documents to generate Anki CSV files with Korean definitions.")
@Component
public class AnkiAgent {

    private final PdfParser pdfParser;
    private final CoreFileTools fileTools;
    private final LocalRagTools localRagTools;
    private final AnkiWorkspace workspace;
    private final Terminal terminal;

    public AnkiAgent(PdfParser pdfParser, CoreFileTools fileTools, LocalRagTools localRagTools, 
                     AnkiWorkspace workspace, Terminal terminal) {
        this.pdfParser = pdfParser;
        this.fileTools = fileTools;
        this.localRagTools = localRagTools;
        this.workspace = workspace;
        this.terminal = terminal;
    }

    private void logToTerminal(String message) {
        terminal.writer().println("[AnkiAgent] " + message);
        terminal.writer().flush();
    }

    // --- DTOs ---

    public record AnkiStartRequest(Path filePath, String workspaceName, Path ragPath, int maxCards) {}
    public record AnkiResumeRequest(String workspaceName, int maxCards) {}

    public record AnkiOverview(String summary, List<String> initialTerms) {}

    public record ScoredTerm(String term, double score) {}

    public record RawTerms(List<ScoredTerm> terms) {}

    public record AnkiTerm(String term, String definition) {}

    public record AnkiCardList(List<AnkiTerm> cards) {}

    public record AnkiResult(String csvPath) {}

    @State
    public record InitialState(AnkiStartRequest request, Path wsPath, String fullMarkdown, AnkiOverview overview, List<ScoredTerm> accumulatedTerms) {}

    @State
    public record ExtractedState(String workspaceName, Path wsPath, Path ragPath, int maxCards, String fullMarkdown, AnkiOverview overview, List<ScoredTerm> finalTerms) {}

    // --- Actions ---

    /**
     * Start Phase 1: Initialize and extract overview.
     */
    @Action
    public InitialState start(AnkiStartRequest req, ActionContext ctx) throws IOException {
        Path wsPath = workspace.initWorkspace(req.workspaceName());
        
        String markdown;
        if (req.filePath().toString().toLowerCase().endsWith(".pdf")) {
            logToTerminal("Parsing PDF: " + req.filePath());
            markdown = pdfParser.parsePdfToMarkdown(req.filePath().toFile(), wsPath.resolve("images"));
        } else {
            markdown = fileTools.readFile(req.filePath().toString()).content();
        }

        logToTerminal("Extracting overview and initial terms...");
        
        String sample = markdown.length() > 5000 ? markdown.substring(0, 5000) : markdown;
        
        AnkiOverview overview = ctx.ai().withLlm(LlmOptions.withLlmForRole("simple").withoutThinking())
                .creating(AnkiOverview.class)
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

    /**
     * Resume Phase: Load state from workspace and skip to filtering.
     */
    @Action
    public ExtractedState resume(AnkiResumeRequest req) throws IOException {
        Path wsPath = workspace.initWorkspace(req.workspaceName());
        logToTerminal("Resuming from workspace: " + wsPath);
        
        ExtractedState saved = workspace.loadState(wsPath);
        // Update maxCards from the new request if provided
        return new ExtractedState(saved.workspaceName(), saved.wsPath(), saved.ragPath(), 
                                  req.maxCards(), saved.fullMarkdown(), saved.overview(), saved.finalTerms());
    }

    /**
     * Phase 2: Sequential Content Extraction.
     */
    @Action
    public ExtractedState extractSequentially(InitialState state, ActionContext ctx) throws IOException {
        int chunkSize = 15000;
        List<String> chunks = splitText(state.fullMarkdown(), chunkSize);
        logToTerminal(String.format("Scanning document in %d segments (Phase 2)...", chunks.size()));
        
        List<ScoredTerm> allScoredTerms = new ArrayList<>(state.accumulatedTerms());

        for (int i = 0; i < chunks.size(); i++) {
            final int chunkIdx = i;
            String chunk = chunks.get(i);
            
            // Only include high-score terms (>= 0.8) in context, limited to last 50
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
                logToTerminal(String.format("[Phase 2] Segment %d/%d: Added %d terms. (Cumulative: %d)", 
                        chunkIdx + 1, chunks.size(), addedCount, allScoredTerms.size()));
            }
        }

        ExtractedState finalState = new ExtractedState(
                state.request().workspaceName(), state.wsPath(), state.request().ragPath(), 
                state.request().maxCards(), state.fullMarkdown(), state.overview(), allScoredTerms);
        
        // SAVE ALL TERMS AND STATE
        workspace.saveAllTerms(state.wsPath(), allScoredTerms);
        workspace.saveState(state.wsPath(), finalState);
        logToTerminal("All terms saved to: " + state.wsPath().resolve("all_extracted_terms.json"));

        return finalState;
    }

    /**
     * Phase 3: Rank-based Filtering.
     */
    @Action
    public RawTerms filterByRank(ExtractedState state) {
        logToTerminal(String.format("[Phase 3] Filtering to top %d terms from %d extracted terms...", state.maxCards(), state.finalTerms().size()));

        List<ScoredTerm> topTerms = state.finalTerms().stream()
                .sorted(Comparator.comparingDouble(ScoredTerm::score).reversed())
                .limit(state.maxCards())
                .collect(Collectors.toList());
        
        logToTerminal("Top selection complete.");
        return new RawTerms(topTerms);
    }

    /**
     * Phase 4: Definition Finalization.
     */
    @Action
    public AnkiCardList finalizeDefinitions(RawTerms refinedTerms, ExtractedState state, ActionContext ctx) throws IOException {
        logToTerminal(String.format("[Phase 4] Translating and defining %d terms...", refinedTerms.terms().size()));

        var searchOps = localRagTools.getOrOpenInstance(state.workspaceName(), state.ragPath());
        var rag = new JsonSafeToolishRag("doc_knowledge", "Knowledge base from the source document", searchOps);

        List<AnkiTerm> finalCards = new ArrayList<>();
        
        int batchSize = 5;
        for (int i = 0; i < refinedTerms.terms().size(); i += batchSize) {
            int end = Math.min(i + batchSize, refinedTerms.terms().size());
            List<String> batch = refinedTerms.terms().subList(i, end).stream().map(ScoredTerm::term).toList();
            
            logToTerminal(String.format("[Phase 4] Defining batch %d-%d of %d...", i + 1, end, refinedTerms.terms().size()));
            
            try {
                AnkiCardList definedBatch = ctx.ai().withLlm(LlmOptions.withLlmForRole("simple").withoutThinking())
                        .withReference(rag)
                        .creating(AnkiCardList.class)
                        .fromPrompt(String.format("""
                        # TASK
                        Provide Korean translations and concise Korean definitions for the provided English terms.

                        # DOCUMENT OVERVIEW
                        %s
                        
                        # ENGLISH TERMS
                        %s
                        
                        # INSTRUCTIONS
                        1. For each term, provide a card with:
                           - **term**: "English Term (Korean Translation)"
                           - **definition**: A concise Korean explanation suitable for Anki flashcards.
                        2. <example>
                           Term: Digital Signature (디지털 서명)
                           Definition: 메시지의 무결성과 발신자의 신원을 증명하기 위해 사용되는 전자적 서명 기술.
                           </example>
                        """, state.overview().summary(), String.join(", ", batch)));
                
                if (definedBatch.cards() != null) {
                    finalCards.addAll(definedBatch.cards());
                }
            } catch (Exception e) {
                logToTerminal("Batch failed: " + e.getMessage());
            }
        }

        return new AnkiCardList(finalCards);
    }

    @AchievesGoal(description = "Anki CSV generated and notified")
    @Action
    public AnkiResult generateAnkiCsv(AnkiCardList cards, ExtractedState state) throws IOException {
        logToTerminal(String.format("[Phase 5] Saving %d cards to CSV...", cards.cards().size()));

        StringBuilder csv = new StringBuilder();
        for (AnkiTerm card : cards.cards()) {
            csv.append(escapeCsv(card.term())).append(",").append(escapeCsv(card.definition())).append("\n");
        }

        String filename = String.format("anki/%s_cards_ko.csv", state.workspaceName());
        String savedPath = fileTools.saveGeneratedContent(filename, csv.toString());

        logToTerminal("✅ Success: " + savedPath);
        
        // PUBLISH NOTIFICATION
        ApplicationContextHolder.getPublisher().publishEvent(
            new NotificationEvent("Anki 생성 완료", 
                String.format("'%s' 문서에서 %d개의 카드가 생성되었습니다.", state.workspaceName(), cards.cards().size()))
        );

        return new AnkiResult(savedPath);
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

    private String escapeCsv(String text) {
        if (text == null) return "";
        String escaped = text.replace("\"", "\"\"");
        if (escaped.contains(",") || escaped.contains("\"") || escaped.contains("\n")) {
            return "\"" + escaped + "\"";
        }
        return escaped;
    }
}
