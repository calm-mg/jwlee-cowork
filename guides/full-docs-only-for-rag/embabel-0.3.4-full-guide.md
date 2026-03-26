---
title: "Embabel Agent Framework User Guide"
source: "https://docs.embabel.com/embabel-agent/guide/0.3.4-SNAPSHOT/"
author:
published:
created: 2026-03-25
description:
tags:
  - "clippings"
---
![315px Meister der Weltenchronik 001](https://docs.embabel.com/embabel-agent/guide/0.3.4-SNAPSHOT/images/common/315px-Meister_der_Weltenchronik_001.png)

315px Meister der Weltenchronik 001

Embabel Agent Release: 0.3.4-SNAPSHOT

© 2024-2026 Embabel Pty, Ltd

Rod Johnson, Alex Hein-Heifetz, Dr. Igor Dayen, Arjen Poutsma, Jasper Blues

## 1\. Overview

Agentic AI is the use of large language (and other multi-modal) models not just to generate text, but to act as reasoning, goal-driven agents that can plan, call tools, and adapt their actions to deliver outcomes.

The JVM is a compelling platform for this because its strong type safety provides guardrails for integrating LLM-driven behaviors with real systems. Because so many production applications already run on the JVM it is the natural place to embed AI.

While Agentic AI has been hyped, much of it has lived in academic demos with little practical value; by integrating directly into legacy and enterprise JVM applications, we can unlock AI capabilities without rewriting core systems or tearing down a factory to install new machinery.

### 1.1. Glossary

Before we begin, in this glossary we’ll explain some terms that may be new if you’re taking your first steps as an applied AI software developer. It is assumed that you already know what a large language model (LLM) is from an end-user’s point of view.

|  | You may skim or skip this section if you’re already a seasoned agentic AI engineer. |
| --- | --- |

Agent

An Agent in the Embabel framework is a self-contained component that bundles together domain logic, AI capabilities, and tool usage to achieve a specific goal on behalf of the user.

Inside, it exposes multiple `@Action` methods, each representing discrete steps the agent can take. Actions depend on typically structured (sometimes natural language) input. The input is used to perform tasks on behalf of the user - executing domain code, calling AI models or even calling other agents as a sub-process.

When an AI model is called it may be given access to tools that expand its capabilities in order to achieve a goal. The output is a new type, representing a transformation of the input, however during execution one or more side-effects can occur. An example of side effects might be new records stored in a database, orders placed on an e-commerce site and so on.

Tools

Tools extend the raw capabilities of an LLM by letting it interact with the outside world. On its own, a language model can only generate responses from its training data and context window, which risks producing inaccurate or “hallucinated” answers.

While tool usage is inspired by an technique known as **ReAct** (Reason + Act), which itself builds on **Chain of Thought** reasoning, most recent LLMs allow specifying tools specifically instead of relying on prompt engineering techniques.

When tools are present, the LLM interprets the user request, plans steps, and then delegates certain tasks to tools in a loop. This lets the model alternate between reasoning (“what needs to be done?”) and acting (“which tool can do it?”).

**Benefits of tools include:**

- The ability to answer questions or perform tasks beyond what the LLM was trained on, by delegating to domain-specific or external systems.
- Producing useful **side effects**, such as creating database records, generating visualizations, booking flights, or invoking any process the system designer provides.
	In short, tools are one way to bridge the gap between **text prediction** and **real-world action**, turning an LLM into a practical agent capable of both reasoning and execution. In Embabel many tools are bound domain objects.

MCP

Model Context Protocol [(MCP)](https://docs.embabel.com/embabel-agent/guide/0.3.4-SNAPSHOT/#reference.integrations__mcp) is a standardized way of hosting and sharing tools. Unlike plain tools, which are usually wired directly into one agent or app, an MCP Server makes tools discoverable and reusable across models and runtimes they can be registered system-wide or at runtime, and invoked through a common protocol. Embabel can both consume and publish such tools for systems integration.

Domain Integrated Context Engineering (DICE)

Enhances context engineering by grounding both LLM inputs and outputs in typed domain objects. Instead of untyped prompts, context is structured with business-aware models that provide precision, testability, and seamless integration with existing systems. DICE transforms context into a re-usable, inspectable, and reliably manipulable artifact.

### 1.2. Why do we need an Agent Framework?

Aren’t LLMs smart enough to solve our problems directly? Aren’t MCP tools all we need to allow them to solve complex problems? LLMs seem to get more capable by the day and MCPs can give LLMs access to a lot of empowering tools, making them even more capable.

But there are still many reasons that a higher level orchestration technology is needed, especially for business applications. Here are some of the most important:

- **Explainability**: Why were choices made in solving a problem?
- **Discoverability**: How do we find the right tools at each point, and ensure that models aren’t confused in choosing between them?
- **Ability to mix models**, so that we are not reliant only on the largest models but can use local, cheaper, private models for many tasks
- **Ability to inject guardrails** at any point in a flow
- **Ability to manage flow execution** and introduce greater resilience
- **Composability of flows at scale**. We’ll soon be seeing not just agents running on one system, but federations of agents.
- **Safer integration with sensitive existing systems** such as databases, where it is dangerous to allow even the best LLM write access.

Agent frameworks break complex tasks into smaller, manageable components, offering greater control and predictability.

Agent frameworks offer "code agency" as well as "LLM agency." This division is well described in this [paper from NVIDIA Research](https://research.nvidia.com/labs/lpr/slm-agents/).

Further reading:

### 1.3. Embabel Differentiators

So how does Embabel differ from other agent frameworks? We like to believe the Embabel agent framework is to be the best fit for developing agentic AI in the enterprise.

#### 1.3.1. Sophisticated Planning

Goes beyond a finite state machine or sequential execution with nesting by introducing a true planning step, using a non-LLM AI algorithm. This enables the system to perform tasks it wasn’t programmed to do by combining known steps in a novel order, as well as make decisions about parallelization and other runtime behavior.

#### 1.3.2. Superior Extensibility and Reuse

Because of dynamic planning, adding more domain objects, actions, goals and conditions can extend the capability of the system, *without editing FSM definitions* or existing code.

#### 1.3.3. Strong Typing and Object Orientation

Actions, goals and conditions are informed by a domain model, which can include behavior. Everything is strongly typed and prompts and manually authored code interact cleanly. No more magic maps. Enjoy full refactoring support.

#### 1.3.4. Platform Abstraction

Clean separation between programming model and platform internals allows running locally while potentially offering higher QoS in production without changing application code.

#### 1.3.5. LLM Mixing

It is easy to build applications that mix LLMs, ensuring the most cost-effective yet capable solution. This enables the system to leverage the strengths of different models for different tasks. In particular, it facilitates the use of local models for point tasks. This can be important for cost and privacy.

#### 1.3.6. Spring and JVM Integration

Built on Spring and the JVM, making it easy to access existing enterprise functionality and capabilities. For example:

- Spring can inject and manage agents, including using Spring AOP to decorate functions.
- Robust persistence and transaction management solutions are available.

#### 1.3.7. Designed for Testability

Both unit testing and agent end-to-end testing are easy from the ground up.

### 1.4. Core Concepts

Agent frameworks break up tasks into separate smaller interactions, making LLM use more predictable and focused.

- **Actions**: Steps an agent takes. These are the building blocks of agent behavior.
- **Goals**: What an agent is trying to achieve.
- **Conditions**: Conditions to do evaluations while planning. Conditions are reassessed after each action is executed.
- **Domain Model**: Objects underpinning the flow and informing Actions, Goals and Conditions.

This enables Embabel to create a **plan**: A sequence of actions to achieve a goal. Plans are dynamically formulated by the system, not the programmer. The system replans after the completion of each action, allowing it to adapt to new information as well as observe the effects of the previous action. This is effectively an [OODA loop](https://en.wikipedia.org/wiki/OODA_loop).

|  | Application developers don’t usually have to deal with conditions and planning directly, as most conditions result from data flow defined in code, allowing the system to infer pre and post conditions to (re-)evaluate the plan. |
| --- | --- |

#### 1.4.1. Complete Example

Let’s look at a complete example that demonstrates how Embabel infers conditions from input/output types and manages data flow between actions. This example comes from the [Embabel Agent Examples](https://github.com/embabel/embabel-agent-examples) repository:

```java
@Agent(description = "Find news based on a person's star sign")  (1)
public class StarNewsFinder {

    private final HoroscopeService horoscopeService;  (2)
    private final int storyCount;

    public StarNewsFinder(
            HoroscopeService horoscopeService,  (3)
            @Value("${star-news-finder.story.count:5}") int storyCount) {
        this.horoscopeService = horoscopeService;
        this.storyCount = storyCount;
    }

    @Action  (4)
    public StarPerson extractStarPerson(UserInput userInput, OperationContext context) {  (5)
        return context.ai()
            .withLlm(OpenAiModels.GPT_41)
            .createObject("""
                Create a person from this user input, extracting their name and star sign:
                %s""".formatted(userInput.getContent()), StarPerson.class);  (6)
    }

    @Action  (7)
    public Horoscope retrieveHoroscope(StarPerson starPerson) {  (8)
        // Uses regular injected Spring service - not LLM
        return new Horoscope(horoscopeService.dailyHoroscope(starPerson.sign()));  (9)
    }

    @Action  (10)
    public RelevantNewsStories findNewsStories(
            StarPerson person, Horoscope horoscope, OperationContext context) {  (11)
        var prompt = """
            %s is an astrology believer with the sign %s.
            Their horoscope for today is: %s
            Given this, use web tools to find %d relevant news stories.
            """.formatted(person.name(), person.sign(), horoscope.summary(), storyCount);

        return context.ai().withDefaultLlm()
            .withToolGroup(CoreToolGroups.WEB)  (12)
            .createObject(prompt, RelevantNewsStories.class);
    }

    @AchievesGoal(description = "Write an amusing writeup based on horoscope and news")  (13)
    @Action
    public Writeup writeup(
            StarPerson person, RelevantNewsStories stories, Horoscope horoscope,
            OperationContext context) {  (14)
        var llm = LlmOptions.fromCriteria(ModelSelectionCriteria.getAuto())
            .withTemperature(0.9);  (15)

        var storiesFormatted = stories.items().stream()
            .map(s -> "- " + s.url() + ": " + s.summary())
            .collect(Collectors.joining("\n"));

        var prompt = """
            Write something amusing for %s based on their horoscope and news stories.
            Format as Markdown with links.
            <horoscope>%s</horoscope>
            <news_stories>
            %s
            </news_stories>
            """.formatted(person.name(), horoscope.summary(), storiesFormatted);  (16)

        return context.ai().withLlm(llm).createObject(prompt, Writeup.class);  (17)
    }
}
```

| **1** | **Agent Declaration**: The `@Agent` annotation defines this as an agent capable of a multi-step flow. |
| --- | --- |
| **2** | **Spring Integration**: Regular Spring dependency injection - the agent uses both LLM services and traditional business services. |
| **3** | **Service Injection**: `HoroscopeService` is injected like any Spring bean - agents can mix AI and non-AI operations seamlessly. |
| **4** | **Action Definition**: `@Action` marks methods as steps the agent can take. Each action represents a capability. |
| **5** | **Input Condition Inference**: The method signature `extractStarPerson(UserInput userInput, …​)` tells Embabel:  - **Precondition**: "A UserInput object must be available" - **Required Data**: The agent needs user input to proceed - **Capability**: This action can extract structured data from unstructured input |
| **6** | **Output Condition Creation**: Returning `StarPerson` creates:  - **Postcondition**: "A StarPerson object is now available in the world state" - **Data Availability**: This output becomes input for subsequent actions - **Type Safety**: The domain model enforces structure |
| **7** | **Non-LLM Action**: Not all actions use LLMs - this demonstrates hybrid AI/traditional programming. |
| **8** | **Data Flow Chain**: The method signature `retrieveHoroscope(StarPerson starPerson)` creates:  - **Precondition**: "A StarPerson object must exist" (from previous action) - **Dependency**: This action can only execute after `extractStarPerson` completes - **Service Integration**: Uses the injected `horoscopeService` rather than an LLM |
| **9** | **Regular Service Call**: This action calls a traditional Spring service - demonstrating how agents blend AI and conventional operations. |
| **10** | **Another Action**: This action uses tools specified at the `PromptRunner` level. |
| **11** | **Multi-Input Dependencies**: This method requires both `StarPerson` and `Horoscope` - showing complex data flow orchestration. |
| **12** | **Tool-Enabled LLM**: `withToolGroup(CoreToolGroups.WEB)` adds web search tools to this LLM call, allowing it to search for current news stories. |
| **13** | **Goal Achievement**: `@AchievesGoal` marks this as a terminal action that completes the agent’s objective. |
| **14** | **Complex Input Requirements**: The final action requires three different data types, showing sophisticated orchestration. |
| **15** | **Creative Configuration**: High temperature (0.9) optimizes for creative, entertaining output - appropriate for amusing writeups. |
| **16** | **Structured Prompt with Data**: The prompt includes both the horoscope summary and formatted news stories using XML-style tags. This ensures the LLM has all the context it needs from earlier actions. |
| **17** | **Final Output**: Returns `Writeup`, completing the agent’s goal with personalized content. |

State is managed by the framework, through the process blackboard.

#### 1.4.2. The Inferred Execution Plan for the Example

Based on the type signatures alone, Embabel automatically infers this execution plan for the example agent above:

**Goal**: Produce a `Writeup` (final return type of `@AchievesGoal` action)

The initial plan:

- To emit `Writeup` → need `writeup()` action
- `writeup()` requires `StarPerson`, `RelevantNewsStories`, and `Horoscope`
- To get `StarPerson` → need `extractStarPerson()` action
- To get `Horoscope` → need `retrieveHoroscope()` action (requires `StarPerson`)
- To get `RelevantNewsStories` → need `findNewsStories()` action (requires `StarPerson` and `Horoscope`)
- `extractStarPerson()` requires `UserInput` → must be provided by user

Execution sequence:

`UserInput` → `extractStarPerson()` → `StarPerson` → `retrieveHoroscope()` → `Horoscope` → `findNewsStories()` → `RelevantNewsStories` → `writeup()` → `Writeup` and achieves goal.

#### 1.4.3. Key Benefits of Type-Driven Flow

**Automatic Orchestration**: No manual workflow definition needed - the agent figures out the sequence from type dependencies. This is particularly beneficial if things go wrong, as the planner can re-evaluate the situation and may be able to find an alternative path to the goal.

**Dynamic Replanning**: After each action, the agent reassesses what’s possible based on available data objects.

**Type Safety**: Compile-time guarantees that data flows correctly between actions. No magic string keys.

**Flexible Execution**: If multiple actions could produce the required input type, the agent chooses based on context and efficiency. (Actions can have cost and value.)

This demonstrates how Embabel transforms simple method signatures into sophisticated multi-step agent behavior, with the complex orchestration handled automatically by the framework.

## 2\. Getting Started

### 2.1. Quickstart

There are two GitHub template repos you can use to create your own project:

- Java template - [github.com/embabel/java-agent-template](https://github.com/embabel/java-agent-template)
- Kotlin template - [github.com/embabel/kotlin-agent-template](https://github.com/embabel/kotlin-agent-template)

Or you can use our [project creator](https://github.com/embabel/project-creator) to create a custom project:

```bash
uvx --from git+https://github.com/embabel/project-creator.git project-creator
```

|  | The `uvx` command can be installed from the [astral-uv](https://docs.astral.sh/uv/) package. It is a Python package and project manager used to run the Embabel [project creator](https://github.com/embabel/project-creator) scripts. |
| --- | --- |

Now you have the code you need to run Embabel with LLMs from Open AI or Anthropic by using the included Maven profiles. Skip ahead to [Environment Setup](https://docs.embabel.com/embabel-agent/guide/0.3.4-SNAPSHOT/#reference.environment_setup) for API-key configuration, or detailed instructions on how to use other LLM providers.

### 2.2. Getting the Binaries

The easiest way to get started with Embabel Agent is to add the Spring Boot starter dependency to your project. Embabel release binaries are published to Maven Central.

#### 2.2.1. Build Configuration

Add the appropriate Embabel Agent Spring Boot starter to your build file depending on your choice of application type:

##### Shell Starter

Starts the application in console mode with an interactive shell powered by Embabel.

```xml
<dependency>
    <groupId>com.embabel.agent</groupId>
    <artifactId>embabel-agent-starter-shell</artifactId>
    <version>${embabel-agent.version}</version>
</dependency>
```

**Features:**

- ✅ Interactive command-line interface
- ✅ Agent discovery and registration
- ✅ Human-in-the-loop capabilities
- ✅ Progress tracking and logging
- ✅ Development-friendly error handling

##### MCP Server Starter

Starts the application with HTTP listener where agents are autodiscovered and registered as MCP servers, available for integration via SSE, Streamable-HTTP or Stateless Streamable-HTTP protocols.

```xml
<dependency>
    <groupId>com.embabel.agent</groupId>
    <artifactId>embabel-agent-starter-mcpserver</artifactId>
    <version>${embabel-agent.version}</version>
</dependency>
```

**Features:**

- ✅️ MCP protocol server implementation
- ✅️ Tool registration and discovery
- ✅️ JSON-RPC communication via SSE (Server-Sent Events), Streamable-HTTP or Stateless Streamable-HTTP
- ✅️ Integration with MCP-compatible clients
- ✅️ Security and sandboxing

##### Basic Agent Platform Starter

Initializes Embabel Agent Platform in the Spring Container. Platform beans are available via Spring Dependency Injection mechanism. Application startup mode (web, console, microservice, etc.) is determined by the Application Designer.

```xml
<dependency>
    <groupId>com.embabel.agent</groupId>
    <artifactId>embabel-agent-starter</artifactId>
    <version>${embabel-agent.version}</version>
</dependency>
```

**Features:**

- ✅️ Application decides on startup mode (console, web application, etc)
- ✅️ Agent discovery and registration
- ✅️ Agent Platform beans available via Dependency Injection mechanism
- ✅️ Progress tracking and logging
- ✅️ Development-friendly error handling

##### Embabel Snapshots

If you want to use Embabel snapshots, you’ll need to add the Embabel repository to your build.

```xml
<repositories>
    <repository>
        <id>embabel-releases</id>
        <url>https://repo.embabel.com/artifactory/libs-release</url>
        <releases>
            <enabled>true</enabled>
        </releases>
        <snapshots>
            <enabled>false</enabled>
        </snapshots>
    </repository>
    <repository>
        <id>embabel-snapshots</id>
        <url>https://repo.embabel.com/artifactory/libs-snapshot</url>
        <releases>
            <enabled>false</enabled>
        </releases>
        <snapshots>
            <enabled>true</enabled>
        </snapshots>
    </repository>
    <repository>
        <id>spring-milestones</id>
        <url>https://repo.spring.io/milestone</url>
        <snapshots>
            <enabled>false</enabled>
        </snapshots>
    </repository>
</repositories>
```

#### 2.2.2. Environment Setup

Before running your application, you’ll need to set up your environment with API keys for the LLM providers you plan to use.

Example `.env` file:

```xml
OPENAI_API_KEY=your_openai_api_key_here
ANTHROPIC_API_KEY=your_anthropic_api_key_here
GEMINI_API_KEY=your_gemini_api_key_here
MISTRAL_API_KEY=your_mistral_api_key_here
```

If you added the binaries directly to your projecdt or want to use other LLM providers than Open AI and Anthropic you will also need to add some dependencies specific to those vendors. Just follow the instructions below for your vendor(s) of choice.

##### OpenAI Compatible (GPT-4, GPT-5, etc.)

- Required:
	- `OPENAI_API_KEY`: API key for OpenAI or compatible services (e.g., Azure OpenAI, etc.)
- Optional:
	- `OPENAI_BASE_URL`: base URL of the OpenAI deployment (for Azure AI use `{resource-name}.openai.azure.com/openai`)
	- `OPENAI_COMPLETIONS_PATH`: custom path for completions endpoint (default: `/v1/completions`)
	- `OPENAI_EMBEDDINGS_PATH`: custom path for embeddings endpoint (default: `/v1/embeddings`)

Alternatively, configure via `application.yml`:

```yaml
embabel:
  agent:
    platform:
      models:
        openai:
          api-key: ${OPENAI_API_KEY:sk-dev-key}  (1)
          base-url: ${OPENAI_BASE_URL:}  (2)
```

| **1** | API key with optional default for local development |
| --- | --- |
| **2** | Optional base URL override |

If you are not using the Embabel template projects you also need to add the `embabel-agent-starter-openai` starter.

Add this to your build system as follows:

```xml
<dependency>
    <groupId>com.embabel.agent</groupId>
    <artifactId>embabel-agent-starter-openai</artifactId>
    <version>${embabel-agent.version}</version>
</dependency>
```

##### OpenAi Custom

- Required:
	- `OPENAI_CUSTOM_API_KEY`: API key for the OpenAI-compatible service
- Optional:
	- `OPENAI_CUSTOM_BASE_URL`: base URL for the OpenAI-compatible API
	- `OPENAI_CUSTOM_MODELS`: comma-separated list of custom model names to register (useful for OpenAI-compatible providers like Groq, Together AI, etc.)

When using `OPENAI_CUSTOM_MODELS`, set `EMBABEL_MODELS_DEFAULT_LLM` to specify which model to use as the default.

Example for using Groq:

```bash
export OPENAI_CUSTOM_BASE_URL="https://api.groq.com/openai/v1"
export OPENAI_CUSTOM_API_KEY="your-groq-api-key"
export OPENAI_CUSTOM_MODELS="llama-3.3-70b-versatile,mixtral-8x7b-32768"
export EMBABEL_MODELS_DEFAULT_LLM="llama-3.3-70b-versatile"
```

Alternatively, configure via `application.yml`:

```yaml
embabel:
  agent:
    platform:
      models:
        openai:
          custom:
            api-key: ${OPENAI_CUSTOM_API_KEY:your-dev-key}
            base-url: https://api.groq.com/openai/v1
            models: llama-3.3-70b-versatile,mixtral-8x7b-32768
```

You also need to add the `embabel-agent-starter-openai-custom` starter.

Add this to your build system as follows:

```xml
<dependency>
    <groupId>com.embabel.agent</groupId>
    <artifactId>embabel-agent-starter-openai-custom</artifactId>
    <version>${embabel-agent.version}</version>
</dependency>
```

##### Anthropic (Claude 3.x, etc.)

- Required:
	- `ANTHROPIC_API_KEY`: API key for Anthropic services
- Optional:
	- `ANTHROPIC_BASE_URL`: base URL for Anthropic API

Alternatively, configure via `application.yml`:

```yaml
embabel:
  agent:
    platform:
      models:
        anthropic:
          api-key: ${ANTHROPIC_API_KEY:sk-ant-dev-key}
          base-url: ${ANTHROPIC_BASE_URL:}
```

If you are not using the Embabel template projects you also need to add the `embabel-agent-starter-anthropic` starter.

Add this to your build system as follows:

```xml
<dependency>
    <groupId>com.embabel.agent</groupId>
    <artifactId>embabel-agent-starter-anthropic</artifactId>
    <version>${embabel-agent.version}</version>
</dependency>
```

##### DeepSeek

- Required:
	- `DEEPSEEK_API_KEY`: API key for DeepSeek services
- Optional:
	- `DEEPSEEK_BASE_URL`: base URL for DeepSeek API (default: `api.deepseek.com`)

Alternatively, configure via `application.yml`:

```yaml
embabel:
  agent:
    platform:
      models:
        deepseek:
          api-key: ${DEEPSEEK_API_KEY:sk-dev-key}
          base-url: ${DEEPSEEK_BASE_URL:}
```

You also need to add the `embabel-agent-starter-deepseek` starter.

Add this to your build system as follows:

```xml
<dependency>
    <groupId>com.embabel.agent</groupId>
    <artifactId>embabel-agent-starter-deepseek</artifactId>
    <version>${embabel-agent.version}</version>
</dependency>
```

##### Google Gemini (OpenAI Compatible)

Uses the OpenAI-compatible endpoint for Gemini models.

- Required:
	- `GEMINI_API_KEY`: API key for Google Gemini services
- Optional:
	- `GEMINI_BASE_URL`: base URL for Gemini API (default: `generativelanguage.googleapis.com/v1beta/openai`)

Alternatively, configure via `application.yml`:

```yaml
embabel:
  agent:
    platform:
      models:
        gemini:
          api-key: ${GEMINI_API_KEY:your-dev-key}
          base-url: ${GEMINI_BASE_URL:}
```

You also need to add the `embabel-agent-starter-gemini` starter.

Add this to your build system as follows:

```xml
<dependency>
    <groupId>com.embabel.agent</groupId>
    <artifactId>embabel-agent-starter-gemini</artifactId>
    <version>${embabel-agent.version}</version>
</dependency>
```

##### Google GenAI (Native)

Uses the native Google GenAI SDK for direct access to Gemini models with full feature support including thinking mode.

- Required (API Key authentication):
	- `GOOGLE_API_KEY`: API key for Google AI Studio
- Required (Vertex AI authentication - alternative to API key):
	- `GOOGLE_PROJECT_ID`: Google Cloud project ID
	- `GOOGLE_LOCATION`: Google Cloud region (e.g., `us-central1`)

|  | Use API key authentication for Google AI Studio, or Vertex AI authentication for Google Cloud deployments. Vertex AI authentication requires Application Default Credentials (ADC) to be configured. |
| --- | --- |

|  | Gemini 3 models are only available in the `global` location on Vertex AI. To use Gemini 3 with Vertex AI, you must set `GOOGLE_LOCATION=global`. |
| --- | --- |

To add Google GenAI support to your project add the `embabel-agent-starter-google-genai` starter.

Add this to your build system as follows:

```xml
<dependency>
    <groupId>com.embabel.agent</groupId>
    <artifactId>embabel-agent-starter-google-genai</artifactId>
    <version>${embabel-agent.version}</version>
</dependency>
```

Available LLM models include:

- `gemini-3-pro-preview` - Latest Gemini 3 Pro preview with advanced reasoning
- `gemini-2.5-pro` - High-performance model with thinking support
- `gemini-2.5-flash` - Best price-performance model
- `gemini-2.5-flash-lite` - Cost-effective high-throughput model
- `gemini-2.0-flash` - Fast and efficient
- `gemini-2.0-flash-lite` - Lightweight version

Available embedding models include:

Example configuration in `application.yml`:

```yaml
embabel:
  models:
    default-llm: gemini-2.5-flash  (1)
    default-embedding-model: gemini-embedding-001  (2)
    llms:
      fast: gemini-2.5-flash
      best: gemini-2.5-pro
      reasoning: gemini-3-pro-preview
    embedding-services:
      default: gemini-embedding-001

  agent:
    platform:
      models:
        googlegenai:  (3)
          api-key: ${GOOGLE_API_KEY}  (4)
          # Or use Vertex AI authentication:
          # project-id: ${GOOGLE_PROJECT_ID}
          # location: ${GOOGLE_LOCATION}
          max-attempts: 10
          backoff-millis: 5000
```

| **1** | Set a Google GenAI model as the default LLM |
| --- | --- |
| **2** | Set a Google GenAI embedding model as the default embedding model |
| **3** | Google GenAI specific configuration |
| **4** | API key can be set here or via environment variable `GOOGLE_API_KEY` |

##### Mistral AI

- Required:
	- `MISTRAL_API_KEY`: API key for Mistral AI services
- Optional:
	- `MISTRAL_BASE_URL`: base URL for Mistral AI API (default: `api.mistral.ai`)

Alternatively, configure via `application.yml`:

```yaml
embabel:
  agent:
    platform:
      models:
        mistralai:
          api-key: ${MISTRAL_API_KEY:your-dev-key}
          base-url: ${MISTRAL_BASE_URL:}
```

You also need to add the `embabel-agent-starter-mistral-ai` starter.

Add this to your build system as follows:

```xml
<dependency>
    <groupId>com.embabel.agent</groupId>
    <artifactId>embabel-agent-starter-mistral-ai</artifactId>
    <version>${embabel-agent.version}</version>
</dependency>
```

##### LM Studio

[LM Studio](https://lmstudio.ai/) is a desktop application that lets you easily discover, download, and run powerful LLMs on your own computer (Windows, Mac, Linux) for free, enabling offline use, local document Q&A, and even hosting an OpenAI-compatible API server for your projects, making advanced AI accessible without relying on cloud services. It supports formats like GGUF and offers privacy and control over your models.

The LM Studio [Local Server](https://lmstudio.ai/docs/developer/core/server) allows you to run an LLM API server on localhost.

To add LM Studio support, add the `embabel-agent-starter-lmstudio` starter.

Add this to your build system as follows:

```xml
<dependency>
    <groupId>com.embabel.agent</groupId>
    <artifactId>embabel-agent-starter-lmstudio</artifactId>
    <version>${embabel-agent.version}</version>
</dependency>
```

Configure an LLM API on LM Studio by following [these instructions](https://lmstudio.ai/docs/developer/core/server).

Specify the example configuration in `application.yml`. Below I have an open-ai llm and embedding model downloaded on my LLM studio and exposed via the LLM Server.

```yaml
embabel:
  agent:
    platform:
      autonomy:
        agent-confidence-cut-off: 0.8
        goal-confidence-cut-off: 0.8
      models:
        lmstudio:
          base-url: http://127.0.0.1:1234
  models:
    default-llm: openai/gpt-oss-20b
    default-embedding-model: text-embedding-nomic-embed-text-v1.5
```

##### Ollama

[Ollama](https://ollama.com/) is an open source application that lets you easily [discover](https://ollama.com/library), download, and run powerful LLMs on your own computer (Windows, Mac, Linux) for free, enabling offline use, local document Q&A, and even hosting an API server for your projects, making advanced AI accessible without relying on cloud services.

The Ollama application allows you to run an LLM API server on localhost. Exposing both its own Ollama API and an Open-AI-compatible API.

Get Ollama running locally by following [these instructions](https://docs.ollama.com/quickstart).

To use the Ollama API with your agent, add the `embabel-agent-starter-ollama` starter.

Add this to your build system as follows:

```xml
<dependency>
    <groupId>com.embabel.agent</groupId>
    <artifactId>embabel-agent-starter-ollama</artifactId>
    <version>${embabel-agent.version}</version>
</dependency>
```

Specify the example configuration in `application.yml`. Embabel uses Spring AI configuration to configure the Ollama integration.

```yaml
spring:
  ai:
    ollama:
      # Not needed when using the default port
      base-url: http://localhost:11434

embabel:
  models:
    defaultLlm: ministral-3:8b
    default-embedding-model: qwen3-embedding
```

To instead use the Open-AI-compatible API with your agent, add the `embabel-agent-starter-openai-custom` starter.

Add this to your build system as follows:

```xml
<dependency>
    <groupId>com.embabel.agent</groupId>
    <artifactId>embabel-agent-starter-openai-custom</artifactId>
    <version>${embabel-agent.version}</version>
</dependency>
```

Specify the example configuration in `application.yml`. Embabel uses Spring AI configuration to configure the Ollama integration.

```yaml
embabel:
  agent:
    platform:
      models:
        openai:
          custom:
            api-key: not-set  # Property needed but value not used by Ollama
            base-url: http://localhost:11434
            models: ministral-3:8b,qwen3-embedding
  models:
    defaultLlm: ministral-3:8b
    default-embedding-model: qwen3-embedding
```

### 2.3. Getting Embabel Running

#### 2.3.1. Running the Examples

The quickest way to get started with Embabel is to run the examples:

```bash
# Clone and run examples
git clone https://github.com/embabel/embabel-agent-examples
cd embabel-agent-examples/scripts/java
./shell.sh
```

|  | Choose the `java` or `kotlin` scripts directory depending on your preference. |
| --- | --- |

#### 2.3.2. Prerequisites

- Java 21+
- API Key from OpenAI, Anthropic, or Google
- Maven 3.9+ (optional)

Set your API keys:

```bash
export OPENAI_API_KEY="your_openai_key"
export ANTHROPIC_API_KEY="your_anthropic_key"
export GOOGLE_API_KEY="your_google_api_key"
```

|  | For Google GenAI, you can use either `GOOGLE_API_KEY` (Google AI Studio) or Vertex AI authentication with `GOOGLE_PROJECT_ID` and `GOOGLE_LOCATION`. |
| --- | --- |

#### 2.3.3. Using the Shell

Spring Shell is an easy way to interact with the Embabel agent framework, especially during development.

Type `help` to see available commands. Use `execute` or `x` to run an agent:

```bash
execute "Lynda is a Scorpio, find news for her" -p -r
```

This will look for an agent, choose the star finder agent and run the flow. `-p` will log prompts `-r` will log LLM responses. Omit these for less verbose logging.

Options:

Use the `chat` command to enter an interactive chat with the agent. It will attempt to run the most appropriate agent for each command.

|  | Spring Shell supports history. Type `!!` to repeat the last command. This will survive restarts, so is handy when iterating on an agent. |
| --- | --- |

#### 2.3.4. Example Commands

Try these commands in the shell:

```bash
# Simple horoscope agent
execute "My name is Sarah and I'm a Leo"

# Research with web tools (requires Docker Desktop with MCP extension)
execute "research the recent australian federal election. what is the position of the Greens party?"

# Fact checking
x "fact check the following: holden cars are still made in australia"
```

#### 2.3.5. Implementing Your Own Shell Commands

Particularly during development, you may want to implement your own shell commands to try agents or flows. Simply write a Spring Shell component and Spring will inject it and register it automatically.

For example, you can inject the `AgentPlatform` and use it to invoke agents directly, as in this code from the examples repository:

```java
@ShellComponent
public record SupportAgentShellCommands(
        AgentPlatform agentPlatform
) {

    @ShellMethod("Get bank support for a customer query")
    public String bankSupport(
            @ShellOption(value = "id", help = "customer id", defaultValue = "123") Long id,
            @ShellOption(value = "query", help = "customer query", defaultValue = "What's my balance, including pending amounts?") String query
    ) {
        var supportInput = new SupportInput(id, query);
        System.out.println("Support input: " + supportInput);
        var invocation = AgentInvocation
                .builder(agentPlatform)
                .options(ProcessOptions.builder().verbosity(v -> v.showPrompts(true)).build())
                .build(SupportOutput.class);
        var result = invocation.invoke(supportInput);
        return result.toString();
    }
}
```

### 2.4. Adding a Little AI to Your Application

Before we get into the magic of full-blown Embabel agents, let’s see how easy it is to add a little AI to your application using the Embabel framework. Sometimes this is all you need.

The simplest way to use Embabel is to inject an `OperationContext` and use its AI capabilities directly. This approach is consistent with standard Spring dependency injection patterns.

```java
package com.embabel.example.injection;

import com.embabel.agent.api.common.OperationContext;
import com.embabel.common.ai.model.LlmOptions;
import org.springframework.stereotype.Component;

/**
 * Demonstrate the simplest use of Embabel's AI capabilities,
 * injecting an AI helper into a Spring component.
 * The jokes will be terrible, but don't blame Embabel, blame the LLM.
 */
@Component
public record InjectedComponent(Ai ai) {

    public record Joke(String leadup, String punchline) {
    }

    public String tellJokeAbout(String topic) {
        return ai
                .withDefaultLlm()
                .generateText("Tell me a joke about " + topic);
    }

    public Joke createJokeObjectAbout(String topic1, String topic2, String voice) {
        return ai
                .withLlm(LlmOptions.withDefaultLlm().withTemperature(.8))
                .createObject("""
                                Tell me a joke about %s and %s.
                                The voice of the joke should be %s.
                                The joke should have a leadup and a punchline.
                                """.formatted(topic1, topic2, voice),
                        Joke.class);
    }

}
```

This example demonstrates several key aspects of Embabel’s design philosophy:

- **Standard Spring Integration**: The `Ai` object is injected like any other Spring dependency using constructor injection
- **Simple API**: Access AI capabilities through the `Ai` interface directly or `OperationContext.ai()`, which can also be injected in the same way
- **Flexible Configuration**: Configure LLM options like temperature on a per-call basis
- **Type Safety**: Generate structured objects directly with `createObject()` method
- **Consistent Patterns**: Works exactly like you’d expect any Spring component to work

The `Ai` type provides access to all of Embabel’s AI capabilities without requiring a full agent setup, making it perfect for adding AI features to existing applications incrementally.

|  | The `Ai` and OperationContext\` APIs are used throughout Embabel applications, as a convenient gateway to key AI and other functionality. |
| --- | --- |

### 2.5. Writing Your First Agent

The easiest way to create your first agent is to use the [Java or Kotlin template repositories](https://docs.embabel.com/embabel-agent/guide/0.3.4-SNAPSHOT/#getting-started.quickstart).

#### 2.5.1. Example: WriteAndReviewAgent

The template includes a `WriteAndReviewAgent` that demonstrates key concepts:

```java
@Agent(description = "Agent that writes and reviews stories")
public class WriteAndReviewAgent {

    @Action
    public Story writeStory(UserInput userInput, OperationContext context) {
        return context.ai()
            .withAutoLlm()
            .createObject("""
                You are a creative writer who aims to delight and surprise.
                Write a story about %s
                """.formatted(userInput.getContent()),
            Story.class);
    }

    @AchievesGoal(description = "Review a story")
    @Action
    public ReviewedStory reviewStory(Story story, OperationContext context) {
        return context.ai()
            .withLlmByRole("reviewer")
            .createObject("""
                You are a meticulous editor.
                Carefully review this story:
                %s
            """.formatted(story.text),
            ReviewedStory.class);
    }
}
```

#### 2.5.2. Key Concepts Demonstrated

**Multiple LLMs with Different Configurations:**

- Writer LLM uses high temperature (0.8) for creativity
- Reviewer LLM uses low temperature (0.2) for analytical review
- Different personas guide the model behavior

**Actions and Goals:**

- `@Action` methods are the steps the agent can take
- `@AchievesGoal` marks the final action that completes the agent’s work

**Domain Objects:**

- `Story` and `ReviewedStory` are strongly-typed domain objects
- Help structure the interaction between actions

#### 2.5.3. Running Your Agent

Set your API keys and run the shell:

```bash
export OPENAI_API_KEY="your_key_here"
./scripts/shell.sh
```

In the shell, try:

```bash
x "Tell me a story about a robot learning to paint"
```

The agent will:

1. Generate a creative story using the writer LLM
2. Review and improve it using the reviewer LLM
3. Return the final reviewed story

- Explore the [examples repository](https://github.com/embabel/embabel-agent-examples) for more complex agents
- Read the [Reference Documentation](https://docs.embabel.com/embabel-agent/guide/0.3.4-SNAPSHOT/#reference.reference) for detailed API information
- Try building your own domain-specific agents

### 2.6. Embabel Modules

Embabel spans multiple modules, in this and other repositories in the `embabel` organization.

The status of these modules varies. There are three statuses:

- **Stable**: these modules are considered production ready. We strive to avoid breaking changes.
- **Incubating**: these modules are under active development and may have breaking changes in minor releases. However, they are considered generally usable and can be expected to graduate to stable. Use with caution.
- **Experimental**: these modules are early stage and may have breaking changes in any release. They are not recommended for production use. These modules may be removed without replacement and there is no guarantee of them graduating to a more stable status.

Of course, contributions are welcome to all modules!

#### 2.6.1. Module Directory

The following are modules intended for direct use (versus supporting infrastructure).

##### Core Modules

| Name | Location | Purpose | Notes | Status |
| --- | --- | --- | --- | --- |
| `embabel-agent-api` | This repo | Core API | Main programming interface for building agents | Stable |
| `embabel-agent-domain` | This repo | Domain types and entities | Shared domain model | Incubating |

##### Feature Modules

| Name | Location | Purpose | Notes | Status |
| --- | --- | --- | --- | --- |
| `embabel-agent-a2a` | This repo | Agent-to-Agent protocol support | Google A2A protocol implementation | Incubating |
| `embabel-agent-code` | This repo | Coding domain library | Code analysis and generation utilities | Stable |
| `embabel-agent-discord` | This repo | Discord bot integration | Build agents as Discord bots | Experimental |
| `embabel-agent-eval` | This repo | Agent evaluation framework | Assess agent performance on tasks | Experimental |
| `embabel-agent-mcpserver` | This repo | MCP server support | Export agents as MCP servers | Stable |
| `embabel-agent-openai` | This repo | OpenAI-specific utilities | Structured outputs, response format | Stable |
| `embabel-agent-remote` | This repo | Remote action support | Execute actions on remote systems, enabling dynamic registration to extend the capabilities of an Embabel server | Experimental |
| `embabel-agent-shell` | This repo | Command-line interface | Interactive shell for agent development | Stable |
| `embabel-agent-skills` | This repo | Support for emerging Agent Skills standard | Composable agent skills | Experimental |
| `embabel-agent-spec` | This repo | Serializable action and goal definitions | Enables agents to be defined in YML or otherwise persisted in a serialized format | Experimental |

##### RAG and Context Engineering Modules

| Name | Location | Purpose | Notes | Status |
| --- | --- | --- | --- | --- |
| `embabel-agent-rag-core` | This repo | Core RAG abstractions | Base interfaces for RAG, encompassing programming model (`ToolishRag`), storage abstractions (`SearchOperations`) and document model. | Stable |
| `embabel-agent-rag-lucene` | This repo | Lucene RAG store | Local storage with Apache Lucene supporting vector and text search | Stable |
| `embabel-agent-rag-tika` | This repo | Apache Tika integration | Document parsing (Markdown, PDF, Word, etc.) | Incubating |
| `embabel-agent-rag-neo-drivine` | `embabel/embabel-agent-rag-neo-drivine` | Neo4j graph RAG | RAG store for Neo4j graph database | Incubating |
| `embabel-rag-pgvector` | `embabel/embabel-rag-pgvector` | PostgreSQL pgvector RAG | RAG store for PostgreSQL with pgvector extension supporting hybrid search (vector, full-text, fuzzy) | Incubating |
| `dice` | `embabel/dice` | Support for [Domain Oriented Context Engineering](https://medium.com/@springrod/context-engineering-needs-domain-understanding-b4387e8e4bf8:) | Sophisticated pipeline for context engineering and integration with enterprise data. Incorporates proposition extraction and projection into knowledge graphs, memory and experimental representations. | Incubating |

##### Spring Boot Starters

| Name | Location | Purpose | Notes | Status |
| --- | --- | --- | --- | --- |
| `embabel-agent-starter` | This repo | Base starter | Core dependencies only (no LLM provider) | Stable |
| `embabel-agent-starter-anthropic` | This repo | Anthropic starter | Quick start with Claude | Stable |
| `embabel-agent-starter-openai` | This repo | OpenAI starter | Quick start with GPT | Stable |
| `embabel-agent-starter-ollama` | This repo | Ollama starter | Quick start with local Ollama | Stable |
| `embabel-agent-starter-shell` | This repo | Shell starter | Add interactive shell for development | Stable |
| `embabel-agent-starter-a2a` | This repo | A2A starter | Add A2A server support | Incubating |
| `embabel-agent-starter-mcpserver` | This repo | MCP server starter | Add MCP server support | Stable |
| `embabel-agent-starter-bedrock` | This repo | Bedrock starter | Quick start with AWS Bedrock | Stable |
| `embabel-agent-starter-deepseek` | This repo | DeepSeek starter | Quick start with DeepSeek | Stable |
| `embabel-agent-starter-gemini` | This repo | Gemini starter | Quick start with Vertex AI | Stable |
| `embabel-agent-starter-google-genai` | This repo | Google GenAI starter | Quick start with AI Studio | Incubating |
| `embabel-agent-starter-lmstudio` | This repo | LM Studio starter | Quick start with LM Studio | Incubating |
| `embabel-agent-starter-mistral-ai` | This repo | Mistral AI starter | Quick start with Mistral | Stable |
| `embabel-agent-starter-dockermodels` | This repo | Docker Models starter | Quick start with Docker Desktop AI | Stable |
| `embabel-agent-starter-openai-custom` | This repo | Custom OpenAI starter | Quick start with OpenRouter, etc. | Stable |

##### Test Support

| Name | Location | Purpose | Notes | Status |
| --- | --- | --- | --- | --- |
| `embabel-agent-test` | This repo | Test utilities | JUnit extensions, test DSL | Incubating |

##### Example Repositories

| Name | Location | Purpose | Notes | Status |
| --- | --- | --- | --- | --- |
| `embabel-agent-examples` | `embabel/embabel-agent-examples` | Example agents | Sample implementations and tutorials | Stable |
| `java-agent-template` | `embabel/java-agent-template` | Java project template | Starter template for Java agents | Stable |

#### 2.6.2. Experimental APIs

While the status of modules may change over time, any module may contain clearly identified experimental functionality. This enables us to innovate in the open without excessive build complexity.

Please try and provide feedback on this functionality, but don’t rely on it and be aware that it may change without notice.

|  | Any type or method annotated with the `@ApiStatus.Experimental` annotation is not guaranteed to be stable. |
| --- | --- |

## 3\. Reference

### 3.1. Invoking an Agent

Agents can be invoked programmatically or via user input.

See [Invoking Embabel Agents](https://docs.embabel.com/embabel-agent/guide/0.3.4-SNAPSHOT/#reference.invoking) for details on programmatic invocation. Programmatic invocation typically involves structured types other than user input.

In the case of user input, an LLM will choose the appropriate agent via the `Autonomy` class. Behavior varies depending on configuration:

- In closed mode, the LLM will select the agent based on the user input and the available agents in the system.
- In open mode, the LLM will select the goal based on the user input and then assemble an agent that can achieve that goal from the present world state.

### 3.2. Agent Process Flow

When an agent is invoked, Embabel creates an `AgentProcess` with a unique identifier that manages the complete execution lifecycle.

#### 3.2.1. AgentProcess Lifecycle

An `AgentProcess` maintains state throughout its execution and can transition between various states:

**Process States:**

- `NOT_STARTED`: The process has not started yet
- `RUNNING`: The process is executing without any known problems
- `COMPLETED`: The process has completed successfully
- `FAILED`: The process has failed and cannot continue
- `TERMINATED`: The process was killed by an early termination policy
- `KILLED`: The process was killed by the user or platform
- `STUCK`: The process cannot formulate a plan to progress (may be temporary)
- `WAITING`: The process is waiting for user input or external event
- `PAUSED`: The process has paused due to scheduling policy

**Process Execution Methods:**

- `tick()`: Perform the next single step and return when an action completes
- `run()`: Execute the process as far as possible until completion, failure, or a waiting state

These methods are not directly called by user code, but are managed by the framework to control execution flow.

Each `AgentProcess` maintains:

- **Unique ID**: Persistent identifier for tracking and reference
- **History**: Record of all executed actions with timing information
- **Goal**: The objective the process is trying to achieve
- **Failure Info**: Details about any failure that occurred
- **Parent ID**: Reference to parent process for nested executions

#### 3.2.2. Planning

Planning occurs after each action execution using Goal-Oriented Action Planning (GOAP). The planning process:

1. **Analyze Current State**: Examine the current blackboard contents and world state
2. **Identify Available Actions**: Find all actions that can be executed based on their preconditions
3. **Search for Action Sequences**: Use A\* algorithm to find optimal paths to achieve the goal
4. **Select Optimal Plan**: Choose the best action sequence based on cost and success probability
5. **Execute Next Action**: Run the first action in the plan and replan

This creates a dynamic **OODA loop** (Observe-Orient-Decide-Act): - **Observe**: Check current blackboard state and action results - **Orient**: Understand what has changed since the last planning cycle - **Decide**: Formulate or update the plan based on new information - **Act**: Execute the next planned action

The replanning approach allows agents to:

- Adapt to unexpected action results
- Handle dynamic environments where conditions change
- Recover from partial failures
- Take advantage of new opportunities that arise

#### 3.2.3. Blackboard

The Blackboard serves as the shared memory system that maintains state throughout the agent process execution. It implements the [Blackboard architectural pattern](https://en.wikipedia.org/wiki/Blackboard_\(design_pattern\)), a knowledge-based system approach.

Most of the time, user code doesn’t need to interact with the blackboard directly, as it is managed by the framework. For example, action inputs come from the blackboard, and action outputs are automatically added to the blackboard, and conditions are evaluated based on its contents.

**Key Characteristics:**

- **Central Repository**: Stores all domain objects, intermediate results, and process state
- **Type-Based Access**: Objects are indexed and retrieved by their types
- **Ordered Storage**: Objects maintain the order they were added, with latest being default
- **Immutable Objects**: Once added, objects cannot be modified (new versions can be added)
- **Condition Tracking**: Maintains boolean conditions used by the planning system

**Core Operations:**

```java
// Add objects to blackboard (1)
blackboard.add(person);
blackboard.set("result", analysis);

// Retrieve objects by type
Person person = blackboard.last(Person.class);
List<Person> allPersons = blackboard.all(Person.class);

// Check conditions
blackboard.setCondition("userVerified", true);
boolean verified = blackboard.getCondition("userVerified"); (2)

// Hide an object
blackboard.hide(somethingWeDontWantToPlanOnLater); (3)
```

| **1** | **Adding Objects**: Objects are added to the blackboard automatically when returned from action methods, so you don’t typically need to call this API. They can also be added manually using the `+=` operator (Kotlin only) or `add` / `set` method with an optional key. |
| --- | --- |
| **2** | **Conditions**: Conditions are normally calculated in `@Condition` methods, so you don’t usually need to check or set them via the API. |
| **3** | **Hiding Objects**: Prevents an object from being considered in future planning cycles. For example, the object might be a command that we have handled. It will remain in the blackboard history but will not be available to planning or via the Blackboard API. |

**Data Flow:**

1. **Input Processing**: Initial user input is added to the blackboard
2. **Action Execution**: Each action reads inputs from blackboard and adds results
3. **State Evolution**: Blackboard accumulates objects representing the evolving state
4. **Planning Input**: Current blackboard state informs the next planning cycle
5. **Result Extraction**: Final results are retrieved from blackboard upon completion

The blackboard enables:

- **Loose Coupling**: Actions don’t need direct references to each other
- **Flexible Data Flow**: Actions can consume any available data of the right type
- **State Persistence**: Complete execution history is maintained
- **Debugging Support**: Full visibility into state evolution for troubleshooting

#### 3.2.4. Binding

By default, items in the blackboard are matched by type. When there are multiple candidates of the same type, the most recently added one is provided. It is also possible to assign a specific name to blackboard items.

An example of explicit binding in an action method:

```java
@Action
public Person extractPerson(UserInput userInput, OperationContext context) {
    PersonImpl maybeAPerson = context.promptRunner().withLlm(LlmOptions.fromModel(OpenAiModels.GPT_41)).createObjectIfPossible(
            """
            Create a person from this user input, extracting their name:
            %s""".formatted(userInput.getContent()),
            PersonImpl.class
    );
    if (maybeAPerson != null) {
        context.bind("user", maybeAPerson); (1)
    }
    return maybeAPerson;
}
```

| **1** | Explicit binding to the blackboard. Not usually necessary as action method return values are automatically bound. |
| --- | --- |

The following example requires a `Thing` named `thingOne` to be present in the blackboard:

```java
@Action
public Whatever doWithThing(
        @RequireNameMatch Thing thingOne) { (1)
```

| **1** | The `@RequireNameMatch` annotation on the parameter specifies that the parameter should be matched by both type and name. Multiple parameters can be so annotated. |
| --- | --- |

The following example uses `@Action.outputBinding` to cause a `thingOne` to be bound in the blackboard, satisfying the previous example:

```java
@Action(outputBinding = "thingOne")
public Thing bindThing1() { ...
```

|  | When routing flows by type, the name is not important, but for reference the default name is 'it'. |
| --- | --- |

#### 3.2.5. Context

Embabel offers a way to store longer term state: the `com.embabel.agent.core.Context`. While a blackboard is tied to a specific agent process, a context can persist across multiple processes.

Contexts are identified by a unique `contextId` string. When starting an agent process, you can specify a `contextId` in the `ProcessOptions`. This will populate that process’s blackboard with any data stored in the specified context.

|  | Context persistence is dependent on the implementation of `com.embabel.agent.spi.ContextRepository`. The default implementation works only in memory, so does not survive server restarts. |
| --- | --- |

### 3.3. Goals, Actions and Conditions

### 3.4. Domain Objects

Domain objects in Embabel are not just strongly-typed data structures - they are real objects with behavior that can be selectively exposed to LLMs and used in agent actions.

#### 3.4.1. Objects with Behavior

Unlike simple structs or DTOs, Embabel domain objects can encapsulate business logic and expose it to LLMs through the `@Tool` annotation. For example:

```java
@Entity
public class Customer {
    private String name;
    private LoyaltyLevel loyaltyLevel;
    private List<Order> orders;

    @Tool(description = "Calculate the customer's loyalty discount percentage") (1)
    public BigDecimal getLoyaltyDiscount() {
        return loyaltyLevel.calculateDiscount(orders.size());
    }

    @Tool(description = "Check if customer is eligible for premium service")
    public boolean isPremiumEligible() {
        return orders.stream()
            .mapToDouble(Order::getTotal)
            .sum() > 1000.0;
    }

    public void updateLoyaltyLevel() { (2)
        // Internal business logic
    }
}
```

| **1** | The `@Tool` annotation exposes this method to LLMs when the object is added via `PrompRunner.withToolObject()`. |
| --- | --- |
| **2** | Unannotated methods such as `updateLoyaltyLevel` are never exposed to LLMs, regardless of their visibility level. This ensures that tool exposure is safe, explicit and controlled. |

#### 3.4.2. Selective Tool Exposure

The `@Tool` annotation allows you to selectively expose domain object methods to LLMs. For example:

- **Business Logic**: Expose methods that provide *safely invocable* business value to the LLM
- **Calculated Properties**: Methods that compute derived values. This can help LLMs with calculations they might otherwise get wrong.
- **Business Rules**: Methods that implement domain-specific rules

|  | Always keep internal implementation details hidden, and think carefully before exposing methods that mutate state or have side effects. |
| --- | --- |

#### 3.4.3. Use of Domain Objects in Actions

Domain objects can be used naturally in action methods, combining LLM interactions with traditional object-oriented programming. The availability of the domain object instances also drives Embabel planning.

```java
@Action
public Recommendation generateRecommendation(Customer customer, OperationContext context) {
    var prompt = String.format(
        "Generate a personalized recommendation for %s based on their profile",
        customer.getName()
    );

    return context.ai()
        .withToolObject(customer) (1)
        .withDefaultLlm()
        .createObject(prompt, Recommendation.class);
}
```

| **1** | The `Customer` domain object is provided as a tool object, allowing the LLM to call its `@Tool` methods. The LLM has access to `customer.getLoyaltyDiscount()` and `customer.isPremiumEligible()`. |
| --- | --- |

|  | Domain object methods, even if annotated, will not be exposed to LLMs unless explicitly added via `withToolObject()`. |
| --- | --- |

#### 3.4.4. Domain Understanding is Critical

As outlined in [Context Engineering Needs Domain Understanding](https://medium.com/@springrod/context-engineering-needs-domain-understanding-b4387e8e4bf8), Rod Johnson’s blog introducing DICE (Domain-Integrated Context Engineering), domain understanding is fundamental to effective context engineering. Domain objects serve as the bridge between:

- **Business Domain**: Real-world entities and their relationships
- **Agent Behavior**: How LLMs understand and interact with the domain
- **Code Actions**: Traditional programming logic that operates on domain objects

#### 3.4.5. Benefits

- **Rich Context**: LLMs receive both data structure and behavioral context
- **Encapsulation**: Business logic stays within domain objects where it belongs
- **Reusability**: Domain objects can be used across multiple agents
- **Testability**: Domain logic can be unit tested independently
- **Evolution**: Adding new tools to domain objects extends agent capabilities

This approach ensures that agents work with meaningful business entities rather than generic data structures, leading to more natural and effective AI interactions.

### 3.5. Configuration

#### 3.5.1. Enabling Embabel

Annotate your Spring Boot application class to get agentic behavior.

Example:

```java
@SpringBootApplication
public class MyAgentApplication {
    public static void main(String[] args) {
        SpringApplication.run(MyAgentApplication.class, args);
    }
}
```

This is a normal Spring Boot application class. You can add other Spring Boot annotations as needed.

You also need to add the [dependency and configuration for your LLM provider(s) of choice](https://docs.embabel.com/embabel-agent/guide/0.3.4-SNAPSHOT/#reference.environment_setup).

#### 3.5.2. Configuration Properties

The following table lists all available configuration properties in Embabel Agent Platform. Properties are organized by their configuration prefix and include default values where applicable. They can be set via `application.properties`, `application.yml`, profile-specific configuration files or environment variables, as per standard Spring configuration practices.

##### Setting default LLM and roles

From `ConfigurableModelProviderProperties` - configuration for default LLMs and role-based model selection.

| Property | Type | Default | Description |
| --- | --- | --- | --- |
| `embabel.models.default-llm` | String | `gpt-4.1-mini` | Default LLM name. It’s good practice to override this in configuration. |
| `embabel.models.default-embedding-model` | String | `null` | Default embedding model name. Need not be set, in which case it defaults to null. |
| `embabel.models.llms` | Map<String, String> | `{}` | Map of role to LLM name. Each entry will require an LLM to be registered with the same name. May not include the default LLM. |
| `embabel.models.embedding-services` | Map<String, String> | `{}` | Map of role to embedding service name. Does not need to include the default embedding service. You can create as many roles as you wish. |

Role-based model selection allows you to assign specific LLMs or embedding services to different roles within your application. For example:

```yaml
embabel:
  models:
    default-llm: gpt-4o-mini
    default-embedding-model: text-embedding-3-small
    llms:
      cheapest: gpt-4o-mini
      best: gpt-4o
      reasoning: o1-preview
    embedding-services:
      fast: text-embedding-3-small
      accurate: text-embedding-3-large
```

It’s good practice to decouple your code from specific models in this way.

##### Platform Configuration

From `AgentPlatformProperties` - unified configuration for all agent platform properties.

| Property | Type | Default | Description |
| --- | --- | --- | --- |
| `embabel.agent.platform.name` | String | `embabel-default` | Core platform identity name |
| `embabel.agent.platform.description` | String | `Embabel Default Agent Platform` | Platform description |

##### Logging Personality

Configuration for agent logging output style and theming.

| Property | Type | Default | Description |
| --- | --- | --- | --- |
| `embabel.agent.logging.personality` | String | *(none)* | Themed logging messages to add personality to agent output |

| Value | Description |
| --- | --- |
| `starwars` | Star Wars themed logging messages |
| `severance` | Severance themed logging messages. Praise Kier |
| `colossus` | Colossus: The Forbin Project themed messages |
| `hitchhiker` | Hitchhiker’s Guide to the Galaxy themed messages |
| `montypython` | Monty Python themed logging messages |

```yaml
embabel:
  agent:
    logging:
      personality: hitchhiker
```

##### Agent Scanning

From `AgentPlatformProperties.ScanningConfig` - configures scanning of the classpath for agents.

| Property | Type | Default | Description |
| --- | --- | --- | --- |
| `embabel.agent.platform.scanning.annotation` | Boolean | `true` | Whether to auto register beans with @Agent and @Agentic annotation |
| `embabel.agent.platform.scanning.bean` | Boolean | `false` | Whether to auto register as agents Spring beans of type `Agent` |

##### Ranking Configuration

From `AgentPlatformProperties.RankingConfig` - configures ranking of agents and goals based on user input when the platform should choose the agent or goal.

| Property | Type | Default | Description |
| --- | --- | --- | --- |
| `embabel.agent.platform.ranking.llm` | String | `null` | Name of the LLM to use for ranking, or null to use auto selection |
| `embabel.agent.platform.ranking.max-attempts` | Int | `5` | Maximum number of attempts to retry ranking |
| `embabel.agent.platform.ranking.backoff-millis` | Long | `100` | Initial backoff time in milliseconds |
| `embabel.agent.platform.ranking.backoff-multiplier` | Double | `5.0` | Multiplier for backoff time |
| `embabel.agent.platform.ranking.backoff-max-interval` | Long | `180000` | Maximum backoff time in milliseconds |

##### LLM Operations

From `AgentPlatformProperties.LlmOperationsConfig` - configuration for LLM operations including prompts and data binding.

| Property | Type | Default | Description |
| --- | --- | --- | --- |
| `embabel.agent.platform.llm-operations.prompts.maybe-prompt-template` | String | `maybe_prompt_contribution` | Template for "maybe" prompt, enabling failure result when LLM lacks information |
| `embabel.agent.platform.llm-operations.prompts.generate-examples-by-default` | Boolean | `true` | Whether to generate examples by default |
| `embabel.agent.platform.llm-operations.data-binding.max-attempts` | Int | `10` | Maximum retry attempts for data binding |
| `embabel.agent.platform.llm-operations.data-binding.fixed-backoff-millis` | Long | `30` | Fixed backoff time in milliseconds between retries |

##### Process ID Generation

From `AgentPlatformProperties.ProcessIdGenerationConfig` - configuration for process ID generation.

| Property | Type | Default | Description |
| --- | --- | --- | --- |
| `embabel.agent.platform.process-id-generation.include-version` | Boolean | `false` | Whether to include version in process ID generation |
| `embabel.agent.platform.process-id-generation.include-agent-name` | Boolean | `false` | Whether to include agent name in process ID generation |

##### Autonomy Configuration

From `AgentPlatformProperties.AutonomyConfig` - configures thresholds for agent and goal selection. Certainty below thresholds will result in failure to choose an agent or goal.

| Property | Type | Default | Description |
| --- | --- | --- | --- |
| `embabel.agent.platform.autonomy.agent-confidence-cut-off` | Double | `0.6` | Confidence threshold for agent operations |
| `embabel.agent.platform.autonomy.goal-confidence-cut-off` | Double | `0.6` | Confidence threshold for goal achievement |

##### Model Provider Configuration

From `AgentPlatformProperties.ModelsConfig` - model provider integration configurations.

###### Anthropic

| Property | Type | Default | Description |
| --- | --- | --- | --- |
| `embabel.agent.platform.models.anthropic.max-attempts` | Int | `10` | Maximum retry attempts |
| `embabel.agent.platform.models.anthropic.backoff-millis` | Long | `5000` | Initial backoff time in milliseconds |
| `embabel.agent.platform.models.anthropic.backoff-multiplier` | Double | `5.0` | Backoff multiplier |
| `embabel.agent.platform.models.anthropic.backoff-max-interval` | Long | `180000` | Maximum backoff interval in milliseconds |

###### OpenAI

| Property | Type | Default | Description |
| --- | --- | --- | --- |
| `embabel.agent.platform.models.openai.max-attempts` | Int | `10` | Maximum retry attempts |
| `embabel.agent.platform.models.openai.backoff-millis` | Long | `5000` | Initial backoff time in milliseconds |
| `embabel.agent.platform.models.openai.backoff-multiplier` | Double | `5.0` | Backoff multiplier |
| `embabel.agent.platform.models.openai.backoff-max-interval` | Long | `180000` | Maximum backoff interval in milliseconds |

###### Google GenAI (Native)

Uses the native Google GenAI SDK (`spring-ai-google-genai`) for direct access to Gemini models with full feature support.

| Property | Type | Default | Description |
| --- | --- | --- | --- |
| `embabel.agent.platform.models.googlegenai.max-attempts` | Int | `10` | Maximum retry attempts |
| `embabel.agent.platform.models.googlegenai.backoff-millis` | Long | `5000` | Initial backoff time in milliseconds |
| `embabel.agent.platform.models.googlegenai.backoff-multiplier` | Double | `5.0` | Backoff multiplier |
| `embabel.agent.platform.models.googlegenai.backoff-max-interval` | Long | `180000` | Maximum backoff interval in milliseconds |

Google GenAI models (both LLM and embedding) are configured via the `embabel-agent-starter-google-genai` starter dependency.

The following embedding models are available:

| Model Name | Model ID | Dimensions | Price (per 1M tokens) |
| --- | --- | --- | --- |
| `gemini_embedding_001` | `gemini-embedding-001` | 3072 | $0.15 |

The following environment variables control authentication:

| Environment Variable | Description |
| --- | --- |
| `GOOGLE_API_KEY` | API key for Google AI Studio authentication |
| `GOOGLE_PROJECT_ID` | Google Cloud project ID (for Vertex AI authentication) |
| `GOOGLE_LOCATION` | Google Cloud region, e.g., `us-central1` (for Vertex AI authentication) |

|  | Either `GOOGLE_API_KEY` or both `GOOGLE_PROJECT_ID` and `GOOGLE_LOCATION` must be set. |
| --- | --- |

|  | Gemini 3 models are only available in the `global` location on Vertex AI. To use Gemini 3 with Vertex AI, you must set `GOOGLE_LOCATION=global`. |
| --- | --- |

To add new Google GenAI embedding models, edit the configuration file:

```java
embabel-agent-autoconfigure/models/embabel-agent-google-genai-autoconfigure/
  src/main/resources/models/google-genai-models.yml
```

```yaml
embedding_models:
  - name: "gemini_embedding_001"
    model_id: "gemini-embedding-001"
    display_name: "Gemini Embedding 001"
    dimensions: 3072
    pricing_model:
      usd_per1m_tokens: 0.15
```

##### HTTP Client Configuration

From `NettyClientFactoryProperties` - configuration for the HTTP client used by model providers (OpenAI, Anthropic, etc.) when making API calls.

Embabel uses Reactor Netty as the HTTP client for improved performance and non-blocking I/O. This is particularly important for LLM API calls which can have long response times.

###### Dependency Requirement

To use the Netty client, you must manually add the following autoconfiguration dependency to your project:

```xml
<dependency>
    <groupId>com.embabel.agent</groupId>
    <artifactId>embabel-agent-netty-client-autoconfigure</artifactId>
</dependency>
```

For Gradle:

```gradle
implementation 'com.embabel.agent:embabel-agent-netty-client-autoconfigure'
```

| Property | Type | Default | Description |
| --- | --- | --- | --- |
| `embabel.agent.platform.http-client.connect-timeout` | Duration | `25s` | Connection timeout for establishing HTTP connections to model providers |
| `embabel.agent.platform.http-client.read-timeout` | Duration | `1m` | Read timeout (response timeout) for receiving responses from model providers. Increase this value for models that generate long responses or when using extended thinking features. |

```yaml
embabel:
  agent:
    platform:
      http-client:
        connect-timeout: 10s
        read-timeout: 10m
```

|  | For models with extended thinking enabled (like Claude with thinking mode), consider increasing `read-timeout` to `10m` or higher to accommodate longer processing times. |
| --- | --- |

###### When to Adjust Timeouts

- **Long-running LLM calls**: If you experience timeout errors during complex reasoning tasks, increase `read-timeout`
- **Slow network environments**: Increase `connect-timeout` if connection establishment is failing
- **Streaming responses**: The `read-timeout` applies to the initial response; streaming content has its own handling

|  | The HTTP client configuration applies to all model providers that use Spring’s `RestClient` and `WebClient`, including OpenAI, Anthropic, and OpenAI-compatible endpoints. |
| --- | --- |

##### Server-Sent Events

From `AgentPlatformProperties.SseConfig` - server-sent events configuration.

| Property | Type | Default | Description |
| --- | --- | --- | --- |
| `embabel.agent.platform.sse.max-buffer-size` | Int | `100` | Maximum buffer size for SSE |
| `embabel.agent.platform.sse.max-process-buffers` | Int | `1000` | Maximum number of process buffers |

##### Test Configuration

From `AgentPlatformProperties.TestConfig` - test configuration.

| Property | Type | Default | Description |
| --- | --- | --- | --- |
| `embabel.agent.platform.test.mock-mode` | Boolean | `true` | Whether to enable mock mode for testing |

##### Process Repository Configuration

From `ProcessRepositoryProperties` - configuration for the agent process repository.

| Property | Type | Default | Description |
| --- | --- | --- | --- |
| `embabel.agent.platform.process-repository.window-size` | Int | `1000` | Maximum number of agent processes to keep in memory when using default `InMemoryAgentProcessRepository`. When exceeded, oldest processes are evicted. |

##### Standalone LLM Configuration

###### LLM Operations Prompts

From `LlmOperationsPromptsProperties` - properties for ChatClientLlmOperations operations.

| Property | Type | Default | Description |
| --- | --- | --- | --- |
| `embabel.llm-operations.prompts.maybe-prompt-template` | String | `maybe_prompt_contribution` | Template to use for the "maybe" prompt, which can enable a failure result if the LLM does not have enough information to create the desired output structure |
| `embabel.llm-operations.prompts.generate-examples-by-default` | Boolean | `true` | Whether to generate examples by default |
| `embabel.llm-operations.prompts.default-timeout` | Duration | `60s` | Default timeout for operations |

###### LLM Data Binding

From `LlmDataBindingProperties` - data binding properties with retry configuration for LLM operations.

| Property | Type | Default | Description |
| --- | --- | --- | --- |
| `embabel.llm-operations.data-binding.max-attempts` | Int | `10` | Maximum retry attempts for data binding |
| `embabel.llm-operations.data-binding.fixed-backoff-millis` | Long | `30` | Fixed backoff time in milliseconds between retries |

##### Additional Model Providers

###### AWS Bedrock

From `BedrockProperties` - AWS Bedrock model configuration properties.

| Property | Type | Default | Description |
| --- | --- | --- | --- |
| `embabel.models.bedrock.models` | List | `[]` | List of Bedrock models to configure |
| `embabel.models.bedrock.models[].name` | String | `""` | Model name |
| `embabel.models.bedrock.models[].knowledge-cutoff` | String | `""` | Knowledge cutoff date |
| `embabel.models.bedrock.models[].input-price` | Double | `0.0` | Input token price |
| `embabel.models.bedrock.models[].output-price` | Double | `0.0` | Output token price |

###### Docker Local Models

From `DockerProperties` - configuration for Docker local models (OpenAI-compatible).

| Property | Type | Default | Description |
| --- | --- | --- | --- |
| `embabel.docker.models.base-url` | String | `localhost:12434/engines` | Base URL for Docker model endpoint |
| `embabel.docker.models.max-attempts` | Int | `10` | Maximum retry attempts |
| `embabel.docker.models.backoff-millis` | Long | `2000` | Initial backoff time in milliseconds |
| `embabel.docker.models.backoff-multiplier` | Double | `5.0` | Backoff multiplier |
| `embabel.docker.models.backoff-max-interval` | Long | `180000` | Maximum backoff interval in milliseconds |

### 3.6. Annotation model

Embabel provides a Spring-style annotation model to define agents, actions, goals, and conditions. This is the recommended model to use in Java, and remains compelling in Kotlin.

#### 3.6.1. The @Agent annotation

This annotation is used on a class to define an agent. It is a Spring stereotype annotation, so it triggers Spring component scanning. Your agent class will automatically be registered as a Spring bean. It will also be registered with the agent framework, so it can be used in agent processes.

You must provide the `description` parameter, which is a human-readable description of the agent. This is particularly important as it may be used by the LLM in agent selection.

#### 3.6.2. The @EmbabelComponent annotation

This annotation is used on a class to indicate that this class exposes actions, goals and conditions that may be used by agents, but is not an agent in itself. It is a Spring stereotype annotation, so it triggers Spring component scanning. Your Embabel component class will automatically be registered as a Spring bean. It will also be registered with the agent framework, so its actions, goals and conditions can be used in agent processes.

Embabel Components are most useful in combination with the [Utility AI planner](https://docs.embabel.com/embabel-agent/guide/0.3.4-SNAPSHOT/#reference.planners__utility) that selects the most valuable next action among all available actions.

#### 3.6.3. The @Action annotation

The `@Action` annotation is used to mark methods that perform actions within an agent.

Action metadata can be specified on the annotation, including:

- `description`: A human-readable description of the action.
- `pre`: A list of preconditions *additional to the input types* that must be satisfied before the action can be executed.
- `post`: A list of postconditions *additional to the output type(s)* that may be satisfied after the action is executed.
- `canRerun`: A boolean indicating whether the action can be rerun if it has already been executed. Defaults to false.
- `readOnly`: A boolean indicating whether the action has no external side effects. Read-only actions only analyze data and produce derived objects without modifying external systems (APIs, databases, files, etc.). This is useful for learning/catchup modes where you want to ingest and understand data without triggering mutations. Defaults to false.
- `clearBlackboard`: A boolean indicating whether to clear the blackboard after this action completes. When true, all objects on the blackboard are removed except the action’s output. This is useful for resetting context in multi-step workflows. It can also make persistence of flows more efficient by dispensing with objects that are no longer needed. Defaults to false.
- `cost`:Relative cost of the action from 0-1. Defaults to 0.0.
- `value`: Relative value of performing the action from 0-1. Defaults to 0.0.

##### Clearing the Blackboard

The `clearBlackboard` attribute is useful in two scenarios:

1. **Multi-step workflows** where you want to reset the processing context
2. **Looping states** where an action returns to a previously-visited state type

When an action with `clearBlackboard = true` completes, all objects on the blackboard are removed except the action’s output. This prevents accumulated intermediate data from affecting subsequent processing and enables loops.

###### Looping States

The most common use case for `clearBlackboard` is enabling loops in state-based workflows:

```java
@State
record ProcessingState(String data, int iteration) {
    @Action(clearBlackboard = true)  (1)
    LoopOutcome process() {
        if (iteration >= 3) {
            return new DoneState(data);
        }
        return new ProcessingState(data + "+", iteration + 1);  (2)
    }
}
```

| **1** | `clearBlackboard = true` enables returning to the same state type |
| --- | --- |
| **2** | Without clearing, returning `ProcessingState` would be blocked since the type already exists |

See [Using States](https://docs.embabel.com/embabel-agent/guide/0.3.4-SNAPSHOT/reference.states) for more details on looping state patterns.

###### Resetting Context

You can also use `clearBlackboard` to reset context in multi-step workflows:

```java
@Agent(description = "Multi-step document processing")
public class DocumentProcessor {

    @Action(clearBlackboard = true)  (1)
    public ProcessedDocument preprocess(RawDocument doc) {
        return new ProcessedDocument(doc.getContent().trim());
    }

    @AchievesGoal(description = "Produce final output")
    @Action
    public FinalOutput transform(ProcessedDocument doc) {  (2)
        return new FinalOutput(doc.getContent().toUpperCase());
    }
}
```

| **1** | After `preprocess` completes, the blackboard is cleared and only `ProcessedDocument` remains. The original `RawDocument` is removed. |
| --- | --- |
| **2** | The `transform` action receives only the `ProcessedDocument`, not any earlier inputs. |

|  | Avoid using `clearBlackboard` on goal-achieving actions (those with `@AchievesGoal`). Clearing the blackboard removes `hasRun` tracking conditions, which may interfere with goal satisfaction. Use `clearBlackboard` on intermediate actions instead. |
| --- | --- |

##### Dynamic Cost Computation with @Cost

While the `cost` and `value` fields on `@Action` allow specifying static values, you can compute these dynamically at planning time using the `@Cost` annotation. This is useful when the cost of an action depends on the current state of the blackboard.

The `@Cost` annotation marks a method that returns a cost value (a `double` between 0.0 and 1.0). You then reference this method from the `@Action` annotation using `costMethod` or `valueMethod`.

```java
@Agent(description = "Processor with dynamic cost")
public class DataProcessor {

    @Cost(name = "processingCost")  (1)
    public double computeProcessingCost(@Nullable LargeDataSet data) {  (2)
        if (data != null && data.size() > 1000) {
            return 0.9;  // High cost for large datasets
        }
        return 0.1;  // Low cost for small or missing datasets
    }

    @Action(costMethod = "processingCost")  (3)
    public ProcessedData process(RawData input) {
        return new ProcessedData(input.transform());
    }
}
```

| **1** | The `@Cost` annotation marks a method for dynamic cost computation. The `name` parameter identifies this cost method. |
| --- | --- |
| **2** | Domain object parameters in `@Cost` methods must be nullable. If the object isn’t on the blackboard, `null` is passed. |
| **3** | The `costMethod` field references the `@Cost` method by name. |

Key differences from `@Condition` methods:

- All domain object parameters in `@Cost` methods must be nullable (use `@Nullable` in Java or `?` in Kotlin)
- When a domain object is not available on the blackboard, `null` is passed instead of causing the method to fail
- The method must return a `double` between 0.0 and 1.0
- The `Blackboard` can be passed as a parameter for direct access to all available objects

You can also compute dynamic value using `valueMethod`:

```java
@Agent(description = "Agent with dynamic value computation")
public class PrioritizedAgent {

    @Cost(name = "urgencyValue")
    public double computeUrgency(@Nullable Task task) {
        if (task == null) {
            return 0.5;
        }
        if (task.getPriority() == Priority.HIGH) {
            return 1.0;
        }
        if (task.getPriority() == Priority.MEDIUM) {
            return 0.6;
        }
        return 0.2;
    }

    @AchievesGoal(description = "Process high-priority tasks")
    @Action(valueMethod = "urgencyValue")
    public Result processTask(Task task) {
        return new Result(String.format("Processed: %s", task.getName()));
    }
}
```

|  | The `@Cost` method is called during planning, before the action executes. It allows the planner to make informed decisions about which actions to prefer based on runtime state. |
| --- | --- |

|  | Dynamic cost is especially useful with **Utility planning** (`PlannerType.UTILITY`), where cost/value tradeoffs are a core concept. The utility planner evaluates actions based on their net value (value minus cost), making dynamic cost computation essential for sophisticated decision-making. |
| --- | --- |

#### 3.6.4. The @Condition annotation

The `@Condition` annotation is used to mark methods that evaluate conditions. They can take an `OperationContext` parameter to access the blackboard and other infrastructure. If they take domain object parameters, the condition will automatically be false until suitable instances are available.

> Condition methods should not have side effects—for example, on the blackboard. This is important because they may be called multiple times.

##### Dynamic Conditions with SpEL

In addition to using `@Condition` methods, you can specify dynamic preconditions directly on `@Action` annotations using Spring Expression Language (SpEL). These expressions are evaluated against the blackboard, allowing you to create conditions based on runtime state without writing separate condition methods.

The expression language is pluggable, but currently SpEL is the only supported implementation. See the [Spring Expression Language (SpEL) documentation](https://docs.spring.io/spring-framework/reference/core/expressions.html) for full syntax details.

SpEL conditions are specified in the `pre` array with a `spel:` prefix:

```java
@Action(
    pre = {"spel:assessment.urgency > 0.5"}  (1)
)
public void handleUrgentIssue(Issue issue, IssueAssessment assessment) {
    // This action only runs when urgency exceeds 0.5
}
```

| **1** | The `spel:` prefix indicates this is a SpEL expression evaluated against the blackboard. |
| --- | --- |

###### Expression Syntax

SpEL expressions reference blackboard objects by their binding names (typically the camelCase form of the class name). The expression must evaluate to a boolean.

```java
@Agent(description = "Issue triage agent")
public class IssueTriageAgent {

    @Action(
        pre = {"spel:issueAssessment.urgency > 0.0"}  (1)
    )
    public void escalateUrgentIssue(
            GHIssue issue,
            IssueAssessment issueAssessment
    ) {
        logger.info("Escalating urgent issue #{}", issue.getNumber());
    }

    @Action(
        pre = {"spel:ghIssue instanceof T(org.kohsuke.github.GHPullRequest) && ghIssue.changedFiles > 10"}  (2)
    )
    public void reviewLargePullRequest(
            GHPullRequest issue,
            PullRequestAssessment assessment
    ) {
        logger.info("Large PR detected: #{} with {} files changed",
            issue.getNumber(), issue.getChangedFiles());
    }
}
```

| **1** | Simple property comparison: action fires only when `urgency` property exceeds 0.0. |
| --- | --- |
| **2** | Type check with property access: action fires only for pull requests with more than 10 changed files. The `T()` operator references a Java type for `instanceof` checks. |

###### Collection Filtering

SpEL’s collection selection syntax (`?[]`) is useful for checking conditions on collections stored in the blackboard:

```java
@Action(
    pre = {
        "spel:newEntity.newEntities.?[#this instanceof T(com.example.domain.Issue) " +
        "&& !(#this instanceof T(com.example.domain.PullRequest))].size() > 0"  (1)
    }
)
public IssueAssessment reactToNewIssue(
        GHIssue ghIssue,
        NewEntity<?> newEntity,
        Ai ai
) {
    // Fires only when newEntities contains Issues that aren't PullRequests
    return ai.withLlm("claude-sonnet-4")
             .creating(IssueAssessment.class)
             .fromTemplate("issue_triage", Map.of("issue", ghIssue));
}

@Action(
    pre = {
        "spel:newEntity.newEntities.?[#this instanceof T(com.example.domain.PullRequest)].size() > 0"  (2)
    }
)
public PullRequestAssessment reactToNewPullRequest(
        GHPullRequest pr,
        NewEntity<?> newEntity,
        Ai ai
) {
    // Fires only when newEntities contains PullRequests
    return ai.withLlm("claude-sonnet-4")
             .creating(PullRequestAssessment.class)
             .fromTemplate("pr_triage", Map.of("pr", pr));
}
```

| **1** | The `?[]` operator filters the collection. `#this` refers to each element. This expression checks that at least one element is an `Issue` but not a `PullRequest`. |
| --- | --- |
| **2** | Simpler filter checking for `PullRequest` instances. |

###### Common SpEL Patterns

| Pattern | Description |
| --- | --- |
| `spel:obj.property > value` | Simple property comparison |
| `spel:obj instanceof T(com.example.Type)` | Type checking using fully qualified class name |
| `spel:collection.size() > 0` | Check collection is not empty |
| `spel:collection.?[condition].size() > 0` | Check that filtered collection has elements |
| `spel:obj.property != null` | Null checking |
| `spel:condition1 && condition2` | Combining conditions with AND |
| `spel:condition1 \|\| condition2` | Combining conditions with OR |

|  | Use SpEL conditions for simple property checks and type discrimination. For complex logic or conditions that need to be reused across multiple actions, prefer `@Condition` methods. For reactive scenarios where you simply want an action to fire when a specific type is added to the blackboard, consider using the [`trigger` field](https://docs.embabel.com/embabel-agent/guide/0.3.4-SNAPSHOT/#reference.annotations_trigger) instead—it’s simpler than writing a SpEL expression. |
| --- | --- |

|  | Blackboard binding names are derived from the class name in camelCase by default. You can specify explicit binding names using `outputBinding` on actions or by adding objects to the blackboard with specific names. |
| --- | --- |

> Both Action and Condition methods may be inherited from superclasses. That is, annotated methods on superclasses will be treated as actions on a subclass instance.

> Give your Action and Condition methods unique names, so the planner can distinguish between them.

#### 3.6.5. Parameters

`@Action` methods must have at least one parameter. `@Condition` methods must have zero or more parameters, but otherwise follow the same rules as `@Action` methods regarding parameters. Ordering of parameters is not important.

Parameters fall in two categories:

- *Domain objects*. These are the normal inputs for action methods. They are backed by the blackboard and will be used as inputs to the action method. A nullable domain object parameter will be populated if it is non-null on the blackboard. This enables nice-to-have parameters that are not required for the action to run. In Kotlin, use a nullable parameter with `?`: in Java, mark the parameter with the `org.springframework.lang.Nullable` or another `Nullable` annotation.
- *Infrastructure parameters*, such as the `OperationContext`, `ProcessContext`, and `Ai` may be used in action or condition methods.

|  | Domain objects drive planning, specifying the preconditions to an action. |
| --- | --- |

The `ActionContext` or `ExecutingOperationContext` subtype can be used in action methods. It adds `asSubProcess` methods that can be used to run other agents in subprocesses. This is an important element of composition.

> Use the least specific type possible for parameters. Use `OperationContext` unless you are creating a subprocess.

##### Custom Parameters

Besides two default parameter categories described above, you can provide your own parameters by implementing the `ActionMethodArgumentResolver` interface. The two main methods of this interface are:

- `supportsParameter`, which indicates what kind of parameters are supported, and
- `resolveArgument`, which resolves the argument into an object used to invoke the action method.

|  | Note the similarity with Spring MVC, where you can provide custom parameters by implementing a `HandlerMethodArgumentResolver`. |
| --- | --- |

> All default parameters are provided by `ActionMethodArgumentResolver` implementations.

To register your custom argument resolver, provide it to the `DefaultActionMethodManager` component in your Spring configuration. Typically, you will register (some of) the defaults as well your custom resolver, in order to support the default parameters.

|  | Make sure to register the `BlackboardArgumentResolver` as last resolver, to ensure that others take precedence. |
| --- | --- |

##### The @Provided Annotation

The `@Provided` annotation marks an action method parameter as being provided by the platform context (such as Spring’s `ApplicationContext`) rather than resolved from the blackboard.

This is particularly useful for:

- **Accessing the enclosing component** from within `@State` classes (which must be static or top-level)
- **Injecting services** that aren’t domain objects but are needed for processing
- **Accessing configuration** or other platform-managed beans

```java
@EmbabelComponent
public class ReservationFlow {

    private final BookingService bookingService;
    private final NotificationService notificationService;

    public ReservationFlow(BookingService bookingService, NotificationService notificationService) {
        this.bookingService = bookingService;
        this.notificationService = notificationService;
    }

    @Action
    public CollectDetails start(UserRequest request) {
        return new CollectDetails(request.customerId());
    }

    @State
    public record CollectDetails(String customerId) {

        @Action
        public ConfirmReservation confirm(
                ReservationDetails details,                    (1)
                @Provided ReservationFlow flow                 (2)
        ) {
            var booking = flow.bookingService.reserve(details);
            flow.notificationService.sendConfirmation(booking);
            return new ConfirmReservation(booking);
        }
    }

    @State
    public record ConfirmReservation(Booking booking) {
        @AchievesGoal(description = "Reservation completed")
        @Action
        public BookingResult complete() {
            return new BookingResult(booking);
        }
    }
}
```

| **1** | `ReservationDetails` is a domain object resolved from the blackboard. |
| --- | --- |
| **2** | `ReservationFlow` is injected via `@Provided` from the Spring context - this gives access to the services in the enclosing component. |

###### How It Works

When Spring is available, the `SpringContextProvider` resolves `@Provided` parameters by looking up beans from the `ApplicationContext`. The parameter type must match a bean in the context.

```java
@State
public record ProcessingState(String data) {

    @Action
    public NextState process(
        @Provided MyService myService,     (1)
        @Provided AppConfig config         (2)
    ) {
        var result = myService.process(data, config.getSetting());
        return new NextState(result);
    }
}
```

| **1** | Any Spring bean can be injected using `@Provided`. |
| --- | --- |
| **2** | Multiple `@Provided` parameters can be used in a single method. |

###### When to Use @Provided

Use `@Provided` when you need access to:

- The enclosing `@EmbabelComponent` or `@Agent` class from a `@State` action
- Services that are infrastructure concerns, not domain objects
- Configuration or environment values

Do **not** use `@Provided` for:

- Domain objects that should drive planning (use regular parameters instead)
- Objects that need to be tracked on the blackboard

|  | Since `@State` classes must be static nested classes or top-level classes, `@Provided` is the recommended way to access the enclosing component’s services. This keeps state classes serializable while still providing access to dependencies. |
| --- | --- |

|  | `@Provided` parameters are resolved before blackboard parameters. If a type could come from either source, `@Provided` takes precedence. |
| --- | --- |

#### 3.6.6. Binding by name

The `@RequireNameMatch` annotation can be used to [bind parameters by name](https://docs.embabel.com/embabel-agent/guide/0.3.4-SNAPSHOT/#reference.flow__binding).

#### 3.6.7. Reactive triggers with trigger

The `trigger` field on the `@Action` annotation enables reactive behavior where an action only fires when a specific type is the *most recently added* value to the blackboard. This is useful in event-driven scenarios where you want to react to a particular event even when multiple parameters of various types are available.

For example, in a chat system you might want an action to fire only when a new user message arrives, not when other context is updated:

```java
@Agent(description = "Chat message handler")
public class ChatAgent {

    @AchievesGoal(description = "Respond to user message")
    @Action(trigger = UserMessage.class)  (1)
    public Response handleMessage(
            UserMessage message,
            Conversation conversation  (2)
    ) {
        return new Response("Received: " + message.content());
    }
}
```

| **1** | The `trigger` field means this action only fires when `UserMessage` is the last result added to the blackboard. |
| --- | --- |
| **2** | `Conversation` must also be available, but doesn’t need to be the triggering event. |

Without `trigger`, an action fires as soon as all its parameters are available on the blackboard. With `trigger`, the specified type must additionally be the most recent value added.

This is particularly useful when:

- You have multiple actions that could handle different event types
- You want to distinguish between "data available" and "event just occurred"
- You’re building event-driven or reactive workflows

```java
@Agent(description = "Multi-event processor")
public class EventProcessor {

    @Action(trigger = EventA.class)  (1)
    public Result handleEventA(EventA eventA, EventB eventB) {
        return new Result("Triggered by A");
    }

    @AchievesGoal(description = "Handle event B")
    @Action(trigger = EventB.class)  (2)
    public Result handleEventB(EventA eventA, EventB eventB) {
        return new Result("Triggered by B");
    }
}
```

| **1** | `handleEventA` fires when `EventA` is added (and `EventB` is available). |
| --- | --- |
| **2** | `handleEventB` fires when `EventB` is added (and `EventA` is available). |

|  | The `trigger` field checks that the specified type matches the `lastResult()` on the blackboard. The last result is the most recent object added via any binding operation. |
| --- | --- |

#### 3.6.8. Handling of return types

Action methods normally return a single domain object.

Nullable return types are allowed. Returning null will trigger replanning. There may or not be an alternative path from that point, but it won’t be what the planner was previously trying to achieve.

There is a special case where the return type can essentially be a union type, where the action method can return one ore more of several types. This is achieved by a return type implementing the `SomeOf` tag interface. Implementations of this interface can have multiple nullable fields. Any non-null values will be bound to the blackboard, and the postconditions of the action will include all possible fields of the return type.

For example:

```java
// Must implement the SomeOf interface
public record FrogOrDog(
    @Nullable Frog frog,
    @Nullable Dog dog
) implements SomeOf {}

@Agent(description = "Illustrates use of the SomeOf interface")
public class ReturnsFrogOrDog {

    @Action
    public FrogOrDog frogOrDog() {
        return new FrogOrDog(new Frog("Kermit"), null);
    }

    // This works because the frog field of the return type was set
    @AchievesGoal(description = "Create a prince from a frog")
    @Action
    public PersonWithReverseTool toPerson(Frog frog) {
        return new PersonWithReverseTool(frog.name());
    }
}
```

This enables routing scenarios in an elegant manner.

|  | Multiple fields of the `SomeOf` instance may be non-null and this is not an error. It may enable the most appropriate routing. |
| --- | --- |

Routing can also be achieved via subtypes, as in the following example:

```java
@Action
public Intent classifyIntent(UserInput userInput) {  (1)
    return switch (userInput.content()) {
        case "billing" -> new BillingIntent();
        case "sales" -> new SalesIntent();
        case "service" -> new ServiceIntent();
        default -> {
            logger.warn("Unknown intent: {}", userInput);
            yield null;
        }
    };
}

@Action
public IntentClassificationSuccess billingAction(BillingIntent intent) {  (2)
    return new IntentClassificationSuccess("billing");
}

@Action
public IntentClassificationSuccess salesAction(SalesIntent intent) {
    return new IntentClassificationSuccess("sales");
}

// ...
```

| **1** | Classification action returns supertype `Intent`. Real classification would likely use an LLM. |
| --- | --- |
| **2** | `billingAction` and other action methods takes a subtype of `Intent`, so will only be invoked if the classification action returned that subtype. |

#### 3.6.9. Action method implementation

Embabel makes it easy to seamlessly integrate LLM invocation and application code, using common types. An `@Action` method is a normal method, and can use any libraries or frameworks you like.

The only special thing about it is its ability to use the `OperationContext` parameter to access the blackboard and invoke LLMs.

#### 3.6.10. The @AchievesGoal annotation

The `@AchievesGoal` annotation can be added to an `@Action` method to indicate that the completion of the action achieves a specific goal.

#### 3.6.11. Implementing the StuckHandler interface

If an annotated agent class implements the `StuckHandler` interface, it can handle situations where an action is stuck itself. For example, it can add data to the blackboard.

Example:

```java
@Agent(description = "self unsticking agent")
public class SelfUnstickingAgent implements StuckHandler {

    private boolean called = false;

    // The agent will get stuck as there's no dog to convert to a frog
    @Action
    @AchievesGoal(description = "the big goal in the sky")
    public Frog toFrog(Dog dog) {
        return new Frog(dog.name());
    }

    // This method will be called when the agent is stuck
    @Override
    public StuckHandlerResult handleStuck(AgentProcess agentProcess) {
        called = true;
        agentProcess.addObject(new Dog("Duke"));
        return new StuckHandlerResult(
            "Unsticking myself",
            this,
            StuckHandlingResultCode.REPLAN,
            agentProcess
        );
    }
}
```

#### 3.6.12. Advanced Usage: Nested processes

An `@Action` method can invoke another agent process. This is often done to use a stereotyped process that is composed using the DSL.

Use the `ActionContext.asSubProcess` method to create a sub-process from the action context.

For example:

```java
@Action
public ScoredResult<Report, SimpleFeedback> report(
        ReportRequest reportRequest,
        ActionContext context
) {
    return context.asSubProcess(
        // Will create an agent sub process with strong typing
        EvaluatorOptimizer.generateUntilAcceptable(
            5,
            ctx -> ctx.promptRunner()
                .withToolGroup(CoreToolGroups.WEB)
                .create(String.format("""
                    Given the topic, generate a detailed report in %d words.

                    # Topic
                    %s

                    # Feedback
                    %s
                    """,
                    reportRequest.words(),
                    reportRequest.topic(),
                    ctx.getInput() != null ? ctx.getInput() : "No feedback provided")),
            ctx -> ctx.promptRunner()
                .withToolGroup(CoreToolGroups.WEB)
                .create(String.format("""
                    Given the topic and word count, evaluate the report and provide feedback
                    Feedback must be a score between 0 and 1, where 1 is perfect.

                    # Report
                    %s

                    # Report request:
                    %s
                    Word count: %d
                    """,
                    ctx.getInput().report(),
                    reportRequest.topic(),
                    reportRequest.words()))
        ));
}
```

#### 3.6.13. Running Subagents with RunSubagent

The `RunSubagent` utility provides a convenient way to run a nested agent from within an `@Action` method without needing direct access to `ActionContext`. This is particularly useful when you want to delegate work to another `@Agent` -annotated class or an `Agent` instance.

##### Running an @Agent-annotated Instance

Use `RunSubagent.fromAnnotatedInstance()` when you have an instance of a class annotated with `@Agent`:

|  | The annotated instance can be Spring-injected into your agent class. Since `@Agent` is a Spring stereotype annotation, you can inject one agent into another and run it as a subagent. This enables clean separation of concerns while maintaining testability. |
| --- | --- |

```java
@Agent(description = "Outer agent that delegates to an injected subagent")
public class OuterAgent {

    private final InnerSubAgent innerSubAgent;

    public OuterAgent(InnerSubAgent innerSubAgent) {  (1)
        this.innerSubAgent = innerSubAgent;
    }

    @Action
    public TaskOutput start(UserInput input) {
        return RunSubagent.fromAnnotatedInstance(
            innerSubAgent,  (2)
            TaskOutput.class
        );
    }

    @Action
    @AchievesGoal(description = "Processing complete")
    public TaskOutput done(TaskOutput output) {
        return output;
    }
}

@Agent(description = "Inner subagent that processes input")
public class InnerSubAgent {

    @Action
    public Intermediate stepOne(UserInput input) {
        return new Intermediate(input.getContent());
    }

    @Action
    @AchievesGoal(description = "Subagent complete")
    public TaskOutput stepTwo(Intermediate data) {
        return new TaskOutput(data.value().toUpperCase());
    }
}
```

| **1** | Spring injects the `InnerSubAgent` bean via constructor injection. |
| --- | --- |
| **2** | The injected instance is passed to `RunSubagent.fromAnnotatedInstance()`. |

In Kotlin, you can use the reified version for a more concise syntax:

```java
@Agent(description = "Outer agent via explicit type parameter")
public class OuterAgentExplicit {

    @Action
    public TaskOutput start(UserInput input) {
        return RunSubagent.fromAnnotatedInstance(
            new InnerSubAgent(),
            TaskOutput.class
        );
    }

    @Action
    @AchievesGoal(description = "Processing complete")
    public TaskOutput done(TaskOutput output) {
        return output;
    }
}
```

##### Running an Agent Instance

Use `RunSubagent.instance()` when you already have an `Agent` object (for example, one created programmatically or via `AgentMetadataReader`):

```java
@Agent(description = "Outer agent with Agent instance")
public class OuterAgentWithAgentInstance {

    @Action
    public TaskOutput start(UserInput input) {
        Agent agent = (Agent) new AgentMetadataReader()
            .createAgentMetadata(new InnerSubAgent());
        return RunSubagent.instance(agent, TaskOutput.class);
    }

    @Action
    @AchievesGoal(description = "Processing complete")
    public TaskOutput done(TaskOutput output) {
        return output;
    }
}
```

In Kotlin with reified types:

```java
@Agent(description = "Outer agent via explicit agent instance")
public class OuterAgentExplicitInstance {

    @Action
    public TaskOutput start(UserInput input) {
        Agent agent = (Agent) new AgentMetadataReader()
            .createAgentMetadata(new InnerSubAgent());
        return RunSubagent.instance(agent, TaskOutput.class);
    }

    @Action
    @AchievesGoal(description = "Processing complete")
    public TaskOutput done(TaskOutput output) {
        return output;
    }
}
```

##### How It Works

`RunSubagent` methods throw a `SubagentExecutionRequest` exception that is caught by the framework. The framework then executes the subagent as a subprocess within the current agent process, sharing the same blackboard context. The result of the subagent’s goal-achieving action is returned to the calling action.

This approach has several advantages:

- **Cleaner syntax**: No need to pass `ActionContext` to the action method
- **Type safety**: The return type is enforced at compile time
- **Composition**: Easily compose complex workflows from simpler agents
- **Reusability**: The same subagent can be used in multiple contexts

##### Comparison with ActionContext.asSubProcess

Both `RunSubagent` and `ActionContext.asSubProcess` achieve the same result, but differ in style:

| Approach | When to use | Example |
| --- | --- | --- |
| `RunSubagent.fromAnnotatedInstance()` | When you have an `@Agent` -annotated instance and don’t need `ActionContext` | `RunSubagent.fromAnnotatedInstance(new SubAgent(), Result.class)` |
| `RunSubagent.instance()` | When you have an `Agent` object | `RunSubagent.instance(agent, Result.class)` |
| `ActionContext.asSubProcess()` | When you need access to `ActionContext` for other operations | `context.asSubProcess(Result.class, agent)` |

|  | Use `RunSubagent` when your action method only needs to delegate to a subagent. Use `ActionContext.asSubProcess()` when you need additional context operations. |
| --- | --- |

### 3.7. DSL

You can also create agents using a DSL in Kotlin or Java.

This is useful for workflows where you want an atomic action that is complete in itself but may contain multiple steps or actions.

#### 3.7.1. Standard Workflows

There are a number of standard workflows, constructed by builders, that meet common requirements. These can be used to create agents that will be exposed as Spring beans, or within `@Action` methods within other agents. All are type safe. As far as possible, they use consistent APIs.

- `SimpleAgentBuilder`: The simplest agent, with no preconditions or postconditions.
- `ScatterGatherBuilder`: Fork join pattern for parallel processing.
- `ConsensusBuilder`: A pattern for reaching consensus among multiple sources. Specialization of `ScatterGather`.
- `RepeatUntil`: Repeats a step until a condition is met.
- `RepeatUntilAcceptable`: Repeats a step while a condition is met, with a separate evaluator providing feedback.

Creating a simple agent:

```java
var agent = SimpleAgentBuilder
    .returning(Joke.class) (1)
    .running(tac -> tac.ai() (2)
        .withDefaultLlm()
        .createObject("Tell me a joke", Joke.class))
    .buildAgent("joker", "This is guaranteed to return a dreadful joke"); (3)
```

| **1** | Specify the return type. |
| --- | --- |
| **2** | specify the action to run. Takes a `SupplierActionContext<RESULT>` `OperationContext` parameter allowing access to the current `AgentProcess`. |
| **3** | Build an agent with the given name and description. |

A more complex example:

```java
@Action
FactChecks runAndConsolidateFactChecks(
        DistinctFactualAssertions distinctFactualAssertions,
        ActionContext context) {
    var llmFactChecks = properties.models().stream()
            .flatMap(model -> factCheckWithSingleLlm(model, distinctFactualAssertions, context))
            .toList();
    return ScatterGatherBuilder (1)
            .returning(FactChecks.class) (2)
            .fromElements(FactCheck.class) (3)
            .generatedBy(llmFactChecks) (4)
            .consolidatedBy(this::reconcileFactChecks) (5)
            .asSubProcess(context); (6)
    }
```

| **1** | Start building a scatter gather agent. |
| --- | --- |
| **2** | Specify the return type of the overall agent. |
| **3** | Specify the type of elements to be gathered. |
| **4** | Specify the list of functions to run in parallel, each generating an element, here of type `FactCheck`. |
| **5** | Specify a function to consolidate the results. In this case it will take a list of `FactCheck` and return a `FactCheck` and return a `FactChecks` object. |
| **6** | Build and run the agent as a subprocess of the current process. This is an alternative to `buildAgent` shown in the `SimpleAgentBuilder` example. The API is consistent. |

|  | If you wish to experiment, the [embabel-agent-examples](https://github.com/embabel/embabel-agent-examples) repository includes the [fact checker](https://github.com/embabel/embabel-agent-examples/blob/main/examples-java/src/main/java/com/embabel/example/factchecker/FactChecker.java) shown above. |
| --- | --- |

Whereas the `@Agent` annotation causes a class to be picked up immediately by Spring, with the DSL you’ll need an extra step to register an agent with Spring. As shown in the example below, any `@Bean` of `Agent` type results auto registration, just like declaring a class annotated with `@Agent` does.

```java
@Configuration
public class FactCheckerAgentConfiguration {

    @Bean
    public Agent factChecker(FactCheckerProperties factCheckerProperties) {
        return factCheckerAgent(
            List.of(
                LlmOptions.create(AnthropicModels.CLAUDE_35_HAIKU).withTemperature(0.3),
                LlmOptions.create(AnthropicModels.CLAUDE_35_HAIKU).withTemperature(0.0)
            ),
            factCheckerProperties
        );
    }
}
```

### 3.8. Core Types

#### 3.8.1. LlmOptions

The `LlmOptions` class specifies which LLM to use and its hyperparameters. It’s defined in the [embabel-common](https://github.com/embabel/embabel-common) project and provides a fluent API for LLM configuration:

```java
// Create LlmOptions with model and temperature
var options = LlmOptions
    .withModel(OpenAiModels.GPT_4O_MINI)
    .withTemperature(0.8);

// Use different hyperparameters for different tasks
var analyticalOptions = LlmOptions
    .withModel(OpenAiModels.GPT_4O_MINI)
    .withTemperature(0.2)
    .withTopP(0.9);
```

**Important Methods:**

- `withModel(String)`: Specify the model name
- `withRole(String)`: Specify the model role. The role must be one defined in configuration via `embabel.models.llms.<role>=<model-name>`
- `withTemperature(Double)`: Set creativity/randomness (0.0-1.0)
- `withTopP(Double)`: Set nucleus sampling parameter
- `withTopK(Integer)`: Set top-K sampling parameter
- `withPersona(String)`: Add a system message persona

`LlmOptions` is serializable to JSON, so you can set properties of type `LlmOptions` in `application.yml` and other application configuration files. This is a powerful way of externalizing not only models, but hyperparameters.

#### 3.8.2. PromptRunner

All LLM calls in user applications should be made via the `PromptRunner` interface. Once created, a `PromptRunner` can run multiple prompts with the same LLM, hyperparameters, tool groups and `PromptContributors`.

##### Getting a PromptRunner

You obtain a `PromptRunner` from an `OperationContext` using the fluent API:

```java
@Action
public Story createStory(UserInput input, OperationContext context) {
    // Get PromptRunner with default LLM
    var runner = context.ai().withDefaultLlm();

    // Get PromptRunner with specific LLM options
    var customRunner = context.ai().withLlm(
        LlmOptions.withModel(OpenAiModels.GPT_4O_MINI)
            .withTemperature(0.8)
    );

    return customRunner.createObject("Write a story about: " + input.getContent(), Story.class);
}
```

##### PromptRunner Methods

**Core Object Creation:**

- `createObject(String, Class<T>)`: Create a typed object from a prompt, otherwise throw an exception. An exception triggers retry. If retry fails repeatedly, re-planning occurs.
- `createObjectIfPossible(String, Class<T>)`: Try to create an object, return null on failure. This can cause replanning.
- `generateText(String)`: Generate simple text response

|  | Normally you want to use one of the `createObject` methods to ensure the response is typed correctly. |
| --- | --- |

**Tool and Context Management:**

- `withToolGroup(String)`: Add [tool groups](https://docs.embabel.com/embabel-agent/guide/0.3.4-SNAPSHOT/#reference.tools__tool-groups) for LLM access
- `withToolObject(Object)`: Add domain objects with [@Tool](https://docs.embabel.com/embabel-agent/guide/0.3.4-SNAPSHOT/#reference.tools) methods
- `withPromptContributor(PromptContributor)`: Add [context](https://docs.embabel.com/embabel-agent/guide/0.3.4-SNAPSHOT/#reference.prompt-contributors) contributors
- `withImage(AgentImage)`: Add an image to the prompt for vision-capable LLMs
- `withImages(AgentImage…​)`: Add multiple images to the prompt

**LLM Configuration:**

- `withLlm(LlmOptions)`: Use specific LLM configuration
- `withGenerateExamples(Boolean)`: Control example generation

**Returning a Specific Type**

- `creating(Class<T>)`: Go into the `Creating` fluent API for returning a particular type.

For example:

```java
var story = context.ai()
    .withDefaultLlm()
    .withToolGroup(CoreToolGroups.WEB)
    .creating(Story.class)
    .fromPrompt("Create a story about: " + input.getContent());
```

The main reason to do this is to add strongly typed examples for [few-shot prompting](https://www.promptingguide.ai/techniques/fewshot). For example:

```java
var story = context.ai()
    .withDefaultLlm()
    .withToolGroup(CoreToolGroups.WEB)
    .creating(Story.class)
    .withExample("A children's story", new Story("Once upon a time...")) (1)
    .fromPrompt("Create a story about: " + input.getContent());
```

| **1** | **Example**: The example will be included in the prompt in JSON format to guide the LLM. |
| --- | --- |

**Working with Images:**

```java
var image = AgentImage.fromFile(imageFile);

var answer = context.ai()
    .withLlm(AnthropicModels.CLAUDE_35_HAIKU)  (1)
    .withImage(image)  (2)
    .generateText("What is in this image?");
```

| **1** | **Vision-capable model required**: Use Claude 3.x, GPT-4 Vision, or other multimodal LLMs |
| --- | --- |
| **2** | **Add image**: Images are sent with the text prompt to the LLM. Can be used multiple times for multiple images. |

**Advanced Features:**

- `rendering(String)`: Use [Jinja](https://docs.embabel.com/embabel-agent/guide/0.3.4-SNAPSHOT/#reference.templates) templates for prompts (returns `Rendering` interface)
- `withTool(Subagent.ofClass(MyAgent.class).consuming(MyInput.class))`: Enable handoffs to other agents (see [Subagent: Agent Handoffs as Tools](https://docs.embabel.com/embabel-agent/guide/0.3.4-SNAPSHOT/#subagent-tool))
- `evaluateCondition(String, String)`: Evaluate boolean condition

**Validation**

Embabel supports [JSR-380](https://beanvalidation.org/2.0-jsr380/) bean validation annotations on domain objects. When creating objects via `PromptRunner.createObject` or `createObjectIfPossible`, validation is automatically performed after deserialization. If validation fails, Embabel transparently retries the LLM call to obtain a valid object, describing the validation errors to the LLM to help it correct its response.

If validation fails a second time, `InvalidLlmReturnTypeException` is thrown. This will trigger replanning if not caught. You can also choose to catch it within the action method making the LLM call, and take appropriate action in your own code.

Simple example of annotation use:

```java
public class User {

    @NotNull(message = "Name cannot be null")
    private String name;

    @AssertTrue(message = "Working must be true")
    private boolean working;

    @Size(min = 10, max = 200, message
      = "About Me must be between 10 and 200 characters")
    private String aboutMe;

    @Min(value = 18, message = "Age should not be less than 18")
    @Max(value = 150, message = "Age should not be greater than 150")
    private int age;

    @Email(message = "Email should be valid")
    private String email;

    // standard setters and getters
}
```

You can also use custom annotations with validators that will be injected by Spring. For example:

```java
@Target({ElementType.FIELD, ElementType.PARAMETER}) (1)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = PalindromeValidator.class)
public @interface MustBePalindrome {
    String message() default "Must be a palindrome";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}

public class Palindromic {
    @MustBePalindrome (2)
    private String eats;

    public Palindromic(String eats) {
        this.eats = eats;
    }

    public String getEats() {
        return eats;
    }
}

@Component (3)
public class PalindromeValidator implements ConstraintValidator<MustBePalindrome, String> {

    private final Ai ai; (4)

    public PalindromeValidator(Ai ai) {
        this.ai = ai;
    }

    @Override
    public boolean isValid(String field, ConstraintValidatorContext context) {
        if (field == null) {
            return false;
        }
        return field.equals(new StringBuilder(field).reverse().toString());
    }
}
```

| **1** | Define the custom annotation |
| --- | --- |
| **2** | Apply the annotation to a field |
| **3** | Implement the validator as a Spring component. Note the `@Component` annotation. |
| **4** | Spring will inject the validator with dependencies, such as the `Ai` instance in this case |

Thus we have standard JSR-280 validation with full Spring dependency injection support.

#### 3.8.3. AgentImage

Represents an image for use with vision-capable LLMs.

**Factory Methods:**

- `AgentImage.fromFile(File)`: Load from file (auto-detects MIME type from common extensions)
- `AgentImage.fromPath(Path)`: Load from path (auto-detects MIME type)
- `AgentImage.create(String, byte[])`: Create with explicit MIME type and byte array
- `AgentImage.fromBytes(String, byte[])`: Create from filename and bytes (auto-detects MIME type)

For uncommon image formats or if auto-detection fails, use `AgentImage.create()` with an explicit MIME type.

### 3.9. Tools

Tools can be passed to LLMs to allow them to perform actions. Tools can either be outside the JVM process, as with MCP, or inside the JVM process, as with domain objects exposing `@LlmTool` methods.

Embabel allows you to provide tools to LLMs in two ways:

- Via the `PromptRunner` by providing one or more in process **tool instances**. A tool instance is an object with methods annotated with Embabel `@LlmTool` or Spring AI `@Tool`.
- At action or `PromptRunner` level, from a **tool group**.

`LlmReference` implementations also expose tools, but this is handled internally by the framework.

#### 3.9.1. In Process Tools: Implementing Tool Instances

Implement one or more methods annotated with `@LlmTool` on a class. You do not need to annotate the class itself. Each annotated method represents a distinct tool that will be exposed to the LLM.

A simple example of a tool method:

```java
public class MathTools {

    @LlmTool(description = "add two numbers")
    public double add(double a, double b) {
        return a + b;
    }

    // Other tools
}
```

Classes implementing tools can be stateful. They are often domain objects. Tools on mapped entities are especially useful, as they can encapsulate state that is never exposed to the LLM. See [Domain Tools: Direct Access, Zero Ceremony](https://medium.com/@springrod/domain-tools-direct-access-zero-ceremony-9a3e8d4cf550) for a discussion of tool use patterns.

The `@Tool` annotation comes from [Spring AI](https://docs.spring.io/spring-ai/reference/api/tools.html).

Tool methods can have any visibility, and can be static or instance scope. They are allowed on inner classes.

> You can define any number of arguments for the method (including no argument) with most types (primitives, POJOs, enums, lists, arrays, maps, and so on). Similarly, the method can return most types, including void. If the method returns a value, the return type must be a serializable type, as the result will be serialized and sent back to the model.
> 
> The following types are not currently supported as parameters or return types for methods used as tools:
> 
> - Optional
> - Asynchronous types (e.g. CompletableFuture, Future)
> - Reactive types (e.g. Flow, Mono, Flux)
> - Functional types (e.g. Function, Supplier, Consumer).

— Spring AI  
Tool Calling

You can obtain the current `AgentProcess` in a Tool method implementation via `AgentProcess.get()`. This enables tools to bind to the `AgentProcess`, making objects available to other actions. For example:

```java
@LlmTool(description = "My Tool")
public String bindCustomer(Long id) {
    var customer = customerRepository.findById(id);
    var agentProcess = AgentProcess.get();
    if (agentProcess != null) {
        agentProcess.addObject(customer);
        return "Customer bound to blackboard";
    }
    return "No agent process: Unable to bind customer";
}
```

#### 3.9.2. Tool Groups

Embabel introduces the concept of a **tool group**. This is a level of indirection between user intent and tool selection. For example, we don’t ask for Brave or Google web search: we ask for "web" tools, which may be resolved differently in different environments.

|  | Tools use should be focused. Thus tool groups are not specified at agent level, but on individual actions. |
| --- | --- |

Tool groups are often backed by [MCP](https://docs.embabel.com/embabel-agent/guide/0.3.4-SNAPSHOT/#reference.integrations__mcp).

##### Configuring Tool Groups in configuration files

If you have configured MCP servers in your application configuration, you can selectively expose tools from those servers to agents by configuring tool groups. The easiest way to do this is in your `application.yml` or `application.properties` file. Select tools by name.

For example:

```yaml
embabel:

    agent:
    platform:
      tools:
        includes:
          weather:
            description: Get weather for location
            provider: Docker
            tools:
              - weather
```

##### Configuring Tool Groups in Spring @Configuration

You can also use Spring’s `@Configuration` and `@Bean` annotations to expose ToolGroups to the agent platform with greater control. The framework provides a default `ToolGroupsConfiguration` that demonstrates how to inject MCP servers and selectively expose MCP tools:

```java
@Configuration
public class ToolGroupsConfiguration {

    private final List<McpSyncClient> mcpSyncClients;

    public ToolGroupsConfiguration(List<McpSyncClient> mcpSyncClients) {
        this.mcpSyncClients = mcpSyncClients;
    }

    @Bean
    public MathTools mathToolGroup() {
        return new MathTools();
    }

    @Bean
    public ToolGroup mcpWebToolsGroup() { (1)
        return new McpToolGroup(
            CoreToolGroups.WEB_DESCRIPTION,
            "docker-web",
            "Docker",
            Set.of(ToolGroupPermission.INTERNET_ACCESS),
            mcpSyncClients,
            callback -> {
                // Only expose specific web tools, exclude rate-limited ones
                String name = callback.getToolDefinition().name();
                return (name.contains("brave") || name.contains("fetch")) &&
                       !name.contains("brave_local_search");
            }
        );
    }
}
```

| **1** | This method creates a Spring bean of type `ToolGroup`. This will automatically be picked up by the agent platform, allowing the tool group to be requested by name (role). |
| --- | --- |

##### Key Configuration Patterns

**MCP Client Injection:** The configuration class receives a `List<McpSyncClient>` via constructor injection. Spring automatically provides all available MCP clients that have been configured in the application.

**Selective Tool Exposure:** Each `McpToolGroup` uses a `filter` lambda to control which tools from the MCP servers are exposed to agents. This allows fine-grained control over tool availability and prevents unwanted or problematic tools from being used.

**Tool Group Metadata:** Tool groups include descriptive metadata like `name`, `provider`, and `description` to help agents understand their capabilities. The `permissions` property declares what access the tool group requires (e.g., `INTERNET_ACCESS`).

##### Creating Custom Tool Group Configurations

Applications can implement their own `@Configuration` classes to expose custom tool groups, which can be backed by any service or resource, not just MCP.

```java
@Configuration
public class MyToolGroupsConfiguration {

    @Bean
    public ToolGroup databaseToolsGroup(DataSource dataSource) {
        return new DatabaseToolGroup(dataSource);
    }

    @Bean
    public ToolGroup emailToolsGroup(EmailService emailService) {
        return new EmailToolGroup(emailService);
    }
}
```

This approach leverages Spring’s dependency injection to provide tool groups with the services and resources they need, while maintaining clean separation of concerns between tool configuration and agent logic.

##### Using Tools in Action Methods

Tools are specified on the `PromptRunner` when making LLM calls. This gives you fine-grained control over which tools are available for each specific prompt.

Here’s an example from the `StarNewsFinder` agent that demonstrates web tool usage within an action:

```java
@Action
public RelevantNewsStories findNewsStories(
        StarPerson person, Horoscope horoscope, OperationContext context) {
    var prompt = """
            %s is an astrology believer with the sign %s.
            Their horoscope for today is:
                <horoscope>%s</horoscope>
            Given this, use web tools and generate search queries
            to find %d relevant news stories summarize them in a few sentences.
            Include the URL for each story.
            Do not look for another horoscope reading or return results directly about astrology;
            find stories relevant to the reading above.
            """.formatted(
            person.name(), person.sign(), horoscope.summary(), storyCount);

    // Tools are specified on the PromptRunner
    return context.ai().withDefaultLlm()
        .withToolGroup(CoreToolGroups.WEB)  // Add web search tools
        .createObject(prompt, RelevantNewsStories.class);
}
```

##### Key Tool Usage Patterns

**PromptRunner Tool Methods:** Tools are added to the `PromptRunner` fluent API using methods like `withToolGroup()`, `withTools()`, and `withToolObject()`.

**Multiple Tool Groups:** Actions can add multiple tool groups by chaining `withToolGroup()` calls when they need different types of capabilities.

**Tool-Aware Prompts:** Prompts should explicitly instruct the LLM to use the available tools. For example, "use web tools and generate search queries" clearly directs the LLM to utilize the web search capabilities.

##### Additional PromptRunner Examples

```java
// Add tool groups to a specific prompt
context.ai().withAutoLlm().withToolGroup(CoreToolGroups.WEB).create(
    "Given the topic, generate a detailed report using web research.\n\n" +
    "# Topic\n" +
    reportRequest.getTopic()
);

// Add multiple tool groups
context.ai().withDefaultLlm()
    .withToolGroup(CoreToolGroups.WEB)
    .withToolGroup(CoreToolGroups.MATH)
    .createObject("Calculate stock performance with web data", StockReport.class);
```

**Adding Tool Objects with @LlmTool Methods:**

You can also provide domain objects with `@LlmTool` methods directly to specific prompts:

```java
context.ai()
    .withDefaultLlm()
    .withToolObject(jokerTool)
    .createObject("Create a UserInput object for fun", UserInput.class);

// Add tool object with filtering and custom naming strategy
context.ai()
    .withDefaultLlm()
    .withToolObject(
        new ToolObject(calculatorService)
            .withNamingStrategy(name -> "calc_" + name)
            .withFilter(methodName -> methodName.startsWith("compute"))
    ).createObject("Perform calculations", Result.class);
```

**Available PromptRunner Tool Methods:**

- `withToolGroup(String)`: Add a single tool group by name
- `withToolGroup(ToolGroup)`: Add a specific ToolGroup instance
- `withToolGroups(Set<String>)`: Add multiple tool groups
- `withTools(vararg String)`: Convenient method to add multiple tool groups
- `withToolObject(Any)`: Add domain object with `@LlmTool` or `@Tool` methods
- `withToolObject(ToolObject)`: Add ToolObject with custom configuration
- `withTool(Tool)`: Add a framework-agnostic Tool instance
- `withTools(List<Tool>)`: Add multiple framework-agnostic Tool instances

#### 3.9.3. Framework-Agnostic Tool Interface

In addition to Spring AI’s `@Tool` annotation, Embabel provides its own framework-agnostic `Tool` interface in the `com.embabel.agent.api.tool` package. This allows you to create tools that are not tied to any specific LLM framework, making your code more portable and testable.

The `Tool` interface includes nested types to avoid naming conflicts with framework-specific types:

- `Tool.Definition` - Describes the tool (name, description, input schema)
- `Tool.InputSchema` - Defines the parameters the tool accepts
- `Tool.Parameter` - A single parameter with name, type, and description
- `Tool.Result` - The result returned by a tool (text, artifact, or error)
- `Tool.Handler` - Functional interface for implementing tool logic

##### Creating Tools Programmatically

You can create tools using the `Tool.create()` factory methods:

```java
// Simple tool with no parameters
Tool greetTool = Tool.create(
    "greet",
    "Greets the user",
    (input) -> Tool.Result.text("Hello!")
);

// Tool with parameters (using factory methods)
Tool addTool = Tool.create(
    "add",
    "Adds two numbers together",
    Tool.InputSchema.of(
        Tool.Parameter.integer("a", "First number"),
        Tool.Parameter.integer("b", "Second number")
    ),
    (input) -> {
        // Parse input JSON and compute result
        return Tool.Result.text("42");
    }
);

// Tool with metadata (e.g., return directly without LLM processing)
Tool directTool = Tool.create(
    "lookup",
    "Looks up data directly",
    Tool.Metadata.create(true), // returnDirect = true
    (input) -> Tool.Result.text("Direct result")
);
```

The `Tool.Parameter` class provides factory methods for common parameter types:

- `Tool.Parameter.string(name, description)` - String parameter
- `Tool.Parameter.string(name, description, required)` - String with optional flag
- `Tool.Parameter.string(name, description, required, enumValues)` - String with allowed values
- `Tool.Parameter.integer(name, description)` - Integer parameter
- `Tool.Parameter.double(name, description)` - Floating-point parameter

All factory methods default to `required = true`.

##### Creating Strongly Typed Tools

For tools with complex input and output structures, use `Tool.fromFunction()` to work with domain objects directly. The input schema is generated automatically from the input type, and JSON marshaling is handled for you.

```java
// Define input and output types
record AddRequest(int a, int b) {}
record AddResult(int sum) {}

// Create typed tool - schema is generated from AddRequest
Tool addTool = Tool.fromFunction(
    "add",
    "Adds two numbers together",
    AddRequest.class,
    AddResult.class,
    input -> new AddResult(input.a() + input.b())
);

// Call the tool - input is deserialized, output is serialized
Tool.Result result = addTool.call("{\"a\": 5, \"b\": 3}");
// Result contains: {"sum":8}

// String output is returned directly (not double-serialized)
Tool greetTool = Tool.fromFunction(
    "greet",
    "Greets someone",
    GreetRequest.class,
    String.class,
    input -> "Hello " + input.name() + "!"
);

// With custom metadata
Tool directTool = Tool.fromFunction(
    "lookup",
    "Looks up data directly",
    LookupRequest.class,
    LookupResult.class,
    Tool.Metadata.create(true), // returnDirect = true
    input -> new LookupResult(findData(input))
);
```

You can also instantiate `TypedTool` directly:

```kotlin
val tool = TypedTool(
    name = "add",
    description = "Add two numbers",
    inputType = AddRequest::class.java,
    outputType = AddResult::class.java,
) { input -> AddResult(input.a + input.b) }
```

Key features of typed tools:

- **Automatic schema generation**: The input schema is derived from the input type’s structure
- **JSON marshaling**: Input JSON is deserialized to the input type, and output is serialized from the output type
- **String pass-through**: If the output type is `String`, it’s returned directly without JSON serialization
- **Result pass-through**: If the function returns a `Tool.Result`, it’s used as-is
- **Exception handling**: Exceptions thrown by the function are converted to `Tool.Result.Error`
- **Control flow signals**: Exceptions implementing `ToolControlFlowSignal` (like `ReplanRequestedException`) propagate through

##### Creating Tools from Annotated Methods

Embabel provides `@LlmTool` and `@LlmTool.Param` annotations for creating tools from annotated methods. This approach is similar to Spring AI’s `@Tool` but uses Embabel’s framework-agnostic abstractions.

```java
public class MathService {

    @LlmTool(description = "Adds two numbers together")
    public int add(
            @LlmTool.Param(description = "First number") int a,
            @LlmTool.Param(description = "Second number") int b) {
        return a + b;
    }

    @LlmTool(description = "Multiplies two numbers")
    public int multiply(
            @LlmTool.Param(description = "First number") int a,
            @LlmTool.Param(description = "Second number") int b) {
        return a * b;
    }
}

// Create tools from all annotated methods on an instance
List<Tool> mathTools = Tool.fromInstance(new MathService());

// Or safely create tools (returns empty list if no annotations found)
List<Tool> tools = Tool.safelyFromInstance(someObject);
```

The `@LlmTool` annotation supports:

- `name`: Tool name (defaults to method name if empty)
- `description`: Description of what the tool does (required)
- `returnDirect`: Whether to return the result directly without further LLM processing

The `@LlmTool.Param` annotation supports:

- `description`: Description of the parameter (helps the LLM understand what to provide)
- `required`: Whether the parameter is required (defaults to true)

##### Adding Framework-Agnostic Tools via PromptRunner

Use `withTool()` or `withTools()` to add framework-agnostic tools to a `PromptRunner`:

```java
// Add a single tool
Tool calculatorTool = Tool.create("calculate", "Performs calculations",
    (input) -> Tool.Result.text("Result: 42"));

context.ai()
    .withDefaultLlm()
    .withTool(calculatorTool)
    .createObject("Calculate 6 * 7", MathResult.class);

// Add tools from annotated methods
List<Tool> mathTools = Tool.fromInstance(new MathService());

context.ai()
    .withDefaultLlm()
    .withTools(mathTools)
    .createObject("Add 5 and 3", MathResult.class);

// Combine with other tool sources
context.ai()
    .withDefaultLlm()
    .withToolGroup(CoreToolGroups.WEB)  // Tool group
    .withToolObject(domainObject)        // Spring AI @Tool methods
    .withTools(mathTools)                // Framework-agnostic tools
    .createObject("Research and calculate", Report.class);
```

##### Tool Results

Tools return `Tool.Result` which can be one of three types:

```java
// Text result (most common)
Tool.Result.text("The answer is 42");

// Result with an artifact (e.g., generated file, image)
Tool.Result.withArtifact("Generated report", reportBytes);

// Error result
Tool.Result.error("Failed to process request", exception);
```

##### Modifying Tool Descriptions

Tools provide `withDescription()` and `withNote()` methods to create copies with modified descriptions. This is useful when you need to customize a tool’s description for a specific context without modifying the original tool.

**withDescription(newDescription)**

Creates a new tool with a completely replaced description:

```java
// Replace the entire description
Tool customTool = originalTool.withDescription("Custom description for this context");

// The original tool is unchanged
System.out.println(originalTool.getDefinition().getDescription()); // original description
System.out.println(customTool.getDefinition().getDescription());   // Custom description for this context
```

**withNote(note)**

Creates a new tool with an appended note to the existing description:

```java
// Add a note to the existing description
Tool annotatedTool = originalTool.withNote("Use this when querying large datasets");

// Result: "Original description. Use this when querying large datasets"
System.out.println(annotatedTool.getDefinition().getDescription());
```

Both methods preserve all other tool properties (name, input schema, metadata, functionality):

```java
Tool original = Tool.create("calculator", "Performs calculations",
    Tool.InputSchema.of(Tool.Parameter.integer("x", "Number")),
    input -> Tool.Result.text("42"));

// Create a customized version
Tool customized = original
    .withDescription("Specialized math tool")
    .withNote("Optimized for financial calculations");

// Name and functionality unchanged
assert customized.getDefinition().getName().equals("calculator");
assert customized.call("{}").text().equals("42");
```

##### When to Use Each Approach

| Approach | Use When |
| --- | --- |
| Spring AI `@Tool` | You’re comfortable with Spring AI and want IDE support for tool annotations on domain objects |
| `Tool.create()` / `Tool.of()` | You need programmatic tool creation with simple inputs, want framework independence, or are creating tools dynamically |
| `Tool.fromFunction()` | You need programmatic tool creation with complex typed inputs and outputs, automatic JSON marshaling, and schema generation |
| `@LlmTool` / `@LlmTool.Param` | You prefer annotation-based tools but want Embabel’s framework-agnostic abstractions |
| Tool Groups | You need to organize related tools, use MCP servers, or control tool availability at deployment time |

#### 3.9.4. Tool Decoration: Extending Tool Behavior

Embabel uses a powerful decoration pattern to extend tool behavior without modifying the underlying tool or complicating the `PromptRunner`. A decorated tool wraps another tool, intercepting calls to add functionality like artifact capture, event publishing, or blackboard integration.

This pattern is fundamental to Embabel’s architecture:

- **Subagents** use decoration to wrap agent execution as a tool
- **Asset tracking** uses decoration to capture tool outputs for chatbot interfaces
- **Blackboard publishing** uses decoration to make tool results available to other actions
- **Event streaming** uses decoration to publish tool calls to external systems
- Internal platform features like observability and exception handling also use decoration

##### The DelegatingTool Interface

All tool decorators implement `DelegatingTool`:

```java
public interface DelegatingTool extends Tool {
    Tool getDelegate();
}
```

This allows decorators to be unwrapped when needed, and enables chaining multiple decorators.

##### ArtifactSinkingTool: Capturing Tool Outputs

`ArtifactSinkingTool` captures artifacts from `Tool.Result.WithArtifact` results and sends them to a sink. This is the foundation for making structured tool outputs available elsewhere.

```java
// Capture all artifacts and publish to blackboard
Tool wrapped = Tool.publishToBlackboard(myTool);

// Capture specific types
Tool wrapped = Tool.publishToBlackboard(myTool, SearchResult.class);

// With filtering and transformation
Tool wrapped = Tool.publishToBlackboard(
    myTool,
    SearchResult.class,
    result -> result.getScore() > 0.5,  // filter
    result -> result.getDocument()       // transform
);

// Capture to a custom sink
Tool wrapped = Tool.sinkArtifacts(myTool, SearchResult.class, mySink);
```

##### Built-in Sinks

Embabel provides several `ArtifactSink` implementations:

| Sink | Purpose |
| --- | --- |
| `BlackboardSink` | Publishes to the current `AgentProcess` blackboard, making artifacts available to other actions |
| `ListSink` | Collects artifacts into a list, useful for aggregating results |
| `CompositeSink` | Delegates to multiple sinks, enabling multi-destination publishing |

##### Creating Custom Sinks

Implement `ArtifactSink` to create custom destinations:

```java
// Publish to an event stream
ArtifactSink eventSink = artifact -> {
    eventPublisher.publish(new ToolArtifactEvent(artifact));
};

// Use with any tool
Tool wrapped = Tool.sinkArtifacts(myTool, MyType.class, eventSink);
```

##### How Decoration Enables Extension

The decoration pattern lets Embabel add sophisticated behavior while keeping `PromptRunner` simple. When you use `Subagent.ofClass(MyAgent.class)` (Java) or `Subagent.ofClass(MyAgent::class.java)` (Kotlin), Embabel creates a tool that:

1. Wraps agent execution in a `Tool.call()` method
2. Shares the parent blackboard with the child process
3. Captures the agent’s result as a tool artifact

Similarly, when you configure asset tracking in a chatbot, Embabel wraps tools with `AssetAddingTool` to capture outputs as viewable assets.

This approach has key advantages:

- **Composable**: Multiple decorators can be chained
- **Transparent**: The underlying tool doesn’t know it’s wrapped
- **Extensible**: New behaviors can be added without framework changes
- **Type-safe**: Generic decorators like `ArtifactSinkingTool<T>` preserve type information

#### 3.9.5. Subagent: Agent Handoffs as Tools

A `Subagent` is a specialized `Tool` that delegates to another Embabel agent. When the LLM invokes this tool, it runs the specified agent as a subprocess, sharing the parent process’s blackboard context. This enables composition of agents and "handoff" patterns where one agent delegates specialized tasks to another.

##### Creating Subagents

Subagent uses a fluent builder pattern. First select how to reference the agent, then specify the input type using `consuming()`:

```java
// From an @Agent annotated class (most common)
Subagent.ofClass(ConcertAssembler.class).consuming(ConcertPlan.class)

// By agent name (resolved at runtime from platform)
Subagent.byName("ConcertAssembler").consuming(ConcertPlan.class)

// From an already-resolved Agent instance
Subagent.ofInstance(resolvedAgent).consuming(ConcertPlan.class)

// From an instance of an @Agent annotated class (e.g., a Spring bean)
Subagent.ofAnnotatedInstance(myAgentBean).consuming(ConcertPlan.class)
```

The `consuming()` method specifies the input type that the LLM will provide when invoking this tool. This type is used to generate the JSON schema that guides the LLM’s tool invocation.

##### Using Subagents with PromptRunner

Use `withTool()` to add a Subagent to your prompt:

```java
@Action
public Concert assembleConcert(ConcertPlan plan, OperationContext context) {
    return context.ai()
        .withDefaultLlm()
        .withTool(Subagent.ofClass(PerformanceFinder.class)
                .consuming(WorksToFind.class))  (1)
        .creating(Concert.class)
        .fromPrompt("Assemble a concert based on: " + plan);
}
```

| **1** | The LLM can now invoke `PerformanceFinder` as a tool, providing `WorksToFind` input to delegate the performance search task. |
| --- | --- |

##### Subagent with Asset Tracking

For chat applications that track assets, wrap the Subagent with `AssetAddingTool` to automatically track returned artifacts:

```java
@Action
public Concert assembleConcert(ConcertPlan plan, OperationContext context) {
    var subagent = Subagent.ofClass(PerformanceFinder.class)
            .consuming(WorksToFind.class);
    var trackedSubagent = assetTracker.addReturnedAssets(subagent);  (1)

    return context.ai()
        .withDefaultLlm()
        .withTool(trackedSubagent)
        .creating(Concert.class)
        .fromPrompt("Assemble a concert based on: " + plan);
}

// With filtering - only track certain assets
var trackedSubagent = assetTracker.addReturnedAssets(subagent, asset ->
    asset instanceof Performance  // Only track Performance assets
);
```

| **1** | Wrap with `addReturnedAssets()` to track artifacts returned by the subagent. |
| --- | --- |

##### Input Type and JSON Schema

The input type you specify with `consuming()` determines the JSON schema that the LLM sees when invoking the tool.

For example:

```java
// The input type
public record WorksToFind(List<String> composers, String era, int maxResults) {}

// Create the subagent with explicit input type
Subagent.ofClass(PerformanceFinder.class).consuming(WorksToFind.class)
```

The Subagent tool will:

- Use "PerformanceFinder" as the tool name (from `@Agent` annotation)
- Use "Finds performances" as the tool description (from `@Agent` annotation)
- Generate a JSON schema from `WorksToFind`

**From the LLM’s perspective, a Subagent is just another tool.** The calling LLM sees the JSON schema for `WorksToFind` and can populate it directly:

```json
{
  "composers": ["Mozart", "Beethoven"],
  "era": "Classical",
  "maxResults": 5
}
```

When the tool is invoked, Subagent deserializes this JSON into a `WorksToFind` object and passes it to the target agent. The input type should match the first non-injected parameter of the agent’s entry-point action.

##### Blackboard Sharing

When a Subagent runs, it receives a **spawned blackboard** from the parent process. This means:

- The subagent can read objects from the parent’s blackboard
- Objects added by the subagent are available to the parent after the subagent completes
- The subagent operates in its own process context but shares state appropriately

##### When to Use Subagent

| Scenario | Recommendation |
| --- | --- |
| Complex specialized task that has its own multi-action workflow | Use Subagent - the target agent can plan and execute multiple steps |
| Simple tool call with deterministic logic | Use a regular `@LlmTool` method instead |
| LLM-orchestrated mini-workflow with sub-tools | Consider [AgenticTool](https://docs.embabel.com/embabel-agent/guide/0.3.4-SNAPSHOT/#reference.tools__agentic-tools) which operates at the tool level |
| Need the full power of GOAP planning for the subtask | Subagent is ideal - the target agent uses its own planner |

#### 3.9.6. Agentic Tools

An **agentic tool** is a tool that uses an LLM to orchestrate other tools. Unlike a regular tool which executes deterministic logic, an agentic tool delegates to an LLM that decides which sub-tools to call based on a prompt.

This pattern is useful for encapsulating a mini-orchestration as a single tool that can be used in larger workflows.

Embabel provides three agentic tool implementations, each offering different levels of control over tool availability:

##### Choosing an Agentic Tool

| Tool Type | Tool Availability | Best For | Example Use Case |
| --- | --- | --- | --- |
| `SimpleAgenticTool` | All tools available immediately | Simple orchestration, exploration tasks | Math calculator with add/multiply/divide tools |
| `PlaybookTool` | Progressive unlock via conditions (prerequisites, artifacts, blackboard) | Structured workflows, guided processes | Research workflow: search → analyze → summarize |
| `StateMachineTool` | State-based availability using enum states | Formal state machines, multi-phase processes | Order processing: DRAFT → CONFIRMED → SHIPPED → DELIVERED |

All three implement the `AgenticTool` interface and share a common fluent API with `with*` methods.

The `AgenticTool` interface defines:

```java
public interface AgenticTool<THIS extends AgenticTool<THIS>> extends Tool {
    LlmOptions getLlm();                              // LLM configuration
    int getMaxIterations();                           // Max tool loop iterations (default: 20)

    THIS withLlm(LlmOptions llm);
    THIS withSystemPrompt(String prompt);
    THIS withSystemPrompt(AgenticSystemPromptCreator creator);  // Dynamic prompt
    THIS withMaxIterations(int maxIterations);
    THIS withParameter(Tool.Parameter parameter);
    THIS withToolObject(Object toolObject);
}
```

The `AgenticSystemPromptCreator` functional interface receives both the `ExecutingOperationContext` (for access to blackboard, process options, etc.) and the input string passed to the tool:

```java
tool.withSystemPrompt((ctx, input) ->
    "Context: " + ctx.getProcessContext().getProcessOptions().getContextId() +
    ". Task: " + input
);
```

|  | For complex workflows with defined outputs, branching logic, loops, or state management, use Embabel’s [GOAP planner](https://docs.embabel.com/embabel-agent/guide/0.3.4-SNAPSHOT/#reference.planners), [Utility AI](https://docs.embabel.com/embabel-agent/guide/0.3.4-SNAPSHOT/#reference.planners__utility), or [@State workflows](https://docs.embabel.com/embabel-agent/guide/0.3.4-SNAPSHOT/#reference.states) instead. These provide deterministic, typesafe planning that is far more powerful and predictable than LLM-driven orchestration. |
| --- | --- |

##### SimpleAgenticTool: Flat Tool Orchestration

`SimpleAgenticTool` makes all sub-tools available immediately. The LLM decides freely which tools to use based on the prompt.

```java
import com.embabel.agent.api.tool.agentic.simple.SimpleAgenticTool;

// Create the agentic tool
SimpleAgenticTool mathOrchestrator = new SimpleAgenticTool("math-orchestrator", "Orchestrates math operations")
    .withTools(addTool, multiplyTool, divideTool)
    .withParameter(Tool.Parameter.string("expression", "Math expression to evaluate"))
    .withLlm(LlmOptions.withModel("gpt-4"));

// Use it like any other tool
context.ai()
    .withDefaultLlm()
    .withTool(mathOrchestrator)
    .generateText("What is 5 + 3 * 2?");
```

##### PlaybookTool: Conditional Tool Unlocking

`PlaybookTool` allows tools to be progressively unlocked based on conditions:

- **Prerequisites**: unlock after other tools have been called
- **Artifacts**: unlock when certain artifact types are produced
- **Blackboard**: unlock based on process state
- **Custom predicates**: unlock based on arbitrary conditions

```java
import com.embabel.agent.api.tool.agentic.playbook.PlaybookTool;

// Tools unlock progressively
PlaybookTool researcher = new PlaybookTool("researcher", "Research and analyze topics")
    .withTools(searchTool, fetchTool)                    // Always available
    .withTool(analyzeTool).unlockedBy(searchTool)        // Unlocks after search
    .withTool(summarizeTool).unlockedBy(analyzeTool)     // Unlocks after analyze
    .withParameter(Tool.Parameter.string("topic", "Research topic"));

// Multiple prerequisites (AND)
.withTool(reportTool).unlockedByAll(searchTool, analyzeTool)

// Any prerequisite (OR)
.withTool(processTool).unlockedByAny(searchTool, fetchTool)

// Unlock when artifact type produced
.withTool(formatTool).unlockedByArtifact(Document.class)

// Unlock based on blackboard state
.withTool(actionTool).unlockedByBlackboard(UserProfile.class)

// Custom predicate
.withTool(finalizeTool).unlockedWhen(ctx -> ctx.getIterationCount() >= 3)
```

When a locked tool is called before its conditions are met, the LLM receives an informative message guiding it to use prerequisite tools first.

##### StateMachineTool: State-Based Availability

`StateMachineTool` uses explicit states defined by an enum. Tools are registered with specific states where they’re available, and can trigger transitions to other states.

```java
import com.embabel.agent.api.tool.agentic.state.StateMachineTool;

enum OrderState { DRAFT, CONFIRMED, SHIPPED, DELIVERED }

StateMachineTool<OrderState> orderProcessor = new StateMachineTool<>("orderProcessor", "Process orders", OrderState.class)
    .withInitialState(OrderState.DRAFT)
    .inState(OrderState.DRAFT)
        .withTool(addItemTool)
        .withTool(confirmTool).transitionsTo(OrderState.CONFIRMED)
    .inState(OrderState.CONFIRMED)
        .withTool(shipTool).transitionsTo(OrderState.SHIPPED)
    .inState(OrderState.SHIPPED)
        .withTool(deliverTool).transitionsTo(OrderState.DELIVERED)
    .inState(OrderState.DELIVERED)
        .withTool(reviewTool).build()
    .withGlobalTools(statusTool, helpTool)  // Available in all states
    .withParameter(Tool.Parameter.string("orderId", "Order to process"));
```

The `startingIn(state)` method allows starting in a different state at runtime:

```java
// Resume an order that's already confirmed
Tool resumedProcessor = orderProcessor.startingIn(OrderState.CONFIRMED);
```

##### Domain Tools: Tools from Retrieved Objects

All three agentic tools support **domain tools** - `@LlmTool` methods on domain objects that become available when a single instance is retrieved.

```java
// Domain class with @LlmTool methods
public class User {
    private final String id;
    private final String name;

    public User(String id, String name) {
        this.id = id;
        this.name = name;
    }

    @LlmTool(description = "Get user's profile information")
    public String getProfile() {
        return "Profile for " + name;
    }

    @LlmTool(description = "Update user's settings")
    public String updateSettings(String settings) {
        return "Settings updated for " + name;
    }
}

// Register domain tools - they become available when a single User is retrieved
PlaybookTool userManager = new PlaybookTool("userManager", "Manage users")
    .withTools(searchUserTool, getUserTool)
    .withToolChainingFrom(User.class);  // User methods available after getUserTool returns a single User
```

Domain tools are "declared" to the LLM immediately but return an error until an instance is bound. When a tool returns a **single** artifact (not a collection) of a registered type, that instance is bound and its `@LlmTool` methods become executable.

##### Creating Agentic Tools

Create agentic tools using the constructor and fluent `with*` methods:

```java
// Create sub-tools
Tool addTool = Tool.create("add", "Adds two numbers", input -> {
    // Parse JSON input and compute result
    return Tool.Result.text("5");
});

Tool multiplyTool = Tool.create("multiply", "Multiplies two numbers", input -> {
    return Tool.Result.text("6");
});

// Create the agentic tool
SimpleAgenticTool mathOrchestrator = new SimpleAgenticTool("math-orchestrator", "Orchestrates math operations")
    .withTools(addTool, multiplyTool)
    .withLlm(LlmOptions.withModel("gpt-4"))
    .withSystemPrompt("Use the available tools to solve the given math problem");

// Use it like any other tool
context.ai()
    .withDefaultLlm()
    .withTool(mathOrchestrator)
    .generateText("What is 5 + 3 * 2?");
```

|  | The `withSystemPrompt` call is optional. By default, agentic tools generate a system prompt from the tool’s description: *"You are an intelligent agent that can use tools to help you complete tasks. Use the provided tools to perform the following task: {description}"*. Only call `withSystemPrompt` if you need custom orchestration instructions. |
| --- | --- |

##### Defining Input Parameters

|  | You must define input parameters for your agentic tool so the LLM knows what arguments to pass when calling it. Without parameters, the LLM won’t know what input format to use. |
| --- | --- |

Use the `withParameter` method with `Tool.Parameter` factory methods for concise parameter definitions:

```java
// Research tool that requires a topic parameter
SimpleAgenticTool researcher = new SimpleAgenticTool("researcher", "Research a topic thoroughly")
    .withParameter(Tool.Parameter.string("topic", "The topic to research"))
    .withToolObjects(new SearchTools(), new SummarizerTools());

// Calculator with multiple parameters
SimpleAgenticTool calculator = new SimpleAgenticTool("smart-calculator", "Perform complex calculations")
    .withParameter(Tool.Parameter.string("expression", "Mathematical expression to evaluate"))
    .withParameter(Tool.Parameter.integer("precision", "Decimal places for result", false))  // optional
    .withToolObject(new MathTools());
```

Available parameter factory methods:

- `Tool.Parameter.string(name, description, required?)` - String parameter
- `Tool.Parameter.integer(name, description, required?)` - Integer parameter
- `Tool.Parameter.double(name, description, required?)` - Floating-point parameter

All factory methods default to `required = true`. Set `required = false` for optional parameters.

##### Creating Agentic Tools from Annotated Objects

Use `withToolObject` or `withToolObjects` to add tools from objects with `@LlmTool` -annotated methods:

```java
// Tool classes with @LlmTool methods
public class SearchTools {
    @LlmTool(description = "Search the web")
    public String search(String query) { return "Results for: " + query; }
}

public class CalculatorTools {
    @LlmTool(description = "Add two numbers")
    public int add(int a, int b) { return a + b; }

    @LlmTool(description = "Multiply two numbers")
    public int multiply(int a, int b) { return a * b; }
}

// Create agentic tool with tools from multiple objects
// Uses default system prompt based on description
SimpleAgenticTool assistant = new SimpleAgenticTool("assistant", "Multi-capability assistant")
    .withToolObjects(new SearchTools(), new CalculatorTools());

// With LLM options and custom system prompt
SimpleAgenticTool smartAssistant = new SimpleAgenticTool("smart-assistant", "Smart assistant")
    .withToolObjects(new SearchTools(), new CalculatorTools())
    .withLlm(LlmOptions.withModel("gpt-4"))
    .withSystemPrompt("Use tools intelligently");
```

Objects without `@LlmTool` methods are silently ignored, allowing you to mix objects safely.

##### Agentic Tools with Spring Dependency Injection

Agentic tools can encapsulate stateful services via dependency injection:

```java
@Component
public class ResearchOrchestrator {

    private final WebSearchService webSearchService;
    private final SummarizerService summarizerService;

    public ResearchOrchestrator(WebSearchService webSearchService, SummarizerService summarizerService) {
        this.webSearchService = webSearchService;
        this.summarizerService = summarizerService;
    }

    @LlmTool(description = "Search the web for information")
    public List<SearchResult> search(String query) {
        return webSearchService.search(query);
    }

    @LlmTool(description = "Summarize text content")
    public String summarize(String content) {
        return summarizerService.summarize(content);
    }
}

// In your configuration
@Configuration
public class ToolConfiguration {

    @Bean
    public SimpleAgenticTool researchTool(ResearchOrchestrator orchestrator) {
        return new SimpleAgenticTool("research-assistant", "Research topics using web search and summarization")
            .withToolObject(orchestrator)
            .withLlm(new LlmOptions().withRole("smart"));
            // Uses default system prompt based on description
    }
}
```

##### How Agentic Tools Execute

When an agentic tool’s `call()` method is invoked:

1. The tool retrieves the current `AgentProcess` context
2. It configures a `PromptRunner` with the specified `LlmOptions`
3. It adds all sub-tools to the prompt runner
4. It executes the prompt with the input, allowing the LLM to orchestrate the sub-tools
5. The final LLM response is returned as the tool result

This means agentic tools create a nested LLM interaction: the outer LLM decides to call the agentic tool, then the inner LLM orchestrates the sub-tools.

##### Modifying Agentic Tools

Use the `with*` methods to create modified copies:

```java
SimpleAgenticTool base = new SimpleAgenticTool("base", "Base orchestrator")
    .withTools(tool1)
    .withSystemPrompt("Original prompt");

// Create copies with modifications
SimpleAgenticTool withNewLlm = base.withLlm(new LlmOptions().withModel("gpt-4"));
SimpleAgenticTool withMoreTools = base.withTools(tool2, tool3);
SimpleAgenticTool withNewPrompt = base.withSystemPrompt("Updated prompt");

// Add input parameters
SimpleAgenticTool withParams = base.withParameter(Tool.Parameter.string("query", "Search query"));

// Add tools from an object with @LlmTool methods
SimpleAgenticTool withAnnotatedTools = base.withToolObject(calculatorService);

// Add tools from multiple objects
SimpleAgenticTool withMultipleObjects = base.withToolObjects(searchService, calculatorService);

// Dynamic system prompt based on execution context and input
SimpleAgenticTool withDynamicPrompt = base.withSystemPrompt((ctx, input) -> {
    String contextId = ctx.getProcessContext().getProcessOptions().getContextId().getId();
    return "Process requests for context " + contextId + ". Task: " + input;
});
```

The available modification methods are:

- `withParameter(Tool.Parameter)`: Add an input parameter (use `Tool.Parameter.string()`, `.integer()`, `.double()`)
- `withLlm(LlmOptions)`: Set LLM configuration
- `withTools(vararg Tool)`: Add additional Tool instances
- `withToolObject(Any)`: Add tools from an object with `@LlmTool` methods
- `withToolObjects(vararg Any)`: Add tools from multiple annotated objects
- `withSystemPrompt(String)`: Set a fixed system prompt
- `withSystemPrompt((ExecutingOperationContext, String) → String)`: Set a dynamic prompt based on execution context and input
- `withCaptureNestedArtifacts(Boolean)`: Control whether artifacts from nested agentic tool calls are captured (default: `false`)
- `withToolChainingFrom(Class<T>)`: Register a class whose `@LlmTool` methods become available when an artifact of that type is returned
- `withToolChainingFrom(Class<T>, DomainToolPredicate<T>)`: Register with a predicate to filter which instances contribute tools
- `withToolChainingFromAny()`: Auto-discover tools from any returned artifact with `@LlmTool` methods

##### Controlling Artifact Capture in Nested Agentic Tools

When an agentic tool orchestrates other tools, those sub-tools may return artifacts (via `Tool.Result.WithArtifact`). By default, artifacts from nested agentic tool calls are **not** captured—only the final result from the outermost agentic tool is returned.

This prevents intermediate artifacts from bubbling up when you only care about the final result. For example, if an outer `assembleConcert` tool calls an inner `findPerformances` tool, you typically want only the final `Concert` artifact, not all the intermediate `Performance` artifacts.

Use `withCaptureNestedArtifacts(true)` if you need to capture artifacts from nested agentic tools:

```java
// Default: nested artifacts are NOT captured
SimpleAgenticTool concertAssembler = new SimpleAgenticTool("assembleConcert", "Assemble a concert program")
    .withTools(findPerformancesTool, createConcertTool);
// Only the Concert artifact from createConcert is returned

// Opt-in: capture all nested artifacts
SimpleAgenticTool fullCapture = concertAssembler.withCaptureNestedArtifacts(true);
// Both Performance artifacts from findPerformances AND Concert from createConcert are captured
```

|  | This setting only affects artifacts from nested agentic tool calls. Artifacts from regular (non-agentic) tools are always captured. |
| --- | --- |

##### Tool Chaining

When working with objects returned by tools, you often want to expose `@LlmTool` methods on those objects as additional tools—but only after the object has been retrieved. The `withToolChainingFrom()` method enables this pattern.

|  | Tool chaining increases determinism. Once a tool returns a specific object, the LLM gains access to that object’s business methods—navigating a data structure through well-defined operations rather than unstructured reasoning. This keeps the LLM on a guided path through your domain logic. |
| --- | --- |

Tool chaining is available on both `AgenticTool` and `PromptRunner`, via the shared `ToolChaining` interface. This means you can use tool chaining not only in agentic tool loops, but also in simple `createObject` and `generateText` calls through `PromptRunner`. This is significant because it enables any action to dynamically discover and use tools from returned artifacts without requiring a full agentic tool setup.

When you register a class, placeholder tools are created for each `@LlmTool` method on that class. Initially, these tools return "not available yet" messages. When a tool returns an artifact matching the registered type, the placeholder tools become active and delegate to the bound instance.

**Last Wins Semantics**: When multiple artifacts of the same type are returned, only the most recent one’s tools are active. This ensures the LLM always works with the "current" instance.

```java
// Domain class with tool methods
public class User {
    private final String id;
    private String email;

    @LlmTool("Update the user's email address")
    public String updateEmail(String newEmail) {
        this.email = newEmail;
        return "Email updated to " + newEmail;
    }
}

// Create agentic tool with tool chaining
SimpleAgenticTool userManager = new SimpleAgenticTool("userManager", "Manage user accounts")
    .withTools(searchUserTool, getUserTool)           // Tools to find/retrieve users
    .withToolChainingFrom(User.class);                // User methods become tools when retrieved

// Flow:
// 1. LLM calls searchUserTool to find users
// 2. LLM calls getUserTool which returns a User artifact
// 3. updateEmail() becomes available as a tool bound to that User
// 4. LLM calls updateEmail("new@example.com")
```

###### Predicate-Based Filtering

You can control which instances contribute tools using a predicate. The predicate receives the artifact and the current `AgentProcess`, allowing filtering based on object state or process context.

```java
// Only expose tools for admin users
SimpleAgenticTool adminManager = new SimpleAgenticTool("adminManager", "Manage admin users")
    .withTools(searchUserTool, getUserTool)
    .withToolChainingFrom(User.class, (user, agentProcess) ->
        user.getRole().equals("admin")
    );

// Regular users won't have their tools exposed
// Only when an admin User is retrieved will updateEmail() become available
```

###### Auto-Discovery Mode

For maximum flexibility, use `withToolChainingFromAny()` to automatically discover and expose tools from any returned artifact that has `@LlmTool` methods. Unlike registered sources, auto-discovery replaces ALL previous bindings when a new artifact is discovered—ensuring only one "current" object’s tools are active at a time.

```java
// Auto-discover tools from any returned object
SimpleAgenticTool explorer = new SimpleAgenticTool("explorer", "Explore and manipulate objects")
    .withTools(searchTool, getTool)
    .withToolChainingFromAny();  // Tools from any returned object are exposed

// Flow:
// 1. LLM calls getTool which returns a User -> User tools are available
// 2. LLM calls another getTool which returns an Order -> Order tools replace User tools
// 3. Only the most recent object's tools are active
```

This pattern is useful when:

- **Objects have operations**: The object itself knows how to perform actions (e.g., `user.updateEmail()`, `order.cancel()`)
- **Context-dependent tools**: Operations only make sense after retrieving a specific instance
- **Clean API design**: Tools are defined on the class rather than as separate tool classes
- **Exploratory workflows**: The LLM dynamically works with whatever object is "current"

All agentic tool types support tool chaining:

- `SimpleAgenticTool`: Chained tools are available as soon as an artifact is returned
- `PlaybookTool`: Chained tools are available immediately (not subject to unlock conditions)
- `StateMachineTool`: Chained tools are available globally (not state-bound)

###### Tool Chaining on PromptRunner

Tool chaining is not limited to agentic tools. Because both `AgenticTool` and `PromptRunner` implement the `ToolChaining` interface, you can use `withToolChainingFrom()` and `withToolChainingFromAny()` directly on a `PromptRunner` obtained from an action’s `OperationContext`.

This is important because it enables dynamic tool discovery within simple `createObject` and `generateText` calls—without requiring a full `SimpleAgenticTool` wrapper.

```java
// In an @Action method:
PromptRunner ai = context.ai()
    .withToolChainingFrom(User.class)       // Chained tools from User
    .withTools(searchUserTool, getUserTool);

// When getUserTool returns a User artifact, User's @LlmTool methods
// automatically become available for the LLM to call
String result = ai.generateText("Find user Alice and update her email to alice@new.com");
```

##### Filtering Artifacts for Asset Tracking

When using tools with an `AssetTracker` (common in chat applications), you can filter which artifacts become tracked assets. The `addReturnedAssets` and `addAnyReturnedAssets` methods accept a `Predicate<Asset>` filter that works with both Java and Kotlin:

```java
// Track only assets that pass the filter
Tool wrapped = assetTracker.addReturnedAssets(concertTool, asset -> {
    // Only track concerts with at least 3 works
    return asset instanceof Concert concert && concert.getWorks().size() >= 3;
});

// Apply the same filter to multiple tools
List<Tool> wrappedTools = assetTracker.addAnyReturnedAssets(
    List.of(tool1, tool2, tool3),
    asset -> asset.getId().startsWith("important-")
);
```

The filter is applied after type matching, so you can use type-specific criteria to decide which artifacts are worth tracking.

##### Migration from Other Frameworks

If you’re coming from frameworks like LangChain or Google ADK, Embabel’s agentic tools provide a familiar pattern similar to their "supervisor" architectures:

| Framework | Pattern | Embabel Equivalent |
| --- | --- | --- |
| LangChain/LangGraph | Supervisor agent with worker agents | `SimpleAgenticTool` with sub-tools |
| Google ADK | Coordinator with `sub_agents` / `AgentTool` | `SimpleAgenticTool` with sub-tools |

The key differences:

- **Tool-centric**: Embabel’s agentic tools operate at the tool level, not the agent level. They’re lightweight and can be mixed freely with regular tools.
- **Simpler model**: No graph-based workflows or explicit Sequential/Parallel/Loop patterns—just LLM-driven orchestration.
- **Composable**: An agentic tool is still "just a tool" that can be used anywhere tools are accepted.

However, for anything beyond simple orchestration, Embabel offers far more powerful alternatives:

| Scenario | Use This Instead |
| --- | --- |
| Business processes with defined outputs | [GOAP planner](https://docs.embabel.com/embabel-agent/guide/0.3.4-SNAPSHOT/#reference.planners) - deterministic, goal-oriented planning with preconditions and effects |
| Exploration and event-driven systems | [Utility AI](https://docs.embabel.com/embabel-agent/guide/0.3.4-SNAPSHOT/#reference.planners__utility) - selects highest-value action at each step |
| Branching, looping, or stateful workflows | [@State workflows](https://docs.embabel.com/embabel-agent/guide/0.3.4-SNAPSHOT/#reference.states) - typesafe state machines with GOAP planning within each state |

These provide **deterministic, typesafe planning** that is far more predictable and powerful than supervisor-style LLM orchestration. Use `SimpleAgenticTool` for simple cases, `PlaybookTool` for structured workflows, or `StateMachineTool` for formal state machines. Graduate to GOAP, Utility, or @State for production workflows where predictability matters.

|  | For supervisor-style orchestration with typed outputs and full blackboard state management, see [SupervisorInvocation](https://docs.embabel.com/embabel-agent/guide/0.3.4-SNAPSHOT/#reference.planners__supervisor-invocation). It operates at a higher level than agentic tools, orchestrating `@Action` methods rather than `Tool` instances, and produces typed goal objects with currying support. |
| --- | --- |

#### 3.9.7. Progressive Tools

> Great fleas have little fleas upon their backs to bite 'em,  
> And little fleas have lesser fleas, and so ad infinitum.  
> And the great fleas themselves, in turn, have greater fleas to go on;  
> While these again have greater still, and greater still, and so on.

— Augustus De Morgan

**Progressive tools** enable dynamic tool disclosure—presenting a simplified interface initially, then revealing more granular tools based on context or when the LLM expresses intent.

##### The Progressive Tool Hierarchy

Embabel provides a hierarchy of progressive tool interfaces:

- **`ProgressiveTool`**: The base interface for tools that can reveal inner tools based on context. Its `innerTools(process: AgentProcess)` method returns tools that may vary depending on the current agent process state.
- **`UnfoldingTool`**: A `ProgressiveTool` with a fixed set of inner tools. When invoked, it "unfolds" to reveal its contents—like opening a folded map to see the details inside. This is the most commonly used progressive tool type.

An `UnfoldingTool` presents a high-level description to the LLM and, when invoked, exposes its inner tools. This pattern is useful for **progressive tool disclosure** —reducing initial complexity while allowing access to detailed functionality on demand.

##### When to Use UnfoldingTool

UnfoldingTool is useful when:

- You have many related tools that might overwhelm the LLM with choices
- You want to group tools by category (e.g., "database operations", "file operations")
- You want the LLM to express intent before revealing detailed options
- You need to reduce token usage for tool descriptions

##### Creating a Simple UnfoldingTool

The simplest form exposes all inner tools when invoked:

```java
import com.embabel.agent.api.tool.progressive.UnfoldingTool;
import com.embabel.agent.api.tool.Tool;

// Create inner tools
Tool queryTool = Tool.create("query_table", "Execute a SQL query",
    Tool.InputSchema.of(Tool.Parameter.string("sql", "The SQL query to execute")),
    input -> Tool.Result.text("{\"rows\": 5}")
);

Tool insertTool = Tool.create("insert_record", "Insert a new record",
    Tool.InputSchema.of(Tool.Parameter.string("table", "Table name")),
    input -> Tool.Result.text("{\"id\": 123}")
);

Tool deleteTool = Tool.create("delete_record", "Delete a record",
    Tool.InputSchema.of(Tool.Parameter.integer("id", "Record ID to delete")),
    input -> Tool.Result.text("{\"deleted\": true}")
);

// Create the UnfoldingTool facade
var databaseTool = UnfoldingTool.of(
    "database_operations",
    "Use this tool to work with the database. Invoke to see specific operations.",
    List.of(queryTool, insertTool, deleteTool)
);
```

##### Fluent Builder API

UnfoldingTool supports a fluent builder pattern for combining tools from multiple sources. Use `withTools()` to add individual tools or `withToolObject()` to add tools from `@LlmTool` annotated objects:

```java
import com.embabel.agent.api.tool.progressive.UnfoldingTool;

// Start with base tools and add more
var combinedTools = UnfoldingTool.of(
        "workspace",
        "Workspace operations. Invoke to see available tools.",
        List.of(baseTool))
    .withTools(searchTool, filterTool)           // Add individual tools
    .withToolObject(new DatabaseOperations())    // Add from @LlmTool class
    .withToolObject(new FileOperations());       // Chain multiple sources
```

This is useful when:

- **Combining existing tools**: Merge tools from different sources into one progressive facade
- **Adding ad-hoc tools**: Start with annotated tool classes and add programmatic tools
- **Context-specific grouping**: Build different tool combinations for different invocation contexts

The builder preserves all properties (`removeOnInvoke`, `childToolUsageNotes`) from the original UnfoldingTool.

##### Category-Based Tool Selection

Use `byCategory` to expose different tools based on the category the LLM selects:

```java
import com.embabel.agent.api.tool.progressive.UnfoldingTool;
import java.util.Map;

// Define tools by category
Map<String, List<Tool>> toolsByCategory = Map.of(
    "read", List.of(readFileTool, listDirectoryTool, searchFilesTool),
    "write", List.of(writeFileTool, deleteFileTool, moveFileTool)
);

// Create category-based UnfoldingTool
var fileTool = UnfoldingTool.byCategory(
    "file_operations",
    "File operations. Pass category: 'read' for reading files, 'write' for modifying files.",
    toolsByCategory
);

// The tool's schema automatically includes the category as an enum parameter
// When invoked with {"category": "read"}, only read tools are exposed
// When invoked with {"category": "write"}, only write tools are exposed
```

##### Custom Selection Logic

For more complex selection logic, use `selectable`:

```java
import com.embabel.agent.api.tool.progressive.UnfoldingTool;
import com.fasterxml.jackson.databind.ObjectMapper;

List<Tool> allTools = List.of(basicTool, advancedTool, adminTool);

var permissionBasedTool = UnfoldingTool.selectable(
    "api_operations",
    "API operations. Pass 'accessLevel': 'basic', 'advanced', or 'admin'.",
    allTools,
    Tool.InputSchema.of(
        Tool.Parameter.string("accessLevel", "Access level for operations",
            true, List.of("basic", "advanced", "admin"))
    ),
    true,  // removeOnInvoke
    input -> {
        // Custom selection logic
        try {
            ObjectMapper mapper = new ObjectMapper();
            Map<String, Object> params = mapper.readValue(input, Map.class);
            String level = (String) params.get("accessLevel");
            return switch (level) {
                case "basic" -> List.of(basicTool);
                case "advanced" -> List.of(basicTool, advancedTool);
                case "admin" -> allTools;
                default -> List.of(basicTool);
            };
        } catch (Exception e) {
            return List.of(basicTool);
        }
    }
);
```

##### Keeping the Facade Available

By default, a UnfoldingTool is removed after invocation (replaced by its inner tools). Set `removeOnInvoke = false` to keep the facade available for re-invocation with different arguments:

```java
// Facade stays available after invocation
var persistentTool = UnfoldingTool.of(
    "operations",
    "Operations. Invoke multiple times with different needs.",
    allTools,
    false  // removeOnInvoke = false
);
```

##### Enabling UnfoldingTool in the Tool Loop

UnfoldingTool is **enabled by default** when using Embabel’s tool loop. The `ToolInjectionStrategy.DEFAULT` includes `UnfoldingToolInjectionStrategy`, so no additional configuration is needed.

If you need to combine with custom strategies, use `ChainedToolInjectionStrategy`:

```java
import com.embabel.agent.spi.loop.ChainedToolInjectionStrategy;

// Combine UnfoldingTool support with custom strategies
ChainedToolInjectionStrategy combined =
    ChainedToolInjectionStrategy.withUnfolding(customStrategy1, customStrategy2);
```

##### How UnfoldingToolWorks

1. **Initial state**: The LLM sees only the facade tool (e.g., "database\_operations")
2. **LLM invokes**: The LLM calls the facade with optional arguments
3. **Strategy evaluates**: `UnfoldingToolInjectionStrategy` detects the invocation
4. **Tools replaced**: The facade is removed (if `removeOnInvoke` is true) and inner tools are added
5. **Continue**: The LLM now sees and can use the specific inner tools

This flow reduces the initial tool set complexity while allowing the LLM to access detailed tools when it needs them.

##### Context Preservation and Usage Notes

When a UnfoldingTool is expanded, its child tools replace the facade. Without context preservation, the LLM would lose important information about *why* these tools are grouped together.

For example, a "spotify\_search" tool containing `vector_search`, `text_search`, and `regex_search` would expand to just three generic search tools - the LLM wouldn’t know these are specifically for searching Spotify music data.

Embabel solves this by automatically injecting a **context tool** alongside the child tools. This context tool:

- Preserves the parent’s description ("Search Spotify for music data")
- Lists the available child tools
- Includes optional usage notes (via `childToolUsageNotes`)

The `childToolUsageNotes` parameter provides guidance on when and how to use the child tools. This guidance appears **once** in the context tool rather than being duplicated in each child tool’s description:

```java
var spotifySearch = UnfoldingTool.of(
    "spotify_search",
    "Search Spotify for music data including artists, albums, and tracks.",
    List.of(vectorSearchTool, textSearchTool, regexSearchTool),
    true,  // removeOnInvoke
    "Try vector search first for semantic queries like 'upbeat jazz'. " +
    "Use text search for exact artist or album names. " +
    "Use regex search for pattern matching on metadata."
);
```

After the LLM invokes `spotify_search`, it will see:

- `vector_search` - the actual search tool
- `text_search` - the actual search tool
- `regex_search` - the actual search tool
- `spotify_search_context` - context tool with description and usage notes

The context tool’s description includes the original purpose and available tools. When called, it returns full details about each child tool plus the usage notes - providing a single reference point without polluting individual tool descriptions.

##### Annotation-Based UnfoldingTool

For a more declarative approach, use the `@UnfoldingTools` class annotation combined with `@LlmTool` method annotations:

```java
import com.embabel.agent.api.annotation.UnfoldingTools;
import com.embabel.agent.api.annotation.LlmTool;

@UnfoldingTools(
    name = "database_operations",
    description = "Database operations. Invoke to see specific tools."
)
public class DatabaseTools {

    @LlmTool(description = "Execute a SQL query")
    public QueryResult query(String sql) {
        // implementation
    }

    @LlmTool(description = "Insert a record")
    public InsertResult insert(String table, Map<String, Object> data) {
        // implementation
    }

    @LlmTool(description = "Delete a record")
    public void delete(long id) {
        // implementation
    }
}

// Create the UnfoldingTool from the annotated class
var tool = UnfoldingTool.fromInstance(new DatabaseTools());
```

You can also specify `childToolUsageNotes` in the annotation to provide guidance on using the child tools:

```java
@UnfoldingTools(
    name = "music_search",
    description = "Search music database for artists, albums, and tracks",
    childToolUsageNotes = "Try vector search first for semantic queries. " +
        "Use text search for exact artist names."
)
public class MusicSearchTools {

    @LlmTool(description = "Semantic search using embeddings")
    public List<Track> vectorSearch(String query) {
        // implementation
    }

    @LlmTool(description = "Exact match text search")
    public List<Track> textSearch(String query) {
        // implementation
    }
}
```

##### Category-Based Selection with Annotations

Add `category` to `@LlmTool` annotations to automatically create a category-based UnfoldingTool:

```java
@UnfoldingTools(
    name = "file_operations",
    description = "File operations. Pass category: 'read' or 'write'."
)
public class FileTools {

    @LlmTool(description = "Read file contents", category = "read")
    public String readFile(String path) {
        return Files.readString(Path.of(path));
    }

    @LlmTool(description = "List directory contents", category = "read")
    public List<String> listDir(String path) {
        return Files.list(Path.of(path)).map(Path::toString).toList();
    }

    @LlmTool(description = "Write file contents", category = "write")
    public void writeFile(String path, String content) {
        Files.writeString(Path.of(path), content);
    }

    @LlmTool(description = "Delete a file", category = "write")
    public void deleteFile(String path) {
        Files.delete(Path.of(path));
    }
}

// Automatically creates category-based selection
var tool = UnfoldingTool.fromInstance(new FileTools());
// When invoked with {"category": "read"}, only read tools are exposed
// When invoked with {"category": "write"}, only write tools are exposed
```

##### @UnfoldingTools Annotation Attributes

| Attribute | Type | Default | Description |
| --- | --- | --- | --- |
| `name` | String | Required | Name of the facade tool the LLM will see |
| `description` | String | Required | Description explaining the tool category |
| `removeOnInvoke` | boolean | `true` | Whether to remove the facade after invocation |
| `categoryParameter` | String | `"category"` | Name of the parameter for category selection |

##### @LlmTool Category Attribute

The `category` attribute on `@LlmTool` is used when the containing class has `@UnfoldingTools`:

- Tools with the same category are grouped together
- Tools without a category are added to all category groups plus an "all" category
- If no tools have categories, a simple (non-category-based) UnfoldingTool is created

##### Real-World Example: Spotify Integration

Here’s a real-world example from the Impromptu chatbot that uses `@UnfoldingTools` to progressively disclose Spotify functionality:

```java
@UnfoldingTools(
    name = "spotify",
    description = "Access Spotify music features. Invoke this tool to enable Spotify " +
            "operations like playing music, searching tracks, managing playlists, " +
            "and controlling playback."
)
public record SpotifyTools(ImpromptuUser user, SpotifyService spotifyService) {

    @LlmTool(description = "Check if user has linked their Spotify account")
    public String checkSpotifyStatus() { /* ... */ }

    @LlmTool(description = "Get the user's Spotify playlists")
    public String getPlaylists() { /* ... */ }

    @LlmTool(description = "Search for tracks on Spotify by song name, artist, or both")
    public String searchTracks(String query) { /* ... */ }

    @LlmTool(description = "Play a track on Spotify by searching for it")
    public String playTrack(String query) { /* ... */ }

    @LlmTool(description = "Pause the current Spotify playback")
    public String pausePlayback() { /* ... */ }

    // ... more tools
}
```

With this setup:

1. The LLM initially sees a single `spotify` tool
2. When the user says "play some jazz", the LLM invokes `spotify`
3. The `spotify` facade is replaced with all the inner tools (`getPlaylists`, `searchTracks`, `playTrack`, etc.)
4. The LLM can then call `searchTracks` or `playTrack` to fulfill the request

##### Auto-Detection with Tool.fromInstance()

When you use `Tool.fromInstance()` on a class annotated with `@UnfoldingTools`, it automatically creates an `UnfoldingTool`:

```java
// Auto-detects @UnfoldingTools and creates an UnfoldingTool
List<Tool> tools = Tool.fromInstance(new SpotifyTools(user, service));
// Returns a single UnfoldingTool, not individual tools
```

This works seamlessly with `withToolObject()` on PromptRunner:

```java
context.ai()
    .withToolObject(new SpotifyTools(user, spotifyService))
    .respond("Play some classical music");
// The SpotifyTools are automatically exposed as a single UnfoldingTool facade
```

##### Nested UnfoldingTools

UnfoldingTools can be nested for multi-level progressive disclosure. This enables organizing large tool collections into logical hierarchies where the LLM navigates by invoking facade tools.

###### Programmatic Nesting

Use `UnfoldingTool.of()` to create nested hierarchies programmatically:

```java
// Inner UnfoldingTool for user management
var userManagement = UnfoldingTool.of(
    "user_management",
    "User management operations",
    List.of(createUserTool, deleteUserTool, updateUserTool)
);

// Inner UnfoldingTool for system config
var systemConfig = UnfoldingTool.of(
    "system_config",
    "System configuration operations",
    List.of(updateConfigTool, backupTool, restoreTool)
);

// Outer UnfoldingTool containing both
var adminTool = UnfoldingTool.of(
    "admin_operations",
    "Administrative operations. Invoke to see categories.",
    List.of(userManagement, systemConfig)
);

// Flow:
// 1. LLM sees: admin_operations
// 2. LLM invokes: admin_operations -> sees: user_management, system_config
// 3. LLM invokes: user_management -> sees: createUser, deleteUser, updateUser
```

###### Annotation-Based Nesting with Inner Classes

You can also create nested hierarchies using `@UnfoldingTools` annotations on inner classes. When `UnfoldingTool.fromInstance()` is called, it automatically discovers and includes any nested inner classes that are also annotated with `@UnfoldingTools`:

```java
@UnfoldingTools(
    name = "admin_operations",
    description = "Administrative operations. Invoke to access specific areas."
)
public class AdminTools {

    @LlmTool(description = "Get system status")
    public String getStatus() {
        return "System is healthy";
    }

    // Nested inner class - automatically discovered and included as a nested UnfoldingTool
    @UnfoldingTools(
        name = "user_management",
        description = "User management operations. Invoke to see specific tools."
    )
    public static class UserManagement {

        @LlmTool(description = "Create a new user")
        public String createUser(String username) { return "Created user: " + username; }

        @LlmTool(description = "Delete a user")
        public String deleteUser(String username) { return "Deleted user: " + username; }

        // Can nest even deeper
        @UnfoldingTools(
            name = "user_permissions",
            description = "User permission operations"
        )
        public static class Permissions {

            @LlmTool(description = "Grant permission to user")
            public String grant(String user, String permission) { return "Granted"; }

            @LlmTool(description = "Revoke permission from user")
            public String revoke(String user, String permission) { return "Revoked"; }
        }
    }

    @UnfoldingTools(
        name = "system_config",
        description = "System configuration. Invoke to see config tools."
    )
    public static class SystemConfig {

        @LlmTool(description = "Update configuration")
        public String updateConfig(String key, String value) { return "Updated"; }

        @LlmTool(description = "Backup configuration")
        public String backup() { return "Backed up"; }
    }
}

// Create the full nested hierarchy automatically
var adminTool = UnfoldingTool.fromInstance(new AdminTools());

// Flow:
// 1. LLM sees: admin_operations
// 2. LLM invokes: admin_operations -> sees: getStatus, user_management, system_config
// 3. LLM invokes: user_management -> sees: createUser, deleteUser, user_permissions
// 4. LLM invokes: user_permissions -> sees: grant, revoke
```

This approach provides several benefits:

- **Encapsulation**: All related tools are organized in a single class hierarchy
- **Automatic discovery**: No manual wiring - inner classes with `@UnfoldingTools` are automatically included
- **Arbitrary depth**: Nest as many levels as needed to organize your tools logically
- **Mixed content**: Each level can have both direct `@LlmTool` methods and nested `@UnfoldingTools` classes

##### Dynamically Configured Inner Tools

A powerful pattern with `UnfoldingTool.selectable()` is creating inner tools that are **configured** based on the parameters passed when invoking the facade. The selector function can create new tool instances with captured state, connection strings, or other configuration:

```java
// UnfoldingTool that configures database tools based on connection parameter
var databaseTool = UnfoldingTool.selectable(
    "database",
    "Database operations. Pass 'connection' to configure tools.",
    Collections.emptyList(),  // Tools created dynamically
    Tool.InputSchema.of(
        Tool.Parameter.string("connection", "Database connection string")
    ),
    true,  // removeOnInvoke
    input -> {
        // Parse connection from input
        ObjectMapper mapper = new ObjectMapper();
        Map<String, Object> params = mapper.readValue(input, Map.class);
        String connection = (String) params.getOrDefault("connection", "localhost");

        // Create tools configured with the connection string
        return List.of(
            Tool.create("query", "Query database at " + connection, queryInput -> {
                // Tool has captured the connection string
                return Tool.Result.text("Queried " + connection + ": " + queryInput);
            }),
            Tool.create("insert", "Insert into database at " + connection, insertInput -> {
                return Tool.Result.text("Inserted into " + connection);
            })
        );
    }
);

// When LLM invokes with {"connection": "prod-db.example.com"}
// The injected tools are configured to use that specific connection
```

This pattern is useful for:

- **Multi-tenant systems**: Configure tools with tenant-specific credentials or endpoints
- **Environment selection**: Let the LLM choose between dev/staging/prod environments
- **Stateful operations**: Create tools that share state (like a shopping cart’s item list)
- **Dynamic service discovery**: Configure tools based on runtime service locations

###### Example: Stateful Shopping Cart Tools

```java
var cartTool = UnfoldingTool.selectable(
    "shopping_cart",
    "Shopping cart. Pass 'cart_id' to select which cart to operate on.",
    Collections.emptyList(),
    Tool.InputSchema.of(
        Tool.Parameter.string("cart_id", "Shopping cart ID")
    ),
    true,
    input -> {
        // Each invocation creates a fresh set of tools with shared state
        String cartId = parseCartId(input);
        List<String> cartItems = new ArrayList<>();  // Shared state

        return List.of(
            Tool.create("add_item", "Add item to cart " + cartId,
                Tool.InputSchema.of(Tool.Parameter.string("item", "Item name")),
                itemInput -> {
                    String item = parseItem(itemInput);
                    cartItems.add(item);  // Captured state
                    return Tool.Result.text("Added " + item + ". Total: " + cartItems.size());
                }
            ),
            Tool.create("view_cart", "View cart " + cartId + " contents", viewInput -> {
                return Tool.Result.text("Cart " + cartId + ": " + String.join(", ", cartItems));
            }),
            Tool.create("checkout", "Checkout cart " + cartId, checkoutInput -> {
                String total = calculateTotal(cartItems);
                cartItems.clear();
                return Tool.Result.text("Checked out " + cartId + " for " + total);
            })
        );
    }
);
```

##### Comparison with Other Approaches

Other agent frameworks address large tool collections with different approaches, each with trade-offs:

- **Anthropic’s Tool Search Tool**: Uses a `defer_loading: true` flag to prevent tools from being loaded upfront. Tools are discovered via a separate "Tool Search Tool" that searches tool metadata. This requires maintaining searchable tool descriptions and adds latency for each discovery step.
- **LangGraph Dynamic Tool Calling**: Uses vector stores and semantic search to select relevant tools based on the user’s query. This requires embedding infrastructure, vector database setup, and careful tuning of similarity thresholds.
- **Google ADK AgentTool**: Uses sub-agents that recursively delegate to other agents, each potentially having their own tool sets. Tool discovery is implicit through the agent hierarchy.
- **LangChain4j ToolProvider**: Provides a `ToolProvider` interface for dynamic tool selection, but it works *before* the LLM call by analyzing the incoming user message. For example, "if the message contains 'booking', include booking tools." This is pre-filtering based on message content, not progressive disclosure through tool invocation. LangChain4j’s documentation also suggests embedding-based classification, RAG over tool descriptions, or two-pass LLM selection—all requiring additional infrastructure or extra LLM calls.

UnfoldingTool takes a fundamentally different approach: **invoke to reveal**. Instead of searching through tool metadata, the LLM simply invokes a facade tool to unlock the tools it contains.

**Beyond Search: Dynamic Tool Configuration**

Crucially, UnfoldingTool goes far beyond what any search-based approach can offer. Search can only **find** pre-existing tools—it cannot create new ones or modify their behavior. With `UnfoldingTool.selectable()`, the selector function can:

- **Create entirely new tool instances** with different implementations based on runtime parameters
- **Capture configuration** (connection strings, credentials, endpoints) into the tool’s behavior
- **Share mutable state** between the tools created in a single invocation
- **Customize tool descriptions** to reflect the specific context of use

For example, when an LLM invokes a "database" UnfoldingTool with `{"connection": "prod-db.example.com"}`, the returned tools don’t just have different descriptions—they have **different behavior** that operates on that specific database. This is fundamentally impossible with search-based discovery, which can only return references to pre-defined tools.

This provides several advantages:

| Aspect | Other Approaches | UnfoldingTool |
| --- | --- | --- |
| **Infrastructure** | Requires vector stores, embeddings, search indices, or pre-filtering logic | No additional infrastructure required |
| **Selection Timing** | Before LLM call (pre-filtering based on message analysis) | After LLM decides to invoke a facade (LLM-driven discovery) |
| **Latency** | Search/embedding adds latency; two-pass selection doubles LLM calls | Instant unlock on invocation |
| **Scalability** | Search quality degrades with very large tool sets; requires careful tuning | Scales to any number of tools via nesting without degradation |
| **Determinism** | Search results can vary based on embedding similarity | Deterministic: invoking a facade always reveals the same tools |
| **Cost** | Embedding generation, vector search, or extra LLM calls incur compute costs | No additional compute beyond the tool call itself |
| **Dynamic Behavior** | Can only return references to pre-existing tools | Can create new tool instances with runtime-configured behavior |

The hierarchical nesting capability of UnfoldingTool means you can organize thousands of tools into a logical tree structure. The LLM navigates this tree by making simple invocations, with no search overhead at any level. For example, a top-level "admin\_operations" facade might reveal 5 category facades, each revealing 20 specific tools—giving access to 100 tools with at most 2 invocations.

|  | UnfoldingTool vs LlmReference  Both `UnfoldingTool` and `LlmReference` expose tools to the LLM, but they serve different purposes:  **Use UnfoldingTool when:**  - You have a single top-level capability that the LLM can invoke as one tool - The prompt contribution is short and can fit in the tool description - Example: A "database" tool that reveals query/insert/delete tools on invocation  **Use LlmReference when:**  - The prompt contribution is long or of general significance (appears in system prompt) - You have a bunch of related tools, not just one top-level tool - You need `notes()` for detailed usage instructions separate from the tool descriptions - The reference contributes context beyond just tool availability  **Implementing both:**  Classes like `Memory` and `ToolishRag` implement both `Tool` and `LlmReference`, giving maximum flexibility: |
| --- | --- |

Java

```java
// Use as LlmReference (adds to system prompt + tools)
ai.withReference(memory).respond(...);

// Use as Tool directly (just the tool)
ai.withTool(memory).respond(...);
```

Kotlin

```kotlin
// Use as LlmReference (adds to system prompt + tools)
ai.withReference(memory).respond(...)

// Use as Tool directly (just the tool)
ai.withTool(memory).respond(...)
```

When used as an `LlmReference`, the `tools()` method exposes the inner tools directly. When used as a `Tool`, the implementation wraps them in an `UnfoldingTool` facade.

#### 3.9.8. Process Introspection Tools

Embabel provides built-in `UnfoldingTool` implementations for introspecting the current agent process and its blackboard. These tools enable agentic workflows where the LLM can monitor its own progress, check resource usage, and access data from previous steps.

##### AgentProcessTools: Runtime Awareness

`AgentProcessTools` provides tools for the LLM to understand its current execution context. This is useful when you want an agent to be aware of its own operational status - for example, to check how much budget remains before undertaking an expensive operation, or to review what actions have been taken so far.

When to use `AgentProcessTools`:

- **Budget-aware agents**: Check remaining cost or token budget before expensive operations
- **Long-running workflows**: Monitor elapsed time and action history
- **Debugging and logging**: Understand what models and tools have been used
- **Self-reflection**: Agents that need to reason about their own behavior

**Sub-tools exposed:**

| Tool Name | Purpose |
| --- | --- |
| `process_status` | Current process ID, status, running time, and goal information |
| `process_budget` | Budget limits (cost, tokens, actions) and remaining capacity |
| `process_cost` | Total cost, LLM invocation count, and detailed token usage |
| `process_history` | List of actions taken so far with execution times |
| `process_tools_stats` | Tool usage statistics (call counts per tool) |
| `process_models` | LLM models that have been invoked |

```java
import com.embabel.agent.tools.process.AgentProcessTools;
import com.embabel.agent.api.tool.progressive.UnfoldingTool;

// Create the tool - typically added to an agentic tool
var processTools = new AgentProcessTools().create();

// Add to SimpleAgenticTool
var assistant = new SimpleAgenticTool("assistant", "...")
    .withTools(processTools);
```

|  | These tools require an active `AgentProcess` context. If called outside of an agent execution, they return an error message indicating no process is available. |
| --- | --- |

##### BlackboardTools: Accessing Workflow Data

`BlackboardTools` provides tools for the LLM to access objects in the current process’s blackboard. The blackboard is Embabel’s shared context mechanism - it holds artifacts from previous actions, tool outputs (when using `ArtifactSink`), and any other objects bound to the process.

When to use `BlackboardTools`:

- **Multi-step workflows**: Access results from earlier actions without re-execution
- **Tool output access**: When tools use `ArtifactSink` to publish structured data, BlackboardTools lets the LLM retrieve it
- **Context awareness**: Let the LLM explore what data is available in the current context
- **Debugging**: Inspect blackboard contents during development

**Sub-tools exposed:**

| Tool Name | Purpose |
| --- | --- |
| `blackboard_list` | List all objects in the blackboard with their types and indices |
| `blackboard_get` | Get an object by its binding name (e.g., "user", "searchResults") |
| `blackboard_last` | Get the most recent object of a given type (matches simple name or FQN) |
| `blackboard_describe` | Get a detailed description/formatting of an object by binding name |
| `blackboard_count` | Count the number of objects of a given type in the blackboard |

```java
import com.embabel.agent.tools.blackboard.BlackboardTools;
import com.embabel.agent.api.tool.progressive.UnfoldingTool;

// Create with default formatting
var blackboardTools = new BlackboardTools().create();

// Or with custom formatting for blackboard entries
var blackboardTools = new BlackboardTools().create(myCustomFormatter);

// Add to SimpleAgenticTool
var assistant = new SimpleAgenticTool("assistant", "...")
    .withTools(blackboardTools);
```

**Formatting blackboard entries:**

By default, `BlackboardTools` uses `DefaultBlackboardEntryFormatter` which:

- Uses `infoString()` for objects implementing `HasInfoString`
- Uses `content` property for objects implementing `HasContent`
- Falls back to `toString()` for other objects

You can provide a custom `BlackboardEntryFormatter` to control how objects are presented to the LLM.

**Type matching:**

The `blackboard_last` and `blackboard_count` tools match types by:

- **Simple name**: `"Person"` matches any class named `Person`
- **Fully qualified name**: `"com.example.Person"` matches that specific class

This flexibility lets the LLM query by whatever name is most convenient.

##### Combining Process Introspection Tools

For agents that need full situational awareness, combine both tools:

```java
SimpleAgenticTool awarenessAgent = new SimpleAgenticTool(
        "aware_assistant",
        "An assistant that can check its own status and access previous results")
    .withTools(
        new AgentProcessTools().create(),
        new BlackboardTools().create()
    );
```

#### 3.9.9. McpToolFactory: MCP Tool Integration

`McpToolFactory` is an interface that provides a convenient way to integrate [Model Context Protocol (MCP)](https://modelcontextprotocol.io/) tools into your application. It creates Embabel `Tool` instances from MCP servers, with support for filtering tools and wrapping them in `UnfoldingTool` facades.

`SpringAiMcpToolFactory` is the Spring AI-based implementation.

##### Creating McpToolFactory

`SpringAiMcpToolFactory` requires a list of `McpSyncClient` instances, which are typically provided by Spring’s MCP auto-configuration:

```java
import com.embabel.agent.tools.mcp.McpToolFactory;
import com.embabel.agent.spi.support.springai.SpringAiMcpToolFactory;
import io.modelcontextprotocol.client.McpSyncClient;

@Configuration
public class ToolConfiguration {

    @Bean
    public McpToolFactory mcpToolFactory(List<McpSyncClient> clients) {
        return new SpringAiMcpToolFactory(clients);
    }
}
```

|  | MCP clients are configured in `application.yml`. See [MCP Integration](https://docs.embabel.com/embabel-agent/guide/0.3.4-SNAPSHOT/#reference.integrations__mcp) for configuration details. |
| --- | --- |

##### Getting Individual MCP Tools

Use `toolByName` to retrieve a single MCP tool by its exact name:

```java
// Returns null if not found
Tool braveSearch = mcpToolFactory.toolByName("brave_web_search");
if (braveSearch != null) {
    ai.withTool(braveSearch).generateText("Search for recent news about AI");
}

// Throws IllegalArgumentException if not found (with helpful error message)
Tool requiredTool = mcpToolFactory.requiredToolByName("brave_web_search");
```

##### Creating UnfoldingToolFacades from MCP

`McpToolFactory` can wrap groups of MCP tools in an `UnfoldingTool` facade for progressive disclosure. This is useful when you have many MCP tools but want to present them as logical categories.

**By Exact Tool Names:**

```java
// Create a UnfoldingTool with specific tool names
var wikipediaTool = mcpToolFactory.unfoldingByName(
    "wikipedia",
    "Search and find content from Wikipedia",
    Set.of("search_wikipedia", "get_article", "get_related_topics", "get_summary")
);
```

**By Regex Patterns:**

```java
import java.util.regex.Pattern;

// Match tools by regex patterns
var dbTool = mcpToolFactory.unfoldingMatching(
    "database_operations",
    "Database operations. Invoke to access database tools.",
    List.of(Pattern.compile("^db_.*"), Pattern.compile(".*query.*"))
);
```

**With Custom Filter:**

```java
// Custom filter predicate
var webTool = mcpToolFactory.unfolding(
    "web_operations",
    "Web operations. Invoke to access web tools.",
    callback -> callback.getToolDefinition().name().startsWith("web_")
);
```

##### Controlling Facade Removal

By default, `UnfoldingTool` facades created by `McpToolFactory` are removed after invocation (replaced by inner tools). Use `removeOnInvoke = false` to keep the facade:

```java
// Keep facade even after invocation
var persistentTool = mcpToolFactory.unfoldingByName(
    "wikipedia",
    "Search Wikipedia",
    Set.of("search_wikipedia", "get_article"),
    false  // removeOnInvoke = false
);
```

##### Real-World Example: Chatbot with MCP Tools

Here’s a real-world example from a production chatbot that uses `McpToolFactory` to integrate MCP tools with graceful degradation:

```java
@Configuration
public class ChatConfiguration {

    @Bean
    public McpToolFactory mcpToolFactory(List<McpSyncClient> clients) {
        return new SpringAiMcpToolFactory(clients);
    }

    @Bean
    public CommonTools commonTools(McpToolFactory mcpToolFactory) {
        var deferMessage = "Use this tool only after trying local sources";
        var tools = new LinkedList<>();

        // Single MCP tool - gracefully handle missing tools
        var braveSearch = mcpToolFactory.toolByName("brave_web_search");
        if (braveSearch != null) {
            tools.add(braveSearch.withNote(deferMessage));
        }

        // UnfoldingTool grouping related Wikipedia MCP tools
        var wikipediaTool = mcpToolFactory.unfoldingByName(
            "wikipedia",
            "Search and find content from Wikipedia: " + deferMessage,
            Set.of("search_wikipedia", "get_article", "get_related_topics", "get_summary")
        );
        if (!wikipediaTool.getInnerTools().isEmpty()) {
            tools.add(wikipediaTool);
        }

        return new CommonTools(tools);
    }
}
```

This pattern:

- **Gracefully degrades** when MCP tools aren’t available (e.g., in test environments)
- **Groups related tools** behind a descriptive facade using `UnfoldingTool`
- **Adds usage hints** with `withNote()` to guide the LLM on when to use external tools
- **Checks for empty results** before adding tools to avoid empty facades

##### McpToolFactory Method Summary

| Method | Description |
| --- | --- |
| `toolByName(String)` | Get a single MCP tool by exact name. Returns `null` if not found. |
| `requiredToolByName(String)` | Get a single MCP tool by exact name. Throws `IllegalArgumentException` if not found, with a helpful error message listing available tools. |
| `unfoldingByName(name, description, toolNames, removeOnInvoke?)` | Create an `UnfoldingTool` containing tools with exact matching names. |
| `unfoldingMatching(name, description, patterns, removeOnInvoke?)` | Create an `UnfoldingTool` containing tools matching any of the regex patterns. |
| `unfolding(name, description, filter, removeOnInvoke?)` | Create an `UnfoldingTool` with a custom filter predicate. |

### 3.10. Structured Prompt Elements

Embabel provides a number of ways to structure and manage prompt content.

**Prompt contributors** are a fundamental way to structure and inject content into LLM prompts. You don’t need to use them—you can simply build your prompts as strings—but they can be useful to achieve consistency and reuse across multiple actions or even across multiple agents using the same domain objects.

Prompt contributors implement the `PromptContributor` interface and provide text that gets included in the final prompt sent to the LLM. By default the text will be included in the system prompt message.

#### 3.10.1. The PromptContributor Interface and LlmReference Subinterface

All prompt contributors implement the `PromptContributor` interface with a `contribution()` method that returns a string to be included in the prompt.

Add `PromptContributor` instances to a `PromptRunner` using the `withPromptContributor()` method.

A subinterface of `PromptContributor` is `LlmReference`.

An `LlmReference` is a prompt contributor that can also provide tools via annotated `@Tool` methods. So that tool naming can be disambiguated, an `LlmReference` must also include name and description metadata.

Add `LlmReference` instances to a `PromptRunner` using the `withReference()` method.

Use `LlmReference` if:

- You want to provide both prompt content and tools from the same object
- You want to provide specific instructions on how to use these tools, beyond the individual tool descriptions
- Your data may be best exposed as either prompt content or tools, depending on the context. For example, if you have a list of 10 items you might just put in the prompt and say "Here are all the items: …". If you have a list of 10,000 objects, you would include advice to use the tools to query them.

|  | An `LlmReference` is somewhat similar to a Claude Skill. |
| --- | --- |

`LlmReference` instances can be created programmatically or defined in YML using `LlmReferenceProvider` implementations. For example, you could define a `references.yml` file in this format:

```yaml
- fqn: com.embabel.coding.tools.git.GitHubRepository
  url: https://github.com/embabel/embabel-agent-examples.git
  description: Embabel examples Repository

- fqn: com.embabel.coding.tools.git.GitHubRepository
  url: https://github.com/embabel/java-agent-template.git
  description: >
    Java agent template Repository: Simplest Java example with Maven
    Can be used as a GitHub template

- fqn: com.embabel.coding.tools.git.GitHubRepository
  url: https://github.com/embabel/embabel-agent.git
  description: >
    Embabel agent framework implementation repo: Look to check code under embabel-agent-api

- fqn: com.embabel.coding.tools.api.ApiReferenceProvider
  name: embabel-agent
  description: Embabel Agent API
  acceptedPackages:
    - com.embabel.agent
    - com.embabel.common
```

The `fqn` property specifies the fully qualified class name of the `LlmReferenceProvider` implementation. This enables you to define your own `LlmReferenceProvider` implementations. Out of the box, Embabel provides:

- `com.embabel.agent.api.reference.LiteralText`: Text in `notes`
- `com.embabel.agent.api.reference.SpringResource`: Contents of the given Spring resource path
- `com.embabel.agent.api.reference.WebPage`: Content of the given web page, if it can be fetched
- `com.embabel.coding.tools.git.GitHubRepository`: GitHub repositories (`embabel-agent-code` module)
- `com.embabel.coding.tools.api.ApiReferenceProvider`: API from classpath (`embabel-agent-code` module)

You can parse your YML files into `List<LlmReference>` using the `LlmReferenceProviders.fromYml` method.

The `resource` argument is a Spring resource specification.

Thus `LlmReferenceProviders.fromYml("references.yml")` will load `references.yml` under `src/main/resources/`

#### 3.10.2. Built-in Convenience Classes

Embabel provides several convenience classes that implement `PromptContributor` for common use cases. These are optional utilities - you can always implement the interface directly for custom needs.

##### Persona

The `Persona` class provides a structured way to define an AI agent’s personality and behavior:

```java
var persona = Persona.create(
    "Alex the Analyst",
    "A detail-oriented data analyst with expertise in financial markets",
    "Professional yet approachable, uses clear explanations",
    "Help users understand complex financial data through clear analysis"
);
```

This generates a prompt contribution like:

```
You are Alex the Analyst.
Your persona: A detail-oriented data analyst with expertise in financial markets.
Your objective is Help users understand complex financial data through clear analysis.
Your voice: Professional yet approachable, uses clear explanations.
```

##### RoleGoalBackstory

The `RoleGoalBackstory` class follows the Crew AI pattern and is included for users migrating from that framework:

```java
var agent = RoleGoalBackstory.withRole("Senior Software Engineer")
    .andGoal("Write clean, maintainable code")
    .andBackstory("10+ years experience in enterprise software development");
```

This generates:

```
Role: Senior Software Engineer
Goal: Write clean, maintainable code
Backstory: 10+ years experience in enterprise software development
```

#### 3.10.3. Custom PromptContributor Implementations

You can create custom prompt contributors by implementing the interface directly. This gives you complete control over the prompt content:

```java
public class CustomSystemPrompt implements PromptContributor {
    private final String systemName;

    public CustomSystemPrompt(String systemName) {
        this.systemName = systemName;
    }

    @Override
    public String contribution() {
        return "System: " + systemName + " - Current time: " + LocalDateTime.now();
    }
}

public class ConditionalPrompt implements PromptContributor {
    private final Supplier<Boolean> condition;
    private final String trueContent;
    private final String falseContent;

    public ConditionalPrompt(Supplier<Boolean> condition, String trueContent, String falseContent) {
        this.condition = condition;
        this.trueContent = trueContent;
        this.falseContent = falseContent;
    }

    @Override
    public String contribution() {
        return condition.get() ? trueContent : falseContent;
    }
}
```

#### 3.10.4. Examples from embabel-agent-examples

The [embabel-agent-examples](https://github.com/embabel/embabel-agent-examples) repository demonstrates various agent development patterns and Spring Boot integration approaches for building AI agents with Embabel.

#### 3.10.5. Best Practices

- Keep prompt contributors focused and single-purpose
- Use the convenience classes (`Persona`, `RoleGoalBackstory`) when they fit your needs
- Implement custom `PromptContributor` classes for domain-specific requirements
- Consider using dynamic contributors for context-dependent content
- Test your prompt contributions to ensure they produce the desired LLM behavior

### 3.11. Templates

Embabel supports Jinja templates for generating prompts. You do this via the `PromptRunner.rendering(String)` method.

This method takes a Spring resource path to a Jinja template. The default location is under `classpath:/prompts/` and the `.jinja` extension is added automatically.

You can also specify a full resource path with [Spring resource conventions](https://docs.spring.io/spring-framework/reference/core/resources.html).

Once you have specified the template, you can create objects using a model map.

An example:

```java
DistinctFactualAssertions distinctFactualAssertions = context.ai()
    .withLlm(properties.deduplicationLlm())
    // Jinjava template from classpath at prompts/factchecker/consolidate_assertions.jinja
    .rendering("factchecker/consolidate_assertions")
    .createObject(
            DistinctFactualAssertions.class,
            Map.of(
                    "assertions", allAssertions,
                    "reasoningWordCount", properties.reasoningWordCount()
            )
    );
```

|  | Don’t rush to externalize prompts. In modern languages with multi-line strings, it’s often easier to keep prompts in the codebase. Externalizing them can sacrifice type safety and lead to complexity and maintenance challenges. |
| --- | --- |

### 3.12. RAG (Retrieval-Augmented Generation)

Retrieval-Augmented Generation (RAG) is a technique that enhances LLM responses by retrieving relevant information from a knowledge base before generating answers. This grounds LLM outputs in specific, verifiable sources rather than relying solely on training data.

For more background on RAG concepts, see:

Embabel Agent provides RAG support through the `LlmReference` interface, which allows you to attach references (including RAG stores) to LLM calls. The key classes are `ToolishRag` for exposing search operations as LLM tools, and `SearchOperations` for the underlying search functionality.

#### 3.12.1. Agentic RAG Architecture

Unlike traditional RAG implementations that perform a single retrieval step, Embabel Agent’s RAG is **entirely agentic and tool-based**. The LLM has full control over the retrieval process:

- **Autonomous Search**: The LLM decides when to search, what queries to use, and how many results to retrieve
- **Iterative Refinement**: The LLM can perform multiple searches with different queries until it finds relevant information
- **Cross-Reference Discovery**: The LLM can follow references, expand chunks to see surrounding context, and zoom out to parent sections
- **HyDE Support**: The LLM can generate hypothetical documents (HyDE queries) to improve semantic search results

This agentic approach produces better results than single-shot RAG because the LLM can:

1. Start with a broad search and narrow down
2. Try different phrasings if initial queries return poor results
3. Expand promising results to get more context
4. Combine information from multiple chunks

#### 3.12.2. Facade Pattern for Safe Tool Exposure

Embabel Agent uses a facade pattern to expose RAG capabilities safely and consistently across different store implementations. The `ToolishRag` class acts as a facade that:

1. **Inspects Store Capabilities**: Examines which `SearchOperations` subinterfaces the store implements
2. **Exposes Appropriate Tools**: Only creates tool wrappers for supported operations
3. **Provides Consistent Interface**: All tools use the same parameter patterns regardless of underlying store

```java
@Override
public List<Tool> tools() {
    List<Object> toolObjects = new ArrayList<>();
    if (searchOperations instanceof VectorSearch) {
        toolObjects.add(new VectorSearchTools((VectorSearch) searchOperations));
    }
    if (searchOperations instanceof TextSearch) {
        toolObjects.add(new TextSearchTools((TextSearch) searchOperations));
    }
    if (searchOperations instanceof ResultExpander) {
        toolObjects.add(new ResultExpanderTools((ResultExpander) searchOperations));
    }
    if (searchOperations instanceof RegexSearchOperations) {
        toolObjects.add(new RegexSearchTools((RegexSearchOperations) searchOperations));
    }
    return toolObjects.stream()
            .flatMap(obj -> Tool.fromInstance(obj).stream())
            .toList();
}
```

This means:

- A Lucene store exposes vector search, text search, regex search, AND result expansion tools
- A Spring AI VectorStore adapter exposes only vector search tools
- A basic text-only store exposes only text search tools
- A directory-based text search exposes text search and regex search

The LLM sees only the tools that actually work with the configured store, preventing runtime errors from unsupported operations.

#### 3.12.3. Getting Started

To use RAG in your Embabel Agent application, add the `rag-core` module and a store implementation to your `pom.xml`:

```xml
<dependency>
    <groupId>com.embabel.agent</groupId>
    <artifactId>embabel-agent-rag-lucene</artifactId>
    <version>${embabel-agent.version}</version>
</dependency>

<dependency>
    <groupId>com.embabel.agent</groupId>
    <artifactId>embabel-agent-rag-tika</artifactId>
    <version>${embabel-agent.version}</version>
</dependency>
```

The `embabel-agent-rag-lucene` module provides Lucene-based vector and text search. The `embabel-agent-rag-tika` module provides Apache Tika integration for parsing various document formats.

#### 3.12.4. Our Model

Embabel Agent uses a hierarchical content model that goes beyond traditional flat chunk storage:

```
Datum (sealed interface)
│   Core: id, uri, metadata, labels()
│
├── ContentElement ─────────────────────────────────────┐
│       Structural content (not embedded)               │
│   ┌───────────────────────────────────────────────┐   │
│   │ ContentRoot / NavigableDocument               │   │
│   │     Documents with URI and title              │   │
│   └───────────────────────────────────────────────┘   │
│   ┌───────────────────────────────────────────────┐   │
│   │ ContainerSection / LeafSection                │   │
│   │     Hierarchical document sections            │   │
│   └───────────────────────────────────────────────┘   │
│                                                       │
└── Retrievable ────────────────────────────────────────┤
        Embeddable/searchable content                   │
    ┌───────────────────────────────────────────────┐   │
    │ Chunk                                         │   │
    │     text, parentId, embedding                 │   │
    │     Primary unit for vector search            │   │
    └───────────────────────────────────────────────┘   │
    ┌───────────────────────────────────────────────┐   │
    │ NamedEntity                                   │   │
    │     Domain entity contract (Person, Product)  │   │
    │     name, description + domain properties     │   │
    │                                               │   │
    │   └── NamedEntityData                         │   │
    │         Storage format with properties map    │   │
    │         Hydration via toTypedInstance()       │   │
    └───────────────────────────────────────────────┘   │
                                                        │
────────────────────────────────────────────────────────┘
```

**Key Design Points:**

- `Datum` is the root sealed interface for all data objects
- `ContentElement` branch contains structural content (documents, sections) that is NOT embedded
- `Retrievable` branch contains searchable content with embeddings (chunks, entities)
- `NamedEntity` is the domain contract for typed entities
- `NamedEntityData` is the storage format with generic `properties` map and hydration support

##### Content Elements

The `ContentElement` interface is the supertype for all content in the RAG system. Key subtypes include:

- **`ContentRoot`** / **`NavigableDocument`**: The root of a document hierarchy, with a required URI and title
- **`Section`**: A hierarchical division of content with a title
- **`ContainerSection`**: A section containing other sections
- **`LeafSection`**: A section containing actual text content
- **`Chunk`**: Traditional RAG text chunks, created by splitting `LeafSection` content

##### Chunks

`Chunk` is the primary unit for vector search. Each chunk:

- Contains a `text` field with the content
- Has a `parentId` linking to its source section
- Includes `metadata` with information about its origin (root document, container section, leaf section)
- Can compute its `pathFromRoot` through the document hierarchy

This hierarchical model enables advanced RAG capabilities like "zoom out" to parent sections or expansion to adjacent chunks.

#### 3.12.5. SearchOperations

`SearchOperations` is the tag interface for search functionality. Concrete implementations implement one or more subinterfaces based on their capabilities. This design allows stores to implement only what’s natural and efficient for them—a vector database need not pretend to support full-text search, and a text search engine need not fake vector similarity.

##### VectorSearch

Classic semantic vector search:

```java
public interface VectorSearch extends SearchOperations {
    <T extends Retrievable> List<SimilarityResult<T>> vectorSearch(
        TextSimilaritySearchRequest request,
        Class<T> clazz
    );
}
```

##### TextSearch

Full-text search using Lucene query syntax:

```java
public interface TextSearch extends SearchOperations {
    <T extends Retrievable> List<SimilarityResult<T>> textSearch(
        TextSimilaritySearchRequest request,
        Class<T> clazz
    );
}
```

Supported query syntax includes:

- `+term` - term must appear
- `-term` - term must not appear
- `"phrase"` - exact phrase match
- `term*` - prefix wildcard
- `term~` - fuzzy match

##### ResultExpander

Expand search results to surrounding context:

```java
public interface ResultExpander extends SearchOperations {
    List<ContentElement> expandResult(
        String id,
        Method method,
        int elementsToAdd
    );
}
```

Expansion methods:

- `SEQUENCE` - expand to previous and next chunks
- `ZOOM_OUT` - expand to enclosing section

##### RegexSearchOperations

Pattern-based search across content:

```java
public interface RegexSearchOperations extends SearchOperations {
    <T extends Retrievable> List<SimilarityResult<T>> regexSearch(
        Pattern regex,
        int topK,
        Class<T> clazz
    );
}
```

Useful for finding specific patterns like error codes, identifiers, or structured content that doesn’t match well with semantic or keyword search.

##### CoreSearchOperations

A convenience interface combining the most common search capabilities:

```java
public interface CoreSearchOperations extends VectorSearch, TextSearch { }
```

Stores that support both vector and text search can implement this single interface for convenience.

#### 3.12.6. ToolishRag

`ToolishRag` is an `LlmReference` that exposes `SearchOperations` as LLM tools. This gives the LLM fine-grained control over RAG searches.

##### Configuration

Create a `ToolishRag` by wrapping your `SearchOperations`:

```java
public ChatActions(SearchOperations searchOperations) {
    this.toolishRag = new ToolishRag(
            "sources",
            "Sources for answering user questions",
            searchOperations
    );
}
```

##### Using with LLM Calls

Attach `ToolishRag` to an LLM call using `.withReference()`:

```java
@Action(canRerun = true, trigger = UserMessage.class)
void respond(Conversation conversation, ActionContext context) {
    var assistantMessage = context.ai()
            .withLlm(properties.chatLlm())
            .withReference(toolishRag)
            .rendering("ragbot")
            .respondWithSystemPrompt(conversation, Map.of(
                    "properties", properties
            ));
    context.sendMessage(conversation.addMessage(assistantMessage));
}
```

Based on the capabilities of the underlying `SearchOperations`, `ToolishRag` exposes:

- **VectorSearchTools**: `vectorSearch(query, topK, threshold)` - semantic similarity search
- **TextSearchTools**: `textSearch(query, topK, threshold)` - BM25 full-text search with Lucene syntax
- **RegexSearchTools**: `regexSearch(regex, topK)` - pattern-based search using regular expressions
- **ResultExpanderTools**: `broadenChunk(chunkId, chunksToAdd)` - expand to adjacent chunks, `zoomOut(id)` - expand to parent section

The LLM autonomously decides when to use these tools based on user queries.

##### ToolishRag lifecycle

It is safe to create a `ToolishRag` instance and reuse across many LLM calls. However, instances are not expensive to create, so you can create a new instance per LLM call. You might choose to do this if you provide a `ResultListener` that will collect queries and results for logging or analysis: for example, to track which queries were most useful for answering user questions and the complexity in terms of number of searches performed. This can be useful for implementing a learning feedback loop, for example to discern which queries performed badly, indicating that content such as documentation needs to be enhanced.

##### Result Filtering

In multi-tenant applications or scenarios where searches should be scoped to specific data subsets, `ToolishRag` supports **result filtering**. Filters are applied transparently to all searches—the LLM does not see or control them, ensuring security and data isolation.

Embabel Agent provides two types of filters:

- **Metadata Filters**: Filter on the `metadata` map of `Datum` objects (chunks, sections, etc.)
- **Property Filters**: Filter on object properties of typed entities (e.g., fields of `NamedEntityData` or custom entity classes)

Both use the same `PropertyFilter` type but are applied at different levels.

###### Motivation

Consider a document management system where:

- Each document belongs to an owner (user or organization)
- Some documents are shared reference data accessible to all users
- The LLM should only search documents the current user is authorized to access

Without filtering, you would need separate RAG stores per user or risk data leakage. With filtering, a single `ToolishRag` instance can be scoped per-request to the current user’s data.

###### Filter API

Embabel Agent provides two filter interfaces for RAG searches:

- **`PropertyFilter`**: Filters on map-based properties (metadata, entity properties)
- **`EntityFilter`**: Extends `PropertyFilter` to add entity-specific filtering, particularly label-based filtering

###### PropertyFilter

The `PropertyFilter` sealed class hierarchy provides type-safe filter expressions for map-based properties:

| Filter Type | Description | Example |
| --- | --- | --- |
| `Eq` | Equals | `PropertyFilter.eq("owner", "alice")` |
| `Ne` | Not equals | `PropertyFilter.ne("status", "deleted")` |
| `Gt`, `Gte` | Greater than (or equal) | `PropertyFilter.gte("score", 0.8)` |
| `Lt`, `Lte` | Less than (or equal) | `PropertyFilter.lt("priority", 5)` |
| `In` | Value in list | `PropertyFilter.in("category", "tech", "science")` |
| `Nin` | Value not in list | `PropertyFilter.nin("status", "deleted", "archived")` |
| `Contains` | String contains substring | `PropertyFilter.contains("tags", "important")` |
| `And` | Logical AND | `PropertyFilter.and(filter1, filter2)` |
| `Or` | Logical OR | `PropertyFilter.or(filter1, filter2)` |
| `Not` | Logical NOT | `PropertyFilter.not(filter)` |

###### EntityFilter

`EntityFilter` extends `PropertyFilter` to add entity-specific filtering. Currently, it adds label-based filtering via `HasAnyLabel`:

| Filter Type | Description | Example |
| --- | --- | --- |
| `HasAnyLabel` | Matches entities with any of the specified labels | `EntityFilter.hasAnyLabel("Person", "Organization")` |

`HasAnyLabel` is particularly useful for:

- **Type-safe entity searches**: Filter results to only include specific entity types
- **Multi-type queries**: Search across multiple entity types in one query

```java
import com.embabel.agent.rag.filter.EntityFilter;
import com.embabel.agent.rag.filter.PropertyFilter;

// Filter by single label
EntityFilter personFilter = EntityFilter.hasAnyLabel("Person");

// Filter by multiple labels (OR semantics - entity must have ANY of these labels)
EntityFilter entityFilter = EntityFilter.hasAnyLabel("Person", "Organization");

// Combine HasAnyLabel with property filters using fluent API
PropertyFilter simpleCombo = EntityFilter.hasAnyLabel("Person")
    .and(PropertyFilter.eq("status", "active"));

// Multiple conditions
PropertyFilter complexFilter = EntityFilter.hasAnyLabel("Person")
    .and(PropertyFilter.eq("status", "active"))
    .and(PropertyFilter.gte("score", 0.8));

// OR combinations
PropertyFilter orFilter = EntityFilter.hasAnyLabel("Person")
    .or(PropertyFilter.eq("fallback", true));

// With negation
PropertyFilter notDeleted = EntityFilter.hasAnyLabel("Person")
    .and(PropertyFilter.not(PropertyFilter.eq("status", "deleted")));

// Complex grouping
PropertyFilter accessFilter = PropertyFilter.or(
    PropertyFilter.and(
        EntityFilter.hasAnyLabel("Person", "Employee"),
        PropertyFilter.eq("active", true)
    ),
    PropertyFilter.eq("role", "admin")
);
```

Since `EntityFilter` extends `PropertyFilter`, all filter types share the same `and`, `or`, `not` operators and can be freely combined.

|  | `EntityFilter.HasAnyLabel` is typically handled via in-memory filtering as most vector stores don’t have native label support. When using Neo4j backends, labels can be translated to native Cypher label predicates for optimal performance. |
| --- | --- |

|  | **Limitation: Nested Properties Not Supported**  Filters currently operate on top-level properties only. Nested property paths like `"address.city"` or `"metadata.source"` are **not** supported. The filter key must match a direct key in the metadata map or a top-level property on the entity object.  For example:  - `PropertyFilter.eq("owner", "alice")` - **Supported**: filters on top-level `owner` property - `PropertyFilter.eq("address.city", "London")` - **Not supported**: nested path will not match |
| --- | --- |

###### Kotlin Operator Syntax

Kotlin users can use operator and infix functions for a more natural DSL syntax:

```java
import com.embabel.agent.rag.filter.PropertyFilter;

// Simple filter with not operator
PropertyFilter notDeleted = PropertyFilter.not(PropertyFilter.eq("status", "deleted"));

// Combine with 'and' and 'or'
PropertyFilter userAccess = PropertyFilter.and(
    PropertyFilter.eq("owner", userId),
    PropertyFilter.gte("confidenceScore", 0.7)
);

// Complex expressions with grouping
PropertyFilter accessFilter = PropertyFilter.or(
    PropertyFilter.and(
        PropertyFilter.eq("owner", userId),
        PropertyFilter.ne("status", "deleted")
    ),
    PropertyFilter.eq("role", "admin")
);
```

`ToolishRag` accepts two separate filter parameters:

- **`metadataFilter`**: A `PropertyFilter` that filters on the `metadata` map of `Datum` objects. Metadata is typically ingestion-time information like source URI, ingestion date, owner ID, etc.
- **`entityFilter`**: An `EntityFilter` that filters on entity properties and labels. For `NamedEntityData`, this filters on the `properties` map and `labels()`. For typed entities, reflection is used to access top-level fields.

```java
// Filter on metadata (e.g., which user owns the document)
PropertyFilter metadataFilter = PropertyFilter.eq("ownerId", currentUserId);

// Filter on entity labels and properties
EntityFilter entityFilter = EntityFilter.hasAnyLabel("Person");

// Apply both filters
ToolishRag scopedRag = toolishRag
    .withMetadataFilter(metadataFilter)
    .withEntityFilter(entityFilter);
```

In most cases, you’ll use metadata filters for access control and entity filters for type-based and business logic filtering.

##### Neo4j Cypher Filtering

When using Neo4j via the Drivine module, metadata filters are automatically converted to Cypher WHERE clauses using `CypherFilterConverter`:

```java
// The filter is converted to Cypher WHERE clause automatically
PropertyFilter filter = PropertyFilter.and(
    PropertyFilter.eq("owner", "alice"),
    PropertyFilter.gte("confidenceScore", 0.7)
);

// In DrivineNamedEntityDataRepository:
List<SimilarityResult<T>> results = repository.vectorSearch(request, filter);
// Generates: WHERE (e.owner = $_filter_0) AND (e.confidenceScore >= $_filter_1) AND ...
```

The converter produces parameterized queries for safety and handles all filter types including nested logical expressions.

For both `DrivineStore` (chunks) and `DrivineNamedEntityDataRepository` (named entities), **both** metadata and property filters are translated to native Cypher WHERE clauses. This is because Neo4j stores all data as node properties - metadata is simply the set of properties that aren’t core fields like `id`, `text`, `parentId`, etc. This provides optimal performance by filtering at the database level rather than in-memory.

###### Basic Usage

Apply a metadata filter to scope all searches to a specific owner:

```java
// Create a filter for the current user
PropertyFilter ownerFilter = PropertyFilter.eq("ownerId", currentUserId);

// Apply to ToolishRag - all searches will be filtered
ToolishRag scopedRag = toolishRag.withMetadataFilter(ownerFilter);

// Use in LLM call - LLM cannot see or bypass the filter
context.ai()
    .withReference(scopedRag)
    .respondWithSystemPrompt(conversation, Map.of());
```

###### Complex Filters

Combine filters for more sophisticated access control:

```java
// User can access their own documents OR documents in their departments
PropertyFilter accessFilter = PropertyFilter.or(
    PropertyFilter.eq("ownerId", currentUserId),
    PropertyFilter.in("departmentId", userDepartmentIds)
);

ToolishRag scopedRag = toolishRag.withMetadataFilter(accessFilter);

// Organization-scoped with status restriction
PropertyFilter orgFilter = PropertyFilter.and(
    PropertyFilter.eq("orgId", currentOrgId),
    PropertyFilter.ne("status", "deleted"),
    PropertyFilter.gte("confidenceScore", 0.7)
);

ToolishRag scopedRag2 = toolishRag.withMetadataFilter(orgFilter);
```

###### Per-Request Scoping Pattern

A common pattern is to create a scoped `ToolishRag` per request in a web application:

```java
@Action(trigger = UserMessage.class)
void respond(Conversation conversation, ActionContext context) {
    // Get current user from security context
    String userId = SecurityContextHolder.getContext()
        .getAuthentication().getName();

    // Create user-scoped RAG for this request
    ToolishRag userScopedRag = toolishRag.withMetadataFilter(
        PropertyFilter.eq("ownerId", userId)
    );

    context.ai()
        .withReference(userScopedRag)
        .rendering("assistant")
        .respondWithSystemPrompt(conversation, Map.of());
}
```

###### Backend Implementation

Filters are applied at different levels depending on the backend:

- **Spring AI VectorStore**: Metadata filters are translated to `Filter.Expression` for native filtering; entity filters (including `HasAnyLabel`) are applied in-memory
- **Neo4j (Drivine)**: Both metadata and entity filters (including `HasAnyLabel`) are translated to native Cypher WHERE clauses and label predicates (optimal performance)
- **Lucene**: Both filter types are applied as post-filters with inflated `topK` to compensate for filtered-out results
- **Custom stores**: Can implement `FilteringVectorSearch` / `FilteringTextSearch` for native translation, or fall back to in-memory filtering

The `InMemoryPropertyFilter` utility class provides fallback filtering for any store implementation:

```java
// In your SearchOperations implementation
List<SimilarityResult<T>> results = performSearch(request);
return InMemoryPropertyFilter.filterResults(results, metadataFilter, entityFilter);
```

For `EntityFilter.HasAnyLabel`, the in-memory filter checks if the entity has any of the specified labels via `NamedEntityData.labels()`.

This ensures filtering works across all backends, with native optimization for metadata filters where available.

#### 3.12.7. Ingestion

##### Document Parsing with Tika

Embabel Agent uses Apache Tika for document parsing. `TikaHierarchicalContentReader` reads various formats (Markdown, HTML, PDF, Word, etc.) and extracts a hierarchical structure:

```java
@ShellMethod("Ingest URL or file path")
String ingest(@ShellOption(defaultValue = "./data/document.md") String location) {
    var uri = location.startsWith("http://") || location.startsWith("https://")
            ? location
            : Path.of(location).toAbsolutePath().toUri().toString();
    var ingested = NeverRefreshExistingDocumentContentPolicy.INSTANCE
            .ingestUriIfNeeded(
                    luceneSearchOperations,
                    new TikaHierarchicalContentReader(),
                    uri
            );
    return ingested != null ?
            "Ingested document with ID: " + ingested :
            "Document already exists, no ingestion performed.";
}
```

##### Chunking Configuration

Content is split into chunks with configurable parameters:

```yaml
ragbot:
  chunker-config:
    max-chunk-size: 800
    overlap-size: 100
```

Configuration options:

- `maxChunkSize` - Maximum characters per chunk (default: 1500)
- `overlapSize` - Character overlap between consecutive chunks (default: 200)
- `includeSectionTitleInChunk` - Include section title in chunk text (default: true)

##### Chunk Transformation

When chunks are created from documents, they often lack the context needed for effective retrieval. A chunk containing "This approach improves performance by 40%" is not useful unless the reader knows what "this approach" refers to. The `ChunkTransformer` interface allows you to enrich chunks with additional context before they are indexed.

###### The urtext Field

Every `Chunk` has two text fields:

- `text` - The indexed content, which may be transformed with additional context
- `urtext` - The original, unmodified chunk text

The `urtext` field preserves the original content for accurate citations. When displaying search results to users, use `urtext` to show exactly what appeared in the source document, while using the enriched `text` for vector embeddings and search.

###### AddTitlesChunkTransformer

```java
@Bean
ChunkTransformer chunkTransformer() {
    return AddTitlesChunkTransformer.INSTANCE;
}
```

This transforms a chunk like:

```java
This approach improves performance by 40% compared to the baseline.
```

Into:

```java
# Title: Performance Optimization Guide
# URI: https://docs.example.com/performance
# Section: Caching Strategies

This approach improves performance by 40% compared to the baseline.
```

Now the chunk carries its context, improving both retrieval accuracy and LLM understanding.

###### Custom Transformers

You can create custom transformers by implementing `ChunkTransformer` or extending `AbstractChunkTransformer`:

```java
public class MetadataEnrichingTransformer extends AbstractChunkTransformer {

    @Override
    public Map<String, Object> additionalMetadata(
            Chunk chunk,
            ChunkTransformationContext context) {
        return Map.of(
            "documentType", context.getDocument().getMetadata().get("type"),
            "lastModified", Instant.now().toString()
        );
    }

    @Override
    public String newText(Chunk chunk, ChunkTransformationContext context) {
        // Optionally modify the text
        return chunk.getText();
    }
}
```

The `ChunkTransformationContext` provides access to:

- `section` - The `Section` containing this chunk
- `document` - The `ContentRoot` (may be null for orphan sections)

###### Chaining Transformers

Use `ChainedChunkTransformer` to apply multiple transformations in sequence:

```java
@Bean
ChunkTransformer chunkTransformer() {
    return new ChainedChunkTransformer(List.of(
        AddTitlesChunkTransformer.INSTANCE,
        new MetadataEnrichingTransformer(),
        new CustomCleanupTransformer()
    ));
}
```

Transformers are applied in order, with each receiving the output of the previous transformer.

###### Configuring the Store

Pass your `ChunkTransformer` to the store implementation:

```java
@Bean
DrivineStore drivineStore(
        PersistenceManager persistenceManager,
        EmbeddingService embeddingService,
        ChunkTransformer chunkTransformer,  (1)
        MyProperties properties) {
    return new DrivineStore(
        persistenceManager,
        properties.neoRag(),
        properties.chunkerConfig(),
        chunkTransformer,  (2)
        embeddingService,
        platformTransactionManager,
        new DrivineCypherSearch(persistenceManager)
    );
}
```

| **1** | Inject the `ChunkTransformer` bean |
| --- | --- |
| **2** | Pass it to the store constructor |

|  | For most use cases, `AddTitlesChunkTransformer` is all you need. It adds essential context that significantly improves retrieval quality without adding complexity. |
| --- | --- |

##### Using Docling for Markdown Conversion

While we believe that you should write your Gen AI **applications** in Java or Kotlin, ingestion is more in the realm of data science, and Python is indisputably strong in this area.

For complex documents like PDFs, consider using [Docling](https://github.com/DS4SD/docling) to convert to Markdown first:

```bash
docling https://example.com/document.pdf --from pdf --to md --output ./data
```

Markdown is easier to parse hierarchically and produces better chunks than raw PDF extraction.

#### 3.12.8. Supported Stores

Embabel Agent provides several RAG store implementations:

##### Lucene (embabel-agent-rag-lucene)

Full-featured store with vector search, text search, and result expansion. Supports both in-memory and file-based persistence:

```java
@Bean
LuceneSearchOperations luceneSearchOperations(
        ModelProvider modelProvider,
        RagbotProperties properties) {
    var embeddingService = modelProvider.getEmbeddingService(
            DefaultModelSelectionCriteria.INSTANCE);
    return LuceneSearchOperations
            .withName("docs")
            .withEmbeddingService(embeddingService)
            .withChunkerConfig(properties.chunkerConfig())
            .withIndexPath(Paths.get("./.lucene-index"))  // File persistence
            .buildAndLoadChunks();
}
```

Omit `.withIndexPath()` for in-memory only storage.

##### Neo4j

Graph database store for RAG (available in separate modules `embabel-agent-rag-neo-drivine` and `embabel-agent-rag-neo-ogm`). Ideal when you need graph relationships between content elements.

##### PostgreSQL pgvector (embabel-rag-pgvector)

PostgreSQL-based RAG store using the pgvector extension (available in the separate `embabel/embabel-rag-pgvector` repository). Supports hybrid search combining vector similarity, full-text search via tsvector/tsquery, and fuzzy matching via pg\_trgm. Ideal when you already use PostgreSQL and want a familiar, battle-tested database for RAG.

##### Spring AI VectorStore (SpringVectorStoreVectorSearch)

Adapter that wraps any Spring AI `VectorStore`, enabling use of any vector database Spring AI supports:

```java
public class SpringVectorStoreVectorSearch implements VectorSearch {
    private final VectorStore vectorStore;

    public SpringVectorStoreVectorSearch(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    @Override
    public <T extends Retrievable> List<SimilarityResult<T>> vectorSearch(
            TextSimilaritySearchRequest request,
            Class<T> clazz) {
        SearchRequest searchRequest = SearchRequest
            .builder()
            .query(request.getQuery())
            .similarityThreshold(request.getSimilarityThreshold())
            .topK(request.getTopK())
            .build();
        List<Document> results = vectorStore.similaritySearch(searchRequest);
        // ... convert results
    }
}
```

This allows integration with Pinecone, Weaviate, Milvus, Chroma, and other stores via Spring AI.

#### 3.12.9. Implementing Your Own RAG Store

To implement a custom RAG store, implement only the `SearchOperations` subinterfaces that are natural and efficient for your store. This is a key design principle: **stores should only implement what they can do well**.

For example:

- A **vector database** like Pinecone might implement only `VectorSearch` since that’s its strength
- A **full-text search engine** might implement `TextSearch` and `RegexSearchOperations`
- A **hierarchical document store** might add `ResultExpander` for context expansion
- A **full-featured store** like Lucene can implement all interfaces

The `ToolishRag` facade automatically exposes only the tools that your store supports. This means you don’t need to provide stub implementations or throw "not supported" exceptions—simply don’t implement interfaces that don’t fit your store’s capabilities.

```java
// A store that only supports vector search
public class MyVectorOnlyStore implements VectorSearch {
    @Override
    public <T extends Retrievable> List<SimilarityResult<T>> vectorSearch(
            TextSimilaritySearchRequest request,
            Class<T> clazz) {
        // Implement vector similarity search
    }
}

// A store that supports both vector and text search
public class MyFullTextStore implements VectorSearch, TextSearch {
    @Override
    public <T extends Retrievable> List<SimilarityResult<T>> vectorSearch(
            TextSimilaritySearchRequest request,
            Class<T> clazz) {
        // Implement vector similarity search
    }

    @Override
    public <T extends Retrievable> List<SimilarityResult<T>> textSearch(
            TextSimilaritySearchRequest request,
            Class<T> clazz) {
        // Implement full-text search
    }

    @Override
    public String getLuceneSyntaxNotes() {
        return "Full Lucene syntax supported";
    }
}
```

For ingestion support, extend `ChunkingContentElementRepository` to handle document storage and chunking.

#### 3.12.10. Complete Example

See the [rag-demo](https://github.com/embabel/rag-demo) project for a complete working example including:

### 3.13. Building Chatbots

Chatbots are an important application of Gen AI, although far from the only use, especially in enterprise.

Unlike many other frameworks, Embabel does not maintain a conversation thread to do its core work. This is a good thing as it means that context compression is not required for most tasks.

If you want to build a chatbot you should use the `Conversation` interface explicitly, and expose a `Chatbot` bean, typically backed by action methods that handle `UserMessage` events.

#### 3.13.1. Core Concepts

##### Long-Lived AgentProcess

An Embabel chatbot is backed by a **long-lived `AgentProcess`** that pauses between user messages. This design has important implications:

- The same `AgentProcess` can respond to events besides user input
- The blackboard maintains state across the entire session
- Actions can be triggered by user messages, system events, or other objects added to the blackboard
- It’s a **working context** rather than just a chat session

When a user sends a message, it’s added to the blackboard as a `UserMessage`. The `AgentProcess` then runs, selects an appropriate action to handle it, and pauses again waiting for the next event.

##### Utility AI for Chatbots

**Utility AI is often the best approach for chatbots.** Instead of defining a fixed flow, you define multiple actions with costs, and the planner selects the highest-value action to respond to each message.

This allows:

- Multiple response strategies (e.g., RAG search, direct answer, clarification request)
- Dynamic behavior based on context
- Easy extensibility by adding new action methods

##### Goals in Chatbots

Typically, chatbot agents **do not need a goal**. The agent process simply waits for user messages and responds to them indefinitely.

However, you can define a goal if you want to ensure the conversation terminates and the `AgentProcess` completes rather than waiting forever. This is useful for:

- Transactional conversations (e.g., completing a booking)
- Wizard-style flows with a defined endpoint
- Conversations that should end after collecting specific information

#### 3.13.2. Key Interfaces

##### Chatbot

The `Chatbot` interface manages multiple chat sessions:

```java
public interface Chatbot {
    ChatSession createSession(
        User user,
        OutputChannel outputChannel,
        String contextId,
        String conversationId
    );

    ChatSession findSession(String conversationId);
}
```

##### Context IDs and Session State

The `contextId` parameter allows you to **pre-populate the session’s blackboard** with objects from a named context. This is useful when:

- **Users have multiple contexts** - A user might have different projects, accounts, or workspaces. Each context can maintain its own state that persists across sessions.
- **Resuming prior state** - When a user returns, you can restore their previous session state (e.g., user preferences, in-progress work, conversation history from a previous session).
- **Pre-loading domain objects** - You can populate the blackboard with objects that should always be present, such as the current user’s profile, active subscription, or relevant configuration.

```java
// Create a session with a specific context
ChatSession session = chatbot.createSession(
    user,
    outputChannel,
    "project-alpha",  // Context ID - loads saved state for this project
    null
);

// Or create an anonymous session without context
ChatSession anonymousSession = chatbot.createSession(
    null,
    outputChannel,
    null,
    null
);
```

The context mechanism works with \`AgentPlatform’s context storage:

1. When `createSession` is called with a `contextId`, the platform looks up any saved objects for that context
2. Those objects are added to the new session’s blackboard
3. As the session runs, changes to the blackboard can be persisted back to the context
4. The next time a session is created with that `contextId`, the updated state is restored

This enables **stateful conversations across sessions** without requiring the chatbot to manually track and restore state.

##### ChatSession

Each session represents an ongoing conversation:

```java
public interface ChatSession {
    OutputChannel getOutputChannel();
    User getUser();
    Conversation getConversation();
    String getProcessId();

    void onUserMessage(UserMessage userMessage);
    boolean isFinished();
}
```

##### Conversation

The `Conversation` interface holds the message history and tracks assets:

```java
public interface Conversation extends StableIdentified, AssetView {
    List<Message> getMessages();
    AssetTracker getAssetTracker();
    List<Asset> getAssets();  // Combined view of all assets
    Message addMessage(Message message);
    UserMessage lastMessageIfBeFromUser();
}
```

Message types include:

#### 3.13.3. Asset Tracking

Chatbots can track **assets** —structured outputs like generated documents, search results, or user-created content—at two levels:

##### Conversation-Level Assets

The `Conversation` has an `AssetTracker` for explicitly tracking assets throughout the session:

```java
// Add an asset to the conversation tracker
conversation.getAssetTracker().addAsset(myAsset);

// Get all tracked assets
List<Asset> trackedAssets = conversation.getAssetTracker().getAssets();
```

Use conversation-level tracking when:

- Assets are created by tools or external processes
- Assets should persist across multiple messages
- You want explicit control over what’s tracked

##### Message-Level Assets

`AssistantMessage` implements `AssetView` and can include assets directly:

```java
AssistantMessage message = new AssistantMessage(
    "Here's the report you requested",
    null,  // name
    null,  // awaitable
    List.of(reportAsset, summaryAsset)  // assets
);
conversation.addMessage(message);
```

Use message-level assets when:

- Assets are directly tied to a specific response
- You want assets to appear alongside the message in the UI
- The asset represents output from that specific interaction

##### Combined Asset View

The `Conversation.assets` property provides a **merged view** of all assets:

```java
// Gets assets from BOTH the tracker AND all messages
List<Asset> allAssets = conversation.getAssets();
```

The merge follows these rules:

1. **Tracker assets appear first** (explicit tracking takes priority)
2. **Message assets follow** in chronological order
3. **Duplicates are removed** by ID (tracker version wins)

This allows flexible asset management:

```java
@Action(canRerun = true, trigger = UserMessage.class)
void respond(Conversation conversation, ActionContext context) {
    // Create an asset from the response
    Asset resultAsset = createResultAsset(result);

    // Option 1: Add to message (appears with this response)
    var message = new AssistantMessage(
        "Here's your analysis",
        null, null,
        List.of(resultAsset)
    );
    conversation.addMessage(message);

    // Option 2: Add to tracker (explicitly tracked)
    conversation.getAssetTracker().addAsset(resultAsset);

    // Either way, it's visible via conversation.getAssets()
}
```

##### Using Assets as Tools

Assets can be exposed to the LLM as tools via their `LlmReference`:

```java
// Get references from recent assets
List<LlmReference> refs = conversation.mostRecent(5).references();

// Use in a prompt
var response = context.ai()
    .withReferences(refs)  // Assets become available as tools
    .respond(conversation.getMessages());
```

This enables scenarios like:

- Editing previously generated content
- Combining multiple assets
- Querying structured data from earlier in the conversation

#### 3.13.4. Building a Chatbot

##### Step 1: Create Action Methods

Define action methods in an `@EmbabelComponent` that respond to user messages using the `trigger` parameter:

```java
@EmbabelComponent
public class ChatActions {

    private final ToolishRag toolishRag;
    private final RagbotProperties properties;

    public ChatActions(
            SearchOperations searchOperations,
            RagbotProperties properties) {
        this.toolishRag = new ToolishRag(
                "sources",
                "Sources for answering user questions",
                searchOperations
        );
        this.properties = properties;
    }

    @Action(canRerun = true, trigger = UserMessage.class)  (1) (2)
    void respond(
            Conversation conversation, (3)
            ActionContext context) {
        var assistantMessage = context.ai()
                .withLlm(properties.chatLlm())
                .withReference(toolishRag)
                .rendering("ragbot")
                .respondWithSystemPrompt(conversation, Map.of(
                        "properties", properties
                ));
        context.sendMessage(conversation.addMessage(assistantMessage)); (4)
    }
}
```

| **1** | `trigger = UserMessage.class` - action is invoked when a `UserMessage` is the last object added to the blackboard |
| --- | --- |
| **2** | `canRerun = true` - action can be executed multiple times (for each user message) |
| **3** | `Conversation` parameter is automatically injected from the blackboard |
| **4** | `context.sendMessage()` sends the response to the output channel |

##### Step 2: Configure the Chatbot Bean

Use `AgentProcessChatbot.utilityFromPlatform()` to create a utility-based chatbot that discovers all available actions:

```java
@Configuration
class ChatConfiguration {

    @Bean
    Chatbot chatbot(AgentPlatform agentPlatform) {
        return AgentProcessChatbot.utilityFromPlatform(agentPlatform);  (1) (2)
    }
}
```

| **1** | Creates a chatbot using Utility AI planning to select the best action |
| --- | --- |
| **2** | Discovers all `@Action` methods from `@EmbabelComponent` classes on the platform |

For debugging, you can pass a custom `Verbosity` configuration:

```java
@Bean
Chatbot chatbot(AgentPlatform agentPlatform) {
    return AgentProcessChatbot.utilityFromPlatform(
            agentPlatform,
            new InMemoryConversationFactory(), (1)
            new Verbosity().showPrompts()      (2)
    );
}
```

| **1** | Conversation factory (required when specifying verbosity) |
| --- | --- |
| **2** | `Verbosity` configuration for debugging prompts |

|  | Be sure that the `AgentPlatform` has loaded all its actions before creating a new session on your `AgentProcessChatbot`. Otherwise the actions needed to respond to chat may not be available in the session. |
| --- | --- |

#### 3.13.5. Conversation Storage

By default, chatbots use **in-memory conversations** that are lost when the session ends. For production applications, you typically want to **persist conversations** to a backing store.

##### Storage Types

Embabel supports two conversation storage types via `ConversationStoreType`:

| Type | Description |
| --- | --- |
| `IN_MEMORY` | Conversations stored in memory only. Fast and simple, suitable for testing and ephemeral sessions. |
| `STORED` | Conversations persisted to a backing store (e.g., Neo4j). Requires `embabel-chat-store` dependency. |

##### Configuring Persistent Storage

To use persistent conversations, inject `ConversationFactoryProvider` and pass the appropriate factory when creating the chatbot:

```java
@Configuration
class ChatConfiguration {

    @Bean
    Chatbot chatbot(
            AgentPlatform agentPlatform,
            ConversationFactoryProvider conversationFactoryProvider) { (1)

        ConversationFactory factory = conversationFactoryProvider
                .getFactory(ConversationStoreType.STORED); (2)

        return new AgentProcessChatbot(
                agentPlatform,
                user -> createAgent(agentPlatform),
                factory,  (3)
                // ... other configuration
        );
    }
}
```

| **1** | Inject the `ConversationFactoryProvider` via Spring DI |
| --- | --- |
| **2** | Get the factory for the desired storage type |
| **3** | Pass the factory to the chatbot - storage is configured once at creation time |

|  | Storage type is configured once when creating the chatbot, not per-call. This ensures consistent behavior across all sessions. |
| --- | --- |

##### Adding embabel-chat-store

To enable persistent storage, add the `embabel-chat-store` dependency:

```xml
<dependency>
    <groupId>com.embabel.chat</groupId>
    <artifactId>embabel-chat-store</artifactId>
</dependency>
```

This provides:

- `StoredConversationFactory` - creates conversations that persist to Neo4j
- `StoredConversation` - conversation implementation with async persistence
- Message lifecycle events (`MessageEvent`) for UI updates
- Title generation for conversation sessions

##### Restoring Conversations

To restore a conversation, pass the `conversationId` when creating a session:

```java
// Restore existing conversation or create new one
ChatSession session = chatbot.createSession(
    user,
    outputChannel,
    null,            // contextId
    conversationId   (1)
);

// Messages are already loaded if conversation existed
List<Message> history = session.getConversation().getMessages();
```

| **1** | If the conversation exists in storage, it will be loaded automatically. If not, a new conversation is created with this ID. |
| --- | --- |

This allows applications to:

- Resume conversations across server restarts
- Display conversation history to returning users
- Continue multi-turn interactions from where they left off

|  | For lower-level access, you can also use `ConversationFactory.load(conversationId)` directly to check if a conversation exists before creating a session. |
| --- | --- |

##### Step 3: Use the Chatbot

Interact with the chatbot through its session interface:

```java
// New session (fresh state, generated conversation ID)
ChatSession session = chatbot.createSession(user, outputChannel, null, null); (1)

// Session with context (restores blackboard state)
ChatSession withContext = chatbot.createSession(user, outputChannel, "user-workspace-123", null); (2)

// Restore existing conversation by ID
ChatSession restored = chatbot.createSession(user, outputChannel, null, savedConversationId); (3)

// Both context and conversation restoration
ChatSession full = chatbot.createSession(user, outputChannel, "user-workspace-123", savedConversationId); (4)

session.onUserMessage(new UserMessage("What does this document say about taxes?")); (5)
// Response is automatically sent to the outputChannel
```

| **1** | Create a new session with fresh blackboard and auto-generated conversation ID |
| --- | --- |
| **2** | Load prior blackboard state from the "user-workspace-123" context |
| **3** | Restore an existing conversation with its message history |
| **4** | Both: load context state AND restore conversation history |
| **5** | Send a user message - triggers the agent to select and run an action |

#### 3.13.6. How Message Triggering Works

When you specify `trigger = UserMessage.class` on an action:

1. The chatbot adds the `UserMessage` to both the `Conversation` and the `AgentProcess` blackboard
2. The planner evaluates all actions whose trigger conditions are satisfied
3. For utility planning, the action with the highest value (lowest cost) is selected
4. The action method receives the `Conversation` (with the new message) via parameter injection

This trigger-based approach means:

- You can have multiple actions that respond to user messages with different costs
- The planner picks the most appropriate response strategy
- Actions can also be triggered by other event types (not just `UserMessage`)

#### 3.13.7. Dynamic Cost Methods

For more sophisticated action selection, use `@Cost` methods:

```java
@Cost (1)
double dynamic(Blackboard bb) { (2)
    return bb.getObjects().size() > 5 ? 100 : 10; (3)
}

@Action(canRerun = true,
        trigger = UserMessage.class,
        costMethod = "dynamic") (4)
void respond(Conversation conversation, ActionContext context) {
    // ...
}
```

| **1** | `@Cost` marks this as a cost calculation method |
| --- | --- |
| **2** | Receives the `Blackboard` to inspect current state |
| **3** | Returns cost value - lower costs mean higher priority |
| **4** | `costMethod` links the action to the cost calculation method |

#### 3.13.8. Prompt Templates

Chatbots typically use **Jinja prompt templates** rather than inline string prompts. This isn’t strictly necessary—simple chatbots can use regular string prompts built in code:

```java
var assistantMessage = context.ai()
        .withLlm(properties.chatLlm())
        .withSystemPrompt("You are a helpful assistant. Answer questions concisely.") (1)
        .respond(conversation.getMessages());
```

| **1** | Simple inline prompt - fine for basic chatbots |
| --- | --- |

However, production chatbots often need **longer, more complex prompts** for:

- Personality and tone (personas)
- Guardrails and safety instructions
- Domain-specific objectives
- Dynamic behavior based on configuration

For these cases, Jinja templates are the better choice:

```java
var assistantMessage = context.ai()
        .withLlm(properties.chatLlm())
        .withReference(toolishRag)
        .rendering("ragbot") (1)
        .respondWithSystemPrompt(conversation, Map.of( (2)
                "properties", properties,
                "persona", properties.persona(),
                "objective", properties.objective()
        ));
```

| **1** | Loads `prompts/ragbot.jinja` from resources |
| --- | --- |
| **2** | Template bindings - accessible in Jinja as `properties.persona()` etc. |

Templates allow:

- Separation of prompt engineering from code
- Dynamic persona and objective selection via configuration
- Reusable prompt elements (guardrails, personalization)
- Prompt iteration without code changes

##### Template Structure Example

A typical chatbot template structure from the rag-demo project:

```java
prompts/
├── ragbot.jinja                    # Main entry point
├── elements/
│   ├── guardrails.jinja            # Safety restrictions
│   └── personalization.jinja       # Dynamic persona/objective loader
├── personas/
│   ├── clause.jinja                # Legal expert persona
│   └── ...
└── objectives/
    └── legal.jinja                 # Legal document analysis objective
```

The main template (`ragbot.jinja`) composes from reusable elements:

```jinja
{% include "elements/guardrails.jinja" %} (1)

{% include "elements/personalization.jinja" %} (2)
```

| **1** | Include safety guardrails first |
| --- | --- |
| **2** | Then include persona and objective (which are dynamically selected) |

Guardrails define safety boundaries (`elements/guardrails.jinja`):

```jinja
{# Safety and content guardrails for the ragbot. #}

DO NOT DISCUSS POLITICS OR CONTROVERSIAL TOPICS.
```

Personalization dynamically loads persona and objective (`elements/personalization.jinja`):

```jinja
{% set persona_template = "personas/" ~ properties.persona() ~ ".jinja" %} (1)
{% include persona_template %}

{% set objective_template = "objectives/" ~ properties.objective() ~ ".jinja" %} (2)
{% include objective_template %}
```

| **1** | Build template path from `properties.persona()` (e.g., "clause" → "personas/clause.jinja") |
| --- | --- |
| **2** | Build template path from `properties.objective()` (e.g., "legal" → "objectives/legal.jinja") |

A persona template (`personas/clause.jinja`):

```jinja
Your name is Clause.
You are a brilliant legal chatbot who excels at interpreting
legislation and legal documents.
```

An objective template (`objectives/legal.jinja`):

```jinja
You are an authoritative interpreter of legislation and legal documents.
You are renowned for thoroughness and for never missing anything.

You answer questions definitively, in a clear and concise manner.
You cite relevant sections to back up your answers.
If you don't know, say you don't know.
NEVER FABRICATE ANSWERS.

You ground your answers in literal citations from the provided sources.
Always use the available tools. (1)
```

| **1** | Instructs the LLM to use RAG tools provided via `withReference()` |
| --- | --- |

This modular approach lets you:

- Switch personas via `application.yml` without code changes
- Share guardrails across multiple chatbot configurations
- Test different objectives independently

#### 3.13.9. Advanced: State Management with @State

For complex chatbots that need to track state across messages, use `@State` classes. State classes are automatically managed by the agent framework:

- State objects are persisted in the blackboard
- Actions can depend on specific state being present
- State transitions drive the conversation flow

Cross-reference the @State annotation documentation for details on:

- Defining state classes
- State-dependent actions
- Nested state machines

#### 3.13.10. Complete Example

See the [rag-demo](https://github.com/embabel/rag-demo) project for a complete chatbot implementation including:

- `ChatActions.java` - Action methods responding to user messages
- `ChatConfiguration.java` - Chatbot bean configuration
- `RagbotShell.java` - Spring Shell integration for interactive testing
- Jinja templates for persona-driven prompts
- RAG integration for document-grounded responses

To run the example:

```bash
./scripts/shell.sh

# In the shell:
ingest ./data/document.md
chat
> What does the document say about...
```

### 3.14. The AgentProcess

An `AgentProcess` is created every time an agent is run. It has a unique id.

### 3.15. ProcessOptions

Agent processes can be configured with `ProcessOptions`.

`ProcessOptions` controls:

- `contextId`: An identifier of any existing context in which the agent is running.
- `blackboard`: The blackboard to use for the agent. Allows starting from a particular state.
- `test`: Whether the agent is running in test mode.
- `verbosity`: The verbosity level of the agent. Allows fine grained control over logging prompts, LLM returns and detailed planning information
- `control`: Control options, determining whether the agent should be terminated as a last resort. `EarlyTerminationPolicy` can based on an absolute number of actions or a maximum budget.
- Delays: Both operations (actions) and tools can have delays. This is useful to avoid rate limiting.

![embabel execution context](https://docs.embabel.com/embabel-agent/guide/0.3.4-SNAPSHOT/images/embabel_execution_context.dot.png)

### 3.16. The AgentPlatform

An `AgentPlatform` provides the ability to run agents in a specific environment. This is an SPI interface, so multiple implementations are possible.

![embabel agent platform model](https://docs.embabel.com/embabel-agent/guide/0.3.4-SNAPSHOT/images/embabel_agent_platform_model.dot.png)

### 3.17. Invoking Embabel Agents

While many examples show Embabel agents being invoked via `UserInput` through the Embabel shell, they can also be invoked programmatically with strong typing.

This is usually how they’re used in web applications. It is also the most deterministic approach as code, rather than LLM assessment of user input, determines which agent is invoked and how.

#### 3.17.1. Creating an AgentProcess Programmatically

You can create and execute agent processes directly using the `AgentPlatform`:

```java
// Create an agent process with bindings
AgentProcess agentProcess = agentPlatform.createAgentProcess(
    myAgent,
    new ProcessOptions(),
    Map.of("input", userRequest)
);

// Start the process and wait for completion
Object result = agentPlatform.start(agentProcess).get();

// Or run synchronously
AgentProcess completedProcess = agentProcess.run();
MyResultType result = completedProcess.last(MyResultType.class);
```

You can create processes and populate their input map from varargs objects:

```java
// Create process from objects (like in web controllers)
AgentProcess agentProcess = agentPlatform.createAgentProcessFrom(
    travelAgent,
    new ProcessOptions(),
    travelRequest,
    userPreferences
);
```

#### 3.17.2. Using AgentInvocation

`AgentInvocation` provides a higher-level, type-safe API for invoking agents. It automatically finds the appropriate agent based on the expected result type.

##### Basic Usage

```java
// Simple invocation with explicit result type
var invocation =
    AgentInvocation.create(agentPlatform, TravelPlan.class);

TravelPlan plan = invocation.invoke(travelRequest);
```

##### Invocation with Named Inputs

```java
// Invoke with a map of named inputs
Map<String, Object> inputs = Map.of(
    "request", travelRequest,
    "preferences", userPreferences
);

TravelPlan plan = invocation.invoke(inputs);
```

##### Custom Process Options

Configure verbosity, budget, and other execution options:

```java
var processOptions = new ProcessOptions()
    .withVerbosity(new Verbosity()
        .withShowPrompts(true)
        .withShowLlmResponses(true)
        .withDebug(true));

var invocation =
    AgentInvocation.builder(agentPlatform)
        .options(processOptions)
        .build(TravelPlan.class);

TravelPlan plan = invocation.invoke(travelRequest);
```

##### Asynchronous Invocation

For long-running operations, use async invocation:

```java
CompletableFuture<TravelPlan> future = invocation.invokeAsync(travelRequest);

// Handle result when complete
future.thenAccept(plan -> {
    logger.info("Travel plan generated: {}", plan);
});

// Or wait for completion
TravelPlan plan = future.get();
```

##### Agent Selection

`AgentInvocation` automatically finds agents by examining their goals:

- Searches all registered agents in the platform
- Finds agents with goals that produce the requested result type
- Uses the first matching agent found
- Throws an error if no suitable agent is available

##### Real-World Web Application Example

Here’s how `AgentInvocation` is used in the [Tripper travel planning application](http://github.com/embabel/tripper) with htmx for asynchronous UI updates:

```java
@Controller
public class TripPlanningController {

    private final AgentPlatform agentPlatform;
    private final ConcurrentHashMap<String, CompletableFuture<TripPlan>> activeJobs =
        new ConcurrentHashMap<>();
    private static final Logger logger =
        LoggerFactory.getLogger(TripPlanningController.class);
    private static final ConcurrentHashMap<String, TripPlan> tripResultCache =
        new ConcurrentHashMap<>();

    public TripPlanningController(AgentPlatform agentPlatform) {
        this.agentPlatform = agentPlatform;
    }

    @PostMapping("/plan-trip")
    public String planTrip(
            @ModelAttribute TripRequest tripRequest,
            Model model) {
        // Generate unique job ID for tracking
        String jobId = UUID.randomUUID().toString();

        // Create agent invocation with custom options
        var processOptions = new ProcessOptions()
            .withVerbosity(new Verbosity().withShowPrompts(true));
        var invocation = AgentInvocation.builder(agentPlatform)
            .options(processOptions)
            .build(TripPlan.class);

        // Start async agent execution
        CompletableFuture<TripPlan> future = invocation.invokeAsync(tripRequest);
        activeJobs.put(jobId, future);

        // Set up completion handler
        future.whenComplete((result, throwable) -> {
            if (throwable != null) {
                logger.error("Trip planning failed for job {}", jobId, throwable);
            } else {
                logger.info("Trip planning completed for job {}", jobId);
            }
        });

        model.addAttribute("jobId", jobId);
        model.addAttribute("tripRequest", tripRequest);

        // Return htmx template that will poll for results
        return "trip-planning-progress";
    }

    @GetMapping("/trip-status/{jobId}")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getTripStatus(@PathVariable String jobId) {
        CompletableFuture<TripPlan> future = activeJobs.get(jobId);
        if (future == null) {
            return ResponseEntity.notFound().build();
        }

        if (future.isDone()) {
            try {
                TripPlan tripPlan = future.get();
                activeJobs.remove(jobId);

                return ResponseEntity.ok(Map.of(
                    "status", "completed",
                    "result", tripPlan,
                    "redirect", "/trip-result/" + jobId
                ));
            } catch (Exception e) {
                activeJobs.remove(jobId);
                return ResponseEntity.ok(Map.of(
                    "status", "failed",
                    "error", e.getMessage()
                ));
            }
        } else if (future.isCancelled()) {
            activeJobs.remove(jobId);
            return ResponseEntity.ok(Map.of("status", "cancelled"));
        } else {
            return ResponseEntity.ok(Map.of(
                "status", "in_progress",
                "message", "Planning your amazing trip..."
            ));
        }
    }

    @GetMapping("/trip-result/{jobId}")
    public String showTripResult(
            @PathVariable String jobId,
            Model model) {
        // Retrieve completed result from cache or database
        TripPlan tripPlan = tripResultCache.get(jobId);
        if (tripPlan == null) {
            return "redirect:/error";
        }

        model.addAttribute("tripPlan", tripPlan);
        return "trip-result";
    }

    @DeleteMapping("/cancel-trip/{jobId}")
    @ResponseBody
    public ResponseEntity<Map<String, String>> cancelTrip(@PathVariable String jobId) {
        CompletableFuture<TripPlan> future = activeJobs.get(jobId);

        if (future != null && !future.isDone()) {
            future.cancel(true);
            activeJobs.remove(jobId);
            return ResponseEntity.ok(Map.of("status", "cancelled"));
        } else {
            return ResponseEntity.badRequest()
                .body(Map.of("error", "Job not found or already completed"));
        }
    }
}
```

**Key Patterns:**

- **Async Execution**: Uses `invokeAsync()` to avoid blocking the web request
- **Job Tracking**: Maintains a map of active futures for status polling
- **htmx Integration**: Returns status updates that htmx can consume for UI updates
- **Error Handling**: Proper exception handling and user feedback
- **Resource Cleanup**: Removes completed jobs from memory
- **Process Options**: Configures verbosity and debugging for production use

##### Alternative: Direct AgentProcess Creation

For simpler use cases, you can create and start an `AgentProcess` directly without `AgentInvocation`. This approach is used in the [Tripper](http://github.com/embabel/tripper) application and works well with webhooks or form submissions where you want to:

- Start a long-running agent process
- Return immediately with a process ID
- Poll for status using the platform’s built-in controllers

```java
@Controller
@RequestMapping("/journey")
public class JourneyController {

    private final AgentPlatform agentPlatform;

    public JourneyController(AgentPlatform agentPlatform) {
        this.agentPlatform = agentPlatform;
    }

    @PostMapping("/plan")
    public String planJourney(@ModelAttribute JourneyPlanForm form, Model model) {
        // Convert form to domain objects
        TravelBrief travelBrief = new TravelBrief(
            form.getFrom(),
            form.getTo(),
            form.getDepartureDate(),
            form.getReturnDate(),
            form.getBrief()
        );

        // Find the appropriate agent
        Agent agent = agentPlatform.agents().stream()
            .filter(a -> a.getName().toLowerCase().contains("travel"))
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("No travel agent found"));

        // Create the agent process with input bindings
        AgentProcess agentProcess = agentPlatform.createAgentProcessFrom(
            agent,
            new ProcessOptions(
                new Verbosity().withShowPrompts(true),
                Budget.DEFAULT  // or custom budget
            ),
            travelBrief  // Vararg inputs bound to blackboard
        );

        // Start the process asynchronously
        agentPlatform.start(agentProcess);

        // Add process ID to model for status polling
        model.addAttribute("processId", agentProcess.getId());
        model.addAttribute("travelBrief", travelBrief);

        // Return a view that polls /api/v1/process/{processId} for status
        return "processing";
    }
}
```

The platform provides built-in REST endpoints for status checking:

- `GET /api/v1/process/{processId}` - Returns process status, result, and URLs
- `DELETE /api/v1/process/{processId}` - Terminates a running process
- `GET /events/process/{processId}` - SSE stream of process events

A simple status polling controller can check completion and redirect to results:

```java
@Controller
public class ProcessStatusController {

    private final AgentPlatform agentPlatform;

    public ProcessStatusController(AgentPlatform agentPlatform) {
        this.agentPlatform = agentPlatform;
    }

    @GetMapping("/status/{processId}")
    public String checkStatus(
            @PathVariable String processId,
            @RequestParam String successView,
            @RequestParam String resultModelKey,
            Model model) {

        AgentProcess process = agentPlatform.getAgentProcess(processId);
        if (process == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Process not found");
        }

        switch (process.getStatus()) {
            case COMPLETED:
                model.addAttribute(resultModelKey, process.lastResult());
                return successView;

            case FAILED:
                model.addAttribute("error", "Process failed: " + process.getFailureInfo());
                return "error";

            case TERMINATED:
                model.addAttribute("error", "Process was terminated");
                return "error";

            default:
                // Still running - return polling view
                model.addAttribute("processId", processId);
                return "processing";
        }
    }
}
```

**When to Use Each Approach:**

| Approach | Best For |
| --- | --- |
| `AgentInvocation.invokeAsync()` | When you need a `CompletableFuture` for programmatic handling, chaining, or integration with reactive frameworks |
| Direct `AgentProcess` creation | Webhooks, form submissions, or UI flows where you poll for status via REST/SSE |

##### Webhook Integration Example

For webhook-triggered workflows (e.g., JIRA, GitHub), the direct approach works well:

```java
@RestController
@RequestMapping("/webhook")
public class WebhookController {

    private final AgentPlatform agentPlatform;

    public WebhookController(AgentPlatform agentPlatform) {
        this.agentPlatform = agentPlatform;
    }

    @PostMapping("/jira/issue-created")
    public ResponseEntity<Map<String, String>> onJiraIssueCreated(
            @RequestBody JiraWebhookPayload payload) {

        // Find agent that handles JIRA issues
        Agent agent = agentPlatform.agents().stream()
            .filter(a -> a.getName().contains("JiraIssue"))
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("No JIRA agent configured"));

        // Create domain object from webhook payload
        JiraIssue issue = new JiraIssue(
            payload.getIssue().getKey(),
            payload.getIssue().getFields().getSummary(),
            payload.getIssue().getFields().getDescription()
        );

        // Create and start the agent process
        AgentProcess process = agentPlatform.createAgentProcessFrom(
            agent,
            ProcessOptions.DEFAULT,
            issue
        );
        agentPlatform.start(process);

        // Return process ID for status tracking
        return ResponseEntity.accepted().body(Map.of(
            "processId", process.getId(),
            "statusUrl", "/api/v1/process/" + process.getId(),
            "sseUrl", "/events/process/" + process.getId()
        ));
    }
}
```

The webhook caller can then poll `/api/v1/process/{processId}` or subscribe to SSE events at `/events/process/{processId}` to track progress.

|  | Agents can also be exposed as [MCP](https://docs.embabel.com/embabel-agent/guide/0.3.4-SNAPSHOT/#reference.integrations__mcp) servers and consumed from tools like Claude Desktop. |
| --- | --- |

#### 3.17.3. Dynamic Agent and Goal Selection with Autonomy

The `Autonomy` class provides LLM-powered dynamic selection of agents and goals based on user intent. Rather than programmatically choosing which agent to run, `Autonomy` uses an LLM to rank available agents or goals against the user’s input and select the best match.

This is how the Embabel Shell processes natural language commands.

##### Execution Modes

`Autonomy` supports two execution modes:

**Closed Mode** (`chooseAndRunAgent`): The LLM selects the most appropriate agent based on the user’s intent. The selected agent runs in isolation using only its own actions and goals.

**Open Mode** (`chooseAndAccomplishGoal`): The LLM selects the most appropriate goal from all available goals across all agents. Embabel then assembles a dynamic agent that can use any action from any agent to achieve that goal.

##### Closed Mode Example

Use closed mode when you want strict agent boundaries:

```java
@Service
public class IntentHandler {

    private final Autonomy autonomy;

    public IntentHandler(Autonomy autonomy) {
        this.autonomy = autonomy;
    }

    public AgentProcessExecution handleUserIntent(String userIntent) {
        // LLM ranks all agents and selects the best match
        return autonomy.chooseAndRunAgent(
            userIntent,
            ProcessOptions.DEFAULT
        );
    }
}
```

##### Open Mode Example

Use open mode when you want maximum flexibility in achieving goals:

```java
@Service
public class GoalHandler {

    private final Autonomy autonomy;
    private final AgentPlatform agentPlatform;

    public GoalHandler(Autonomy autonomy, AgentPlatform agentPlatform) {
        this.autonomy = autonomy;
        this.agentPlatform = agentPlatform;
    }

    public AgentProcessExecution handleUserIntent(String userIntent) {
        // LLM ranks all goals and selects the best match
        // Then assembles an agent from available actions to achieve it
        return autonomy.chooseAndAccomplishGoal(
            ProcessOptions.DEFAULT,
            GoalChoiceApprover.APPROVE_ALL,
            agentPlatform,  // AgentScope containing goals and actions
            Map.of("userInput", new UserInput(userIntent)),
            new GoalSelectionOptions()
        );
    }
}
```

##### Using Arbitrary Bindings

`chooseAndAccomplishGoal` accepts any bindings, not just `UserInput`. A `BindingsFormatter` extracts intent text from the bindings for goal ranking:

```java
public AgentProcessExecution processTask(Task task, Person person) {
    // Bindings can be any objects
    Map<String, Object> bindings = Map.of(
        "task", task,
        "person", person
    );

    return autonomy.chooseAndAccomplishGoal(
        ProcessOptions.DEFAULT,
        GoalChoiceApprover.APPROVE_ALL,
        agentPlatform,
        bindings,
        new GoalSelectionOptions(),
        BindingsFormatter.DEFAULT  // Extracts intent from bindings
    );
}
```

The default `BindingsFormatter` extracts text using this priority:

1. `PromptContributor.contribution()` if the object implements `PromptContributor`
2. `HasInfoString.infoString()` if the object implements `HasInfoString`
3. `toString()` otherwise

You can provide a custom formatter:

```java
BindingsFormatter customFormatter = bindings -> {
    Task task = (Task) bindings.get("task");
    Person person = (Person) bindings.get("person");
    return String.format("Process task '%s' for %s", task.getDescription(), person.getName());
};

return autonomy.chooseAndAccomplishGoal(
    ProcessOptions.DEFAULT,
    GoalChoiceApprover.APPROVE_ALL,
    agentPlatform,
    bindings,
    new GoalSelectionOptions(),
    customFormatter
);
```

##### Goal Choice Approval

You can require approval before executing a selected goal:

```java
// Approve only high-confidence matches
GoalChoiceApprover approver = GoalChoiceApprover.approveWithScoreOver(0.8);

// Or implement custom approval logic
GoalChoiceApprover customApprover = request -> {
    if (request.getGoal().getName().contains("dangerous")) {
        return new GoalChoiceNotApproved("Dangerous goals require manual approval");
    }
    return GoalChoiceApproved.INSTANCE;
};
```

##### Confidence Thresholds

`Autonomy` uses configurable confidence thresholds to filter matches. If no agent or goal exceeds the threshold, a `NoAgentFound` or `NoGoalFound` exception is thrown.

Configure thresholds in `application.properties`:

```properties
# Minimum confidence for agent selection (0.0 to 1.0)
embabel.agent.platform.autonomy.agent-confidence-cut-off=0.6

# Minimum confidence for goal selection (0.0 to 1.0)
embabel.agent.platform.autonomy.goal-confidence-cut-off=0.6
```

Or override per-request using `GoalSelectionOptions`:

```java
GoalSelectionOptions options = new GoalSelectionOptions(
    0.5,    // goalConfidenceCutOff - override platform default
    null,   // agentConfidenceCutOff - use platform default
    false   // multiGoal - whether to select multiple goals
);
```

##### Shell Usage

The Embabel Shell uses `Autonomy` for the `execute` (or `x`) command:

```bash
# Closed mode (default) - select best agent
x "Find a horoscope for Alice who is a Scorpio"

# Open mode - select best goal, use any actions
x "Find a horoscope for Alice who is a Scorpio" -o

# Show goal rankings without executing
choose-goal "Find a horoscope for Alice"
```

##### Handling Selection Failures

```java
try {
    return autonomy.chooseAndRunAgent(userIntent, ProcessOptions.DEFAULT);
} catch (NoAgentFound e) {
    // No agent matched with sufficient confidence
    logger.info("No matching agent. Rankings: {}", e.getAgentRankings());
    return fallbackResponse();
} catch (NoGoalFound e) {
    // No goal matched with sufficient confidence (open mode)
    logger.info("No matching goal. Rankings: {}", e.getGoalRankings());
    return fallbackResponse();
} catch (GoalNotApproved e) {
    // Goal was rejected by the approver
    logger.info("Goal not approved: {}", e.getReason());
    return requiresApprovalResponse();
}
```

### 3.18. Using States

GOAP planning has many benefits, but can make looping hard to express. For this reason, Embabel supports the notion of **states** within a GOAP plan.

#### 3.18.1. How States Work with GOAP

Within each state, GOAP planning works normally. Actions have preconditions based on the types they require, and effects based on the types they produce. The planner finds the optimal sequence of actions to reach the goal.

When an action returns a `@State` -annotated class, the framework:

1. **Hides previous state objects** - Any existing state objects are hidden from the blackboard
2. **Binds the new state object** - The returned state is added to the blackboard
3. **Re-plans from the new state** - The planner considers only actions from the new state
4. **Continues execution** - Until a goal is reached or no plan can be found

**Context is preserved** across state transitions - non-state objects (such as user messages, customer data, and conversation history) remain available. Only state objects are hidden, ensuring that only the current state’s actions are considered by the planner.

|  | State transitions **hide** previous state objects but do **not clear** the blackboard. Non-state objects remain available in the new state. To clear the entire blackboard (e.g., for looping), use `clearBlackboard = true` on the action. |
| --- | --- |

#### 3.18.2. When to Use States

States are ideal for:

- **Linear stages** where each stage naturally flows to the next
- **Branching workflows** where a decision point leads to different processing paths
- **Looping patterns** where processing may need to repeat (e.g., revise-and-review cycles)
- **Human-in-the-loop workflows** where user feedback determines the next state
- **Complex workflows** that are easier to reason about as discrete phases

States allow loopback to a whole state, which may contain one or more actions. This is more flexible than traditional GOAP, where looping requires careful management of preconditions.

#### 3.18.3. Staying in the Current State

An action can return `this` to stay in the current state. This is useful for actions that respond to inputs without changing state, such as chat handlers:

```java
@State
record ChitchatState(String context) {
    @Action(canRerun = true)  (1)
    ChitchatState respond(UserMessage message, Ai ai) {
        var response = ai.generateText("Respond to: " + message.content());
        // ... send response
        return this;  (2)
    }
}
```

| **1** | `canRerun = true` is required - by default, actions only run once per process |
| --- | --- |
| **2** | Returning `this` keeps the same state instance active |

When an action returns `this`:

- The state remains active with no transition
- The blackboard is preserved (no clearing)
- The action can run again on subsequent planning cycles (if `canRerun = true`)

|  | Without `canRerun = true`, the action’s `hasRun` flag would prevent it from executing again, even though it returned `this`. |
| --- | --- |

#### 3.18.4. Looping States

For looping patterns where an action may return to a previously-visited state type, use `clearBlackboard = true` on the looping action:

```java
@State
record ProcessingState(String data, int iteration) implements LoopOutcome {
    @Action(clearBlackboard = true)  (1)
    LoopOutcome process() {
        if (iteration >= 3) {
            return new DoneState(data);  (2)
        }
        return new ProcessingState(data + "+", iteration + 1);  (3)
    }
}
```

| **1** | `clearBlackboard = true` allows the action to loop back to the same state type |
| --- | --- |
| **2** | Terminal condition exits the loop |
| **3** | Returns a new instance of the same state type for another iteration |

Without `clearBlackboard = true`, the planner would see the output type already exists on the blackboard and skip the action. Clearing the blackboard resets the context, allowing natural loops.

|  | Only use `clearBlackboard = true` on actions that participate in loops. For linear state transitions, the default behavior (preserving the blackboard) is usually preferred. |
| --- | --- |

#### 3.18.5. The @State Annotation

Classes returned from actions that should trigger state transitions must be annotated with `@State`:

```java
@State
record ProcessingState(String data) {
    @Action
    NextState process() {
        return new NextState(data.toUpperCase());
    }
}
```

##### Inheritance

The `@State` annotation is inherited through the class hierarchy. If a superclass or interface is annotated with `@State`, all subclasses and implementing classes are automatically considered state types. This means you don’t need to annotate every class in a hierarchy - just annotate the base type.

```java
@State
interface Stage {}  (1)

record AssessStory(String content) implements Stage { ... }  (2)
record ReviseStory(String content) implements Stage { ... }
record Done(String content) implements Stage { ... }
```

| **1** | Only the parent interface needs `@State` |
| --- | --- |
| **2** | Implementing records/data classes are automatically treated as state types |

This works with:

- **Interfaces**: Classes implementing a `@State` interface are state types
- **Abstract classes**: Classes extending a `@State` abstract class are state types
- **Concrete classes**: Classes extending a `@State` class are state types
- **Deep hierarchies**: The annotation is inherited through multiple levels

##### Behavior

When an action returns a `@State` -annotated class (or a class that inherits `@State`):

- Any previous state objects are **hidden** from the blackboard (not removed, but no longer visible)
- The returned object is bound to the blackboard (as `it`)
- Planning considers only actions defined within the **current** state class
- Any `@AchievesGoal` methods in the state become potential goals

Context (non-state objects) is preserved across state transitions. This means user messages, customer data, conversation history, etc. remain available in the new state. Only state objects are hidden, providing **state scoping** - ensuring only the current state’s actions are considered.

|  | For looping states that return to a previously-visited state type, use `@Action(clearBlackboard = true)` on the looping action. This clears the blackboard (including hasRun conditions) and allows the loop to continue. See [Looping States](https://docs.embabel.com/embabel-agent/guide/0.3.4-SNAPSHOT/#looping-states) for details. |
| --- | --- |

#### 3.18.6. Parent State Interface Pattern

For dynamic choice between states, define a parent interface (or sealed interface/class) that child states implement. Thanks to [inheritance](https://docs.embabel.com/embabel-agent/guide/0.3.4-SNAPSHOT/#inheritance), you only need to annotate the parent interface - all implementing classes are automatically state types:

```java
@State
interface Stage {}  (1)

record AssessStory(String content) implements Stage {  (2)
    @Action
    Stage assess() {
        if (isAcceptable()) {
            return new Done(content);
        } else {
            return new ReviseStory(content);
        }
    }
}

record ReviseStory(String content) implements Stage {
    @Action
    AssessStory revise() {
        return new AssessStory(improvedContent());
    }
}

record Done(String content) implements Stage {
    @AchievesGoal(description = "Processing complete")
    @Action
    Output complete() {
        return new Output(content);
    }
}
```

| **1** | `@State` on the parent interface |
| --- | --- |
| **2** | No `@State` needed on implementing records/data classes - they inherit it from `Stage` |

This pattern enables:

- **Polymorphic return types**: Actions can return any implementation of the parent interface
- **Dynamic routing**: The runtime value determines which state is entered
- **Looping**: States can return other states that eventually loop back

The framework automatically discovers all implementations of the parent interface and registers their actions as potential next steps.

#### 3.18.7. Example: WriteAndReviewAgent

The following example demonstrates a complete write-and-review workflow with:

- State-based flow control with looping
- Human-in-the-loop feedback using `WaitFor`
- LLM-powered content generation and assessment
- Configurable properties passed through states

```java
abstract class Personas { (1)
    static final RoleGoalBackstory WRITER = RoleGoalBackstory
            .withRole("Creative Storyteller")
            .andGoal("Write engaging and imaginative stories")
            .andBackstory("Has a PhD in French literature; used to work in a circus");

    static final Persona REVIEWER = new Persona(
            "Media Book Review",
            "New York Times Book Reviewer",
            "Professional and insightful",
            "Help guide readers toward good stories"
    );
}

@Agent(description = "Generate a story based on user input and review it")
public class WriteAndReviewAgent {

    public record Story(String text) {}

    public record ReviewedStory(
            Story story,
            String review,
            Persona reviewer
    ) implements HasContent, Timestamped {
        // ... content formatting methods
    }

    @State
    interface Stage {} (2)

    record Properties( (3)
            int storyWordCount,
            int reviewWordCount
    ) {}

    private final Properties properties;

    WriteAndReviewAgent(
            @Value("${storyWordCount:100}") int storyWordCount,
            @Value("${reviewWordCount:100}") int reviewWordCount
    ) {
        this.properties = new Properties(storyWordCount, reviewWordCount);
    }

    @Action
    AssessStory craftStory(UserInput userInput, Ai ai) { (4)
        var draft = ai
                .withLlm(LlmOptions.withAutoLlm().withTemperature(.7))
                .withPromptContributor(Personas.WRITER)
                .createObject(String.format("""
                        Craft a short story in %d words or less.
                        The story should be engaging and imaginative.
                        Use the user's input as inspiration if possible.

                        # User input
                        %s
                        """,
                        properties.storyWordCount,
                        userInput.getContent()
                ).trim(), Story.class);
        return new AssessStory(userInput, draft, properties); (5)
    }

    record HumanFeedback(String comments) {} (6)

    private record AssessmentOfHumanFeedback(boolean acceptable) {}

    @State
    record AssessStory(UserInput userInput, Story story, Properties properties) implements Stage {

        @Action
        HumanFeedback getFeedback() { (7)
            return WaitFor.formSubmission("""
                    Please provide feedback on the story
                    %s
                    """.formatted(story.text),
                    HumanFeedback.class);
        }

        @Action(clearBlackboard = true)  (8)
        Stage assess(HumanFeedback feedback, Ai ai) {
            var assessment = ai.withDefaultLlm().createObject("""
                    Based on the following human feedback, determine if the story is acceptable.
                    Return true if the story is acceptable, false otherwise.

                    # Story
                    %s

                    # Human feedback
                    %s
                    """.formatted(story.text(), feedback.comments),
                    AssessmentOfHumanFeedback.class);
            if (assessment.acceptable) {
                return new Done(userInput, story, properties); (9)
            } else {
                return new ReviseStory(userInput, story, feedback, properties); (10)
            }
        }
    }

    @State
    record ReviseStory(UserInput userInput, Story story, HumanFeedback humanFeedback,
                       Properties properties) implements Stage {

        @Action(clearBlackboard = true)  (11)
        AssessStory reviseStory(Ai ai) {
            var draft = ai
                    .withLlm(LlmOptions.withAutoLlm().withTemperature(.7))
                    .withPromptContributor(Personas.WRITER)
                    .createObject(String.format("""
                            Revise a short story in %d words or less.
                            Use the user's input as inspiration if possible.

                            # User input
                            %s

                            # Previous story
                            %s

                            # Revision instructions
                            %s
                            """,
                            properties.storyWordCount,
                            userInput.getContent(),
                            story.text(),
                            humanFeedback.comments
                    ).trim(), Story.class);
            return new AssessStory(userInput, draft, properties); (12)
        }
    }

    @State
    record Done(UserInput userInput, Story story, Properties properties) implements Stage {

        @AchievesGoal( (13)
                description = "The story has been crafted and reviewed by a book reviewer",
                export = @Export(remote = true, name = "writeAndReviewStory"))
        @Action
        ReviewedStory reviewStory(Ai ai) {
            var review = ai
                    .withAutoLlm()
                    .withPromptContributor(Personas.REVIEWER)
                    .generateText(String.format("""
                            You will be given a short story to review.
                            Review it in %d words or less.
                            Consider whether the story is engaging, imaginative, and well-written.

                            # Story
                            %s

                            # User input that inspired the story
                            %s
                            """,
                            properties.reviewWordCount,
                            story.text(),
                            userInput.getContent()
                    ).trim());
            return new ReviewedStory(story, review, Personas.REVIEWER);
        }
    }
}
```

| **1** | **Personas**: Reusable prompt contributors that give the LLM context about its role |
| --- | --- |
| **2** | **Parent state interface**: Allows actions to return any implementing state dynamically |
| **3** | **Properties record**: Configuration bundled together for easy passing through states |
| **4** | **Entry action**: Uses LLM to generate initial story draft |
| **5** | **State transition**: Returns `AssessStory` with all necessary data |
| **6** | **HITL data type**: Simple record/data class to capture human feedback |
| **7** | **WaitFor integration**: Pauses execution and waits for user to submit feedback form |
| **8** | **Looping action**: `clearBlackboard = true` enables returning to a previously-visited state type |
| **9** | **Terminal branch**: If acceptable, transitions to `Done` state |
| **10** | **Loop branch**: If not acceptable, transitions to `ReviseStory` with the feedback |
| **11** | **Looping action**: `clearBlackboard = true` enables looping back to `AssessStory` |
| **12** | **Loop back**: Returns new `AssessStory` for another round of feedback |
| **13** | **Goal achievement**: Final action that produces the reviewed story and exports it |

#### 3.18.8. Execution Flow

The execution flow for this agent:

1. **`craftStory`** executes with LLM, returns `AssessStory` → enters `AssessStory` state
2. **`getFeedback`** calls `WaitFor.formSubmission()` → agent pauses, waits for user input
3. User submits feedback → `HumanFeedback` added to blackboard
4. **`assess`** executes with LLM to interpret feedback:
	- If acceptable: returns `Done` → blackboard cleared, enters `Done` state
	- If not acceptable: returns `ReviseStory` → blackboard cleared, enters `ReviseStory` state
5. If in `ReviseStory`: **`reviseStory`** executes with LLM, returns `AssessStory` → blackboard cleared, loop back to step 2
6. When in `Done`: **`reviewStory`** executes with LLM, returns `ReviewedStory` → goal achieved

The planner handles all transitions automatically, including loops. The looping actions (`assess` and `reviseStory`) use `clearBlackboard = true` to enable returning to previously-visited state types.

#### 3.18.9. Human-in-the-Loop with WaitFor

The `WaitFor.formSubmission()` method is key for human-in-the-loop workflows:

```java
@Action
HumanFeedback getFeedback() {
    return WaitFor.formSubmission("""
            Please provide feedback on the story
            %s
            """.formatted(story.text),
            HumanFeedback.class);
}
```

When this action executes:

1. The agent process enters a `WAITING` state
2. A form is generated based on the `HumanFeedback` record structure
3. The user sees the prompt and fills out the form
4. Upon submission, the `HumanFeedback` instance is created and added to the blackboard
5. The agent resumes execution with the feedback available

This integrates naturally with the state pattern: the feedback stays within the current state until the next state transition.

#### 3.18.10. Passing Data Through States

When using `clearBlackboard = true` for looping states, all necessary context must be passed through state records since the blackboard is cleared:

```java
@State
record AssessStory(
    UserInput userInput,    // Original user request
    Story story,            // Current story draft
    Properties properties   // Configuration
) implements Stage { ... }

@State
record ReviseStory(
    UserInput userInput,
    Story story,
    HumanFeedback humanFeedback,  // Additional context for revision
    Properties properties
) implements Stage { ... }
```

|  | Use a `Properties` record/data class to bundle configuration values that need to pass through multiple states, rather than repeating individual fields. |
| --- | --- |

|  | For non-looping state transitions (where `clearBlackboard` is not used), the blackboard is preserved, and data can be accessed from the blackboard directly. This is useful when states need access to shared context like user identity or conversation history. |
| --- | --- |

#### 3.18.11. State Class Requirements

|  | State classes **must be** either **static nested classes** (Java) or **top-level classes** (Kotlin). Non-static inner classes are **not allowed** because they hold a reference to their enclosing instance, causing serialization and persistence issues. The framework will throw an `IllegalStateException` if it detects a non-static inner class annotated with `@State`. |
| --- | --- |

```java
// GOOD: Static nested class (Java record is implicitly static)
@State
record AssessStory(UserInput userInput, Story story) implements Stage { ... }

// GOOD: Top-level class
@State
record ProcessingState(String data) { ... }

// BAD: Non-static inner class - will throw IllegalStateException
@State
class AssessStory implements Stage { ... } // Inner class in non-static context
```

In Java, records declared inside a class are implicitly static, making them ideal for state classes. In Kotlin, data classes declared inside a class are inner by default; use **top-level declarations** instead.

|  | Top-level state classes are the recommended pattern for Kotlin. They can access the enclosing component via the `@Provided` annotation. See [The @Provided Annotation](https://docs.embabel.com/embabel-agent/guide/0.3.4-SNAPSHOT/reference.annotations) for full documentation. |
| --- | --- |

#### 3.18.12. Key Points

- Annotate state classes with `@State` (or inherit from a `@State` -annotated type)
- `@State` is inherited through class hierarchies - annotate only the base type
- Use **static nested classes** (Java records) or **top-level classes** to avoid persistence issues
- Use a parent interface for polymorphic state returns
- State actions are automatically discovered and registered
- **State scoping**: When entering a new state, previous states are hidden - only current state’s actions are available
- **Context is preserved**: Non-state objects (user data, conversation, etc.) remain available across transitions
- **Blackboard preserved**: State transitions hide previous states but preserve all other blackboard contents
- **Staying in state**: Return `this` with `canRerun = true` to stay in the current state without transitioning
- For **looping states**, use `@Action(clearBlackboard = true)` to enable returning to previously-visited state types
- When using `clearBlackboard = true`, pass all necessary data through state record fields
- Goals are defined with `@AchievesGoal` on terminal state actions
- Use `WaitFor` for human-in-the-loop interactions within states
- Within a state, normal GOAP planning applies to sequence actions

### 3.19. Choosing a Planner

Embabel supports multiple planning strategies. Most are deterministic, but their behaviour differs—although it is always predictable.

All planning strategies are entirely typesafe in Java or Kotlin.

The planning strategies currently supported out of the box are:

| Planner | Best For | Description |
| --- | --- | --- |
| **GOAP** (default) | Business processes with defined outputs | Goal-oriented, deterministic planning. Plans a path from current state to goal using preconditions and effects. |
| **Utility** | Exploration and event-driven systems | Selects the highest-value available action at each step. Ideal when you don’t know the outcome upfront. |
| **Supervisor** | Flexible multi-step workflows | LLM-orchestrated composition. An LLM selects which actions to call based on type schemas and gathered artifacts. |

As most of the documentation covers GOAP, this section discusses the alternative planners and nested workflows.

#### 3.19.1. Utility AI

[Utility AI](https://en.wikipedia.org/wiki/Utility_system) selects the action with the highest *net value* from all available actions at each step. Unlike GOAP, which plans a path to a goal, Utility AI makes greedy decisions based on immediate value.

Utility AI excels in **exploratory scenarios** where you don’t know exactly what you want to achieve. Consider a GitHub issue triage system: when a new issue arrives, you don’t have a predetermined goal. Instead, you want to react appropriately based on the issue’s characteristics—maybe label it, maybe respond, maybe escalate. The "right" action depends on what you discover as you process it.

This makes Utility AI ideal for scenarios where:

- There is no clear end goal—you’re exploring possibilities
- Multiple actions could be valuable depending on context
- You want to respond to changing conditions as they emerge
- The best outcome isn’t known upfront

##### When to Use Utility AI

- **Event-driven systems**: React to incoming events (issues, stars, webhooks) with the most appropriate action
- **Chatbots**: Where the platform provides multiple response options and selects the best one
- **Exploration**: When you want to discover what’s possible rather than achieve a specific goal

##### Using Utility AI with @EmbabelComponent

For Utility AI, actions are typically provided via `@EmbabelComponent` rather than `@Agent`. This allows the *platform* to select actions across multiple components based on utility, rather than constraining actions to a single agent.

Here’s an example from the Shepherd project that reacts to GitHub events:

```java
@EmbabelComponent  (1)
public class IssueActions {

    private final ShepherdProperties properties;
    private final CommunityDataManager communityDataManager;
    private final GitHubUpdater gitHubUpdater;

    public IssueActions(ShepherdProperties properties,
                        CommunityDataManager communityDataManager,
                        GitHubUpdater gitHubUpdater) {
        this.properties = properties;
        this.communityDataManager = communityDataManager;
        this.gitHubUpdater = gitHubUpdater;
    }

    @Action(outputBinding = "ghIssue")  (2)
    public GHIssue saveNewIssue(GHIssue ghIssue, OperationContext context) {
        var existing = communityDataManager.findIssueByGithubId(ghIssue.getId());
        if (existing == null) {
            var issueEntityStatus = communityDataManager.saveAndExpandIssue(ghIssue);
            context.add(issueEntityStatus);  (3)
            return ghIssue;
        }
        return null;  (4)
    }

    @Action(
        pre = {"spel:newEntity.newEntities.?[#this instanceof T(com.embabel.shepherd.domain.Issue)].size() > 0"}  (5)
    )
    public IssueAssessment reactToNewIssue(GHIssue ghIssue, NewEntity<?> newEntity, Ai ai) {
        return ai
            .withLlm(properties.getTriageLlm())
            .creating(IssueAssessment.class)
            .fromTemplate("first_issue_response", Map.of("issue", ghIssue));  (6)
    }

    @Action(pre = {"spel:issueAssessment.urgency > 0.0"})  (7)
    public void heavyHitterIssue(GHIssue issue, IssueAssessment issueAssessment) {
        // Take action on high-urgency issues
    }
}
```

| **1** | `@EmbabelComponent` contributes actions to the platform, not a specific agent |
| --- | --- |
| **2** | `outputBinding` names the result for later actions to reference |
| **3** | Add entity status to context, making it available to subsequent actions |
| **4** | Returning `null` prevents further actions from firing for this issue |
| **5** | SpEL precondition: only fire if new issues were created |
| **6** | Use AI to assess the issue via a template |
| **7** | This action only fires if the assessment shows urgency > 0 |

The platform selects which action to run based on:

1. Which preconditions are satisfied (type availability + SpEL conditions)
2. The `cost` and `value` parameters on `@Action` (net value = value - cost)

##### Action Cost and Value

The `@Action` annotation supports `cost` and `value` parameters (both 0.0 to 1.0):

```java
@Action(
    cost = 0.1,   (1)
    value = 0.8   (2)
)
public Output highValueAction(Input input) {
    // Action implementation
}
```

| **1** | Cost to execute (0.0 to 1.0) - lower is cheaper |
| --- | --- |
| **2** | Value when executed (0.0 to 1.0) - higher is more valuable |

The Utility planner calculates *net value* as `value - cost` and selects the action with the highest net value from all available actions.

##### The Nirvana Goal

Utility AI supports a special "Nirvana" goal that is never satisfied. This keeps the process running, continuously selecting the highest-value available action until no actions are available.

##### Extensibility

Utility AI fosters extensibility. For example, multiple groups within an organization can contribute their own `@EmbabelComponent` classes with actions that bring their own expertise to enhance behaviours around shared types, while retaining the ability to own and control their own extended model.

##### Utility and States

Utility AI can combine with the `@State` annotation to implement classification and routing patterns. This is particularly useful when you need to:

- **Classify input** into different categories at runtime
- **Route processing** through category-specific handlers
- **Achieve different goals** based on classification

The key pattern is:

1. An entry action classifies input and returns a `@State` type
2. Each `@State` class contains an `@AchievesGoal` action that produces the final output
3. The `@AchievesGoal` output is *not* a `@State` type (to prevent infinite loops)

Here’s an example of a ticket triage system that routes support tickets based on severity:

```java
@Agent(
    description = "Triage and process support tickets",
    planner = PlannerType.UTILITY  (1)
)
public class TicketTriageAgent {

    public record Ticket(String id, String description, String customerId) {}
    public record ResolvedTicket(String id, String resolution, String handledBy) {}

    @State
    public sealed interface TicketCategory permits CriticalTicket, BugTicket, GeneralTicket {}  (2)

    @Action
    public TicketCategory triageTicket(Ticket ticket) {  (3)
        if (ticket.description().toLowerCase().contains("down")) {
            return new CriticalTicket(ticket);
        } else if (ticket.description().toLowerCase().contains("bug")) {
            return new BugTicket(ticket);
        } else {
            return new GeneralTicket(ticket);
        }
    }

    @State
    public record CriticalTicket(Ticket ticket) implements TicketCategory {
        @AchievesGoal(description = "Handle critical ticket with immediate escalation")  (4)
        @Action
        public ResolvedTicket handleCritical() {
            return new ResolvedTicket(
                ticket.id(),
                "Escalated to on-call engineer",
                "CRITICAL_RESPONSE_TEAM"
            );
        }
    }

    @State
    public record BugTicket(Ticket ticket) implements TicketCategory {
        @AchievesGoal(description = "Handle bug report")
        @Action
        public ResolvedTicket handleBug() {
            return new ResolvedTicket(
                ticket.id(),
                "Bug logged in issue tracker",
                "ENGINEERING_TEAM"
            );
        }
    }

    @State
    public record GeneralTicket(Ticket ticket) implements TicketCategory {
        @AchievesGoal(description = "Handle general inquiry")
        @Action
        public ResolvedTicket handleGeneral() {
            return new ResolvedTicket(
                ticket.id(),
                "Response sent with FAQ links",
                "SUPPORT_TEAM"
            );
        }
    }
}
```

| **1** | Use `PlannerType.UTILITY` for opportunistic action selection |
| --- | --- |
| **2** | Sealed interface as the state supertype |
| **3** | Entry action classifies and returns a `@State` instance |
| **4** | Each state has an `@AchievesGoal` action producing the final output |

When a `Ticket` is processed:

1. The `triageTicket` action classifies it into one of the state types
2. Entering a state clears other objects from the blackboard
3. The Utility planner selects the `@AchievesGoal` action for that state
4. The goal is achieved when `ResolvedTicket` is produced

This pattern works well when:

- Classification determines the processing path
- Each category has distinct handling requirements
- The final output type is the same across all categories

##### UtilityInvocation: Lightweight Utility Pattern

For simple utility workflows, you don’t need to create an `@Agent` class. `UtilityInvocation` provides a fluent API to run utility-based workflows directly from `@EmbabelComponent` actions.

Example 1. Invoking with UtilityInvocation

```java
UtilityInvocation.on(agentPlatform)
    .withScope(AgentScopeBuilder.fromInstances(issueActions, labelActions))
    .run(new GHIssue(issueData));
```

###### Configuration Options

`UtilityInvocation` supports several configuration methods:

| Method | Description |
| --- | --- |
| `.withScope(AgentScopeBuilder)` | Defines which actions are available |
| `.withAgentName(String)` | Sets a custom name for the created agent (defaults to platform name) |
| `.withProcessOptions(ProcessOptions)` | Configures process-level options |
| `.terminateWhenStuck()` | Adds early termination policy when no actions are available |

Example 2. Setting a custom agent name

```java
UtilityInvocation.on(agentPlatform)
    .withScope(AgentScopeBuilder.fromInstance(myActions))
    .withAgentName("issue-triage-agent")
    .run(input);
```

#### 3.19.2. Supervisor

The Supervisor planner uses an LLM to orchestrate actions dynamically. This is a popular pattern in frameworks like [LangGraph](https://langchain-ai.github.io/langgraph/concepts/agentic_concepts/#supervisor) and [Google ADK](https://google.github.io/adk-docs/agents/multi-agents/#supervisor-agent-sample), where a supervisor LLM decides which tools to call and in what order.

|  | Unlike GOAP and Utility, the Supervisor planner is **non-deterministic**. The LLM may choose different action sequences for the same inputs. This makes it less suitable for business-critical workflows requiring reproducibility. |
| --- | --- |

##### Type-Informed vs Type-Driven

A key design decision in supervisor architectures is how types relate to composition:

| Approach | Description |
| --- | --- |
| **Type-Driven** (GOAP) | Types *constrain* composition. An action requiring `MarketData` can only run after an action produces `MarketData`. This is deterministic but rigid. |
| **Type-Informed** (Supervisor) | Types *inform* composition. The LLM sees type schemas and decides what to call based on semantic understanding. This is flexible but non-deterministic. |

Embabel’s Supervisor planner takes the **type-informed** approach while maximizing the benefits of types:

- Actions return **typed outputs** that are validated
- The LLM sees **type schemas** to understand what each action produces
- Results are stored on the **typed blackboard** for later actions
- The same actions work with **any planner** (GOAP, Utility, or Supervisor)

This is a "typed supervisor" pattern—a middle ground between fully type-driven (GOAP) and untyped string-passing (typical LangGraph).

##### When to Use Supervisor

Supervisor is appropriate when:

- Action ordering is **context-dependent** and hard to predefine
- You want an LLM to **synthesize information** across multiple sources
- The workflow benefits from **flexible composition** rather than strict sequencing
- Non-determinism is acceptable for your use case

- You need **reproducible**, auditable execution paths
- Actions have strict **dependency ordering** that must be enforced
- Latency and cost matter (each decision requires an LLM call)

##### Using Supervisor

To use Supervisor, annotate your agent with `planner = PlannerType.SUPERVISOR` and mark one action with `@AchievesGoal`:

```java
@Agent(
    planner = PlannerType.SUPERVISOR,
    description = "Market research report generator"
)
public class MarketResearchAgent {

    public record MarketDataRequest(String topic) {}
    public record MarketData(Map<String, String> revenues, Map<String, Double> marketShare) {}

    public record CompetitorAnalysisRequest(List<String> companies) {}
    public record CompetitorAnalysis(Map<String, List<String>> strengths) {}

    public record ReportRequest(String topic, List<String> companies) {}
    public record FinalReport(String title, List<String> sections) {}

    @Action(description = "Gather market data including revenues and market share")  (1)
    public MarketData gatherMarketData(MarketDataRequest request, Ai ai) {
        return ai.withDefaultLlm().createObject(
            "Generate market data for: " + request.topic(),
            MarketData.class
        );
    }

    @Action(description = "Analyze competitors: strengths and positioning")
    public CompetitorAnalysis analyzeCompetitors(CompetitorAnalysisRequest request, Ai ai) {
        return ai.withDefaultLlm().createObject(
            "Analyze competitors: " + String.join(", ", request.companies()),
            CompetitorAnalysis.class
        );
    }

    @AchievesGoal(description = "Compile all information into a final report")  (2)
    @Action(description = "Compile the final report")
    public FinalReport compileReport(ReportRequest request, Ai ai) {
        return ai.withDefaultLlm().createObject(
            "Create a market research report for " + request.topic(),
            FinalReport.class
        );
    }
}
```

| **1** | Tool actions have descriptions visible to the supervisor LLM |
| --- | --- |
| **2** | The goal action is called when the supervisor has gathered enough information |

The supervisor LLM sees type schemas for available actions:

```
Available actions:
- gatherMarketData(request: MarketDataRequest) -> MarketData
    Schema: { revenues: Map, marketShare: Map }
- analyzeCompetitors(request: CompetitorAnalysisRequest) -> CompetitorAnalysis
    Schema: { strengths: Map }

Current artifacts on blackboard:
- MarketData: { revenues: {"CompanyA": "$10B"}, marketShare: {...} }

Goal: FinalReport
```

The LLM decides action ordering based on this information, making informed decisions without being constrained by declared dependencies.

##### Interoperability

Using wrapper request types (like `MarketDataRequest`) enables actions to work with **any planner**:

- **GOAP**: Request types flow through the blackboard based on preconditions/effects
- **Utility**: Actions fire when their request types are available with highest net value
- **Supervisor**: The LLM constructs request objects to call actions

This means you can switch planners without changing your action code—useful for testing with deterministic planners (GOAP) and deploying with flexible planners (Supervisor).

##### Comparison with LangGraph

[LangGraph’s supervisor pattern](https://github.com/langchain-ai/langgraph-supervisor-py) is a popular approach for multi-agent orchestration. Here’s how a similar workflow looks in LangGraph vs Embabel:

```python
from langgraph_supervisor import create_supervisor
from langgraph.prebuilt import create_react_agent

# Tools return strings - no type information
def gather_market_data(topic: str) -> str:
    """Gather market data for a topic."""
    return f"Revenue data for {topic}..."  (1)

def analyze_competitors(companies: str) -> str:
    """Analyze competitors."""
    return f"Analysis of {companies}..."  (1)

# Create agents with tools
research_agent = create_react_agent(
    model="openai:gpt-4o",
    tools=[gather_market_data, analyze_competitors],
    name="research_expert",
)

# Supervisor sees all tools, always  (2)
workflow = create_supervisor([research_agent], model=model)
app = workflow.compile()

# State is a dict of messages  (3)
result = app.invoke({"messages": [{"role": "user", "content": "Research cloud market"}]})
```

| **1** | Tools return strings—the LLM must parse and interpret results |
| --- | --- |
| **2** | All tools always visible—no filtering based on context |
| **3** | State is untyped message history |

Example 3. Embabel

```java
@Agent(planner = PlannerType.SUPERVISOR)
public class MarketResearchAgent {

    // Tools return typed objects with schemas  (1)
    @Action(description = "Gather market data for a topic")
    public MarketData gatherMarketData(MarketDataRequest request, Ai ai) {
        return ai.withDefaultLlm().createObject(
            "Generate market data for " + request.topic(), MarketData.class);
    }

    @Action(description = "Analyze competitors")
    public CompetitorAnalysis analyzeCompetitors(CompetitorAnalysisRequest request, Ai ai) {
        return ai.withDefaultLlm().createObject(
            "Analyze " + request.companies(), CompetitorAnalysis.class);
    }

    @AchievesGoal
    @Action
    public FinalReport compileReport(ReportRequest request, Ai ai) { ... }
}

// State is a typed blackboard  (2)
// Tools are filtered based on available inputs  (3)
```

| **1** | Tools return typed, validated objects-- `MarketData`, `CompetitorAnalysis` |
| --- | --- |
| **2** | Blackboard holds typed artifacts, not just message strings |
| **3** | Tools with satisfied inputs are prioritized via currying |

##### Key Advantages

Embabel’s Supervisor offers several advantages over typical supervisor implementations:

| Aspect | Typical Supervisor (LangGraph) | Embabel Supervisor |
| --- | --- | --- |
| **Output Types** | Strings—LLM must parse | Typed objects—validated and structured |
| **Tool Visibility** | All tools always available | Tools filtered by blackboard state (currying) |
| **Domain Awareness** | None—tools are opaque functions | Type schemas visible to LLM |
| **Determinism** | Fully non-deterministic | Semi-deterministic: tool availability constrained by types |
| **State** | Untyped message history | Typed blackboard with named artifacts |

###### Blackboard-Driven Tool Filtering

A key differentiator is **curried tool filtering**. When an action’s inputs are already on the blackboard, those parameters are "curried out"--the tool signature simplifies.

|  | What is Currying?  [Currying](https://en.wikipedia.org/wiki/Currying) is a functional programming technique where a function with multiple parameters is transformed into a sequence of functions, each taking a single parameter.  In Embabel’s context: if an action requires `(MarketDataRequest, Ai)` and `MarketDataRequest` is already on the blackboard, we "curry out" that parameter—the tool exposed to the LLM only needs to provide any remaining parameters. This simplifies the LLM’s task and signals which tools are "ready" to run. |
| --- | --- |

```
# Initial state: empty blackboard
Available tools:
- gatherMarketData(request: MarketDataRequest) -> MarketData
- analyzeCompetitors(request: CompetitorAnalysisRequest) -> CompetitorAnalysis

# After MarketData is gathered:
Available tools:
- gatherMarketData(request: MarketDataRequest) -> MarketData  [READY - 0 params needed]
- analyzeCompetitors(request: CompetitorAnalysisRequest) -> CompetitorAnalysis
```

This reduces the LLM’s decision space and guides it toward logical next steps—tools with satisfied inputs appear "ready" with fewer parameters. This is more deterministic than showing all tools equally, while remaining more flexible than GOAP’s strict ordering.

###### Semi-Determinism

While still LLM-orchestrated, Embabel’s Supervisor is **more deterministic** than typical implementations:

1. **Type constraints**: Actions can only produce specific types—no arbitrary string outputs
2. **Input filtering**: Tools unavailable until their input types exist
3. **Schema guidance**: LLM sees what each action produces, not just descriptions
4. **Validated outputs**: Results must conform to declared types

This makes debugging easier and behaviour more predictable, while retaining the flexibility that makes supervisor patterns valuable.

###### When Embabel’s Approach Excels

- **Domain-rich workflows**: When your domain has clear types (reports, analyses, forecasts), schemas help the LLM understand relationships
- **Multi-step synthesis**: When actions build on each other’s outputs, typed blackboard tracks progress clearly
- **Hybrid determinism**: When you want more predictability than pure LLM orchestration but more flexibility than GOAP

##### SupervisorInvocation: Lightweight Supervisor Pattern

For simple supervisor workflows, you don’t need to create an `@Agent` class. `SupervisorInvocation` provides a fluent API to run supervisor-orchestrated workflows directly from `@EmbabelComponent` actions.

This is ideal when:

- You have a small set of related actions in an `@EmbabelComponent`
- You want LLM-orchestrated composition without creating a full agent
- You’re prototyping or exploring supervisor patterns before committing to a full agent design

###### Example: Meal Preparation Workflow

Here’s a complete example from the [embabel-agent-examples](https://github.com/embabel/embabel-agent-examples) repository:

Example 4. Stages - Actions as @EmbabelComponent

```java
@EmbabelComponent
public class Stages {

    public record Cook(String name, int age) {}

    public record Order(String dish, int quantity) {}

    public record Meal(String dish, int quantity, String orderedBy, String cookedBy) {}

    @Action
    public Cook chooseCook(UserInput userInput, Ai ai) {
        return ai.withAutoLlm().createObject(
                """
                From the following user input, choose a cook.
                User input: %s
                """.formatted(userInput),
                Cook.class
        );
    }

    @Action
    public Order takeOrder(UserInput userInput, Ai ai) {
        return ai.withAutoLlm().createObject(
                """
                From the following user input, take a food order
                User input: %s
                """.formatted(userInput),
                Order.class
        );
    }

    @Action
    @AchievesGoal(description = "Cook the meal according to the order")
    public Meal prepareMeal(Cook cook, Order order, UserInput userInput, Ai ai) {
        // The model will get the orderedBy from UserInput
        return ai.withAutoLlm().createObject(
                """
                Prepare a meal based on the cook and order details and target customer
                Cook: %s, age %d
                Order: %d x %s
                User input: %s
                """.formatted(cook.name(), cook.age(), order.quantity(), order.dish(), userInput.getContent()),
                Meal.class
        );
    }
}
```

Example 5. Invoking with SupervisorInvocation

```java
Stages stages = new Stages();

Meal meal = SupervisorInvocation.on(agentPlatform)
    .returning(Stages.Meal.class)
    .withScope(AgentScopeBuilder.fromInstance(stages))
    .invoke(new UserInput(request));
```

###### Configuration Options

`SupervisorInvocation` supports several configuration methods:

| Method | Description |
| --- | --- |
| `.returning(Class)` | Specifies the goal type to produce |
| `.withScope(AgentScopeBuilder)` | Defines which actions are available |
| `.withAgentName(String)` | Sets a custom name for the created agent (defaults to `{platformName}.supervisor`) |
| `.withGoalDescription(String)` | Provides a custom description for the goal |
| `.withProcessOptions(ProcessOptions)` | Configures process-level options |

Example 6. Setting a custom agent name

```java
SupervisorInvocation.on(agentPlatform)
    .returning(Report.class)
    .withScope(AgentScopeBuilder.fromInstance(actions))
    .withAgentName("market-research-supervisor")
    .invoke(request);
```

The supervisor LLM sees:

1. **Available actions** with their type signatures and schemas
2. **Current artifacts** on the blackboard (including `UserInput` content)
3. **Goal** to produce a `Meal`

It then orchestrates the actions—calling `chooseCook` and `takeOrder` (possibly in parallel), then `prepareMeal` when the dependencies are satisfied.

###### Key Design Points

1. **Actions use UserInput explicitly**: Each action receives `UserInput` and includes it in the LLM prompt, ensuring the actual user request is used.
2. **@AchievesGoal marks the target**: The `prepareMeal` action is marked with `@AchievesGoal` to indicate it produces the final output.
3. **Type-driven dependencies**: `prepareMeal` requires `Cook` and `Order`, which guides the supervisor’s orchestration.

###### SupervisorInvocation vs @Agent with planner = SUPERVISOR

| Aspect | SupervisorInvocation | @Agent(planner = SUPERVISOR) |
| --- | --- | --- |
| **Declaration** | Fluent API, no class annotation | Annotated agent class |
| **Action source** | `@EmbabelComponent` or multiple components | Single `@Agent` class |
| **Best for** | Quick prototypes, simple workflows | Formalized, reusable agents |
| **Goal specification** | `.returning(Class)` fluent method | `@AchievesGoal` on action |
| **Scope** | Explicit via `AgentScopeBuilder` | Implicit from agent class |

###### Comparison with AgenticTool

Both `SupervisorInvocation` and [AgenticTool](https://docs.embabel.com/embabel-agent/guide/0.3.4-SNAPSHOT/#reference.tools__agentic-tools) provide LLM-orchestrated composition, but at different levels:

| Aspect | AgenticTool | SupervisorInvocation |
| --- | --- | --- |
| **Level** | Tool (can be used within actions) | Invocation (runs a complete workflow) |
| **Sub-components** | Other `Tool` instances | `@Action` methods from `@EmbabelComponent` |
| **Output** | `Tool.Result` (text, artifact, or error) | Typed goal object (e.g., `Meal`) |
| **State management** | Minimal (LLM conversation only) | Full blackboard with typed artifacts |
| **Type awareness** | Tools have names and descriptions | Actions have typed inputs/outputs with schemas |
| **Currying** | None | Inputs on blackboard are curried out |
| **Use case** | Mini-orchestration within an action | Complete multi-step workflow with typed results |

Use `AgenticTool` when you need a tool that internally orchestrates other tools. Use `SupervisorInvocation` when you need a complete workflow that produces a typed result with full blackboard state management.

![embabel planning system](https://docs.embabel.com/embabel-agent/guide/0.3.4-SNAPSHOT/images/embabel_planning_system.dot.png)

### 3.20. API vs SPI

Embabel makes a clean distinction between its API and SPI. The API is the public interface that users interact with, while the SPI (Service Provider Interface) is intended for developers who want to extend or customize the behavior of Embabel, or platform providers.

|  | Application code should only depend on the API (com.embabel.agent.api.\*) not the SPI. The SPI is subject to change and should not be used in production code. |
| --- | --- |

### 3.21. Embabel and Spring

Embabel embraces [Spring](https://spring.io/projects/spring-framework). Spring was revolutionary when it arrived, and two decades on it still defines how most JVM applications are built. You may already know Spring from years of Java or Kotlin development. Or perhaps you’re arriving from Python or another ecosystem. In any case it’s worth noting that Embabel was spearheaded by the creator of Spring himself: the noteworthy Rod Johnson.

Embabel has been assembled using the Spring core platform and then builds upon the [Spring AI](https://spring.io/projects/spring-ai) portfolio project.

We recommend using [Spring Boot](https://spring.io/projects/spring-boot) for building Embabel applications. Not only does it provide a familiar environment for JVM developers, its philosophy is highly relevant for anyone aiming to craft a production-grade agentic AI application.

Why? Because the foundation of the Spring framework is:

- Composability via discreet, fit-for-purpose reusable units. Dependency injection facilitates this.
- Cross-cutting abstractions — such as transaction management and security. Aspect-oriented programming (AOP) is what makes this work.

This same foundation makes it possible to craft agentic applications that are composable, testable, and built on enterprise-grade service abstractions. With ~70% of production applications deployed on the JVM, Embabel can bring AI super-powers to existing systems — extending their value rather than replacing them. In this way, Embabel applies the Spring philosophy so that agentic applications are not just clever demos, but truly production-ready systems.

### 3.22. Working with LLMs

Embabel supports any LLM supported by Spring AI. In practice, this is just about any LLM.

#### 3.22.1. Choosing an LLM

Embabel encourages you to think about LLM choice for every LLM invocation. The `PromptRunner` interface makes this easy. Because Embabel enables you to break agentic flows up into multiple action steps, each step can use a smaller, focused prompt with fewer tools. This means it may be able to use a smaller LLM.

Considerations:

- **Consider the complexity of the return type you expect** from the LLM. This is typically a good proxy for determining required LLM quality. A small LLM is likely to struggle with a deeply nested return structure.
- **Consider the nature of the task.** LLMs have different strengths; review any available documentation. You don’t necessarily need a huge, expensive model that is good at nearly everything, at the cost of your wallet and the environment.
- **Consider the sophistication of tool calling required**. Simple tool calls are fine, but complex orchestration is another indicator you’ll need a strong LLM. (It may also be an indication that you should create a more sophisticated flow using Embabel GOAP.)
- **Consider trying a local LLM** running under Ollama or Docker.

|  | Trial and error is your friend. Embabel makes it easy to switch LLMs; try the cheapest thing that could work and switch if it doesn’t. |
| --- | --- |

#### 3.22.2. Advanced: Custom LLM Integration

Embabel’s tool loop is framework-agnostic, allowing you to integrate any LLM provider by implementing the `LlmMessageSender` interface. This is useful when:

- You want to use an LLM provider not supported by Spring AI
- You need custom request/response handling
- You’re integrating with a proprietary or internal LLM service

##### The LlmMessageSender Interface

The core abstraction is the `LlmMessageSender` functional interface:

```java
@FunctionalInterface
public interface LlmMessageSender {
    LlmMessageResponse call(
        List<Message> messages,
        List<Tool> tools
    );
}
```

The implementation makes a single LLM inference call and returns the response. Importantly, it does **not** execute tools—it only returns any tool call requests from the LLM. Tool execution is handled by Embabel’s `DefaultToolLoop`.

```java
public record ToolCall(
    String id,         // Unique identifier for the tool call
    String name,       // Name of the tool to invoke
    String arguments   // JSON arguments for the tool
) {}
```
