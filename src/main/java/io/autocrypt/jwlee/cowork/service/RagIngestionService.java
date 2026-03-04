package io.autocrypt.jwlee.cowork.service;

import com.embabel.agent.rag.ingestion.policy.NeverRefreshExistingDocumentContentPolicy;
import com.embabel.agent.rag.service.SearchOperations;
import com.embabel.agent.rag.store.ChunkingContentElementRepository;
import com.embabel.agent.rag.ingestion.TikaHierarchicalContentReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

@Service
public class RagIngestionService {

    private static final Logger logger = LoggerFactory.getLogger(RagIngestionService.class);
    private final ChunkingContentElementRepository ragStore;

    public RagIngestionService(@Qualifier("luceneSearch") SearchOperations ragStore) {
        // LuceneSearchOperations implements both SearchOperations and ChunkingContentElementRepository
        this.ragStore = (ChunkingContentElementRepository) ragStore;
    }

    /**
     * Ingests a file or all files within a directory.
     */
    public void ingestPath(Path path) {
        if (Files.isDirectory(path)) {
            ingestDirectory(path);
        } else {
            ingestFile(path);
        }
    }

    /**
     * Parses a local file (PDF, MD, etc.) and saves it to the Lucene vector database.
     */
    public void ingestFile(Path filePath) {
        String uri = filePath.toUri().toString();
        
        // Use the policy to only ingest if not already present
        var documentId = NeverRefreshExistingDocumentContentPolicy.INSTANCE
                .ingestUriIfNeeded(
                        ragStore,
                        new TikaHierarchicalContentReader(),
                        uri
                );

        if (documentId != null) {
            logger.info("Successfully ingested document: {} (ID: {})", filePath.getFileName(), documentId);
        }
    }

    /**
     * Recursively walks through a directory and ingests all readable files.
     */
    private void ingestDirectory(Path dirPath) {
        logger.info("Scanning directory for ingestion: {}", dirPath.toAbsolutePath());
        try (Stream<Path> paths = Files.walk(dirPath)) {
            paths.filter(Files::isRegularFile)
                 .filter(this::isSupportedFormat)
                 .forEach(this::ingestFile);
        } catch (IOException e) {
            logger.error("Failed to walk directory: {}", dirPath, e);
        }
    }

    private boolean isSupportedFormat(Path path) {
        String fileName = path.getFileName().toString().toLowerCase();
        return fileName.endsWith(".pdf") || fileName.endsWith(".md") || fileName.endsWith(".txt") || fileName.endsWith(".docx");
    }
}
