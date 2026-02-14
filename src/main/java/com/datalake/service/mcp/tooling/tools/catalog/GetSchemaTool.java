package com.datalake.service.mcp.tooling.tools.catalog;

import com.datalake.domain.mcp.MCPToolResult;
import com.datalake.service.iceberg.IcebergCatalogService;
import com.datalake.service.mcp.tooling.base.AbstractMCPTool;
import com.datalake.service.mcp.tooling.base.IcebergMaintenanceTool;
import com.datalake.service.mcp.tooling.tools.analysis.TroubleshootTableTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * MCP Tool: Get Schema
 * Retrieves the schema of an Iceberg table
 */
@Component("get_schema")
public class GetSchemaTool extends AbstractMCPTool implements IcebergMaintenanceTool {
    private static final Logger log = LoggerFactory.getLogger(GetSchemaTool.class);

    private final IcebergCatalogService catalogService;

    public GetSchemaTool(IcebergCatalogService catalogService) {
        super("get_schema", "Get the schema of an Iceberg table", "discovery");
        this.catalogService = catalogService;
    }

    @Override
    protected void validateArguments(Map<String, Object> arguments) {
        String tableName = (String) arguments.get("table_name");
        if (tableName == null || tableName.trim().isEmpty()) {
            throw new IllegalArgumentException("table_name is required");
        }
    }

    @Override
    protected MCPToolResult executeInternal(Map<String, Object> arguments) {
        String database = (String) arguments.getOrDefault("database", "default");
        String tableName = (String) arguments.get("table_name");

        log.info("get_schema - executing for table: {}.{}", database, tableName);

        try {
            Map<String, Object> schema = catalogService.getTableSchema(database, tableName);

            return MCPToolResult.builder()
                    .success(true)
                    .message("Retrieved schema for table: " + database + "." + tableName)
                    .data(schema)
                    .build();
        } catch (Exception e) {
            log.error("Failed to get schema for table: {}.{}", database, tableName, e);
            return MCPToolResult.builder()
                    .success(false)
                    .message("Failed to get schema: " + e.getMessage())
                    .build();
        }
    }
}
