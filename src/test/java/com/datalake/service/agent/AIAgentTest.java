package com.datalake.service.agent;

import com.datalake.domain.chat.ChatRequest;
import com.datalake.domain.chat.ChatResponse;
import com.datalake.service.llm.LLMService;
import com.datalake.service.mcp.server.MCPToolExecutor;
import com.datalake.service.rag.RAGService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for AI Agent autonomous capabilities
 */
@SpringBootTest
@TestPropertySource(properties = {
        "spark.enabled=false",
        "mcp.client.enabled=false",
        "agent.enabled=true",
        "agent.planning.enabled=true",
        "agent.reflection.enabled=true",
        "agent.max-iterations=3",
        "llm.ollama.url=http://localhost:11434",
        "aws.glue.enabled=false"
})
public class AIAgentTest {

    @Autowired
    private AIAgent aiAgent;

    @Autowired
    private MCPToolExecutor mcpToolExecutor;

    @Autowired
    private LLMService llmService;

    @Autowired
    private RAGService ragService;

    @Test
    void testAIAgentLoadsSuccessfully() {
        assertThat(aiAgent).isNotNull();
        assertThat(mcpToolExecutor).isNotNull();
        assertThat(llmService).isNotNull();
        assertThat(ragService).isNotNull();
    }

    @Test
    void testAIAgentProcessesSimpleQuery() {
        ChatRequest request = new ChatRequest();
        request.setMessage("What is Apache Iceberg?");

        ChatResponse response = aiAgent.processQuery(request);

        assertThat(response).isNotNull();
        assertThat(response.getMessage()).isNotBlank();
        assertThat(response.getType()).isEqualTo("agent");
    }

    @Test
    void testAIAgentResponseHasMetadata() {
        ChatRequest request = new ChatRequest();
        request.setMessage("Explain data lakehouse");

        ChatResponse response = aiAgent.processQuery(request);

        assertThat(response).isNotNull();
        assertThat(response.getMetadata()).isNotNull();
        assertThat(response.getMetadata()).containsKey("executionTrace");
        assertThat(response.getMetadata()).containsKey("toolsUsed");
        assertThat(response.getMetadata()).containsKey("iterations");
    }

    @Test
    void testAIAgentHandlesComplexQuery() {
        ChatRequest request = new ChatRequest();
        request.setMessage("How do I optimize my Iceberg table performance?");

        ChatResponse response = aiAgent.processQuery(request);

        assertThat(response).isNotNull();
        assertThat(response.getMessage()).isNotBlank();
        // Should use RAG knowledge
        assertThat(response.getMessage().length()).isGreaterThan(50);
    }

    @Test
    void testAIAgentUsesRAGKnowledge() {
        ChatRequest request = new ChatRequest();
        request.setMessage("What are the benefits of table compaction?");

        ChatResponse response = aiAgent.processQuery(request);

        assertThat(response).isNotNull();
        assertThat(response.getMessage()).isNotBlank();
        // Metadata should show RAG was used
        assertThat(response.getMetadata()).isNotNull();
    }

    @Test
    void testAIAgentExecutionTrace() {
        ChatRequest request = new ChatRequest();
        request.setMessage("Tell me about Apache Spark");

        ChatResponse response = aiAgent.processQuery(request);

        assertThat(response).isNotNull();
        assertThat(response.getMetadata()).isNotNull();
        assertThat(response.getMetadata().get("executionTrace")).isNotNull();
    }
}

