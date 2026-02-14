package com.datalake.service.mcp.tooling.tools.catalog;

import com.datalake.domain.mcp.MCPToolResult;
import com.datalake.service.iceberg.IcebergCatalogService;
import com.datalake.service.mcp.tooling.base.AbstractMCPTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * MCP Tool: List all databases in the Iceberg catalog
 */
@Component("list_databases")
public class ListDatabasesTool extends AbstractMCPTool {

    private static final Logger log = LoggerFactory.getLogger(ListDatabasesTool.class);
    private final IcebergCatalogService icebergCatalogService;

    public ListDatabasesTool(IcebergCatalogService icebergCatalogService) {
        super(
                "list_databases",
                "List all databases/namespaces in the Iceberg catalog",
                "catalog"
        );
        this.icebergCatalogService = icebergCatalogService;
    }

    @Override
    protected void validateArguments(Map<String, Object> arguments) {
        // No arguments required
        log.debug("list_databases validateArguments - no validation needed");
    }

    @Override
    protected MCPToolResult executeInternal(Map<String, Object> arguments) {
        long startTime = System.currentTimeMillis();
        log.info("list_databases - executing to list all databases");

        try {
            List<String> databases = icebergCatalogService.listDatabases();

            Map<String, Object> result = new HashMap<>();
            result.put("databases", databases);
            result.put("count", databases.size());

            long executionTime = System.currentTimeMillis() - startTime;
            log.info("✅ Found {} databases", databases.size());

            return MCPToolResult.builder()
                    .success(true)
                    .data(result)
                    .message(String.format("Retrieved %d databases from catalog", databases.size()))
                    .executionTimeMs(executionTime)
                    .build();

        } catch (Exception e) {
            long executionTime = System.currentTimeMillis() - startTime;
            log.error("❌ Failed to list databases", e);
            return MCPToolResult.builder()
                    .success(false)
                    .message("Failed to list databases: " + e.getMessage())
                    .executionTimeMs(executionTime)
                    .build();
        }
    }
}


