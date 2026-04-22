package io.autocrypt.jwlee.cowork.bitbucketprapp;

import com.embabel.agent.test.unit.FakeOperationContext;
import com.embabel.agent.test.unit.FakePromptRunner;
import com.embabel.agent.rag.lucene.LuceneSearchOperations;
import io.autocrypt.jwlee.cowork.core.tools.ConfluenceService;
import io.autocrypt.jwlee.cowork.core.tools.CoworkLogger;
import io.autocrypt.jwlee.cowork.core.tools.LocalRagTools;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

import static org.mockito.Mockito.*;

class BitbucketPrReviewAgentTest {

    private static final BitbucketPrReviewAgent.GuideDocument STYLE_GUIDE =
            new BitbucketPrReviewAgent.GuideDocument(
                    "Style Guide",
                    "https://example.atlassian.net/wiki/pages/123",
                    "123",
                    "style-guide"
            );

    private static final BitbucketPrReviewAgent.GuideDocument ARCH_GUIDE =
            new BitbucketPrReviewAgent.GuideDocument(
                    "Architecture Guide",
                    "https://example.atlassian.net/wiki/pages/456",
                    "456",
                    "arch-guide"
            );

    private static final BitbucketPrReviewAgent.PrMetadata DEFAULT_PR_METADATA =
            new BitbucketPrReviewAgent.PrMetadata(
                    "Sample PR",
                    "Sample description",
                    "Sample overview",
                    true,
                    1,
                    1,
                    0,
                    10,
                    2,
                    0,
                    false,
                    false
            );

    @Test
    void testPrepareReviewContext_ExtractsPrOverviewFromDescription() throws IOException {
        BitbucketService bitbucketService = mock(BitbucketService.class);
        ConfluenceService confluenceService = mock(ConfluenceService.class);
        CoworkLogger logger = mock(CoworkLogger.class);
        BitbucketPrReviewAgent agent = new BitbucketPrReviewAgent(bitbucketService, null, null, confluenceService, logger);

        when(confluenceService.getPageStorage("123")).thenReturn("style-guide");
        when(confluenceService.getPageStorage("456")).thenReturn("arch-guide");
        when(bitbucketService.fetchPullRequest("autocrypt", "repo", "1")).thenReturn(new PullRequestData(
                "1",
                "Auth hardening",
                """
                ## Overview
                로그인 실패 처리와 예외 메시지 정리를 개선합니다.

                ## Testing
                - auth service test 보강
                """,
                """
                diff --git a/src/main/java/AuthService.java b/src/main/java/AuthService.java
                --- a/src/main/java/AuthService.java
                +++ b/src/main/java/AuthService.java
                @@ -1,1 +1,2 @@
                +new line
                """
        ));

        var request = new BitbucketPrReviewAgent.PrReviewRequest(
                "autocrypt/repo",
                1L,
                null,
                null,
                "https://example.atlassian.net/wiki/pages/123",
                "https://example.atlassian.net/wiki/pages/456"
        );

        BitbucketPrReviewAgent.DraftContext draft = agent.prepareReviewContext(new BitbucketPrReviewAgent.InitialState(request));

        assertEquals("Auth hardening", draft.prMetadata().title());
        assertEquals("로그인 실패 처리와 예외 메시지 정리를 개선합니다.", draft.prMetadata().overview());
        assertTrue(draft.prMetadata().explicitOverviewProvided());
        assertEquals("123", draft.styleGuide().pageId());
        assertEquals("456", draft.archGuide().pageId());
        assertEquals(1, draft.prMetadata().productionFileCount());
        assertFalse(draft.prMetadata().testFocused());
    }

    @Test
    void testPrepareReviewContext_ExtractsInlineOverviewFormat() throws IOException {
        BitbucketService bitbucketService = mock(BitbucketService.class);
        ConfluenceService confluenceService = mock(ConfluenceService.class);
        CoworkLogger logger = mock(CoworkLogger.class);
        BitbucketPrReviewAgent agent = new BitbucketPrReviewAgent(bitbucketService, null, null, confluenceService, logger);

        when(confluenceService.getPageStorage("123")).thenReturn("style-guide");
        when(confluenceService.getPageStorage("456")).thenReturn("arch-guide");
        when(bitbucketService.fetchPullRequest("autocrypt", "repo", "2")).thenReturn(new PullRequestData(
                "2",
                "Scheduler migration",
                """
                Overview: 스케줄러 제거 후 event loop 기반으로 전환합니다.

                Testing:
                - smoke only
                """,
                """
                diff --git a/src/main/java/Scheduler.java b/src/main/java/Scheduler.java
                --- a/src/main/java/Scheduler.java
                +++ b/src/main/java/Scheduler.java
                @@ -1,1 +1,2 @@
                +new line
                """
        ));

        var request = new BitbucketPrReviewAgent.PrReviewRequest(
                "autocrypt/repo",
                2L,
                null,
                null,
                "https://example.atlassian.net/wiki/pages/123",
                "https://example.atlassian.net/wiki/pages/456"
        );

        BitbucketPrReviewAgent.DraftContext draft = agent.prepareReviewContext(new BitbucketPrReviewAgent.InitialState(request));

        assertEquals("스케줄러 제거 후 event loop 기반으로 전환합니다.", draft.prMetadata().overview());
        assertTrue(draft.prMetadata().explicitOverviewProvided());
    }

