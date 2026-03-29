package io.autocrypt.jwlee.cowork;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import com.embabel.agent.api.annotation.LlmTool;
import com.embabel.agent.api.common.Ai;
import com.embabel.agent.rag.tools.ResultsEvent;
import com.embabel.common.ai.model.LlmOptions;

import io.autocrypt.jwlee.cowork.core.tools.ConfluenceService;
import io.autocrypt.jwlee.cowork.core.tools.LocalRagTools;
import io.autocrypt.jwlee.cowork.core.workaround.JsonSafeToolishRag;

/**
 * 다양한 LLM 실험 및 간이 테스트를 위한 플레이그라운드입니다.
 */
public class PlaygroundTest extends BaseIntegrationTest {

    private static final Logger log = LoggerFactory.getLogger(PlaygroundTest.class);

    @Autowired
    private Ai ai;

    @Autowired
    private LocalRagTools localRagTools;

    @Autowired
    private ConfluenceService confluenceService;

    @Test
    public void confluenceSmartSearchDemo() {
        String productInfo = """
            # 제품별 컨플루언스 부모 페이지(Ancestor) ID 정보:
            - V2X-EE: 1772650979
            - PKI-Vehicles: 1819934721
            - 이동의자유: 2571141121
            - PKI-PnC: 2829353188
            """;

        String question = "PKI-Vehicles 제품의 최근 두달간의 ADR 업데이트 내역을 모두 정리해줘.";

        log.info("==================================================");
        log.info("Confluence Smart Search Demo: LLM with Ancestor IDs");
        log.info("Question: {}", question);
        log.info("==================================================");

        String prompt = String.format("""
            %s
            사용자의 질문에 답변하기 위해 위 정보를 참고하여 `search` 도구를 사용하세요.
            반드시 해당 제품의 Ancestor ID를 필터에 적용하여 정확한 범위를 검색해야 합니다.

            # 질문: %s
            """, productInfo, question);

        // 프록시 문제를 우회하기 위한 로컬 도구 객체 생성
        Object tool = new Object() {
            @LlmTool(description = "컨플루언스 지식 베이스에서 관련 문서를 검색합니다.")
            public java.util.List<ConfluenceService.ConfluencePageInfo> search(ConfluenceService.RagSearchRequest request) {
                log.info("[Tool Call] Confluence.search 호출됨: keyword={}, ancestor={}", request.keyword(), request.ancestorId());
                return confluenceService.searchForRag(request);
            }
        };

        String result = ai.withLlm(LlmOptions.withDefaultLlm().withoutThinking())
                .withToolObject(tool)
                .generateText(prompt);

        log.info("Final Answer:\n{}", result);
        log.info("==================================================");
    }

    @Test
    public void confluenceRagDemo() {
        String keyword = "OKR";
        String ancestorId = "12345678"; // 시연용 부모 페이지 ID 예시

        log.info("==================================================");
        log.info("Confluence CQL RAG Demo: Filtering by type=page and ancestor={}", ancestorId);
        log.info("Searching for: {}", keyword);
        log.info("==================================================");

        // Confluence에서 직접 검색하여 RAG용 검색 결과를 생성
        var request = new ConfluenceService.RagSearchRequest(
                keyword, 
                null, 
                null, 
                ancestorId, 
                3
        );
        
        var results = confluenceService.searchForRag(request);
        
        log.info("Found {} results from Confluence.", results.size());
        results.forEach(p -> log.info("- Page: {} (ID: {})", p.title(), p.id()));

        if (!results.isEmpty()) {
            // 가져온 컨텐츠를 바탕으로 LLM 답변 생성 시도 (예시)
            String context = results.stream()
                    .map(p -> String.format("[%s]\n%s", p.title(), p.content()))
                    .reduce("", (a, b) -> a + "\n\n" + b);

            String result = ai.withLlm(LlmOptions.withLlmForRole("normal").withoutThinking())
                    .generateText("다음 검색 결과를 참고하여 OKR 진행 현황을 요약해줘:\n\n" + context);
            
            log.info("LLM Summary:\n{}", result);
        }
        
        log.info("==================================================");
    }

