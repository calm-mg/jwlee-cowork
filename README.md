# JWLEE Custom Agentic Shell

**JWLEE Custom Agentic Shell**은 개발자가 자신의 작업 워크플로우에 맞춰 임의로 AI 에이전트를 추가하고, 안전하게 제어할 수 있는 개인용 CLI(Command Line Interface) 플랫폼입니다. 

Gemini CLI와 유사한 기능을 제공하지만, Spring Shell과 Embabel 프레임워크를 기반으로 구축되어 강력한 확장성과 통제력을 갖춘 것이 특징입니다.

---

## 🚀 주요 기능 및 특징 (Key Features)

### 1. Human-in-the-Loop (HITL) 안전 장치
AI가 생성한 코드를 바로 적용하거나 시스템 명령어를 실행하기 전, 반드시 사용자에게 계획을 제시하고 **승인(Y/N)**을 받는 워크플로우를 기본 탑재했습니다. `CoreApprovalState`를 통해 파일 손상이나 예기치 않은 시스템 조작을 원천적으로 방지합니다.

### 2. 수직적 슬라이스 (Vertical Slice) 아키텍처
**Spring Modulith**를 활용하여 에이전트 간의 무분별한 의존성 얽힘을 물리적으로 차단했습니다. 
새로운 에이전트를 추가할 때는 `agents` 패키지 하위에 독립적인 모듈로 구성되며, 공통 기능(파일 조작 등)은 철저히 통제된 `core` 모듈만을 참조하도록 설계되었습니다.

### 3. Vibe Coding 최적화 (Scaffolding)
누구나 쉽게 자신만의 AI 명령어를 만들 수 있도록 `agents.scaffold` 패키지에 **복사-붙여넣기용 템플릿**을 제공합니다.
- `ScaffoldCommand`: Spring Shell 진입점
- `ScaffoldAgent`: LLM 프롬프트 및 행동 정의
- `ScaffoldTools`: 외부 API 및 도메인 로직 연동
- `ScaffoldState`: 다단계 상태(State) 기반 분기

### 4. 강력한 Core Tools 지원
에이전트가 즉시 활용할 수 있는 검증된 코어 도구(`@LlmTool`)들을 제공하며, 보안을 위해 작업 디렉토리 상위(`../`)로의 접근은 차단됩니다.
- **File Tools**: `readFile`, `writeFile`, `listDirectory`, `glob`

### 5. 프롬프트 및 페르소나 템플릿화
프롬프트와 에이전트 페르소나를 자바 코드에서 분리하여 리소스 파일로 관리합니다. `PromptProvider`를 통해 비즈니스 로직(Logic)과 페르소나/프롬프트(Vibe)를 분리하여 코드 재컴파일 없이 즉각적인 프롬프트 튜닝이 가능합니다.
- **구조화된 페르소나 관리**: 마크다운 헤더(`# ROLE`, `# GOAL`, `# BACKSTORY`)를 활용해 에이전트의 성격을 정의하고 `PromptProvider.getPersona()`로 즉시 객체화함
- **Jinja2 동적 렌더링**: `.jinja` 확장자를 지원하여 복잡한 조건문이나 반복문이 포함된 프롬프트를 유연하게 생성함
- **파일 기반 확장성**: 단순 고정형 프롬프트는 `.md` 또는 `.txt`로, 변수 주입이 필요한 프롬프트는 `.jinja`로 구분하여 명시적으로 관리함

---

## 🛠 시작하기 (Getting Started)

### 요구 사항
- Java 21 이상
- `GEMINI_API_KEY` 환경 변수 설정
  - 현재 `pom.xml` 기준으로 활성화된 LLM 스타터는 Gemini 경로입니다.
  - Groq / Ollama 관련 설정 파일은 있지만, Maven 프로파일 의존성은 주석 처리되어 있어 바로 실행되지는 않습니다.
- Bitbucket PR 리뷰를 사용할 경우 아래 추가 설정
  - `BITBUCKET_API_TOKEN` 또는 `ATLASSIAN_API_TOKEN`
  - Confluence 스타일 가이드를 함께 읽으려면 `ATLASSIAN_API_TOKEN`

### 실행 방법
Maven Wrapper를 사용하여 애플리케이션을 빌드하고 실행합니다. 아래 명령은 현재 저장소 기준으로 실제 검증한 경로입니다.

```bash
# 환경 변수 설정 예시
export GEMINI_API_KEY="your-api-key"

# 최소 빌드 확인
./mvnw -DskipTests compile

# 전체 테스트 실행
# 주의: 일부 테스트는 실제 LLM / Jira / Confluence 연동을 사용하므로
# 관련 토큰과 접근 가능한 사내 환경이 필요할 수 있습니다.
./mvnw test

# 인터랙티브 셸 모드 실행 (기본: Gemini 프로파일)
./mvnw spring-boot:run
```

