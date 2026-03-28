package io.autocrypt.jwlee.cowork.core.rag;

import com.embabel.common.ai.model.ModelProvider;
import com.embabel.common.ai.model.ModelSelectionCriteria;
import com.embabel.agent.rag.service.SearchOperations;
import com.embabel.agent.rag.lucene.LuceneSearchOperations;
import com.embabel.agent.rag.tools.ToolishRag;
import io.autocrypt.jwlee.cowork.core.workaround.JsonSafeToolishRag;
import com.embabel.agent.rag.ingestion.transform.AddTitlesChunkTransformer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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

        Path indexPath = Paths.get(luceneIndexDir);
        boolean isExistingIndex = false;
        if (Files.exists(indexPath) && Files.isDirectory(indexPath)) {
            try (var stream = Files.list(indexPath)) {
                isExistingIndex = stream.anyMatch(p -> p.getFileName().toString().startsWith("segments"));
                if (!isExistingIndex) {
                    // Invalid index (e.g., empty dir or interrupted initialization), clear it to allow fresh start
                    deleteRecursively(indexPath);
                }
            }
        }

        var builder = LuceneSearchOperations
                .withName("rca-knowledge")
                .withEmbeddingService(embeddingService)
                .withIndexPath(indexPath)
                .withChunkTransformer(AddTitlesChunkTransformer.INSTANCE);

        return isExistingIndex ? builder.buildAndLoadChunks() : builder.build();
    }

    private void deleteRecursively(Path path) throws IOException {
        if (Files.exists(path)) {
            try (java.util.stream.Stream<Path> walk = Files.walk(path)) {
                walk.sorted(java.util.Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(java.io.File::delete);
            }
        }
    }

    @Bean(name = "luceneRagTool")
    @Primary
    @ConditionalOnBean(name = "luceneSearch")
    public JsonSafeToolishRag luceneRagTool(Optional<SearchOperations> luceneSearch) {
        return luceneSearch
                .map(search -> new JsonSafeToolishRag("rca_sources", "Technical documents for RCA", search))
                .orElse(null);
    }
}