    @Test
    void testPrepareReviewContext_ExtractsPageIdFromViewPageActionUrl() throws IOException {
        BitbucketService bitbucketService = mock(BitbucketService.class);
        ConfluenceService confluenceService = mock(ConfluenceService.class);
        CoworkLogger logger = mock(CoworkLogger.class);
        BitbucketPrReviewAgent agent = new BitbucketPrReviewAgent(bitbucketService, null, null, confluenceService, logger);

        when(confluenceService.getPageStorage("123")).thenReturn("style-guide");
        when(confluenceService.getPageStorage("456")).thenReturn("arch-guide");
        when(bitbucketService.fetchPullRequest("autocrypt", "repo", "3")).thenReturn(new PullRequestData(
                "3",
                "Viewpage test",
                "Overview: test",
                "diff --git a/a.txt b/a.txt\n--- a/a.txt\n+++ b/a.txt\n@@ -1 +1 @@\n+line\n"
        ));

        var request = new BitbucketPrReviewAgent.PrReviewRequest(
                "autocrypt/repo",
                3L,
                null,
                null,
                "https://example.atlassian.net/wiki/pages/viewpage.action?pageId=123",
                "https://example.atlassian.net/wiki/pages/viewpage.action?pageId=456"
        );

        BitbucketPrReviewAgent.DraftContext draft = agent.prepareReviewContext(new BitbucketPrReviewAgent.InitialState(request));

        assertEquals("123", draft.styleGuide().pageId());
        assertEquals("456", draft.archGuide().pageId());
    }

    @Test
    void testPrepareReviewContext_SupportsLegacyShortUrl() throws IOException {
        BitbucketService bitbucketService = mock(BitbucketService.class);
        ConfluenceService confluenceService = mock(ConfluenceService.class);
        CoworkLogger logger = mock(CoworkLogger.class);
        BitbucketPrReviewAgent agent = new BitbucketPrReviewAgent(bitbucketService, null, null, confluenceService, logger);

        when(confluenceService.getPageStorage("878641171")).thenReturn("style-guide");
        when(confluenceService.getPageStorage("456")).thenReturn("arch-guide");
        when(bitbucketService.fetchPullRequest("autocrypt", "repo", "4")).thenReturn(new PullRequestData(
                "4",
                "Legacy short URL test",
                "Overview: legacy short URL",
                "diff --git a/a.txt b/a.txt\n--- a/a.txt\n+++ b/a.txt\n@@ -1 +1 @@\n+line\n"
        ));

        var request = new BitbucketPrReviewAgent.PrReviewRequest(
                "autocrypt/repo",
                4L,
                null,
                null,
                "https://auto-jira.atlassian.net/wiki/x/EwBfN",
                "https://example.atlassian.net/wiki/pages/456"
        );

        BitbucketPrReviewAgent.DraftContext draft = agent.prepareReviewContext(new BitbucketPrReviewAgent.InitialState(request));

        assertEquals("878641171", draft.styleGuide().pageId());
    }

    @Test
    void testPrepareReviewContext_FailsFastForUnknownShortUrl() {
        BitbucketService bitbucketService = mock(BitbucketService.class);
        ConfluenceService confluenceService = mock(ConfluenceService.class);
        CoworkLogger logger = mock(CoworkLogger.class);
        BitbucketPrReviewAgent agent = new BitbucketPrReviewAgent(bitbucketService, null, null, confluenceService, logger);

        var request = new BitbucketPrReviewAgent.PrReviewRequest(
                "autocrypt/repo",
                4L,
                null,
                null,
                "https://auto-jira.atlassian.net/wiki/x/UNKNOWN",
                "https://example.atlassian.net/wiki/pages/456"
        );

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> agent.prepareReviewContext(new BitbucketPrReviewAgent.InitialState(request)));

