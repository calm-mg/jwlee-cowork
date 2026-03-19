package io.autocrypt.jwlee.cowork.agents.sample;

import com.embabel.agent.api.annotation.AchievesGoal;
import com.embabel.agent.api.annotation.Action;
import com.embabel.agent.api.annotation.Agent;
import com.embabel.agent.api.common.Ai;
import com.embabel.agent.domain.io.UserInput;
import com.embabel.agent.rag.tools.ToolishRag;
import io.autocrypt.jwlee.cowork.core.rag.RagIngestionService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import java.nio.file.Path;

@Agent(description = "Expert Root Cause Analysis Agent. Research incidents using Lucene-based technical knowledge.")
public class RootCauseAnalysisAgent {

    private final ToolishRag luceneRag;
    private final RagIngestionService ingestionService;
    
    @Value("${embabel.agent.rag.import.dir:knowledge/documents}")
    private String defaultKnowledgeDir;

    public RootCauseAnalysisAgent(
            @Qualifier("luceneRagTool") ToolishRag luceneRag,
            RagIngestionService ingestionService) {
        this.luceneRag = luceneRag;
        this.ingestionService = ingestionService;
    }

    public record SyncResult(boolean success, String path) {}

    public record RcaReport(
            String incidentSummary,
            String likelyRootCause,
            String supportingEvidence,
            String suggestedMitigation
    ) {}

    @Action
    public SyncResult syncKnowledgeBase() {
        ingestionService.ingestPath(Path.of(defaultKnowledgeDir));
        return new SyncResult(true, defaultKnowledgeDir);
    }

    @AchievesGoal(description = "Produce a detailed Root Cause Analysis report")
    @Action
    public RcaReport analyzeIncident(UserInput input, SyncResult syncStatus, Ai ai) {
        return ai.withAutoLlm()
                .withReference(luceneRag)
                .createObject(String.format("""
                        [지침]
                        당신은 숙련된 SRE 전문가입니다. 내부 지식 베이스(rca_sources)를 활용하여 장애 분석 리포트를 작성하세요.
                        
                        [장애 상황]
                        %s
                        
                        [분석 및 인용 규칙 - 매우 중요]
                        1. 검색 도구(rca_sources)의 결과 중 유사한 증상을 다룬 문서를 찾으세요.
                        2. 'supportingEvidence' 필드에는 반드시 검색 결과의 # URI 항목에 표시된 실제 파일명을 토씨 하나 틀리지 않고 그대로 적으세요. (예: incident-db-timeout.md)
                        3. 파일명을 절대로 임의로 수정하거나(예: 날짜 변경 등) 지어내지 마십시오. 오직 URI에 있는 파일명만 사용하세요.
                        4. 'likelyRootCause'에는 해당 문서에서 찾은 기술적 원인(예: 커넥션 풀 크기, 트래픽 폭주 등)을 구체적으로 설명하세요.
                        
                        [출력 형식]
                        - 모든 필드는 한국어로 작성합니다.
                        - JSON 형식을 엄격히 준수합니다.
                        """, input.getContent()), RcaReport.class);
    }
}
