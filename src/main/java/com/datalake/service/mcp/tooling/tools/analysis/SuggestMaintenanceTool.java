package com.datalake.service.mcp.tooling.tools.analysis;

import com.datalake.domain.mcp.MCPToolResult;
import com.datalake.service.llm.LLMService;
import com.datalake.service.maintenance.MaintenanceService;
import com.datalake.service.mcp.tooling.base.AbstractMCPTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * MCP Tool: AI-Powered Maintenance Suggestion
 *
 * Analyzes table statistics and provides intelligent recommendations on:
 * - Whether compaction is needed
 * - Whether snapshot expiration is needed
 * - Whether orphan file cleanup is needed
 * - Estimated impact and priority
 */
@Component("suggest_maintenance")
public class SuggestMaintenanceTool extends AbstractMCPTool {

    private static final Logger log = LoggerFactory.getLogger(SuggestMaintenanceTool.class);
    private final MaintenanceService maintenanceService;
    private final LLMService llmService;

    // Thresholds for maintenance recommendations
    private static final int SMALL_FILE_THRESHOLD = 100; // MB
    private static final int SMALL_FILE_COUNT_THRESHOLD = 100;
    private static final int SNAPSHOT_COUNT_THRESHOLD = 50;
    private static final double SMALL_FILE_RATIO_THRESHOLD = 0.3; // 30%

    public SuggestMaintenanceTool(MaintenanceService maintenanceService, LLMService llmService) {
        super(
                "suggest_maintenance",
                "Analyze table health and suggest maintenance operations (compaction, snapshot cleanup, etc.)",
                "analysis"
        );
        this.maintenanceService = maintenanceService;
        this.llmService = llmService;
    }

    @Override
    protected void validateArguments(Map<String, Object> arguments) {
        Object tableNameObj = arguments.get("table_name");
        if (tableNameObj == null) {
            tableNameObj = arguments.get("table");
        }

        if (tableNameObj == null || String.valueOf(tableNameObj).trim().isEmpty()) {
            throw new IllegalArgumentException("table_name is required");
        }
    }

    @Override
    protected MCPToolResult executeInternal(Map<String, Object> arguments) {
        long startTime = System.currentTimeMillis();

        String database = String.valueOf(arguments.get("database"));
        Object tableNameObj = arguments.get("table_name");
        if (tableNameObj == null) {
            tableNameObj = arguments.get("table");
        }
        String tableName = String.valueOf(tableNameObj);
        String fullTableName = database + "." + tableName;

        log.info("🔍 Analyzing maintenance needs for table: {}", fullTableName);

        try {
            // Get table statistics
            Map<String, Object> statsResult = maintenanceService.getTableStats(database, tableName);

            // Extract the actual stats from the result
            @SuppressWarnings("unchecked")
            Map<String, Object> stats = (Map<String, Object>) statsResult.get("stats");
            if (stats == null) {
                stats = new HashMap<>();
            }

            // Analyze and generate recommendations
            List<Map<String, Object>> recommendations = analyzeMaintenanceNeeds(stats);

            // Use LLM to provide natural language explanation
            String aiExplanation = generateAIExplanation(fullTableName, stats, recommendations);

            Map<String, Object> result = new HashMap<>();
            result.put("table", fullTableName);
            result.put("statistics", stats);
            result.put("recommendations", recommendations);
            result.put("ai_explanation", aiExplanation);
            result.put("maintenance_required", !recommendations.isEmpty());

            long executionTime = System.currentTimeMillis() - startTime;
            log.info("✅ Maintenance analysis complete for {}: {} recommendation(s)", fullTableName, recommendations.size());

            return MCPToolResult.builder()
                    .success(true)
                    .data(result)
                    .message(String.format("Found %d maintenance recommendation(s)", recommendations.size()))
                    .executionTimeMs(executionTime)
                    .build();

        } catch (Exception e) {
            long executionTime = System.currentTimeMillis() - startTime;
            log.error("❌ Failed to analyze maintenance needs for {}", fullTableName, e);
            return MCPToolResult.builder()
                    .success(false)
                    .message("Failed to analyze table: " + e.getMessage())
                    .executionTimeMs(executionTime)
                    .build();
        }
    }

