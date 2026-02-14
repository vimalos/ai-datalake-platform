package com.datalake.service.mcp.protocol;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Server Capabilities - MCP Protocol
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ServerCapabilities {

    @JsonProperty("tools")
    private ToolsCapability tools;

    @JsonProperty("resources")
    private ResourcesCapability resources;

    @JsonProperty("prompts")
    private PromptsCapability prompts;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ToolsCapability {
        @JsonProperty("listChanged")
        private Boolean listChanged;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ResourcesCapability {
        @JsonProperty("subscribe")
        private Boolean subscribe;

        @JsonProperty("listChanged")
        private Boolean listChanged;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PromptsCapability {
        @JsonProperty("listChanged")
        private Boolean listChanged;
    }
}

