package com.datalake.domain.chat;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Chat response to user
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatResponse {
    private String message;
    private String type; // action, knowledge, analysis, hybrid, tool-assisted, metadata
    private Object data;
    private List<String> sources;
    private Map<String, Object> metadata; // For LLM-MCP integration tracking
    private long responseTimeMs;

    public static ChatResponse error(String message) {
        return ChatResponse.builder()
                .message("Error: " + message)
                .type("error")
                .build();
    }
}