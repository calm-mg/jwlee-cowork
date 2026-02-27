# Embabel Presentation Agent Mastery (102)

이 가이드는 `jwlee-cowork` 프로젝트를 기반으로, Embabel 0.3.4 환경에서 지능형 발표자료 생성 에이전트를 구축하는 모든 과정을 상세히 기술함.

---

## 1. 프로젝트 기반 기술 Q&A (Deep Insight)

### 1.1. 검색 및 RAG 엔진 관련
- **Lucene은 왜 사용하는가?**: JVM 환경에서 별도의 서버(Elasticsearch 등) 설치 없이 프로세스 내부에서 동작하는 가장 강력하고 검증된 오픈소스 검색 엔진임. 고성능 키워드 검색(BM25)을 기본으로 제공하며, 로컬 환경에서 가장 빠르고 경제적임.
- **Tika는 무엇인가?**: 문서판 '만능 맥가이버 칼'임. PDF, Word, PPT 등 수천 가지 파일 형식에서 텍스트와 메타데이터를 추출하여 검색 엔진(Lucene)이 읽을 수 있는 형태로 변환해 주는 필수 도구임.
- **Lucene의 인덱싱 주기**: Embabel은 시작 시 소스 폴더(`knowledge`)를 스캔함. 파일의 변경이나 추가가 감지된 경우에만 증분 인덱싱(Incremental Indexing)을 수행하여 효율적으로 최신성을 유지함.
- **Scale-out 및 Stateless 전략**: Lucene은 로컬 머신 전용임. 서버를 수평 확장하려면 `PostgreSQL + pgvector` 모듈을 연동하거나, 외부 전문 Vector DB(Chroma, Milvus 등)를 연결하여 앱 인스턴스들이 동일한 지식 저장소를 보게 해야 함.

### 1.2. 인공지능 모델 및 운용 관련
- **Embedding Model List 출력 이유**: 시스템이 현재 가용한 모델 자산을 명확히 파악하고 있는지 확인하기 위함임. 특히 RAG 작동 시 '의미 기반 검색'이 가능한지 디버깅하는 핵심 지표가 됨.
- **Rate Limit (RPM) 대응**: 에이전트는 루프(계획-검색-생성) 한 번에 LLM을 여러 번 호출함. 무료 티어 사용 시 `backoff-millis`를 2500ms 이상으로 설정하고, 안정적인 `gemini-1.5-flash` 모델을 기본으로 사용하는 것이 유리함.

---

## 2. 실전 시스템 설정 (Configuration)

### 2.1. 정석적인 RAG 및 DI 설정 (`RagConfiguration.java`)
임베딩 엔진에 대한 강제 의존성을 제거하고, 텍스트 검색만으로도 동작하게 설계함. 도구 생성 로직을 팩토리(Bean) 단계로 고립시켜 에이전트와의 결합도를 낮춤.

```java
@Configuration
@EnableConfigurationProperties(RagServiceEnhancerProperties.class)
public class RagConfiguration {

    @Value("${embabel.agent.rag.import.dir:knowledge}")
    private String importDir;

    @Bean
    public SearchOperations searchOperations() throws IOException {
        // 임베딩 없이 텍스트 기반 검색 수행하는 고성능 스토어
        return new DirectoryTextSearch(importDir);
    }

    @Bean
    public ToolishRag localKnowledgeTool(SearchOperations searchOperations) {
        // 엔진의 능력에 맞춰 도구를 자동 노출하는 Facade 생성
        return new ToolishRag("local_knowledge", "Search presentation resources", searchOperations);
    }
}
```

### 2.2. MCP 연동 (Spring AI Standard)
표준 프로토콜을 통해 사내 Confluence 등 외부 도구를 에이전트에 통합함.

```properties
# application.properties
spring.ai.mcp.client.enabled=true
spring.ai.mcp.client.stdio.connections.confluence.command=npx
spring.ai.mcp.client.stdio.connections.confluence.args=-y,@aashari/mcp-server-atlassian-confluence
spring.ai.mcp.client.stdio.connections.confluence.env.ATLASSIAN_SITE_NAME=your-jira-site
```

---

## 3. 에이전트 비즈니스 로직 (Business Logic)

### 3.1. 페이지별 독립 관리 설계
- **철학**: 전체 슬라이드를 매번 다시 생성하지 않고, 페이지별(`page_n.md`)로 분리하여 관리함.
- **효과**: 특정 페이지만 정밀하게 수정(`modifyExistingSlide`)할 수 있어 LLM의 정확도가 올라가고 토큰 비용이 획기적으로 절감됨.

### 3.2. 템플릿 카탈로그 지능형 활용
AI에게 각 레이아웃의 목적을 교육하여 스스로 최적의 디자인을 고르게 함.

**catalog.md (Knowledge Base)**
- `tpl-con-3-2`: 비교/상세 설명 (3:2 분할)
- `tpl-con-title`: 메인 타이틀
- `tpl-con-default-slide`: 일반 내용 전달 (기본값)

### 3.3. 최종 에이전트 코드 (`PresentationAgent.java`)

```java
@Agent(description = "Advanced Slides 전문 생성 및 수정 에이전트")
public class PresentationAgent {
    private final ToolishRag localKnowledgeTool;
    private final SlideFileService fileService;

    // 1. 설정부 (고정 템플릿 사용 - AI 생성보다 안정적)
    @Action
    public PresentationSettings initializeSettings(UserInput input) throws IOException {
        String fixedSettings = "--- theme: consult ... style tags ...";
        fileService.saveSettings(fixedSettings);
        return new PresentationSettings(fixedSettings);
    }

    // 2. 신규 생성 (카탈로그 기반 지능형 선택)
    @Action
    public SlidePage createNewSlide(UserInput input, Ai ai) throws IOException {
        return ai.withAutoLlm()
                .withReference(localKnowledgeTool)
                .createObject("카탈로그를 참조하여 최적의 템플릿으로 슬라이드 생성해줘", SlidePage.class);
    }

    // 3. 정밀 수정 (기존 파일 로드 후 업데이트)
    @Action
    public SlidePage modifyExistingSlide(UserInput input, Ai ai) throws IOException {
        Integer pageNum = ai.withAutoLlm().generateObject("몇 페이지 수정인지 추출해줘", Integer.class);
        String oldContent = fileService.readPage(pageNum);
        return ai.withAutoLlm().fromPrompt("기존 내용: " + oldContent + " 수정 요청: " + input.getContent());
    }

    // 4. 자동 병합 (최종 목표 달성)
    @AchievesGoal(description = "모든 페이지가 병합되어 발표 자료 완성")
    @Action
    public FinalPresentation merge(SlidePage lastUpdated) throws IOException {
        return new FinalPresentation(fileService.mergeAll());
    }
}
```

---

## 4. Troubleshooting 요약
1. **클래스명 하이픈**: `Project-Name` 형태는 Java 문법 위반이므로 `ProjectName`으로 수정 필수임.
2. **LlmTool 어노테이션**: 라이브러리 0.3.4 기준 `@Tool`이 아닌 `@LlmTool`을 사용해야 인식됨.
3. **withReference**: 여러 도구가 묶인 파사드 객체를 넘길 때는 `withToolObject`가 아닌 `withReference`를 써야 LLM이 인자를 인식함.
4. **Embedding 부재**: Native 스타터(`google-genai`)를 사용하지 않을 경우 임베딩 서비스가 누락되어 RAG 초기화 실패할 수 있음. `DirectoryTextSearch`로 우회하여 해결함.
