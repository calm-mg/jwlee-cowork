package io.autocrypt.jwlee.cowork.bitbucketprapp;

import com.embabel.agent.api.annotation.AchievesGoal;
import com.embabel.agent.api.annotation.Action;
import com.embabel.agent.api.annotation.Agent;
import com.embabel.agent.api.annotation.State;
import com.embabel.agent.api.common.ActionContext;
import com.embabel.agent.api.common.Ai;
import com.embabel.common.ai.model.LlmOptions;
import io.autocrypt.jwlee.cowork.core.tools.ConfluenceService;
import io.autocrypt.jwlee.cowork.core.tools.CoreWorkspaceProvider;
import io.autocrypt.jwlee.cowork.core.tools.CoworkLogger;
import io.autocrypt.jwlee.cowork.core.tools.LocalRagTools;
import io.autocrypt.jwlee.cowork.core.workaround.JsonSafeToolishRag;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Agent(description = "Bitbucket PR의 변경사항을 분석하여 논리, 제품 스펙, 스타일 가이드, 테스트 충실도를 검사하는 에이전트")
@Component
public class BitbucketPrReviewAgent {
    private static final int MAX_GOOD_HIGHLIGHTS = 2;
    private static final int MAX_OVERVIEW_CHARS = 1200;
    private static final Map<String, String> LEGACY_SHORT_URL_PAGE_IDS = Map.of(
            "EwBfN", "878641171",
            "iwJOaw", "1800274571"
    );


    private final BitbucketService bitbucketService;
    private final LocalRagTools localRagTools;
    private final CoreWorkspaceProvider workspaceProvider;
    private final ConfluenceService confluenceService;
    private final CoworkLogger logger;

    public BitbucketPrReviewAgent(BitbucketService bitbucketService,
                                  LocalRagTools localRagTools,
                                  CoreWorkspaceProvider workspaceProvider,
                                  ConfluenceService confluenceService,
                                  CoworkLogger logger) {
        this.bitbucketService = bitbucketService;
        this.localRagTools = localRagTools;
        this.workspaceProvider = workspaceProvider;
        this.confluenceService = confluenceService;
        this.logger = logger;
    }

    // DTOs
    public record PrReviewRequest(
            @NotBlank String repositorySlug,
            @Min(1) Long pullRequestId,
            String manualsDir,
            String standardsDir,
            @NotBlank String styleGuideUrl,
            @NotBlank String archGuideUrl
    ) {}

    public record CodeComment(
            String fileName,
            Integer lineNumber,
            String content,
            String type, // "GLOBAL" | "LINE"
            String severity, // "MUST_FIX" | "SHOULD_FIX" | "SUGGESTION"
            String criteriaId
    ) {}

    public record StyleAnalysisResult(
            List<CodeComment> comments,
            int score
    ) {}

    public record ArchAnalysisResult(
            List<CodeComment> comments,
            int score
    ) {}

    public record BundleAnalysisResult(
            StyleAnalysisResult styleResult,
            ArchAnalysisResult archResult
    ) {}

    public record FinalReviewReport(
            int overallScore,
            int styleScore,
            int architectureScore,
            String summary,
            List<CodeComment> globalComments,
            List<CodeComment> lineComments,
            int totalIssuesFound,
            List<String> truncatedFiles
    ) {}

    public record AllAnalysisResults(
            ReadyContext context,
            List<StyleAnalysisResult> styleResults,
            List<ArchAnalysisResult> archResults
    ) {}

    public record DiffSegment(
            String fileName,
            String diffContent,
            boolean isTruncated,
            int totalLines
    ) {}

    public record ConcatenatedDiff(
            List<String> fileNames,
            String combinedDiff,
            boolean isTruncated
    ) {}

    public record GuideDocument(
            String label,
            String url,
            String pageId,
            String content
    ) {}

    public record PrMetadata(
            String title,
            String description,
            String overview,
            boolean explicitOverviewProvided,
            int totalFilesChanged,
            int productionFileCount,
            int testFileCount,
            int addedLines,
            int removedLines,
            int newFileCount,
            boolean testFocused,
            boolean largeFeatureChange
    ) {}

    // States
    @State
    public record InitialState(PrReviewRequest request) {}

