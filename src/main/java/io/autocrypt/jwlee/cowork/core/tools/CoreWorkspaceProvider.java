package io.autocrypt.jwlee.cowork.core.tools;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Provides standardized workspace directory structures for all agents.
 * Format: output/{agent-name}/{workspace-id}/{sub-category}/
 */
@Component
public class CoreWorkspaceProvider {

    private static final Path BASE_OUTPUT = Paths.get("output");
    private static final Pattern NON_LATIN = Pattern.compile("[^\\w-]");
    private static final Pattern WHITESPACE = Pattern.compile("[\\s]");

    public enum SubCategory {
        RAG("rag"),
        STATE("state"),
        ARTIFACTS("artifacts"),
        EXPORT("export");

        private final String dirName;
        SubCategory(String dirName) { this.dirName = dirName; }
        public String getDirName() { return dirName; }
    }

    /**
     * Resolves the base workspace path for an agent and workspace ID.
     */
    public Path getWorkspacePath(String agentName, String workspaceId) {
        String slug = toSlug(workspaceId);
        return BASE_OUTPUT.resolve(agentName).resolve(slug);
    }

    /**
     * Resolves and ensures a specific sub-category directory within a workspace.
     */
    public Path getSubPath(String agentName, String workspaceId, SubCategory category) throws IOException {
        Path subPath = getWorkspacePath(agentName, workspaceId).resolve(category.getDirName());
        if (!Files.exists(subPath)) {
            Files.createDirectories(subPath);
        }
        return subPath;
    }

    /**
     * Converts a string (potentially a filename or path) into a safe, clean directory name.
     */
    public String toSlug(String input) {
        if (input == null || input.isBlank()) return "default";
        
        // Remove file extension if present
        String name = input;
        int lastDot = name.lastIndexOf('.');
        if (lastDot > 0) {
            name = name.substring(0, lastDot);
        }
        
        // Take only the filename part if it's a path
        name = Paths.get(name).getFileName().toString();

        String nowhitespace = WHITESPACE.matcher(name).replaceAll("-");
        String normalized = Normalizer.normalize(nowhitespace, Normalizer.Form.NFD);
        String slug = NON_LATIN.matcher(normalized).replaceAll("");
        
        String result = slug.toLowerCase(Locale.ENGLISH).replaceAll("-+", "-").replaceAll("^-|-$", "");
        
        if (result.isBlank()) return "ws-" + Math.abs(input.hashCode() % 10000);
        
        // Length limit
        if (result.length() > 35) {
            return result.substring(0, 30) + "-" + String.format("%04x", result.hashCode() & 0xFFFF);
        }
        return result;
    }
}
