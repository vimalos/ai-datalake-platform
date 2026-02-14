package com.datalake.service.mcp.server;

import com.datalake.domain.mcp.MCPTool;
import com.datalake.domain.mcp.MCPToolResult;
import com.datalake.service.mcp.protocol.*;
import com.datalake.service.mcp.registry.MCPToolRegistry;
import com.datalake.service.mcp.tooling.base.IcebergMaintenanceTool;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * MCP Protocol Server - Implements Model Context Protocol Specification
 *
 * Protocol: JSON-RPC 2.0
 * Spec: https://spec.modelcontextprotocol.io/
 * Version: 2024-11-05
 *
 * Supported Methods:
 * - initialize: Handshake and capability negotiation
 * - initialized: Notification after successful init
 * - tools/list: List available tools
 * - tools/call: Execute a tool
 *
 * This service handles external MCP protocol communication.
 * For internal tool execution, see MCPToolExecutor.
 */
@Service
public class MCPProtocolServer {
    private static final Logger log = LoggerFactory.getLogger(MCPProtocolServer.class);
    private static final String PROTOCOL_VERSION = "2024-11-05";

    @Autowired
    private MCPToolRegistry toolRegistry;

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${mcp.server.name:ai-datalake-platform}")
    private String serverName;

    @Value("${mcp.server.version:1.0.0}")
    private String serverVersion;

    private final Map<String, Boolean> initializedClients = new ConcurrentHashMap<>();
    private ServerCapabilities serverCapabilities;

    @PostConstruct
    public void init() {
        // Define server capabilities
        serverCapabilities = ServerCapabilities.builder()
                .tools(ServerCapabilities.ToolsCapability.builder()
                        .listChanged(false)
                        .build())
                .build();

        log.info("✅ MCP Protocol Server initialized - Protocol: {} Server: {} v{}",
                PROTOCOL_VERSION, serverName, serverVersion);
    }

    /**
     * Process JSON-RPC 2.0 request
     */
    public JsonRpcResponse processRequest(JsonRpcRequest request) {
        log.debug("MCP Request: method={}, id={}", request.getMethod(), request.getId());

        try {
            // Validate JSON-RPC version
            if (!"2.0".equals(request.getJsonrpc())) {
                return JsonRpcResponse.builder()
                        .id(request.getId())
                        .error(JsonRpcError.invalidRequest("JSON-RPC version must be 2.0"))
                        .build();
            }

            // Route to appropriate method handler
            Object result = switch (request.getMethod()) {
                case "initialize" -> handleInitialize(request);
                case "tools/list" -> handleToolsList(request);
                case "tools/call" -> handleToolsCall(request);
                default -> throw new IllegalArgumentException("Method not found: " + request.getMethod());
            };

            return JsonRpcResponse.builder()
                    .id(request.getId())
                    .result(result)
                    .build();

        } catch (IllegalArgumentException e) {
            log.error("Method not found: {}", request.getMethod());
            return JsonRpcResponse.builder()
                    .id(request.getId())
                    .error(JsonRpcError.methodNotFound(request.getMethod()))
                    .build();

        } catch (Exception e) {
            log.error("Error processing MCP request", e);
            return JsonRpcResponse.builder()
                    .id(request.getId())
                    .error(JsonRpcError.internalError(e.getMessage()))
                    .build();
        }
    }

    /**
     * Handle "initialize" method - MCP handshake
     */
    private InitializeResult handleInitialize(JsonRpcRequest request) {
        log.info("🤝 MCP Initialize request received");

        try {
            InitializeParams params = objectMapper.convertValue(
                    request.getParams(),
                    InitializeParams.class
            );

            String clientId = request.getId() != null ? request.getId().toString() : "unknown";
            initializedClients.put(clientId, true);

            log.info("✅ MCP Client initialized: {} (Protocol: {})",
                    params.getClientInfo() != null ? params.getClientInfo().getName() : "Unknown",
                    params.getProtocolVersion());

            return InitializeResult.builder()
                    .protocolVersion(PROTOCOL_VERSION)
                    .capabilities(serverCapabilities)
                    .serverInfo(Implementation.builder()
                            .name(serverName)
                            .version(serverVersion)
                            .build())
                    .build();

        } catch (Exception e) {
            log.error("Failed to parse initialize params", e);
            throw new IllegalArgumentException("Invalid initialize parameters: " + e.getMessage());
        }
    }