    @State
    public record DraftContext(
            PrReviewRequest request,
            List<DiffSegment> diffSegments,
            String manualsRagKey,
            String standardsRagKey,
            GuideDocument styleGuide,
            GuideDocument archGuide,
            PrMetadata prMetadata
    ) {}

    @State
    public record ReadyContext(
            PrReviewRequest request,
            List<ConcatenatedDiff> bundles,
            String manualsRagKey,
            String standardsRagKey,
            GuideDocument styleGuide,
            GuideDocument archGuide,
            PrMetadata prMetadata
    ) {}

    private record OverviewExtraction(
            String text,
            boolean explicit
    ) {}

    @Action
    public DraftContext prepareReviewContext(InitialState state) throws IOException {
        var req = state.request();
        logger.info("BitbucketPrReview", "Preparing review context for PR " + req.pullRequestId());

        // 1. RAG 인스턴스 초기화 (Optional)
        String manualsRagKey = null;
        String standardsRagKey = null;

        if (req.manualsDir() != null && !req.manualsDir().isBlank() && java.nio.file.Files.isDirectory(java.nio.file.Path.of(req.manualsDir()))) {
            manualsRagKey = "manuals-" + workspaceProvider.toSlug(req.manualsDir());
            logger.info("BitbucketPrReview", "Ingesting manuals RAG from " + req.manualsDir() + " as " + manualsRagKey);
            localRagTools.ingestDirectory(req.manualsDir(), manualsRagKey);
        }
        if (req.standardsDir() != null && !req.standardsDir().isBlank() && java.nio.file.Files.isDirectory(java.nio.file.Path.of(req.standardsDir()))) {
            standardsRagKey = "standards-" + workspaceProvider.toSlug(req.standardsDir());
            logger.info("BitbucketPrReview", "Ingesting standards RAG from " + req.standardsDir() + " as " + standardsRagKey);
            localRagTools.ingestDirectory(req.standardsDir(), standardsRagKey);
        }

        // 2. 가이드 내용 가져오기 (Mandatory)
        GuideDocument styleGuide = fetchGuideContent(req.styleGuideUrl(), "Style Guide");
        GuideDocument archGuide = fetchGuideContent(req.archGuideUrl(), "Architecture Guide");

        // 3. Bitbucket PR Diff 가져오기 및 세그먼트 분리
        String workspace = "autocrypt"; 
        String repo = req.repositorySlug();
        if (req.repositorySlug().contains("/")) {
            String[] parts = req.repositorySlug().split("/");
            workspace = parts[0];
            repo = parts[1];
        }

        PullRequestData prData = bitbucketService.fetchPullRequest(workspace, repo, String.valueOf(req.pullRequestId()));
        List<DiffSegment> segments = splitDiff(prData.diff());
        PrMetadata prMetadata = buildPrMetadata(prData, segments);

        return new DraftContext(req, segments, manualsRagKey, standardsRagKey, styleGuide, archGuide, prMetadata);
    }

    private GuideDocument fetchGuideContent(String url, String label) {
        String pageId = extractPageId(url);
        if (pageId == null) {
            throw new IllegalArgumentException(label + " URL에서 Confluence pageId를 찾을 수 없습니다. "
                    + "/wiki/pages/<id> 또는 viewpage.action?pageId=<id> 형식을 사용하세요. "
                    + "기존 short URL은 등록된 항목만 호환됩니다. URL: " + url);
        }

        logger.info("BitbucketPrReview", "Fetching Confluence " + label + " for pageId: " + pageId);
        String content = confluenceService.getPageStorage(pageId);
        if (content == null || content.isBlank()) {
            throw new IllegalStateException(label + " 내용을 Confluence에서 불러오지 못했습니다. "
                    + "pageId=" + pageId + ", URL=" + url + ". 토큰/권한/페이지 존재 여부를 확인하세요.");
        }

        logger.info("BitbucketPrReview", "Loaded " + label + " from pageId " + pageId + " (" + content.length() + " chars)");
        return new GuideDocument(label, url, pageId, content);
    }

