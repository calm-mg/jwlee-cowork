#!/usr/bin/env python3

from datetime import datetime, timezone, timedelta
import os
import requests
import json
import pandas as pd
from openpyxl.cell.cell import ILLEGAL_CHARACTERS_RE
import base64
import traceback

# --- Configurations ---
TEAMS_WEBHOOK_URL = os.getenv('TEAMS_WEBHOOK_URL')
ATLASSIAN_EMAIL = "jwlee@autocrypt.io"
RAW_TOKEN = os.getenv('ATLASSIAN_API_TOKEN')

# (중요) 환경변수가 설정되지 않았을 경우를 대비해 에러 처리
if not RAW_TOKEN or not TEAMS_WEBHOOK_URL:
    raise ValueError("필수 환경변수(ATLASSIAN_API_TOKEN, TEAMS_WEBHOOK_URL)가 설정되지 않았습니다.")

auth_str = f"{ATLASSIAN_EMAIL}:{RAW_TOKEN}"
TOKEN = base64.b64encode(auth_str.encode('utf-8')).decode('utf-8')

TEAMS_NOTIFY_ON_SUCCESS = False # True로 설정하면 스크립트 성공 시에도 알림을 보냅니다
DEBUG_MODE = False # True로 설정하면 각 연도의 첫 번째 이슈 데이터를 JSON 형식으로 출력합니다
TARGET_YEARS = [2026]
MULTIPLIER = 1.0

# 스크립트의 현재 위치를 기준으로 경로 설정
SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
EXCEL_FILE_PATH = os.path.join(SCRIPT_DIR, 'out/CAM연구소_지표.xlsx')

def log(message):
    """Print message with KST timestamp."""
    kst = datetime.now(timezone(timedelta(hours=9)))
    print(f"[{kst.strftime('%Y-%m-%d %H:%M:%S')}] {message}")

def send_teams_notification(title, message, color="0076D7"):
    """Sends a notification to a Microsoft Teams channel using MessageCard format."""
    if not TEAMS_WEBHOOK_URL:
        log("Teams webhook URL not configured. Skipping notification.")
        return
    try:
        payload = {
            "@type": "MessageCard",
            "@context": "http://schema.org/extensions",
            "themeColor": color,
            "summary": title,
            "sections": [{
                "activityTitle": f"📢 {title}",
                "text": message,
                "markdown": True
            }]
        }
        requests.post(TEAMS_WEBHOOK_URL, json=payload)
    except Exception as e:
        log(f"Error sending Teams notification: {e}")

def get_done_date(row):
    """
    Iterate forwards through an issue's history to find the most recent 'Done' status change.
    """
    try:
        # The API returns histories newest-to-oldest. We iterate forwards to find the first 'Done',
        # which corresponds to the most recent one.
        histories = row.get('changelog.values') or row.get('changelog.histories')
        if not isinstance(histories, list):
            return None

        for history in histories:
            for item in history.get('items', []):
                if item.get('field') == 'status' and item.get('toString').lower() == 'done':
                    return history.get('created', '') # Return full timestamp
    except (KeyError, TypeError, IndexError):
        return None
    return None

def get_component(row):
    """Extracts the component name from the issue data."""
    try:
        # Assumes the column and data exist and are in the expected format
        return row['fields.components'][0]['name']
    except (KeyError, TypeError, IndexError):
        # Fails gracefully if the structure is not as expected
        return None

def get_in_progress_date(row):
    """Finds the first time an issue was moved to In Progress."""
    try:
        # The API returns histories newest-to-oldest. We iterate backwards (reversed)
        # to find the last history item, which corresponds to the chronologically first one.
        histories = row.get('changelog.values') or row.get('changelog.histories')
        if not isinstance(histories, list):
            return None
        
        for history in reversed(histories):
            for item in history.get('items', []):
                if item.get('field') == 'status' and item.get('toString').lower() == 'in progress':
                    return history.get('created', '') # Return full timestamp
    except (KeyError, TypeError, IndexError):
        return None
    return None

