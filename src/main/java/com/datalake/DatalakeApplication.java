package com.datalake;

import com.datalake.service.mcp.registry.MCPToolRegistry;
import com.datalake.service.mcp.server.MCPToolExecutor;
import com.datalake.service.rag.RAGService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

/**
 * Main Application Entry Point for AI-Powered Data Lakehouse Platform.
 *
 * This platform integrates Apache Iceberg, Apache Spark, LLM (Ollama), RAG, and MCP Protocol
 * to provide an intelligent data lakehouse with natural language query capabilities.
 *
 * Architecture Flow:
 * UI → ChatController → AIAgent → LLM + RAG → MCP Client → MCP Server → Iceberg Tools → AWS Glue Catalog
 *
 * Key Components:
 * - Apache Iceberg: Open table format for large-scale data lakes
 * - Apache Spark: Distributed query engine for Iceberg operations
 * - LLM (Ollama): Local language model for natural language understanding
 * - RAG Engine: Retrieval-Augmented Generation for domain knowledge
 * - MCP Protocol: Model Context Protocol for tool orchestration
 * - AWS Glue: Metastore catalog for Iceberg tables
 * - S3: Data storage layer
 */
@SpringBootApplication
@RequiredArgsConstructor
public class DatalakeApplication {

    private static final Logger log = LoggerFactory.getLogger(DatalakeApplication.class);

    @Autowired
    private final RAGService ragService;
    @Autowired
    private final MCPToolExecutor mcpToolExecutor;
    @Autowired
    private final MCPToolRegistry toolRegistry;

    public static void main(String[] args) {
        SpringApplication.run(DatalakeApplication.class, args);
    }

    /**
     * Initializes the platform components on application startup.
     * Loads RAG knowledge base and validates MCP tool registrations.
     */
    @Bean
    @SuppressWarnings("unused")
    public CommandLineRunner init() {
        return args -> {
            log.info("=== Initializing AI Data Lakehouse Platform ===");

            try {
                log.info("Loading RAG knowledge base...");
                ragService.loadDefaultKnowledgeBase();

                var tools = mcpToolExecutor.listTools();
                log.info("MCP tools registered at startup: {}", tools.size());

                var maintenanceTools = toolRegistry.getToolsByCategory("maintenance");
                log.info("Maintenance tools discovered: {}", maintenanceTools.size());

                var compactionImpl = toolRegistry.getImplementation("compact_table");
                log.info("Compaction implementation present: {}", compactionImpl.isPresent());

                log.info("=== Initialization Complete ===");
                log.info("Platform ready at http://localhost:8080");
                log.info("API Docs available at http://localhost:8080/swagger-ui.html");

            } catch (Exception e) {
                log.error("Initialization failed", e);
                throw new RuntimeException("Failed to initialize platform", e);
            }
        };
    }
}
