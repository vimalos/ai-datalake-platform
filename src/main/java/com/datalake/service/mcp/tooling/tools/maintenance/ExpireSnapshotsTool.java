package com.datalake.service.mcp.tooling.tools.maintenance;

import com.datalake.domain.mcp.MCPToolResult;
import com.datalake.service.maintenance.MaintenanceService;
import com.datalake.service.mcp.tooling.base.AbstractMCPTool;
import com.datalake.service.mcp.tooling.base.IcebergMaintenanceTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component("expire_snapshots")
public class ExpireSnapshotsTool extends AbstractMCPTool implements IcebergMaintenanceTool {
    private static final Logger log = LoggerFactory.getLogger(ExpireSnapshotsTool.class);

    private final MaintenanceService maintenanceService;

    public ExpireSnapshotsTool(MaintenanceService maintenanceService) {
        super("expire_snapshots", "Remove old snapshots from an Iceberg table", "maintenance");
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
        int olderThanDays = arguments.containsKey("older_than_days")
                ? ((Number) arguments.get("older_than_days")).intValue()
                : 7;

        try {
            Map<String, Object> result = maintenanceService.expireSnapshots(database, tableName, olderThanDays);
            Map<String, Object> payload = new java.util.LinkedHashMap<>();
            payload.putAll(result);
            payload.put("summary", String.format(
                    "Expired snapshots for %s.%s (retention: %d days)",
                    database,
                    tableName,
                    olderThanDays
            ));
            return MCPToolResult.builder()
                    .success(true)
                    .message("Expired snapshots for " + database + "." + tableName)
                    .data(payload)
                    .build();
        } catch (Exception e) {
            log.error("Failed to expire snapshots", e);
            return MCPToolResult.builder()
                    .success(false)
                    .message("Failed: " + e.getMessage())
                    .build();
        }
    }
}
