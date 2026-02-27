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

@Agent(description = "Advanced Slides presentation creator that can handle multi-page structures with context")
public class PresentationAgent {

    private final ToolishRag localKnowledgeTool;
    private final SlideFileService fileService;

    public PresentationAgent(ToolishRag localKnowledgeTool, SlideFileService fileService) {
        this.localKnowledgeTool = localKnowledgeTool;
        this.fileService = fileService;
    }

    public record SlidePage(int pageNumber, String markdown) {}
    public record PresentationSettings(String content) {}
    public record PresentationPlan(String title, List<String> pageTopics) {}
    public record FinalPresentation(String filePath) {}

    /**
     * Step 1: Initialize presentation settings (Fixed template).
     */
    @Action
    public PresentationSettings initializeSettings(UserInput input) throws IOException {
        String fixedSettings = """
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
                .force-center { display: flex !important; flex-direction: column; justify-content: center; align-items: center; width: 100%; height: 100%; text-align: center; }
                </style>
                """;
        fileService.saveSettings(fixedSettings);
        return new PresentationSettings(fixedSettings);
    }

    /**
     * Step 2: Create a high-level plan for the entire presentation.
     * This acts as the 'Map' to keep context consistent across all slides.
     */
    @Action
    public PresentationPlan planPresentation(UserInput input, PresentationSettings settings, Ai ai) {
        return ai.withAutoLlm()
                .withReference(localKnowledgeTool)
                .withToolGroup(CoreToolGroups.WEB)
                .createObject(String.format("""
                        Plan a comprehensive presentation based on: %s
                        Reference local knowledge and web search to create a logical flow.
                        Define a title and a list of specific topics for each slide page.
                        If the user requested a specific number of pages, respect that.
                        """, input.getContent()), PresentationPlan.class);
    }

    /**
     * Step 3: Generate all slides based on the plan.
     * By receiving 'PresentationPlan', the LLM knows the full context of the presentation.
     */
    @Action
    public List<SlidePage> generateAllSlides(PresentationPlan plan, Ai ai) throws IOException {
        List<SlidePage> allPages = new ArrayList<>();
        
        for (int i = 0; i < plan.pageTopics().size(); i++) {
            int pageNum = i + 1;
            String topic = plan.pageTopics().get(i);
            
            SlidePage page = ai.withAutoLlm()
                    .withReference(localKnowledgeTool)
                    .creating(SlidePage.class)
                    .fromPrompt(String.format("""
                            You are crafting page %d of a %d-page presentation titled '%s'.
                            The full outline is: %s
                            
                            CURRENT TOPIC FOR THIS PAGE: %s
                            
                            # Instructions:
                            1. CONSULT 'catalog.md' to pick the best template for this topic.
                            2. Use Advanced Slides containers (::: title, ::: left, etc.).
                            3. Ensure consistency with the overall presentation context.
                            """, pageNum, plan.pageTopics().size(), plan.title(), plan.pageTopics(), topic));
            
            fileService.savePage(page.pageNumber(), page.markdown());
            allPages.add(page);
        }
        return allPages;
    }

    /**
     * Step 4: Finalize and merge.
     */
    @AchievesGoal(description = "The multi-page presentation has been merged and saved")
    @Action
    public FinalPresentation finishPresentation(List<SlidePage> allSlides) throws IOException {
        String path = fileService.mergeAll();
        return new FinalPresentation(path);
    }

    /**
     * Separate Action: 정밀 수정 (기존 구조 유지)
     */
    @Action
    public SlidePage modifyExistingSlide(UserInput input, PresentationSettings settings, Ai ai) throws IOException {
        Integer targetPage = ai.withAutoLlm()
                .creating(Integer.class)
                .fromPrompt("Extract only the page number as an integer from this request: " + input.getContent());
        
        String currentContent = fileService.readPage(targetPage);
        
        SlidePage updated = ai.withAutoLlm()
                .withReference(localKnowledgeTool)
                .creating(SlidePage.class)
                .fromPrompt(String.format("""
                        Modify the following presentation slide based on this request: %s
                        Current content of page %d:
                        %s
                        """, input.getContent(), targetPage, currentContent));
        
        fileService.savePage(updated.pageNumber(), updated.markdown());
        return updated;
    }
}
