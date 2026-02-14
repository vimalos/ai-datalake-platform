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
 * Chat API Controller - Single entry point for all user interactions
 * Delegates ALL requests to AIOrchestrationService for proper LLM->MCP flow
 */
@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class ChatController {
    private static final Logger log = LoggerFactory.getLogger(ChatController.class);

    private final AIOrchestrationService orchestrationService;

    /**
     * Main chat endpoint - handles ALL user queries through AI orchestration
     * Flow: UI -> ChatController -> AIOrchestrationService -> LLM+RAG -> MCPClient -> MCPServer -> Tools
     */
    @PostMapping("/query")
    public ResponseEntity<ChatResponse> query(@RequestBody ChatRequest request) {
        log.info("Chat query received: {}", request.getMessage());
        ChatResponse response = orchestrationService.processQuery(request);
        return ResponseEntity.ok(response);
    }

    /**
     * Health check endpoint
     */
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("Chat service is running");
    }
}