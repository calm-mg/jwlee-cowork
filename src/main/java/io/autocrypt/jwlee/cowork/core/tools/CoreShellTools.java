package io.autocrypt.jwlee.cowork.core.tools;

import com.embabel.agent.api.annotation.LlmTool;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Core shell and search tools for agents.
 */
@Component
public class CoreShellTools {

    @LlmTool(description = "Executes a bash shell command. Returns combined stdout and stderr.")
    public String runShellCommand(String command) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder("bash", "-c", command);
        pb.redirectErrorStream(true);
        Process process = pb.start();
        
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
        }
        
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            return "[Exit Code " + exitCode + "]\n" + output.toString();
        }
        return output.toString();
    }

    @LlmTool(description = "Searches for a regular expression pattern within file contents. Returns matching lines with file paths.")
    public List<String> grepSearch(String pattern, String includePattern) throws IOException {
        Path root = Paths.get(".").toAbsolutePath().normalize();
        Pattern regex = Pattern.compile(pattern, Pattern.CASE_INSENSITIVE);
        final PathMatcher matcher = (includePattern != null && !includePattern.isEmpty()) 
                ? FileSystems.getDefault().getPathMatcher("glob:" + includePattern) 
                : null;
        
        List<String> results = new ArrayList<>();
        
        try (Stream<Path> stream = Files.walk(root)) {
            stream.filter(Files::isRegularFile)
                  .filter(p -> {
                      if (matcher == null) return true;
                      Path rel = root.relativize(p);
                      return matcher.matches(rel);
                  })
                  .forEach(p -> {
                      try {
                          List<String> lines = Files.readAllLines(p, StandardCharsets.UTF_8);
                          for (int i = 0; i < lines.size(); i++) {
                              if (regex.matcher(lines.get(i)).find()) {
                                  // Use relative path in results
                                  String relPath = root.relativize(p).toString();
                                  results.add(relPath + ":" + (i + 1) + ":" + lines.get(i));
                              }
                          }
                      } catch (Exception ignored) {
                          // Skip binary files or unreadable files
                      }
                  });
        }
        return results;
    }
}
