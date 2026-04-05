package io.autocrypt.jwlee.cowork.docdiffagent;

import com.embabel.agent.api.annotation.AchievesGoal;
import com.embabel.agent.api.annotation.Action;
import com.embabel.agent.api.annotation.Agent;
import com.embabel.agent.api.common.ActionContext;
import com.embabel.common.ai.model.LlmOptions;
import io.autocrypt.jwlee.cowork.core.prompts.PromptProvider;
import io.autocrypt.jwlee.cowork.core.tools.BashTool;
import io.autocrypt.jwlee.cowork.core.tools.CoreWorkspaceProvider;
import io.autocrypt.jwlee.cowork.core.tools.CoworkLogger;
import io.autocrypt.jwlee.cowork.core.tools.FileReadTool;
import io.autocrypt.jwlee.cowork.core.tools.FileWriteTool;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * DocDiffAgent using a simplified two-stage GOAP path to avoid Stuck states.
 * 1. DiffRequest -> TOCMapResult (Mapping)
 * 2. TOCMapResult + DiffRequest -> DocDiffReport (Full-Scan Analysis)
 */
@Agent(description = "기술 문서의 버전 간 차이점(추가/삭제/변경)을 상세히 분석하여 보고서를 생성하는 에이전트")
@Component
public class DocDiffAgent {

    private final BashTool bashTool;
    private final FileReadTool fileReadTool;
    private final FileWriteTool fileWriteTool;
    private final CoreWorkspaceProvider workspaceProvider;
    private final CoworkLogger logger;
    private final PromptProvider promptProvider;

    public DocDiffAgent(BashTool bashTool, 
                        FileReadTool fileReadTool, 
                        FileWriteTool fileWriteTool,
                        CoreWorkspaceProvider workspaceProvider,
                        CoworkLogger logger, 
                        PromptProvider promptProvider) {
        this.bashTool = bashTool;
        this.fileReadTool = fileReadTool;
        this.fileWriteTool = fileWriteTool;
        this.workspaceProvider = workspaceProvider;
        this.logger = logger;
        this.promptProvider = promptProvider;
    }

    // --- Actions ---

    /**
     * Stage 1: Extracts TOCs for both versions and creates a Mapping result.
     */
    @Action
    public TOCMapResult prepareAnalysisMap(DiffRequest request, ActionContext ctx) throws IOException {
        logger.info("DocDiffAgent", "Step 1: Extracting and mapping TOCs...");
        
        TOCResult sourceTOC = extractTOC(request.source());
        TOCResult targetTOC = extractTOC(request.target());

        String prompt = promptProvider.getPrompt("agents/docdiff/map-toc.jinja", Map.of(
                "sourceVersion", request.source().version(),
                "targetVersion", request.target().version(),
                "sourceTOC", sourceTOC.entries(),
                "targetTOC", targetTOC.entries()
        ));

        return ctx.ai().withLlm(LlmOptions.withLlmForRole("normal"))
                .creating(TOCMapResult.class)
                .fromPrompt(prompt);
    }

    /**
     * Stage 2: Performs full-scan analysis of every section and synthesizes the final report.
     */
    @AchievesGoal(description = "기술 문서 버전 차이 전수 분석 및 상세 보고서 생성")
    @Action
    public DocDiffReport generateDetailedReport(TOCMapResult mapResult, DiffRequest request, ActionContext ctx) throws IOException {
        logger.info("DocDiffAgent", "Step 2: Performing full-scan analysis of all sections...");
        
        List<SectionDiff> allDiffs = new ArrayList<>();

        // Analyze Modified Sections
        for (MappedSection mapped : mapResult.modified()) {
            logger.info("DocDiffAgent", "  Analyzing modified section: " + mapped.target().title());
            allDiffs.add(internalAnalyzeContent(mapped, request.source(), request.target(), ctx));
        }

        // Analyze Added Sections
        for (TOCEntry added : mapResult.added()) {
            logger.info("DocDiffAgent", "  Analyzing new section: " + added.title());
            allDiffs.add(internalAnalyzeAdded(added, request.target(), ctx));
        }

        // Synthesize Final Report
        String synthPrompt = promptProvider.getPrompt("agents/docdiff/synthesize-report.jinja", Map.of(
                "mapResult", mapResult,
                "sectionDiffs", allDiffs
        ));

        DocDiffReport report = ctx.ai().withLlm(LlmOptions.withLlmForRole("performant").withMaxTokens(65536))
                .creating(DocDiffReport.class)
                .fromPrompt(synthPrompt);

        // Save to Workspace
        String workspaceId = "doc-diff-" + request.source().version().replace(".", "") + "-to-" + request.target().version().replace(".", "");
        Path artifactPath = workspaceProvider.getSubPath("DocDiffAgent", workspaceId, CoreWorkspaceProvider.SubCategory.ARTIFACTS);
        fileWriteTool.writeFile(artifactPath.resolve("diff-report.md").toString(), report.content());
        
        logger.info("DocDiffAgent", "✅ Final report saved to: " + artifactPath.toAbsolutePath());
        return report;
    }

