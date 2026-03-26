# Chapter 0: Setup & Configuration

Embabel is configured via Spring Boot starters and `application.yml`. This chapter covers the essential Maven dependencies and property configurations required to boot an Embabel environment.

## 0.1 Maven Dependencies (pom.xml)
Embabel modules are hosted on a dedicated repository. You need the core starter, a model provider (e.g., Gemini or Ollama), and RAG modules if using knowledge bases.

```xml
<dependencies>
    <!-- Core: Agent and GOAP engine -->
    <dependency>
        <groupId>com.embabel.agent</groupId>
        <artifactId>embabel-agent-starter</artifactId>
        <version>${embabel-agent.version}</version>
    </dependency>

    <!-- RAG Stack (Optional) -->
    <dependency>
        <groupId>com.embabel.agent</groupId>
        <artifactId>embabel-agent-rag-lucene</artifactId> <!-- Local index -->
        <version>${embabel-agent.version}</version>
    </dependency>

    <!-- Model Provider (Pick at least one) -->
    <dependency>
        <groupId>com.embabel.agent</groupId>
        <artifactId>embabel-agent-starter-google-genai</artifactId> <!-- Gemini -->
        <version>${embabel-agent.version}</version>
    </dependency>
</dependencies>

<repositories>
    <repository>
        <id>embabel-releases</id>
        <url>https://repo.embabel.com/artifactory/libs-release</url>
    </repository>
</repositories>
```

## 0.2 Model & Platform Configuration (application.yml)
Define global LLM preferences, embedding models, and platform resiliency (Retries, Timeouts).

```yaml
embabel:
  models:
    default-llm: gemini-2.5-flash        # The default model to use
    default-embedding-model: gemini-embedding-001 # For RAG embedding
    llms:
      cheapest: gemini-2.5-flash-lite    # Used when cost optimization is needed
      normal: gemini-2.5-flash
  agent:
    platform:
      llm-operations:
        timeout-seconds: 300            # Wait time for LLM response
        data-binding:
          max-attempts: 15              # Retries for failed JSON parsing
      models:
        googlegenai:
          api-key: ${GEMINI_API_KEY}    # Inject via environment variable
```

## 0.3 Role-Based Identities (Persona)
Embabel allows you to define specialized personas (Identities) in `application.yml`. These personas are mapped to `RoleGoalBackstory` beans, which the LLM uses to adopt specific behaviors.

### 0.3.1 Define in application.yml
Structure your identities under `embabel.identities`.

```yaml
embabel:
  identities:
    email:
      writer:
        role: "Corporate Text Processor"
        goal: "Sanitize toxic input into professional email bodies"
        backstory: "Focus only on the message content. Do not include signatures."
      reviewer:
        role: "Compliance Auditor"
        goal: "Verify professional tone and signature presence"
```

### 0.3.2 Map to Spring Beans (Configuration)
Use `@ConfigurationProperties` to bind YAML to records and expose them as `RoleGoalBackstory` beans. **The bean name must match the role name used in code.**

```java
import com.embabel.agent.prompt.persona.RoleGoalBackstory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({EmailConfig.WriterProps.class, EmailConfig.ReviewerProps.class})
public class EmailConfig {

    @ConfigurationProperties("embabel.identities.email.writer")
    public record WriterProps(String role, String goal, String backstory) {}

    @ConfigurationProperties("embabel.identities.email.reviewer")
    public record ReviewerProps(String role, String goal, String backstory) {}

    @Bean
    public RoleGoalBackstory emailWriterPersona(WriterProps props) {
        return new RoleGoalBackstory(props.role(), props.goal(), props.backstory());
    }

    @Bean
    public RoleGoalBackstory emailReviewerPersona(ReviewerProps props) {
        return new RoleGoalBackstory(props.role(), props.goal(), props.backstory());
    }
}
```

### 0.3.3 Usage in Agent Actions
Invoke these personas using `.withLlmByRole("roleName")`. Embabel will automatically find the matching `RoleGoalBackstory` bean.

```java
import com.embabel.agent.api.annotation.Action;
import com.embabel.agent.api.annotation.Agent;
import com.embabel.agent.api.common.OperationContext;
import com.embabel.agent.domain.io.UserInput;

@Agent(description = "Processes corporate emails")
public class EmailAgent {

    @Action
    public String writeEmail(UserInput input, OperationContext ctx) {
        return ctx.ai()
            .withLlmByRole("emailWriterPersona") // Matches the @Bean name
            .generateText("Draft an email about: " + input.getContent());
    }
}
```

## 0.4 RAG & Storage Configuration
Specify the location for the knowledge base (input) and the vector index (storage).

```yaml
embabel:
  rag:
    lucene:
      dir: target/lucene-index          # Path for local vector index
    import:
      dir: knowledge                   # Directory for auto-ingestion
```

# Chapter 1: Foundation & Type-Driven Flow (GOAP)

Embabel's core magic is **Goal-Oriented Action Planning (GOAP)**. Unlike sequential workflows, you define "capabilities" (Actions) and a "target" (Goal), and the framework automatically calculates the path using input/output types.

## 1.1 The Basic Agent Pattern
An agent is a class annotated with `@Agent`. It contains methods annotated with `@Action`.

### Standard GOAP Agent Implementation
```java
@Agent(description = "Writes a creative story and analyzes its sentiment")
public class ContentAgent {

    // Action 1: Satisfied by UserInput. Returns a Story object.
    @Action
    public Story writeStory(UserInput input, OperationContext ctx) {
        return ctx.ai().withDefaultLlm()
                .withTemperature(0.8) // High for creativity
                .createObject("Write a short story about: " + input.getContent(), Story.class);
    }

    // Action 2: Satisfied once a Story object exists on the Blackboard.
    @AchievesGoal(description = "The story is analyzed and ready for delivery")
    @Action
    public Analysis analyzeStory(Story story, OperationContext ctx) {
        return ctx.ai().withLlmByRole("analyst") // Specific model selection
                .withTemperature(0.1) // Low for deterministic analysis
                .createObject("Analyze the tone and sentiment of: " + story.text(), Analysis.class);
    }
}
```

