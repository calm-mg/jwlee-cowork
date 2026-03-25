package io.autocrypt.jwlee.cowork.agents.chatbot;

import com.embabel.agent.api.annotation.Action;
import com.embabel.agent.api.annotation.Agent;
import com.embabel.agent.api.annotation.LlmTool;
import com.embabel.agent.api.common.ActionContext;
import com.embabel.agent.api.common.Ai;
import com.embabel.agent.prompt.persona.RoleGoalBackstory;
import com.embabel.agent.rag.tools.ToolishRag;
import com.embabel.agent.rag.tools.TryHyDE;
import io.autocrypt.jwlee.cowork.core.workaround.JsonSafeToolishRag;
import com.embabel.chat.Conversation;
import com.embabel.chat.Message;
import com.embabel.chat.UserMessage;
import io.autocrypt.jwlee.cowork.core.tools.CoreFileTools;
import io.autocrypt.jwlee.cowork.core.tools.LocalRagTools;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;

@Agent(description = "Main Orchestrator Agent for general chat and task coordination")
@Component
public class ChatbotAgent {

    private final RoleGoalBackstory mainOrchestratorPersona;
    private final CoreFileTools coreFileTools;
    private final LocalRagTools localRagTools;

    public ChatbotAgent(RoleGoalBackstory mainOrchestratorPersona, 
                        CoreFileTools coreFileTools,
                        LocalRagTools localRagTools) {
        this.mainOrchestratorPersona = mainOrchestratorPersona;
        this.coreFileTools = coreFileTools;
        this.localRagTools = localRagTools;
    }

    /**
     * Wrapper tool that fixes the RAG name to 'chatbot' and returns JSON.
     */
    public class ChatbotRagWrapper {
        @LlmTool(description = "Ingest a URL or file into the chatbot's in-memory knowledge base. ONLY use this when the user explicitly says 'Add RAG' or 'RAG 추가해'.")
        public String ingest(String location) throws IOException {
            String result = localRagTools.ingestUrlToMemory(location, "chatbot");
            return "{\"result\": \"" + result.replace("\"", "'") + "\"}";
        }

        @LlmTool(description = "Ingest a directory of files into the chatbot's in-memory knowledge base. ONLY use this when the user explicitly says 'Add RAG' or 'RAG 추가해'.")
        public String ingestDirectory(String directoryPath) throws IOException {
            String result = localRagTools.ingestDirectoryToMemory(directoryPath, "chatbot");
            return "{\"result\": \"" + result.replace("\"", "'") + "\"}";
        }
    }

    @Action(canRerun = true, trigger = UserMessage.class)
    public void chat(Conversation conversation, ActionContext context, Ai ai) throws IOException {
        List<Message> messages = conversation.getMessages();
        List<Message> contextMessages = messages.size() > 10 
                ? messages.subList(messages.size() - 10, messages.size()) 
                : messages;

        // Use managed in-memory instance from LocalRagTools
        var searchOps = localRagTools.getOrOpenMemoryInstance("chatbot");
        
        // Match name 'knowledge' as instructed in application.yml persona
        var toolishRag = new JsonSafeToolishRag("knowledge", "General knowledge base containing uploaded documents and URLs", searchOps)
                .withHint(TryHyDE.usingConversationContext());
        
        var response = ai.withLlmByRole("simple")
                .withPromptContributor(mainOrchestratorPersona)
                .withToolObject(coreFileTools)
                .withToolObject(new ChatbotRagWrapper())
                .withReference(toolishRag)
                .respond(contextMessages);

        context.sendMessage(conversation.addMessage(response));
    }
}
