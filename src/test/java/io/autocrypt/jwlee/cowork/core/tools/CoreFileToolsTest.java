package io.autocrypt.jwlee.cowork.core.tools;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class CoreFileToolsTest {

    private CoreFileTools fileTools;
    private Path tempDir;

    @BeforeEach
    void setUp() throws IOException {
        fileTools = new CoreFileTools();
        // Create a temporary directory inside the current working directory for safe testing
        tempDir = Files.createDirectories(Paths.get("temp_test_dir"));
        Files.writeString(tempDir.resolve("test.txt"), "Hello, World!");
    }

    @AfterEach
    void tearDown() throws IOException {
        if (Files.exists(tempDir)) {
            try (Stream<Path> walk = Files.walk(tempDir)) {
                walk.sorted(Comparator.reverseOrder())
                    .forEach(p -> {
                        try {
                            Files.delete(p);
                        } catch (IOException e) {
                            // ignore
                        }
                    });
            }
        }
    }

    @Test
    void testReadFile() throws IOException {
        String content = fileTools.readFile("temp_test_dir/test.txt");
        assertEquals("Hello, World!", content);
    }

    @Test
    void testWriteFile() throws IOException {
        String path = "temp_test_dir/new_file.txt";
        String result = fileTools.writeFile(path, "New Content");
        assertEquals("Successfully wrote to " + path, result);
        assertEquals("New Content", Files.readString(Paths.get(path)));
    }

    @Test
    void testReplace() throws IOException {
        String path = "temp_test_dir/test.txt";
        fileTools.replace(path, "World", "Gemini");
        assertEquals("Hello, Gemini!", Files.readString(Paths.get(path)));
    }

    @Test
    void testListDirectory() throws IOException {
        List<String> files = fileTools.listDirectory("temp_test_dir");
        assertTrue(files.contains("test.txt"));
    }

    @Test
    void testGlob() throws IOException {
        List<String> files = fileTools.glob("temp_test_dir/*.txt");
        assertFalse(files.isEmpty());
        assertTrue(files.stream().anyMatch(p -> p.endsWith("test.txt")));
    }

    @Test
    void testPathTraversalProtection() {
        // Attempt to read a file outside the working directory
        assertThrows(IllegalArgumentException.class, () -> {
            fileTools.readFile("../pom.xml"); // Assuming pom.xml is not at ../ relative to root
        });

        // Test absolute path outside workspace
        assertThrows(IllegalArgumentException.class, () -> {
            fileTools.readFile("/etc/passwd");
        });
    }
}