## 1.2 Key Mechanics for Few-Shot Learning
- **Automatic Chaining**: If Action B requires `Analysis` as an argument and Action A returns `Analysis`, Action B will only run after Action A completes.
- **The Blackboard**: A shared memory for the process
- **UserInput**: The starting point. It is automatically placed on the Blackboard when an agent is invoked via a user message
- **AchievesGoal**: Every agent needs at least one action marked with `@AchievesGoal`. This tells the planner what the "winning condition" is

## 1.3 OperationContext & Ai API
The `Ai` interface (via `ctx.ai()`) is the primary way to call LLMs. It provides high-level methods for structured output and text generation, with built-in resiliency.

### 1.3.1 Core PromptRunner Methods
These methods govern how the LLM produces output and how the framework handles failures.

- **`createObject(String prompt, Class<T> clazz)`**: 
  - **Behavior**: Attempts to create a structured POJO from the prompt
  - **Error Handling**: If the LLM produces invalid JSON or fails to meet the schema, it throws an exception. This **triggers a retry** (configured in `application.yml`)
  - **Re-planning**: If retries are exhausted, it triggers a **system-wide re-planning** to find an alternative path to the goal
- **`createObjectIfPossible(String prompt, Class<T> clazz)`**: 
  - **Behavior**: Similar to `createObject`, but returns `null` instead of throwing an exception on failure
  - **Re-planning**: Returning `null` signals the planner that this specific path is blocked, often leading to immediate **re-planning**
- **`generateText(String prompt)`**: 
  - **Behavior**: Returns a raw `String` response from the LLM. Best for creative writing or simple chat

### 1.3.2 Hyperparameter Tuning & LlmOptions
You can fine-tune the LLM's behavior (like creativity vs. determinism) using the `withLlm(LlmOptions)` method. This allows you to set the `temperature` and select specific model criteria.

- **`withTemperature(double value)`**: 
  - **High (0.7 - 0.9)**: Recommended for creative writing, brainstorming, and storytelling
  - **Low (0.0 - 0.2)**: Recommended for structured data extraction, factual analysis, and deterministic logic
- **LlmOptions Factory Methods**:
  - `LlmOptions.withAutoLlm()`: Automatically selects the best available model based on platform rankings
  - `LlmOptions.withDefaultLlm()`: Uses the `default-llm` specified in `application.yml`
  - `LlmOptions.fromCriteria(ModelSelectionCriteria criteria)`: Selects a model based on specific requirements (e.g., small, large, vision-capable)

### 1.3.3 API Usage Example
```java
import com.embabel.agent.api.common.OperationContext;
import com.embabel.agent.api.annotation.Action;
import com.embabel.agent.api.annotation.EmbabelComponent;
import com.embabel.agent.domain.io.UserInput;
import com.embabel.agent.api.common.Ai;
import com.embabel.common.ai.model.LlmOptions

@EmbabelComponent
public class ContentActions {

    @Action
    public Story draftStory(UserInput input, Ai ai) {
        // High temperature for creative drafting
        return ai.withLlm(LlmOptions.withAutoLlm().withTemperature(0.7))
                .createObject("Craft a short story about: " + input.getContent(), Story.class);
    }

    @Action
    public Analysis analyzeFactual(Story story, Ai ai) {
        // Low temperature for deterministic factual analysis
        return ai.withLlm(LlmOptions.withDefaultLlm().withTemperature(0.1))
                .createObject("Analyze the facts in: " + story.text(), Analysis.class);
    }
}
```

## 1.4 Validation & Reliability
Embabel supports **JSR-380 bean validation** (Jakarta Validation) annotations on domain objects. This ensures that structured output from the LLM adheres to strict business rules before it is placed on the Blackboard.

### 1.4.1 Key Mechanics
- **Automatic Validation**: When creating objects via `createObject` or `createObjectIfPossible`, validation is automatically performed after deserialization.
- **Self-Correcting Retries**: If validation fails, Embabel **transparently retries** the LLM call. It includes the specific validation error messages in the retry prompt to help the LLM correct its own output.
- **Fail-Fast to Re-plan**: If validation fails a second time, an `InvalidLlmReturnTypeException` is thrown. This triggers **system-wide re-planning** if not caught, allowing the agent to try a different strategy to reach the goal.
- **Custom Handling**: You can catch `InvalidLlmReturnTypeException` within your `@Action` method to perform manual recovery or fallback logic.

### 1.4.2 Annotated Domain Object Example
```java
import jakarta.validation.constraints.*;

public class User {
    @NotBlank(message = "Name cannot be empty")
    private String name;

    @AssertTrue(message = "Working must be true")
    private boolean working;

    @Size(min = 10, max = 200, message = "About Me must be between 10 and 200 characters")
    private String aboutMe;

    @Min(value = 18, message = "Age should not be less than 18")
    @Max(value = 150, message = "Age should not be greater than 150")
    private int age;

    @Email(message = "Email should be valid")
    private String email;

    // Standard getters and setters
}
```

## 1.5 Jinja Prompt Templates
Embabel uses **Jinjava** (a Java implementation of Jinja2) to manage complex prompts. This allows you to separate prompt engineering from Java logic, making prompts easier to version, test, and reuse.

### 1.5.1 Core Concepts
- **Template Location**: Templates must be placed in `src/main/resources/prompts/` with a `.jinja` extension
- **Rendering**: Use `.rendering("templateName")` on the `Ai` interface to select a template (omit the folder and extension)
- **Bindings**: Pass dynamic data to the template using `Map<String, Object>` in methods like `respondWithSystemPrompt`

### 1.5.2 Usage Example
#### 1. Java Code
```java
@Action(trigger = UserMessage.class)
public void chat(Conversation conv, ActionContext ctx) {
    var response = ctx.ai()
            .rendering("support") // Loads src/main/resources/prompts/support.jinja
            .respondWithSystemPrompt(conv, Map.of(
                    "persona", "Technical Support",
                    "severity", "high"
            ));
    ctx.sendMessage(conv.addMessage(response));
}
```

