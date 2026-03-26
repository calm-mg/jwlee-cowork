package io.autocrypt.jwlee.cowork.advancedslidesagent;

import com.embabel.agent.core.AgentPlatform;
import io.autocrypt.jwlee.cowork.advancedslidesagent.dto.SlideGenerationRequest;
import io.autocrypt.jwlee.cowork.advancedslidesagent.dto.SlideMarkdownOutput;
import io.autocrypt.jwlee.cowork.core.commands.BaseAgentCommand;
import io.autocrypt.jwlee.cowork.core.tools.CoreFileTools;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import org.springframework.shell.standard.ShellOption;

import java.io.IOException;
import java.util.concurrent.ExecutionException;

/**
 * AdvancedSlidesCommand as defined in DSL-AdvancedSlidesAgent.md.
 */
@ShellComponent
public class AdvancedSlidesCommand extends BaseAgentCommand {

    private final CoreFileTools fileTools;

    public AdvancedSlidesCommand(AgentPlatform agentPlatform, CoreFileTools fileTools) {
        super(agentPlatform);
        this.fileTools = fileTools;
    }

    @ShellMethod(value = "Generate Obsidian Advanced Slides from source material", key = "slides")
    public void generateSlides(
            @ShellOption(help = "Workspace ID for the generation") String workspaceId,
            @ShellOption(value = {"--source", "-s"}, help = "Raw source text material", defaultValue = ShellOption.NULL) String source,
            @ShellOption(value = {"--source-file", "-f"}, help = "Path to a file containing source material", defaultValue = ShellOption.NULL) String sourceFile,
            @ShellOption(help = "Specific instructions for slide generation") String instructions,
            @ShellOption(value = {"--show-prompts", "-p"}, defaultValue = "false") boolean showPrompts,
            @ShellOption(value = {"--show-responses", "-r"}, defaultValue = "false") boolean showResponses
    ) throws ExecutionException, InterruptedException, IOException {

        if (source == null && sourceFile == null) {
            System.err.println("Error: Source material is required. Please provide --source or --source-file.");
            return;
        }

        SlideGenerationRequest request = new SlideGenerationRequest(workspaceId, source, sourceFile, instructions);

        SlideMarkdownOutput output = invokeAgent(SlideMarkdownOutput.class, getOptions(showPrompts, showResponses), request);

        System.out.println("--- GENERATED SLIDE MARKDOWN ---");
        System.out.println(output.markdownContent());
        System.out.println("--------------------------------");
        System.out.println("Successfully generated slides!");
        System.out.println("Saved to: " + output.savedFilePath());
    }
}
