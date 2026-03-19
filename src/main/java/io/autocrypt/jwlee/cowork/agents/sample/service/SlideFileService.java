package io.autocrypt.jwlee.cowork.agents.sample.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.List;

@Service
public class SlideFileService {

    @Value("${presentation.output.dir:output/slides}")
    private String outputDir;

    @Value("${presentation.merged.file.path:output/slides/final_presentation.md}")
    private String mergedFilePath;

    public void savePage(int pageNumber, String templateName, String contentMarkdown) throws IOException {
        Path path = Paths.get(outputDir, String.format("page_%d.md", pageNumber));
        Files.createDirectories(path.getParent());
        
        // Deterministically wrap the AI content with template declaration
        String wrappedContent = String.format("<!-- slide template=\"[[%s]]\" -->\n\n%s", 
                templateName.trim(), contentMarkdown.trim());
        
        Files.writeString(path, wrappedContent);
    }

    public String readPage(int pageNumber) throws IOException {
        Path path = Paths.get(outputDir, String.format("page_%d.md", pageNumber));
        if (!Files.exists(path)) return null;
        return Files.readString(path);
    }

    public void saveSettings(String settings) throws IOException {
        Path path = Paths.get(outputDir, "settings.md");
        Files.createDirectories(path.getParent());
        Files.writeString(path, settings);
    }

    public String mergeAll() throws IOException {
        Path dir = Paths.get(outputDir);
        if (!Files.exists(dir)) return "";

        StringBuilder merged = new StringBuilder();
        
        // 1. Add settings at the top (no separator after settings)
        Path settingsPath = dir.resolve("settings.md");
        if (Files.exists(settingsPath)) {
            merged.append(Files.readString(settingsPath).trim()).append("\n\n");
        }

        // 2. Add all pages with exactly one '---' between them
        List<Path> pages = Files.list(dir)
                .filter(p -> p.getFileName().toString().startsWith("page_") && p.getFileName().toString().endsWith(".md"))
                .sorted(Comparator.comparingInt(this::extractPageNumber))
                .toList();

        for (int i = 0; i < pages.size(); i++) {
            String content = Files.readString(pages.get(i)).trim();
            merged.append(content);
            if (i < pages.size() - 1) {
                merged.append("\n\n---\n\n"); // Deterministic separator
            }
        }

        Path finalPath = Paths.get(mergedFilePath);
        Files.createDirectories(finalPath.getParent());
        Files.writeString(finalPath, merged.toString());
        return finalPath.toString();
    }

    private int extractPageNumber(Path path) {
        String name = path.getFileName().toString();
        return Integer.parseInt(name.replace("page_", "").replace(".md", ""));
    }
}
