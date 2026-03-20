package io.autocrypt.jwlee.cowork.core.tools;

import com.embabel.agent.api.annotation.LlmTool;
import org.jline.terminal.Terminal;
import org.jline.utils.AttributedString;
import org.jline.utils.AttributedStyle;
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

    private static final long MAX_FILE_SIZE = 500 * 1024; // 500KB safety limit
    private final Terminal terminal;

    public CoreFileTools(Terminal terminal) {
        this.terminal = terminal;
    }

    public record FileResult(String path, String content, String status) {
        public static FileResult success(String path, String content) {
            return new FileResult(path, content, "SUCCESS");
        }
        public static FileResult error(String path, String message) {
            return new FileResult(path, null, "ERROR: " + message);
        }
    }

    private Path resolveAndCheckPath(String pathStr) {
        Path root = Paths.get(".").toAbsolutePath().normalize();
        Path target = root.resolve(pathStr).normalize();
        if (!target.startsWith(root)) {
            throw new IllegalArgumentException("Access denied: Path " + pathStr + " is outside the working directory.");
        }
        return target;
    }

    @LlmTool(description = "Reads the complete content of a specified file. Returns a FileResult.")
    public FileResult readFile(String path) throws IOException {
        Path filePath = resolveAndCheckPath(path);
        if (!Files.exists(filePath)) return FileResult.error(path, "File not found.");
        
        long size = Files.size(filePath);
        if (size > MAX_FILE_SIZE) return FileResult.error(path, "File too large.");

        String content = Files.readString(filePath, StandardCharsets.UTF_8);
        return FileResult.success(path, content);
    }

    @LlmTool(description = "Writes content to a NEW file. Fails if file exists. Returns FileResult.")
    public FileResult writeFile(String path, String content) throws IOException {
        Path filePath = resolveAndCheckPath(path);
        if (Files.exists(filePath)) return FileResult.error(path, "File already exists.");

        if (filePath.getParent() != null) Files.createDirectories(filePath.getParent());
        Files.writeString(filePath, content, StandardCharsets.UTF_8);
        return FileResult.success(path, "Successfully created and wrote to " + path);
    }

    /**
     * Utility method for agents to safely save generated reports or long-form content.
     * Automatically creates directories and overwrites existing files.
     */
    public String saveGeneratedContent(String filename, String content) throws IOException {
        Path filePath = resolveAndCheckPath("output/" + filename);
        if (filePath.getParent() != null) Files.createDirectories(filePath.getParent());
        Files.writeString(filePath, content, StandardCharsets.UTF_8);
        
        terminal.writer().println(new AttributedString("✔ Content saved to: " + filePath, 
                AttributedStyle.DEFAULT.foreground(AttributedStyle.GREEN)).toAnsi());
        terminal.writer().flush();
        
        return filePath.toString();
    }

    @LlmTool(description = "Lists files in a directory. Returns a list of names.")
    public List<String> listDirectory(String path) throws IOException {
        Path dirPath = resolveAndCheckPath(path);
        try (Stream<Path> stream = Files.list(dirPath)) {
            return stream.map(p -> p.getFileName().toString()).collect(Collectors.toList());
        }
    }

    @LlmTool(description = "Finds files matching glob patterns.")
    public List<String> glob(String pattern) throws IOException {
        Path root = Paths.get(".").toAbsolutePath().normalize();
        final PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + pattern);
        try (Stream<Path> stream = Files.walk(root)) {
            return stream.filter(p -> matcher.matches(root.relativize(p)))
                    .map(p -> root.relativize(p).toString()).collect(Collectors.toList());
        }
    }
}
