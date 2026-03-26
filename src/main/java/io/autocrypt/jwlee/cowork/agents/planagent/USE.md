# USE AgentGenerationPlanAgent

사용자의 요구사항을 분석하여 새로운 에이전트의 상세 설계도(DSL)를 자동으로 생성합니다.

## CLI 명령어

### 기본 실행
```bash
> plan-agent --goal "에이전트 목표" --features "기능1, 기능2..." --constraints "제약 사항..."
```

### 실행 예시
```bash
> plan-agent --goal "아침 업무 요약 분석" --features "어제자 Jira 상태 변화 추출, Confluence 회의록 요약" --constraints "JiraExcelService 및 ConfluenceService 사용 필수"
```

## 결과물 확인
생성된 설계도는 `guides/DSLs/DSL-{AgentName}.md` 경로에 저장됩니다.

## 설계 원칙
- **신뢰도 계층형 RAG**: 프로젝트 내의 실제 작동하는 `*Agent.java` 코드와 `few-shot` 문서를 최우선으로 참고합니다.
- **2단계 처리**: Flash 모델로 연구하고 Pro 모델로 합성하여 안정적인 결과물을 도출합니다.
