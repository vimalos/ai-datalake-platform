package com.datalake.domain.mcp;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * MCP Request from LLM
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MCPRequest {
    private String toolName;
    private Map<String, Object> arguments;
}
