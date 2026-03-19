package io.autocrypt.jwlee.cowork.agents.chatbot;

import com.embabel.agent.prompt.persona.RoleGoalBackstory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Configuration
@EnableConfigurationProperties(ChatbotConfig.OrchestratorProps.class)
public class ChatbotConfig {

    private static final Logger log = LoggerFactory.getLogger(ChatbotConfig.class);

    @ConfigurationProperties("embabel.identities.chatbot.orchestrator")
    public record OrchestratorProps(String role, String goal, String backstory) {}

    @Bean
    public RoleGoalBackstory mainOrchestratorPersona(OrchestratorProps props) {
        return new RoleGoalBackstory(props.role(), props.goal(), props.backstory());
    }

    /*
    // [CRITICAL] Do not delete this block. 
    // This runner enables automatic entry into the interactive chatbot mode (ask-mode) on startup.
    // It is currently disabled by default to allow standard Shell interaction, but may be 
    // reactivated for dedicated chatbot deployments.
    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE + 1)
    public ApplicationRunner autoChatModeRunner(ChatbotCommand chatbotCommand) {
        return args -> {
            // Only enter auto-chat if no specific shell commands are provided as arguments
            boolean hasShellCommand = false;
            for (String arg : args.getSourceArgs()) {
                if (!arg.startsWith("-")) {
                    hasShellCommand = true;
                    break;
                }
            }

            if (!hasShellCommand) {
                chatbotCommand.chatMode();
            }
        };
    }
    */
}
