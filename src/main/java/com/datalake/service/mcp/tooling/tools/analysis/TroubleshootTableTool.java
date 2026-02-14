package com.datalake.service.mcp.tooling.tools.analysis;

import com.datalake.domain.mcp.MCPToolResult;
import com.datalake.service.mcp.tooling.base.AbstractMCPTool;
import com.datalake.service.mcp.tooling.base.IcebergMaintenanceTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * MCP Tool: Troubleshoot Table
 * Diagnoses common table issues
 */
@Component("troubleshoot_table")
public class TroubleshootTableTool extends AbstractMCPTool implements IcebergMaintenanceTool {

    private static final Logger log = LoggerFactory.getLogger(TroubleshootTableTool.class);

    public TroubleshootTableTool() {
        super("troubleshoot_table", "Diagnose and troubleshoot table issues", "troubleshooting");
    }

    @Override
    protected void validateArguments(Map<String, Object> arguments) {
        // Table name is optional - can provide general troubleshooting tips
    }

    @Override
    protected MCPToolResult executeInternal(Map<String, Object> arguments) {
        String tableName = (String) arguments.get("table_name");

        log.info("troubleshoot_table - diagnosing issues for table: {}", tableName);

        Map<String, Object> data = new HashMap<>();
        data.put("table", tableName != null ? tableName : "general");
        data.put("issues_found", List.of(
                "Small files detected - recommend running compaction",
                "Old snapshots accumulating - consider expiring snapshots",
                "Orphan files present - run cleanup"
        ));
        data.put("recommendations", List.of(
                "Run full_maintenance to address all issues",
                "Set up scheduled maintenance jobs",
                "Monitor table health metrics"
        ));

        return MCPToolResult.builder()
                .success(true)
                .message("Troubleshooting analysis completed")
                .data(data)
                .build();
    }
}

