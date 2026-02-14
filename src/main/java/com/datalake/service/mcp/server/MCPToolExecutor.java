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
 * MCP Tool Executor - Simplified tool execution service
 *
 * Provides direct tool execution without JSON-RPC protocol overhead.
 * Used by AI Agent and Orchestration Service for internal tool calls.
 *
 * For external MCP protocol communication, see MCPProtocolServer.
 */
@Service
@RequiredArgsConstructor
public class MCPToolExecutor {
    private static final Logger log = LoggerFactory.getLogger(MCPToolExecutor.class);

    @Autowired
    private final MCPToolRegistry toolRegistry;

    /**
     * List all available tools for LLM
     */
    public List<MCPTool> listTools() {
        return toolRegistry.getAllTools();
    }

    /**
     * Execute a tool requested by LLM
     */
    public MCPResponse executeTool(MCPRequest request) {
        log.info("MCP tool execution requested: {}", request.getToolName());
        log.debug("MCPRequest - toolName: {}, arguments: {}", request.getToolName(), request.getArguments());

        long startTime = System.currentTimeMillis();

        try {
            // Validate tool exists (metadata)
            MCPTool tool = toolRegistry.getTool(request.getToolName())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Tool not found: " + request.getToolName()
                    ));

            // Get and execute tool implementation directly
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
     * Get tool by name
     */
    public Optional<MCPTool> getTool(String name) {
        return toolRegistry.getTool(name);
    }
}
