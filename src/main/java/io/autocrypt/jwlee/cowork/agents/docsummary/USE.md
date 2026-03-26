# USE DocSummaryAgent

문서(PDF/Markdown)에서 핵심 용어와 개념을 추출하고 요약 리포트를 생성합니다.

## CLI 명령어

### 용어 추출 및 요약 생성
```bash
> doc-summary --filePath "document.pdf" --wsName "study_01" --maxTerms 20
```

## 주요 특징
- **자동 용어 추출**: 문서 전체를 스캔하여 핵심 기술 용어 및 약어를 추출합니다.
- **RAG 기반 정의**: 추출된 용어에 대해 문서의 문맥을 반영한 정확한 정의를 생성합니다.
- **포맷 지원**: PDF 파싱(이미지 추출 포함) 및 마크다운을 지원합니다.
