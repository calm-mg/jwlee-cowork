# Embabel Agent DSL Guide (v1.0)

This document defines the Domain-Specific Language (DSL) used to specify Embabel agents. This DSL is designed to be human-readable, AI-implementable, and resilient to common coding pitfalls.
## 1. DSL Structure Overview

An Agent DSL file (`DSL-{agent-name}.md`) must follow this structure:

0.  **Header**: (REQUIRED) A disclaimer and reference links to standard guides.
1.  **Metadata**: Basic agent info and global settings.
2.  **Dependencies**: Required tools, services, and other agents.
3.  **Domain Objects (DTOs)**: Data structures for LLM input/output.
4.  **Workflow States**: `@State` definitions for multi-stage processes.
5.  **Actions**: `@Action` definitions including logic and LLM configurations.
6.  **Implementation Guidelines**: Specific instructions for the coding agent.

---

## 2. DSL Syntax Specification

### 2.1 Metadata
```yaml
agent:
  name: MyAwesomeAgent
  description: "Does something amazing"
  timezone: "Asia/Seoul" # REQUIRED
  language: "Korean" # REQUIRED: Use Korean for all user-facing outputs and reports.
  workspace: "my-workspace"
```

### 2.2 Dependencies (Constructor Injection)
List all Spring beans and tools that must be injected via the constructor.
- `CoreFileTools`, `GitTools`, `ObsidianTools`, `GoogleServiceTools`
- `PromptProvider` (REQUIRED for Jinja)
- `LocalRagTools` (If using RAG)
- `CoreWorkspaceProvider` (If using workspace paths)

### 2.3 Domain Objects (DTOs)
Defined as `public record` in the agent class.
- **Syntax**: `[Name]: { [field]: [type] # [validation/desc] }`
- **Example**:
  ```yaml
  ReportRequest:
    title: String # @NotBlank
    priority: int # @Min(1) @Max(5)
  ```

### 2.4 Workflow States (`@State`)
States define the blackboard context at specific points in the plan.
- **Syntax**: `State: [Name] [implements Interface]? { [fields] }`
- **Constraint**: States **do not** inherit fields from parents. All required context must be explicitly passed.
- **Example**:
  ```yaml
  State: ReviewState implements Stage:
    originalRequest: ReportRequest
    draftContent: String
    iterationCount: int
  ```
### 2.5 Actions (`@Action`)
The core logic blocks.
- **Goal**: `@AchievesGoal(description = "...")` - REQUIRED for at least one action.
  - **CRITICAL**: The return type MUST be a **unique Record type** (DTO) defined within the agent.
  - **NEVER return generic types** like `String`, `Boolean`, or `List<String>` in `@AchievesGoal`. This causes planning ambiguity (Stuck state) because the planner cannot distinguish between different agents' results.
  - Even if the output is just a Markdown string, wrap it in a unique Record (e.g., `record MorningBriefingReport(String content) {}`).
- **Input**: The DTO or State required to trigger this action.
...

- **Output**: The DTO or State returned. Use `WaitFor.formSubmission([DTO].class)` for HITL.
- **LLM Configuration**:
  - `role`: simple | normal | performant
  - `temperature`: 0.1 (deterministic) | 0.7 (creative)
  - `template`: Path to `.jinja` or `.md` (e.g., `agents/my/prompt.jinja`)
- **Action Pitfall Protection**:
  - **NO External Beans**: Only `Ai`, `ActionContext`, and Blackboard objects (DTOs/States) allowed in method signature.
  - **No Manual JSON Prompts**: Embabel handles DTO schema; do not add "Return as JSON" to prompts.
  - **Hybrid Logic**: Complex data fetching (API/DB) should be done in Java logic before/after LLM calls. The LLM should focus on analysis and transformation.
  - **Multi-Stage Pattern**: For complex tasks, split actions into a "Research Phase" (extracting facts via RAG/Services) and a "Generation Phase" (synthesizing the final report/DSL). Use `String` as a medium for large context between stages.


---

## 3. Mandatory Implementation Guidelines (The "Pitfall List")

When implementing an agent from this DSL, the coding agent MUST adhere to these rules:

### 3.1 Architecture & Injection
- **Action Arguments**: `Action` methods MUST NOT take Spring-managed beans. Inject beans into the constructor and use them inside the method.
- **New Tool Strategy**: 
  - If a required feature doesn't exist in current services, **CREATE** a new `@Component` (e.g., `MyAgentTools`) or a Service.
  - For complex logic that the LLM needs to call directly, use the **DICE pattern**: Define `@LlmTool` methods inside your DTO records or in a dedicated tool class and inject it via `ai.withToolObject()`.