#### 2. Jinja Template (`prompts/support.jinja`)
```jinja
You are a {{ persona }} agent.
Current incident severity: {{ severity }}.

{# Composition: Reuse common prompt fragments #}
{% include "elements/guardrails.jinja" %}

Answer the user's question based on the provided conversation history.
```

### 1.5.3 Key Benefits
- **Composition**: Use `{% include %}` to share common prompt elements (e.g., safety guardrails, output formatting) across different agents
- **Separation of Concerns**: Java developers focus on flow logic; prompt engineers focus on template tuning
- **Dynamic Logic**: Supports Jinja features like filters, loops (`{% for %}`), and conditionals (`{% if %}`) for complex context assembly

# Chapter 2: Domain Engineering & DICE (Domain-Integrated Context Engineering)

Embabel grounds LLM interactions in strongly-typed domain objects and services. This approach, **DICE**, ensures precision by giving LLMs "hands" (tools) to interact with your system.

## 2.1 Domain-Based vs. Service-Based Tools
Use the `@LlmTool` annotation from `com.embabel.agent.api.annotation` to mark methods that the LLM can discover and invoke.

### 2.1.1 Domain-Based Tools (Records/Entities)
Domain objects should encapsulate their own logic. This prevents the LLM from hallucinating business rules by letting it call verified methods instead.

```java
import com.embabel.agent.api.annotation.LlmTool;

/**
 * Represents a user profile. DICE: Encapsulates persona-specific logic.
 */
public record CatLover(String personality, String interest) {
    
    @LlmTool(description = "Generate a personalized greeting for this cat lover")
    public String getGreeting() {
        return String.format("Meow! Greetings to our %s friend who loves %s!", personality, interest);
    }
}
```

### 2.1.2 Service-Based Tools (Spring Components/APIs)
Spring-managed beans can also provide tools, typically for data fetching or external API calls (e.g., database queries, web services).

```java
import com.embabel.agent.api.annotation.LlmTool;
import org.springframework.stereotype.Component;
import java.util.List;

@Component
public class CatFactService {
    @LlmTool(description = "Fetch random interesting facts about cats from an external API.")
    public List<String> getCatFacts(int count) {
        // Implementation of external API call
        return List.of("Cats have five toes on their front paws.");
    }
}
```

## 2.2 Injecting Tools into Actions
To make these tools available to an LLM during an `@Action`, use the `withToolObject` method on the `Ai` interface. You can inject multiple tools (both domain and service objects) simultaneously.

### 2.2.1 Action with Multiple Tool Injection
```java
import com.embabel.agent.api.annotation.Action;
import com.embabel.agent.api.annotation.AchievesGoal;
import com.embabel.agent.api.common.Ai;

@Action
@AchievesGoal(description = "User is entertained with personalized cat facts")
public CatFactResponse entertainLover(CatLover lover, CatFactService service, Ai ai) {
    String prompt = """
            1. Call 'getGreeting' for a personalized opening.
            2. Use 'getCatFacts' to fetch 3 interesting facts.
            3. Explain these facts based on the lover's personality.
            """;

    return ai.withLlmByRole("normal")
            .withToolObject(service) // Service-based API tool
            .withToolObject(lover)   // Domain-based object tool
            .createObject(prompt, CatFactResponse.class);
}
```

## 2.3 Key Mechanics for DICE
- **Selective Exposure**: Only methods annotated with `@LlmTool` are visible to the LLM. Unannotated methods remain hidden for safety.
- **Type Safety**: The return types of `@LlmTool` methods are automatically handled by the framework.
- **Context Availability**: When an `@Action` method is called, its parameters (like `CustomerProfile`) are retrieved from the Blackboard. If the object exists, the planner can use it.
- **withToolGroup (MCP Integration)**: For common external tools (web search, browser, etc.), use predefined groups via the `withToolGroup` method.
  - **Prerequisite**: Using `CoreToolGroups.WEB` requires the **MCP Toolkit** (a Beta feature of **Docker Desktop**) to be installed and running on your system. This toolkit provides the Model Context Protocol (MCP) server that Embabel connects to for external capabilities.

### 2.3.1 Action with Tool Group Example
```java
import com.embabel.agent.api.annotation.Action;
import com.embabel.agent.api.common.Ai;
import com.embabel.agent.core.CoreToolGroups;

@Action
public String executeSearch(String query, Ai ai) {
    // This triggers an external search via the MCP Toolkit
    return ai.withDefaultLlm()
            .withToolGroup(CoreToolGroups.WEB) 
            .generateText("Wikipedia search about: " + query);
}
```

## 2.4 Prompt Engineering with Data Flow
DICE transforms context into an inspectable artifact. Use XML-style tags in prompts to provide structure:
```java
var prompt = """
    Customer name is %s.
    <profile>%s</profile>
    Given the above, generate a personalized greeting.
    """.formatted(customer.getName(), customer.toSummaryString());
```
**LLM Instruction**: Always prioritize using `@LlmTool` methods on domain objects for complex calculations or data fetching rather than asking the LLM to "figure it out" from raw data.

# Chapter 3: Advanced Planning & States

Embabel supports complex workflows through different planners and state-based management.

## 3.1 Utility AI (Event-Driven / Greedy)
Best for cases where no fixed goal exists, but you want to react to state changes with the "most valuable" action. Useful for triage or monitoring.

### 3.1.1 Utility AI Agent Sample
```java
@Agent(
    description = "Triage and process support tickets",
    planner = PlannerType.UTILITY
)
public class TicketTriageAgent {
    // Action 1: High net value (0.9 - 0.1 = 0.8)
    @Action(value = 0.9, cost = 0.1)
    public void urgentEscalation(GHIssue issue) {
        if (issue.getLabels().contains("critical")) { /* Escalate */ }
    }

    // Action 2: Low net value (0.4 - 0.1 = 0.3)
    @Action(value = 0.4, cost = 0.1)
    public void routineLabeling(GHIssue issue) {
        /* Add labels */
    }
}
```
**LLM Instruction**: In Utility mode, the planner selects the action with the highest **Net Value** (Value - Cost) at each step.