실행 후 `>` 프롬프트가 나타나면 명령어 입력을 대기합니다.

### 빠른 시작: PR 리뷰 에이전트 실행
보안 라이브러리 팀처럼 Bitbucket PR 리뷰 자동화를 바로 써보려면 아래 순서가 가장 빠릅니다.

```bash
export GEMINI_API_KEY="your-api-key"
export BITBUCKET_API_TOKEN="your-bitbucket-token"
# Confluence 스타일 가이드 / Jira / Confluence 연동까지 쓸 경우
export ATLASSIAN_API_TOKEN="your-atlassian-token"

# 1) 인터랙티브 셸로 실행
./mvnw spring-boot:run

# 2) 셸에서 PR 리뷰 실행
pr-review \
  --repo "autocrypt/securityplatform" \
  --prId 123 \
  --styleGuide "https://auto-jira.atlassian.net/wiki/x/EwBfN" \
  --archGuide "https://auto-jira.atlassian.net/wiki/x/iwJOaw" \
  --manuals "guides" \
  --standards "standards"
```

셸을 띄우지 않고 한 번에 실행하려면 non-interactive 모드를 사용할 수 있습니다.

```bash
./mvnw spring-boot:run \
  -Dspring-boot.run.arguments="pr-review --repo autocrypt/securityplatform --prId 123 --styleGuide https://auto-jira.atlassian.net/wiki/x/EwBfN --archGuide https://auto-jira.atlassian.net/wiki/x/iwJOaw --manuals guides --standards standards" \
  -Dspring.profiles.include=cron
```

### 3. 비대화형(Non-interactive) 모드 실행 (자동화)
에이전트 명령어를 자동화 도구(Cron 등)에 등록할 때 사용합니다. `cron` 프로파일(`application-cron.yml`)을 활성화하면 셸 진입 없이 명령을 즉시 수행하고 종료합니다.

- **실행 방법**:
  ```bash
  # 'cron' 프로파일을 포함하여 단일 명령어 실행
  ./mvnw spring-boot:run -Dspring-boot.run.profiles=gemini -Dspring-boot.run.arguments="obsidian-daily" -Dspring.profiles.include=cron
  ```
- **전용 스크립트 활용**: `scripts/jwlee-cowork.sh`를 사용하면 환경 변수(`SPRING_PROFILES_INCLUDE=cron`) 설정을 통해 더 간편하게 자동화를 구현할 수 있습니다.

---

## 💡 사용 가능한 에이전트 (Available Agents)

각 에이전트의 상세한 CLI 명령어와 사용 방법은 해당 패키지 디렉토리의 **`USE.md`** 파일을 참조하세요.

1. **[MorningBriefingAgent](src/main/java/io/autocrypt/jwlee/cowork/morningbriefingagent/USE.md)**: 어제자 Jira/Confluence 활동 요약 및 오늘 할 일 제안
2. **[ObsidianAgent](src/main/java/io/autocrypt/jwlee/cowork/obsidianagent/USE.md)**: Obsidian 일일/주간 노트 생성 및 Git 동기화
3. **[PresalesAgent](src/main/java/io/autocrypt/jwlee/cowork/presalesagent/USE.md)**: 기술 요구사항 분석 및 제품 Gap 분석
4. **[TranslateAgent](src/main/java/io/autocrypt/jwlee/cowork/translateagent/USE.md)**: 기술 문서 PDF 전문 번역
5. **[DocSummaryAgent](src/main/java/io/autocrypt/jwlee/cowork/docsummaryagent/USE.md)**: 문서 핵심 용어 추출 및 요약
6. **[BitbucketPrReviewAgent](src/main/java/io/autocrypt/jwlee/cowork/bitbucketprapp/USE.md)**: Bitbucket PR 변경사항을 표준/매뉴얼/보안 기준으로 자동 리뷰하고 코멘트 게시
7. **[AgentGenerationPlanAgent](src/main/java/io/autocrypt/jwlee/cowork/planagentagent/USE.md)**: 신규 에이전트 DSL 설계 자동화

---

## 🤖 AI를 이용한 에이전트 설계 자동화 (Agent Planning)

에이전트 설계를 자동화하려면 `plan-agent` 명령어를 사용하세요. 상세한 가이드는 **[PlanAgent USE.md](src/main/java/io/autocrypt/jwlee/cowork/planagentagent/USE.md)**에 기술되어 있습니다.

---

## 📂 출력 디렉토리 규정 (Workspace Directory Standard)

