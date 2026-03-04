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
- **The Blackboard**: A shared memory for the process. Every object returned by an `@Action` is automatically placed on the Blackboard.
- **UserInput**: The starting point. It is automatically placed on the Blackboard when an agent is invoked via a user message.
- **AchievesGoal**: Every agent needs at least one action marked with `@AchievesGoal`. This tells the planner what the "winning condition" is.

## 1.3 OperationContext & Ai API
The `Ai` interface (via `ctx.ai()`) is the primary way to call LLMs:
- `.createObject(prompt, Class)`: For structured JSON output mapped to a POJO.
- `.generateText(prompt)`: For raw string responses.
- `.withLlm(LlmOptions)`: To tune hyperparameters like temperature or select a specific model.
- `.withLlmByRole("reviewer")`: To use a model configured for a specific role in `application.yml`.

# Chapter 2: Domain Engineering & DICE (Domain-Integrated Context Engineering)

Embabel grounds LLM interactions in strongly-typed domain objects. This approach, **DICE**, ensures precision and reliability by giving LLMs "hands" (tools) within your domain model.

## 2.1 The @Tool Pattern on Domain Objects
Unlike simple DTOs, domain objects should encapsulate logic that LLMs can invoke. This keeps the LLM grounded in your system and prevents hallucinated business logic.

### Domain Object with @Tool Implementation
```java
@Entity
public class CustomerProfile {
    private String id;
    private int loyaltyLevel;
    private List<Order> orderHistory;

    @Tool(description = "Check if the customer is eligible for a VIP upgrade")
    public boolean isConciergeEligible() {
        return loyaltyLevel >= 5 && orderHistory.size() > 10;
    }

    @Tool(description = "Calculate special discount for this customer")
    public double calculateDiscount() {
        return loyaltyLevel * 0.05; // 5% per level
    }
}
```

## 2.2 Using Domain Objects in Actions
To make a domain object's tools available to an LLM, use the `withToolObject` method on the `Ai` interface.

### Action with Tool Injection Sample
```java
@Action
public SupportAdvice handleSupport(UserInput input, CustomerProfile customer, OperationContext ctx) {
    String prompt = "Assist the customer with their request: " + input.getContent();

    return ctx.ai()
            .withLlmByRole("support") // Select specific model for support
            .withToolObject(customer) // The LLM can now call getLoyaltyDiscount() and isConciergeEligible()
            .createObject(prompt, SupportAdvice.class);
}
```

## 2.3 Key Mechanics for DICE
- **Selective Exposure**: Only methods annotated with `@Tool` are visible to the LLM. Unannotated methods remain hidden for safety.
- **Type Safety**: The return types of `@Tool` methods are automatically handled by the framework.
- **Context Availability**: When an `@Action` method is called, its parameters (like `CustomerProfile`) are retrieved from the Blackboard. If the object exists, the planner can use it.
- **withToolGroup**: For common external tools (web search, browser, etc.), use predefined groups:
  ```java
  ctx.ai().withToolGroup(CoreToolGroups.WEB).generateText("Search for...");
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
**LLM Instruction**: Always prioritize using `@Tool` methods on domain objects for complex calculations or data fetching rather than asking the LLM to "figure it out" from raw data.

# Chapter 3: Advanced Planning & States

Embabel supports complex workflows through different planners and state-based management.

## 3.1 Utility AI (Event-Driven / Greedy)
Best for cases where no fixed goal exists, but you want to react to state changes with the "most valuable" action. Useful for triage or monitoring.

### Utility AI Agent Sample
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

### Supervisor Agent Sample
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
For complex stages (e.g., Revise-and-Review). When an action returns a `@State` object, the blackboard hides previous state objects to focus the agent on actions defined within that state. This effectively **prunes context tokens** by hiding irrelevant data from previous states.

### State-Based Agent Sample
```java
@Agent(description = "Draft and review a document")
public class DocumentAgent {

    @Action // Initial entry point
    public DraftStage startDraft(UserInput input) {
        return new DraftStage(input.getContent());
    }

