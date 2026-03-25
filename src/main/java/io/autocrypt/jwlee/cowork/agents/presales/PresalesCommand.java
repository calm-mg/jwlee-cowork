package io.autocrypt.jwlee.cowork.agents.presales;

import com.embabel.agent.api.common.Ai;
import com.embabel.agent.api.invocation.AgentInvocation;
import com.embabel.agent.core.AgentPlatform;
import com.embabel.agent.core.AgentProcess;
import io.autocrypt.jwlee.cowork.core.tools.CoreWorkspaceProvider;
import io.autocrypt.jwlee.cowork.core.tools.LocalRagTools;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import org.springframework.shell.standard.ShellOption;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@ShellComponent
public class PresalesCommand {

    private final PresalesAgent agent;
    private final PresalesWorkspace workspace;
    private final LocalRagTools ragTools;
    private final Ai ai;
    private final AgentPlatform agentPlatform;
    private final CoreWorkspaceProvider workspaceProvider;
    private static final String AGENT_NAME = "presales";

    public PresalesCommand(PresalesAgent agent, PresalesWorkspace workspace, LocalRagTools ragTools, 
                          Ai ai, AgentPlatform agentPlatform, CoreWorkspaceProvider workspaceProvider) {
        this.agent = agent;
        this.workspace = workspace;
        this.ragTools = ragTools;
        this.ai = ai;
        this.agentPlatform = agentPlatform;
        this.workspaceProvider = workspaceProvider;
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
            @ShellOption(value = "--ws", help = "Workspace name") String wsName) throws IOException {
        
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
        
        AgentProcess process = AgentInvocation
                .create(agentPlatform, String.class)
                .runAsync(new PresalesAgent.RequirementRequest(sourceContent, techRagPath))
                .join();
        
        String crs = process.resultOfType(String.class);
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
        return runPhase2(wsPath, wsName, language, crs, productRagPath);
    }

    @ShellMethod(value = "Resume analysis using modified crs.md in the workspace.", key = "presales-resume")
    public String resume(@ShellOption(value = "--ws", help = "Workspace name") String wsName) throws IOException {
        Path wsPath = workspace.getWorkspacePath(wsName);
        PresalesWorkspace.PresalesState state = workspace.loadState(wsPath);
        
        if (state == null) {
            return "Error: Workspace not found or state.json missing.";
        }

        System.out.println("Resuming analysis using modified crs.md...");
        String modifiedCrs = workspace.loadCrs(wsPath);
        Path productRagPath = Path.of(state.productRagPath());
        
        return runPhase2(wsPath, wsName, state.language(), modifiedCrs, productRagPath);
    }

    private String runPhase2(Path wsPath, String wsName, String language, String crs, Path productRagPath) throws IOException {
        System.out.println("Phase 2: Analyzing gap and generating final report using product-spec RAG at " + productRagPath);
        
        AgentProcess process = AgentInvocation
                .create(agentPlatform, PresalesAgent.AnalysisResult.class)
                .runAsync(new PresalesAgent.GapAnalysisRequest(crs, language, productRagPath))
                .join();
        
        PresalesAgent.AnalysisResult result = process.resultOfType(PresalesAgent.AnalysisResult.class);

        workspace.saveAnalysis(wsPath, result.gapAnalysis());
        workspace.saveQuestions(wsPath, result.questions());
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
            - Analysis: analysis.md
            - Questions: questions.md
            - Final Report: final_report.md
            
            Results saved in: %s
            RAG Indices used from: %s
            """, wsName, exportPath.toAbsolutePath(), productRagPath.getParent());
    }
}
