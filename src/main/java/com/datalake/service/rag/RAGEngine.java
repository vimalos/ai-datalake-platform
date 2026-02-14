package com.datalake.service.rag;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * RAG Engine for knowledge base queries
 * Uses LangChain4j with in-memory embedding store for semantic search
 */
@Service
@RequiredArgsConstructor
public class RAGEngine {

    private static final Logger log = LoggerFactory.getLogger(RAGEngine.class);

    private final EmbeddingModel embeddingModel;
    private final EmbeddingStore<TextSegment> embeddingStore;

    /**
     * Index a document into the knowledge base
     */
    public void indexDocument(String docId, String content) {
        try {
            log.debug("Indexing document: {} (length: {} chars)", docId, content.length());

            // Create TextSegment with metadata
            Metadata metadata = new Metadata();
            metadata.put("id", docId);
            metadata.put("source", "knowledge-base");
            TextSegment segment = TextSegment.from(content, metadata);

            // Generate embedding for the document content
            Embedding embedding = embeddingModel.embed(segment).content();

            // Store the embedding with the text segment
            embeddingStore.add(embedding, segment);

            log.info("Successfully indexed document: {} ({} chars)", docId, content.length());
        } catch (Exception e) {
            log.error("Failed to index document: {}. Error: {}", docId, e.getMessage());

            // Provide helpful error messages
            if (e.getMessage() != null && e.getMessage().contains("model") && e.getMessage().contains("not found")) {
                log.error("⚠️  EMBEDDING MODEL NOT AVAILABLE");
                log.error("Please ensure the embedding model is pulled in Ollama.");
                log.error("Run: docker exec ollama ollama pull llama3");
                log.error("Or run: ./init-ollama.sh");
            }

            // Don't re-throw - allow initialization to continue
        }
    }

    /**
     * Query the knowledge base with semantic search
     */
    public Map<String, Object> query(String question) {
        try {
            log.debug("RAG query: {}", question);

            // Generate embedding for the query
            Embedding queryEmbedding = embeddingModel.embed(question).content();

            // Search for most relevant documents
            EmbeddingSearchRequest searchRequest = EmbeddingSearchRequest.builder()
                    .queryEmbedding(queryEmbedding)
                    .maxResults(3)
                    .minScore(0.5) // Only return matches with >50% relevance
                    .build();

            EmbeddingSearchResult<TextSegment> searchResult = embeddingStore.search(searchRequest);
            List<EmbeddingMatch<TextSegment>> matches = searchResult.matches();

            // Extract the text content from matched documents
            List<String> sources = new ArrayList<>();
            for (EmbeddingMatch<TextSegment> match : matches) {
                if (match.embedded() != null) {
                    sources.add(match.embedded().text());
                    log.debug("Found relevant doc (score: {}): {}",
                            match.score(),
                            match.embedded().metadata().getString("id"));
                }
            }

            log.info("Found {} relevant documents for query (avg score: {})",
                    sources.size(),
                    matches.isEmpty() ? 0.0 : matches.stream().mapToDouble(EmbeddingMatch::score).average().orElse(0.0));

            Map<String, Object> result = new java.util.HashMap<>();
            result.put("sources", sources);
            result.put("count", sources.size());

            // Calculate average relevance score
            double avgScore = matches.isEmpty() ? 0.0 :
                    matches.stream()
                            .mapToDouble(EmbeddingMatch::score)
                            .average()
                            .orElse(0.0);
            result.put("confidence", avgScore);

            return result;
        } catch (Exception e) {
            log.error("Failed to query knowledge base: {}", e.getMessage());

            if (e.getMessage() != null && e.getMessage().contains("model") && e.getMessage().contains("not found")) {
                log.error("⚠️  EMBEDDING MODEL NOT AVAILABLE");
                log.error("Please ensure the embedding model is pulled in Ollama.");
                log.error("Run: docker exec ollama ollama pull llama3");
            }

            Map<String, Object> error = new java.util.HashMap<>();
            error.put("error", e.getMessage());
            error.put("sources", new ArrayList<>());
            return error;
        }
    }

    /**
     * Get embedding model
     */
    public EmbeddingModel getEmbeddingModel() {
        return embeddingModel;
    }

    /**
     * Get embedding store
     */
    public EmbeddingStore<TextSegment> getEmbeddingStore() {
        return embeddingStore;
    }
}