- **Blackboard First**: Use `ActionContext` or parameters to access data already on the blackboard.

### 3.2 AI & Prompting
- **Type-Driven Generation**: Use `.creating(DTO.class).fromPrompt(...)`. Embabel automatically adds JSON formatting instructions and performs validation retries.
- **LlmOptions**: 
  - Use `LlmOptions.withLlmForRole("role")` for fine-tuning.
  - **CRITICAL**: For Gemini 2.5+ models, you MUST call `.withoutThinking()` when using tools (RAG, @LlmTool) to avoid 'missing thought_signature' errors.
  - **Role Separation**: Prefer using a lighter model (e.g., `gemini-2.5-flash`) for information retrieval/RAG and a performant model for final creative synthesis.

### 3.3 State & Looping
- **Explicit Context**: When creating a new `@State` object, explicitly pass all necessary variables from the current state/context.
- **Looping**: Use `clearBlackboard = true` on actions that return to a previous state type to avoid stale data.

### 3.4 HITL & Notifications
- **HITL**: Use `WaitFor.formSubmission(ApprovalDecision.class)` to pause for user input.
- **Notifications**: 
  - For long-running tasks, publish `NotificationEvent` via `ApplicationContextHolder.getPublisher().publishEvent()`.
  - **UX Tip**: For report-generating agents, consider sending the **full Markdown content** in the `NotificationEvent` message so the user can see it immediately without opening files.

### 3.5 Storage & Paths
- **Lucene RAG**: 
  - Prefer `localRagTools.getOrOpenMemoryInstance(name)` for transient data.
  - Use `localRagTools.getOrOpenInstance(name, path)` for persistent data.
- **Workspace**: 
  - Use `CoreWorkspaceProvider.getSubPath(agentName, workspaceId, SubCategory.ARTIFACTS)` for files.
  - Slug generation for `workspaceId` should use `workspaceProvider.toSlug(input)`.
- **Jira/Docs**: Adhere to existing directory path conventions in `output/`.

### 3.6 Standard Utils
- **Timezone**: Always use `ZoneId.of("Asia/Seoul")` for `LocalDate` or `LocalDateTime`.
- **Logging**: Use `CoworkLogger` injected into the constructor.
- **CLI Command**: 
  - Implementation MUST include a Spring Shell command class extending `BaseAgentCommand`.
  - MUST support `-p` (`--show-prompts`) and `-r` (`--show-responses`) flags using `invokeAgent` and `getOptions`.

---

## 4. How to use this DSL Guide

### For AgentGenerationPlanAgent:
1.  Read the target project requirements and user instructions.
2.  **Trust Hierarchy**: 
    - Priority 1 (VERIFIED): Existing `*Agent.java` and `few-shot` docs. These reflect the actual runtime environment and workarounds.
    - Priority 2 (REFERENCE): Full framework guides. Use these for general concepts only.
3.  Synthesize a complete `DSL-{agent-name}.md` file following Section 2 and 3.
4.  **Storage**: Save the generated file to `guides/DSLs/DSL-{agent-name}.md`.
5.  Ensure all identified "Pitfalls" are explicitly addressed in the "Action" or "Implementation Guidelines" sections of the generated file.

### For Coding Agents (Gemini CLI / Claude Code):
1.  **Relocation (IMPORTANT)**: Before starting implementation, **MOVE** the `DSL-{agent-name}.md` from `guides/DSLs/` to the agent's specific vertical slice package directory (e.g., `src/main/java/.../agents/{agent-name}/`).
2.  Read the relocated DSL and implement the Java code, DTOs, and Prompts strictly following its instructions.
3.  **Documentation (Final Step)**: After successful implementation and validation, **CREATE** a `USE.md` file in the same directory. This file must contain:
    - Detailed CLI command usage (with parameter examples).
    - Description of expected outputs and where to find them.
    - Any prerequisites (e.g., Python environment, specific API tokens).
3.  If any part of the DSL is ambiguous, refer back to `guides/embabel-0.3.4-full-guide.md` and `guides/embabel-few-shot.md` for ground truth.
es/embabel-few-shot.md` for ground truth.
4.  Implement the Java code, DTOs, and Prompts strictly following the DSL and the Pitfall List.
