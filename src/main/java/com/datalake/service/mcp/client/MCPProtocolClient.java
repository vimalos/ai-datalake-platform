package com.datalake.service.mcp.client;

import com.datalake.domain.mcp.MCPTool;
import com.datalake.service.mcp.protocol.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * MCP Protocol Client - Implements Model Context Protocol Client
 *
 * Uses JSON-RPC 2.0 for communication
 * Spec: https://spec.modelcontextprotocol.io/
 * Version: 2024-11-05
 *
 * Enables communication with external MCP servers.
 */
@Service
@ConditionalOnProperty(name = "mcp.client.enabled", havingValue = "true", matchIfMissing = true)
public class MCPProtocolClient {
    private static final Logger log = LoggerFactory.getLogger(MCPProtocolClient.class);
    private static final String PROTOCOL_VERSION = "2024-11-05";

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final Map<String, String> remoteServers = new ConcurrentHashMap<>();
    private final Map<String, Boolean> initializedServers = new ConcurrentHashMap<>();
    private final AtomicLong requestIdCounter = new AtomicLong(1);

    @Value("${mcp.client.remote-servers:}")
    private String remoteServersConfig;

    @Value("${mcp.client.name:ai-datalake-client}")
    private String clientName;

    @Value("${mcp.client.version:1.0.0}")
    private String clientVersion;

    public MCPProtocolClient(ObjectMapper objectMapper) {
        this.restTemplate = new RestTemplate();
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void init() {
        if (remoteServersConfig != null && !remoteServersConfig.trim().isEmpty()) {
            String[] servers = remoteServersConfig.split(",");
            for (String server : servers) {
                String trimmed = server.trim();
                if (!trimmed.isEmpty()) {
                    String[] parts = trimmed.split("\\|");
                    String name = parts.length > 1 ? parts[0] : "remote-" + remoteServers.size();
                    String url = parts.length > 1 ? parts[1] : parts[0];
                    remoteServers.put(name, url);
                    log.info("📡 Registered remote MCP server: {} -> {}", name, url);
                }
            }
        }
        log.info("✅ MCP Protocol Client initialized with {} remote server(s)", remoteServers.size());
    }

    /**
     * Initialize connection with MCP server (handshake)
     */
    public InitializeResult initialize(String serverUrl) {
        log.info("🤝 Initializing MCP connection with: {}", serverUrl);

        try {
            // Build initialize request
            JsonRpcRequest request = JsonRpcRequest.builder()
                    .id(requestIdCounter.getAndIncrement())
                    .method("initialize")
                    .params(InitializeParams.builder()
                            .protocolVersion(PROTOCOL_VERSION)
                            .capabilities(ClientCapabilities.builder().build())
                            .clientInfo(Implementation.builder()
                                    .name(clientName)
                                    .version(clientVersion)
                                    .build())
                            .build())
                    .build();

            JsonRpcResponse response = sendRequest(serverUrl, request);

            if (response.getError() != null) {
                log.error("MCP initialization failed: {}", response.getError().getMessage());
                throw new RuntimeException("MCP initialization failed: " + response.getError().getMessage());
            }

            InitializeResult result = objectMapper.convertValue(
                    response.getResult(),
                    InitializeResult.class
            );

            initializedServers.put(serverUrl, true);
            log.info("✅ MCP connection initialized: {} v{}",
                    result.getServerInfo().getName(),
                    result.getServerInfo().getVersion());

            return result;

        } catch (Exception e) {
            log.error("Failed to initialize MCP connection", e);
            throw new RuntimeException("MCP initialization failed", e);
        }
    }

    /**
     * List tools from MCP server using tools/list method
     */
    public List<McpTool> listTools(String serverUrl) {
        log.debug("📋 Listing tools from MCP server: {}", serverUrl);

        ensureInitialized(serverUrl);

        try {
            JsonRpcRequest request = JsonRpcRequest.builder()
                    .id(requestIdCounter.getAndIncrement())
                    .method("tools/list")
                    .params(Map.of())
                    .build();

            JsonRpcResponse response = sendRequest(serverUrl, request);

            if (response.getError() != null) {
                log.error("tools/list failed: {}", response.getError().getMessage());
                return Collections.emptyList();
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> result = (Map<String, Object>) response.getResult();
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> toolsList = (List<Map<String, Object>>) result.get("tools");

            List<McpTool> tools = new ArrayList<>();
            for (Map<String, Object> toolData : toolsList) {
                McpTool tool = objectMapper.convertValue(toolData, McpTool.class);
                tools.add(tool);
            }

            log.info("✅ Retrieved {} tools from MCP server", tools.size());
            return tools;

        } catch (Exception e) {
            log.error("Failed to list tools", e);
            return Collections.emptyList();
        }
    }

    /**
     * Call a tool using tools/call method
     */
    public CallToolResult callTool(String serverUrl, String toolName, Map<String, Object> arguments) {
        log.info("🔧 Calling MCP tool: {} on {}", toolName, serverUrl);

        ensureInitialized(serverUrl);

        try {
            JsonRpcRequest request = JsonRpcRequest.builder()
                    .id(requestIdCounter.getAndIncrement())
                    .method("tools/call")
                    .params(CallToolParams.builder()
                            .name(toolName)
                            .arguments(arguments != null ? arguments : new HashMap<>())
                            .build())
                    .build();

            JsonRpcResponse response = sendRequest(serverUrl, request);

            if (response.getError() != null) {
                log.error("tools/call failed: {}", response.getError().getMessage());
                return CallToolResult.error("Tool call failed: " + response.getError().getMessage());
            }

            CallToolResult result = objectMapper.convertValue(
                    response.getResult(),
                    CallToolResult.class
            );

            log.info("✅ Tool {} executed successfully", toolName);
            return result;

        } catch (Exception e) {
            log.error("Failed to call tool: {}", toolName, e);
            return CallToolResult.error("Tool call failed: " + e.getMessage());
        }
    }

    /**
     * Send JSON-RPC request to MCP server
     */
    private JsonRpcResponse sendRequest(String serverUrl, JsonRpcRequest request) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<JsonRpcRequest> entity = new HttpEntity<>(request, headers);

            ResponseEntity<JsonRpcResponse> response = restTemplate.postForEntity(
                    serverUrl + "/mcp",
                    entity,
                    JsonRpcResponse.class
            );

            if (response.getBody() == null) {
                throw new RuntimeException("Empty response from MCP server");
            }

            return response.getBody();

        } catch (Exception e) {
            log.error("Failed to send MCP request", e);
            throw new RuntimeException("MCP request failed", e);
        }
    }

    /**
     * Ensure server is initialized, initialize if not
     */
    private void ensureInitialized(String serverUrl) {
        if (!initializedServers.getOrDefault(serverUrl, false)) {
            initialize(serverUrl);
        }
    }

    /**
     * Get all configured remote servers
     */
    public Map<String, String> getRemoteServers() {
        return Collections.unmodifiableMap(remoteServers);
    }

    /**
     * Add remote server
     */
    public void addRemoteServer(String name, String url) {
        remoteServers.put(name, url);
        log.info("📡 Added remote MCP server: {} -> {}", name, url);
    }

    /**
     * Check if client is initialized with server
     */
    public boolean isInitialized(String serverUrl) {
        return initializedServers.getOrDefault(serverUrl, false);
    }
}

