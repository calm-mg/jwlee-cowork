# DSL-AdvancedSlidesAgent.md

> [IMPORTANT] When implementing this agent, you MUST:
> 1. **Relocate** this file to the agent's specific vertical slice directory (e.g., `src/main/java/io/autocrypt/jwlee/cowork/advancedslidesagent/`).
> 2. Refer to the following guides for ground truth on coding patterns, workarounds, and framework usage:
> - `guides/DSL_GUIDE.md`: Standard DSL rules and common pitfalls.
> - `guides/few-shots/embabel-few-shot.md`: Verified Embabel coding patterns and DTO structures.
> - `guides/few-shots/spring-shell-few-shot.md`: Verified CLI command implementation patterns.
> - `README.md`: Project-specific directory standards and module architecture.

## 1. Metadata
```yaml
agent:
  name: AdvancedSlidesAgent
  description: "Acts like NotebookLM to analyze source material and generate Obsidian Advanced Slides markdown based on user instructions."
  timezone: "Asia/Seoul"
  language: "Korean"
  workspace: "advanced-slides"
```

## 2. Dependencies (Constructor Injection)
- `CoreWorkspaceProvider` (For resolving workspace and export paths)
- `CoreFileTools` (For reading few-shot guidelines and writing the final markdown)
- `PromptProvider` (For managing Jinja templates)
- `CoworkLogger` (For standard logging)

## 3. Domain Objects (DTOs)
```yaml
SlideGenerationRequest:
  workspaceId: String # Workspace identifier for file saving
  sourceMaterial: String # The raw source text to be used as context
  instructions: String # User's specific instructions for slide generation

SlideStructurePlan:
  slideCount: int # Estimated number of slides
  outline: String # Structured outline of the slides (slide by slide)

SlideMarkdownOutput:
  markdownContent: String # The final generated Advanced Slides markdown
  savedFilePath: String # The absolute path where the markdown file was saved
```

## 4. Workflow States (`@State`)
```yaml
State: SlideGenerationState implements Stage:
  request: SlideGenerationRequest
  plan: SlideStructurePlan
```

## 5. Actions (`@Action`)

### 5.1 `analyzeAndStructure`
- **Description**: Analyzes the raw source material and user instructions to generate a logical slide-by-slide structure.
- **Input**: `SlideGenerationRequest`
- **Output**: `SlideGenerationState`
- **LLM Configuration**:
  - `role`: normal (e.g., gemini-2.5-flash)
  - `temperature`: 0.3
  - `template`: `agents/advancedslides/analyze-structure.jinja`
- **Prompt Instructions**:
  - Provide the `sourceMaterial` and `instructions`.
  - Ask the LLM to extract key points and map them to a logical slide progression.
  - Return a `SlideStructurePlan` DTO.
- **Logic**:
  - Create and return a new `SlideGenerationState` combining the original request and the generated plan.

### 5.2 `generateMarkdown`
- **Goal**: `@AchievesGoal(description = "Generates final Obsidian Advanced Slides markdown and saves it to the export directory")`
- **Input**: `SlideGenerationState`
- **Output**: `SlideMarkdownOutput` (CRITICAL: Must be a unique record type)
- **LLM Configuration**:
  - `role`: performant (e.g., gemini-2.5-pro)
  - `temperature`: 0.5
  - `template`: `agents/advancedslides/generate-markdown.jinja`
- **Prompt Instructions**:
  - Inject `request.sourceMaterial`, `request.instructions`, and `plan.outline`.
  - **CRITICAL**: Inject the full content of `guides/few-shots/adv-slides-few-shot.md` into the prompt as a variable (e.g., `{{ slideGuidelines }}`). Instruct the LLM to STRICTLY adhere to these layout and formatting rules.
  - Do not use RAG for the guidelines; embed them directly in the prompt text.
- **Logic**:
  1. Before calling the LLM, use `CoreFileTools` (or standard Java NIO) to read the content of `guides/few-shots/adv-slides-few-shot.md` and pass it to the prompt context.
  2. Call the LLM to generate the markdown string (wrapped in a temporary Record or extracted directly).
  3. Use `CoreWorkspaceProvider.getSubPath("AdvancedSlidesAgent", state.request().workspaceId(), SubCategory.ARTIFACTS)` to get the base artifact directory.
  4. Append `/export` to this path to create the export subdirectory. Ensure the directory exists.
  5. Generate a filename (e.g., `slides_{timestamp}.md` or a slugified name).
  6. Use `CoreFileTools` to write the generated markdown string to this path.
  7. Return a `SlideMarkdownOutput` containing the raw markdown and the absolute saved file path.

## 6. Implementation Guidelines

### 6.1 Architecture & Injection
- **Package**: Create the package `io.autocrypt.jwlee.cowork.advancedslidesagent`. All agent-specific classes (Agent, DTOs, States) must reside here.
- **Action Pitfall Protection**: Do NOT inject `CoreWorkspaceProvider` or `CoreFileTools` directly into the `@Action` method signatures. Inject them into the agent's constructor and reference them within the action methods.

### 6.2 Prompting & Context
- **Guideline Injection**: The requirement states that `adv-slides-few-shot.md` must be strictly followed and included in the prompt body (not via RAG). You must read this file from the project root (`guides/few-shots/adv-slides-few-shot.md`) during the agent's execution or initialization and pass its content into the Jinja template using `ActionContext` or prompt variables.
- **Type-Driven Generation**: Use Embabel's `.creating(SlideStructurePlan.class)` for the first action. For the final generation, if you need just the raw markdown string from the LLM, you can use `.creating(SlideMarkdownRaw.class)` (a temporary internal DTO) and then map it to the final `SlideMarkdownOutput` after the file saving logic.

### 6.3 Storage & Paths
- **Export Directory**: The requirement explicitly asks to save the result in an `export` subdirectory based on the `CoreWorkspaceProvider` standard.
  ```java
  Path artifactPath = workspaceProvider.getSubPath(agentName, workspaceId, SubCategory.ARTIFACTS);
  Path exportDir = artifactPath.resolve("export");
  // ensure exportDir exists, then save file
  ```

### 6.4 CLI Command
- Implement `AdvancedSlidesCommand` extending `BaseAgentCommand`.
- The command should accept the `workspaceId`, `sourceMaterial` (or a file path to read it from), and `instructions`.
- Must support `-p` (`--show-prompts`) and `-r` (`--show-responses`).
- **Output Handling**: The command MUST print the `markdownContent` from the returned `SlideMarkdownOutput` to the console, and also print a success message indicating the `savedFilePath`.