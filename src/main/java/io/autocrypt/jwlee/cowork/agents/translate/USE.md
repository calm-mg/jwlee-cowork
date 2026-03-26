# USE TranslateAgent

PDF 기술 문서를 읽고 전문 용어를 유지하며 마크다운 형식으로 번역합니다.

## CLI 명령어

### 번역 시작
```bash
> translate-start --pdf-path "my_doc.pdf" --workspace-name "ws_01"
```

## 환경 설정 (필수)
Python 환경이 구축되어 있어야 합니다.
```bash
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
```

## 주요 특징
- PyMuPDF 기반 고성능 파싱
- 용어집(Glossary) 자동 추출 및 적용
- 다단계 HITL(Human-in-the-Loop) 검토 프로세스
