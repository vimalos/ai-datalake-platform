package com.datalake.service.llm;

import com.datalake.exception.GlobalExceptionHandler;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.exception.HttpException;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * LLM Service using LangChain4j
 * Supports both local (Ollama) and cloud (Claude API) with RAG
 */
@Service
public class LLMService {

    private static final Logger log = org.slf4j.LoggerFactory.getLogger(LLMService.class);

    private final EmbeddingModel embeddingModel;
    private final EmbeddingStore<TextSegment> embeddingStore;
    private final ChatModel cloudModel = null;
    private final RestTemplate rest = new RestTemplate();
    private final String ollamaBaseUrl;
    private final String configuredModel;
    private final double configuredTemperature;
    private final long configuredTimeoutSeconds;
    // make mutable so we can rebuild with fallback model
    private volatile ChatModel localModel;

    public LLMService(
            @Value("${llm.ollama.url}") String ollamaUrl,
            @Value("${llm.ollama.model}") String ollamaModel,
            @Value("${llm.claude.api-key:}") String claudeApiKey,
            @Value("${llm.temperature:0.3}") double temperature,
            @Value("${llm.timeout.seconds:120}") long timeoutSeconds,
            EmbeddingModel embeddingModel,
            EmbeddingStore<TextSegment> embeddingStore
    ) {
        this.embeddingModel = embeddingModel;
        this.embeddingStore = embeddingStore;

        this.ollamaBaseUrl = ollamaUrl != null ? ollamaUrl.trim() : "http://localhost:11434";
        this.configuredModel = (ollamaModel == null || ollamaModel.isBlank()) ? "" : ollamaModel.trim();
        this.configuredTemperature = temperature;
        this.configuredTimeoutSeconds = timeoutSeconds;

        // Build local Ollama model with fallback logic
        this.localModel = buildOllamaModelWithFallback(this.ollamaBaseUrl, this.configuredModel, temperature, timeoutSeconds);

        log.info("LLM Service initialized - Local model: {}, Embeddings: enabled from config",
                (this.localModel != null ? this.configuredModel : "none"));
    }

    private ChatModel buildOllamaModelWithFallback(String baseUrl, String configuredModel,
                                                   double temperature, long timeoutSeconds) {
        try {
            // Check available models from Ollama
            List<String> available = fetchAvailableOllamaModels(baseUrl);
            if (available != null && !available.isEmpty()) {
                log.debug("Available Ollama models: {}", available);
                if (configuredModel != null && !configuredModel.isEmpty() && available.contains(configuredModel)) {
                    log.info("✅ Configured Ollama model '{}' is available", configuredModel);
                    return createOllamaChatModel(baseUrl, configuredModel, temperature, timeoutSeconds);
                } else {
                    String fallback = available.get(0);
                    log.info("⚠️ Configured Ollama model '{}' not found; falling back to '{}'", configuredModel, fallback);
                    return createOllamaChatModel(baseUrl, fallback, temperature, timeoutSeconds);
                }
            } else {
                // No list available - try building with configured model
                if (configuredModel != null && !configuredModel.isEmpty()) {
                    log.info("ℹ️ Could not fetch model list from Ollama; using configured model '{}'. Ollama may still initialize the model on first use.", configuredModel);
                    return createOllamaChatModel(baseUrl, configuredModel, temperature, timeoutSeconds);
                } else {
                    log.warn("❌ No Ollama models available and no configured model set.");
                    return null;
                }
            }
        } catch (Exception e) {
            log.error("❌ Failed to initialize Ollama model ({}). Model may not be available or Ollama is unreachable at {}. Error: {}",
                    configuredModel, baseUrl, e.getMessage());
            // last attempt: try configured model - may throw later
            try {
                if (configuredModel != null && !configuredModel.isEmpty()) {
                    log.info("Attempting final fallback to configured model: {}", configuredModel);
                    return createOllamaChatModel(baseUrl, configuredModel, temperature, timeoutSeconds);
                }
            } catch (Exception ex) {
                log.error("Final attempt to create Ollama model failed", ex);
            }
            return null;
        }
    }

    private ChatModel createOllamaChatModel(String baseUrl, String modelName, double temperature, long timeoutSeconds) {
        try {
            return OllamaChatModel.builder()
                    .baseUrl(baseUrl)
                    .modelName(modelName)
                    .temperature(temperature)
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .build();
        } catch (Exception e) {
            log.error("Failed to create OllamaChatModel for {}@{}", modelName, baseUrl, e);
            throw e;
        }
    }

    @SuppressWarnings("unchecked")
    private List<String> fetchAvailableOllamaModels(String baseUrl) {
        try {
            String url = baseUrl;
            if (!url.endsWith("/")) url += "/";
            url += "api/tags"; // Correct Ollama endpoint for listing models

            ResponseEntity<Map> resp = rest.getForEntity(url, Map.class);
            if (resp.getStatusCode().is2xxSuccessful()) {
                Map<String, Object> body = resp.getBody();
                if (body == null) return List.of();

                List<Map<String, Object>> models = (List<Map<String, Object>>) body.get("models");
                if (models == null) return List.of();

                return models.stream()
                        .map(m -> String.valueOf(m.getOrDefault("name", "")))
                        .filter(s -> s != null && !s.isBlank())
                        .toList();
            }
        } catch (Exception e) {
            log.warn("Could not fetch available Ollama models from {}: {}", baseUrl, e.getMessage());
        }
        return List.of();
    }

    public String complete(String prompt) {
        return complete(prompt, false);
    }

