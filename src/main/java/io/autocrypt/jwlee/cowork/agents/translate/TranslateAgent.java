package io.autocrypt.jwlee.cowork.agents.translate;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.embabel.agent.api.annotation.AchievesGoal;
import com.embabel.agent.api.annotation.Action;
import com.embabel.agent.api.annotation.Agent;
import com.embabel.agent.api.annotation.RunSubagent;
import com.embabel.agent.api.annotation.State;
import com.embabel.agent.api.common.ActionContext;
import com.embabel.agent.api.common.Ai;
import com.embabel.agent.prompt.persona.RoleGoalBackstory;
import com.embabel.common.ai.model.LlmOptions;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.autocrypt.jwlee.cowork.agents.anki.AnkiAgent;
import io.autocrypt.jwlee.cowork.core.hitl.ApplicationContextHolder;
import io.autocrypt.jwlee.cowork.core.hitl.NotificationEvent;
import io.autocrypt.jwlee.cowork.core.tools.LocalRagTools;
import io.autocrypt.jwlee.cowork.core.tools.PdfParser;

@Agent(description = "PDF 전문 번역 에이전트")
@Component
public class TranslateAgent {

    private final PdfParser parser;
    private final TranslateWorkspace workspace;
    private final ObjectMapper objectMapper;
    private final AnkiAgent ankiAgent;
    private final LocalRagTools localRagTools;
    private final RoleGoalBackstory translatorPersona;

    public TranslateAgent(PdfParser parser, TranslateWorkspace workspace, ObjectMapper objectMapper, AnkiAgent ankiAgent, LocalRagTools localRagTools) {
        this.parser = parser;
        this.workspace = workspace;
        this.objectMapper = objectMapper;
        this.ankiAgent = ankiAgent;
        this.localRagTools = localRagTools;
        this.translatorPersona = new RoleGoalBackstory(
            "Senior Technical Translator",
            "Accurately translate technical documents from English to Korean while maintaining strict terminology and formatting.",
            """
            **Tone and Style Guidelines:**
            - Maintain a formal, professional, and objective tone.
            - Use plain statements for body text.
            - Use noun-based endings for definitions, list items, and headings to improve readability.
            - Ensure that the logical structure and hierarchical relationship of concepts are preserved.
            
            **Markdown Formatting:**
            - Strictly maintain all original markdown syntax (#, ##, bold, italics, lists, blockquotes).
            - Preserve the structure and labels of Figures and Tables.
            - Ensure LaTeX-style formulas (if any) are kept intact.
            
            **Korean Document Standards:**
            - Do not put a period (.) at the end of items in a bulleted or numbered list.
            - Output ONLY the translated text without any meta-comments, introductory remarks, or concluding explanations.
            """
        );
    }

    public record TranslateStartRequest(String pdfPath, String workspaceName) {}
    public record TranslateResumeRequest(String workspaceName) {}

    public record DocumentContext(String summary, String toc, Map<String, String> glossary, List<String> boilerplatePatterns) {}
    public record TranslationResult(String message) {}

    @State
    public interface Stage {}

    @Action
    public AnkiAgent.AnkiStartRequest start(TranslateStartRequest req, ActionContext ctx) throws IOException {
        Path wsPath = workspace.initWorkspace(req.workspaceName());
        
        TranslateWorkspace.TranslateState state = new TranslateWorkspace.TranslateState();
        state.setOriginalPdfPath(req.pdfPath());
        state.setCurrentPhase(TranslateWorkspace.TranslateState.Phase.CONTEXT_EXTRACTION);
        workspace.saveState(wsPath, state);

        System.out.println("Ingesting document into RAG for glossary context...");
        Path ragPath = wsPath.resolve("rag");
        localRagTools.ingestUrlAt(req.pdfPath(), req.workspaceName(), ragPath);

        System.out.println("Initiating glossary generation via AnkiAgent subagent (Target: 100 terms)...");
        
        return new AnkiAgent.AnkiStartRequest(
            Path.of(req.pdfPath()),
            req.workspaceName(),
            ragPath,
            100 
        );
    }

    @Action
    public AnkiAgent.AnkiResult runAnkiSubagent(AnkiAgent.AnkiStartRequest req) {
        return RunSubagent.fromAnnotatedInstance(
            ankiAgent,
            AnkiAgent.AnkiResult.class
        );
    }

