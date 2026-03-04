package io.autocrypt.jwlee.cowork.config;

import com.embabel.chat.Chatbot;
import com.embabel.agent.core.AgentPlatform;
import com.embabel.chat.agent.AgentProcessChatbot;
import com.embabel.agent.tools.mcp.McpToolFactory;
import com.embabel.agent.spi.support.springai.SpringAiMcpToolFactory;
import com.embabel.agent.api.tool.Tool;
import io.modelcontextprotocol.client.McpSyncClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.util.List;

@Configuration
public class ChatConfiguration {

    @Bean
    public Chatbot chatbot(AgentPlatform agentPlatform) {
        // Discovery-based chatbot that finds all @Action(trigger = UserMessage.class)
        return AgentProcessChatbot.utilityFromPlatform(agentPlatform);
    }

    @Bean
    public McpToolFactory mcpToolFactory(List<McpSyncClient> clients) {
        return new SpringAiMcpToolFactory(clients);
    }

    @Bean(name = "excalidrawTool")
    public Tool excalidrawTool(McpToolFactory mcpToolFactory) {
        // matryoshka는 도구들을 '러시아 인형'처럼 묶어서 에이전트가 호출 시 내부 도구들을 즉시 펼쳐볼 수 있게 합니다.
        return mcpToolFactory.matryoshka(
            "excalidraw",
            "Excalidraw drawing tools. You MUST invoke this tool to access specific drawing functions like create_element.",
            callback -> true
        );
    }
}