## 3.2 Supervisor Planner (LLM-Orchestrated)
The LLM acts as a manager, deciding which tools to call based on type schemas and currying.
- **Currying**: If an input (e.g., `MarketData`) is already on the blackboard, the tool appears "READY" with fewer parameters to fill.

### 3.2.1 Supervisor Agent Sample
```java
@Agent(planner = PlannerType.SUPERVISOR, description = "Market research analyst")
public class ResearchSupervisor {
    @Action(description = "Gather revenue data for a company")
    public MarketData gatherData(MarketDataRequest req, Ai ai) { ... }

    @AchievesGoal(description = "Compile the final research report")
    @Action
    public FinalReport compileReport(ReportRequest req, Ai ai) { ... }
}
```

## 3.3 @State Workflows & Looping
Embabel supports **States** within a GOAP plan to handle complex, multi-stage, or looping workflows. When an action returns a `@State`-annotated object, the framework hides previous state objects and focuses the planner on actions available within the new state.

### 3.3.1 Key State Mechanics
- **State Scoping**: Entering a new state hides previous state objects on the blackboard, pruning the context for the LLM. Non-state objects (user data, etc.) are preserved.
- **Inheritance**: `@State` is inherited. Annotating a parent interface or `sealed interface` automatically makes all implementations state types.
- **Staying in State**: Return `this` with `@Action(canRerun = true)` to remain in the current state without transitioning.
- **Looping with `clearBlackboard = true`**: To return to a previously visited state type (e.g., revise-and-review), you MUST use `clearBlackboard = true`. This resets the "has run" flags and clears the blackboard, allowing the loop to execute naturally.

### 3.3.2 Branching & Looping Sample (Sealed Interface)
This pattern uses a `sealed interface` to define explicit success/failure paths for the planner.

```java
import com.embabel.agent.api.annotation.Action;
import com.embabel.agent.api.annotation.State;
import com.embabel.agent.api.annotation.AchievesGoal;
import com.embabel.agent.api.common.Ai;

@State
public interface ReviewOutcome permits ReviewPassed, ReviewFailed {}

public record ReviewPassed(String content) implements ReviewOutcome {}
public record ReviewFailed(String feedback) implements ReviewOutcome {}

@State
public record DraftingState(String lastFeedback) {
    
    @Action
    public ReviewOutcome review(String draft, Ai ai) {
        // Logic to return ReviewPassed or ReviewFailed
        return new ReviewFailed("Tone is too aggressive");
    }

    @Action(canRerun = true, clearBlackboard = true)
    public DraftingState loopBack(ReviewFailed failed) {
        // Loops back to the same state type
        return new DraftingState(failed.feedback());
    }
}
```

## 3.4 Human-in-the-Loop (WaitFor)
Pause agent execution to wait for user input using `WaitFor.formSubmission()`. The process enters a `WAITING` state and resumes once the user submits the required record type.

### 3.4.1 HITL Integration Example
```java
import com.embabel.agent.api.annotation.Action;
import com.embabel.agent.core.hitl.WaitFor;

public record UserDecision(String input) {}

@Action(canRerun = true)
public UserDecision askUser(String searchResult) {
    System.out.println("Current Result: " + searchResult);
    // Pauses and waits for a form submission of UserDecision
    return WaitFor.formSubmission("Should we search more?", UserDecision.class);
}
```

**Operational Tip**: When using `clearBlackboard = true` for looping, ensure all necessary context (like original user queries or configuration) is passed as fields in the state record, as it will be the only data surviving the blackboard wipe.

## 3.5 Advanced HITL: The WAITING State & Direct Injection
When an agent reaches a point requiring human intervention via `WaitFor`, it enters a formal **WAITING** state. This is a first-class lifecycle state in Embabel managed by the blackboard.

### 3.5.1 The FormBindingRequest Mechanism
Calling `WaitFor.formSubmission()` triggers two specific actions on the blackboard:
- **Status Change**: The agent process status transitions to `WAITING`
- **Request Object**: A `FormBindingRequest` is added to the blackboard, describing the expected data type and the prompt for the user

### 3.5.2 Resuming a Process from a Controller
To resume a waiting agent from an external system (like a Spring Controller), you must inject the expected object directly into the process's blackboard and then trigger a re-plan.

```java
import com.embabel.agent.core.AgentPlatform;
import com.embabel.chat.ChatSession;
import com.embabel.chat.UserMessage;
import java.time.Instant;

public void resumeWorkflow(String processId, String comments, boolean approved, ChatSession session, AgentPlatform platform) {
    // 1. Create the structured data object expected by the agent
    var feedback = new WriteAndReviewAgent.HumanFeedback(approved, comments);
    
    // 2. Inject the object directly into the blackboard via AgentPlatform
    // Note: Use platform.getAgentProcess(id).getBlackboard().addObject()
    platform.getAgentProcess(processId).getBlackboard().addObject(feedback);
    
    // 3. Send a signal (UserMessage) to wake up the planner and evaluate the new state
    session.onUserMessage(new UserMessage("Decision submitted", "system", Instant.now()));
}
```

### 3.5.3 Comprehensive Example: WriteAndReviewAgent
This example demonstrates a complete multi-state workflow featuring content generation, human-in-the-loop approval, and conditional looping.

