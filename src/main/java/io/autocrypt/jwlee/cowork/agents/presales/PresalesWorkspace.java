package io.autocrypt.jwlee.cowork.agents.presales;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Component
public class PresalesWorkspace {

    private static final String BASE_DIR = "output/presales";
    private final ObjectMapper objectMapper;

    public PresalesWorkspace(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public record PresalesState(
            String workspaceName,
            String sourcePath, // renamed from originalEmailPath
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

    public Path initWorkspace(String name) throws IOException {
        Path wsPath = Paths.get(BASE_DIR, name).toAbsolutePath().normalize();
        if (!Files.exists(wsPath)) {
            Files.createDirectories(wsPath);
        }
        return wsPath;
    }

    public void saveState(Path wsPath, PresalesState state) throws IOException {
        Path stateFile = wsPath.resolve("state.json");
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(stateFile.toFile(), state);
    }

    public PresalesState loadState(Path wsPath) throws IOException {
        Path stateFile = wsPath.resolve("state.json");
        if (!Files.exists(stateFile)) {
            return null;
        }
        return objectMapper.readValue(stateFile.toFile(), PresalesState.class);
    }

    public void saveCrs(Path wsPath, String content) throws IOException {
        Files.writeString(wsPath.resolve("crs.md"), content);
    }

    public String loadCrs(Path wsPath) throws IOException {
        return Files.readString(wsPath.resolve("crs.md"));
    }

    public void saveAnalysis(Path wsPath, String content) throws IOException {
        Files.writeString(wsPath.resolve("analysis.md"), content);
    }

    public void saveQuestions(Path wsPath, String content) throws IOException {
        Files.writeString(wsPath.resolve("questions.md"), content);
    }

    public void saveFinalReport(Path wsPath, String content) throws IOException {
        Files.writeString(wsPath.resolve("final_report.md"), content);
    }

    public Path getWorkspacePath(String name) {
        return Paths.get(BASE_DIR, name).toAbsolutePath().normalize();
    }
}
