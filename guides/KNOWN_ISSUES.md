# Known Issues

## 1. RAG 도구 응답 무시 현상 (Gemini JSON 응답 누락 버그)

### 현상
`ChatbotAgent`에서 `ToolishRag`를 사용하여 문서를 검색할 경우, 검색 엔진은 관련 텍스트(예: 진척도 35%)를 프레임워크 로깅 객체에 정상적으로 담아주지만, 최종적으로 LLM(Gemini)은 "정보를 찾을 수 없다"고 답변하며 환각(Hallucination)이 발생하는 현상

### 원인 분석
이 문제는 `Knowledge Cutoff`와 무관하며, Spring AI의 Gemini 어댑터에서 **비 JSON 형태의 도구 응답을 조용히 누락(Silently drops)**하는 프레임워크의 버그임

- Gemini(Google GenAI) API는 도구(Tool) 응답이 반드시 유효한 JSON 객체 형태여야 함
- RAG 검색 도구가 일반 텍스트(Plain Text) 형식으로 검색 결과를 반환할 때, `GoogleGenAiChatModel.parseJsonToMap()` 과정에서 이를 파싱하지 못하고 실제 API 전송 페이로드에서 데이터를 드랍시킴
- 프레임워크 내부 객체 로그(아래 증거 데이터)에는 응답이 존재하는 것처럼 보이지만, 실제 네트워크 단에서는 빈 값으로 전송되어 LLM이 정보를 찾을 수 없다고 답변하게 됨
- 참고 PR: https://github.com/embabel/embabel-agent/pull/1465

### 📝 증거 데이터 (Captured Full Prompt)

아래는 POC 환경에서 Spring AI 계층이 Gemini로 전송하기 직전에 로깅한 **실제 프롬프트 객체(Prompt Payload)** 전문의 핵심 발췌임
로깅 상으로는 데이터가 존재하지만 실제 전송 시 누락됨

#### 1. Gemini로 전송된 프롬프트 (Prompt payload)
```text
Prompt{messages=[
  SystemMessage{textContent='Current date: 2026-03-22
  ----
  Reference: knowledge
  Description: Internal docs
  Tool prefix: knowledge
  ... (중략) ...
  Knowledge cutoff: 2025-01', messageType=SYSTEM}, 
  
  UserMessage{content='이동의자유 BE FE 진척도는 어때?', messageType=USER}, 
  
  AssistantMessage[toolCalls=[ToolCall[name=knowledge_textSearch, arguments={"query":"이동의자유 BE FE 진척도"}]]], 
  
  ToolResponseMessage{responses=[
    ToolResponse[name=knowledge_textSearch, responseData=5 results:

chunkId: b0fdc7eb-b38c-499f-8413-8b10ca8c6c44 3.81 - # Title: 주간보고서 (2026-03-20)
# URI: file:///home/jwlee/workspace/jwlee-cowork/poc/rag-poc/poc_data.md
# Section: 주간보고서 (2026-03-20)

사업 지원
- **[이동의자유 (BE, FE)]** API 규격 조정, 신규 기능(주행/결제) 개발 및 CD 구성 : ~04/31 (35%)
... (원문 전체) ...
  ]]}
]}
```

#### 2. Gemini의 실제 답변 (Final Generation)
```text
Response: ChatResponse [generations=[Generation[assistantMessage=AssistantMessage [
  textContent="이동의자유 BE FE 진척도" 에 대한 정보를 찾을 수 없습니다. 다른 검색어를 사용하시겠습니까?
]]]]
```

### 해결 방안 및 워크어라운드(Workaround) 적용 상태
- **현재 상태:** 프레임워크 패치 대기 중 (프로젝트 내 워크어라운드 코드 적용 완료)
- 원본 프레임워크인 Embabel-agent에 PR(#1465)이 생성되었으나 아직 병합(Merge)되지 않아 최신 버전에서도 동일한 버그가 발생함
- 이를 해결하기 위해 `jwlee-cowork` 프로젝트 내부의 `core/workaround` 패키지에 `JsonSafeToolishRag` 래퍼(Wrapper) 클래스를 구현함
- 기존 `ToolishRag`를 직접 호출하지 않고 `JsonSafeToolishRag`로 감싸면, 검색 엔진이 내뱉는 일반 텍스트(Plain Text)를 가로채어 강제로 `{"result": "..."}` 형태의 JSON 포맷으로 변환함. 이미 JSON 형태인 데이터(`{`나 `[`로 시작)인 경우에는 이중으로 감싸지 않고(Double-wrapping 방지) 그대로 반환하도록 설계됨
- 이 조치를 통해 **Gemini 뿐만 아니라 텍스트 응답을 바로 수용하는 OpenAI, Groq 등 다른 LLM에서도 충돌이나 파싱 에러 없이 완벽하게 동작**함
- **추가 보완:** Tika 등을 통해 파싱된 PDF 데이터 내부에 포함된 제어 문자(Control Characters, 예: `CTRL-CHAR, code 9` 탭 문자 등)가 수동 이스케이프의 한계로 인해 Jackson 파서에서 `JsonParseException`을 유발하는 현상이 발견됨. 이를 원천 차단하기 위해 워크어라운드 클래스 내부에 수동 문자열 치환 대신 **Jackson의 `ObjectMapper`를 사용하여 `Map` 객체를 완벽한 규격의 JSON 문자열로 직렬화(Serialization)** 하도록 로직을 개선하여 100% 안정성을 확보함
- 추후 프레임워크에서 이 버그가 완전히 수정되면, `JsonSafeToolishRag` 대신 다시 `ToolishRag`를 사용하도록 되돌리기만 하면 됨