    /**
     * Analyze table statistics and generate recommendations
     */
    private List<Map<String, Object>> analyzeMaintenanceNeeds(Map<String, Object> stats) {
        List<Map<String, Object>> recommendations = new ArrayList<>();

        int fileCount = getIntStat(stats, "fileCount");
        int snapshotCount = getIntStat(stats, "snapshotCount");
        int smallFileCount = getIntStat(stats, "smallFileCount");
        long totalSizeBytes = getLongStat(stats, "totalSizeBytes");

        // Recommendation 1: Compaction (many small files)
        if (smallFileCount > SMALL_FILE_COUNT_THRESHOLD) {
            double smallFileRatio = fileCount > 0 ? (double) smallFileCount / fileCount : 0;
            if (smallFileRatio > SMALL_FILE_RATIO_THRESHOLD) {
                recommendations.add(Map.of(
                        "operation", "compact_table",
                        "priority", "HIGH",
                        "reason", String.format("Table has %d small files (%.1f%% of total files)",
                                smallFileCount, smallFileRatio * 100),
                        "expected_benefit", "Improved query performance, reduced I/O overhead",
                        "estimated_savings", String.format("%.1f%% file reduction expected", smallFileRatio * 100 * 0.7)
                ));
            }
        }

        // Recommendation 2: Snapshot expiration (too many snapshots)
        if (snapshotCount > SNAPSHOT_COUNT_THRESHOLD) {
            recommendations.add(Map.of(
                    "operation", "expire_snapshots",
                    "priority", "MEDIUM",
                    "reason", String.format("Table has %d snapshots (threshold: %d)",
                            snapshotCount, SNAPSHOT_COUNT_THRESHOLD),
                    "expected_benefit", "Reduced metadata overhead, faster planning",
                    "estimated_savings", String.format("~%d old snapshots can be removed",
                            snapshotCount - SNAPSHOT_COUNT_THRESHOLD)
            ));
        }

        // Recommendation 3: Orphan file cleanup (if table is large and old)
        if (fileCount > 500 && snapshotCount > 20) {
            recommendations.add(Map.of(
                    "operation", "remove_orphan_files",
                    "priority", "LOW",
                    "reason", "Large table with many historical operations may have orphan files",
                    "expected_benefit", "Storage cost reduction",
                    "estimated_savings", "Actual savings depends on orphan file count"
            ));
        }

        return recommendations;
    }

    /**
     * Use LLM to generate natural language explanation
     */
    private String generateAIExplanation(String tableName, Map<String, Object> stats, List<Map<String, Object>> recommendations) {
        if (recommendations.isEmpty()) {
            return String.format("✅ Table '%s' is in good health. No maintenance required at this time.", tableName);
        }

        try {
            String prompt = String.format("""
                    You are a data lakehouse expert analyzing an Apache Iceberg table.
                    
                    Table: %s
                    
                    Statistics:
                    - Total files: %d
                    - Small files: %d
                    - Snapshots: %d
                    - Total size: %.2f MB
                    - Row count: %d
                    
                    Recommendations:
                    %s
                    
                    Provide a brief (2-3 sentences), actionable explanation of what maintenance is needed and why.
                    Be conversational and helpful. Focus on the business impact.
                    """,
                    tableName,
                    getIntStat(stats, "fileCount"),
                    getIntStat(stats, "smallFileCount"),
                    getIntStat(stats, "snapshotCount"),
                    getLongStat(stats, "totalSizeBytes") / (1024.0 * 1024.0),
                    getLongStat(stats, "rowCount"),
                    formatRecommendationsForLLM(recommendations)
            );

            return llmService.complete(prompt);

        } catch (Exception e) {
            log.warn("Failed to generate AI explanation, using default", e);
            return String.format("⚠️ Table '%s' needs maintenance. %d operation(s) recommended.",
                    tableName, recommendations.size());
        }
    }

    private String formatRecommendationsForLLM(List<Map<String, Object>> recommendations) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < recommendations.size(); i++) {
            Map<String, Object> rec = recommendations.get(i);
            sb.append(String.format("%d. %s (%s priority): %s\n",
                    i + 1,
                    rec.get("operation"),
                    rec.get("priority"),
                    rec.get("reason")
            ));
        }
        return sb.toString();
    }

    private int getIntStat(Map<String, Object> stats, String key) {
        Object value = stats.get(key);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return 0;
    }

    private long getLongStat(Map<String, Object> stats, String key) {
        Object value = stats.get(key);
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        return 0L;
    }
}


