package com.datalake.service.mcp.client;

import com.datalake.domain.mcp.MCPRequest;
import com.datalake.domain.mcp.MCPResponse;
import com.datalake.domain.mcp.MCPTool;
import com.datalake.service.maintenance.MaintenanceService;
import com.fasterxml.jackson.core.type.TypeReference;
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

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MCP Client - Connects to remote MCP servers
 * <p>
 * Enables this platform to:
 * - Discover tools from remote MCP servers
 * - Execute tools on remote servers
 * - Aggregate capabilities from multiple MCP endpoints
 */
@Service
@ConditionalOnProperty(name = "mcp.client.enabled", havingValue = "true", matchIfMissing = true)
public class MCPClient {
    private static final Logger log = LoggerFactory.getLogger(MCPClient.class);

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final Map<String, String> remoteServers = new ConcurrentHashMap<>();

    @Value("${mcp.client.remote-servers:}")
    private String remoteServersConfig;

    @Value("${mcp.client.local-url:}")
    private String localServerUrl;

    @Value("${mcp.client.timeout:30}")
    private int timeoutSeconds;

    public MCPClient(ObjectMapper objectMapper) {
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
        log.info("✅ MCP Client initialized with {} remote server(s)", remoteServers.size());
    }

    /**
     * Discover tools from a remote MCP server
     */
    public List<MCPTool> discoverTools(String serverUrl) {
        try {
            log.debug("Discovering tools from: {}", serverUrl);

            ResponseEntity<String> response = restTemplate.getForEntity(
                    serverUrl + "/mcp/tools",
                    String.class
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                List<MCPTool> tools = objectMapper.readValue(
                        response.getBody(),
                        new TypeReference<>() {
                        }
                );
                log.info("✅ Discovered {} tools from {}", tools.size(), serverUrl);
                return tools;
            }
        } catch (Exception e) {
            log.error("Failed to discover tools from {}: {}", serverUrl, e.getMessage());
        }
        return Collections.emptyList();
    }

    /**
     * Execute a tool on a remote MCP server
     */
    public MCPResponse executeTool(String serverUrl, MCPRequest request) {
        try {
            log.debug("Executing tool {} on remote server: {}", request.getToolName(), serverUrl);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<MCPRequest> entity = new HttpEntity<>(request, headers);

            ResponseEntity<String> response = restTemplate.postForEntity(
                    serverUrl + "/mcp/execute",
                    entity,
                    String.class
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                MCPResponse mcpResponse = objectMapper.readValue(
                        response.getBody(),
                        MCPResponse.class
                );
                log.info("✅ Tool {} executed on remote server", request.getToolName());
                return mcpResponse;
            }
        } catch (Exception e) {
            log.error("Failed to execute tool {} on {}: {}",
                    request.getToolName(), serverUrl, e.getMessage());
            return MCPResponse.error("Remote execution failed: " + e.getMessage(), null);
        }
        return MCPResponse.error("No response from remote server", null);
    }

    /**
     * Discover tools from all configured remote servers
     */
    public Map<String, List<MCPTool>> discoverAllTools() {
        Map<String, List<MCPTool>> allTools = new ConcurrentHashMap<>();
        remoteServers.forEach((name, url) -> {
            List<MCPTool> tools = discoverTools(url);
            if (!tools.isEmpty()) {
                allTools.put(name, tools);
            }
        });
        return allTools;
    }

    /**
     * Execute tool on a specific named remote server
     */
    public MCPResponse executeOnRemoteServer(String serverName, MCPRequest request) {
        String serverUrl = remoteServers.get(serverName);
        if (serverUrl == null) {
            log.error("Remote server not found: {}", serverName);
            return MCPResponse.error("Remote server not found: " + serverName, null);
        }
        return executeTool(serverUrl, request);
    }

    /**
     * Get all configured remote servers
     */
    public Map<String, String> getRemoteServers() {
        return Collections.unmodifiableMap(remoteServers);
    }

    /**
     * Add a remote server dynamically
     */
    public void addRemoteServer(String name, String url) {
        remoteServers.put(name, url);
        log.info("📡 Added remote MCP server: {} -> {}", name, url);
    }

    /**
     * Remove a remote server
     */
    public void removeRemoteServer(String name) {
        String removed = remoteServers.remove(name);
        if (removed != null) {
            log.info("Removed remote MCP server: {}", name);
        }
    }

    /**
     * Resolve the default MCP server URL (local preferred, then first remote)
     */
    public Optional<String> getDefaultServerUrl() {
        if (localServerUrl != null && !localServerUrl.trim().isEmpty()) {
            return Optional.of(localServerUrl.trim());
        }
        return remoteServers.values().stream().findFirst();
    }
}
