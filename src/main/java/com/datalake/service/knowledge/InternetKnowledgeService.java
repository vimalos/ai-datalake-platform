package com.datalake.service.knowledge;

import com.datalake.service.llm.LLMService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Internet Knowledge Service - Retrieves information from the internet
 *
 * Uses DuckDuckGo Instant Answer API (no API key required) for:
 * - General knowledge queries
 * - Technology definitions
 * - How-to questions not in local knowledge base
 *
 * Falls back to LLM general knowledge if internet search fails
 */
@Service
@ConditionalOnProperty(name = "knowledge.internet.enabled", havingValue = "true", matchIfMissing = true)
public class InternetKnowledgeService {

    private static final Logger log = LoggerFactory.getLogger(InternetKnowledgeService.class);

    private final RestTemplate restTemplate;
    private final LLMService llmService;

    @Value("${knowledge.internet.provider:duckduckgo}")
    private String provider;

    @Value("${knowledge.internet.timeout:5000}")
    private int timeoutMs;

    public InternetKnowledgeService(LLMService llmService) {
        this.restTemplate = new RestTemplate();
        this.llmService = llmService;
        log.info("🌐 Internet Knowledge Service initialized");
    }

    /**
     * Search for information on the internet
     *
     * @param query Search query
     * @param maxResults Maximum number of results
     * @return List of search results with titles and snippets
     */
    public List<Map<String, String>> search(String query, int maxResults) {
        log.info("🔍 Internet search: {}", query);

        try {
            if ("duckduckgo".equalsIgnoreCase(provider)) {
                return searchDuckDuckGo(query, maxResults);
            } else {
                return searchWithLLM(query);
            }
        } catch (Exception e) {
            log.warn("⚠️ Internet search failed, falling back to LLM: {}", e.getMessage());
            return searchWithLLM(query);
        }
    }

    /**
     * Search using DuckDuckGo Instant Answer API
     */
    private List<Map<String, String>> searchDuckDuckGo(String query, int maxResults) {
        try {
            String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
            String url = String.format("https://api.duckduckgo.com/?q=%s&format=json&no_html=1&skip_disambig=1", encodedQuery);

            log.debug("🌐 Calling DuckDuckGo API: {}", url);
            ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return parseDuckDuckGoResponse(response.getBody(), maxResults);
            }
        } catch (Exception e) {
            log.warn("DuckDuckGo search failed: {}", e.getMessage());
        }
        return new ArrayList<>();
    }

    /**
     * Parse DuckDuckGo API response
     */
    @SuppressWarnings("unchecked")
    private List<Map<String, String>> parseDuckDuckGoResponse(Map<String, Object> response, int maxResults) {
        List<Map<String, String>> results = new ArrayList<>();

        // Get abstract (main answer)
        String abstractText = (String) response.get("AbstractText");
        String abstractUrl = (String) response.get("AbstractURL");
        if (abstractText != null && !abstractText.isEmpty()) {
            Map<String, String> result = new HashMap<>();
            result.put("title", "DuckDuckGo Answer");
            result.put("snippet", abstractText);
            result.put("url", abstractUrl != null ? abstractUrl : "");
            results.add(result);
        }

        // Get related topics
        List<Map<String, Object>> relatedTopics = (List<Map<String, Object>>) response.get("RelatedTopics");
        if (relatedTopics != null) {
            for (int i = 0; i < Math.min(relatedTopics.size(), maxResults - results.size()); i++) {
                Map<String, Object> topic = relatedTopics.get(i);
                String text = (String) topic.get("Text");
                String firstURL = (String) topic.get("FirstURL");

                if (text != null && !text.isEmpty()) {
                    Map<String, String> result = new HashMap<>();
                    result.put("title", "Related Topic " + (i + 1));
                    result.put("snippet", text);
                    result.put("url", firstURL != null ? firstURL : "");
                    results.add(result);
                }
            }
        }

        log.info("✅ Found {} results from DuckDuckGo", results.size());
        return results;
    }

    /**
     * Fallback: Use LLM's general knowledge
     */
    private List<Map<String, String>> searchWithLLM(String query) {
        log.info("📚 Using LLM general knowledge for: {}", query);

        try {
            String prompt = String.format("""
                    Answer this question using your general knowledge: "%s"
                    
                    Provide a concise, accurate answer (2-4 sentences).
                    If you're not sure, say so.
                    """, query);

            String answer = llmService.complete(prompt);

            Map<String, String> result = new HashMap<>();
            result.put("title", "LLM General Knowledge");
            result.put("snippet", answer);
            result.put("url", "");

            return List.of(result);

        } catch (Exception e) {
            log.error("LLM fallback failed", e);
            return new ArrayList<>();
        }
    }

    /**
     * Get a quick answer for a query (single result)
     */
    public String getQuickAnswer(String query) {
        List<Map<String, String>> results = search(query, 1);

        if (!results.isEmpty()) {
            return results.get(0).get("snippet");
        }

        return "No information found.";
    }

    /**
     * Check if a query seems to be asking for general/internet knowledge
     */
    public boolean isGeneralKnowledgeQuery(String query) {
        String lowerQuery = query.toLowerCase();

        // Exclude database-specific queries (these should use MCP tools)
        if (lowerQuery.contains("database") && (
                lowerQuery.contains("available") ||
                lowerQuery.contains("show") ||
                lowerQuery.contains("list") ||
                lowerQuery.contains("what")
        )) {
            return false; // This is asking about OUR databases, not general knowledge
        }

        if (lowerQuery.contains("table") && (
                lowerQuery.contains("show") ||
                lowerQuery.contains("list") ||
                lowerQuery.contains("schema")
        )) {
            return false; // This is asking about OUR tables
        }

        // Patterns that indicate general knowledge queries
        boolean isGeneralPattern =
               lowerQuery.matches("^what is .*") ||
               lowerQuery.matches("^who is .*") ||
               lowerQuery.matches("^how to .*") ||
               lowerQuery.matches("^why .*") ||
               lowerQuery.matches("^when .*") ||
               lowerQuery.contains("explain ") && !lowerQuery.contains("table") ||
               lowerQuery.contains("define ") ||
               lowerQuery.contains("meaning of ");

        // Even if it matches general pattern, exclude if it's about specific technologies we work with
        if (isGeneralPattern && (
                lowerQuery.contains("iceberg") ||
                lowerQuery.contains("glue") ||
                lowerQuery.contains("spark")
        )) {
            // Allow internet search only for conceptual questions, not operational ones
            return !lowerQuery.contains("my ") &&
                   !lowerQuery.contains("our ") &&
                   !lowerQuery.contains("this ");
        }

        return isGeneralPattern;
    }
}

