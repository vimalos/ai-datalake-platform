package com.datalake.service.mcp.tooling.tools.analysis;

import com.datalake.domain.mcp.MCPToolResult;
import com.datalake.service.maintenance.MaintenanceService;
import com.datalake.service.mcp.tooling.base.AbstractMCPTool;
import com.datalake.service.mcp.tooling.base.IcebergMaintenanceTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component("get_table_stats")
public class GetTableStatsTool extends AbstractMCPTool implements IcebergMaintenanceTool {

    private static final Logger log = LoggerFactory.getLogger(GetTableStatsTool.class);

    private final MaintenanceService maintenanceService;

    public GetTableStatsTool(MaintenanceService maintenanceService) {
        super("get_table_stats", "Get statistics and health metrics for an Iceberg table", "analysis");
        this.maintenanceService = maintenanceService;
    }

    @Override
    protected void validateArguments(Map<String, Object> arguments) {
        log.debug("get_table_stats validateArguments - received arguments: {}", arguments);

        Object tableNameObj = arguments.get("table_name");
        if (tableNameObj == null) {
            tableNameObj = arguments.get("table");
        }

        log.debug("get_table_stats - tableNameObj: {} (type: {})",
                tableNameObj, tableNameObj != null ? tableNameObj.getClass().getSimpleName() : "null");

        if (tableNameObj == null || tableNameObj.toString().trim().isEmpty()) {
            log.error("get_table_stats - table_name is null or empty. Arguments keys: {}", arguments.keySet());
            throw new IllegalArgumentException("table_name is required (use 'table_name' or 'table' parameter)");
        }
    }

    @Override
    protected MCPToolResult executeInternal(Map<String, Object> arguments) {
        String database = (String) arguments.getOrDefault("database", "default");

        Object tableNameObj = arguments.get("table_name");
        if (tableNameObj == null) {
            tableNameObj = arguments.get("table");
        }
        String tableName = tableNameObj.toString();

        log.info("get_table_stats - executing for table: {}.{}", database, tableName);

        try {
            Map<String, Object> stats = maintenanceService.getTableStats(database, tableName);

            return MCPToolResult.builder()
                    .success(true)
                    .message("Retrieved statistics for " + database + "." + tableName)
                    .data(stats)
                    .build();
        } catch (Exception e) {
            log.error("Failed to get table stats for {}.{}", database, tableName, e);
            return MCPToolResult.builder()
                    .success(false)
                    .message("Failed to get statistics: " + e.getMessage())
                    .build();
        }
    }
}