    @State // Annotation on the class/record, not a field
    record DraftStage(String content) {
        @Action // Action only visible when in DraftStage
        public ReviewStage submitForReview(DraftStage stage, Ai ai) {
            var review = ai.generateText("Review this: " + stage.content());
            return new ReviewStage(stage.content(), review);
        }
    }

    @State
    record ReviewStage(String content, String reviewText) {
        @AchievesGoal
        @Action
        public FinalDoc finalize(ReviewStage stage) {
            return new FinalDoc(stage.content());
        }

        @Action(clearBlackboard = true) // Loop back to drafting by clearing state
        public DraftStage revise(ReviewStage stage) {
            return new DraftStage(stage.content() + " [Revised]");
        }
    }
}
```

## 3.4 Human-in-the-Loop (WaitFor)
Pause an agent's execution to wait for user input.
```java
@Action
public WaitFor<UserFeedback> askForFeedback(Story story) {
    return WaitFor.formSubmission("Please review the story: " + story.text(), UserFeedback.class);
}
```
**LLM Instruction**: When `WaitFor` is returned, the process state changes to `WAITING`. It resumes only after the specified input is provided.

# Chapter 4: RAG & Conversations (Multi-Turn)

Embabel’s RAG and Chat architectures are designed to minimize token usage by treating context as manageable **Assets**, **References**, and **States**.

## 4.1 Agentic RAG (Search as a Tool, ToolishRag)
Embabel RAG is **Agentic** and **Tool-based**. Instead of simply prepending documents to a prompt (Stateless RAG), it treats search as a set of tools (`vectorSearch`, `textSearch`, etc.) that the LLM can invoke as needed. This prevents overwhelming the model with irrelevant context.

### ToolishRag Implementation Example
`ToolishRag` acts as a facade that exposes search operations as LLM tools.

```java
@Agent(description = "Researches and answers questions from technical documentation")
public class DocAgent {
    private final SearchOperations searchOps;

    public DocAgent(SearchOperations searchOps) {
        this.searchOps = searchOps;
    }

