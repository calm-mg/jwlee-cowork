# Bitbucket PR Review Agent 사용 가이드

이 에이전트는 Bitbucket PR의 Diff를 분석하여 스타일 가이드 준수 여부와 소프트웨어 아키텍처 설계를 검토합니다.

## 1. 주요 기능 및 개선 사항
- **계층적 번들링 (Hierarchical Bundling)**: 5,000 토큰 단위로 Diff를 묶어 분석 효율을 높였습니다.
- **분석 이원화 및 RAG 정책**:
  - **Style Review**: RAG를 사용하지 않고 오직 스타일 가이드 문서와 코드만으로 분석합니다.
  - **Architecture Review**: Confluence 아키텍처 가이드를 우선 참조하며, 추가 정보(표준/매뉴얼)가 필요한 경우에만 RAG를 탐색합니다.
- **가이드 필수화**: 스타일 및 아키텍처 가이드 URL은 필수 입력 사항입니다.
- **RAG 선택화**: 표준 문서 및 제품 매뉴얼 경로는 선택 사항이며, 비어있을 경우 RAG 기능을 사용하지 않습니다.

## 필수 설정 (Environment Variables)
아래 환경 변수 중 하나가 설정되어 있어야 Bitbucket API 호출이 가능합니다.
- `BITBUCKET_API_TOKEN`: Bitbucket App Password 또는 Access Token
- `ATLASSIAN_API_TOKEN`: Atlassian API Token (공용 사용 시)

추가로 LLM 호출을 위해 아래 설정이 필요합니다.
- `GEMINI_API_KEY`: 기본 Gemini 프로파일에서 사용

기본 스타일 가이드 URL과 아키텍처 가이드 URL이 Confluence를 가리키므로, 가이드까지 함께 참조하려면 `ATLASSIAN_API_TOKEN`이 설정되어 있어야 합니다.

## 저장소 루트에서 바로 실행하기
Spring Shell에 직접 들어가거나, non-interactive 모드로 1회 실행할 수 있습니다.

```bash
# 최소 빌드 확인
./mvnw -DskipTests compile

# 인터랙티브 셸 실행
./mvnw spring-boot:run
```

셸이 뜬 뒤 아래처럼 실행합니다.

```bash
pr-review \
  --repo "autocrypt/securityplatform" \
  --prId 2256 \
  --styleGuide "https://auto-jira.atlassian.net/wiki/x/EwBfN" \
  --archGuide "https://auto-jira.atlassian.net/wiki/x/iwJOaw" \
  --standards "output/00.materials/v2x-ee/tech" \
  --manuals "output/00.materials/v2x-ee/prod"
```

한 번에 바로 실행하려면 아래 명령을 사용합니다.

```bash
./mvnw spring-boot:run \
  -Dspring-boot.run.arguments="pr-review --repo autocrypt/securityplatform --prId 2256 --styleGuide https://auto-jira.atlassian.net/wiki/x/EwBfN --archGuide https://auto-jira.atlassian.net/wiki/x/iwJOaw --standards output/00.materials/v2x-ee/tech --manuals output/00.materials/v2x-ee/prod" \
  -Dspring.profiles.include=cron
```

## 2. CLI 사용법
JWLEE CLI 쉘에서 `pr-review` 명령어를 사용합니다.

```bash
pr-review \
  --prId 2256 \
  --styleGuide "https://auto-jira.atlassian.net/wiki/x/EwBfN" \
  --archGuide "https://auto-jira.atlassian.net/wiki/x/iwJOaw" \
  --standards "output/00.materials/v2x-ee/tech" \
  --manuals "output/00.materials/v2x-ee/prod"
```

### 파라미터 상세
- `--repo`: 저장소 슬러그 (기본값: `autocrypt/securityplatform`)
- `--prId`: (Mandatory) Bitbucket Pull Request ID
- `--styleGuide`: (Mandatory) 스타일 가이드 Confluence URL
- `--archGuide`: (Mandatory) 아키텍처 가이드 Confluence URL
- `--standards`: (Optional) 표준 문서 폴더 경로. 비워두면 RAG 미사용.
- `--manuals`: (Optional) 제품 매뉴얼 폴더 경로. 비워두면 RAG 미사용.
- `--show-prompts`: 프롬프트 출력
- `--show-responses`: AI 응답 출력

`--manuals`, `--standards` 경로가 실제 디렉토리일 때만 RAG 인덱싱이 수행됩니다. 경로가 없으면 해당 지식 소스 없이 리뷰가 진행됩니다.

## 3. 분석 기준
- **Style 분석**: 코딩 컨벤션, 가독성, 명명법.
- **Arch 분석**: SOLID 원칙, 계층 분리, 아키텍처 가이드라인 준수.

## 4. 출력 결과
- Bitbucket PR에 라인별 코멘트 및 전역 요약 리포트가 게시됩니다.
- CLI 화면에 통합 품질 점수가 출력됩니다.
