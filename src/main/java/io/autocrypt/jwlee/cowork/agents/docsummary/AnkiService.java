package io.autocrypt.jwlee.cowork.agents.docsummary;

import io.autocrypt.jwlee.cowork.core.hitl.ApplicationContextHolder;
import io.autocrypt.jwlee.cowork.core.hitl.NotificationEvent;
import io.autocrypt.jwlee.cowork.core.tools.CoreWorkspaceProvider;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@Service
public class AnkiService {

    private final CoreWorkspaceProvider workspaceProvider;
    private static final String AGENT_NAME = "docsummary";

    public AnkiService(CoreWorkspaceProvider workspaceProvider) {
        this.workspaceProvider = workspaceProvider;
    }

    public String generateAnkiCsv(String workspaceName, List<DocSummaryAgent.DefinedTerm> terms) throws IOException {
        StringBuilder csv = new StringBuilder();
        for (DocSummaryAgent.DefinedTerm term : terms) {
            csv.append(escapeCsv(term.term())).append(",").append(escapeCsv(term.definition())).append("\n");
        }

        Path exportPath = workspaceProvider.getSubPath(AGENT_NAME, workspaceName, CoreWorkspaceProvider.SubCategory.EXPORT);
        Path csvFile = exportPath.resolve("anki_cards_ko.csv");
        Files.writeString(csvFile, csv.toString());

        // PUBLISH NOTIFICATION
        ApplicationContextHolder.getPublisher().publishEvent(
            new NotificationEvent("Anki 생성 완료", 
                String.format("'%s' 문서에서 %d개의 카드가 생성되었습니다.", workspaceName, terms.size()))
        );

        return csvFile.toAbsolutePath().toString();
    }

    private String escapeCsv(String text) {
        if (text == null) return "";
        String escaped = text.replace("\"", "\"\"");
        if (escaped.contains(",") || escaped.contains("\"") || escaped.contains("\n")) {
            return "\"" + escaped + "\"";
        }
        return escaped;
    }
}
