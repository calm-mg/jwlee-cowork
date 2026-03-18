package io.autocrypt.jwlee.cowork;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

class ModulithArchitectureTest {

    @Test
    void verifyModules() {
        // 프로젝트 내의 모듈 구조를 분석합니다.
        ApplicationModules modules = ApplicationModules.of(JwleeCoworkApplication.class);
        
        // 모듈 간의 의존성 규칙을 검증하고, 순환 참조가 있는지 확인합니다.
        modules.verify();
        
        // (선택 사항) 모듈 구조를 시각화하기 위한 문서를 생성합니다. (target/spring-modulith 하위)
        new Documenter(modules).writeModulesAsPlantUml().writeIndividualModulesAsPlantUml();
    }
}
