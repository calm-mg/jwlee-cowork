# Embabel Agent DSL: BitbucketPrReviewAgent (v1.0)
<!--
이 문서는 Bitbucket PR 리뷰를 자동화하는 Embabel 에이전트의 명세서입니다.
Confluence 스타일/아키텍처 가이드와 제품 매뉴얼·표준 문서를 참조하여 논리적 결함 및 스펙 준수 여부를 검사합니다.
참조: guides/DSL_GUIDE.md
-->

## 1. Metadata
```yaml
agent:
  name: BitbucketPrReviewAgent
  description: "Bitbucket PR의 변경사항을 분석하여 논리, 제품 스펙, 스타일 가이드, 테스트 충실도를 검사하는 에이전트"
  timezone: "Asia/Seoul"
  language: "Korean"
  workspace: "bitbucket-pr-review"
```

## 2. Dependencies
- `CoreFileTools`: 로컬 파일 시스템의 매뉴얼 및 표준 문서 읽기
- `GitTools`: Bitbucket PR Diff 추출 및 분석 (Diff Parser 포함)
- `LocalRagTools`: 제품 매뉴얼 및 표준 문서용 Lucene RAG 인스턴스 관리
- `PromptProvider`: Jinja2 템플릿 (`.jinja`) 로딩
- `CoreWorkspaceProvider`: 분석 결과 및 임시 파일 저장 경로 제공
- `ApplicationContext`: 이벤트 발행 (Notification)

## 3. Domain Objects (DTOs)
```yaml
# PR 분석 요청 정보
PrReviewRequest:
  repositorySlug: String # @NotBlank
  pullRequestId: Long # @Min(1)
  manualsDir: String # 개발자 제품 매뉴얼 폴더 경로
  standardsDir: String # 구현 대상 표준 문서 폴더 경로
  styleGuideUrl: String # Golden Rule 스타일 가이드 URL (권장: full page URL, 호환: https://auto-jira.atlassian.net/wiki/x/EwBfN)

# 코드 리뷰 코멘트 객체
CodeComment:
  fileName: String
  lineNumber: Integer # Nullable (전역 코멘트의 경우 null)
  content: String # Markdown 형식의 리뷰 내용
  type: String # "GLOBAL" | "LINE"
  severity: String # "MUST_FIX" | "SHOULD_FIX" | "SUGGESTION"
  criteriaId: String # 10개 기준 중 어떤 것에 해당하는지

# 개별 파일/번들 분석 결과 (스타일)
StyleAnalysisResult:
  comments: List<CodeComment>
  score: Integer # 0-100

# 개별 파일/번들 분석 결과 (아키텍처)
ArchAnalysisResult:
  comments: List<CodeComment>
  score: Integer # 0-100

# PR 메타데이터
PrMetadata:
  title: String
  description: String
  overview: String # PR description의 Overview 섹션 또는 fallback 요약
  totalFilesChanged: Integer
  productionFileCount: Integer
  testFileCount: Integer
  addedLines: Integer
  removedLines: Integer
  newFileCount: Integer
  testFocused: Boolean
  largeFeatureChange: Boolean

# 최종 수합 리포트
FinalReviewReport:
  overallScore: Integer # 전체 정량적 점수
  styleScore: Integer # 스타일 품질 점수
  architectureScore: Integer # 아키텍처/요구사항 적합성 점수
  summary: String # 전체 요약 (전역 코멘트 포함)
  globalComments: List<CodeComment>
  lineComments: List<CodeComment> # 파일별, 라인별로 정렬된 코멘트
  totalIssuesFound: int # GOOD 코멘트는 제외한 실제 이슈 수
  truncatedFiles: List<String> # 분석 한계를 넘어선 파일 목록
```

## 4. Workflow States
```yaml
State: InitialState:
  request: PrReviewRequest

State: DraftContext:
  request: PrReviewRequest
  diffSegments: List<DiffSegment> # GitTools로 추출된 파일별 Diff
  manualsRagKey: String 
  standardsRagKey: String 
  styleGuideContent: String
  archGuideContent: String
  prMetadata: PrMetadata

State: ReadyContext:
  request: PrReviewRequest
  bundles: List<ConcatenatedDiff> # 토큰 제한에 맞춰 묶인 Diff 번들
  manualsRagKey: String
  standardsRagKey: String
  styleGuideContent: String
  archGuideContent: String
  prMetadata: PrMetadata

State: AnalysisState:
  styleResults: List<StyleAnalysisResult>
  archResults: List<ArchAnalysisResult>
```

## 5. Actions

### Action: PrepareReviewContext
- **Goal**: RAG 인스턴스 초기화 및 PR Diff 데이터 로드
- **Input**: `InitialState`
- **Output**: `DraftContext`
- **Logic**:
  - Bitbucket PR의 `title`, `description`, `diff`를 조회합니다.
  - `description`에서 `Overview` 섹션을 우선 추출하고, 없으면 본문/제목에서 fallback 요약을 생성합니다.
  - Diff 기반으로 테스트 중심 PR 여부와 대규모 기능/구조 변경 가능성을 추정합니다.

