package io.autocrypt.jwlee.cowork.advancedslidesagent;

import com.embabel.agent.api.annotation.Action;
import com.embabel.agent.api.annotation.AchievesGoal;
import com.embabel.agent.api.annotation.EmbabelComponent;
import com.embabel.agent.api.common.ActionContext;
import com.embabel.agent.api.common.Ai;
import io.autocrypt.jwlee.cowork.advancedslidesagent.dto.*;
import io.autocrypt.jwlee.cowork.core.tools.CoreFileTools;
import io.autocrypt.jwlee.cowork.core.tools.CoreWorkspaceProvider;
import io.autocrypt.jwlee.cowork.core.tools.CoreWorkspaceProvider.SubCategory;
import io.autocrypt.jwlee.cowork.core.tools.CoworkLogger;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * AdvancedSlidesAgent as defined in DSL-AdvancedSlidesAgent.md.
 */
@EmbabelComponent
@Component
public class AdvancedSlidesAgent {

    private final CoreWorkspaceProvider workspaceProvider;
    private final CoreFileTools fileTools;
    private final CoworkLogger logger;

    public interface Stage {}

    public AdvancedSlidesAgent(CoreWorkspaceProvider workspaceProvider,
                                CoreFileTools fileTools,
                                CoworkLogger logger) {
        this.workspaceProvider = workspaceProvider;
        this.fileTools = fileTools;
        this.logger = logger;
    }

    /**
     * Internal DTO for raw markdown generation.
     */
    public record SlideMarkdownRaw(String markdownContent) {}

    @Action
    public SlideGenerationState analyzeAndStructure(SlideGenerationRequest request, Ai ai) {
        logger.info("AdvancedSlides", "Analyzing and structuring slide content for workspace: " + request.workspaceId());
        
        SlideStructurePlan plan = ai.withLlmByRole("normal")
                .rendering("agents/advancedslides/analyze-structure")
                .createObject(SlideStructurePlan.class, Map.of(
                        "sourceMaterial", request.sourceMaterial(),
                        "instructions", request.instructions()
                ));

        return new SlideGenerationState(request, plan);
    }

    @AchievesGoal(description = "Generates final Obsidian Advanced Slides markdown and saves it to the export directory")
    @Action
    public SlideMarkdownOutput generateMarkdown(SlideGenerationState state, Ai ai) throws IOException {
        logger.info("AdvancedSlides", "Generating markdown for slides in workspace: " + state.request().workspaceId());

        // 1. Read few-shot guidelines
        CoreFileTools.FileResult guidelinesResult = fileTools.readFile("guides/few-shots/adv-slides-few-shot.md");
        String slideGuidelines = guidelinesResult.status().equals("SUCCESS") ? guidelinesResult.content() : "";

        // 2. Call LLM to generate markdown
        SlideMarkdownRaw rawOutput = ai.withLlmByRole("performant")
                .rendering("agents/advancedslides/generate-markdown")
                .createObject(SlideMarkdownRaw.class, Map.of(
                        "sourceMaterial", state.request().sourceMaterial(),
                        "instructions", state.request().instructions(),
                        "outline", state.plan().outline(),
                        "slideGuidelines", slideGuidelines
                ));

        // 3. Resolve export directory
        Path exportDir = workspaceProvider.getSubPath("AdvancedSlidesAgent", state.request().workspaceId(), SubCategory.EXPORT);
        
        // 4. Generate filename
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String filename = "slides_" + timestamp + ".md";
        Path savedPath = exportDir.resolve(filename);

        // 5. Write file
        Files.writeString(savedPath, rawOutput.markdownContent(), StandardCharsets.UTF_8);
        logger.info("AdvancedSlides", "Slides saved to: " + savedPath.toAbsolutePath());

        return new SlideMarkdownOutput(rawOutput.markdownContent(), savedPath.toAbsolutePath().toString());
    }
}
