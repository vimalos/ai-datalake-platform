package com.datalake.api.controller;

import com.datalake.service.mcp.protocol.JsonRpcRequest;
import com.datalake.service.mcp.protocol.JsonRpcResponse;
import com.datalake.service.mcp.server.MCPProtocolServer;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * MCP Controller - Exposes MCP protocol server via HTTP
 *
 * Endpoints:
 * POST /mcp - JSON-RPC 2.0 endpoint for MCP protocol
 *
 * This follows the Model Context Protocol specification:
 * https://spec.modelcontextprotocol.io/
 */
@RestController
@RequestMapping("/mcp")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class MCPController {
    private static final Logger log = LoggerFactory.getLogger(MCPController.class);

    private final MCPProtocolServer mcpProtocolServer;

    /**
     * Main MCP endpoint - handles JSON-RPC 2.0 requests
     *
     * Supported methods:
     * - initialize: Client handshake
     * - tools/list: List available tools
     * - tools/call: Execute a tool
     */
    @PostMapping(
            value = "",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<JsonRpcResponse> handleMcpRequest(@RequestBody JsonRpcRequest request) {
        log.info("MCP JSON-RPC request: method={}, id={}", request.getMethod(), request.getId());

        JsonRpcResponse response = mcpProtocolServer.processRequest(request);

        if (response.getError() != null) {
            log.error("MCP request failed: {} - {}",
                    response.getError().getCode(),
                    response.getError().getMessage());
        } else {
            log.debug("MCP request successful: id={}", response.getId());
        }

        return ResponseEntity.ok(response);
    }

    /**
     * Health check for MCP server
     */
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("MCP Server is running");
    }
}

