package io.autocrypt.jwlee.cowork.core.tools;

import com.embabel.agent.api.annotation.LlmTool;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Core file manipulation tools for agents, inspired by Gemini CLI.
 */
@Component
public class CoreFileTools {

    private Path resolveAndCheckPath(String pathStr) {
        Path root = Paths.get(".").toAbsolutePath().normalize();
        Path target = root.resolve(pathStr).normalize();
        if (!target.startsWith(root)) {
            throw new IllegalArgumentException("Access denied: Path " + pathStr + " is outside the working directory.");
        }
        return target;
    }

    @LlmTool(description = "Reads the complete content of a specified file. Returns the content as a string.")
    public String readFile(String path) throws IOException {
        Path filePath = resolveAndCheckPath(path);
        return Files.readString(filePath, StandardCharsets.UTF_8);
    }

    @LlmTool(description = "Writes the complete content to a file, automatically creating missing parent directories. Overwrites existing files.")
    public String writeFile(String path, String content) throws IOException {
        Path filePath = resolveAndCheckPath(path);
        if (filePath.getParent() != null) {
            Files.createDirectories(filePath.getParent());
        }
        Files.writeString(filePath, content, StandardCharsets.UTF_8);
        return "Successfully wrote to " + path;
    }

    @LlmTool(description = "Replaces ONE occurrence of a literal string within a file. Provide enough context in 'oldString' to ensure uniqueness.")
    public String replace(String path, String oldString, String newString) throws IOException {
        Path filePath = resolveAndCheckPath(path);
        String content = Files.readString(filePath, StandardCharsets.UTF_8);
        
        if (!content.contains(oldString)) {
            throw new IllegalArgumentException("Could not find exact match for 'oldString' in file: " + path);
        }
        
        // Count occurrences to ensure uniqueness
        int firstIndex = content.indexOf(oldString);
        int lastIndex = content.lastIndexOf(oldString);
        if (firstIndex != lastIndex) {
            throw new IllegalArgumentException("Found multiple occurrences of 'oldString'. Provide more context to make it unique.");
        }
        
        String newContent = content.replace(oldString, newString);
        Files.writeString(filePath, newContent, StandardCharsets.UTF_8);
        return "Successfully updated " + path;
    }

    @LlmTool(description = "Lists the names of files and subdirectories directly within a specified directory path.")
    public List<String> listDirectory(String path) throws IOException {
        Path dirPath = resolveAndCheckPath(path);
        try (Stream<Path> stream = Files.list(dirPath)) {
            return stream.map(p -> p.getFileName().toString())
                    .collect(Collectors.toList());
        }
    }

    @LlmTool(description = "Finds files matching specific glob patterns (e.g., 'src/**/*.java'). Returns relative paths.")
    public List<String> glob(String pattern) throws IOException {
        Path root = Paths.get(".").toAbsolutePath().normalize();
        final PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + pattern);
        
        try (Stream<Path> stream = Files.walk(root)) {
            return stream.filter(p -> {
                // Relativize the path against the absolute root to match the glob against the relative string
                Path rel = root.relativize(p);
                return matcher.matches(rel);
            })
                    .map(p -> root.relativize(p).toString())
                    .collect(Collectors.toList());
        }
    }
}
