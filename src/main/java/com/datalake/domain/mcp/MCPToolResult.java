package com.datalake.domain.mcp;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Result of MCP tool execution
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MCPToolResult {
    private boolean success;
    private Object data;
    private String message;
    private long executionTimeMs;
}