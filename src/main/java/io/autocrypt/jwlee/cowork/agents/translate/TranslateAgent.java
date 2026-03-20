package io.autocrypt.jwlee.cowork.agents.translate;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.embabel.agent.api.annotation.AchievesGoal;
import com.embabel.agent.api.annotation.Action;
import com.embabel.agent.api.annotation.Agent;
import com.embabel.agent.api.annotation.State;
import com.embabel.agent.api.common.ActionContext;
import com.embabel.agent.api.common.Ai;
import com.embabel.agent.core.hitl.WaitFor;
import com.embabel.agent.prompt.persona.RoleGoalBackstory;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.autocrypt.jwlee.cowork.core.hitl.ApplicationContextHolder;
import io.autocrypt.jwlee.cowork.core.hitl.ApprovalDecision;
import io.autocrypt.jwlee.cowork.core.hitl.ApprovalRequestedEvent;
import io.autocrypt.jwlee.cowork.core.hitl.NotificationEvent;

@Agent(description = "PDF 전문 번역 에이전트")
@Component
public class TranslateAgent {

    private final TranslatePdfParser parser;
    private final TranslateWorkspace workspace;
    private final ObjectMapper objectMapper;
    private final RoleGoalBackstory translatorPersona;

    public TranslateAgent(TranslatePdfParser parser, TranslateWorkspace workspace, ObjectMapper objectMapper) {
        this.parser = parser;
        this.workspace = workspace;
        this.objectMapper = objectMapper;
        this.translatorPersona = new RoleGoalBackstory(
            "Senior Technical Translator",
            "Accurately translate technical documents from English to Korean while maintaining strict terminology and formatting.",
            """
            **Tone and Style Guidelines:**
            - Maintain a formal, professional, and objective tone.
            - Use plain statements (~한다, ~이다) for body text.
            - Use noun-based endings (~함, ~음, ~임) for definitions, list items, and headings to improve readability.
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
    public Stage start(TranslateStartRequest req, Ai ai, ActionContext ctx) {
        try {
            Path wsPath = workspace.initWorkspace(req.workspaceName());
            File pdfFile = new File(req.pdfPath());
            
            TranslateWorkspace.TranslateState state = new TranslateWorkspace.TranslateState();
            state.setOriginalPdfPath(req.pdfPath());
            state.setCurrentPhase(TranslateWorkspace.TranslateState.Phase.CONTEXT_EXTRACTION);
            workspace.saveState(wsPath, state);

            System.out.println("Extracting early pages for LLM context analysis...");
            String initialText = parser.extractInitialPagesForLlm(pdfFile, 5);
            
            String prompt = String.format("""
                You are an expert technical translator and domain specialist.
                Analyze the following introductory pages of a technical document (e.g., ISO standards, technical manuals).
                
                # Task
                1. Identify the core subject and context of the document.
                2. Extract the Table of Contents (TOC) if present.
                3. Generate a Glossary of key technical terms, mapping English terms to their standard Korean translations. Pay special attention to domain-specific terminology.
                4. Identify 'Boilerplate Patterns': Look for repeating headers, footers, license texts, or copyright notices that appear as isolated text blocks across pages (not part of the main body text). Output them as a list of EXACT strings or regex patterns that match the ENTIRE block. DO NOT output overly generic terms (like just 'ISO') that might legitimately appear in the middle of a sentence.
                
                # Document Content:
                %s
                
                # Output Format (JSON):
                Provide the output matching the requested schema.
                """, initialText);

            System.out.println("Generating glossary and context...");
            DocumentContext docContext = ai.withLlmByRole("performant")
                .withPromptContributor(translatorPersona)
                .creating(DocumentContext.class).fromPrompt(prompt);

            return new ReviewGlossaryState(wsPath, docContext, workspace, parser, objectMapper, translatorPersona);
        } catch (Exception e) {
            ApplicationContextHolder.getPublisher().publishEvent(
                new NotificationEvent("번역 시작 실패", "초기 설정 또는 컨텍스트 추출 중 오류가 발생했습니다: " + e.getMessage())
            );
            throw new RuntimeException(e);
        }
    }

    @Action
    public Stage resume(TranslateResumeRequest req) {
        try {
            Path wsPath = Path.of(req.workspaceName());
            TranslateWorkspace.TranslateState state = workspace.loadState(wsPath);
            
            if (state.getCurrentPhase() == TranslateWorkspace.TranslateState.Phase.GLOSSARY_WAITING) {
                return new FileGlossaryWaitState(wsPath, state, workspace, parser, objectMapper, translatorPersona);
            } else if (state.getCurrentPhase() == TranslateWorkspace.TranslateState.Phase.CHUNK_TRANSLATION) {
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
    public record ReviewGlossaryState(Path wsPath, DocumentContext context, TranslateWorkspace workspace, TranslatePdfParser parser, ObjectMapper objectMapper, RoleGoalBackstory translatorPersona) implements Stage {
        @Action
        public ApprovalDecision waitForFeedback(ActionContext ctx) {
            String processId = ctx.getProcessContext().getAgentProcess().getId();
            
            StringBuilder preview = new StringBuilder();
            preview.append("**요약(Summary):**\n").append(context.summary()).append("\n\n");
            preview.append("**용어집(Glossary) 초안:**\n");
            if (context.glossary() != null) {
                context.glossary().forEach((k, v) -> preview.append("- ").append(k).append(": ").append(v).append("\n"));
            }
            preview.append("\n**보일러플레이트 패턴(Boilerplate):**\n");
            if (context.boilerplatePatterns() != null) {
                context.boilerplatePatterns().forEach(p -> preview.append("- ").append(p).append("\n"));
            }
            
            ApplicationContextHolder.getPublisher().publishEvent(
                new ApprovalRequestedEvent(processId, "1차 용어집 검토: 내용을 승인(Y)하거나 추가 수정 지시(N)를 남겨주세요.", preview.toString())
            );
            return WaitFor.formSubmission("1차 터미널 피드백 대기", ApprovalDecision.class);
        }

        @Action(clearBlackboard = true)
        public Stage processFeedback(ApprovalDecision decision, Ai ai) {
            try {
                if (decision.approved()) {
                    String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(context);
                    workspace.saveGlossary(wsPath, json);
                    
                    TranslateWorkspace.TranslateState state = workspace.loadState(wsPath);
                    state.setCurrentPhase(TranslateWorkspace.TranslateState.Phase.GLOSSARY_WAITING);
                    workspace.saveState(wsPath, state);

                    return new FileGlossaryWaitState(wsPath, state, workspace, parser, objectMapper, translatorPersona);
                } else {
                    String prompt = String.format("""
                        사용자 피드백을 반영하여 용어집 및 맥락을 수정하세요.
                        
                        # 피드백: %s
                        
                        # 이전 맥락:
                        - 요약: %s
                        - 용어집: %s
                        - 보일러플레이트 패턴: %s
                        
                        수정된 JSON 결과를 생성하세요.
                        """, decision.comment(), context.summary(), context.glossary(), context.boilerplatePatterns());
                    
                    DocumentContext revised = ai.withLlmByRole("normal")
                        .creating(DocumentContext.class).fromPrompt(prompt);
                    
                    return new ReviewGlossaryState(wsPath, revised, workspace, parser, objectMapper, translatorPersona);
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    @State
    public record FileGlossaryWaitState(Path wsPath, TranslateWorkspace.TranslateState state, TranslateWorkspace workspace, TranslatePdfParser parser, ObjectMapper objectMapper, RoleGoalBackstory translatorPersona) implements Stage {
        @Action
        public ApprovalDecision waitForFileEdit(ActionContext ctx) {
            String processId = ctx.getProcessContext().getAgentProcess().getId();
            ApplicationContextHolder.getPublisher().publishEvent(
                new ApprovalRequestedEvent(processId, "2차 파일 검수: " + wsPath.resolve("glossary.json") + " 파일을 편하게 수정하신 후, 완료되면 승인(Y)을 눌러주세요.", "File is ready for manual edit.")
            );
            return WaitFor.formSubmission("2차 파일 검수 대기", ApprovalDecision.class);
        }

        @Action(clearBlackboard = true)
        public Stage proceed(ApprovalDecision decision) {
            try {
                String glossaryJson = workspace.readGlossary(wsPath);
                DocumentContext context = objectMapper.readValue(glossaryJson, DocumentContext.class);
                
                state.setCurrentPhase(TranslateWorkspace.TranslateState.Phase.CHUNK_TRANSLATION);
                
                File pdfFile = new File(state.getOriginalPdfPath());
                System.out.println("Parsing full PDF structure...");
                List<TranslatePdfParser.PdfElement> elements = parser.parsePdf(pdfFile, wsPath.resolve("images"), context.boilerplatePatterns());
                
                // Parser returns a single element with type 'markdown'
                String fullMarkdown = elements.get(0).content();
                List<String> chunks = chunkElements(fullMarkdown);
                
                state.setTotalChunks(chunks.size());
                state.setCompletedChunks(0);
                workspace.saveState(wsPath, state);

                for (int i = 0; i < chunks.size(); i++) {
                    workspace.saveTranslatedChunk(wsPath, i + 1000, chunks.get(i));
                }

                return new TranslationLoopState(wsPath, context, state, workspace, translatorPersona);
            } catch (Exception e) {
                ApplicationContextHolder.getPublisher().publishEvent(
                    new NotificationEvent("PDF 파싱 실패", "PDF 구조 분석 중 오류가 발생했습니다: " + e.getMessage())
                );
                throw new RuntimeException(e);
            }
        }

        private List<String> chunkElements(String fullMarkdown) {
            List<String> chunks = new ArrayList<>();
            String[] blocks = fullMarkdown.split("\\n{2,}");
            
            StringBuilder currentChunk = new StringBuilder();
            int MAX_CHUNK_CHARS = 5000;
            
            for (String block : blocks) {
                String trimmedBlock = block.trim();
                if (trimmedBlock.isEmpty()) continue;
                
                // +2 for \n\n
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
                    1. **Tone and Style**: Use formal, professional Korean suitable for technical standards or manuals (e.g., end sentences with '~함', '~임', '~이다', '~한다').
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

                String translated = ai.withLlmByRole("normal")
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
