package com.datalake.service.mcp.tooling.base;

import com.datalake.domain.mcp.MCPToolResult;

import java.util.Map;

/**
 * Base interface for all MCP Tools
 * All tool implementations must implement this interface
 */
public interface IcebergMaintenanceTool {

    /**
     * Get the tool name (unique identifier)
     */
    String getName();

    /**
     * Get the tool description
     */
    String getDescription();

    /**
     * Get the tool category
     */
    String getCategory();

    /**
     * Execute the tool with provided arguments
     *
     * @param arguments Tool-specific arguments
     * @return Result of tool execution
     */
    MCPToolResult execute(Map<String, Object> arguments);
}