    public String complete(String prompt, boolean useCloud) {
        ChatModel model = (useCloud && cloudModel != null) ? cloudModel : localModel;
        if (model == null) throw new IllegalStateException("No LLM model available");

        try {
            return model.chat(prompt);
        } catch (HttpException he) {
            // handle model not found - try to recover by fetching available models and rebuilding
            String msg = he.getMessage() != null ? he.getMessage().toLowerCase() : "";
            if (msg.contains("model") && msg.contains("not found")) {
                log.warn("Ollama model not found during chat; attempting to recover by selecting a fallback model");
                try {
                    List<String> available = fetchAvailableOllamaModels(this.ollamaBaseUrl);
                    if (!available.isEmpty()) {
                        String fallback = available.get(0);
                        log.info("Rebuilding local model with fallback: {}", fallback);
                        this.localModel = createOllamaChatModel(this.ollamaBaseUrl, fallback, this.configuredTemperature, this.configuredTimeoutSeconds);
                        return this.localModel.chat(prompt);
                    }
                } catch (Exception ex) {
                    log.error("Recovery attempt failed", ex);
                }
            }
            log.error("LLM HTTP error", he);
            throw new RuntimeException("LLM HTTP error", he);
        } catch (Exception e) {
            log.error("LLM completion failed", e);
            throw new RuntimeException("Failed to generate response", e);
        }
    }

    public String generateResponse(String userQuery, String context) {
        String prompt = String.format("""
                You are an expert AI assistant for Apache Iceberg data lakehouse platform.
                Provide concise, actionable, and technical responses.
                
                User Query: %s
                
                Context: %s
                
                Guidelines:
                - Be specific and technical
                - Provide code examples when relevant
                - Suggest best practices
                - If uncertain, say so explicitly
                
                Response:""", userQuery, context);

        return complete(prompt);
    }

    public String generateResponse(String userQuery, String context, String retrievedKnowledge) {
        String prompt = String.format("""
                You are an expert AI assistant for Apache Iceberg data lakehouse platform.
                
                IMPORTANT: Always PRIORITIZE information from the Knowledge Base below.
                The Knowledge Base contains the most accurate and up-to-date information about this platform.
                
                User Query: %s
                
                === KNOWLEDGE BASE (USE THIS FIRST) ===
                %s
                ========================================
                
                Additional Context: %s
                
                Instructions:
                1. First, check if the Knowledge Base above contains relevant information
                2. Base your answer primarily on the Knowledge Base
                3. Only use general knowledge if the Knowledge Base doesn't cover the topic
                4. Always cite when using Knowledge Base information
                
                Response:""", userQuery,
                retrievedKnowledge.isEmpty() ? "No specific knowledge base content found for this query." : retrievedKnowledge,
                context);

        return complete(prompt);
    }

    /**
     * Generate tool selection with RAG context
     * This method is called by AIOrchestrationService for MCP tool selection
     */
    public String selectToolsWithContext(String userQuery, String ragContext, String toolsDescription,
                                         String database, String table, int maxTools) {
        String prompt = String.format("""
                You are an AI assistant for an Apache Iceberg data lakehouse platform.
                Your task is to analyze the user's query and select appropriate MCP tools to execute.
                
                User Query: %s
                Database: %s
                Table: %s
                
                === KNOWLEDGE BASE (PRIORITY INFORMATION) ===
                %s
                =============================================
                
                Available MCP Tools:
                %s
                
                Instructions:
                1. FIRST, review the Knowledge Base above for context about Iceberg, tables, and best practices
                2. Analyze what information or actions the query needs
                3. Select ONLY the necessary tools from the list above
                4. For each tool, specify the exact arguments needed
                5. Maximum %d tool calls allowed
                6. Use Knowledge Base information to make better tool selection decisions
                
                Respond in this EXACT format:
                
                TOOLS_TO_USE:
                - tool_name: <name> | args: <key1=value1, key2=value2>
                
                REASONING:
                <Explain why these tools will help answer the query, referencing Knowledge Base if relevant>
                
                If no tools are needed (query can be answered from Knowledge Base alone):
                TOOLS_TO_USE: NONE
                REASONING: <Explain why no tools are needed and how Knowledge Base provides the answer>
                
                Important:
                - Use database/table from context if not specified by user
                - For "show tables" or "list tables", use tool: list_tables with database argument
                - For table schema, use tool: get_schema with database and table arguments
                - For table statistics, use tool: get_table_stats with database and table arguments
                - Tool names must EXACTLY match those in the Available MCP Tools list
                
                Now analyze the query and respond:
                """,
                userQuery,
                database != null ? database : "default",
                table != null ? table : "not specified",
                ragContext != null && !ragContext.isEmpty() ? ragContext : "No specific knowledge base content available.",
                toolsDescription,
                maxTools);

        return complete(prompt);
    }

    /**
     * Generate final response from tool results with RAG context
     */
    public String synthesizeResponseFromTools(String userQuery, String ragContext, String toolResults) {
        String prompt = String.format("""
                You are an AI assistant for Apache Iceberg data lakehouse operations.
                
                User Query: %s
                
                === KNOWLEDGE BASE (PRIORITY CONTEXT) ===
                %s
                ==========================================
                
                Tool Execution Results:
                %s
                
                Instructions:
                1. FIRST, consider information from the Knowledge Base above
                2. Then, interpret the Tool Execution Results
                3. Combine both to provide a comprehensive answer
                4. Format the response in a user-friendly way with clear explanations
                5. Reference Knowledge Base concepts when explaining technical details
                
                Provide a helpful, concise answer to the user's query:
                """,
                userQuery,
                ragContext != null && !ragContext.isEmpty() ? ragContext : "No additional knowledge base context.",
                toolResults);

        return complete(prompt);
    }

    public EmbeddingModel getEmbeddingModel() {
        return embeddingModel;
    }

    public EmbeddingStore<TextSegment> getEmbeddingStore() {
        return embeddingStore;
    }
}