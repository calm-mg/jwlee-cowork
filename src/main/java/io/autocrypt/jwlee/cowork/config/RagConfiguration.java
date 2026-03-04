package io.autocrypt.jwlee.cowork.config;

import com.embabel.agent.rag.service.RagServiceEnhancerProperties;
import com.embabel.agent.rag.service.SearchOperations;
import com.embabel.agent.rag.service.support.DirectoryTextSearch;
import com.embabel.agent.rag.tools.ToolishRag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.io.IOException;

@Configuration
@EnableConfigurationProperties(RagServiceEnhancerProperties.class)
public class RagConfiguration {

    @Value("${embabel.agent.rag.import.dir:knowledge/documents}")
    private String importDir;

    @Bean(name = "directorySearch")
    public SearchOperations searchOperations() throws IOException {
        return new DirectoryTextSearch(importDir);
    }

    @Bean(name = "directoryRagTool")
    public ToolishRag localKnowledgeTool(SearchOperations directorySearch) {
        return new ToolishRag("local_knowledge", "Simple directory-based text search (Legacy)", directorySearch);
    }
}