### Action: ConcatenateSegments
- **Goal**: 효율적인 분석을 위해 Diff 세그먼트를 번들로 묶음
- **Input**: `DraftContext`
- **Output**: `ReadyContext`
- **Logic**:
  - C/H 파일 등 연관된 파일은 가급적 같은 번들에 포함시킵니다.
  - 번들당 약 5000 토큰(약 20,000자)을 넘지 않도록 구성합니다.
  - 아키텍처 가이드 문서를 Confluence에서 로드합니다.

### Action: AnalyzeCodeSegment
- **Goal**: 번들링된 Diff에 대해 단일 LLM 호출로 스타일 및 아키텍처 분석 결과를 동시에 생성
- **Input**: `ReadyContext`
- **Output**: `AllAnalysisResults` (각 번들 호출 결과에서 StyleAnalysisResult와 ArchAnalysisResult를 함께 추출)
- **LLM Configuration**:
  - `role`: performant
  - `temperature`: 0.1
  - `template`: `agents/bitbucketprapp/analyze-code.jinja`
- **Prompt Requirements (The 10+ Criteria)**:
  1. **논리적 무결성**: 알고리즘 오류, Edge Case 처리 누락.
  2. **표준 준수 (Highest Priority)**: 필요 시 RAG로 조회한 표준 문서 규격 일치 여부.
  3. **제품 스펙 준수**: 매뉴얼에 명시된 기능 정의와의 일치성.
  4. **스타일 가이드 준수**: 지정된 Confluence Golden Rule 적용 여부.
  5. **테스트 존재 여부 (Context Aware)**: 프로덕션 로직 변경 규모에 비례하여 테스트 기대치를 조정.
  6. **테스트 유의미성**: Assert가 적절한지, 주요 로직을 검증하는지 검사하되 테스트 전용 PR은 기준 완화.
  7. **자원 및 성능**: 메모리 누수, 불필요한 루프, DB 쿼리 비효율.
  8. **보안**: 하드코딩된 비밀번호, SQL 인젝션, 권한 검사 누락.
  9. **가독성 및 명명법**: 변수/함수 이름의 직관성, 함수 길이.
  10. **예외 처리**: 적절한 예외 타입 사용 및 로그 기록 여부.
  - PR Overview와 변경 규모를 함께 주입하여, 단일 응답 안에서 `STYLE_GUIDE` 점수와 `ARCHITECTURE` 점수를 독립적으로 산정합니다.
  - standards/manuals RAG 참조는 `ARCHITECTURE` 판단에만 사용하고, `STYLE_GUIDE` 평가는 가이드 문서와 diff 중심으로 제한합니다.
  - GOOD 코멘트는 선택 사항이며, 점수 가산용으로 남발하지 않습니다.

### Action: SynthesizeFinalReport
- **AchievesGoal**: 모든 분석 결과를 누락 없이 수합하고 최적화하여 최종 리포트 생성
- **Input**: `AnalysisState`
- **Output**: `FinalReviewReport`
- **Logic**:
  - 중복된 코멘트(여러 파일에서 공통적으로 발생하는 스타일 오류 등)를 병합하여 전역 코멘트로 승격하거나 정리합니다.
  - GOOD 코멘트는 전역 하이라이트 위주로 최대 2개만 유지합니다.
  - `isTruncated`인 파일의 경우, 첫 번째 라인 코멘트로 "이 파일은 500줄을 초과하여 상단부만 분석되었습니다."라는 경고를 강제 삽입합니다.
  - `styleScore`, `architectureScore`, `overallScore`를 분리 계산합니다.
  - `totalIssuesFound`는 GOOD 코멘트를 제외한 실제 문제 수만 집계합니다.

## 6. Implementation Guidelines
- **Modulith Package**: `io.autocrypt.jwlee.cowork.bitbucketprapp` 패키지 사용.
- **RAG Strategy**: `localRagTools.getOrOpenInstance`를 사용하여 영속성을 유지하며, standards/manuals는 필요 시에만 `ARCHITECTURE` 분석에 참조합니다.
- **Style Guide Injection**: Confluence URL의 내용은 실제 구현 시 `PromptProvider`를 통해 텍스트 전문이 프롬프트의 `<style_guide>` 태그 내에 주입되도록 처리합니다.
- **PR Context Awareness**: PR Overview/설명과 변경 규모 통계를 분석 프롬프트에 함께 주입합니다.
- **Positive Feedback Control**: GOOD 코멘트는 선택 사항이며, 최종 보고서에서는 최대 2개까지만 유지합니다.
- **Test Enforcement**: 테스트 관련 지적은 컨텍스트 기반으로 수행하며, 대규모 프로덕션 변경에서 검증 근거가 거의 없을 때만 MUST_FIX 수준을 권고합니다.
- **Token Efficiency**: 번들당 스타일/아키텍처를 별도 호출하지 않고 한 번의 분석 응답으로 함께 생성하여 중복 프롬프트 비용을 줄입니다.
- **Concurrency**: 여러 Diff 번들은 병렬로 실행 가능하도록 설계하며, Embabel의 GOAP 플래너가 이를 최적화하도록 지원합니다.
