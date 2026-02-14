package com.datalake.service.mcp.tooling.tools.catalog;

import com.datalake.domain.mcp.MCPToolResult;
import com.datalake.service.iceberg.IcebergCatalogService;
import com.datalake.service.mcp.tooling.base.AbstractMCPTool;
import com.datalake.service.mcp.tooling.base.IcebergMaintenanceTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * MCP Tool: List Tables
 * Lists all Iceberg tables in a database
 */
@Component("list_tables")
public class ListTablesTool extends AbstractMCPTool implements IcebergMaintenanceTool {
    private static final Logger log = LoggerFactory.getLogger(ListTablesTool.class);

    private final IcebergCatalogService catalogService;

    public ListTablesTool(IcebergCatalogService catalogService) {
        super("list_tables", "List all Iceberg tables in catalog or database", "discovery");
        this.catalogService = catalogService;
    }

    @Override
    protected void validateArguments(Map<String, Object> arguments) {
        log.debug("list_tables validateArguments - arguments: {}", arguments);
        // database is optional - if not provided, use default
    }

    @Override
    protected MCPToolResult executeInternal(Map<String, Object> arguments) {
        String database = (String) arguments.getOrDefault("database", "default");
        log.info("list_tables - executing with database: {}", database);

        try {
            List<String> tables = catalogService.listTablesInDatabase(database);

            Map<String, Object> data = new HashMap<>();
            data.put("database", database);
            data.put("tables", tables);
            data.put("count", tables.size());

            return MCPToolResult.builder()
                    .success(true)
                    .message("Retrieved " + tables.size() + " tables from database: " + database)
                    .data(data)
                    .build();
        } catch (Exception e) {
            log.error("Failed to list tables in database: {}", database, e);
            return MCPToolResult.builder()
                    .success(false)
                    .message("Failed to list tables: " + e.getMessage())
                    .build();
        }
    }
}

