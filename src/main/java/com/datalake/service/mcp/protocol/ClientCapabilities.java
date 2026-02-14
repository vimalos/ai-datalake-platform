package com.datalake.service.mcp.protocol;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Client Capabilities - MCP Protocol
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ClientCapabilities {

    @JsonProperty("roots")
    private RootsCapability roots;

    @JsonProperty("sampling")
    private Object sampling;  // Can be extended with sampling capabilities

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class RootsCapability {
        @JsonProperty("listChanged")
        private Boolean listChanged;
    }
}

