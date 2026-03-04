package io.autocrypt.jwlee.cowork.config;

import com.embabel.common.ai.model.ModelProvider;
import com.embabel.common.ai.model.ByNameModelSelectionCriteria;
import com.embabel.agent.rag.service.SearchOperations;
import com.embabel.agent.rag.lucene.LuceneSearchOperations;
import com.embabel.agent.rag.tools.ToolishRag;
import com.embabel.agent.rag.ingestion.transform.AddTitlesChunkTransformer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import java.io.IOException;
import java.nio.file.Paths;

@Configuration
public class LuceneRagConfiguration {

    @Value("${embabel.agent.rag.lucene.dir:target/lucene-index}")
    private String luceneIndexDir;

    @Bean(name = "luceneSearch")
    @Primary
    public SearchOperations luceneSearchOperations(ModelProvider modelProvider) throws IOException {
        var geminiEmbedding = modelProvider.getEmbeddingService(
                new ByNameModelSelectionCriteria("gemini-embedding-001"));

        return LuceneSearchOperations
                .withName("rca-knowledge")
                .withEmbeddingService(geminiEmbedding)
                .withIndexPath(Paths.get(luceneIndexDir))
                // 각 데이터 조각에 문서 제목(장애 보고서 등)을 자동으로 붙여서 검색 품질을 높임
                .withChunkTransformer(AddTitlesChunkTransformer.INSTANCE)
                .buildAndLoadChunks();
    }

    @Bean(name = "luceneRagTool")
    @Primary
    public ToolishRag luceneRagTool(SearchOperations luceneSearch) {
        return new ToolishRag("rca_sources", "Technical documents, PDFs, and incident reports for Root Cause Analysis", luceneSearch);
    }
}
