package com.datalake.service.orchestration;

import com.datalake.domain.chat.ChatRequest;
import com.datalake.domain.chat.ChatResponse;
import com.datalake.service.agent.AIAgent;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Orchestration Service - Entry Point for Query Processing.
 *
 * This service acts as a thin delegation layer between the REST API and the AI Agent.
 * It follows the Single Responsibility Principle by delegating all query processing
 * logic to the AIAgent, which handles planning, execution, and reflection.
 *
 * Architecture Flow:
 * ChatController → AIOrchestrationService → AIAgent → LLM/RAG/MCP → Response
 *
 * Design Principles:
 * - Single path through the system (no branching logic)
 * - Thin layer with minimal business logic
 * - AIAgent is the autonomous decision maker
 * - Clean separation of concerns
 *
 * Responsibilities:
 * - Request validation
 * - Error handling
 * - Logging
 * - Delegation to AIAgent
 */
@Service
@RequiredArgsConstructor
public class AIOrchestrationService {

    private static final Logger log = LoggerFactory.getLogger(AIOrchestrationService.class);

    private final AIAgent aiAgent;

    /**
     * Processes user queries through the AI Agent.
     *
     * The AI Agent autonomously handles:
     * 1. UNDERSTAND: Uses RAG to retrieve relevant domain knowledge
     * 2. PLAN: Creates multi-step execution plan based on query intent
     * 3. EXECUTE: Calls MCP tools (list databases, query data, compact tables, etc.)
     * 4. REFLECT: Synthesizes final human-readable response
     *
     * Supported Query Types:
     * - Discovery: "Show databases", "List tables", "Show schema"
     * - Analysis: "How many records?", "Show table stats"
     * - Actions: "Compact users table", "Expire snapshots"
     * - Knowledge: "What is Iceberg?", "How does compaction work?"
     *
     * @param request Contains user's natural language query and optional context
     * @return ChatResponse with formatted answer and execution metadata
     */
    public ChatResponse processQuery(ChatRequest request) {
        log.info("🔄 Processing user query through AI Agent");
        log.debug("Query: {}", request.getMessage());

        try {
            return aiAgent.processQuery(request);

        } catch (Exception e) {
            log.error("❌ Error processing query", e);
            return ChatResponse.error("Query processing failed: " + e.getMessage());
        }
    }

    /**
     * Returns the AI Agent instance for internal access.
     */
    public AIAgent getAIAgent() {
        return aiAgent;
    }
}
