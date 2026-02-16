package com.datalake.api.controller;

import com.datalake.domain.chat.ChatRequest;
import com.datalake.domain.chat.ChatResponse;
import com.datalake.service.orchestration.AIOrchestrationService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST API Controller for Natural Language Chat Interface.
 *
 * This is the primary entry point for all user interactions with the AI Data Lakehouse Platform.
 * All queries are routed through the AIOrchestrationService which coordinates the AI Agent,
 * LLM, RAG, and MCP protocol for intelligent query processing.
 *
 * Request Flow:
 * 1. User submits natural language query via UI
 * 2. ChatController receives request
 * 3. Delegates to AIOrchestrationService
 * 4. AI Agent analyzes intent and creates execution plan
 * 5. LLM processes query with RAG-enhanced context
 * 6. MCP Client communicates with MCP Server
 * 7. MCP Server executes appropriate Iceberg tools
 * 8. Response flows back through layers to UI
 */
@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class ChatController {
    private static final Logger log = LoggerFactory.getLogger(ChatController.class);

    private final AIOrchestrationService orchestrationService;

    /**
     * Processes natural language queries about the data lakehouse.
     *
     * Handles queries such as:
     * - "Show all databases"
     * - "List tables in analytics database"
     * - "Show schema of users table"
     * - "How many records in users table?"
     * - "Run compaction on users table"
     * - "Does this table need maintenance?"
     *
     * @param request Contains the user's natural language query
     * @return ChatResponse with formatted answer and execution details
     */
    @PostMapping("/query")
    public ResponseEntity<ChatResponse> query(@RequestBody ChatRequest request) {
        log.info("Chat query received: {}", request.getMessage());
        ChatResponse response = orchestrationService.processQuery(request);
        return ResponseEntity.ok(response);
    }

    /**
     * Health check endpoint to verify service availability.
     */
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("Chat service is running");
    }
}