```java
import com.embabel.agent.api.annotation.*;
import com.embabel.agent.api.common.ActionContext;
import com.embabel.agent.api.common.Ai;
import com.embabel.chat.AssistantMessage;
import com.embabel.chat.UserMessage;
import com.embabel.agent.core.hitl.WaitFor;
import com.embabel.common.ai.model.LlmOptions;
import org.springframework.stereotype.Component;

@Agent(description = "Iterative story writing with human-in-the-loop review")
@Component
public class WriteAndReviewAgent {

    public record Story(String text) {}
    public record HumanFeedback(boolean acceptable, String comments) {}

    @State
    public interface Stage {}

    @Action
    public AssessStory craftStory(UserMessage input, Ai ai, ActionContext ctx) {
        var draft = ai.withLlm(LlmOptions.withAutoLlm().withTemperature(0.7))
                      .createObject("Write a story about: " + input.getContent(), Story.class);
        ctx.sendMessage(new AssistantMessage("Generated draft: " + draft.text()));
        return new AssessStory(input, draft);
    }

    @State
    public record AssessStory(UserMessage originalInput, Story story) implements Stage {
        @Action
        public HumanFeedback getFeedback() {
            // Process enters WAITING state here
            return WaitFor.formSubmission("Review the draft. Approve or provide instructions.", HumanFeedback.class);
        }

        @Action(clearBlackboard = true)
        public Stage assess(HumanFeedback feedback, ActionContext ctx) {
            if (feedback.acceptable()) {
                ctx.sendMessage(new AssistantMessage("Approved! Finalizing..."));
                return new Done(story);
            }
            ctx.sendMessage(new AssistantMessage("Rejected. Instructions: " + feedback.comments()));
            return new ReviseStory(originalInput, story, feedback.comments());
        }
    }

    @State
    public record ReviseStory(UserMessage input, Story story, String instructions) implements Stage {
        @Action(clearBlackboard = true)
        public AssessStory revise(Ai ai, ActionContext ctx) {
            var revised = ai.createObject("Revise this story: " + story.text() + 
                                         "\nBased on: " + instructions, Story.class);
            ctx.sendMessage(new AssistantMessage("Revised draft: " + revised.text()));
            return new AssessStory(input, revised);
        }
    }

    @State
    public record Done(Story story) implements Stage {
        @AchievesGoal(description = "Story is finalized and reviewed")
        public String finish() { return story.text(); }
    }
}
```

**Key Patterns in this Example:**
- **State Scoping**: The planner only sees actions relevant to the current stage (e.g., `revise` is only available in `ReviseStory`)
- **Looping**: `ReviseStory` returns a new `AssessStory`, creating a cycle until the human approves
- **Blackboard Clearing**: `clearBlackboard = true` is used on transitions to prevent the planner from using stale data from previous iterations
- **Direct Feedback Usage**: The `assess` action uses the injected `HumanFeedback` object immediately without additional AI inference

# Chapter 4: RAG & Conversations (Multi-Turn)

Embabel’s RAG and Chat architectures are designed to minimize token usage by treating context as manageable **Assets**, **References**, and **States**.

## 4.1 Agentic RAG (Search as a Tool, ToolishRag)
Embabel RAG is **Agentic**. Instead of just stuffing document chunks into a prompt (Stateless RAG), it treats the search engine as a set of tools (e.g., `vectorSearch`) that the LLM invokes only when necessary.

### 4.1.1 Spring Configuration for Lucene RAG
Configure the `SearchOperations` (the engine) and wrap it in a `ToolishRag` (the LLM tool).

```java
import com.embabel.agent.rag.lucene.LuceneSearchOperations;
import com.embabel.agent.rag.service.SearchOperations;
import com.embabel.agent.rag.tools.ToolishRag;
import com.embabel.agent.rag.ingestion.transform.AddTitlesChunkTransformer;
import com.embabel.common.ai.model.ModelProvider;
import com.embabel.common.ai.model.ModelSelectionCriteria;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.nio.file.Paths;

@Configuration
public class RagConfig {

    @Bean
    public SearchOperations luceneSearch(ModelProvider modelProvider) {
        // Automatically selects the best embedding service (e.g., Gemini)
        var embeddingService = modelProvider.getEmbeddingService(ModelSelectionCriteria.getAuto());

        return LuceneSearchOperations
                .withName("technical-docs")
                .withEmbeddingService(embeddingService)
                .withIndexPath(Paths.get("target/lucene-index"))
                // ENHANCEMENT: Prepend document titles to each chunk for better retrieval accuracy
                .withChunkTransformer(AddTitlesChunkTransformer.INSTANCE) 
                .buildAndLoadChunks();
    }

    @Bean
    public ToolishRag luceneRagTool(SearchOperations luceneSearch) {
        // Expose the search engine to the LLM as a tool named "docs"
        return new ToolishRag("docs", "Technical documentation and incident reports", luceneSearch);
    }
}
```

### 4.1.2 Agent Usage & Citation Logic
Inject the `ToolishRag` bean and use `.withReference(rag)` in your actions. You can also instruct the LLM to provide exact citations from the source URIs.

```java
import com.embabel.agent.api.annotation.Action;
import com.embabel.agent.api.annotation.Agent;
import com.embabel.agent.api.common.Ai;
import com.embabel.agent.rag.tools.ToolishRag;

@Agent(description = "Expert Root Cause Analysis Agent")
public class RcaAgent {
    private final ToolishRag luceneRag;

    public RcaAgent(ToolishRag luceneRag) { this.luceneRag = luceneRag; }

    @Action
    public Report analyze(String incident, Ai ai) {
        return ai.withAutoLlm()
                .withReference(luceneRag) // LLM now has "docs" tools
                .createObject(String.format("""
                        Analyze the incident using our 'docs' knowledge base.
                        
                        # INCIDENT: %s
                        
                        # CITATION RULES:
                        - For 'evidence' field, use the actual filename from the # URI field in the search results.
                        - Do not invent filenames; use exact matches only.
                        """, incident), Report.class);
    }
}
```

## 4.2 Metadata Filtering & Scoped RAG
Ensure data isolation (multi-tenancy) or domain specificity by applying filters to the RAG instance.

