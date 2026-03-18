package io.autocrypt.jwlee.cowork.agents.sample;

import com.embabel.agent.api.annotation.Action;
import com.embabel.agent.api.annotation.EmbabelComponent;
import com.embabel.chat.Conversation;
import com.embabel.agent.api.common.ActionContext;
import com.embabel.chat.UserMessage;
import com.embabel.agent.api.tool.Tool;
import org.springframework.beans.factory.annotation.Qualifier;

@EmbabelComponent
public class ExcalidrawChatAgent {

    private final Tool excalidrawTool;

    public ExcalidrawChatAgent(@Qualifier("excalidrawTool") Tool excalidrawTool) {
        this.excalidrawTool = excalidrawTool;
    }

    @Action(canRerun = true, trigger = UserMessage.class)
    public void respond(Conversation conversation, ActionContext context) {
        var assistantMessage = context.ai()
                .withLlmByRole("normal")
                .withTool(excalidrawTool) // Use the injected MCP unfolding tool
                .withSystemPrompt("""
                        You are a professional diagram architect using Excalidraw.
                        
                        CORE WORKFLOW:
                        1. First, call the 'excalidraw' tool to enable drawing capabilities.
                        2. Once drawing is enabled, use 'batch_create_elements' to create multiple elements (like a cat) in a SINGLE tool call.
                        3. Avoid making many individual 'create_element' calls; always prefer 'batch_create_elements' for complex shapes.
                        
                        DRAWING GUIDELINES:
                        - For a cat: Use ellipses for head/body, triangles for ears, and lines for whiskers/tail.
                        - Use relative coordinates so the parts fit together.
                        
                        IMPORTANT:
                        - If you cannot find a specific tool, check the list revealed after calling 'excalidraw'.
                        - ALWAYS RESPOND IN THE USER'S LANGUAGE.
                        """)
                .respond(conversation.getMessages());

        context.sendMessage(conversation.addMessage(assistantMessage));
    }
}