    @AchievesGoal(description = "Answer the user question from documentation")
    @Action
    public Answer answerQuestion(UserInput input, OperationContext ctx) {
        // ToolishRag acts as an LLM Reference (a bundle of search tools)
        var docs = new ToolishRag("docs", "Technical documentation", searchOps);

        return ctx.ai()
            .withLlmByRole("researcher")
            .withReference(docs) // The LLM can now call vectorSearch and textSearch only when needed.
            .createObject("Answer using our docs: " + input.getContent(), Answer.class);
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
A chatbot in Embabel is a long-lived `AgentProcess` that manages multi-turn context by separating **Message History** from **Blackboard State**.

- **Message Triggers**: Use `@Action(trigger = UserMessage.class)` to define actions that fire whenever a new user message arrives.
- **Conversation**: Holds the `Message` history via `addMessage`.
- **Blackboard Hydration**: When resuming a session, Embabel restores structured objects (POJOs) to the blackboard. This is much more token-efficient than re-sending raw text history, as structured data carries higher information density.

### Chatbot Architecture Sample
```java
@EmbabelComponent
public class SupportChatActions {

    // Message trigger: Fires when the process receives a UserMessage
    @Action(trigger = UserMessage.class)
    public void onUserMessage(UserMessage msg, Conversation conversation, OperationContext ctx) {
        // Conversation holds the message history.
        var response = ctx.ai()
                .rendering("support-persona") // Load prompt from Jinja template
                .respond(conversation.getMessages());

        conversation.addMessage(response);
    }
}
```

## 4.4 Asset Tracking & Asset-as-a-Tool
**Assets** are structured outputs (documents, reports, POJOs) generated during a session. Embabel saves tokens by allowing these assets to be re-used as **Tools** (LLM References) in subsequent turns.

- **AssetTracker**: Maintains the list of artifacts generated in the conversation.
- **Asset-as-a-Tool**: Use `conversation.mostRecent().references()` to expose previous turn results as searchable tools for the current turn.

```java
@Action(trigger = UserMessage.class)
public void respondWithAssets(Conversation conv, OperationContext ctx) {
    // Re-use assets from the last 5 turns as tools
    List<LlmReference> assetRefs = conv.mostRecent(5).references();

    var response = ctx.ai()
            .withReferences(assetRefs) // LLM can now "query" previous turn results
            .respond(conv.getMessages());

    conv.addMessage(response);
}
```

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

### Key Advantages
- **Hybrid Search**: Combines semantic vector similarity with traditional full-text search (PostgreSQL `tsvector`) and fuzzy matching (`pg_trgm`).
- **High Availability**: Leverages mature PostgreSQL HA solutions (e.g., Patroni, Repmgr) to ensure continuous operation.
- **Transactional Integrity**: Ensures that document updates and metadata changes are ACID-compliant.

### Implementation with Gemini Embeddings
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

**Operational Tip**: Multiple agent server nodes can connect to the same PostgreSQL cluster, allowing you to scale your AI services horizontally while maintaining a single, consistent knowledge source.

## 4.7 Document Ingestion Pipeline
To populate your PostgreSQL store with data, you need an ingestion pipeline. Embabel provides a unified ingestion mechanism that handles the heavy lifting: parsing, chunking, embedding, and storage.

### Ingestion Service Implementation
This service demonstrates how to take a raw file (like a PDF) and store it in your enterprise RAG database using **Google Gemini** for embeddings.

```java
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

### Key Components of Ingestion
- **TikaHierarchicalContentReader**: Part of the `embabel-agent-rag-tika` module. It extracts structured content and metadata from raw files.
- **Chunking Strategy**: Configured via `ChunkerConfig`. It determines how text is split (e.g., semantic boundaries or fixed size with overlap).
- **Ingest Policy**: `NeverRefreshExistingDocumentContentPolicy` ensures documents are not re-ingested if already present.

**Operational Tip**: For bulk updates or very large documents, use `batchIngest()` on the store to optimize database writes and manage embedding provider rate limits effectively.

## 4.8 Portable RAG for CLI Tools (Lucene & Gemini)
For personal CLI tools or lightweight projects, **Lucene** is the ideal storage engine because it requires zero infrastructure. This setup allows your agent to carry its knowledge base in a local folder, using **Google Gemini** for high-quality embeddings.

### Configuration for Local Storage
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

### Agent Implementation
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

### Why this is best for CLI:
- **Portability**: Knowledge is stored in `./.embabel-index`. Move the project folder, and the index moves with it.
- **Zero Setup**: No Docker or external database is required. It runs wherever Java runs.
- **Hybrid Performance**: Lucene handles both semantic meaning (via Gemini) and exact keyword matches with extremely low latency.

**Operational Tip**: Add `.embabel-index/` to your `.gitignore` to keep binary index files out of source control.

**LLM Instruction**: In RAG mode, always prioritize `vectorSearch` for semantic queries and `textSearch` for keyword/exact matching.

# Chapter 5: Observability & Testing

Embabel's design ensures that agents are **composable, testable, and observable**.

## 5.1 Observability: Tracing Agent Lifecycle
Automatic OpenTelemetry tracing of actions, LLM calls, and planning iterations.

### Custom Operation Tracking (@Tracked)
Add observability spans to your own methods. Inputs, outputs, duration, and errors are captured automatically.
```java
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
**LLM Instruction**: Use `@Tracked` for any business logic or external API calls inside an `@Action` method.

## 5.2 Unit Testing: Predictable & Cost-Effective
Test individual agent actions without real LLM calls using `FakeOperationContext` and `FakePromptRunner`.

### Unit Test Pattern Sample
```java
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

### Integration Test Pattern Sample
```java
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
- **FakePromptRunner**: Fully supports fluent API patterns like `withId()` and `creating()`.
- **MDC Propagation**: `run_id` and `action_name` are automatically added to logs.
- **withExample**: Test actions that use structured examples for few-shot prompting.
- **verifyNoMoreInteractions**: Use in integration tests to ensure the LLM was not called unexpectedly.
