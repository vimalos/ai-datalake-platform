package com.datalake.service.mcp.tooling.tools.maintenance;

import com.datalake.domain.mcp.MCPToolResult;
import com.datalake.service.maintenance.MaintenanceService;
import com.datalake.service.mcp.tooling.base.AbstractMCPTool;
import com.datalake.service.mcp.tooling.base.IcebergMaintenanceTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component("remove_orphan_files")
public class RemoveOrphanFilesTool extends AbstractMCPTool implements IcebergMaintenanceTool {
    private static final Logger log = LoggerFactory.getLogger(RemoveOrphanFilesTool.class);

    private final MaintenanceService maintenanceService;

    public RemoveOrphanFilesTool(MaintenanceService maintenanceService) {
        super("remove_orphan_files", "Remove files not referenced by any snapshot", "maintenance");
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
            Map<String, Object> result = maintenanceService.removeOrphans(database, tableName);
            return MCPToolResult.builder()
                    .success(true)
                    .message("Removed orphan files from " + database + "." + tableName)
                    .data(result)
                    .build();
        } catch (Exception e) {
            log.error("Failed to remove orphan files", e);
            return MCPToolResult.builder()
                    .success(false)
                    .message("Failed: " + e.getMessage())
                    .build();
        }
    }
}