```java
@Action
public void scopedSearch(Customer customer, ToolishRag rag) {
    // Apply metadata filters to restrict search to a specific owner
    var scopedRag = rag.withMetadataFilter(PropertyFilter.eq("ownerId", customer.getId()))
                       .withEntityFilter(EntityFilter.hasLabel("Technical"));

    // Apply filters based on entity labels (e.g., Lucene tags)
    var filteredRag = scopedRag.withEntityFilter(EntityFilter.hasAnyLabel("Person", "Org"));
}
```

## 4.3 Chatbot Architecture (Stateful Conversations)
An Embabel chatbot is backed by a long-lived `AgentProcess` that manages multi-turn context by separating **Message History** from **Blackboard State**.

### 4.3.1 Core Concepts
- **Long-Lived AgentProcess**: Unlike stateless RAG, a chatbot process pauses between messages, maintaining the blackboard state throughout the session
- **Utility AI Selection**: The planner uses Utility AI to select the best response strategy (e.g., RAG search vs. direct answer) based on the current context
- **Message Triggers**: Actions use `trigger = UserMessage.class` to fire automatically when a new user message is added to the blackboard

### 4.3.2 Implementation: Action Methods
Define actions in an `@EmbabelComponent` that respond to `UserMessage` events.

```java
import com.embabel.agent.api.annotation.Action;
import com.embabel.agent.api.annotation.EmbabelComponent;
import com.embabel.agent.api.common.ActionContext;
import com.embabel.chat.Conversation;
import com.embabel.chat.UserMessage;
import com.embabel.chat.AssistantMessage;
import java.util.Map;

@EmbabelComponent
public class ChatActions {

    @Action(canRerun = true, trigger = UserMessage.class)
    public void respond(Conversation conversation, ActionContext context) {
        // AI evaluates the entire message history from conversation.getMessages()
        var assistantMessage = context.ai()
                .withAutoLlm()
                .rendering("chatbot-persona") // Load Jinja template from resources/prompts
                .respond(conversation.getMessages());

        // Add the response to the conversation history and notify output channels
        context.sendMessage(conversation.addMessage(assistantMessage));
    }
}
```

### 4.3.3 Implementation: Configuration
Configure the `Chatbot` bean to discover all registered actions.

```java
import com.embabel.chat.Chatbot;
import com.embabel.agent.core.AgentPlatform;
import com.embabel.chat.agent.AgentProcessChatbot;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ChatConfiguration {

    @Bean
    public Chatbot chatbot(AgentPlatform agentPlatform) {
        // Utility-based chatbot that automatically discovers @Action methods
        return AgentProcessChatbot.utilityFromPlatform(agentPlatform);
    }
}
```

### 4.3.4 Implementation: Web Controller (HTMX)
Manage chat sessions and bridge the agent's output to the web interface.

```java
import com.embabel.chat.ChatSession;
import com.embabel.chat.Chatbot;
import com.embabel.chat.Message;
import com.embabel.chat.UserMessage;
import com.embabel.agent.api.channel.OutputChannel;
import com.embabel.agent.api.channel.MessageOutputChannelEvent;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@RestController
@RequestMapping("/chat")
public class ChatController {
    private final Chatbot chatbot;
    private final List<Message> history = new CopyOnWriteArrayList<>();

    public ChatController(Chatbot chatbot) { this.chatbot = chatbot; }

    @PostMapping("/send")
    public List<Message> sendMessage(@RequestParam String text) {
        // Create or retrieve session with an OutputChannel to capture AI responses
        ChatSession session = chatbot.createSession(null, event -> {
            if (event instanceof MessageOutputChannelEvent me) {
                history.add(me.getMessage());
            }
        }, null, "unique-conv-id");

        UserMessage userMsg = new UserMessage(text);
        history.add(userMsg);
        
        // Trigger the agent process
        session.onUserMessage(userMsg);
        
        return history;
    }
}
```

### **[Tip] Scaling Actions with Multiple Platforms**
When the number of actions grows, managing them and enabling the AI to make efficient choices can become challenging.
- **Multiple `AgentPlatform` Instances**: Create several `AgentPlatform` beans, each configured to scan and manage a specific set of actions relevant to a domain (e.g., `supportPlatform`, `salesPlatform`).
- **`@Qualifier` for `Chatbot` Binding**: Use `@Qualifier` during `@Bean` definition to bind a `Chatbot` instance to a specific `AgentPlatform`, ensuring each chatbot operates within its own action space.

### **[Tip] Building Stateless Chatbots**
The current sample code uses an in-memory session (`ConcurrentHashMap`), making it stateful. For production environments, a stateless architecture is preferred for scalability and resilience.
- **External Storage for Conversation State**: Leverage the `embabel-chat-store` module to persist `Conversation` history and blackboard state to external databases like `Neo4j` or `PostgreSQL`.
- **`conversationId` for State Restoration**: Upon each request, use a `conversationId` (passed from the client) to load the previous conversation state from the database, process the request, and then save any updated state back to the database.
- **Improved Scalability**: This stateless design allows multiple server instances to share the same conversation state, ensuring high availability and horizontal scalability for your chatbot service.

---

## 4.4 Asset Tracking & Asset-as-a-Tool
**Assets** are structured outputs (documents, reports, POJOs) generated during a session. Embabel allows these assets to be re-used as **Tools** (LLM References) in subsequent turns to save tokens and maintain precision.

### 4.4.1 Conversation Assets
The `Conversation` interface provides a merged view of assets from both the explicit `AssetTracker` and individual `AssistantMessage` objects.

```java
import com.embabel.chat.Conversation;
import com.embabel.agent.api.reference.LlmReference;
import java.util.List;

@Action(trigger = UserMessage.class)
public void respondWithContext(Conversation conv, ActionContext ctx) {
    // 1. Retrieve assets from the last 5 turns
    // 2. Convert them into LlmReferences (searchable tools for the LLM)
    List<LlmReference> assetRefs = conv.mostRecent(5).references();

    var response = ctx.ai()
            .withReferences(assetRefs) // LLM can now "query" earlier turn results
            .respond(conv.getMessages());

    conv.addMessage(response);
}
```

