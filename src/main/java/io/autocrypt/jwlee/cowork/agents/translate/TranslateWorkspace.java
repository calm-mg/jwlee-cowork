package io.autocrypt.jwlee.cowork.agents.translate;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.autocrypt.jwlee.cowork.core.tools.CoreWorkspaceProvider;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@Component
public class TranslateWorkspace {

    private final ObjectMapper objectMapper;
    private final CoreWorkspaceProvider workspaceProvider;
    private static final String AGENT_NAME = "translate";

    public TranslateWorkspace(ObjectMapper objectMapper, CoreWorkspaceProvider workspaceProvider) {
        this.objectMapper = objectMapper;
        this.workspaceProvider = workspaceProvider;
    }

    /**
     * Initializes the workspace directory for the translation process.
     */
    public Path initWorkspace(String workspaceName) throws IOException {
        Path workspacePath = workspaceProvider.getWorkspacePath(AGENT_NAME, workspaceName);
        workspaceProvider.getSubPath(AGENT_NAME, workspaceName, CoreWorkspaceProvider.SubCategory.ARTIFACTS);
        workspaceProvider.getSubPath(AGENT_NAME, workspaceName, CoreWorkspaceProvider.SubCategory.STATE);
        workspaceProvider.getSubPath(AGENT_NAME, workspaceName, CoreWorkspaceProvider.SubCategory.EXPORT);
        
        Path imagesPath = workspacePath.resolve(CoreWorkspaceProvider.SubCategory.ARTIFACTS.getDirName()).resolve("images");
        if (!Files.exists(imagesPath)) Files.createDirectories(imagesPath);
        
        return workspacePath;
    }

    private Path getStatePath(Path workspacePath) {
        return workspacePath.resolve(CoreWorkspaceProvider.SubCategory.STATE.getDirName());
    }

    private Path getArtifactsPath(Path workspacePath) {
        return workspacePath.resolve(CoreWorkspaceProvider.SubCategory.ARTIFACTS.getDirName());
    }

    /**
     * Saves the metadata state to state.json.
     */
    public void saveState(Path workspacePath, TranslateState state) throws IOException {
        File stateFile = getStatePath(workspacePath).resolve("state.json").toFile();
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(stateFile, state);
    }

    /**
     * Loads the metadata state from state.json if it exists.
     */
    public TranslateState loadState(Path workspacePath) throws IOException {
        File stateFile = getStatePath(workspacePath).resolve("state.json").toFile();
        if (stateFile.exists()) {
            return objectMapper.readValue(stateFile, TranslateState.class);
        }
        return new TranslateState();
    }

    /**
     * Saves a translated chunk.
     */
    public void saveTranslatedChunk(Path workspacePath, int chunkIndex, String content) throws IOException {
        Path chunksDir = getArtifactsPath(workspacePath).resolve("chunks");
        if (!Files.exists(chunksDir)) Files.createDirectories(chunksDir);
        Path chunkFile = chunksDir.resolve(String.format("chunk_%03d.md", chunkIndex));
        Files.writeString(chunkFile, content);
    }

    /**
     * Reads a translated chunk.
     */
    public String readTranslatedChunk(Path workspacePath, int chunkIndex) throws IOException {
        Path chunkFile = getArtifactsPath(workspacePath).resolve("chunks").resolve(String.format("chunk_%03d.md", chunkIndex));
        if (Files.exists(chunkFile)) {
            return Files.readString(chunkFile);
        }
        return null;
    }

    /**
     * Saves the glossary to glossary.json.
     */
    public void saveGlossary(Path workspacePath, String glossaryJsonContent) throws IOException {
        Path glossaryFile = getStatePath(workspacePath).resolve("glossary.json");
        Files.writeString(glossaryFile, glossaryJsonContent);
    }

    /**
     * Reads the glossary from glossary.json.
     */
    public String readGlossary(Path workspacePath) throws IOException {
        Path glossaryFile = getStatePath(workspacePath).resolve("glossary.json");
        if (Files.exists(glossaryFile)) {
            return Files.readString(glossaryFile);
        }
        return null;
    }

    public static class TranslateState {
        private String originalPdfPath;
        private int totalChunks;
        private int completedChunks;
        private Phase currentPhase;

        public enum Phase {
            INIT,
            CONTEXT_EXTRACTION,
            GLOSSARY_WAITING,
            CHUNK_TRANSLATION,
            MERGE_AND_POSTPROCESS,
            DONE
        }

        public TranslateState() {
            this.currentPhase = Phase.INIT;
        }

        // Getters and Setters
        public String getOriginalPdfPath() { return originalPdfPath; }
        public void setOriginalPdfPath(String originalPdfPath) { this.originalPdfPath = originalPdfPath; }
        
        public int getTotalChunks() { return totalChunks; }
        public void setTotalChunks(int totalChunks) { this.totalChunks = totalChunks; }
        
        public int getCompletedChunks() { return completedChunks; }
        public void setCompletedChunks(int completedChunks) { this.completedChunks = completedChunks; }
        
        public Phase getCurrentPhase() { return currentPhase; }
        public void setCurrentPhase(Phase currentPhase) { this.currentPhase = currentPhase; }
    }
}