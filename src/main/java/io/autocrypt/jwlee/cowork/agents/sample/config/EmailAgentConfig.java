package io.autocrypt.jwlee.cowork.agents.sample.config;

import com.embabel.agent.prompt.persona.RoleGoalBackstory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({EmailAgentConfig.WriterProps.class, EmailAgentConfig.ReviewerProps.class})
public class EmailAgentConfig {

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