### 4.4.2 Key Asset Mechanics
- **AssetTracker**: Maintains artifacts that should persist across multiple turns or server restarts
- **Chronological Merging**: `conversation.getAssets()` returns tracker assets first, followed by message-level assets in order
- **Duplicate Removal**: Assets are automatically de-duplicated by their unique ID, with tracker versions taking priority

## 4.5 Eager Search Pattern
Pre-load context via similarity search *before* the LLM starts its reasoning, while keeping the tools available for follow-up queries. This combines the speed of traditional RAG with the flexibility of agentic tools.

```java
@Action
public void eagerRAG(UserInput input, ToolishRag rag, OperationContext ctx) {
    // 1. Pre-search 3 relevant chunks and include them in the prompt immediately
    var eagerRag = rag.withEagerSearchAbout(input.getContent(), 3);

    ctx.ai().withReference(eagerRag)
            .generateText("Analyze the request using the provided context...");
}
```

## 4.6 Enterprise RAG Storage (PostgreSQL & pgvector)
For production services requiring **High Availability (HA)** and **Scalability**, Embabel supports external vector stores beyond the default Lucene implementation. The `embabel-rag-pgvector` module provides a robust, battle-tested solution for enterprise environments.

### 4.6.1 Key Advantages
- **Hybrid Search**: Combines semantic vector similarity with traditional full-text search (PostgreSQL `tsvector`) and fuzzy matching (`pg_trgm`).
- **High Availability**: Leverages mature PostgreSQL HA solutions (e.g., Patroni, Repmgr) to ensure continuous operation.
- **Transactional Integrity**: Ensures that document updates and metadata changes are ACID-compliant.

### 4.6.2 Implementation with Gemini Embeddings
To use PostgreSQL as your RAG store, configure the `PgVectorSearchOperations` bean in your Spring context. This example uses **Google Gemini** for generating embeddings.

```java
@Configuration
public class EnterpriseRagConfig {

    @Bean
    public SearchOperations pgVectorSearch(
            ModelProvider modelProvider,
            DataSource dataSource,
            RagProperties properties) {

        // 1. Retrieve Gemini embedding service
        var embeddingService = modelProvider.getEmbeddingService(
                ModelSelectionCriteria.fromModel("gemini-embedding-001"));

        // 2. Build PostgreSQL-based search operations
        // Note: For pgvector configuration, use specific builder from embabel-rag-pgvector
        return PgVectorSearchOperations.builder()
                .withName("enterprise-docs")
                .withDataSource(dataSource) // Shared DB connection
                .withEmbeddingService(embeddingService)
                .withHybridSearchEnabled(true) // Enable vector + full-text search
                .withChunkerConfig(properties.getChunkerConfig())
                .build();
    }
}
```

**[Operational Tip]** Multiple agent server nodes can connect to the same PostgreSQL cluster, allowing you to scale your AI services horizontally while maintaining a single, consistent knowledge source.

## 4.7 Document Ingestion Pipeline
To populate your PostgreSQL store with data, you need an ingestion pipeline. Embabel provides a unified ingestion mechanism that handles the heavy lifting: parsing, chunking, embedding, and storage.

### 4.7.1 Ingestion Service Implementation
This service demonstrates how to take a raw file (like a PDF) and store it in your enterprise RAG database using **Google Gemini** for embeddings.

```java
import com.embabel.agent.rag.service.SearchOperations;
import com.embabel.agent.rag.ingestion.policy.NeverRefreshExistingDocumentContentPolicy;
import com.embabel.agent.rag.ingestion.TikaHierarchicalContentReader;
import org.springframework.stereotype.Service;
import java.nio.file.Path;

@Service
public class RagIngestionService {

    private final SearchOperations ragStore; // Injected SearchOperations implementation

    public RagIngestionService(SearchOperations ragStore) {
        this.ragStore = ragStore;
    }

    /**
     * Parses a local file and saves it to the vector database.
     */
    public void processNewDocument(Path pdfPath) {
        // Extract text and metadata via Tika and trigger the ingestion process
        // Automatically performs:
        // - Chunking: Splits text into manageable segments
        // - Embedding: Calls Gemini to generate vectors for each segment
        // - Persistence: Saves everything to the configured store
        var uri = pdfPath.toUri().toString();
        NeverRefreshExistingDocumentContentPolicy.INSTANCE
                .ingestUriIfNeeded(
                        ragStore,
                        new TikaHierarchicalContentReader(),
                        uri
                );
    }
}
```

### 4.7.2 Key Components of Ingestion
- **TikaHierarchicalContentReader**: Part of the `embabel-agent-rag-tika` module. It extracts structured content and metadata from raw files
- **Chunking Strategy**: Configured via `ChunkerConfig`. It determines how text is split (e.g., semantic boundaries or fixed size with overlap)
- **Ingest Policy**: `NeverRefreshExistingDocumentContentPolicy` ensures documents are not re-ingested if already present

**[Operational Tip]** For bulk updates or very large documents, use `batchIngest()` on the store to optimize database writes and manage embedding provider rate limits effectively.

## 4.8 Portable RAG for CLI Tools (Lucene & Gemini)
For personal CLI tools or lightweight projects, **Lucene** is the ideal storage engine because it requires zero infrastructure. This setup allows your agent to carry its knowledge base in a local folder, using **Google Gemini** for high-quality embeddings.

### 4.8.1 Configuration for Local Storage
Setup the `SearchOperations` bean to use a hidden local folder (`.embabel-index`) for persistence.

```java
@Configuration
public class PortableRagConfig {
    @Bean
    public SearchOperations personalRag(ModelProvider modelProvider) {
        // Use Gemini for high-quality API-based embeddings
        var geminiEmbedding = modelProvider.getEmbeddingService(
                ModelSelectionCriteria.fromModel("gemini-embedding-001"));

        return LuceneSearchOperations
                .withName("cli-knowledge")
                .withEmbeddingService(geminiEmbedding)
                .withIndexPath(Paths.get("./.embabel-index")) // Local folder storage
                .buildAndLoadChunks();
    }
}
```