    @Action
    public ReadyContext concatenateSegments(DraftContext draft) throws IOException {
        logger.info("BitbucketPrReview", "Concatenating " + draft.diffSegments().size() + " segments into bundles hierarchically");
        
        // 1. 부모 디렉토리별로 그룹화
        Map<String, List<DiffSegment>> groupsByDir = draft.diffSegments().stream()
                .collect(Collectors.groupingBy(s -> {
                    int lastSlash = s.fileName().lastIndexOf('/');
                    return lastSlash == -1 ? "" : s.fileName().substring(0, lastSlash);
                }));

        // 2. 디렉토리 경로명으로 정렬
        List<String> sortedDirs = groupsByDir.keySet().stream().sorted().collect(Collectors.toList());

        List<ConcatenatedDiff> bundles = new ArrayList<>();
        List<DiffSegment> currentBundleSegments = new ArrayList<>();
        int currentChars = 0;
        int maxChars = 20000;

        for (String dir : sortedDirs) {
            List<DiffSegment> dirSegments = new ArrayList<>(groupsByDir.get(dir));
            dirSegments.sort(Comparator.comparing(DiffSegment::fileName));

            for (int i = 0; i < dirSegments.size(); i++) {
                DiffSegment segment = dirSegments.get(i);
                List<DiffSegment> unit = new ArrayList<>();
                unit.add(segment);

                if (i + 1 < dirSegments.size()) {
                    DiffSegment next = dirSegments.get(i + 1);
                    if (isPair(segment.fileName(), next.fileName())) {
                        unit.add(next);
                        i++;
                    }
                }

                int unitChars = unit.stream().mapToInt(s -> s.diffContent().length()).sum();

                if (currentChars + unitChars > maxChars && !currentBundleSegments.isEmpty()) {
                    bundles.add(createBundle(currentBundleSegments));
                    currentBundleSegments = new ArrayList<>();
                    currentChars = 0;
                }

                currentBundleSegments.addAll(unit);
                currentChars += unitChars;
            }
        }

        if (!currentBundleSegments.isEmpty()) {
            bundles.add(createBundle(currentBundleSegments));
        }

        return new ReadyContext(draft.request(), bundles, draft.manualsRagKey(), draft.standardsRagKey(), draft.styleGuide(), draft.archGuide(), draft.prMetadata());
    }

    private boolean isPair(String f1, String f2) {
        String base1 = f1.replaceAll("\\.(c|cpp|cxx|cc|h|hpp|hxx|hh)$", "");
        String base2 = f2.replaceAll("\\.(c|cpp|cxx|cc|h|hpp|hxx|hh)$", "");
        return base1.equals(base2);
    }

    private ConcatenatedDiff createBundle(List<DiffSegment> segments) {
        List<String> names = segments.stream().map(DiffSegment::fileName).collect(Collectors.toList());
        String combined = segments.stream().map(DiffSegment::diffContent).collect(Collectors.joining("\n\n"));
        boolean truncated = segments.stream().anyMatch(DiffSegment::isTruncated);
        return new ConcatenatedDiff(names, combined, truncated);
    }

    private String extractPageId(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }

        String normalized = url.trim();
        if (normalized.matches("\\d+")) {
            return normalized;
        }

        Matcher pathMatcher = Pattern.compile("/pages/(\\d+)").matcher(normalized);
        if (pathMatcher.find()) {
            return pathMatcher.group(1);
        }

        Matcher queryMatcher = Pattern.compile("[?&]pageId=(\\d+)", Pattern.CASE_INSENSITIVE).matcher(normalized);
        if (queryMatcher.find()) {
            return queryMatcher.group(1);
        }

        Matcher shortUrlMatcher = Pattern.compile("/x/([A-Za-z0-9]+)").matcher(normalized);
        if (shortUrlMatcher.find()) {
            return LEGACY_SHORT_URL_PAGE_IDS.get(shortUrlMatcher.group(1));
        }

