package com.datalake.domain;

import com.datalake.domain.mcp.MCPRequest;
import com.datalake.domain.mcp.MCPResponse;
import com.datalake.domain.mcp.MCPTool;
import com.datalake.domain.mcp.MCPToolResult;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for MCP model classes
 */
public class MCPModelsTest {

    @Test
    void testMCPToolCreation() {
        MCPTool tool = MCPTool.builder()
                .name("test_tool")
                .description("Test tool description")
                .category("test")
                .requiredArguments(Arrays.asList("arg1", "arg2"))
                .optionalArguments(new HashMap<>())
                .outputSchema("string")
                .build();

        assertThat(tool.getName()).isEqualTo("test_tool");
        assertThat(tool.getDescription()).isEqualTo("Test tool description");
        assertThat(tool.getCategory()).isEqualTo("test");
        assertThat(tool.getRequiredArguments()).hasSize(2);
    }

    @Test
    void testMCPRequestWithArguments() {
        MCPRequest request = new MCPRequest();
        request.setToolName("list_tables");

        Map<String, Object> args = new HashMap<>();
        args.put("database", "my_db");
        args.put("limit", 10);
        request.setArguments(args);

        assertThat(request.getToolName()).isEqualTo("list_tables");
        assertThat(request.getArguments()).hasSize(2);
        assertThat(request.getArguments().get("database")).isEqualTo("my_db");
        assertThat(request.getArguments().get("limit")).isEqualTo(10);
    }

    @Test
    void testMCPResponseSuccess() {
        Object data = Map.of("tables", Arrays.asList("table1", "table2"));
        MCPResponse response = MCPResponse.success(data);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getData()).isNotNull();
        assertThat(response.getMessage()).isNull();
        assertThat(response.getErrors()).isNull();
    }

    @Test
    void testMCPResponseError() {
        MCPResponse response = MCPResponse.error("Something went wrong",
                Arrays.asList("Check database connection", "Verify permissions"));

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getMessage()).isEqualTo("Something went wrong");
        assertThat(response.getSuggestions()).hasSize(2);
        assertThat(response.getData()).isNull();
    }

    @Test
    void testMCPToolResultSuccess() {
        MCPToolResult result = MCPToolResult.builder()
                .success(true)
                .data(Map.of("count", 5))
                .message("Operation completed")
                .executionTimeMs(100L)
                .build();

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getMessage()).isEqualTo("Operation completed");
        assertThat(result.getExecutionTimeMs()).isEqualTo(100L);
    }

    @Test
    void testMCPToolResultError() {
        MCPToolResult result = MCPToolResult.builder()
                .success(false)
                .message("Operation failed")
                .data(null)
                .executionTimeMs(50L)
                .build();

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getMessage()).isEqualTo("Operation failed");
        assertThat(result.getData()).isNull();
        assertThat(result.getExecutionTimeMs()).isEqualTo(50L);
    }
}

