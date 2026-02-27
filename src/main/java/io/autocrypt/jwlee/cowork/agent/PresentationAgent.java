package io.autocrypt.jwlee.cowork.agent;

import com.embabel.agent.api.annotation.AchievesGoal;
import com.embabel.agent.api.annotation.Action;
import com.embabel.agent.api.annotation.Agent;
import com.embabel.agent.api.common.Ai;
import com.embabel.agent.core.CoreToolGroups;
import com.embabel.agent.domain.io.UserInput;
import com.embabel.agent.rag.tools.ToolishRag;
import io.autocrypt.jwlee.cowork.service.SlideFileService;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Agent(description = "Advanced Slides professional creator. Uses deterministic wrapping for templates.")
public class PresentationAgent {

    private final ToolishRag localKnowledgeTool;
    private final SlideFileService fileService;

    public PresentationAgent(ToolishRag localKnowledgeTool, SlideFileService fileService) {
        this.localKnowledgeTool = localKnowledgeTool;
        this.fileService = fileService;
    }

    public record SlidePage(int pageNumber, String templateName, String contentMarkdown) {}
    public record PresentationSettings(String content) {}
    public record PresentationPlan(String title, List<String> pageTopics) {}
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
                .withReference(localKnowledgeTool)
                .withToolGroup(CoreToolGroups.WEB)
                .createObject(String.format("Plan a presentation structure for: %s. Output a list of topics for each slide.", input.getContent()), PresentationPlan.class);
    }

    @Action
    public List<SlidePage> generateAllSlides(PresentationPlan plan, PresentationSettings settings, Ai ai) throws IOException {
        List<SlidePage> allPages = new ArrayList<>();
        for (int i = 0; i < plan.pageTopics().size(); i++) {
            int pageNum = i + 1;
            String topic = plan.pageTopics().get(i);
            
            SlidePage page = ai.withAutoLlm()
                    .withReference(localKnowledgeTool)
                    .creating(SlidePage.class)
                    .fromPrompt(String.format("""
                            You are crafting the CONTENT for page %d of '%s'.
                            TOPIC: %s
                            
                            # YOUR TASKS:
                            1. Select the BEST template name from 'catalog.md' (e.g., 'tpl-con-3-2').
                            2. Write ONLY the inner container markdown (::: title, ::: left, ::: right, ::: block).
                            
                            # CRITICAL RESTRICTIONS:
                            - DO NOT write the '<!-- slide template=... -->' line.
                            - DO NOT write '---' separators.
                            - DO NOT write YAML headers.
                            - JUST write the containers and their content.
                            """, pageNum, plan.title(), topic));
            
            fileService.savePage(page.pageNumber(), page.templateName(), page.contentMarkdown());
            allPages.add(page);
        }
        return allPages;
    }

    @AchievesGoal(description = "Merged professional presentation")
    @Action
    public FinalPresentation finishPresentation(List<SlidePage> allSlides) throws IOException {
        return new FinalPresentation(fileService.mergeAll());
    }

    @Action
    public SlidePage modifyExistingSlide(UserInput input, PresentationSettings settings, Ai ai) throws IOException {
        Integer targetPage = ai.withAutoLlm().creating(Integer.class).fromPrompt("Which page number to modify? " + input.getContent());
        String currentRawFile = fileService.readPage(targetPage);
        
        SlidePage updated = ai.withAutoLlm()
                .withReference(localKnowledgeTool)
                .creating(SlidePage.class)
                .fromPrompt(String.format("""
                        Modify the CONTENT of Page %d.
                        CURRENT FULL CONTENT (including auto-generated parts):
                        %s
                        
                        REQUEST: %s
                        
                        # RULES:
                        1. Return the correct 'templateName'.
                        2. Return ONLY the inner content in 'contentMarkdown'.
                        3. STRIP AWAY any '---' or '<!-- slide ... -->' from your output.
                        """, targetPage, currentRawFile, input.getContent()));
        
        fileService.savePage(updated.pageNumber(), updated.templateName(), updated.contentMarkdown());
        return updated;
    }
}
