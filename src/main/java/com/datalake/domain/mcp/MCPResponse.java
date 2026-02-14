package com.datalake.domain.mcp;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * MCP Response to LLM
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MCPResponse {
    private boolean success;
    private Object data;
    private String message;
    private List<String> errors;
    private List<String> suggestions;

    public static MCPResponse success(Object data) {
        return MCPResponse.builder()
                .success(true)
                .data(data)
                .build();
    }

    public static MCPResponse error(String message, List<String> suggestions) {
        return MCPResponse.builder()
                .success(false)
                .message(message)
                .suggestions(suggestions)
                .build();
    }
}