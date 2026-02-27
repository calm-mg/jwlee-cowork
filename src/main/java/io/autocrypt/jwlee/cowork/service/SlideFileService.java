package io.autocrypt.jwlee.cowork.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class SlideFileService {

    @Value("${presentation.output.dir:output/slides}")
    private String outputDir;

    @Value("${presentation.merged.file.path:output/slides/final_presentation.md}")
    private String mergedFilePath;

    public void savePage(int pageNumber, String content) throws IOException {
        Path path = Paths.get(outputDir, String.format("page_%d.md", pageNumber));
        Files.createDirectories(path.getParent());
        Files.writeString(path, content);
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

        // Read settings first
        StringBuilder merged = new StringBuilder();
        Path settingsPath = dir.resolve("settings.md");
        if (Files.exists(settingsPath)) {
            merged.append(Files.readString(settingsPath)).append("\n---\n");
        }

        // Read all page_n.md files sorted by number
        List<Path> pages = Files.list(dir)
                .filter(p -> p.getFileName().toString().startsWith("page_") && p.getFileName().toString().endsWith(".md"))
                .sorted(Comparator.comparingInt(this::extractPageNumber))
                .toList();

        for (int i = 0; i < pages.size(); i++) {
            merged.append(Files.readString(pages.get(i)));
            if (i < pages.size() - 1) {
                merged.append("\n---\n"); // Advanced Slides separator
            }
        }

        Path finalPath = Paths.get(mergedFilePath);
        Files.createDirectories(finalPath.getParent()); // Ensure Obsidian folder exists
        Files.writeString(finalPath, merged.toString());
        return finalPath.toString();
    }

    private int extractPageNumber(Path path) {
        String name = path.getFileName().toString();
        return Integer.parseInt(name.replace("page_", "").replace(".md", ""));
    }
}