        assertTrue(ex.getMessage().contains("short URL"));
        assertTrue(ex.getMessage().contains("/wiki/pages/<id>"));
    }

    @Test
    void testPrepareReviewContext_FailsFastWhenGuideContentMissing() {
        BitbucketService bitbucketService = mock(BitbucketService.class);
        ConfluenceService confluenceService = mock(ConfluenceService.class);
        CoworkLogger logger = mock(CoworkLogger.class);
        BitbucketPrReviewAgent agent = new BitbucketPrReviewAgent(bitbucketService, null, null, confluenceService, logger);

        when(confluenceService.getPageStorage("123")).thenReturn("");

        var request = new BitbucketPrReviewAgent.PrReviewRequest(
                "autocrypt/repo",
                5L,
                null,
                null,
                "https://example.atlassian.net/wiki/pages/123",
                "https://example.atlassian.net/wiki/pages/456"
        );

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> agent.prepareReviewContext(new BitbucketPrReviewAgent.InitialState(request)));

        assertTrue(ex.getMessage().contains("Style Guide"));
        assertTrue(ex.getMessage().contains("pageId=123"));
    }

    @Test
    void testConcatenateSegments_SmallFilesGrouping() throws IOException {
        ConfluenceService confluenceService = mock(ConfluenceService.class);
        CoworkLogger logger = mock(CoworkLogger.class);
        BitbucketPrReviewAgent agent = new BitbucketPrReviewAgent(null, null, null, confluenceService, logger);
        
        // 순서를 섞어서 입력
        List<BitbucketPrReviewAgent.DiffSegment> segments = List.of(
            new BitbucketPrReviewAgent.DiffSegment("src/util.c", "content-util", false, 20),
            new BitbucketPrReviewAgent.DiffSegment("src/main.h", "content-h", false, 5),
            new BitbucketPrReviewAgent.DiffSegment("src/main.c", "content-c", false, 10)
        );
        
        BitbucketPrReviewAgent.DraftContext draft = new BitbucketPrReviewAgent.DraftContext(
            new BitbucketPrReviewAgent.PrReviewRequest("repo", 1L, null, null, "style-url", "arch-url"),
            segments, "manuals-key", "standards-key", STYLE_GUIDE, ARCH_GUIDE, DEFAULT_PR_METADATA
        );

        // Act
        BitbucketPrReviewAgent.ReadyContext ready = agent.concatenateSegments(draft);

        // Assert: 전체 크기가 작으므로 1개의 번들로 묶여야 함
        assertEquals(1, ready.bundles().size());
        BitbucketPrReviewAgent.ConcatenatedDiff bundle = ready.bundles().get(0);
        
        // 정렬 확인: main.c, main.h, util.c 순서여야 함
        assertEquals("src/main.c", bundle.fileNames().get(0));
        assertEquals("src/main.h", bundle.fileNames().get(1));
        assertEquals("src/util.c", bundle.fileNames().get(2));
        
        assertEquals("456", ready.archGuide().pageId());
    }

    @Test
    void testAnalyzeAllSegments_PromptEval() throws IOException {
        var context = FakeOperationContext.create();
        var promptRunner = (FakePromptRunner) context.promptRunner();

        // Dependencies
        LocalRagTools localRagTools = mock(LocalRagTools.class);
        CoworkLogger logger = mock(CoworkLogger.class);
        when(localRagTools.getOrOpenInstance(anyString())).thenReturn(mock(LuceneSearchOperations.class));

        // 1. Arrange: Expecting a single combined response for one bundle
        context.expectResponse(new BitbucketPrReviewAgent.BundleAnalysisResult(
                new BitbucketPrReviewAgent.StyleAnalysisResult(List.of(), 90),
                new BitbucketPrReviewAgent.ArchAnalysisResult(List.of(), 85)
        ));

        BitbucketPrReviewAgent.ReadyContext readyContext = new BitbucketPrReviewAgent.ReadyContext(
            new BitbucketPrReviewAgent.PrReviewRequest("repo", 1L, null, null, "style-url", "arch-url"),
            List.of(new BitbucketPrReviewAgent.ConcatenatedDiff(List.of("file.c"), "diff", false)),
            "m-key", "s-key",
            new BitbucketPrReviewAgent.GuideDocument("Style Guide", "https://example.atlassian.net/wiki/pages/123", "123", "style-content"),
            new BitbucketPrReviewAgent.GuideDocument("Architecture Guide", "https://example.atlassian.net/wiki/pages/456", "456", "arch-content"),
            new BitbucketPrReviewAgent.PrMetadata(
                    "Feature PR",
                    "## Overview\n로그인 기능 추가",
                    "로그인 기능 추가",
                    true,
                    4,
                    3,
                    1,
                    120,
                    15,
                    1,
                    false,
                    true
            )
        );

        BitbucketPrReviewAgent agent = new BitbucketPrReviewAgent(null, localRagTools, null, null, logger);
        
        // 2. Act
        BitbucketPrReviewAgent.AllAnalysisResults results = agent.analyzeAllSegments(readyContext, context.ai());

        // 3. Assert
        assertEquals(1, results.styleResults().size());
        assertEquals(1, results.archResults().size());
        assertEquals(90, results.styleResults().get(0).score());
        assertEquals(85, results.archResults().get(0).score());

        // Verify Prompt Parameters
        var invocations = promptRunner.getLlmInvocations();
        assertEquals(1, invocations.size());

        assertTrue(invocations.get(0).getPrompt().contains("STYLE_GUIDE"));
        assertTrue(invocations.get(0).getPrompt().contains("로그인 기능 추가"));
        assertTrue(invocations.get(0).getPrompt().contains("ARCHITECTURE"));
        assertTrue(invocations.get(0).getPrompt().contains("대규모 기능/구조 변경 가능성: 높음"));
    }

    @Test
    void testSynthesizeFinalReport_CapsPositiveHighlightsAndCountsOnlyIssues() {
        BitbucketService bitbucketService = mock(BitbucketService.class);
        CoworkLogger logger = mock(CoworkLogger.class);
        BitbucketPrReviewAgent agent = new BitbucketPrReviewAgent(bitbucketService, null, null, null, logger);

        BitbucketPrReviewAgent.ReadyContext readyContext = new BitbucketPrReviewAgent.ReadyContext(
                new BitbucketPrReviewAgent.PrReviewRequest("autocrypt/repo", 1L, null, null, "style-url", "arch-url"),
                List.of(new BitbucketPrReviewAgent.ConcatenatedDiff(List.of("src/main/AuthService.java"), "diff", false)),
                null,
                null,
                STYLE_GUIDE,
                ARCH_GUIDE,
                DEFAULT_PR_METADATA
        );

        BitbucketPrReviewAgent.AllAnalysisResults results = new BitbucketPrReviewAgent.AllAnalysisResults(
                readyContext,
                List.of(new BitbucketPrReviewAgent.StyleAnalysisResult(List.of(
                        new BitbucketPrReviewAgent.CodeComment("src/main/AuthService.java", null, "✅ [GOOD] 예외 흐름이 명확합니다.", "GLOBAL", "SUGGESTION", "STYLE"),
                        new BitbucketPrReviewAgent.CodeComment("src/main/AuthService.java", null, "✅ [GOOD] 메서드 분리가 깔끔합니다.", "GLOBAL", "SUGGESTION", "STYLE"),
                        new BitbucketPrReviewAgent.CodeComment("src/main/AuthService.java", null, "✅ [GOOD] 네이밍이 일관됩니다.", "GLOBAL", "SUGGESTION", "STYLE"),
                        new BitbucketPrReviewAgent.CodeComment("src/main/AuthService.java", 42, "🟠 [MAJOR] null 처리 분기가 누락될 수 있습니다.", "LINE", "SHOULD_FIX", "ARCH")
                ), 88)),
                List.of(new BitbucketPrReviewAgent.ArchAnalysisResult(List.of(), 84))
        );

        BitbucketPrReviewAgent.FinalReviewReport report = agent.synthesizeFinalReport(results, null, null);

        assertEquals(1, report.totalIssuesFound());
        assertEquals(2, report.globalComments().size());
        assertEquals(1, report.lineComments().size());
        assertTrue(report.summary().contains("### 📚 Reference Inputs"));
        assertTrue(report.summary().contains("pageId=123"));
        assertTrue(report.summary().contains("### 👍 Notable Strengths"));
        assertEquals(2, report.summary().split("\\[GOOD\\]").length - 1);
        verify(bitbucketService).postGlobalComment(eq("autocrypt"), eq("repo"), eq("1"), anyString());
        verify(bitbucketService).postLineComment("autocrypt", "repo", "1", "src/main/AuthService.java", 42, "🟠 [MAJOR] null 처리 분기가 누락될 수 있습니다.");
    }

    @Test
    void testSynthesizeFinalReport_SuppressesTestMagicNumberNit() {
        BitbucketService bitbucketService = mock(BitbucketService.class);
        CoworkLogger logger = mock(CoworkLogger.class);
        BitbucketPrReviewAgent agent = new BitbucketPrReviewAgent(bitbucketService, null, null, null, logger);

        BitbucketPrReviewAgent.ReadyContext readyContext = new BitbucketPrReviewAgent.ReadyContext(
                new BitbucketPrReviewAgent.PrReviewRequest("autocrypt/repo", 1L, null, null, "style-url", "arch-url"),
                List.of(new BitbucketPrReviewAgent.ConcatenatedDiff(List.of("src/test/cpp/AuthServiceTest.cpp"), "diff", false)),
                null,
                null,
                STYLE_GUIDE,
                ARCH_GUIDE,
                DEFAULT_PR_METADATA
        );

        BitbucketPrReviewAgent.AllAnalysisResults results = new BitbucketPrReviewAgent.AllAnalysisResults(
                readyContext,
                List.of(new BitbucketPrReviewAgent.StyleAnalysisResult(List.of(
                        new BitbucketPrReviewAgent.CodeComment(
                                "src/test/cpp/AuthServiceTest.cpp",
                                18,
                                "🟠 [MAJOR] 테스트 코드 내에서 std::chrono::milliseconds(100)와 같은 매직 넘버를 사용하고 있습니다. 의미 있는 상수로 정의하십시오.",
                                "LINE",
                                "SHOULD_FIX",
                                "STYLE")
                ), 92)),
                List.of(new BitbucketPrReviewAgent.ArchAnalysisResult(List.of(), 95))
        );

        BitbucketPrReviewAgent.FinalReviewReport report = agent.synthesizeFinalReport(results, null, null);

        assertEquals(0, report.totalIssuesFound());
        assertTrue(report.lineComments().isEmpty());
        verify(bitbucketService).postGlobalComment(eq("autocrypt"), eq("repo"), eq("1"), anyString());
        verify(bitbucketService, never()).postLineComment(eq("autocrypt"), eq("repo"), eq("1"), eq("src/test/cpp/AuthServiceTest.cpp"), eq(18), anyString());
    }

    @Test
    void testSynthesizeFinalReport_SuppressesFalseOverviewMissingComment() {
        BitbucketService bitbucketService = mock(BitbucketService.class);
        CoworkLogger logger = mock(CoworkLogger.class);
        BitbucketPrReviewAgent agent = new BitbucketPrReviewAgent(bitbucketService, null, null, null, logger);

        BitbucketPrReviewAgent.ReadyContext readyContext = new BitbucketPrReviewAgent.ReadyContext(
                new BitbucketPrReviewAgent.PrReviewRequest("autocrypt/repo", 1L, null, null, "style-url", "arch-url"),
                List.of(new BitbucketPrReviewAgent.ConcatenatedDiff(List.of("src/main/Scheduler.java"), "diff", false)),
                null,
                null,
                STYLE_GUIDE,
                ARCH_GUIDE,
                new BitbucketPrReviewAgent.PrMetadata(
                        "Scheduler migration",
                        "## Overview\n스케줄러 제거 후 event loop 기반으로 전환합니다.",
                        "스케줄러 제거 후 event loop 기반으로 전환합니다.",
                        true,
                        3,
                        3,
                        0,
                        80,
                        20,
                        0,
                        false,
                        true
                )
        );

        BitbucketPrReviewAgent.AllAnalysisResults results = new BitbucketPrReviewAgent.AllAnalysisResults(
                readyContext,
                List.of(new BitbucketPrReviewAgent.StyleAnalysisResult(List.of(), 92)),
                List.of(new BitbucketPrReviewAgent.ArchAnalysisResult(List.of(
                        new BitbucketPrReviewAgent.CodeComment(
                                null,
                                null,
                                "🚨 [CRITICAL] PR Overview 누락으로 인한 변경 의도 및 영향 파악 불가",
                                "GLOBAL",
                                "MUST_FIX",
                                "ARCH")
                ), 78))
        );

        BitbucketPrReviewAgent.FinalReviewReport report = agent.synthesizeFinalReport(results, null, null);

        assertEquals(0, report.totalIssuesFound());
        assertTrue(report.globalComments().isEmpty());
        verify(bitbucketService).postGlobalComment(eq("autocrypt"), eq("repo"), eq("1"), anyString());
    }
}
