package com.datalake.service.mcp.tooling.tools.maintenance;

import com.datalake.domain.mcp.MCPToolResult;
import com.datalake.service.maintenance.MaintenanceService;
import com.datalake.service.mcp.tooling.base.AbstractMCPTool;
import com.datalake.service.mcp.tooling.base.IcebergCompactionTool;
import com.datalake.service.mcp.tooling.tools.catalog.ListTablesTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * MCP Tool: Compact Table
 * Compacts small files in an Iceberg table to improve performance
 */
@Component("compact_table")
public class CompactTableTool extends AbstractMCPTool implements IcebergCompactionTool {
    private static final Logger log = LoggerFactory.getLogger(CompactTableTool.class);

    private final MaintenanceService maintenanceService;

    public CompactTableTool(MaintenanceService maintenanceService) {
        super("compact_table", "Compact small files in an Iceberg table", "maintenance");
        this.maintenanceService = maintenanceService;
    }

    @Override
    protected void validateArguments(Map<String, Object> arguments) {
        Object tableNameObj = arguments.get("table_name");
        if (tableNameObj == null) {
            tableNameObj = arguments.get("table");
        }

        if (tableNameObj == null) {
            String tableName = (tableNameObj != null) ? tableNameObj.toString() : null;
            if (tableName == null || tableName.trim().isEmpty()) {
                throw new IllegalArgumentException("Table name is required");
            }
        }
    }

    @Override
    protected MCPToolResult executeInternal(Map<String, Object> arguments) {
        String database = (String) arguments.getOrDefault("database", "default");

        Object tableNameObj = arguments.get("table_name");
        if (tableNameObj == null) {
            tableNameObj = arguments.get("table");
        }
        String tableName = (tableNameObj != null) ? tableNameObj.toString() : null;

        int parallelism = arguments.containsKey("max_concurrent_tasks")
                ? ((Number) arguments.get("max_concurrent_tasks")).intValue()
                : 5;

        log.info("compact_table - executing for table: {}.{} with parallelism: {}",
                database, tableName, parallelism);

        try {
            Map<String, Object> result = maintenanceService.compactTable(database, tableName, parallelism);

            return MCPToolResult.builder()
                    .success(true)
                    .message("Compaction completed for " + database + "." + tableName)
                    .data(result)
                    .build();
        } catch (Exception e) {
            log.error("Failed to compact table: {}.{}", database, tableName, e);
            return MCPToolResult.builder()
                    .success(false)
                    .message("Compaction failed: " + e.getMessage())
                    .build();
        }
    }
}

