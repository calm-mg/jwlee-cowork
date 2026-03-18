# Custom Agentic Shell Development Plan

## 1. 프로젝트 목표
Gemini CLI와 유사한 기능을 제공하며, 개발자가 임의로 에이전트(기능)를 추가할 수 있는 **개인용 Custom Agentic Shell**을 구축한다. 

## 2. 주요 아키텍처 및 기술 스택
- **CLI 프레임워크**: Spring Shell v3.4.0
- **AI 에이전트 엔진**: Embabel (GOAP, State 기반 워크플로우, Toolish RAG)
- **모듈화 검증**: Spring Modulith (에이전트 간 의존성 차단 및 수직적 슬라이스 강제)

## 3. 핵심 요구사항 및 구현 전략

### 3.1. Human-in-the-Loop (승인/거절 시스템)
작업 수행 전 또는 파일 수정, 명령어 실행 전에 사용자에게 승인을 받는 프로세스를 구현한다.
- **구현 방식**: Embabel의 `@State` 워크플로우와 `WaitFor.formSubmission()`을 활용.
- **흐름**: Agent가 계획(Plan)을 수립 -> `ApprovalRequest` 상태로 전환하며 `WaitFor` 호출 -> Spring Shell UI(ComponentFlow 또는 ConfirmationInput)를 통해 Y/N 입력 -> AgentProcess에 결과 주입(Inject) 후 재개.

### 3.2. 제공할 도구 (Tools) 목록 선정
Gemini CLI에서 제공하는 주요 도구들을 Embabel의 `@LlmTool`로 구현하여 에이전트에게 제공한다. 이 도구들은 모든 vertical slice가 쓸 수 있는 경로에 두는, core tool 이 된다.

1. **ReadFile**: 지정된 파일의 내용을 읽어오는 도구
2. **WriteFile**: 지정된 경로에 새로운 파일을 생성하거나 덮어쓰는 도구
3. **Replace**: 파일 내의 특정 문자열을 찾아 정확히 치환하는 도구 (가장 많이 쓰임)
4. **RunShellCommand**: 터미널에서 Bash 명령어를 실행하고 결과를 반환하는 도구
5. **GrepSearch**: 정규식을 이용해 프로젝트 내 파일 내용을 검색하는 도구
6. **Glob**: 패턴(예: `src/**/*.java`)을 이용해 파일 경로를 찾는 도구
7. **ListDirectory**: 특정 디렉토리의 파일/폴더 목록을 조회하는 도구

### 3.3. Spring Modulith를 통한 Vertical Slice 아키텍처
새로운 에이전트를 추가할 때(Vibe Coding 시), 기존 코드와의 얽힘 없이 독립적으로 기능할 수 있도록 모듈화를 강제한다.
- **패키지 구조**: `io.autocrypt.jwlee.cowork.agents.<agent_name>` 하위에 해당 에이전트와 관련된 Shell Command, Agent 클래스, Tools, States 등을 모두 몰아넣는다.
- **Modulith 검증**: `spring-modulith-starter-test`를 적용하여 패키지 간 순환 참조나 허가되지 않은 의존성이 발생하면 빌드가 실패하도록 구성한다.

### 3.4. 에이전트 스캐폴딩 (Boilerplate)
Vibe Coding을 통해 손쉽게 새 에이전트를 만들 수 있도록 표준 템플릿(Boilerplate)을 설계한다. 
템플릿 패키지 (`agents.scaffold`)는 다음 요소들을 포함한다.
1. `XXXCommand.java`: Spring Shell 진입점 (명령어 정의 및 AgentProcess 시작)
2. `XXXAgent.java`: Embabel `@Agent` 메인 클래스 (행동 정의)
3. `XXXState.java`: 작업 계획 -> 승인 대기 -> 실행으로 이어지는 상태 인터페이스
4. `XXXTools.java`: 해당 에이전트 전용 `@LlmTool` 모음

## 4. 단계별 진행 계획 및 진행 상황 (Milestones & Progress)

- [x] **Phase 1: 기반 설정 및 도구 구현**
  - **Spring Modulith 의존성 추가 및 아키텍처 검증**: `pom.xml` 구성 및 `src/test/java/io/autocrypt/jwlee/cowork/ModulithArchitectureTest.java` 작성 완료. `core` 모듈을 OPEN 모듈(`src/main/java/io/autocrypt/jwlee/cowork/core/package-info.java`)로 선언하여 `agents` 모듈에서 안전하게 참조하도록 구성.
  - **공통 도구(Core Tools) 구현**: 작업 디렉토리 상위(`../`) 접근을 원천 차단하는 보안 로직 적용 완료.
    - 파일 관련 (`ReadFile`, `WriteFile`, `Replace`, `ListDirectory`, `Glob`): `src/main/java/io/autocrypt/jwlee/cowork/core/tools/CoreFileTools.java`
    - 시스템 관련 (`RunShellCommand`, `GrepSearch`): `src/main/java/io/autocrypt/jwlee/cowork/core/tools/CoreShellTools.java`

- [x] **Phase 2: HITL 워크플로우 기반 뼈대 완성**
  - **이벤트 기반 비동기 아키텍처 도입**: 단일 쓰레드 터미널(JLine) 환경에서 백그라운드로 도는 에이전트들의 승인 요청이 충돌 없이 안전하게 프롬프트(Y/N)로 표출되도록 구현.
  - **주요 구현물 (경로: `src/main/java/io/autocrypt/jwlee/cowork/core/hitl/`)**:
    - `ApprovalDecision.java`, `ApprovalRequestedEvent.java`: 사용자 응답과 이벤트 규격
    - `CoreApprovalState.java`: 에이전트가 `WaitFor` 대기 상태에 진입하기 직전, `ApplicationContextHolder`를 이용해 정적으로 Spring 이벤트를 발행하는 상태 클래스.
    - `ApplicationContextHolder.java`: Spring DI 밖의 객체(Embabel State)가 스프링 퍼블리셔를 쓸 수 있게 연결.
    - `TerminalApprovalListener.java`: `@Async` 기반 리스너. 이벤트를 수신하면 터미널 화면에 승인 폼을 띄우고, 사용자의 입력(Decision)을 블랙보드에 꽂아 넣은 후 `process.run()`으로 에이전트를 재개시킴.
  - **동작 데모 에이전트**: `demo-hitl` 명령어를 통해 실행해볼 수 있는 참조 모델. (`src/main/java/io/autocrypt/jwlee/cowork/agents/demo/DemoHitlAgent.java` 및 `DemoHitlCommand.java`)
  - *특이사항*: Spring Shell 대화형 테스트 코드 실행 시 스트림 블로킹 이슈가 있어 TDD 기반 자동화 테스트 대신 수동/아키텍처 검증으로 대체함.

- [x] **Phase 3: 스캐폴딩 패키지 작성**
  - 복사-붙여넣기(또는 자동 생성)로 즉시 쓸 수 있는 `agents.scaffold` 패키지 구성 (Command, Agent, State, Tools)

- [x] **Phase 4: 첫 번째 샘플 에이전트 구현**
  - 스캐폴딩을 이용해 실제로 동작하는 커스텀 에이전트 1개(예: ReadmeUpdater 등) 구현 및 테스트

- [x] **Phase 5: 이 프로젝트에 대한 README_new.md 파일 생성**

