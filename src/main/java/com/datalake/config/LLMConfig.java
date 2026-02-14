package com.datalake.config;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.ollama.OllamaEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * LLM Configuration for Embedding Models
 * Supports dynamic model selection with fallback
 */
@Configuration
public class LLMConfig {

    private static final Logger log = org.slf4j.LoggerFactory.getLogger(LLMConfig.class);

    private final RestTemplate rest = new RestTemplate();
    @Value("${llm.ollama.url}")
    private String ollamaUrl;
    @Value("${llm.ollama.embedding-model:llama3}")
    private String configuredEmbeddingModel;

    /**
     * Fetch available models from Ollama
     */
    @SuppressWarnings("unchecked")
    private List<String> fetchAvailableModels() {
        try {
            String url = ollamaUrl + "/api/tags";
            Map<String, Object> response = rest.getForObject(url, Map.class);
            if (response != null && response.containsKey("models")) {
                List<Map<String, Object>> models = (List<Map<String, Object>>) response.get("models");
                return models.stream()
                        .map(m -> (String) m.get("name"))
                        .toList();
            }
        } catch (Exception e) {
            log.warn("Could not fetch available models from Ollama: {}", e.getMessage());
        }
        return List.of();
    }

    /**
     * Get the best available embedding model
     * Priority: configured model > nomic-embed-text > fallback to configured model
     */
    private String selectEmbeddingModel() {
        List<String> available = fetchAvailableModels();

        if (available.isEmpty()) {
            log.warn("No models available from Ollama. Using configured model: {}", configuredEmbeddingModel);
            return configuredEmbeddingModel;
        }

        // Check if configured model is available
        if (available.contains(configuredEmbeddingModel)) {
            log.info("Using configured embedding model: {}", configuredEmbeddingModel);
            return configuredEmbeddingModel;
        }

        // Try nomic-embed-text
        if (available.contains("nomic-embed-text")) {
            log.info("Configured model '{}' not found. Fallback to: nomic-embed-text", configuredEmbeddingModel);
            return "nomic-embed-text";
        }

        // Use first available model
        String fallback = available.get(0);
        log.info("Neither '{}' nor 'nomic-embed-text' found. Using available model: {}",
                configuredEmbeddingModel, fallback);
        return fallback;
    }

    /**
     * Create embedding model with fallback logic
     */
    @Bean
    public EmbeddingModel embeddingModel() {
        String modelName = selectEmbeddingModel();
        log.info("Initializing embedding model: {} from Ollama: {}", modelName, ollamaUrl);

        return OllamaEmbeddingModel.builder()
                .baseUrl(ollamaUrl)
                .modelName(modelName)
                .timeout(Duration.ofSeconds(120))
                .build();
    }

    /**
     * In-memory embedding store for RAG
     * Stores TextSegment objects with their embeddings for semantic search
     * For production, replace with Milvus or other vector database
     */
    @Bean
    public EmbeddingStore<TextSegment> embeddingStore() {
        return new InMemoryEmbeddingStore<>();
    }
}

