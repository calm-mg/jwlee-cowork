package io.autocrypt.jwlee.cowork.agents.docsummary;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.autocrypt.jwlee.cowork.core.tools.CoreWorkspaceProvider;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@Component
public class DocSummaryWorkspace {

    private final ObjectMapper objectMapper;
    private final CoreWorkspaceProvider workspaceProvider;
    private static final String AGENT_NAME = "docsummary";

    public DocSummaryWorkspace(ObjectMapper objectMapper, CoreWorkspaceProvider workspaceProvider) {
        this.objectMapper = objectMapper;
        this.workspaceProvider = workspaceProvider;
    }

    public Path initWorkspace(String workspaceName) throws IOException {
        return workspaceProvider.getWorkspacePath(AGENT_NAME, workspaceName);
    }

    public void saveState(Path wsPath, DocSummaryAgent.ExtractedState state) throws IOException {
        Path statePath = wsPath.resolve(CoreWorkspaceProvider.SubCategory.STATE.getDirName());
        if (!Files.exists(statePath)) Files.createDirectories(statePath);
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(statePath.resolve("state.json").toFile(), state);
    }

    public DocSummaryAgent.ExtractedState loadState(Path wsPath) throws IOException {
        Path statePath = wsPath.resolve(CoreWorkspaceProvider.SubCategory.STATE.getDirName());
        return objectMapper.readValue(statePath.resolve("state.json").toFile(), DocSummaryAgent.ExtractedState.class);
    }

    public void saveAllTerms(Path wsPath, List<DocSummaryAgent.ScoredTerm> terms) throws IOException {
        Path statePath = wsPath.resolve(CoreWorkspaceProvider.SubCategory.STATE.getDirName());
        if (!Files.exists(statePath)) Files.createDirectories(statePath);
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(statePath.resolve("all_extracted_terms.json").toFile(), terms);
    }
}
