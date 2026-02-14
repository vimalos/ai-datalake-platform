package com.datalake.service.mcp.tooling.tools.maintenance;

import com.datalake.domain.mcp.MCPToolResult;
import com.datalake.service.maintenance.MaintenanceService;
import com.datalake.service.mcp.tooling.base.AbstractMCPTool;
import com.datalake.service.mcp.tooling.base.IcebergMaintenanceTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component("full_maintenance")
public class FullMaintenanceTool extends AbstractMCPTool implements IcebergMaintenanceTool {
    private static final Logger log = LoggerFactory.getLogger(FullMaintenanceTool.class);

    private final MaintenanceService maintenanceService;

    public FullMaintenanceTool(MaintenanceService maintenanceService) {
        super("full_maintenance", "Run complete maintenance on table (compact, expire, cleanup)", "maintenance");
        this.maintenanceService = maintenanceService;
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

        try {
            Map<String, Object> result = maintenanceService.runFullMaintenance(database, tableName);
            return MCPToolResult.builder().success(true).message("Full maintenance completed for " + database + "." + tableName).data(result).build();
        } catch (Exception e) {
            log.error("Failed to run full maintenance", e);
            return MCPToolResult.builder().success(false).message("Failed: " + e.getMessage()).build();
        }
    }
}

