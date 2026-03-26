# USE MorningBriefingAgent

Jira와 Confluence의 어제자 변경 사항을 분석하여 업무 요약 리포트를 생성하고 오늘 할 일을 제안합니다.

## CLI 명령어

### 기본 실행 (어제 데이터 분석)
```bash
> morning-briefing
```

### 특정 날짜 분석
```bash
> morning-briefing --targetDate 2026-03-25
```

### 프롬프트 및 응답 로깅 (디버깅)
```bash
> morning-briefing -p -r
```

## 결과물 확인
상세 리포트 마크다운 파일은 다음 경로에 저장됩니다.
`output/morning-briefing/daily/artifacts/report_{YYYY-MM-DD}.md`

## 제약 사항
- `Asia/Seoul` 타임존을 기준으로 날짜를 계산합니다.
- `JiraExcelService`와 `ConfluenceService`가 정상적으로 설정되어 있어야 합니다.