### 4.8.2 Agent Implementation
This agent demonstrates how to both ingest knowledge and research questions using the local Lucene store.

```java
@Agent(description = "Agent that manages and researches local documents")
public class PersonalDocAgent {

    private final SearchOperations ragStore;

    public PersonalDocAgent(SearchOperations ragStore) {
        this.ragStore = ragStore;
    }

    // Action 1: Ingesting a new file into the local index
    @Action
    public void indexFile(String filePath) {
        var uri = Path.of(filePath).toUri().toString();
        NeverRefreshExistingDocumentContentPolicy.INSTANCE
                .ingestUriIfNeeded(
                        ragStore,
                        new TikaHierarchicalContentReader(),
                        uri
                );
    }

    // Action 2: Researching using the indexed knowledge
    @Action
    public Answer research(UserInput input, OperationContext ctx) {
        // Expose the local store as a searchable tool bundle
        var localDocs = new ToolishRag("myDocs", "Local knowledge base", ragStore);

        return ctx.ai()
                .withReference(localDocs)
                .createObject("Answer using my documents: " + input.getContent(), Answer.class);
    }
}
```

### 4.8.3 Why this is best for CLI
- **Portability**: Knowledge is stored in `./.embabel-index`. Move the project folder, and the index moves with it.
- **Zero Setup**: No Docker or external database is required. It runs wherever Java runs.
- **Hybrid Performance**: Lucene handles both semantic meaning (via Gemini) and exact keyword matches with extremely low latency.

**Operational Tip**: Add `.embabel-index/` to your `.gitignore` to keep binary index files out of source control.

**LLM Instruction**: In RAG mode, always prioritize `vectorSearch` for semantic queries and `textSearch` for keyword/exact matching.

# Chapter 5: Observability & Testing

Embabel's design ensures that agents are **composable, testable, and observable**.

## 5.1 Observability: Tracing Agent Lifecycle
Automatic OpenTelemetry tracing of actions, LLM calls, and planning iterations.

### 5.1.1 Custom Operation Tracking (@Tracked)
Add observability spans to your own methods. Inputs, outputs, duration, and errors are captured automatically.
```java
import com.embabel.agent.api.annotation.Tracked;
import com.embabel.agent.api.TrackType;
import org.springframework.stereotype.Component;

@Component
public class PaymentService {
    @Tracked(
        value = "callPaymentApi",
        type = TrackType.EXTERNAL_CALL,
        description = "Payment gateway call"
    )
    public PaymentResult processPayment(Order order) {
        // Automatically creates a span with method arguments and return value
        return gateway.execute(order);
    }
}
```
**[LLM Instruction]** Use `@Tracked` for any business logic or external API calls inside an `@Action` method.

## 5.2 Unit Testing: Predictable & Cost-Effective
Test individual agent actions without real LLM calls using `FakeOperationContext` and `FakePromptRunner`.

### 5.2.1 Unit Test Pattern Sample
```java
import com.embabel.agent.test.unit.FakeOperationContext;
import com.embabel.agent.test.unit.FakePromptRunner;
import com.embabel.agent.domain.io.UserInput;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StoryAgentTest {
    @Test
    void testStoryAgent() {
        var context = FakeOperationContext.create();
        var promptRunner = (FakePromptRunner) context.promptRunner();

        // Arrange: Mock the LLM's object creation
        context.expectResponse(new Story("Once upon a time..."));

        var agent = new StoryAgent();
        var userInput = new UserInput("Tell a story about a dragon");

        // Act: Execute the action directly
        Story story = agent.writeStory(userInput, context);

        // Assert: Verify logic and hyperparameters
        assertEquals("Once upon a time...", story.text());

        // Inspect the underlying prompt sent to the LLM
        var invocation = promptRunner.getLlmInvocations().getFirst();
        assertTrue(invocation.getPrompt().contains("dragon"));
        assertEquals(0.8, invocation.getInteraction().getLlm().getTemperature(), 0.01);
    }
}
```

## 5.3 Integration Testing: Workflow Validation
Verify complete agent workflows under Spring Boot while still avoiding real LLM calls for speed.

### 5.3.1 Integration Test Pattern Sample
```java
import com.embabel.agent.test.integration.EmbabelMockitoIntegrationTest;
import com.embabel.agent.domain.io.UserInput;
import com.embabel.agent.api.invocation.AgentInvocation;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.contains;

class StoryWriterIntegrationTest extends EmbabelMockitoIntegrationTest {
    @Test
    void shouldExecuteCompleteWorkflow() {
        var input = new UserInput("Write about AI");
        var story = new Story("AI will transform the world...");
        var reviewedStory = new ReviewedStory("Excellent exploration of themes.");

        // Stub object creation calls
        whenCreateObject(contains("Craft a short story"), Story.class).thenReturn(story);
        whenCreateObject(contains("Critically review this story"), ReviewedStory.class).thenReturn(reviewedStory);

        // Invoke the agent via AgentInvocation
        var invocation = AgentInvocation.create(agentPlatform, ReviewedStory.class);
        var result = invocation.invoke(input);

        // Verify the chain worked end-to-end
        assertNotNull(result);
        assertEquals(reviewedStory, result);

        // Verify specifically that the reviewer used temperature 0.2
        verifyCreateObjectMatching(p -> p.contains("Critically review"), ReviewedStory.class,
                llm -> llm.getLlm().getTemperature() == 0.2);
    }
}
```

## 5.4 Key Testing Patterns
- **FakePromptRunner**: Fully supports fluent API patterns like `withId()` and `creating()`
- **MDC Propagation**: `run_id` and `action_name` are automatically added to logs
- **withExample**: Test actions that use structured examples for few-shot prompting
- **verifyNoMoreInteractions**: Use in integration tests to ensure the LLM was not called unexpectedly