    /**
     * Handle "tools/list" method - List available tools
     */
    private Map<String, Object> handleToolsList(JsonRpcRequest request) {
        log.debug("📋 MCP tools/list request");

        List<MCPTool> tools = toolRegistry.getAllTools();

        List<McpTool> mcpTools = tools.stream()
                .map(this::convertToMcpTool)
                .collect(Collectors.toList());

        log.debug("Returning {} tools", mcpTools.size());

        return Map.of("tools", mcpTools);
    }

    /**
     * Handle "tools/call" method - Execute a tool
     */
    private CallToolResult handleToolsCall(JsonRpcRequest request) {
        log.info("🔧 MCP tools/call request");

        try {
            CallToolParams params = objectMapper.convertValue(
                    request.getParams(),
                    CallToolParams.class
            );

            String toolName = params.getName();
            Map<String, Object> arguments = params.getArguments();

            log.info("Executing tool: {} with args: {}", toolName, arguments);

            // Get tool implementation
            IcebergMaintenanceTool impl = toolRegistry.getImplementation(toolName)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Tool implementation not found: " + toolName
                    ));

            // Execute tool
            MCPToolResult result = impl.execute(arguments);

            // Convert to MCP format
            if (result.isSuccess()) {
                String message = formatToolResult(result);
                return CallToolResult.success(message);
            } else {
                String errorMsg = result.getMessage() != null ? result.getMessage() : "Tool execution failed";
                return CallToolResult.error(errorMsg);
            }

        } catch (Exception e) {
            log.error("Tool execution failed", e);
            return CallToolResult.error("Tool execution failed: " + e.getMessage());
        }
    }

    /**
     * Convert internal MCPTool to MCP protocol McpTool
     */
    private McpTool convertToMcpTool(MCPTool tool) {
        // Build JSON Schema for input
        Map<String, Object> schema = new HashMap<>();
        schema.put("type", "object");

        if (tool.getRequiredArguments() != null && !tool.getRequiredArguments().isEmpty()) {
            Map<String, Object> properties = new HashMap<>();
            List<String> required = new ArrayList<>();

            for (String arg : tool.getRequiredArguments()) {
                properties.put(arg, Map.of(
                        "type", "string",
                        "description", "The " + arg + " parameter"
                ));
                required.add(arg);
            }

            schema.put("properties", properties);
            schema.put("required", required);
        }

        return McpTool.builder()
                .name(tool.getName())
                .description(tool.getDescription())
                .inputSchema(schema)
                .build();
    }

    /**
     * Format tool result as text
     */
    private String formatToolResult(MCPToolResult result) {
        StringBuilder sb = new StringBuilder();

        if (result.getMessage() != null) {
            sb.append(result.getMessage());
        }

        if (result.getData() != null) {
            if (sb.length() > 0) sb.append("\n\n");
            sb.append("Data:\n");
            try {
                sb.append(objectMapper.writerWithDefaultPrettyPrinter()
                        .writeValueAsString(result.getData()));
            } catch (Exception e) {
                sb.append(result.getData().toString());
            }
        }

        if (result.getExecutionTimeMs() > 0) {
            if (sb.length() > 0) sb.append("\n");
            sb.append(String.format("Execution time: %dms", result.getExecutionTimeMs()));
        }

        return sb.toString();
    }

    /**
     * Check if client is initialized
     */
    public boolean isClientInitialized(String clientId) {
        return initializedClients.getOrDefault(clientId, false);
    }
}