    @Action
    public Stage handleAnkiResult(AnkiAgent.AnkiResult result, TranslateStartRequest startReq, Ai ai) throws IOException {
        Path wsPath = workspace.initWorkspace(startReq.workspaceName());
        TranslateWorkspace.TranslateState state = workspace.loadState(wsPath);
        
        System.out.println("Processing AnkiAgent result and preparing translation chunks...");
        
        // 1. Prepare glossary
        String formattedGlossary = result.terms().stream().collect(Collectors.joining("; "));
        String prompt = String.format("""
            Based on the following document summary and key terminology, identify the Table of Contents (TOC) if present and common Boilerplate Patterns (headers, footers, etc.).
            
            # Summary:
            %s
            
            # Key Terminology:
            %s
            
            # Task:
            1. Extract the Table of Contents (TOC) if present.
            2. Identify 'Boilerplate Patterns' (repeating meta-text).
            
            # Output:
            Provide DocumentContext matching the requested schema. Use the summary provided below.
            """, result.summary(), formattedGlossary);

        DocumentContext partialContext = ai.withLlm(LlmOptions.withLlmForRole("normal").withoutThinking())
            .creating(DocumentContext.class).fromPrompt(prompt);

        Map<String, String> glossaryMap = result.terms().stream().map(t -> {
            int parenIndex = t.lastIndexOf("(");
            if (parenIndex != -1 && t.endsWith(")")) {
                String en = t.substring(0, parenIndex).trim();
                String ko = t.substring(parenIndex + 1, t.length() - 1).trim();
                return Map.entry(en, ko);
            }
            return Map.entry(t, t);
        }).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (v1, v2) -> v1));

        DocumentContext docContext = new DocumentContext(
            result.summary(),
            partialContext.toc(),
            glossaryMap,
            partialContext.boilerplatePatterns()
        );

        // 2. Save glossary for persistence
        String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(docContext);
        workspace.saveGlossary(wsPath, json);

        // 3. Parse full PDF and chunk elements immediately
        File pdfFile = new File(state.getOriginalPdfPath());
        System.out.println("Parsing full PDF structure...");
        List<PdfParser.PdfElement> elements = parser.parsePdf(pdfFile, wsPath.resolve("images"));
        
        String fullMarkdown = elements.get(0).content();
        List<String> chunks = chunkElements(fullMarkdown);
        
        state.setTotalChunks(chunks.size());
        state.setCompletedChunks(0);
        state.setCurrentPhase(TranslateWorkspace.TranslateState.Phase.CHUNK_TRANSLATION);
        workspace.saveState(wsPath, state);

        for (int i = 0; i < chunks.size(); i++) {
            workspace.saveTranslatedChunk(wsPath, i + 1000, chunks.get(i));
        }

        System.out.println("Translation chunks prepared. Starting translation loop...");
        return new TranslationLoopState(wsPath, docContext, state, workspace, translatorPersona);
    }

    private List<String> chunkElements(String fullMarkdown) {
        List<String> chunks = new ArrayList<>();
        String[] blocks = fullMarkdown.split("\\n{2,}");
        
        StringBuilder currentChunk = new StringBuilder();
        int MAX_CHUNK_CHARS = 5000;
        
        for (String block : blocks) {
            String trimmedBlock = block.trim();
            if (trimmedBlock.isEmpty()) continue;
            
            if (currentChunk.length() + trimmedBlock.length() + 2 > MAX_CHUNK_CHARS && !currentChunk.isEmpty()) {
                chunks.add(currentChunk.toString().trim());
                currentChunk.setLength(0);
            }
            currentChunk.append(trimmedBlock).append("\n\n");
        }
        
        if (!currentChunk.isEmpty()) {
            chunks.add(currentChunk.toString().trim());
        }
        return chunks;
    }

    @Action
    public Stage resume(TranslateResumeRequest req) {
        try {
            Path wsPath = Path.of(req.workspaceName());
            TranslateWorkspace.TranslateState state = workspace.loadState(wsPath);
            
            if (state.getCurrentPhase() == TranslateWorkspace.TranslateState.Phase.CHUNK_TRANSLATION) {
                String glossaryJson = workspace.readGlossary(wsPath);
                DocumentContext ctx = objectMapper.readValue(glossaryJson, DocumentContext.class);
                return new TranslationLoopState(wsPath, ctx, state, workspace, translatorPersona);
            } else if (state.getCurrentPhase() == TranslateWorkspace.TranslateState.Phase.MERGE_AND_POSTPROCESS) {
                return new MergeState(wsPath, state, workspace);
            }
            throw new IllegalStateException("Cannot resume from phase: " + state.getCurrentPhase());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @State
    public record TranslationLoopState(Path wsPath, DocumentContext context, TranslateWorkspace.TranslateState state, TranslateWorkspace workspace, RoleGoalBackstory translatorPersona) implements Stage {
        @Action(canRerun = true, clearBlackboard = true)
        public Stage processChunk(Ai ai) {
            try {
                int currentIndex = state.getCompletedChunks();
                if (currentIndex >= state.getTotalChunks()) {
                    state.setCurrentPhase(TranslateWorkspace.TranslateState.Phase.MERGE_AND_POSTPROCESS);
                    workspace.saveState(wsPath, state);
                    return new MergeState(wsPath, state, workspace);
                }

                String sourceChunk = workspace.readTranslatedChunk(wsPath, currentIndex + 1000);
                
                String previousContext = "";
                if (currentIndex > 0) {
                    String lastTranslated = workspace.readTranslatedChunk(wsPath, currentIndex - 1);
                    if (lastTranslated != null) {
                        int contextLen = 1000;
                        previousContext = lastTranslated.length() > contextLen ? 
                            lastTranslated.substring(lastTranslated.length() - contextLen) : lastTranslated;
                    }
                }

                System.out.println("Translating chunk " + (currentIndex + 1) + " of " + state.getTotalChunks() + "...");

                String prompt = String.format("""
                    You are an expert technical translator. Translate the following text from English to professional Korean.
                    The source text is long. Translate ALL of it completely without stopping. Do not truncate or summarize. Translate every single line.
                    If the source text contains unnatural line breaks in the middle of sentences (common in old PDF documents), merge them into natural flowing sentences in the translation output.

                    # Instructions:
                    1. **Tone and Style**: Use formal, professional Korean suitable for technical standards or manuals.
                    2. **Glossary**: Strictly adhere to the provided glossary for terminology translation.
                    3. **Markup Preservation**: Do NOT alter, translate, or remove any markdown syntax.
                    4. **Context**: Consider the provided document summary and the previous translation context to maintain consistency in style and terminology.
                    5. **No Brackets for Glossary**: Translate terms naturally. Do NOT surround translated terms with brackets like [Term](Term).

                    # Document Context:
                    %s

                    # Glossary:
                    %s

                    # Previous Translation Context (for consistency):
                    ... %s

                    # Source Text (To be translated):
                    %s

                    Provide ONLY the translated markdown text.
                    """, context.summary(), context.glossary(), previousContext, sourceChunk);

                String translated = ai.withLlm(LlmOptions.withLlmForRole("normal").withoutThinking())
                    .withPromptContributor(translatorPersona)
                    .generateText(prompt);

                workspace.saveTranslatedChunk(wsPath, currentIndex, translated);
                
                state.setCompletedChunks(currentIndex + 1);
                workspace.saveState(wsPath, state);

                return new TranslationLoopState(wsPath, context, state, workspace, translatorPersona);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    @State
    public record MergeState(Path wsPath, TranslateWorkspace.TranslateState state, TranslateWorkspace workspace) implements Stage {
        @Action
        @AchievesGoal(description = "Translation completely merged and saved.")
        public TranslationResult merge() {
            try {
                System.out.println("Merging chunks...");
                StringBuilder fullText = new StringBuilder();
                for (int i = 0; i < state.getTotalChunks(); i++) {
                    fullText.append(workspace.readTranslatedChunk(wsPath, i)).append("\n\n");
                }
                
                File outputFile = wsPath.resolve("final_translated.md").toFile();
                java.nio.file.Files.writeString(outputFile.toPath(), fullText.toString());
                
                state.setCurrentPhase(TranslateWorkspace.TranslateState.Phase.DONE);
                workspace.saveState(wsPath, state);
                
                System.out.println("✅ Translation completed successfully: " + outputFile.getAbsolutePath());
                ApplicationContextHolder.getPublisher().publishEvent(
                    new NotificationEvent("PDF 번역 완료", "번역 및 머지가 완료되었습니다: " + outputFile.getName())
                );
                return new TranslationResult("Successfully translated and merged to " + outputFile.getAbsolutePath());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }
}