        return null;
    }

    @Action
    public AllAnalysisResults analyzeAllSegments(ReadyContext context, Ai ai) throws IOException {
        logger.info("BitbucketPrReview", "Starting analysis of " + context.bundles().size() + " bundles");
        List<StyleAnalysisResult> styleResults = new ArrayList<>();
        List<ArchAnalysisResult> archResults = new ArrayList<>();

        // RAG 도구 준비 (조건부)
        JsonSafeToolishRag manualsRag = null;
        JsonSafeToolishRag standardsRag = null;
        if (context.manualsRagKey() != null) {
            manualsRag = new JsonSafeToolishRag("manuals", "제품 매뉴얼 지식", localRagTools.getOrOpenInstance(context.manualsRagKey()));
        }
        if (context.standardsRagKey() != null) {
            standardsRag = new JsonSafeToolishRag("standards", "표준 문서 지식", localRagTools.getOrOpenInstance(context.standardsRagKey()));
        }

        for (ConcatenatedDiff bundle : context.bundles()) {
            logger.info("BitbucketPrReview", "Analyzing bundle: " + bundle.fileNames());

            var bundleAi = ai.withLlm(LlmOptions.withLlmForRole("normal")
                            .withoutThinking()
                            .withTemperature(0.1)
                            .withMaxTokens(65536));

            if (standardsRag != null) bundleAi = bundleAi.withReference(standardsRag);
            if (manualsRag != null) bundleAi = bundleAi.withReference(manualsRag);

            BundleAnalysisResult bundleResult = bundleAi.rendering("agents/bitbucketprapp/analyze-code")
                    .createObject(BundleAnalysisResult.class, Map.ofEntries(
                            Map.entry("fileNames", bundle.fileNames()),
                            Map.entry("diffContent", bundle.combinedDiff()),
                            Map.entry("isTruncated", bundle.isTruncated()),
                            Map.entry("style_guide_content", context.styleGuide().content()),
                            Map.entry("arch_guide_content", context.archGuide().content()),
                            Map.entry("pr_title", context.prMetadata().title()),
                            Map.entry("pr_overview", context.prMetadata().overview()),
                            Map.entry("has_explicit_overview", context.prMetadata().explicitOverviewProvided()),
                            Map.entry("total_files_changed", context.prMetadata().totalFilesChanged()),
                            Map.entry("production_file_count", context.prMetadata().productionFileCount()),
                            Map.entry("test_file_count", context.prMetadata().testFileCount()),
                            Map.entry("added_lines", context.prMetadata().addedLines()),
                            Map.entry("removed_lines", context.prMetadata().removedLines()),
                            Map.entry("new_file_count", context.prMetadata().newFileCount()),
                            Map.entry("is_test_focused_pr", context.prMetadata().testFocused()),
                            Map.entry("is_large_feature_change", context.prMetadata().largeFeatureChange())
                    ));

            StyleAnalysisResult styleResult = bundleResult != null && bundleResult.styleResult() != null
                    ? bundleResult.styleResult()
                    : new StyleAnalysisResult(List.of(), 100);
            ArchAnalysisResult archResult = bundleResult != null && bundleResult.archResult() != null
                    ? bundleResult.archResult()
                    : new ArchAnalysisResult(List.of(), 100);

            styleResults.add(styleResult);
            archResults.add(archResult);
        }
        return new AllAnalysisResults(context, styleResults, archResults);
    }


    @AchievesGoal(description = "모든 분석 결과를 수합하여 최종 리포트를 생성하고 코멘트를 게시함")
    @Action
    public FinalReviewReport synthesizeFinalReport(AllAnalysisResults allResults, ActionContext ctx, Ai ai) {
        var context = allResults.context();
        logger.info("BitbucketPrReview", "Synthesizing final report for PR " + context.request().pullRequestId());

        List<CodeComment> rawComments = new ArrayList<>();
        allResults.styleResults().stream().filter(r -> r.comments() != null).flatMap(r -> r.comments().stream()).forEach(rawComments::add);
        allResults.archResults().stream().filter(r -> r.comments() != null).flatMap(r -> r.comments().stream()).forEach(rawComments::add);
        rawComments = rawComments.stream()
                .filter(Objects::nonNull)
                .filter(c -> !shouldSuppressComment(c, context.prMetadata()))
                .collect(Collectors.toList());

        // 1. 중복 제거 (내용과 위치가 완벽히 동일한 코멘트)
        Map<String, CodeComment> uniqueCommentsMap = new LinkedHashMap<>();
        for (CodeComment c : rawComments) {
            String key = c.fileName() + ":" + c.lineNumber() + ":" + c.content().trim();
            uniqueCommentsMap.put(key, c);
        }
        List<CodeComment> allComments = new ArrayList<>(uniqueCommentsMap.values());

        List<CodeComment> positiveHighlights = selectGoodHighlights(allComments);
        List<CodeComment> issueComments = allComments.stream()
                .filter(c -> !isPositiveComment(c))
                .collect(Collectors.toList());

        // 2. 글로벌/라인 코멘트 분류
        List<CodeComment> globalIssueComments = issueComments.stream()
                .filter(c -> c.lineNumber() == null || "GLOBAL".equals(c.type()))
                .collect(Collectors.toList());

        List<CodeComment> globalComments = new ArrayList<>(globalIssueComments);
        globalComments.addAll(positiveHighlights);

        List<CodeComment> lineComments = issueComments.stream()
                .filter(c -> c.lineNumber() != null && !"GLOBAL".equals(c.type()))
                .sorted(Comparator.comparing(CodeComment::fileName, Comparator.nullsLast(String::compareTo))
                        .thenComparing(CodeComment::lineNumber, Comparator.nullsLast(Integer::compareTo)))
                .collect(Collectors.toList());

        // 3. 점수 계산
        double avgStyle = allResults.styleResults().stream().mapToInt(StyleAnalysisResult::score).average().orElse(100);
        double avgArch = allResults.archResults().stream().mapToInt(ArchAnalysisResult::score).average().orElse(100);
        int styleScore = (int) Math.round(avgStyle);
        int architectureScore = (int) Math.round(avgArch);
        int totalScore = (int) Math.round((avgStyle + avgArch) / 2.0);

        // 4. LLM을 사용한 전역 발견 사항(Key Findings) 수합 및 재작성 (더 강력한 performant 모델 사용)
        String synthesizedFindings = "";
        if (!globalIssueComments.isEmpty()) {
            if (globalIssueComments.size() == 1) {
                synthesizedFindings = formatSingleFinding(globalIssueComments.get(0));
            } else {
                // LLM이 파일 정보를 인지할 수 있도록 [파일명]을 접두어로 붙여서 전달
                String rawFindingsText = globalIssueComments.stream()
                        .map(c -> String.format("[File: %s] %s", c.fileName(), c.content()))
                        .collect(Collectors.joining("\n---\n"));

                synthesizedFindings = ai.withLlm(LlmOptions.withLlmForRole("performant").withoutThinking())
                        .rendering("agents/bitbucketprapp/synthesize-findings")
                        .generateText(Map.of("raw_findings", rawFindingsText));
            }
        }

        List<String> truncatedFiles = context.bundles().stream()
                .filter(ConcatenatedDiff::isTruncated)
                .flatMap(b -> b.fileNames().stream())
                .distinct()
                .collect(Collectors.toList());

        // 5. 요약 리포트 생성
        StringBuilder summaryBuilder = new StringBuilder();
        summaryBuilder.append("## 🤖 AI Code Review Summary\n");
        summaryBuilder.append(String.format("### 📊 Overall Score: **%d/100**\n", totalScore));
        summaryBuilder.append(String.format("- ✨ **Style Score**: %d/100\n", styleScore));
        summaryBuilder.append(String.format("- 🏗️ **Architecture Score**: %d/100\n\n", architectureScore));
        summaryBuilder.append("### 📚 Reference Inputs\n");
        summaryBuilder.append("- ").append(formatGuideSummary(context.styleGuide())).append("\n");
        summaryBuilder.append("- ").append(formatGuideSummary(context.archGuide())).append("\n");
        summaryBuilder.append("- Standards RAG: ").append(formatRagSummary(context.request().standardsDir(), context.standardsRagKey())).append("\n");
        summaryBuilder.append("- Manuals RAG: ").append(formatRagSummary(context.request().manualsDir(), context.manualsRagKey())).append("\n\n");

        if (!synthesizedFindings.isBlank()) {
            summaryBuilder.append("### 💡 Key Findings\n");
            summaryBuilder.append(synthesizedFindings).append("\n\n");
        }

        if (!positiveHighlights.isEmpty()) {
            summaryBuilder.append("### 👍 Notable Strengths\n");
            for (CodeComment c : positiveHighlights) {
                summaryBuilder.append("- ").append(formatPositiveHighlight(c)).append("\n");
            }
            summaryBuilder.append("\n");
        }

        if (!truncatedFiles.isEmpty()) {
            summaryBuilder.append("### ⚠️ Analysis Limitations\n");
            summaryBuilder.append("다음 파일들은 크기가 너무 커서 일부분만 분석되었습니다:\n");
            for (String f : truncatedFiles) {
                summaryBuilder.append(String.format("- `%s`\n", f));
            }
        }

        FinalReviewReport report = new FinalReviewReport(
                totalScore,
                styleScore,
                architectureScore,
                summaryBuilder.toString(),
                globalComments,
                lineComments,
                issueComments.size(),
                truncatedFiles
        );

        // 6. Bitbucket 게시
        String workspace = "autocrypt"; 
        String repo = context.request().repositorySlug();
        if (context.request().repositorySlug().contains("/")) {
            String[] parts = context.request().repositorySlug().split("/");
            workspace = parts[0];
            repo = parts[1];
        }

        bitbucketService.postGlobalComment(workspace, repo, String.valueOf(context.request().pullRequestId()), report.summary());

        for (CodeComment lc : lineComments) {
            if (lc.fileName() == null) continue;
            bitbucketService.postLineComment(workspace, repo, String.valueOf(context.request().pullRequestId()), 
                    lc.fileName(), lc.lineNumber() != null ? lc.lineNumber() : 1, lc.content());
        }

        for (ConcatenatedDiff bundle : context.bundles()) {
            if (bundle.isTruncated()) {
                for (String fileName : bundle.fileNames()) {
                    bitbucketService.postLineComment(workspace, repo, String.valueOf(context.request().pullRequestId()), 
                            fileName, 1, "⚠️ 이 파일은 길이가 길어 상단부만 분석되었습니다.");
                }
            }
        }

        return report;
    }

    private PrMetadata buildPrMetadata(PullRequestData prData, List<DiffSegment> segments) {
        String rawDiff = prData.diff() == null ? "" : prData.diff();
        OverviewExtraction overviewExtraction = buildOverview(prData.title(), prData.description());
        int totalFilesChanged = segments.size();
        int testFileCount = (int) segments.stream().map(DiffSegment::fileName).filter(this::isTestFile).count();
        int productionFileCount = Math.max(0, totalFilesChanged - testFileCount);
        int addedLines = countChangedLines(rawDiff, '+');
        int removedLines = countChangedLines(rawDiff, '-');
        int newFileCount = countNewFiles(rawDiff);
        int touchedAreas = (int) segments.stream()
                .map(DiffSegment::fileName)
                .map(this::topLevelArea)
                .filter(area -> !area.isBlank())
                .distinct()
                .count();
        boolean testFocused = testFileCount > 0 && productionFileCount <= 1 && testFileCount >= productionFileCount;
        boolean largeFeatureChange = productionFileCount >= 4
                || newFileCount >= 2
                || addedLines + removedLines >= 250
                || (productionFileCount >= 2 && touchedAreas >= 3 && addedLines + removedLines >= 150);

        return new PrMetadata(
                defaultString(prData.title(), "Untitled PR"),
                defaultString(prData.description(), ""),
                overviewExtraction.text(),
                overviewExtraction.explicit(),
                totalFilesChanged,
                productionFileCount,
                testFileCount,
                addedLines,
                removedLines,
                newFileCount,
                testFocused,
                largeFeatureChange
        );
    }

    private OverviewExtraction buildOverview(String title, String description) {
        String extracted = extractOverviewSection(description);
        if (extracted != null && !extracted.isBlank()) {
            return new OverviewExtraction(truncateText(extracted, MAX_OVERVIEW_CHARS), true);
        }

        String fallback = firstMeaningfulParagraph(description);
        if (fallback != null && !fallback.isBlank()) {
            return new OverviewExtraction(truncateText(fallback, MAX_OVERVIEW_CHARS), false);
        }

        return new OverviewExtraction(truncateText(defaultString(title, ""), MAX_OVERVIEW_CHARS), false);
    }

    private String extractOverviewSection(String description) {
        if (description == null || description.isBlank()) {
            return "";
        }

        String normalized = description.replace("\r\n", "\n");
        String[] lines = normalized.split("\n");
        Pattern markdownHeadingPattern = Pattern.compile("(?i)^#{1,6}\\s*(?:pr\\s*)?(overview|개요)\\s*[:：-]?\\s*$");
        Pattern plainHeadingPattern = Pattern.compile("(?i)^\\*{0,2}\\s*(?:pr\\s*)?(overview|개요)\\s*\\*{0,2}\\s*[:：-]?\\s*$");
        Pattern inlineOverviewPattern = Pattern.compile("(?i)^\\*{0,2}\\s*(?:pr\\s*)?(overview|개요)\\s*\\*{0,2}\\s*[:：-]\\s*(.+)$");
        Pattern anyHeadingPattern = Pattern.compile("^#{1,6}\\s+.*$");
        StringBuilder section = new StringBuilder();
        boolean inOverview = false;

        for (String line : lines) {
            String trimmed = line.trim();
            if (!inOverview) {
                Matcher inlineMatcher = inlineOverviewPattern.matcher(trimmed);
                if (inlineMatcher.matches()) {
                    return inlineMatcher.group(2).trim();
                }
                if (markdownHeadingPattern.matcher(trimmed).matches() || plainHeadingPattern.matcher(trimmed).matches()) {
                    inOverview = true;
                }
                continue;
            }

            if (anyHeadingPattern.matcher(trimmed).matches()
                    || (plainHeadingPattern.matcher(trimmed).matches() && !section.isEmpty())) {
                break;
            }
            section.append(line).append("\n");
        }

        return section.toString().trim();
    }

    private String firstMeaningfulParagraph(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }

        String normalized = text.replace("\r\n", "\n").trim();
        String[] paragraphs = normalized.split("\\n\\s*\\n");
        for (String paragraph : paragraphs) {
            String candidate = paragraph.trim();
            if (!candidate.isBlank() && !candidate.startsWith("#")) {
                return candidate;
            }
        }
        return normalized;
    }

    private String truncateText(String text, int maxChars) {
        if (text == null) {
            return "";
        }
        if (text.length() <= maxChars) {
            return text;
        }
        return text.substring(0, maxChars - 3).trim() + "...";
    }

    private String defaultString(String value, String fallback) {
        return value == null ? fallback : value;
    }

    private int countChangedLines(String rawDiff, char prefix) {
        int count = 0;
        for (String line : rawDiff.split("\n")) {
            if (line.isEmpty()) {
                continue;
            }
            if (line.startsWith("+++ ") || line.startsWith("--- ") || line.startsWith("@@") || line.startsWith("diff --git")) {
                continue;
            }
            if (line.charAt(0) == prefix) {
                count++;
            }
        }
        return count;
    }

    private int countNewFiles(String rawDiff) {
        int count = 0;
        for (String line : rawDiff.split("\n")) {
            if (line.startsWith("new file mode ")) {
                count++;
            }
        }
        return count;
    }

    private String topLevelArea(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return "";
        }
        int firstSlash = fileName.indexOf('/');
        return firstSlash == -1 ? fileName : fileName.substring(0, firstSlash);
    }

    private boolean isTestFile(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return false;
        }
        String normalized = fileName.toLowerCase(Locale.ROOT);
        if (normalized.contains("/src/test/")
                || normalized.contains("/test/")
                || normalized.contains("/tests/")
                || normalized.contains("__tests__")
                || normalized.contains("/fixtures/")
                || normalized.contains("/mocks/")) {
            return true;
        }

        String simpleName = normalized.substring(normalized.lastIndexOf('/') + 1);
        return simpleName.matches(".*(test|tests|spec|it)(\\.[^.]+)+$");
    }

    private boolean isPositiveComment(CodeComment comment) {
        if (comment == null || comment.content() == null) {
            return false;
        }
        String normalized = comment.content().toLowerCase(Locale.ROOT);
        return normalized.contains("[good]") || normalized.contains("✅");
    }

    private boolean shouldSuppressComment(CodeComment comment, PrMetadata prMetadata) {
        return isLenientTestStyleComment(comment) || isFalseOverviewMissingComment(comment, prMetadata);
    }

    private boolean isLenientTestStyleComment(CodeComment comment) {
        if (comment == null || !isTestFile(comment.fileName()) || comment.content() == null) {
            return false;
        }

        String normalized = comment.content().toLowerCase(Locale.ROOT);
        boolean magicNumberNit = normalized.contains("매직 넘버")
                || normalized.contains("magic number")
                || normalized.contains("magic-number")
                || normalized.contains("magic literal")
                || normalized.contains("하드코딩")
                || normalized.contains("의미 있는 상수");

        if (!magicNumberNit) {
            return false;
        }

        boolean flakyRisk = normalized.contains("flake")
                || normalized.contains("flaky")
                || normalized.contains("불안정")
                || normalized.contains("타이밍 이슈")
                || normalized.contains("timeout")
                || normalized.contains("타임아웃")
                || normalized.contains("race")
                || normalized.contains("경쟁 상태");

        return !flakyRisk;
    }

    private boolean isFalseOverviewMissingComment(CodeComment comment, PrMetadata prMetadata) {
        if (comment == null || comment.content() == null || prMetadata == null || !prMetadata.explicitOverviewProvided()) {
            return false;
        }

        String normalized = comment.content().toLowerCase(Locale.ROOT);
        boolean saysOverviewMissing = normalized.contains("overview 누락")
                || normalized.contains("pr overview 누락")
                || normalized.contains("overview가 비어")
                || normalized.contains("overview 비어")
                || normalized.contains("overview 없음")
                || normalized.contains("개요 누락")
                || normalized.contains("개요가 비어")
                || normalized.contains("개요 없음");

        return saysOverviewMissing;
    }

    private List<CodeComment> selectGoodHighlights(List<CodeComment> comments) {
        return comments.stream()
                .filter(this::isPositiveComment)
                .filter(c -> c.lineNumber() == null || "GLOBAL".equals(c.type()))
                .sorted(Comparator.comparing((CodeComment c) -> c.fileName() == null ? "" : c.fileName())
                        .thenComparing(c -> c.content() == null ? "" : c.content()))
                .limit(MAX_GOOD_HIGHLIGHTS)
                .collect(Collectors.toList());
    }

    private String formatPositiveHighlight(CodeComment comment) {
        if (comment.fileName() == null || comment.fileName().isBlank()) {
            return comment.content();
        }
        return comment.content() + " (`" + comment.fileName() + "`)";
    }

    private String formatSingleFinding(CodeComment comment) {
        if (comment.fileName() == null || comment.fileName().isBlank()) {
            return "- " + comment.content();
        }
        return "- " + comment.content() + " (관련 파일: " + comment.fileName() + ")";
    }

    private String formatGuideSummary(GuideDocument guide) {
        return String.format("%s: pageId=%s, url=%s", guide.label(), guide.pageId(), guide.url());
    }

    private String formatRagSummary(String directory, String ragKey) {
        if (ragKey == null || directory == null || directory.isBlank()) {
            return "not used";
        }
        return String.format("loaded from `%s` (key=%s)", directory, ragKey);
    }

    private List<DiffSegment> splitDiff(String rawDiff) {
        List<DiffSegment> segments = new ArrayList<>();
        if (rawDiff == null || rawDiff.isBlank()) return segments;

        String[] parts = rawDiff.split("diff --git ");
        for (String part : parts) {
            if (part.isBlank()) continue;

            String[] lines = part.split("\n");
            String firstLine = lines[0];
            String fileName = "unknown";
            int bIdx = firstLine.indexOf(" b/");
            if (bIdx != -1) {
                fileName = firstLine.substring(bIdx + 3).trim();
            }

            int totalLines = lines.length;
            boolean isTruncated = false;
            String diffContent = "diff --git " + part;

            if (totalLines > 500) {
                isTruncated = true;
                diffContent = diffContent.lines().limit(500).collect(Collectors.joining("\n")) + "\n... (Truncated) ...";
            }

            segments.add(new DiffSegment(fileName, diffContent, isTruncated, totalLines));
        }
        return segments;
    }
}
