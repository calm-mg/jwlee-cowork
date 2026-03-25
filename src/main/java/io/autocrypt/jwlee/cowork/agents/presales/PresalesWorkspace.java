package io.autocrypt.jwlee.cowork.agents.presales;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.autocrypt.jwlee.cowork.core.tools.CoreWorkspaceProvider;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Component
public class PresalesWorkspace {

    private final ObjectMapper objectMapper;
    private final CoreWorkspaceProvider workspaceProvider;
    private static final String AGENT_NAME = "presales";

    public record PresalesState(
            String workspaceName,
            String sourcePath,
            String language,
            String techRagPath,
            String productRagPath,
            Phase currentPhase
    ) {
        public enum Phase {
            INIT,
            COMPLETED
        }
    }

    public PresalesWorkspace(ObjectMapper objectMapper, CoreWorkspaceProvider workspaceProvider) {
        this.objectMapper = objectMapper;
        this.workspaceProvider = workspaceProvider;
    }

    public Path initWorkspace(String name) throws IOException {
        Path wsPath = workspaceProvider.getWorkspacePath(AGENT_NAME, name);
        workspaceProvider.getSubPath(AGENT_NAME, name, CoreWorkspaceProvider.SubCategory.STATE);
        workspaceProvider.getSubPath(AGENT_NAME, name, CoreWorkspaceProvider.SubCategory.EXPORT);
        return wsPath;
    }

    private Path getStatePath(Path wsPath) {
        return wsPath.resolve(CoreWorkspaceProvider.SubCategory.STATE.getDirName());
    }

    private Path getExportPath(Path wsPath) {
        return wsPath.resolve(CoreWorkspaceProvider.SubCategory.EXPORT.getDirName());
    }

    public void saveState(Path wsPath, PresalesState state) throws IOException {
        Path stateFile = getStatePath(wsPath).resolve("state.json");
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(stateFile.toFile(), state);
    }

    public PresalesState loadState(Path wsPath) throws IOException {
        Path stateFile = getStatePath(wsPath).resolve("state.json");
        if (!Files.exists(stateFile)) {
            return null;
        }
        return objectMapper.readValue(stateFile.toFile(), PresalesState.class);
    }

    public void saveCrs(Path wsPath, String content) throws IOException {
        Files.writeString(getExportPath(wsPath).resolve("crs.md"), content);
    }

    public String loadCrs(Path wsPath) throws IOException {
        return Files.readString(getExportPath(wsPath).resolve("crs.md"));
    }

    public void saveAnalysis(Path wsPath, String content) throws IOException {
        Files.writeString(getExportPath(wsPath).resolve("analysis.md"), content);
    }

    public void saveQuestions(Path wsPath, String content) throws IOException {
        Files.writeString(getExportPath(wsPath).resolve("questions.md"), content);
    }

    public void saveFinalReport(Path wsPath, String content) throws IOException {
        Files.writeString(getExportPath(wsPath).resolve("final_report.md"), content);
    }

    public Path getWorkspacePath(String name) {
        return workspaceProvider.getWorkspacePath(AGENT_NAME, name);
    }
}
