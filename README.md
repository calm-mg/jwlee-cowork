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

---

## 🛠 시작하기 (Getting Started)

### 요구 사항
- Java 21 이상
- `GEMINI_API_KEY` 환경 변수 설정

### 실행 방법
Maven Wrapper를 사용하여 애플리케이션을 빌드하고 실행합니다.

```bash
# 환경 변수 설정 (.envrc)
export GEMINI_API_KEY="your-api-key"

# 프로젝트 컴파일 및 테스트 (아키텍처 검증 포함)
./mvnw clean test

# 인터랙티브 셸 모드 실행
./mvnw spring-boot:run
```

실행 후 `>` 프롬프트가 나타나면 명령어 입력을 대기합니다.

---

## 💡 사용 가능한 명령어 예시

기본적으로 제공되는 샘플 에이전트를 통해 시스템의 동작 방식을 확인할 수 있습니다.

### HITL 데모 에이전트
Human-in-the-Loop 승인 절차가 터미널 환경에서 어떻게 비동기로 동작하는지 확인하는 간단한 데모입니다.
```bash
> demo-hitl "가상의 위험한 명령 실행"
```

---

## 🧑‍💻 나만의 에이전트 추가하기 (Developer Guide)

새로운 기능을 가진 AI 명령어(에이전트)를 추가하고 싶다면, 다음 단계를 따르세요.

1. **템플릿 복사**: `src/main/java/io/autocrypt/jwlee/cowork/agents/scaffold` 패키지를 복사하여 `agents.myfeature`와 같은 새로운 패키지를 생성합니다.
2. **이름 변경**: `Scaffold`로 시작하는 클래스명(Agent, Command, Tools, State)을 개발하려는 기능에 맞게 변경합니다. (예: `ReviewAgent`, `ReviewCommand` 등)
3. **의존성 주입**: `Agent` 클래스에서 필요한 도구(`CoreFileTools` 등)를 생성자(Constructor Injection)를 통해 주입받도록 구성합니다.
4. **프롬프트 작성**: `Agent` 클래스 내의 `@Action` 메서드에서 `Ai` 인터페이스를 활용하여 LLM에게 지시할 프롬프트(목표, 제약사항 등)를 작성합니다.
5. **승인 워크플로우 연결**: 작업 수행 전 `CoreApprovalState`를 반환하여 사용자의 승인을 받도록 설계하고, 승인 시 실행될 `@AchievesGoal` 메서드를 구현합니다.
6. **빌드 및 검증**: `./mvnw test`를 실행하여 새로운 에이전트가 Modulith 아키텍처 규칙을 위반하지 않았는지 검증합니다.
