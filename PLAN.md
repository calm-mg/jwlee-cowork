# Agentic AI Showcase Plan

이 문서는 `JWLEE Custom Agentic Shell`의 핵심 기능과 에이전트 개발 워크플로우를 시연하기 위한 계획서입니다.

## 1. Showcase: AdvancedSlidesAgent (발표자료 생성)
발표의 서두를 여는 슬라이드를 에이전트로 즉석 생성하여 기선을 제압합니다.

### 실행 명령어
```bash
slides --workspaceId agent-showcase --source "Hands-on Agentic AI:
- 각종 Agent.java 파일을 살핀 뒤, 해당 파일들에서 발견되는 주요 패턴들을 DSL_GUIDE.md 에서 식별한 패턴들 및 주의사항을 포함해서 Hands-on 이 될 수 있도록 교육하는 것이 목적임
- 프롬프트 엔지니어링과 프롬프트 evaluation 강조: Separating Logic (Java) from Vibe (Jinja/Markdown)
- 도구 연동 및 Toolish Rag, Agent As A Tool(혹은 결정론적 SubAgent 활용)등 Agentic AI Pattern 을 강조해야 함
- Live Demo: Plan-to-Code workflow with AgentGenerationPlanAgent <- 상세설명을 할 필요는 없음. 데모를 개시한다는 페이지만 있으면 됨" \
-instructions "개발자 대상의 전문적인 기술 세션입니다. Obsidian Advanced Slides의 Grid Layout과 Font Awesome 아이콘을 적극 활용하고, 아키텍처의 견고함을 강조하는 스타일로 만들어주세요. 발표자료는 한국어로 작성되어야 합니다."
```

---

## 2. Live Demo: PlanAgent (에이전트 설계 자동화)
기존 에이전트들을 부품(Sub-agent)으로 조합하여 새로운 업무 자동화 에이전트를 설계합니다.

### 실행 명령어
```bash
plan-agent --goal "아침 업무 요약 보고서를 작성하고 슬라이드로 변환하여 Git에 동기화함" \
--features "1. MorningBriefingAgent를 호출하여 어제자 업무 요약 추출, 2. 추출된 내용을 AdvancedSlidesAgent에 전달하여 발표용 슬라이드 생성, 3. 생성된 리포트와 슬라이드를 'obsidian-vault/Calendar/report/{yyyy}/{MM}/{dd}/' 경로에 저장, 4. CoreApprovalState로 사용자에게 최종 확인 요청, 5. 승인 시 GitTools를 사용하여 변경사항을 커밋 및 푸시" \
--constraints "MorningBriefingAgent, AdvancedSlidesAgent, GitTools, ObsidianTools를 필수로 포함할 것. 파일 경로는 LocalDateTime을 사용하여 동적으로 생성할 것."
```

---

## 3. Implementation & Validation (Live Coding)
`PlanAgent`가 생성한 DSL(`guides/DSLs/DSL-MorningReportAgent.md`)을 기반으로 실제 코드를 구현하고 검증합니다.

### 구현 전략 (Vibe Coding Guide)
- **에이전트 오케스트레이션**: `MorningBriefingAgent`와 `AdvancedSlidesAgent`를 생성자 주입으로 받아 직접 호출.
- **인프라 결합**: `GitTools.commitAndPushVault()`를 호출하여 옵시디언 저장소와 즉시 동기화.
- **안전 장치**: `CoreApprovalState`를 사용하여 실제 파일 수정 및 Git 푸시 전 사용자의 명시적 승인(Y/N)을 받음.
- **아키텍처 검증**: 구현 후 `./mvnw test`를 실행하여 `Spring Modulith` 규칙 준수 여부 확인.

---

## 4. Expected Outcome
1. **Vertical Slice**: 에이전트 간의 독립적인 모듈 구조 증명.
2. **HITL**: AI의 자율성과 인간의 통제가 조화된 워크플로우 시연.
3. **Composable Agents**: 에이전트가 다른 에이전트를 도구처럼 사용하는 확장성 제시.
