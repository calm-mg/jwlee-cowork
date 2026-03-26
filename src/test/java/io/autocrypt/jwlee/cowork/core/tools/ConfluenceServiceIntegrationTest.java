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
        if (result.content().length() > 1000) {
            System.out.println("====== [RESULT] Preview: \n" + result.content().substring(0, 1000) + "\n... ======");
        } else if (!result.isEmpty()) {
            System.out.println("====== [RESULT] Content: \n" + result.content() + "\n======");
        }

        assertThat(result.content()).isNotNull();
        assertThat(result.content()).isNotEmpty(); // 실제 데이터가 조회되어야 함
    }

    @Test
    void getCurrentWeeklyReport_realServerTest() {
        // When
        ConfluenceService.ConfluencePageInfo result = confluenceService.getCurrentWeeklyReport();

        // Then
        System.out.println("====== [RESULT] Current Weekly Report Title: " + result.title() + " ======");
        System.out.println("====== [RESULT] Current Weekly Report Content Length: " + result.content().length() + " ======");
        if (result.content().length() > 1000) {
            System.out.println("====== [RESULT] Preview: \n" + result.content().substring(0, 1000) + "\n... ======");
        } else if (!result.isEmpty()) {
            System.out.println("====== [RESULT] Content: \n" + result.content() + "\n======");
        }

        assertThat(result.content()).isNotNull();
        assertThat(result.content()).isNotEmpty(); // 실제 데이터가 조회되어야 함
    }
}
