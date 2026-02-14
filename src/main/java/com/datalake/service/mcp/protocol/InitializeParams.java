package com.datalake.service.mcp.protocol;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * MCP Initialize Request Parameters
 * Method: "initialize"
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class InitializeParams {

    @JsonProperty("protocolVersion")
    private String protocolVersion;

    @JsonProperty("capabilities")
    private ClientCapabilities capabilities;

    @JsonProperty("clientInfo")
    private Implementation clientInfo;
}

