package io.autocrypt.jwlee.cowork.presalesagent;

import com.embabel.agent.api.common.Ai;
import com.embabel.agent.core.AgentPlatform;
import io.autocrypt.jwlee.cowork.core.commands.BaseAgentCommand;
import io.autocrypt.jwlee.cowork.core.tools.CoreWorkspaceProvider;
import io.autocrypt.jwlee.cowork.core.tools.CoworkLogger;
import io.autocrypt.jwlee.cowork.core.tools.LocalRagTools;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import org.springframework.shell.standard.ShellOption;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ExecutionException;

@ShellComponent
public class PresalesCommand extends BaseAgentCommand {

    private final PresalesAgent agent;
    private final PresalesWorkspace workspace;
    private final LocalRagTools ragTools;
    private final Ai ai;
    private final CoreWorkspaceProvider workspaceProvider;
    private final CoworkLogger coworkLogger;
    private static final String AGENT_NAME = "presales";

    public PresalesCommand(PresalesAgent agent, PresalesWorkspace workspace, LocalRagTools ragTools, 
                          Ai ai, AgentPlatform agentPlatform, CoreWorkspaceProvider workspaceProvider, CoworkLogger coworkLogger) {
        super(agentPlatform);
        this.agent = agent;
        this.workspace = workspace;
        this.ragTools = ragTools;
        this.ai = ai;
        this.workspaceProvider = workspaceProvider;
        this.coworkLogger = coworkLogger;
    }

    @ShellMethod(value = "Ingest documents into presales RAG indices.", key = "presales-ingest")
    public String ingest(
            @ShellOption(value = "--type", help = "RAG type (TECH or PRODUCT)") String type,
            @ShellOption(value = "--path", help = "Directory path to ingest") String path,
            @ShellOption(value = "--ws", help = "Workspace name", defaultValue = "common") String wsName) throws IOException {
        
        String ragName = type.equalsIgnoreCase("TECH") ? "tech-ref" : "product-spec";
        Path ragPath = workspaceProvider.getSubPath(AGENT_NAME, wsName, CoreWorkspaceProvider.SubCategory.RAG).resolve(ragName);
        
        return ragTools.ingestDirectoryAt(path, ragName, ragPath);
    }

    @ShellMethod(value = "Start full presales analysis from customer inquiry (email, chat, transcript).", key = "presales-start")
    public String start(
            @ShellOption(value = "--source-path", help = "Path to the customer inquiry file (txt, md, etc.)") String sourcePath,
            @ShellOption(value = "--ws", help = "Workspace name") String wsName,
            @ShellOption(value = {"-p", "--show-prompts"}, defaultValue = "false", help = "Log prompts sent to the LLM") boolean p,
            @ShellOption(value = {"-r", "--show-responses"}, defaultValue = "false", help = "Log LLM responses") boolean r) throws IOException, ExecutionException, InterruptedException {
        
        Path wsPath = workspace.initWorkspace(wsName);
        String sourceContent = Files.readString(Path.of(sourcePath).toAbsolutePath().normalize());

        Path ragBasePath = workspaceProvider.getSubPath(AGENT_NAME, wsName, CoreWorkspaceProvider.SubCategory.RAG);
        Path techRagPath = ragBasePath.resolve("tech-ref");
        Path productRagPath = ragBasePath.resolve("product-spec");

        // 1. Detect language
        String langPrompt = "Identify the language of the following text (e.g., 'English', 'Korean'). Output ONLY the language name: \n\n" + sourceContent;
        String language = ai.withLlmByRole("simple").generateText(langPrompt).trim();

        // 2. Phase 1: Refine Requirements
        System.out.println("Phase 1: Refining requirements using tech-ref RAG at " + techRagPath);
        
        PresalesAgent.CrsResult crsResult = invokeAgent(
                PresalesAgent.CrsResult.class,
                getOptions(p, r),
                new PresalesAgent.RequirementRequest(sourceContent, techRagPath)
        );
        reportOverallMetrics(coworkLogger, "Phase1-Refine");

        String crs = crsResult.content();
        workspace.saveCrs(wsPath, crs);

        // 3. Save State (RAG 경로 저장)
        workspace.saveState(wsPath, new PresalesWorkspace.PresalesState(
            wsName, 
            sourcePath, 
            language, 
            techRagPath.toString(), 
            productRagPath.toString(), 
            PresalesWorkspace.PresalesState.Phase.INIT
        ));

        // 4. Phase 2: Gap Analysis & Finalization
        return runPhase2(wsPath, wsName, language, crs, productRagPath, p, r);
    }

    @ShellMethod(value = "Resume analysis using modified crs.md in the workspace.", key = "presales-resume")
    public String resume(
            @ShellOption(value = "--ws", help = "Workspace name") String wsName,
            @ShellOption(value = {"-p", "--show-prompts"}, defaultValue = "false", help = "Log prompts sent to the LLM") boolean p,
            @ShellOption(value = {"-r", "--show-responses"}, defaultValue = "false", help = "Log LLM responses") boolean r) throws IOException, ExecutionException, InterruptedException {
        Path wsPath = workspace.getWorkspacePath(wsName);
        PresalesWorkspace.PresalesState state = workspace.loadState(wsPath);
        
        if (state == null) {
            return "Error: Workspace not found or state.json missing.";
        }

        System.out.println("Resuming analysis using modified crs.md...");
        String modifiedCrs = workspace.loadCrs(wsPath);
        Path productRagPath = Path.of(state.productRagPath());
        
        return runPhase2(wsPath, wsName, state.language(), modifiedCrs, productRagPath, p, r);
    }

    private String runPhase2(Path wsPath, String wsName, String language, String crs, Path productRagPath, boolean p, boolean r) throws IOException, ExecutionException, InterruptedException {
        System.out.println("Phase 2: Analyzing gap and generating final report using product-spec RAG at " + productRagPath);
        
        PresalesAgent.AnalysisResult result = invokeAgent(
                PresalesAgent.AnalysisResult.class,
                getOptions(p, r),
                new PresalesAgent.GapAnalysisRequest(crs, language, productRagPath)
        );
        reportOverallMetrics(coworkLogger, "Phase2-GapAnalysis");

        workspace.saveFinalReport(wsPath, result.finalReport());

        // Update state to COMPLETED
        PresalesWorkspace.PresalesState oldState = workspace.loadState(wsPath);
        workspace.saveState(wsPath, new PresalesWorkspace.PresalesState(
            wsName, 
            oldState != null ? oldState.sourcePath() : null, 
            language, 
            oldState != null ? oldState.techRagPath() : null, 
            productRagPath.toString(), 
            PresalesWorkspace.PresalesState.Phase.COMPLETED
        ));

        Path exportPath = wsPath.resolve(CoreWorkspaceProvider.SubCategory.EXPORT.getDirName());
        return String.format("""
            ✅ Analysis Completed!
            Workspace: %s
            - CRS: crs.md
            - Final Report: final_report.md
            
            Results saved in: %s
            RAG Indices used from: %s
            """, wsName, exportPath.toAbsolutePath(), productRagPath.getParent());
    }
}