def fetch_issues(session, year, jql_addon=None):
    """
    Fetches all issues for a given year by constructing a JQL query and handling pagination.
    An optional JQL addon can be provided to further filter issues.
    """
    # Calculate date range for the JQL query
    after_date_str = f"{year-1}-12-31"
    before_date_str = f"{year+1}-01-01"

    base_jql = f'project = "VP" AND issueType in (Story, Bug, Hotfix) AND status=Done AND status changed to Done after "{after_date_str}" AND status changed to Done before "{before_date_str}"'
    
    jql = base_jql
    if jql_addon:
        jql = f"{base_jql} {jql_addon}"

    jql += " ORDER BY created DESC"
    
    # Step 1: Fetch all issue IDs using the new paginated search endpoint
    search_url = 'https://auto-jira.atlassian.net/rest/api/3/search/jql'
    all_issue_ids = []
    next_page_token = None
    
    while True:
        payload = {
            'jql': jql,
            'maxResults': 1000,
        }
        if next_page_token:
            payload['nextPageToken'] = next_page_token
            
        response = session.post(search_url, json=payload)
        response.raise_for_status()
        data = response.json()
        
        issue_ids = [issue['id'] for issue in data.get('issues', [])]
        all_issue_ids.extend(issue_ids)
        
        next_page_token = data.get('nextPageToken')
        if not next_page_token:
            break
            
    if not all_issue_ids:
        return []

    # Step 2: Fetch full issue details in bulk
    # Note: The changelog returned by bulk fetch may be limited to 40 items.
    # This might affect the accuracy of get_done_date for issues with very long histories.
    bulk_fetch_url = 'https://auto-jira.atlassian.net/rest/api/3/issue/bulkfetch'
    all_issues_details = []
    
    # Process IDs in chunks to avoid hitting API limits
    chunk_size = 100 
    for i in range(0, len(all_issue_ids), chunk_size):
        chunk_ids = all_issue_ids[i:i + chunk_size]
        
        # "changelog" is not a field, it's an expandable entity.
        fields_to_request = [
            "key", "assignee", "summary", "parent", "customfield_11375", 
            "created", "updated", "components", "issuetype"
        ]
        
        payload = {
            'issueIdsOrKeys': chunk_ids,
            'fields': fields_to_request,
            'expand': ['changelog'] # Correct way to request the changelog
        }
        
        response = session.post(bulk_fetch_url, json=payload)
        response.raise_for_status()
        data = response.json()
        all_issues_details.extend(data.get('issues', []))
        
    return all_issues_details

def process_issues_to_dataframe(issues, merged_keys_set):
    """
    Converts a list of issues into a processed Pandas DataFrame.
    """
    if not issues:
        return pd.DataFrame()

    df = pd.json_normalize(issues, max_level=4)

    # Add DORA-related columns
    df['inProgressDate'] = df.apply(get_in_progress_date, axis=1)
    df['doneDate'] = df.apply(get_done_date, axis=1)
    df['component'] = df.apply(get_component, axis=1)
    df['is_merged'] = df['key'].isin(merged_keys_set)

    columns_to_keep = [
        'key', 'fields.assignee.emailAddress', 'fields.summary', 
        'fields.parent.fields.summary', 'fields.customfield_11375', # customfield_11375 = Original Story Points
        'fields.created', 'fields.updated', 'doneDate', 'inProgressDate', 'component', 'is_merged', 'fields.issuetype.name'
    ]
    
    processed_df = df[[col for col in columns_to_keep if col in df.columns]]
    processed_df = processed_df.map(lambda x: ILLEGAL_CHARACTERS_RE.sub(r'',x) if isinstance(x, str) else x)
    
    if 'fields.customfield_11375' in processed_df.columns:
        processed_df['fields.customfield_11375'] = processed_df['fields.customfield_11375'].astype(float).fillna(0)
    
    return processed_df

