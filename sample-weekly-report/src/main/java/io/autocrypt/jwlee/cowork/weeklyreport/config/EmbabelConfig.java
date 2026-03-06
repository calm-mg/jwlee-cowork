package io.autocrypt.jwlee.cowork.weeklyreport.config;

import com.embabel.agent.prompt.persona.RoleGoalBackstory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({EmbabelConfig.AnalystProps.class, EmbabelConfig.CollectorProps.class})
public class EmbabelConfig {

    @ConfigurationProperties("embabel.identities.weekly-report.analyst")
    public record AnalystProps(String role, String goal, String backstory) {}

    @ConfigurationProperties("embabel.identities.weekly-report.collector")
    public record CollectorProps(String role, String goal, String backstory) {}

    @Bean
    public RoleGoalBackstory analystPersona(AnalystProps props) {
        return new RoleGoalBackstory(props.role(), props.goal(), props.backstory());
    }

    @Bean
    public RoleGoalBackstory collectorPersona(CollectorProps props) {
        return new RoleGoalBackstory(props.role(), props.goal(), props.backstory());
    }
}
