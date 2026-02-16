package com.datalake.service.mcp.server;

import com.datalake.domain.mcp.MCPRequest;
import com.datalake.domain.mcp.MCPResponse;
import com.datalake.domain.mcp.MCPTool;
import com.datalake.domain.mcp.MCPToolResult;
import com.datalake.service.mcp.registry.MCPToolRegistry;
import com.datalake.service.mcp.tooling.base.IcebergMaintenanceTool;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * MCP Tool Executor - Internal Tool Execution Service.
 *
 * This service provides direct execution of MCP tools without JSON-RPC protocol overhead.
 * It serves as the MCP Server's tool execution engine, used internally by the AI Agent
 * and orchestration layer.
 *
 * Architecture Role:
 * - Acts as the "MCP Server" in the MCP protocol architecture
 * - Provides tool discovery (listTools) for LLM integration
 * - Executes tool implementations registered in MCPToolRegistry
 * - Returns structured MCPResponse objects with execution results
 *
 * MCP Protocol Flow:
 * AI Agent → MCP Client → MCPToolExecutor (Server) → Tool Implementation → Response
 *
 * Available Tool Categories:
 * - catalog: list_databases, list_tables, get_schema, create_table
 * - query: query_data, insert_rows
 * - analysis: get_table_stats, suggest_maintenance
 * - maintenance: compact_table, expire_snapshots, remove_orphan_files, full_maintenance
 *
 * For external MCP protocol communication via JSON-RPC, see MCPProtocolServer.
 */
@Service
@RequiredArgsConstructor
public class MCPToolExecutor {
    private static final Logger log = LoggerFactory.getLogger(MCPToolExecutor.class);

    @Autowired
    private final MCPToolRegistry toolRegistry;

    /**
     * Lists all available MCP tools for LLM consumption.
     *
     * The LLM uses this list to understand available capabilities and plan tool invocations.
     * Each tool includes name, description, category, and input schema.
     *
     * @return List of all registered MCP tools
     */
    public List<MCPTool> listTools() {
        return toolRegistry.getAllTools();
    }

    /**
     * Executes a specific MCP tool with provided arguments.
     *
     * This is the core tool execution method that:
     * 1. Validates tool existence in registry
     * 2. Retrieves tool implementation
     * 3. Executes with provided arguments
     * 4. Returns structured result
     *
     * @param request MCPRequest containing tool name and arguments
     * @return MCPResponse with execution result or error details
     */
    public MCPResponse executeTool(MCPRequest request) {
        log.info("MCP tool execution requested: {}", request.getToolName());
        log.debug("MCPRequest - toolName: {}, arguments: {}", request.getToolName(), request.getArguments());

        long startTime = System.currentTimeMillis();

        try {
            MCPTool tool = toolRegistry.getTool(request.getToolName())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Tool not found: " + request.getToolName()
                    ));

            IcebergMaintenanceTool impl = toolRegistry.getImplementation(tool.getName())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "No implementation found for tool: " + request.getToolName()
                    ));

            log.debug("Found implementation for tool: {}. Executing.", tool.getName());
            Map<String, Object> args = request.getArguments();
            log.debug("Passing arguments to implementation: {} (null: {})", args, args == null);

            MCPToolResult result = impl.execute(args);
            result.setExecutionTimeMs(System.currentTimeMillis() - startTime);

            return MCPResponse.success(result);

        } catch (Exception e) {
            log.error("Tool execution failed", e);
            return MCPResponse.builder()
                    .success(false)
                    .message("Error: " + e.getMessage())
                    .errors(List.of(e.getMessage()))
                    .build();
        }
    }

    /**
     * Retrieves tool metadata by name.
     */
    public Optional<MCPTool> getTool(String name) {
        return toolRegistry.getTool(name);
    }
}
