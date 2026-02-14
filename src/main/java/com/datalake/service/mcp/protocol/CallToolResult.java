package com.datalake.service.mcp.protocol;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * MCP tools/call result
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CallToolResult {

    @JsonProperty("content")
    private List<Content> content;

    @JsonProperty("isError")
    private Boolean isError;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Content {
        @JsonProperty("type")
        private String type;  // "text", "image", "resource"

        @JsonProperty("text")
        private String text;

        @JsonProperty("data")
        private String data;  // base64 for images

        @JsonProperty("mimeType")
        private String mimeType;
    }

    public static CallToolResult success(String message) {
        return CallToolResult.builder()
                .content(List.of(
                        Content.builder()
                                .type("text")
                                .text(message)
                                .build()
                ))
                .isError(false)
                .build();
    }

    public static CallToolResult error(String message) {
        return CallToolResult.builder()
                .content(List.of(
                        Content.builder()
                                .type("text")
                                .text(message)
                                .build()
                ))
                .isError(true)
                .build();
    }
}

