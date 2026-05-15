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
    private static final int MAX_BUNDLE_CHARS = 10_000;
    private static final int MAX_DIFF_SEGMENT_CHARS = 10_000;
    private static final int MAX_GUIDE_CONTEXT_CHARS = 8_000;
    private static final int MAX_ANALYSIS_OUTPUT_TOKENS = 8_192;
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

    private enum CommentIntent {
        PRAISE,
        CHECK,
        MUST_FIX,
        SHOULD_FIX,
        INFO
    }

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

                if (currentChars + unitChars > MAX_BUNDLE_CHARS && !currentBundleSegments.isEmpty()) {
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
                            .withMaxTokens(MAX_ANALYSIS_OUTPUT_TOKENS));

            if (standardsRag != null) bundleAi = bundleAi.withReference(standardsRag);
            if (manualsRag != null) bundleAi = bundleAi.withReference(manualsRag);

            String styleGuideContext = truncateContext(context.styleGuide().content(), MAX_GUIDE_CONTEXT_CHARS, "Style Guide");
            String archGuideContext = truncateContext(context.archGuide().content(), MAX_GUIDE_CONTEXT_CHARS, "Architecture Guide");
            logger.info("BitbucketPrReview", String.format(
                    "Bundle input sizes: diff=%d chars, styleGuide=%d/%d chars, archGuide=%d/%d chars, maxOutputTokens=%d",
                    bundle.combinedDiff().length(),
                    styleGuideContext.length(),
                    context.styleGuide().content().length(),
                    archGuideContext.length(),
                    context.archGuide().content().length(),
                    MAX_ANALYSIS_OUTPUT_TOKENS));

            BundleAnalysisResult bundleResult = bundleAi.rendering("agents/bitbucketprapp/analyze-code")
                    .createObject(BundleAnalysisResult.class, Map.ofEntries(
                            Map.entry("fileNames", bundle.fileNames()),
                            Map.entry("diffContent", bundle.combinedDiff()),
                            Map.entry("isTruncated", bundle.isTruncated()),
                            Map.entry("style_guide_content", styleGuideContext),
                            Map.entry("arch_guide_content", archGuideContext),
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
                .filter(c -> c.content() != null && !c.content().isBlank())
                .map(this::normalizeCommentClassification)
                .filter(c -> !shouldSuppressComment(c, context.prMetadata()))
                .collect(Collectors.toList());

        // 1. 중복 제거: 같은 위치의 완전 중복뿐 아니라, 여러 라인에 반복된 동일 의미 코멘트도 1회만 유지한다.
        List<CodeComment> allComments = deduplicateComments(rawComments);

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
        StringBuilder section = new StringBuilder();
        boolean inOverview = false;

        for (String line : lines) {
            String trimmed = line.trim();
            if (!inOverview) {
                Matcher inlineMatcher = overviewInlinePattern().matcher(normalizeHeadingMarkup(trimmed));
                if (inlineMatcher.matches()) {
                    return inlineMatcher.group(1).trim();
                }
                if (isOverviewHeadingLine(trimmed)) {
                    inOverview = true;
                }
                continue;
            }

            if (!section.isEmpty() && isSectionHeadingLine(trimmed)) {
                break;
            }
            section.append(line).append("\n");
        }

        return section.toString().trim();
    }

    private boolean isOverviewHeadingLine(String line) {
        String normalized = normalizeHeadingMarkup(line);
        return overviewHeadingPattern().matcher(normalized).matches();
    }

    private Pattern overviewInlinePattern() {
        return Pattern.compile("(?i)^" + overviewHeadingTerms() + "\\s*[:：-]\\s*(.+)$");
    }

    private Pattern overviewHeadingPattern() {
        return Pattern.compile("(?i)^" + overviewHeadingTerms() + "\\s*[:：-]?\\s*[\\p{Punct}\\p{So}\\p{Sk}\\s]*$");
    }

    private String overviewHeadingTerms() {
        return "(?:(?:(?:pr|pull\\s*request)\\s*)?(?:overview|개요|summary|요약)|(?:변경|작업)\\s*(?:개요|요약))";
    }

    private String normalizeHeadingMarkup(String line) {
        if (line == null) {
            return "";
        }

        String normalized = line.trim();
        normalized = normalized.replaceFirst("^>\\s*", "");
        normalized = normalized.replaceFirst("^#{1,6}\\s*", "");
        normalized = normalized.replaceFirst("(?i)^h[1-6]\\.\\s*", "");
        normalized = normalized.replace("*", "");
        normalized = normalized.replace("_", "");
        return normalized.trim();
    }

    private boolean isSectionHeadingLine(String line) {
        if (line == null || line.isBlank()) {
            return false;
        }

        String trimmed = line.trim();
        if (trimmed.matches("^#{1,6}\\s+.*$") || trimmed.matches("(?i)^h[1-6]\\.\\s+.*$")) {
            return true;
        }

        String normalized = normalizeHeadingMarkup(trimmed);
        return normalized.length() <= 80
                && normalized.matches("^[\\p{L}\\p{N}][\\p{L}\\p{N}\\s/_().-]{1,60}[:：]\\s*$");
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

    private String truncateContext(String text, int maxChars, String label) {
        if (text == null || text.length() <= maxChars) {
            return defaultString(text, "");
        }
        return text.substring(0, maxChars - 120).trim()
                + "\n\n[... " + label + " truncated for PR review latency; use RAG references for additional details if needed ...]";
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

    private CodeComment normalizeCommentClassification(CodeComment comment) {
        CommentIntent intent = classifyCommentIntent(comment);
        String body = stripLeadingReviewLabel(comment.content());

        return switch (intent) {
            case PRAISE -> new CodeComment(
                    comment.fileName(),
                    null,
                    "✅ [GOOD] " + body,
                    "GLOBAL",
                    "SUGGESTION",
                    comment.criteriaId()
            );
            case CHECK -> new CodeComment(
                    comment.fileName(),
                    comment.lineNumber(),
                    "ℹ️ [INFO] " + body,
                    comment.type(),
                    "SUGGESTION",
                    comment.criteriaId()
            );
            case MUST_FIX -> new CodeComment(
                    comment.fileName(),
                    comment.lineNumber(),
                    "🚨 [CRITICAL] " + body,
                    comment.type(),
                    "MUST_FIX",
                    comment.criteriaId()
            );
            case SHOULD_FIX -> new CodeComment(
                    comment.fileName(),
                    comment.lineNumber(),
                    "🟠 [MAJOR] " + body,
                    comment.type(),
                    "SHOULD_FIX",
                    comment.criteriaId()
            );
            case INFO -> new CodeComment(
                    comment.fileName(),
                    comment.lineNumber(),
                    "ℹ️ [INFO] " + body,
                    comment.type(),
                    "SUGGESTION",
                    comment.criteriaId()
            );
        };
    }

    private CommentIntent classifyCommentIntent(CodeComment comment) {
        String normalized = normalizeForIntent(comment.content());
        if ((hasGoodMarker(comment.content())
                || containsPositiveSignal(normalized)
                || containsPraiseOutcomeSignal(normalized))
                && !containsOpenIssueDirective(normalized)) {
            return CommentIntent.PRAISE;
        }

        if (containsCheckSignal(normalized) && !containsExplicitFixDirective(normalized)) {
            return CommentIntent.CHECK;
        }

        if (containsMustFixSignal(normalized)) {
            return CommentIntent.MUST_FIX;
        }

        if (containsConcreteFixSignal(normalized)) {
            return CommentIntent.SHOULD_FIX;
        }

        return CommentIntent.INFO;
    }

    private boolean isPositiveComment(CodeComment comment) {
        if (comment == null || comment.content() == null) {
            return false;
        }
        String normalized = normalizeForIntent(comment.content());
        return (hasGoodMarker(comment.content()) || containsPositiveSignal(normalized))
                && !containsProblemSignal(normalized);
    }

    private boolean shouldSuppressComment(CodeComment comment, PrMetadata prMetadata) {
        return isLenientTestStyleComment(comment)
                || isFalseOverviewMissingComment(comment, prMetadata)
                || isPraiseOnlySeverityMismatch(comment);
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
        if (comment == null || comment.content() == null || prMetadata == null) {
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

        if (!saysOverviewMissing) {
            return false;
        }

        return prMetadata.explicitOverviewProvided()
                || !isBlank(prMetadata.overview())
                || !isBlank(prMetadata.description());
    }

    private List<CodeComment> selectGoodHighlights(List<CodeComment> comments) {
        return comments.stream()
                .filter(this::isPositiveComment)
                .filter(c -> !hasIssueSeverity(c))
                .filter(c -> !containsIssueSeverityLabel(c.content()))
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
            if (diffContent.length() > MAX_DIFF_SEGMENT_CHARS) {
                isTruncated = true;
                diffContent = truncateContext(diffContent, MAX_DIFF_SEGMENT_CHARS, "Diff");
            }

            segments.add(new DiffSegment(fileName, diffContent, isTruncated, totalLines));
        }
        return segments;
    }

    private List<CodeComment> deduplicateComments(List<CodeComment> comments) {
        List<CodeComment> unique = new ArrayList<>();
        Set<String> exactKeys = new HashSet<>();
        List<String> semanticKeys = new ArrayList<>();

        for (CodeComment comment : comments) {
            String exactKey = exactCommentKey(comment);
            if (!exactKeys.add(exactKey)) {
                continue;
            }

            String semanticKey = semanticCommentKey(comment);
            boolean semanticDuplicate = !semanticKey.isBlank()
                    && semanticKeys.stream().anyMatch(existing -> isSemanticallySameComment(existing, semanticKey));
            if (semanticDuplicate) {
                continue;
            }

            unique.add(comment);
            if (!semanticKey.isBlank()) {
                semanticKeys.add(semanticKey);
            }
        }

        return unique;
    }

    private String exactCommentKey(CodeComment comment) {
        return String.join(":",
                defaultString(comment.fileName(), ""),
                String.valueOf(comment.lineNumber()),
                normalizeWhitespace(comment.content()));
    }

    private String semanticCommentKey(CodeComment comment) {
        String normalized = normalizeWhitespace(comment.content()).toLowerCase(Locale.ROOT);
        normalized = normalized.replaceAll("(?i)\\[(critical|major|info|good|must[_\\s-]*fix|should[_\\s-]*fix|suggestion)\\]", " ");
        normalized = normalized.replaceAll("[✅🚨🟠ℹ📌⚠️]", " ");
        normalized = normalized.replaceAll("\\(\\s*관련\\s*파일\\s*:[^)]*\\)", " ");
        normalized = normalized.replaceAll("(?i)\\b(line|라인)\\s*\\d+\\b", " ");
        normalized = normalized.replaceAll("\\s+", " ").trim();
        return normalized;
    }

    private boolean isSemanticallySameComment(String first, String second) {
        if (first.equals(second)) {
            return true;
        }

        Set<String> firstTokens = semanticTokens(first);
        Set<String> secondTokens = semanticTokens(second);
        if (firstTokens.size() < 5 || secondTokens.size() < 5) {
            return false;
        }

        Set<String> intersection = new HashSet<>(firstTokens);
        intersection.retainAll(secondTokens);
        double overlapAgainstSmaller = intersection.size() / (double) Math.min(firstTokens.size(), secondTokens.size());
        double overlapAgainstLarger = intersection.size() / (double) Math.max(firstTokens.size(), secondTokens.size());
        return overlapAgainstSmaller >= 0.90 && overlapAgainstLarger >= 0.75;
    }

    private Set<String> semanticTokens(String text) {
        Set<String> tokens = new HashSet<>();
        Matcher matcher = Pattern.compile("[\\p{L}\\p{N}_]+").matcher(text);
        while (matcher.find()) {
            String token = matcher.group().toLowerCase(Locale.ROOT);
            if (token.length() >= 2) {
                tokens.add(token);
            }
        }
        return tokens;
    }

    private boolean isPraiseOnlySeverityMismatch(CodeComment comment) {
        return isPositiveComment(comment)
                && (hasIssueSeverity(comment) || containsIssueSeverityLabel(comment.content()));
    }

    private boolean hasIssueSeverity(CodeComment comment) {
        if (comment == null || comment.severity() == null) {
            return false;
        }
        String severity = comment.severity().toUpperCase(Locale.ROOT);
        return severity.contains("MUST") || severity.contains("SHOULD");
    }

    private boolean containsIssueSeverityLabel(String content) {
        if (content == null) {
            return false;
        }
        String normalized = content.toLowerCase(Locale.ROOT);
        return normalized.contains("[critical]")
                || normalized.contains("[major]")
                || normalized.contains("[must_fix]")
                || normalized.contains("[must fix]")
                || normalized.contains("[should_fix]")
                || normalized.contains("[should fix]");
    }

    private String normalizeForIntent(String content) {
        return stripLeadingReviewLabel(content)
                .toLowerCase(Locale.ROOT)
                .replace("\uFE0F", "")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private String stripLeadingReviewLabel(String content) {
        String stripped = normalizeWhitespace(content).replace("\uFE0F", "");
        String previous;
        do {
            previous = stripped;
            stripped = stripped.replaceFirst("^(?:[\\s✅🚨🟠ℹ📌⚠:：\\-]+|(?i:\\[(?:critical|major|info|good|must[_\\s-]*fix|should[_\\s-]*fix|suggestion)\\])\\s*)+", "").trim();
        } while (!stripped.equals(previous));
        return stripped.isBlank() ? normalizeWhitespace(content) : stripped;
    }

    private boolean containsPositiveSignal(String normalizedContent) {
        return normalizedContent.contains("잘 사용")
                || normalizedContent.contains("잘 적용")
                || normalizedContent.contains("잘 처리")
                || normalizedContent.contains("잘 해결")
                || normalizedContent.contains("좋습니다")
                || normalizedContent.contains("적절")
                || normalizedContent.contains("효과적")
                || normalizedContent.contains("명확")
                || normalizedContent.contains("깔끔")
                || normalizedContent.contains("일관")
                || normalizedContent.contains("안전하게")
                || normalizedContent.contains("올바르게")
                || normalizedContent.contains("개선되었습니다")
                || normalizedContent.contains("개선했습니다")
                || normalizedContent.contains("해결했습니다")
                || normalizedContent.contains("보장했습니다")
                || normalizedContent.contains("방지했습니다")
                || normalizedContent.contains("향상되었습니다")
                || normalizedContent.contains("향상시켰습니다")
                || normalizedContent.contains("최적화했습니다")
                || normalizedContent.contains("강화합니다")
                || normalizedContent.contains("강화했습니다")
                || normalizedContent.contains("높였습니다")
                || normalizedContent.contains("좋은 설계")
                || normalizedContent.contains("좋은 정리")
                || normalizedContent.contains("중요한 개선")
                || normalizedContent.contains("중요한 수정")
                || normalizedContent.contains("good")
                || normalizedContent.contains("well handled")
                || normalizedContent.contains("appropriate")
                || normalizedContent.contains("clear");
    }

    private boolean containsPraiseOutcomeSignal(String normalizedContent) {
        return normalizedContent.contains("이 변경으로")
                || normalizedContent.contains("수정함으로써")
                || normalizedContent.contains("추가하여")
                || normalizedContent.contains("제거되어")
                || normalizedContent.contains("개선하여")
                || normalizedContent.contains("도입하여")
                || normalizedContent.contains("사용하여")
                || normalizedContent.contains("변경한 것은")
                || normalizedContent.contains("반환하도록 변경")
                || normalizedContent.contains("올바르게 해제")
                || normalizedContent.contains("독립성을 확보")
                || normalizedContent.contains("의존성을 줄")
                || normalizedContent.contains("빌드 시간을 최적화")
                || normalizedContent.contains("효과적으로 방지")
                || normalizedContent.contains("무결성을 보호")
                || normalizedContent.contains("신뢰성을 크게 높")
                || normalizedContent.contains("신뢰성을 높")
                || normalizedContent.contains("신뢰를 강화")
                || normalizedContent.contains("정확성이 향상")
                || normalizedContent.contains("충실도를 높")
                || normalizedContent.contains("복잡도를 줄")
                || normalizedContent.contains("가독성")
                || normalizedContent.contains("재사용성")
                || normalizedContent.contains("더 일치하게")
                || normalizedContent.contains("필수적입니다");
    }

    private boolean hasGoodMarker(String content) {
        if (content == null) {
            return false;
        }
        return content.toLowerCase(Locale.ROOT).contains("[good]") || content.contains("✅");
    }

    private boolean containsCheckSignal(String normalizedContent) {
        return normalizedContent.contains("확인")
                || normalizedContent.contains("검토")
                || normalizedContent.contains("점검")
                || normalizedContent.contains("검증 필요")
                || normalizedContent.contains("검증이 필요")
                || normalizedContent.contains("살펴")
                || normalizedContent.contains("verify")
                || normalizedContent.contains("confirm")
                || normalizedContent.contains("check")
                || normalizedContent.contains("review");
    }

    private boolean containsMustFixSignal(String normalizedContent) {
        return normalizedContent.contains("출시 차단")
                || normalizedContent.contains("릴리즈 차단")
                || normalizedContent.contains("장애")
                || normalizedContent.contains("크래시")
                || normalizedContent.contains("crash")
                || normalizedContent.contains("데이터 손실")
                || normalizedContent.contains("data loss")
                || normalizedContent.contains("보안")
                || normalizedContent.contains("취약")
                || normalizedContent.contains("vulnerab")
                || normalizedContent.contains("sql injection")
                || normalizedContent.contains("인증 우회")
                || normalizedContent.contains("권한 우회")
                || normalizedContent.contains("deadlock")
                || normalizedContent.contains("데드락")
                || normalizedContent.contains("메모리 누수")
                || normalizedContent.contains("memory leak")
                || normalizedContent.contains("표준 위반")
                || normalizedContent.contains("must fix")
                || normalizedContent.contains("must_fix");
    }

    private boolean containsConcreteFixSignal(String normalizedContent) {
        String problemScan = stripResolvedProblemPhrases(normalizedContent);
        if (containsPraiseOutcomeSignal(problemScan) && !containsOpenIssueDirective(problemScan)) {
            return false;
        }
        return containsExplicitFixDirective(problemScan)
                || problemScan.contains("누락")
                || problemScan.contains("위반")
                || problemScan.contains("오류")
                || problemScan.contains("버그")
                || problemScan.contains("불일치")
                || problemScan.contains("잘못")
                || problemScan.contains("미흡")
                || problemScan.contains("부족")
                || problemScan.contains("실패")
                || problemScan.contains("경쟁 상태")
                || problemScan.contains("race condition")
                || problemScan.contains("should fix")
                || problemScan.contains("should_fix");
    }

    private boolean containsExplicitFixDirective(String normalizedContent) {
        String problemScan = stripResolvedProblemPhrases(normalizedContent);
        boolean hasActionVerb = problemScan.contains("수정")
                || problemScan.contains("고쳐")
                || problemScan.contains("보완")
                || problemScan.contains("추가")
                || problemScan.contains("제거")
                || problemScan.contains("변경")
                || problemScan.contains("분리")
                || problemScan.contains("막아")
                || problemScan.contains("차단")
                || problemScan.contains("방지");

        if (containsCheckSignal(problemScan) && !hasActionVerb) {
            return false;
        }

        return problemScan.contains("수정")
                || problemScan.contains("고쳐")
                || problemScan.contains("보완")
                || problemScan.contains("막아")
                || problemScan.contains("차단")
                || problemScan.contains("방지")
                || problemScan.matches(".*(추가|제거|변경|분리).*(필요|해야|하십시오|하세요|권장|누락).*")
                || problemScan.matches(".*(필요|해야|하십시오|하세요|권장).*(추가|제거|변경|분리).*")
                || problemScan.matches(".*(테스트|예외|검증|처리|로직|락|lock|mutex|동기화|상수|타입|매핑|분기|조건).*(필요|해야|하십시오|권장).*")
                || problemScan.matches(".*(필요|해야|하십시오|권장).*(테스트|예외|검증|처리|로직|락|lock|mutex|동기화|상수|타입|매핑|분기|조건).*");
    }

    private boolean containsProblemSignal(String normalizedContent) {
        String problemScan = stripResolvedProblemPhrases(normalizedContent)
                .replaceAll("(?i)\\[(critical|major|info|good|must[_\\s-]*fix|should[_\\s-]*fix|suggestion)\\]", "");
        if (containsPraiseOutcomeSignal(problemScan) && !containsOpenIssueDirective(problemScan)) {
            return false;
        }

        return containsCheckSignal(problemScan)
                || containsMustFixSignal(problemScan)
                || containsConcreteFixSignal(problemScan)
                || problemScan.contains("하지만")
                || problemScan.contains("그러나")
                || problemScan.contains("필요")
                || problemScan.contains("위험")
                || problemScan.contains("문제")
                || problemScan.contains("불가")
                || problemScan.contains("않")
                || problemScan.contains("없")
                || problemScan.contains("해야")
                || problemScan.contains("하십시오")
                || problemScan.contains("권장")
                || problemScan.contains("missing")
                || problemScan.contains("risk")
                || problemScan.contains("invalid")
                || problemScan.contains("should")
                || problemScan.contains("must")
                || problemScan.contains("need");
    }

    private boolean containsOpenIssueDirective(String normalizedContent) {
        String problemScan = normalizedContent.toLowerCase(Locale.ROOT);
        return problemScan.contains("하지만")
                || problemScan.contains("그러나")
                || problemScan.contains("다만")
                || problemScan.contains("필요합니다")
                || problemScan.contains("필요합니다.")
                || problemScan.contains("필요가 있습니다")
                || problemScan.contains("해야 합니다")
                || problemScan.contains("해야합니다")
                || problemScan.contains("하십시오")
                || problemScan.contains("권장합니다")
                || problemScan.contains("확인이 필요")
                || problemScan.contains("검토가 필요")
                || problemScan.contains("검증이 필요")
                || problemScan.contains("누락되어")
                || problemScan.contains("문제가 있습니다")
                || problemScan.contains("문제가 발생")
                || problemScan.contains("발생할 수 있습니다")
                || problemScan.contains("될 수 있습니다")
                || problemScan.contains("should")
                || problemScan.contains("need to")
                || problemScan.contains("must");
    }

    private String stripResolvedProblemPhrases(String normalizedContent) {
        return normalizedContent
                .replace("문제를 해결", "")
                .replace("문제 해결", "")
                .replace("문제가 있었습니다", "")
                .replace("문제가 있더라도", "")
                .replace("위험을 줄", "")
                .replace("위험 감소", "")
                .replace("리스크를 줄", "")
                .replace("리스크 감소", "")
                .replace("메모리 누수나 리소스 충돌을 방지", "")
                .replace("경쟁 조건(race condition)을 효과적으로 방지", "")
                .replace("경쟁 조건을 효과적으로 방지", "")
                .replace("race condition을 효과적으로 방지", "")
                .replace("잘 사용", "")
                .replace("잘 적용", "")
                .replace("잘 처리", "")
                .replace("잘 추가", "")
                .replace("잘 제거", "")
                .replace("잘 변경", "")
                .replace("잘 분리", "")
                .replace("잘 보완", "")
                .replace("risk mitigated", "")
                .replace("risk reduced", "")
                .replace("problem solved", "");
    }

    private String normalizeWhitespace(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ");
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
