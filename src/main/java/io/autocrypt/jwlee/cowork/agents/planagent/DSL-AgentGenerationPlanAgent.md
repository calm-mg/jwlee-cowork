# DSL-AgentGenerationPlanAgent.md

agent:
  name: AgentGenerationPlanAgent
  description: "사용자의 요구사항을 분석하여 Embabel Agent DSL 및 관련 구현 계획을 생성하는 설계 에이전트"
  timezone: "Asia/Seoul"
  workspace: "agent-plan"

dependencies:
  - CoreFileTools: DSL 파일 저장 및 가이드 파일 읽기용
  - PromptProvider: Jinja 템플릿 렌더링 및 페르소나 관리용
  - CoworkLogger: 설계 프로세스 로깅용
  - LocalRagTools: DSL_GUIDE.md 및 기존 에이전트 패턴 검색용 (In-memory RAG)

dto:
  AgentRequirement:
    goal: String # 에이전트의 핵심 목표
    features: List<String> # 구현해야 할 기능 목록
    constraints: String # 기술적 제약 사항 (예: 특정 서비스 사용 필수)
  
  DslGenerationResult:
    agentName: String # 생성된 에이전트 이름
    dslContent: String # 생성된 DSL-{agent-name}.md 마크다운 내용
    implementationStrategy: String # 구현 시 특히 주의해야 할 기술적 포인트 설명

actions:
  @AchievesGoal(description = "에이전트 구현을 위한 DSL 설계 완료")
  generateDsl:
    input: AgentRequirement
    output: DslGenerationResult
    role: performant
    temperature: 0.2
    template: "agents/planagent/generate-dsl.jinja"

implementation_guidelines:
  - "guides/DSL_GUIDE.md의 모든 규칙을 엄격히 준수하여 DSL을 생성할 것"
  - "기존 코드베이스의 서비스(JiraExcelService, ConfluenceService 등)를 적극적으로 찾아 Dependencies에 포함할 것"
  - "Action 정의 시 @AchievesGoal이 포함된 최종 액션이 반드시 하나 이상 존재하게 할 것"
  - "Timezone은 항상 'Asia/Seoul'로 고정하여 가이드에 명시할 것"
  - "생성된 DSL 파일은 'guides/DSLs/DSL-{agent-name}.md' 경로에 저장되도록 안내할 것"
  - "LLM이 자꾸 실수하는 'Action에 Bean 주입' 금지 규칙을 DSL의 implementation_guidelines에 명시적으로 포함할 것"