    @Test
    public void ragDemo() throws Exception {
        String ragName = "rag-demo";
        localRagTools.ingestUrlToMemory("guides/DSL_GUIDE.md", ragName);
        var searchOps = localRagTools.getOrOpenMemoryInstance(ragName);

        String question = "에이전트 DSL을 작성할 때 DTO(Record) 정의 시 주의사항은 무엇인가요?";

        log.info("==================================================");
        log.info("RAG Demo: Using LocalRagTools & ToolishRag");
        log.info("Question: {}", question);
        log.info("==================================================");

        // ToolishRag를 사용하여 지식 베이스를 검색 도구로 노출
        var rag = new JsonSafeToolishRag("knowledge", "General knowledge base about Embabel Agent", searchOps)
                .withListener(event -> log.info("[RAG Event] Query: {}", event.getQuery()));

        String result = ai.withLlm(LlmOptions.withLlmForRole("normal").withoutThinking())
                .withReference(rag)
                .generateText("지식 베이스를 참고하여 질문에 정확히 답해주세요: " + question);

        log.info("Result:\n{}", result);
        log.info("==================================================");
    }

    @Test
    public void toolCallDemo() {
        String prompt = "1234567890 + 9876543210 의 값을 정확하게 계산해줘. 전용 계산기 도구를 사용해.";

        log.info("==================================================");
        log.info("Tool Call Demo: Using SimpleCalculator Tool");
        log.info("Prompt: {}", prompt);
        log.info("==================================================");

        SimpleCalculator calculator = new SimpleCalculator();

        String result = ai.withLlmByRole("normal")
                .withToolObject(calculator)
                .generateText(prompt);

        log.info("Result:\n{}", result);
        log.info("==================================================");
    }

    public static record SimpleCalculator() {
        @LlmTool(description = "10자리 이상의 큰 숫자 덧셈을 정확하게 수행합니다.")
        public long add(long a, long b) {
            log.info("[Tool Call] Calculator.add({}, {}) 호출됨", a, b);
            return a + b;
        }
    }

    @Test
    public void reasoningDemo() {
        String modelName = "gemini-3-flash-preview";
        String prompt = "9.11과 9.9 중 어느 숫자가 더 큰가요? 이유를 설명해주세요.";

        log.info("==================================================");
        log.info("Reasoning Demo: Comparing Thinking vs WithoutThinking");
        log.info("Model: {}", modelName);
        log.info("Prompt: {}", prompt);
        log.info("==================================================");

        // 1. Without Thinking
        log.info("[Executing WITHOUT Thinking...]");
        String fastResult = ai.withLlm(LlmOptions.withModel(modelName).withoutThinking())
                .generateText(prompt);
        log.info("Result (Without Thinking):\n{}", fastResult);

        log.info("--------------------------------------------------");

        // 2. With Thinking
        log.info("[Executing WITH Thinking (Reasoning)...]");
        String reasoningResult = ai.withLlm(LlmOptions.withModel(modelName))
                .generateText(prompt);
        log.info("Result (With Thinking):\n{}", reasoningResult);

        log.info("==================================================");
    }

    @Test
    public void scalingLawDemo() {
        String prompt = "노인과 바다의 ISBN을 알려줘";

        log.info("==================================================");
        log.info("Scaling Law Demo: Comparing 'simple' vs 'normal' roles");
        log.info("Prompt: {}", prompt);
        log.info("==================================================");

        String resultLite = ai.withLlmByRole("simple").generateText(prompt);
        log.info("Result (simple):\n{}", resultLite);

        log.info("--------------------------------------------------");

        String resultNormal = ai.withLlmByRole("normal").generateText(prompt);
        log.info("Result (normal):\n{}", resultNormal);

        log.info("==================================================");
    }
}
