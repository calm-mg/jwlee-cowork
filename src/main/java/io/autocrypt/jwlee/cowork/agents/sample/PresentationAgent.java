package io.autocrypt.jwlee.cowork.agents.sample;

import com.embabel.agent.api.annotation.AchievesGoal;
import com.embabel.agent.api.annotation.Action;
import com.embabel.agent.api.annotation.Agent;
import com.embabel.agent.api.common.Ai;
import com.embabel.agent.api.common.ActionContext;
import com.embabel.agent.core.CoreToolGroups;
import com.embabel.agent.domain.io.UserInput;
import com.embabel.agent.rag.tools.ToolishRag;
import io.autocrypt.jwlee.cowork.service.SlideFileService;
import com.embabel.agent.api.reference.LlmReference;
import com.embabel.agent.prompt.persona.RoleGoalBackstory;
import org.springframework.beans.factory.annotation.Qualifier;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import jakarta.validation.constraints.Pattern;

abstract class PresentationPersonas {
    static final RoleGoalBackstory WRITER = new RoleGoalBackstory(
            "Creative Storyteller",
            "Write engaging and imaginative stories",
            "Has a PhD in French literature; used to work in a circus");
}

@Agent(description = "Advanced Slides professional creator. Uses deterministic wrapping for templates.")
public class PresentationAgent {

    private final ToolishRag localKnowledgeTool;
    private final SlideFileService fileService;
    private final LlmReference presentationExpert;

    public PresentationAgent(
            @Qualifier("directoryRagTool") java.util.Optional<ToolishRag> localKnowledgeTool,
            SlideFileService fileService,
            java.util.Optional<LlmReference> presentationExpert) {
        this.localKnowledgeTool = localKnowledgeTool.orElse(null);
        this.fileService = fileService;
        this.presentationExpert = presentationExpert.orElse(null);
    }

    /**
     * Structured slide content. Logic in toMarkdown() ensures correct formatting without AI retries.
     */
    public record SlidePage(
            int pageNumber,
            String templateName,
            String titleText,
            String leftContent,
            String rightContent,
            String fullWidthContent,
            String footerSource
    ) {
        /**
         * Converts the structured fields into the final markdown format.
         * Ensures the title always starts with '## ' deterministically.
         */
        public String toMarkdown() {
            StringBuilder sb = new StringBuilder();
            
            // Deterministic title fix: ensure it starts with ## 
            String fixedTitle = titleText != null ? titleText.trim() : "";
            if (!fixedTitle.startsWith("##")) {
                fixedTitle = "## " + fixedTitle;
            } else if (fixedTitle.startsWith("##") && !fixedTitle.startsWith("## ")) {
                // Fix cases like "##제목" to "## 제목"
                fixedTitle = "## " + fixedTitle.substring(2).trim();
            }
            
            sb.append("::: title\n").append(fixedTitle).append("\n:::\n\n");

            // Handle content based on template type
            if (templateName != null && (templateName.contains("3-2") || templateName.contains("2-1"))) {
                if (leftContent != null && !leftContent.isBlank()) {
                    sb.append("::: left\n").append(leftContent).append("\n:::\n\n");
                }
                if (rightContent != null && !rightContent.isBlank()) {
                    sb.append("::: right\n").append(rightContent).append("\n:::\n\n");
                }
            }
            
            if (fullWidthContent != null && !fullWidthContent.isBlank()) {
                sb.append("::: block\n").append(fullWidthContent).append("\n:::\n\n");
            }
            
            if (footerSource != null && !footerSource.isBlank()) {
                sb.append("::: source\n").append(footerSource).append("\n:::\n");
            }
            
            return sb.toString();
        }
    }

    public record PresentationSettings(String content) {}
    public record PresentationPlan(String title, String subtitle, List<String> pageTopics) {}
    public record FinalPresentation(String filePath) {}

    @Action
    public PresentationSettings initializeSettings(UserInput input) throws IOException {
        String goldenSettings = """
                ---
                theme: consult
                height: 540
                margin: 0
                maxScale: 4
                mermaid:
                  themeVariables:
                    fontSize: 14px
                  flowchart: 
                    useMaxWidth: false
                    nodeSpacing: 50
                    rankSpacing: 80
                ---
                <style>
                .horizontal_dotted_line{ border-bottom: 2px dotted gray; }
                .small-indent p { margin: 0; }
                .small-indent ul { padding-left: 1em; line-height: 1.3; }
                .small-indent ul > li { padding: 0; }
                ul p { margin-top: 0; }
                .force-center { display: flex !important; flex-direction: column; justify-content: center; align-items: center; width: 100%; height: 100%; text-align: center; }
                </style>
                """;
        fileService.saveSettings(goldenSettings);
        return new PresentationSettings(goldenSettings);
    }

