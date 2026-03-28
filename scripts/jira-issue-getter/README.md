# Jira 이슈 추출 및 자동 업로드 (jwlee-cowork 통합 버전)

이 스크립트는 Jira 프로젝트에서 이슈를 가져와 처리한 후, 데이터를 Excel 파일로 만들어 Power Automate 워크플로우로 이메일 전송합니다. 최종적으로 Power Automate가 파일을 SharePoint(OneDrive)에 업로드합니다. 이 모든 과정은 cron 작업을 통해 자동화되도록 설계되었습니다.

## 전체 아키텍처

1.  **Cron 작업:** `direnv`를 통해 환경 변수를 로드하고, `jwlee-cowork` 가상 환경의 Python으로 스크립트를 실행합니다.
2.  **Python 스크립트:** Jira API에서 데이터를 가져와 Excel 파일을 생성합니다. (`scripts/jira-issue-getter/out/` 경로에 저장)
3.  **보고서 발송:** 생성된 Excel 파일을 첨부하여 지정된 주소로 메일을 보냅니다.
4.  **Power Automate:** 특정 이메일을 수신하면, 첨부된 Excel 파일을 추출하여 지정된 SharePoint 폴더에 저장합니다.

## 설치 및 환경 설정

본 스크립트는 `jwlee-cowork`의 환경을 공유합니다.

1.  **의존성 설치:**
    `jwlee-cowork/requirements.txt`에 필요한 패키지(`pandas`, `requests`, `openpyxl`)가 포함되어 있으며, 가상 환경에 설치되어 있어야 합니다.
    ```bash
    cd ~/workspace/jwlee-cowork
    ./.venv/bin/pip install -r requirements.txt
    ```

## Power Automate 흐름 설정

이메일을 수신하여 SharePoint에 파일을 저장하고 원본 메일을 삭제하는 Power Automate 흐름을 생성해야 합니다.

1.  **트리거:** `새 이메일이 도착했을 때 (V3)`
    *   **받는 사람:** 스크립트가 메일을 보낼 주소 (예: `jira-report@autocrypt.io`)
    *   **첨부 파일 포함:** 예
    *   **제목 필터:** 고유한 제목 (예: `Jira Daily Report`)
2.  **액션 1:** `파일 만들기 (Create file)`
    *   **사이트 주소/폴더 경로:** 파일을 저장할 SharePoint 위치 지정
    *   **파일 이름:** 동적 콘텐츠에서 `첨부 파일 이름` 선택
    *   **파일 콘텐츠:** 동적 콘텐츠에서 `첨부 파일 콘텐츠` 선택
3.  **액션 2:** `이메일 삭제 (V2)`
    *   **메시지 ID:** 동적 콘텐츠에서 `메시지 ID` 선택 (원본 메일을 삭제하기 위함)

## 환경 변수 설정

스크립트가 정상적으로 동작하려면 아래 환경 변수들이 설정되어야 합니다.

-   `ATLASSIAN_API_TOKEN`: Jira API 토큰
-   `SMTP_USER`: 발신자 이메일 주소 (예: `support_v2x@autocrypt.io`)
-   `SMTP_PASSWORD`: 발신자 이메일 계정의 비밀번호 또는 **앱 비밀번호(App Password)**
    *   **중요:** 계정에 다단계 인증(MFA)이 설정된 경우, 반드시 '앱 비밀번호'를 생성하여 사용해야 합니다.

## 실행 방법

### 수동 실행 (테스트용)
`direnv exec`를 사용하여 어디서든 동일한 환경으로 실행할 수 있습니다.
```bash
direnv exec /home/jwlee/workspace/jwlee-cowork /home/jwlee/workspace/jwlee-cowork/.venv/bin/python /home/jwlee/workspace/jwlee-cowork/scripts/jira-issue-getter/jira-issue-getter.py
```

### Cron 작업 등록 (`crontab -e`)
매일 오전 6시(KST)에 실행되도록 설정된 최종 명령어입니다. 기존의 개별 환경 변수 설정 없이 `direnv`가 모든 설정을 처리합니다.

```bash
# KST 기준 매일 오전 6시 00분(UTC 21:00)에 스크립트 실행
00 06 * * * direnv exec /home/jwlee/workspace/jwlee-cowork /home/jwlee/workspace/jwlee-cowork/.venv/bin/python /home/jwlee/workspace/jwlee-cowork/scripts/jira-issue-getter/jira-issue-getter.py >> /tmp/jira-getter.log 2>&1
```

## 주요 파일 위치
- **스크립트:** `jwlee-cowork/scripts/jira-issue-getter/jira-issue-getter.py`
- **결과물(Excel):** `jwlee-cowork/scripts/jira-issue-getter/out/CAM연구소_지표.xlsx`
- **로그:** `/tmp/jira-getter.log`
