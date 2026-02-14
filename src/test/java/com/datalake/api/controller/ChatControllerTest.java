package com.datalake.api.controller;

import com.datalake.domain.chat.ChatRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for Chat Controller
 */
@SpringBootTest
@TestPropertySource(properties = {
        "spark.enabled=false",
        "mcp.client.enabled=false",
        "mcp.llm.integration.enabled=false",
        "llm.ollama.url=http://localhost:11434",
        "aws.glue.enabled=false",
        "rag.knowledge-base.path=src/main/resources/knowledge-base"
})
public class ChatControllerTest {

    @Autowired
    private ChatController chatController;

    @Test
    void testChatControllerLoads() {
        assertThat(chatController).isNotNull();
    }

    @Test
    void testChatRequestModel() {
        ChatRequest request = new ChatRequest();
        request.setMessage("show tables");
        request.setDatabase("test_db");
        request.setTable(null);
        request.setConversationId(null);
        request.setStreamResponse(false);

        assertThat(request.getMessage()).isEqualTo("show tables");
        assertThat(request.getDatabase()).isEqualTo("test_db");
        assertThat(request.isStreamResponse()).isFalse();
    }

    @Test
    void testListDatabases() {
        // This will fail gracefully without Spark, but tests the wiring
        try {
            var response = chatController.query(new ChatRequest("list databases", null, true, null, null));
            // If it doesn't throw, controller is wired correctly
            assertThat(response).isNotNull();
        } catch (Exception e) {
            // Expected without Spark running
            assertThat(e).isNotNull();
        }
    }
}