def main():
    """
    Main function to orchestrate fetching, processing, and saving the Jira issues.
    It preserves data for years not in TARGET_YEARS if the Excel file already exists.
    """
    pd.set_option('display.max_rows', None)

    # Determine the source file for reading existing data
    backup_path = EXCEL_FILE_PATH + '.bak'
    source_file_for_reading = backup_path if os.path.exists(backup_path) else None

    # --- 1. Load existing data that we want to preserve ---
    existing_sheets_to_keep = {}
    if source_file_for_reading:
        log(f"Loading existing data from {source_file_for_reading} to preserve non-target years.")
        try:
            xls = pd.ExcelFile(source_file_for_reading)
            for sheet_name in xls.sheet_names:
                # Preserve sheets that are not targeted for update
                if sheet_name.isdigit() and int(sheet_name) not in TARGET_YEARS:
                    log(f"  - Preserving sheet: {sheet_name}")
                    existing_sheets_to_keep[sheet_name] = pd.read_excel(xls, sheet_name=sheet_name)
        except Exception as e:
            log(f"!!! WARNING: Could not read existing Excel file. It might be corrupted. A new file will be created. Reason: {e}")

    headers = {'Authorization': f'Basic {TOKEN}'}
    with requests.Session() as session:
        session.headers.update(headers)

        # --- 2. Process target years and collect new data ---
        newly_processed_sheets = {}
        for year in TARGET_YEARS:
            log(f"Processing year: {year}")
            
            log(f"Fetching all done issues for {year}...")
            all_done_issues = fetch_issues(session, year)

            merged_keys_set = set()
            try:
                log(f"Fetching merged issues for {year}...")
                merged_issues = fetch_issues(session, year, jql_addon="AND development[pullrequests].all > 0 AND development[pullrequests].open = 0")
                merged_keys_set = {issue['key'] for issue in merged_issues}
                log(f"Found {len(merged_keys_set)} merged issues.")
            except Exception as e:
                log(f"!!! WARNING: Could not fetch merged issues for {year}. The 'is_merged' column will be all False.")
                log(f"!!! Reason: {e}")
                log("!!! This may be due to a Jira configuration or permission issue with the 'development' JQL field.")

            if DEBUG_MODE and all_done_issues:
                log(f"--- DEBUG: Full JSON of first issue for {year} ---")
                log(json.dumps(all_done_issues[0], indent=2, ensure_ascii=False))
                log("--- END DEBUG ---")
            
            if all_done_issues:
                year_df = process_issues_to_dataframe(all_done_issues, merged_keys_set)
                log(f"Processed {len(year_df)} total issues for {year}.")
                
                final_columns = [
                    'key', 'fields.assignee.emailAddress', 'fields.summary',
                    'fields.parent.fields.summary', 'fields.customfield_11375',
                    'fields.created', 'fields.updated', 'doneDate', 'inProgressDate', 'component', 'is_merged', 'fields.issuetype.name'
                ]
                
                for col in final_columns:
                    if col not in year_df.columns:
                        year_df[col] = pd.NA
                final_df = year_df[final_columns]
            else:
                log(f"No issues found for {year}.")
                final_df = pd.DataFrame(columns=[
                    'key', 'fields.assignee.emailAddress', 'fields.summary',
                    'fields.parent.fields.summary', 'fields.customfield_11375',
                    'fields.created', 'fields.updated', 'doneDate', 'inProgressDate', 'component', 'is_merged', 'fields.issuetype.name'
                ])
            
            newly_processed_sheets[str(year)] = final_df

        # --- 3. Write all sheets (preserved and new) to the file ---
        log(f"Writing all data to {EXCEL_FILE_PATH}...")
        with pd.ExcelWriter(EXCEL_FILE_PATH, engine='openpyxl') as writer:
            all_sheets = {**existing_sheets_to_keep, **newly_processed_sheets}
            
            for sheet_name in sorted(all_sheets.keys()):
                log(f"  - Writing sheet: {sheet_name}")
                all_sheets[sheet_name].to_excel(writer, sheet_name=sheet_name, index=False)

    log(f'\nDataFrame saved to {EXCEL_FILE_PATH}')

