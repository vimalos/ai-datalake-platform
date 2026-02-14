package com.datalake.service.mcp.protocol;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * MCP Initialize Result
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class InitializeResult {

    @JsonProperty("protocolVersion")
    private String protocolVersion;

    @JsonProperty("capabilities")
    private ServerCapabilities capabilities;

    @JsonProperty("serverInfo")
    private Implementation serverInfo;
}

