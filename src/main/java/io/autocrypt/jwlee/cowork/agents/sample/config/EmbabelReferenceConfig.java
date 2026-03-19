package io.autocrypt.jwlee.cowork.agents.sample.config;

import com.embabel.agent.api.reference.LlmReference;
import com.embabel.agent.api.reference.LlmReferenceProviders;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * 외부 YAML 파일(references.yml)에 정의된 프롬프트 지침과 도구 참조를 
 * Spring Bean으로 관리하기 위한 설정 클래스입니다.
 */
@Configuration
public class EmbabelReferenceConfig {

    /**
     * 슬라이드 제작 전문가의 페르소나와 템플릿 가이드라인을 담은 레퍼런스입니다.
     * 이 빈은 에이전트에서 .withReference()로 호출되어 시스템 프롬프트를 구성합니다.
     */
    @Bean
    public LlmReference presentationExpert() {
        // src/main/resources/references.yml 파일을 파싱합니다.
        List<LlmReference> references = LlmReferenceProviders.fromYmlFile("references.yml");
        
        if (references.isEmpty()) {
            throw new IllegalStateException("references.yml is empty or could not be loaded.");
        }
        
        // 첫 번째 레퍼런스(presentation-expert-ref)를 반환합니다.
        return references.get(0);
    }
}