def send_report_by_email(file_path):
    """Attaches the generated report to an email and sends it."""
    SMTP_HOST = "smtp.office365.com"
    SMTP_PORT = 587
    RECIPIENT_EMAIL = "support_v2x@autocrypt.io"

    SENDER_EMAIL = os.getenv("SMTP_USER")
    SENDER_PASSWORD = os.getenv("SMTP_PASSWORD")

    if not SENDER_EMAIL or not SENDER_PASSWORD:
        raise ValueError("SMTP_USER and SMTP_PASSWORD environment variables must be set to send email.")

    log(f"Preparing to send report to {RECIPIENT_EMAIL}...")

    # Create Email Message
    msg = EmailMessage()
    msg["Subject"] = f"Jira Daily Report: {datetime.now(timezone(timedelta(hours=9))).strftime('%Y-%m-%d')}"
    msg["From"] = SENDER_EMAIL
    msg["To"] = RECIPIENT_EMAIL
    msg.set_content("Jira issue report attached.")

    # Attach the file
    try:
        with open(file_path, 'rb') as f:
            file_data = f.read()
            file_name = os.path.basename(file_path)
            msg.add_attachment(file_data, maintype='application', subtype='vnd.openxmlformats-officedocument.spreadsheetml.sheet', filename=file_name)
    except FileNotFoundError:
        log(f"Error: Report file not found at {file_path}. Cannot send email.")
        raise

    # Send Email via SMTP
    with smtplib.SMTP(SMTP_HOST, SMTP_PORT) as server:
        server.starttls()
        server.login(SENDER_EMAIL, SENDER_PASSWORD)
        server.send_message(msg)
    
    log("Email with report sent successfully!")


if __name__ == "__main__":
    # Add email library imports to the top-level scope
    from email.message import EmailMessage
    import smtplib

    backup_path = EXCEL_FILE_PATH + '.bak'

    # --- Transaction Start: Create backup ---
    if os.path.exists(EXCEL_FILE_PATH):
        log(f"Backing up {EXCEL_FILE_PATH} to {backup_path}")
        os.rename(EXCEL_FILE_PATH, backup_path)

    try:
        # Run main data processing logic
        main()

        # After success, send the report via email
        log("Sending generated report via email...")
        send_report_by_email(EXCEL_FILE_PATH)

        # --- Commit: On success, remove backup ---
        if os.path.exists(backup_path):
            log("Process successful. Removing backup file.")
            os.remove(backup_path)

        if TEAMS_NOTIFY_ON_SUCCESS:
            success_title = "Jira Issue Getter 성공"
            success_message = f"스크립트가 성공적으로 완료되었습니다.\n\n**파일명:** `{os.path.basename(EXCEL_FILE_PATH)}`"
            send_teams_notification(success_title, success_message, color="00BFFF")

    except Exception as e:
        # --- Rollback: On failure, restore backup ---
        log(f"!!! An error occurred. Rolling back changes. !!!")
        if os.path.exists(backup_path):
            if os.path.exists(EXCEL_FILE_PATH):
                os.remove(EXCEL_FILE_PATH)
            log(f"Restoring backup from {backup_path}...")
            os.rename(backup_path, EXCEL_FILE_PATH)
        
        error_details = traceback.format_exc()
        error_title = "Jira Issue Getter 실패"
        error_message = f"스크립트 실행 중 오류가 발생했습니다.\n\n**에러 내용:**\n```\n{error_details}```"
        send_teams_notification(error_title, error_message, color="FF0000")

        log(f"Script failed and notification sent. Error: {e}")
        raise
