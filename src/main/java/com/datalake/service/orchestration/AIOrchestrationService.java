package com.datalake.service.orchestration;

import com.datalake.domain.chat.ChatRequest;
import com.datalake.domain.chat.ChatResponse;
import com.datalake.service.agent.AIAgent;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * AI Orchestration Service - Single Entry Point
 *
 * ARCHITECTURE PRINCIPLE: Single Responsibility
 * - This service is a thin delegation layer only
 * - All query processing is delegated to AIAgent
 * - AIAgent handles: planning, execution, reflection, tool calling
 * - No business logic here - just request validation and delegation
 *
 * FLOW:
 * ChatController → AIOrchestrationService → AIAgent → Response
 *
 * WHY THIS DESIGN:
 * 1. Single path through the system (no multiple code paths)
 * 2. AIAgent is the single source of truth for query processing
 * 3. Easy to test, debug, and maintain
 * 4. Follows Industry Best Practice: Clean Architecture
 * 5. MCP Protocol Server is separate (POST /mcp endpoint)
 *
 * RESPONSIBILITIES:
 * ✅ Delegate to AIAgent
 * ✅ Error handling
 * ✅ Logging
 * ❌ No business logic
 * ❌ No query processing
 * ❌ No tool selection
 * ❌ No response generation
 */
@Service
@RequiredArgsConstructor
public class AIOrchestrationService {

    private static final Logger log = LoggerFactory.getLogger(AIOrchestrationService.class);

    private final AIAgent aiAgent;

    /**
     * Process incoming chat query through AI Agent
     *
     * This is the SINGLE PATH for all user queries from the UI:
     * - Knowledge-based queries: "What is Iceberg?"
     * - Tool-based queries: "List tables in agebergdb"
     * - Action queries: "Compact the users table"
     * - Complex queries: Multi-step workflows
     *
     * The AIAgent handles all aspects:
     * 1. UNDERSTAND: Retrieve RAG context for query understanding
     * 2. PLAN: Create multi-step execution plan using LLM
     * 3. EXECUTE: Call MCP tools as needed
     * 4. REFLECT: LLM synthesizes final response
     *
     * @param request Chat request from user (message, database, table, etc.)
     * @return Chat response with AI Agent's autonomous response
     */
    public ChatResponse processQuery(ChatRequest request) {
        log.info("🔄 Processing user query through AI Agent");
        log.debug("Query: {}", request.getMessage());

        try {
            // SINGLE PATH: Delegate everything to AI Agent
            // AIAgent is the autonomous brain that handles all query types
            return aiAgent.processQuery(request);

        } catch (Exception e) {
            log.error("❌ Error processing query", e);
            return ChatResponse.error("Query processing failed: " + e.getMessage());
        }
    }

    /**
     * Get AI Agent instance for direct access if needed
     * (for internal use only, not part of normal flow)
     *
     * @return AI Agent instance
     */
    public AIAgent getAIAgent() {
        return aiAgent;
    }
}
