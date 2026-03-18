package io.autocrypt.jwlee.cowork.core.tools;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CoreShellToolsTest {

    private CoreShellTools shellTools;

    @BeforeEach
    void setUp() {
        shellTools = new CoreShellTools();
    }

    @Test
    void testRunShellCommand() throws IOException, InterruptedException {
        String result = shellTools.runShellCommand("echo 'Hello'");
        assertTrue(result.contains("Hello"));
    }

    @Test
    void testRunShellCommandFailure() throws IOException, InterruptedException {
        String result = shellTools.runShellCommand("ls nonexistent_file_xyz");
        assertTrue(result.contains("[Exit Code"));
    }

    @Test
    void testGrepSearch() throws IOException {
        // Search for a specific string we know exists in pom.xml
        List<String> results = shellTools.grepSearch("embabel-agent\\.version", "pom.xml");
        assertFalse(results.isEmpty());
        assertTrue(results.get(0).contains("pom.xml"));
    }
}
