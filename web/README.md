# AutoCrypt Inhouse Agents - Developer Guide

본 프로젝트는 전사 직원이 각자의 직무에 특화된 AI 에이전트를 활용할 수 있도록 구축된 사내 에이전틱 AI 플랫폼임. 개별 부서의 개발자가 새로운 에이전트를 추가하고 UI를 구성하는 절차를 설명함.

## 1. 기술 스택 (Tech Stack)
- **Backend**: Java 21 / Spring Boot 3.5 / Embabel AI Framework
- **Frontend**: Thymeleaf / HTMX / Alpine.js / Tailwind CSS (CDN 방식)
- **Database**: H2 Database (File Mode, `./data/agentdb` 저장)
- **RAG**: In-memory Lucene Index (작업 종료 시 휘발됨)

## 2. 에이전트 추가 절차 (How to add a New Agent)

새로운 에이전트를 추가하려면 다음 3가지 구성 요소가 필요함.

### STEP 1: Web Interface 구현 (`web.agent.Agent` 인터페이스)
`web/src/main/java/io/autocrypt/jwlee/cowork/web/agent/` 패키지에 클래스 생성 및 `Agent` 인터페이스 구현함.
- `getId()`: 에이전트 식별자 (소문자, 하이픈 권장)
- `getName()`: 마켓플레이스에 표시될 이름
- `getDescription()`: 에이전트 기능 설명
- `execute(Map<String, String> params)`: 비동기로 실행될 핵심 로직 구현 (CompletableFuture 반환 필수)

### STEP 2: Embabel 로직 구현 (Inner Logic)
에이전트 클래스 내부에 `@Agent` 어노테이션이 붙은 정적 내부 클래스를 만들거나 외부 빈을 호출함.
- `PresalesWebAgent`의 `PresalesLogicAgent` 구조를 참고할 것
- `@Action` 메서드 내에서 `ctx.ai()`를 통해 LLM 호출 및 RAG 활용 가능
- `RepeatUntilAcceptableBuilder`를 활용한 자기 피드백 루프 구성 권장

### STEP 3: 전용 UI 템플릿 생성 (`templates/agents/{id}.html`)
`web/src/main/resources/templates/agents/` 경로에 `{agent-id}.html` 파일을 생성함.
- **HTMX 연동**: `<form hx-post="/agents/{id}/run" hx-encoding="multipart/form-data">` 형식을 사용함
- **Polling**: `#task-status` 영역에 `hx-get="/tasks/{taskId}/status"`를 통해 실시간 상태 업데이트 구현함
- **OOB Swap**: 작업 완료 시 우측 패널을 교체하려면 `hx-swap-oob="true"` 속성을 활용함

## 3. 주요 기능 활용 가이드

### 다중 파일 업로드 및 RAG 연동
- UI에서 `multiple` 속성으로 여러 파일을 받을 수 있음
- 컨트롤러에서 콤마(,)로 연결된 경로를 에이전트에 전달함
- `LocalRagTools.ingestUrlToMemory()`를 사용하여 개별 작업(Task)만의 독립적인 인메모리 지식 베이스 구축 가능함

### Reveal.js 슬라이드 뷰어
- 에이전트 ID를 `reveal-slides`로 설정할 경우, 상세 페이지에서 즉시 온라인 슬라이드 뷰어 모드 사용 가능함
- LLM 결과물 출력 시 `---` (가로 슬라이드) 및 `--` (세로 슬라이드) 구분자를 사용하도록 프롬프트 작성함

## 4. 로컬 실행 및 빌드
- **별도의 Node.js 설치 불필요** (Tailwind 및 라이브러리는 CDN 활용)
- **필수 환경 변수**: 기본 프로파일 기준 `GEMINI_API_KEY`
- **빌드**: 저장소 루트에서 `./mvnw -f web/pom.xml -DskipTests compile`
- **실행**: 저장소 루트에서 `./mvnw -f web/pom.xml spring-boot:run`
- **대안 실행**: `web` 디렉토리에서는 `../mvnw -f pom.xml spring-boot:run`
- **접속 주소**: `http://localhost:8081`
- **데이터 보존**: 실행 이력은 `web/data/` 폴더에 파일로 남으므로 `mvn clean` 시에도 안전하게 외부 경로로 설정되어 있음

## 5. 개발 표준
- **Lombok 사용 금지**: 모든 도메인/엔티티는 표준 Getter/Setter를 사용하며, 생성자 주입 방식을 고수함
- **Standalone 원칙**: `web` 모듈은 상위 경로의 의존성 없이 독립적으로 빌드 및 실행 가능해야 함
- **비동기 처리**: AI 작업은 반드시 `@Async`와 `CompletableFuture`를 사용하여 사용자의 브라우저 요청을 즉시 해제함
