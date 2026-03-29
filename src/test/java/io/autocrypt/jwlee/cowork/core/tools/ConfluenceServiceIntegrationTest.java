package io.autocrypt.jwlee.cowork.core.tools;

import static org.assertj.core.api.Assertions.assertThat;

import io.autocrypt.jwlee.cowork.BaseIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * ConfluenceService의 실제 서버 연동을 확인하기 위한 통합 테스트입니다.
 */
class ConfluenceServiceIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private ConfluenceService confluenceService;

    @Test
    void getCurrentOkr_realServerTest() {
        // When
        ConfluenceService.ConfluencePageInfo result = confluenceService.getCurrentOkr();

        // Then
        System.out.println("====== [RESULT] Current OKR Title: " + result.title() + " ======");
        System.out.println("====== [RESULT] Current OKR Content Length: " + result.content().length() + " ======");
        if (result.content().length() > 300) {
            System.out.println("====== [RESULT] Preview: \n" + result.content().substring(0, 300) + "\n... ======");
        } else if (!result.isEmpty()) {
            System.out.println("====== [RESULT] Content: \n" + result.content() + "\n======");
        }

        assertThat(result.content()).isNotNull();
        assertThat(result.content()).isNotEmpty();
    }

    @Test
    void getCurrentWeeklyReport_realServerTest() {
        // When
        ConfluenceService.ConfluencePageInfo result = confluenceService.getCurrentWeeklyReport();

        // Then
        System.out.println("====== [RESULT] Current Weekly Report Title: " + result.title() + " ======");
        System.out.println("====== [RESULT] Current Weekly Report Content Length: " + result.content().length() + " ======");
        if (result.content().length() > 300) {
            System.out.println("====== [RESULT] Preview: \n" + result.content().substring(0, 300) + "\n... ======");
        } else if (!result.isEmpty()) {
            System.out.println("====== [RESULT] Content: \n" + result.content() + "\n======");
        }

        assertThat(result.content()).isNotNull();
        assertThat(result.content()).isNotEmpty();
    }

    @Test
    void searchForRag_realServerTest() {
        // Given
        ConfluenceService.RagSearchRequest request = new ConfluenceService.RagSearchRequest(
                "조직 OKR", // keyword
                "회고",      // excludeKeyword
                "2025-01-01",// fromDate
                null,        // ancestorId
                3            // limit
        );

        // When
        java.util.List<ConfluenceService.ConfluencePageInfo> results = confluenceService.searchForRag(request);

        // Then
        System.out.println("====== [RESULT] RAG Search Results Count: " + results.size() + " ======");
        for (int i = 0; i < results.size(); i++) {
            ConfluenceService.ConfluencePageInfo info = results.get(i);
            System.out.println(String.format("[%d] Title: %s (Length: %d)", i + 1, info.title(), info.content().length()));
        }

        assertThat(results).isNotNull();
        assertThat(results).isNotEmpty();
        assertThat(results.size()).isLessThanOrEqualTo(3);
    }
}
