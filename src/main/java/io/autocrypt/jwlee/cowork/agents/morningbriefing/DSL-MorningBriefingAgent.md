# DSL-MorningBriefingAgent

> [IMPORTANT] When implementing this agent, you MUST refer to the following guides for ground truth on coding patterns, workarounds, and framework usage:
> - `guides/DSL_GUIDE.md`: Standard DSL rules and common pitfalls.
> - `guides/few-shots/embabel-few-shot.md`: Verified Embabel coding patterns and DTO structures.
> - `guides/few-shots/spring-shell-few-shot.md`: Verified CLI command implementation patterns.
> - `README.md`: Project-specific directory standards and module architecture.

## 1. Metadata

```yaml
agent:
  name: MorningBriefingAgent
  description: "Analyzes yesterday's Jira status changes and Confluence meeting notes to generate a daily morning summary and propose personal tasks for today."
  timezone: "Asia/Seoul" # REQUIRED
  workspace: "morning-briefing"
```

## 2. Dependencies (Constructor Injection)

The following Spring beans must be injected via the constructor. Do **not** pass these into `@Action` method signatures.

- `JiraExcelService` (REQUIRED: For extracting Jira state changes)
- `ConfluenceService` (REQUIRED: For extracting Confluence meeting logs)
- `PromptProvider` (REQUIRED: For resolving `.jinja` prompt templates)
- `CoreWorkspaceProvider` (REQUIRED: For saving the final generated report)
- `CoworkLogger` (REQUIRED: For agent step logging)

## 3. Domain Objects (DTOs)

Defined as `public record` within the agent class. Embabel will use these for DICE (Domain Integrated Context Engineering) and JSON schema generation.

```yaml
JiraChange:
  issueKey: String # e.g., "PROJ-123"
  summary: String # Issue title
  oldStatus: String
  newStatus: String
  assignee: String

MeetingNote:
  title: String
  rawContent: String
  author: String

MeetingSummary:
  title: String
  keyDecisions: List<String>
  actionItems: List<String>

TaskSuggestion:
  assignee: String
  taskDescription: String
  sourceContext: String # Explanation of why this task is suggested (e.g., based on PROJ-123 or a specific meeting)

MorningBriefingReport:
  reportDate: String # Format: YYYY-MM-DD
  jiraChanges: List<JiraChange>
  meetingSummaries: List<MeetingSummary>
  todayTasks: List<TaskSuggestion>
```

## 4. Workflow States (`@State`)

States to hold the blackboard context as the agent transitions between data gathering, summarizing, and task generation.

```yaml
State: BriefingPreparationState {
  targetDate: String # Yesterday's date in Asia/Seoul
  jiraChanges: List<JiraChange>
  rawMeetingNotes: List<MeetingNote>
}

State: SynthesisState {
  targetDate: String
  jiraChanges: List<JiraChange>
  meetingSummaries: List<MeetingSummary>
}
```

## 5. Actions (`@Action`)

### Action 1: `gatherYesterdayData`
- **Goal**: Fetch raw data from Jira and Confluence using Hybrid Java logic.
- **Input**: None (Triggered at start)
- **Output**: `BriefingPreparationState`
- **LLM Configuration**: None (Pure Java logic)
- **Logic**: 
  1. Calculate yesterday's date using `LocalDate.now(ZoneId.of("Asia/Seoul")).minusDays(1)`.
  2. Call `JiraExcelService` to fetch yesterday's issue changes and map them to `JiraChange` DTOs.
  3. Call `ConfluenceService` to fetch yesterday's meeting logs and map them to `MeetingNote` DTOs.
  4. Return `BriefingPreparationState`.

### Action 2: `summarizeMeetings`
- **Goal**: Condense raw meeting notes into structured summaries.
- **Input**: `BriefingPreparationState`
- **Output**: `SynthesisState`
- **LLM Configuration**:
  - `role`: `normal` (e.g., gemini-2.5-flash for fast processing)
  - `temperature`: 0.1 (deterministic)
  - `template`: `agents/morningbriefing/summarize_meetings.jinja`
- **Logic**: 
  - If `rawMeetingNotes` is empty, map directly to `SynthesisState` with an empty list.
  - Otherwise, use `.creating(MeetingSummaryList.class).fromPrompt(...)` to summarize the `rawMeetingNotes`.

### Action 3: `generateBriefingAndTasks`
- **Goal**: `@AchievesGoal(description = "Generate final morning briefing report with personal task suggestions based on Jira and Confluence context.")`
- **Input**: `SynthesisState`
- **Output**: `MorningBriefingReport`
- **LLM Configuration**:
  - `role`: `performant` (e.g., gemini-2.5-pro for complex reasoning and task allocation)
  - `temperature`: 0.3
  - `template`: `agents/morningbriefing/generate_tasks.jinja`
- **Logic**:
  - Pass the `SynthesisState` to the LLM.
  - The LLM analyzes the `jiraChanges` and `meetingSummaries` to generate `TaskSuggestion` items per assignee.
  - Return the fully populated `MorningBriefingReport`.
  - Save the resulting report to the workspace using `CoreWorkspaceProvider.getSubPath(agentName, workspaceId, SubCategory.ARTIFACTS)`.

## 6. Mandatory Implementation Guidelines

1. **Strict Timezone Enforcement**: 
   - You MUST use `ZoneId.of("Asia/Seoul")` for all date calculations (e.g., determining "yesterday" and "today").
2. **Hybrid Logic & No Beans in Actions**: 
   - Do NOT pass `JiraExcelService` or `ConfluenceService` into the `@Action` method signatures. Use them from the class scope (injected via constructor) inside the `gatherYesterdayData` action.
3. **Type-Driven Generation**: 
   - Use Embabel's `.creating(DTO.class).fromPrompt(...)` for LLM calls. Do not manually instruct the LLM to output JSON in the Jinja template.
4. **Gemini 2.5 Pitfall Protection**:
   - If you utilize any tools (`@LlmTool`) internally during an LLM call with a Gemini 2.5 model, you MUST chain `.withoutThinking()` in the `LlmOptions` to prevent `missing thought_signature` errors.
5. **CLI Command**: 
   - Implement `MorningBriefingCommand` extending `BaseAgentCommand`. 
   - It MUST support `-p` (`--show-prompts`) and `-r` (`--show-responses`) flags.
