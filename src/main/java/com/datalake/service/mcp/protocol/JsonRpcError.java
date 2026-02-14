package com.datalake.service.mcp.protocol;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * JSON-RPC 2.0 Error Object - MCP Protocol Compliant
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class JsonRpcError {

    @JsonProperty("code")
    private int code;

    @JsonProperty("message")
    private String message;

    @JsonProperty("data")
    private Object data;

    // Standard JSON-RPC error codes
    public static final int PARSE_ERROR = -32700;
    public static final int INVALID_REQUEST = -32600;
    public static final int METHOD_NOT_FOUND = -32601;
    public static final int INVALID_PARAMS = -32602;
    public static final int INTERNAL_ERROR = -32603;

    public static JsonRpcError parseError(String message) {
        return JsonRpcError.builder()
                .code(PARSE_ERROR)
                .message(message)
                .build();
    }

    public static JsonRpcError invalidRequest(String message) {
        return JsonRpcError.builder()
                .code(INVALID_REQUEST)
                .message(message)
                .build();
    }

    public static JsonRpcError methodNotFound(String method) {
        return JsonRpcError.builder()
                .code(METHOD_NOT_FOUND)
                .message("Method not found: " + method)
                .build();
    }

    public static JsonRpcError invalidParams(String message) {
        return JsonRpcError.builder()
                .code(INVALID_PARAMS)
                .message(message)
                .build();
    }

    public static JsonRpcError internalError(String message) {
        return JsonRpcError.builder()
                .code(INTERNAL_ERROR)
                .message(message)
                .build();
    }
}