    @Action
    public PresentationPlan planPresentation(UserInput input, PresentationSettings settings, Ai ai) {
        return ai.withAutoLlm()
                // .withPromptContributor(PresentationPersonas.WRITER)
                // .withReference(localKnowledgeTool)
                .createObject(String.format("""
                        Plan a professional presentation structure for: %s. 
                        
                        # OUTPUT REQUIREMENTS:
                        1. Provide a catchy 'title' and a descriptive 'subtitle'.
                        2. List the core 'pageTopics' for the content slides in KOREAN.
                        3. The FIRST topic after the title MUST be "Table of Contents" or "Objectives" (목차/목표).
                        4. DO NOT include the title slide itself in 'pageTopics'.
                        """, input.getContent()), PresentationPlan.class);
    }

    public record SlideList(List<SlidePage> slides) {}

    @Action
    public List<SlidePage> generateAllSlides(PresentationPlan plan, PresentationSettings settings, ActionContext context) throws IOException {
        // 1. Deterministic Title Page (Page 1)
        String today = java.time.LocalDate.now().toString();
        String titleMarkdown = String.format("## %s\n::: block\n#### %s / %s\n:::", plan.title(), plan.subtitle(), today);
        fileService.savePage(1, "tpl-con-title", titleMarkdown);

        // 2. Pre-fetch ALL Context
        String sharedContext = context.ai().withAutoLlm()
                .withReference(localKnowledgeTool)
                .generateText(String.format("""
                        Search the local knowledge base for information related to: '%s'.
                        Specific topics: %s.
                        
                        # INSTRUCTIONS:
                        - Summarize ONLY relevant facts found in the documents.
                        - If NO relevant documents are found at all, return exactly the word 'NONE'.
                        """, plan.title(), String.join(", ", plan.pageTopics())));

        String contextSection = (sharedContext != null && !sharedContext.trim().equalsIgnoreCase("NONE")) ? 
            String.format("# SHARED CONTEXT FROM DOCUMENTS:\n%s", sharedContext) : 
            "# NOTE: No relevant local documents found.";

        // 3. Batch Generate All Pages
        SlideList result = context.ai()
                .withAutoLlm()
                .withReference(presentationExpert)
                .createObject(String.format("""
                        Generate a professional presentation about: '%s'.
                        
                        %s
                        
                        # TOPICS TO COVER (One slide per topic):
                        %s
                        
                        # REQUIREMENTS:
                        1. Generate exactly %d content slides.
                        2. Start page numbers from 2.
                        3. Use appropriate templateName (tpl-con-3-2, tpl-con-2-1, or tpl-con-block).
                        4. All text MUST be in KOREAN.
                        """, plan.title(), contextSection, String.join("\n- ", plan.pageTopics()), plan.pageTopics().size()), SlideList.class);

        // 4. Save and return
        for (SlidePage page : result.slides()) {
            fileService.savePage(page.pageNumber(), page.templateName(), page.toMarkdown());
        }
        return result.slides();
    }

    @AchievesGoal(description = "Merged professional presentation")
    @Action
    public FinalPresentation finishPresentation(List<SlidePage> allSlides) throws IOException {
        return new FinalPresentation(fileService.mergeAll());
    }

    @Action
    public SlidePage modifyExistingSlide(UserInput input, PresentationSettings settings, Ai ai) throws IOException {
        Integer targetPage = ai.withAutoLlm().createObject("Which page number to modify? " + input.getContent(), Integer.class);
        String currentRawFile = fileService.readPage(targetPage);
        
        SlidePage updated = ai.withAutoLlm()
                // .withReference(localKnowledgeTool)
                // .withPromptContributor(PresentationPersonas.WRITER)
                .createObject(String.format("""
                        Modify Page %d of the presentation.
                        CURRENT RAW CONTENT:
                        %s
                        
                        REQUEST: %s
                        
                        # RULES:
                        1. Fill 'titleText' (Korean, starts with ##).
                        2. Select 'templateName'.
                        3. Fill 'leftContent', 'rightContent', or 'fullWidthContent' as appropriate.
                        4. ALL text content MUST BE in KOREAN.
                        """, targetPage, currentRawFile, input.getContent()), SlidePage.class);
        
        fileService.savePage(updated.pageNumber(), updated.templateName(), updated.toMarkdown());
        return updated;
    }
}
