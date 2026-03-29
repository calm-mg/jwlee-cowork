package io.autocrypt.jwlee.cowork.core.tools;

import com.embabel.agent.api.annotation.LlmTool;

import java.util.List;

public interface ConfluenceService {
    
    record ConfluencePageInfo(String id, String title, String content) {
        public boolean isEmpty() {
            return content == null || content.isEmpty();
        }
    }

    record RagSearchRequest(
        @LlmTool.Param(description = "핵심 검색어") String keyword,
        @LlmTool.Param(description = "제외할 단어 (선택)") String excludeKeyword,
        @LlmTool.Param(description = "검색 시작일 yyyy-MM-dd (선택)") String fromDate,
        @LlmTool.Param(description = "특정 페이지 하위 검색을 위한 부모 페이지 ID (선택, ancestor 필터)") String ancestorId,
        @LlmTool.Param(description = "최대 반환 문서 개수 (기본 3)") int limit
    ) {}

    ConfluencePageInfo getCurrentOkr();
    ConfluencePageInfo getCurrentWeeklyReport();
    String getPageStorage(String pageId);
    
    // RAG 전용 검색 함수
    @LlmTool(description = "컨플루언스 지식 베이스에서 관련 문서를 검색합니다.")
    List<ConfluencePageInfo> searchForRag(RagSearchRequest request);
}
