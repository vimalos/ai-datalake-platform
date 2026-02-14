package com.datalake.service.mcp;

import com.datalake.domain.mcp.MCPRequest;
import com.datalake.domain.mcp.MCPResponse;
import com.datalake.domain.mcp.MCPTool;
import com.datalake.service.mcp.registry.MCPToolRegistry;
import com.datalake.service.mcp.server.MCPToolExecutor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for MCP (Model Context Protocol) components
 */
@SpringBootTest
@TestPropertySource(properties = {
        "spark.enabled=false",
        "mcp.client.enabled=false",
        "mcp.llm.integration.enabled=false",
        "agent.enabled=false",
        "llm.ollama.url=http://localhost:11434",
        "aws.glue.enabled=false",
        "rag.knowledge-base.path=src/main/resources/knowledge-base"
})
public class MCPUsageTest {

    @Autowired
    MCPToolExecutor mcpToolExecutor;

    @Autowired
    MCPToolRegistry toolRegistry;

    @Test
    void testMCPToolExecutorLoadsSuccessfully() {
        assertThat(mcpToolExecutor).isNotNull();
        assertThat(toolRegistry).isNotNull();
    }

    @Test
    void testMCPToolsAreRegistered() {
        List<MCPTool> tools = mcpToolExecutor.listTools();

        assertThat(tools).isNotEmpty();
        assertThat(tools).hasSizeGreaterThan(5);

        // Verify key tools are registered
        List<String> toolNames = tools.stream()
                .map(MCPTool::getName)
                .toList();

        assertThat(toolNames).contains(
                "list_tables",
                "get_schema",
                "get_table_stats",
                "compact_table",
                "expire_snapshots"
        );
    }

    @Test
    void testMCPToolsHaveMetadata() {
        List<MCPTool> tools = mcpToolExecutor.listTools();

        for (MCPTool tool : tools) {
            assertThat(tool.getName()).isNotBlank();
            assertThat(tool.getDescription()).isNotBlank();
            assertThat(tool.getCategory()).isNotBlank();
        }
    }

    @Test
    void testToolRegistryCategories() {
        var catalogTools = toolRegistry.getToolsByCategory("catalog");
        var maintenanceTools = toolRegistry.getToolsByCategory("maintenance");

        assertThat(catalogTools).isNotEmpty();
        assertThat(maintenanceTools).isNotEmpty();
    }

    @Test
    void testToolRegistryGetImplementation() {
        var listTablesImpl = toolRegistry.getImplementation("list_tables");
        var compactImpl = toolRegistry.getImplementation("compact_table");

        assertThat(listTablesImpl).isPresent();
        assertThat(compactImpl).isPresent();
    }

    @Test
    void testMCPRequestValidation() {
        MCPRequest request = new MCPRequest();
        request.setToolName("list_tables");

        Map<String, Object> args = new HashMap<>();
        args.put("database", "test_db");
        request.setArguments(args);

        assertThat(request.getToolName()).isEqualTo("list_tables");
        assertThat(request.getArguments()).containsKey("database");
    }

    @Test
    void testMCPResponseStructure() {
        MCPResponse response = MCPResponse.success("test data");

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getData()).isEqualTo("test data");

        MCPResponse errorResponse = MCPResponse.error("error message", null);

        assertThat(errorResponse.isSuccess()).isFalse();
        assertThat(errorResponse.getMessage()).isEqualTo("error message");
    }
}
