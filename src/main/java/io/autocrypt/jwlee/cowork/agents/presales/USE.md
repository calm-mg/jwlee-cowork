# USE PresalesAgent

고객 요구사항(이메일 등)을 분석하여 기술 요구사항 명세(CRS)를 작성하고, 제품 사양과의 Gap 및 공수를 산출합니다.

## CLI 명령어

### 1. 지식 베이스 구축 (RAG 인제스트)
```bash
> presales-ingest --type TECH --path "standards_dir"
> presales-ingest --type PRODUCT --path "specs_dir"
```

### 2. 분석 시작
```bash
> presales-start --email-path "email.txt" --ws "ws_01"
```

### 3. 수정 후 재개 (Resume)
사용자가 `crs.md`를 직접 수정한 뒤 분석 결과만 갱신할 때 사용합니다.
```bash
> presales-resume --ws "ws_01"
```

## 결과물 확인
워크스페이스 폴더(`output/presales/{ws}/`) 내에 `crs.md`, `analysis.md` 등이 생성됩니다.
