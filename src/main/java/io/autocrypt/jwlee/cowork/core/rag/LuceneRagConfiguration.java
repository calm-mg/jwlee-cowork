package io.autocrypt.jwlee.cowork.core.rag;

import com.embabel.common.ai.model.ModelProvider;
import com.embabel.common.ai.model.ModelSelectionCriteria;
import com.embabel.agent.rag.service.SearchOperations;
import com.embabel.agent.rag.lucene.LuceneSearchOperations;
import com.embabel.agent.rag.tools.ToolishRag;
import com.embabel.agent.rag.ingestion.transform.AddTitlesChunkTransformer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.Optional;

@Configuration
public class LuceneRagConfiguration {

    @Value("${embabel.agent.rag.lucene.dir:target/lucene-index}")
    private String luceneIndexDir;

    @Bean(name = "luceneSearch")
    @Primary
    public SearchOperations luceneSearchOperations(ModelProvider modelProvider) throws IOException {
        var embeddingService = modelProvider.getEmbeddingService(
                ModelSelectionCriteria.getAuto());

        if (embeddingService == null) {
            // Skip bean creation if embedding service is unavailable (e.g., in test environments)
            return null;
        }

        return LuceneSearchOperations
                .withName("rca-knowledge")
                .withEmbeddingService(embeddingService)
                .withIndexPath(Paths.get(luceneIndexDir))
                .withChunkTransformer(AddTitlesChunkTransformer.INSTANCE)
                .buildAndLoadChunks();
    }

    @Bean(name = "luceneRagTool")
    @Primary
    @ConditionalOnBean(name = "luceneSearch")
    public ToolishRag luceneRagTool(Optional<SearchOperations> luceneSearch) {
        return luceneSearch
                .map(search -> new ToolishRag("rca_sources", "Technical documents for RCA", search))
                .orElse(null);
    }
}