**JWLEE Custom Agentic Shell**은 모든 에이전트의 산출물을 일관된 구조로 관리하여 프로젝트 유지보수성을 높입니다. 모든 작업 파일은 프로젝트 루트의 `output/` 디렉토리 아래에 에이전트별로 자동 분류됩니다.

### 표준 경로 구조
`output/{agent-name}/{workspace-id}/{sub-category}/`

- **agent-name**: 에이전트 고유 명칭 (예: `docsummary`, `translate`, `presales`)
- **workspace-id**: 작업 세션 식별자 (입력 파일명 기반의 자동 슬러그 또는 사용자 지정 ID)
- **sub-category**: 데이터 성격에 따른 분류
  - **rag**: 검색 엔진(Lucene) 데이터
  - **state**: 에이전트 상태 정보(state.json) 및 중간 처리 데이터
  - **artifacts**: 처리 과정에서 생성된 부속물 (이미지, 청크 파일 등)
  - **export**: 최종 결과물 (마크다운, CSV, 리포트 등)

### 자동 관리 원칙
- **경로 자동 표준화**: 사용자가 입력한 워크스페이스 ID는 시스템 안전을 위해 특수문자 제거 및 소문자 중심의 슬러그(slug)로 자동 변환함
- **자동 디렉토리 생성**: 에이전트 실행 시 필요한 서브 디렉토리를 `CoreWorkspaceProvider`가 자동으로 구성함
- **휘발성 RAG 인메모리 처리**: 임시 작업 시 발생하는 Lucene 인덱스는 파일시스템을 점유하지 않고 메모리 상에서만 관리됨

---

## 🧑‍💻 나만의 에이전트 추가하기 (Developer Guide)

새로운 기능을 가진 AI 명령어(에이전트)를 추가하고 싶다면, 다음 단계를 따르세요.

1. **템플릿 복사**: `src/main/java/io/autocrypt/jwlee/cowork/scaffold` 패키지를 복사하여 `agents.myfeature`와 같은 새로운 패키지를 생성합니다.agent
2. **이름 변경**: `Scaffold`로 시작하는 클래스명(Agent, Command, Tools, State)을 개발하려는 기능에 맞게 변경합니다. (예: `ReviewAgent`, `ReviewCommand` 등)
3. **의존성 주입**: `Agent` 클래스에서 필요한 도구(`CoreFileTools`, `PromptProvider` 등)를 생성자(Constructor Injection)를 통해 주입받도록 구성합니다.
4. **프롬프트 외부화**: 프롬프트와 페르소나를 `src/main/resources/prompts/agents/{myfeature}/` 하위에 파일로 작성하고, `PromptProvider`를 통해 로드하여 사용합니다. (Jinja 템플릿 권장)
5. **승인 워크플로우 연결**: 작업 수행 전 `CoreApprovalState`를 반환하여 사용자의 승인을 받도록 설계하고, 승인 시 실행될 `@AchievesGoal` 메서드를 구현합니다.
6. **빌드 및 검증**: `./mvnw test`를 실행하여 새로운 에이전트가 Modulith 아키텍처 규칙을 위반하지 않았는지 검증합니다.

---

## 📦 패키징 및 배포 (Packaging & Deployment)

Java(Spring Boot)와 Python 분석 스크립트가 공존하는 본 프로젝트는 `maven-assembly-plugin`을 통해 하나의 배포 패키지(ZIP)로 묶입니다.

### 1. 배포 패키지 생성
아래 명령어를 실행하면 `target/` 디렉토리에 JAR 파일과 Python 스크립트가 포함된 ZIP 파일이 생성됩니다.
```bash
./mvnw clean package -DskipTests
```
- **결과물**: `target/jwlee-cowork-{version}.zip`

### 2. 배포 및 실행 환경 설정
ZIP 파일의 압축을 해제한 후, Python 가상환경(`.venv`)을 설정해야 Python 기반 에이전트(예: StructureAgent)가 정상 동작합니다.

```bash
# 1. 압축 해제
unzip jwlee-cowork-0.1.0-SNAPSHOT.zip -d deploy/
cd deploy/

# 2. Python 가상환경 생성 및 의존성 설치
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
deactivate

# 3. 애플리케이션 실행
java -jar jwlee-cowork-0.1.0-SNAPSHOT.jar
```

### 3. 주의 사항
- **Python 의존성**: `StructureAgent` 등 일부 에이전트는 로컬의 `.venv/bin/python`과 `scripts/` 폴더를 직접 참조합니다. JAR 파일만 복사해서 실행할 경우 분석 실패 및 에러가 발생하오니 반드시 전체 패키지 구조를 유지해 주세요.
- **방어 로직**: 스크립트나 가상환경이 누락된 경우, 시스템은 할루시네이션(환각) 방지를 위해 분석을 중단하고 예외를 발생시킵니다.