    // --- Public Helpers (for testing) ---

    public TOCResult extractTOC(DocVersion docVersion) throws IOException {
        String command = String.format("grep -nE '^#+ |^=+ |<h[1-6]' %s", docVersion.filePath());
        String resultJson = bashTool.execute(command);
        String stdout = "";
        try {
            Pattern p = Pattern.compile("\"stdout\":\"(.*?)\",\"message\"", Pattern.DOTALL);
            Matcher m = p.matcher(resultJson);
            if (m.find()) stdout = m.group(1).replace("\\n", "\n").replace("\\\"", "\"");
        } catch (Exception e) { logger.error("DocDiffAgent", "BashTool parse error", e); }

        List<TOCEntry> entries = new ArrayList<>();
        for (String line : stdout.split("\n")) {
            if (line.isBlank()) continue;
            try {
                int colonIdx = line.indexOf(':');
                if (colonIdx == -1) continue;
                int lineNum = Integer.parseInt(line.substring(0, colonIdx));
                String content = line.substring(colonIdx + 1).trim();
                int level = 1;
                if (content.startsWith("#")) { while (level < content.length() && content.charAt(level) == '#') level++; }
                else if (content.startsWith("=")) { while (level < content.length() && content.charAt(level) == '=') level++; }
                String title = content.replaceAll("^#+\\s*|^=+\\s*", "").trim();
                entries.add(new TOCEntry(title, level, lineNum, 0));
            } catch (Exception ignored) {}
        }
        List<TOCEntry> finalized = new ArrayList<>();
        for (int i = 0; i < entries.size(); i++) {
            int nextStart = (i + 1 < entries.size()) ? entries.get(i + 1).startLine() - 1 : Integer.MAX_VALUE;
            TOCEntry c = entries.get(i);
            finalized.add(new TOCEntry(c.title(), c.level(), c.startLine(), nextStart));
        }
        return new TOCResult(finalized);
    }

    private SectionDiff internalAnalyzeContent(MappedSection mapped, DocVersion sourceVer, DocVersion targetVer, ActionContext ctx) throws IOException {
        String sContent = fileReadTool.readFileWithRange(sourceVer.filePath(), mapped.source().startLine(), mapped.source().endLine()).content();
        String tContent = fileReadTool.readFileWithRange(targetVer.filePath(), mapped.target().startLine(), mapped.target().endLine()).content();
        String prompt = promptProvider.getPrompt("agents/docdiff/compare-content.jinja", Map.of(
                "title", mapped.target().title(),
                "sourceContent", sContent,
                "targetContent", tContent
        ));
        return ctx.ai().withLlm(LlmOptions.withLlmForRole("performant").withMaxTokens(65536)).creating(SectionDiff.class).fromPrompt(prompt);
    }

    private SectionDiff internalAnalyzeAdded(TOCEntry addedEntry, DocVersion targetVer, ActionContext ctx) throws IOException {
        String content = fileReadTool.readFileWithRange(targetVer.filePath(), addedEntry.startLine(), addedEntry.endLine()).content();
        String prompt = String.format("""
                당신은 신규 기능 분석 전문가입니다. 다음 새롭게 추가된 섹션의 내용을 기술적으로 상세히 분석하십시오.
                # 섹션 제목: %s
                # 내용: %s
                # 분석 요구 사항: 핵심 API 추출, 도입 목적 및 이득 서술.
                """, addedEntry.title(), content);
        SectionDiff diff = ctx.ai().withLlm(LlmOptions.withLlmForRole("performant").withMaxTokens(65536)).creating(SectionDiff.class).fromPrompt(prompt);
        return new SectionDiff(addedEntry.title(), "ADDED", diff.technicalSummary(), diff.impact(), false);
    }
}
