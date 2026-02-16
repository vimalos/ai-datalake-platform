package com.datalake.service.maintenance;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.apache.spark.sql.SparkSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Apache Iceberg Table Maintenance Service.
 *
 * This service provides automated maintenance operations for Iceberg tables to optimize
 * performance, reduce storage costs, and maintain table health over time.
 *
 * Core Maintenance Operations:
 *
 * 1. TABLE COMPACTION
 *    - Merges small files into larger, optimally-sized files
 *    - Reduces query planning overhead
 *    - Improves scan performance
 *    - Target file size: 512MB - 1GB
 *
 * 2. SNAPSHOT EXPIRATION
 *    - Removes old snapshots beyond retention period
 *    - Frees up metadata storage
 *    - Default retention: 7 days
 *    - Maintains time-travel capability within retention window
 *
 * 3. ORPHAN FILE REMOVAL
 *    - Identifies and deletes unreferenced data files
 *    - Reclaims storage from failed writes or aborted transactions
 *    - Safe operation that preserves data integrity
 *
 * 4. TABLE STATISTICS
 *    - Collects file count, size, snapshot count
 *    - Provides health indicators (small file ratio, growth rate)
 *    - Suggests maintenance actions based on heuristics
 *
 * Why Maintenance Matters:
 * - Small files degrade query performance (increased planning time)
 * - Old snapshots consume storage unnecessarily
 * - Orphan files waste S3 storage costs
 * - Regular maintenance keeps tables performant and cost-effective
 *
 * Integration with MCP Tools:
 * - compact_table: Triggers file compaction
 * - expire_snapshots: Cleans up old snapshots
 * - remove_orphan_files: Reclaims orphaned storage
 * - get_table_stats: Health check and diagnostics
 * - suggest_maintenance: AI-powered maintenance recommendations
 *
 * Spark Integration:
 * - Uses Spark SQL procedures for maintenance operations
 * - Gracefully degrades if Spark is unavailable
 */
@Service
@RequiredArgsConstructor
public class MaintenanceService {

    private static final Logger log = LoggerFactory.getLogger(MaintenanceService.class);

    private final ObjectProvider<SparkSession> sparkProvider;
    private SparkSession spark;

    /**
     * Initializes SparkSession for maintenance operations.
     * Operations gracefully degrade if Spark is unavailable.
     */
    @PostConstruct
    public void init() {
        try {
            this.spark = sparkProvider.getIfAvailable();
            if (spark != null) {
                log.info("✅ SparkSession is available - Maintenance features enabled");
            } else {
                log.debug("ℹ️ SparkSession not available - Maintenance features disabled");
            }
        } catch (Exception e) {
            log.debug("ℹ️ SparkSession initialization skipped: {}", e.getMessage());
            this.spark = null;
        }
    }

    /**
     * Checks if Spark is available for maintenance operations.
     */
    private boolean isSparkAvailable() {
        return this.spark != null;
    }

    /**
     * Returns a standard "disabled" result when Spark is unavailable.
     */
    private Map<String, Object> disabledResult(String action) {
        Map<String, Object> result = new HashMap<>();
        result.put("status", "disabled");
        result.put("timestamp", System.currentTimeMillis());
        result.put("action", action);
        result.put("message", "Spark is not available in this environment");
        return result;
    }

    /**
     * Captures a snapshot of table statistics before/after maintenance operations.
     * Used to measure the impact of maintenance activities.
     */
    private Map<String, Object> getTableStatsSnapshot(String database, String table) {
        Map<String, Object> stats = new HashMap<>();
        stats.put("database", database);
        stats.put("table", table);
        if (!isSparkAvailable()) {
            stats.put("status", "unavailable");
            return stats;
        }
        try {
            String fileStatsQuery = String.format(
                    "SELECT COUNT(*) AS file_count, COALESCE(SUM(file_size_in_bytes), 0) AS total_size_bytes " +
                            "FROM %s.%s.files",
                    database, table
            );
            String snapshotStatsQuery = String.format(
                    "SELECT COUNT(*) AS snapshot_count FROM %s.%s.snapshots",
                    database, table
            );

            long fileCount = spark.sql(fileStatsQuery).collectAsList().get(0).getLong(0);
            long totalSizeBytes = spark.sql(fileStatsQuery).collectAsList().get(0).getLong(1);
            long snapshotCount = spark.sql(snapshotStatsQuery).collectAsList().get(0).getLong(0);

            stats.put("fileCount", fileCount);
            stats.put("totalSizeBytes", totalSizeBytes);
            stats.put("totalSizeMB", bytesToMB(totalSizeBytes));
            stats.put("snapshotCount", snapshotCount);
            stats.put("status", "ok");
        } catch (Exception e) {
            stats.put("status", "error");
            stats.put("error", e.getMessage());
        }
        return stats;
    }

    private Map<String, Object> calculateDelta(Map<String, Object> before, Map<String, Object> after) {
        Map<String, Object> delta = new HashMap<>();
        if (before == null || after == null) return delta;
        try {
            long beforeFiles = (long) before.getOrDefault("fileCount", 0L);
            long afterFiles = (long) after.getOrDefault("fileCount", 0L);
            long beforeSnapshots = (long) before.getOrDefault("snapshotCount", 0L);
            long afterSnapshots = (long) after.getOrDefault("snapshotCount", 0L);
            long beforeBytes = (long) before.getOrDefault("totalSizeBytes", 0L);
            long afterBytes = (long) after.getOrDefault("totalSizeBytes", 0L);

            delta.put("fileCountDelta", afterFiles - beforeFiles);
            delta.put("snapshotCountDelta", afterSnapshots - beforeSnapshots);
            delta.put("sizeBytesDelta", afterBytes - beforeBytes);
            delta.put("sizeMBDelta", bytesToMB(afterBytes - beforeBytes));
        } catch (Exception e) {
            delta.put("error", e.getMessage());
        }
        return delta;
    }

    private double bytesToMB(long bytes) {
        return Math.round((bytes / (1024.0 * 1024.0)) * 100.0) / 100.0;
    }

    /**
     * Compact table by rewriting small files
     */
    public Map<String, Object> compactTable(String database, String table, int minInputFiles) {
        Map<String, Object> result = new HashMap<>();
        result.put("status", "started");
        result.put("timestamp", System.currentTimeMillis());
        result.put("action", "compact");

        if (!isSparkAvailable()) {
            log.warn("SparkSession not available - compactTable is a no-op in this environment");
            return disabledResult("compact");
        }

        Map<String, Object> before = getTableStatsSnapshot(database, table);

        try {
            String query = String.format(
                    "CALL spark_catalog.system.rewrite_data_files(" +
                            "  table => '%s.%s'," +
                            "  strategy => 'binpack'," +
                            "  options => map('min-input-files', '%d', 'target-file-size-bytes', '524288000')" +
                            ")",
                    database, table, minInputFiles
            );

            log.info("Executing compaction: {}", query);
            spark.sql(query);

            Map<String, Object> after = getTableStatsSnapshot(database, table);
            result.put("before", before);
            result.put("after", after);
            result.put("delta", calculateDelta(before, after));
            result.put("summary", String.format(
                    "Compaction completed for %s.%s (fileCount: %s -> %s, sizeMB: %s -> %s)",
                    database,
                    table,
                    before.get("fileCount"),
                    after.get("fileCount"),
                    before.get("totalSizeMB"),
                    after.get("totalSizeMB")
            ));
            result.put("status", "completed");
            result.put("message", "Table compaction completed successfully");
            log.info("Compaction completed for {}.{}", database, table);

        } catch (Exception e) {
            log.error("Compaction failed for {}.{}", database, table, e);
            result.put("status", "failed");
            result.put("error", e.getMessage());
        }

        return result;
    }

    /**
     * Expire old snapshots to reduce metadata
     */
    public Map<String, Object> expireSnapshots(String database, String table, int retentionDays) {
        if (!isSparkAvailable()) {
            log.warn("SparkSession not available - expireSnapshots is a no-op in this environment");
            return disabledResult("expire_snapshots");
        }

        Map<String, Object> result = new HashMap<>();
        result.put("status", "started");
        result.put("timestamp", System.currentTimeMillis());
        result.put("action", "expire_snapshots");

        Map<String, Object> before = getTableStatsSnapshot(database, table);

        try {
            String query = String.format(
                    "CALL spark_catalog.system.expire_snapshots(" +
                            "  table => '%s.%s'," +
                            "  retain_last => 2," +
                            "  older_than => TIMESTAMP '%s'" +
                            ")",
                    database,
                    table,
                    calculateOlderThanTimestamp(retentionDays)
            );

            log.info("Executing snapshot expiration: {}", query);
            spark.sql(query);

            Map<String, Object> after = getTableStatsSnapshot(database, table);
            result.put("before", before);
            result.put("after", after);
            result.put("delta", calculateDelta(before, after));
            result.put("summary", String.format(
                    "Expired snapshots for %s.%s (retention: %d days, snapshots: %s -> %s)",
                    database,
                    table,
                    retentionDays,
                    before.get("snapshotCount"),
                    after.get("snapshotCount")
            ));
            result.put("status", "completed");
            result.put("message", "Snapshots expired successfully");
            log.info("Snapshots expired for {}.{} (retention: {} days)", database, table, retentionDays);

        } catch (Exception e) {
            log.error("Snapshot expiration failed for {}.{}", database, table, e);
            result.put("status", "failed");
            result.put("error", e.getMessage());
        }

        return result;
    }

    /**
     * Remove orphaned files that are not referenced by any snapshot
     */
    public Map<String, Object> removeOrphans(String database, String table) {
        if (!isSparkAvailable()) {
            log.warn("SparkSession not available - removeOrphans is a no-op in this environment");
            return disabledResult("remove_orphan_files");
        }

        Map<String, Object> result = new HashMap<>();
        result.put("status", "started");
        result.put("timestamp", System.currentTimeMillis());
        result.put("action", "remove_orphan_files");

        Map<String, Object> before = getTableStatsSnapshot(database, table);

        try {
            String query = String.format(
                    "CALL spark_catalog.system.remove_orphan_files(" +
                            "  table => '%s.%s'" +
                            ")",
                    database, table
            );

            log.info("Executing orphan removal: {}", query);
            spark.sql(query);

            Map<String, Object> after = getTableStatsSnapshot(database, table);
            result.put("before", before);
            result.put("after", after);
            result.put("delta", calculateDelta(before, after));
            result.put("summary", String.format(
                    "Removed orphan files for %s.%s (fileCount: %s -> %s, sizeMB: %s -> %s)",
                    database,
                    table,
                    before.get("fileCount"),
                    after.get("fileCount"),
                    before.get("totalSizeMB"),
                    after.get("totalSizeMB")
            ));
            result.put("status", "completed");
            result.put("message", "Orphaned files removed successfully");
            log.info("Orphaned files removed from {}.{}", database, table);

        } catch (Exception e) {
            log.error("Orphan removal failed for {}.{}", database, table, e);
            result.put("status", "failed");
            result.put("error", e.getMessage());
        }

        return result;
    }

    /**
     * Rewrite manifest files for better performance
     */
    public Map<String, Object> rewriteManifests(String database, String table) {
        if (!isSparkAvailable()) {
            log.warn("SparkSession not available - rewriteManifests is a no-op in this environment");
            return disabledResult("rewrite_manifests");
        }

        Map<String, Object> result = new HashMap<>();
        result.put("status", "started");
        result.put("timestamp", System.currentTimeMillis());
        result.put("action", "rewrite_manifests");

        try {
            String query = String.format(
                    "CALL spark_catalog.system.rewrite_manifests(" +
                            "  table => '%s.%s'" +
                            ")",
                    database, table
            );

            log.info("Executing manifest rewrite: {}", query);
            spark.sql(query);

            result.put("status", "completed");
            result.put("message", "Manifests rewritten successfully");
            log.info("Manifests rewritten for {}.{}", database, table);

        } catch (Exception e) {
            log.error("Manifest rewrite failed for {}.{}", database, table, e);
            result.put("status", "failed");
            result.put("error", e.getMessage());
        }

        return result;
    }

    /**
     * Compute and update table statistics
     */
    public Map<String, Object> analyzeTable(String database, String table) {
        if (!isSparkAvailable()) {
            log.warn("SparkSession not available - analyzeTable is a no-op in this environment");
            return disabledResult("analyze");
        }

        Map<String, Object> result = new HashMap<>();
        result.put("status", "started");
        result.put("timestamp", System.currentTimeMillis());
        result.put("action", "analyze");

        try {
            String query = String.format(
                    "ANALYZE TABLE %s.%s COMPUTE STATISTICS FOR ALL COLUMNS",
                    database, table
            );

            log.info("Analyzing table: {}", query);
            spark.sql(query);

            result.put("status", "completed");
            result.put("message", "Table statistics computed");
            log.info("Table analysis completed for {}.{}", database, table);

        } catch (Exception e) {
            log.error("Table analysis failed for {}.{}", database, table, e);
            result.put("status", "failed");
            result.put("error", e.getMessage());
        }

        return result;
    }

    /**
     * Run full maintenance procedure (compact, analyze, expire, orphans)
     */
    public Map<String, Object> runFullMaintenance(String database, String table) {
        Map<String, Object> result = new HashMap<>();
        List<Map<String, Object>> procedures = new ArrayList<>();

        result.put("status", "started");
        result.put("timestamp", System.currentTimeMillis());

        Map<String, Object> before = getTableStatsSnapshot(database, table);

        try {
            // 1. Analyze
            procedures.add(analyzeTable(database, table));

            // 2. Compact
            procedures.add(compactTable(database, table, 5));

            // 3. Rewrite manifests
            procedures.add(rewriteManifests(database, table));

            // 4. Expire old snapshots
            procedures.add(expireSnapshots(database, table, 30));

            // 5. Remove orphans
            procedures.add(removeOrphans(database, table));

            result.put("procedures", procedures);
            result.put("status", "completed");
            Map<String, Object> after = getTableStatsSnapshot(database, table);
            result.put("before", before);
            result.put("after", after);
            result.put("delta", calculateDelta(before, after));
            result.put("summary", String.format(
                    "Full maintenance for %s.%s (fileCount: %s -> %s, sizeMB: %s -> %s, snapshots: %s -> %s)",
                    database,
                    table,
                    before.get("fileCount"),
                    after.get("fileCount"),
                    before.get("totalSizeMB"),
                    after.get("totalSizeMB"),
                    before.get("snapshotCount"),
                    after.get("snapshotCount")
            ));
            result.put("message", "Full maintenance completed");

        } catch (Exception e) {
            log.error("Full maintenance failed for {}.{}", database, table, e);
            result.put("status", "failed");
            result.put("error", e.getMessage());
            result.put("procedures", procedures);
        }

        return result;
    }

    /**
     * Get detailed statistics about an Iceberg table
     */
    public Map<String, Object> getTableStats(String database, String tableName) {
        if (!isSparkAvailable()) {
            log.warn("SparkSession not available - getTableStats is a no-op in this environment");
            return disabledResult("get_table_stats");
        }

        Map<String, Object> result = new HashMap<>();
        result.put("status", "started");
        result.put("timestamp", System.currentTimeMillis());
        result.put("action", "get_table_stats");
        result.put("table", database + "." + tableName);

        try {
            String fullTableName = database + "." + tableName;

            // Get snapshot count
            long snapshotCount = 0;
            try {
                snapshotCount = spark.sql(String.format(
                        "SELECT COUNT(*) as count FROM %s.snapshots", fullTableName
                )).first().getLong(0);
            } catch (Exception e) {
                log.warn("Could not get snapshot count: {}", e.getMessage());
            }

            // Get file statistics
            long fileCount = 0;
            long totalSizeBytes = 0;
            long smallFileCount = 0;
            long avgFileSizeBytes = 0;

            try {
                // Get file count
                fileCount = spark.sql(String.format(
                        "SELECT COUNT(*) as count FROM %s.files", fullTableName
                )).first().getLong(0);

                if (fileCount > 0) {
                    // Get total size
                    var sizeRow = spark.sql(String.format(
                            "SELECT COALESCE(SUM(file_size_in_bytes), 0) as total_size FROM %s.files",
                            fullTableName
                    )).first();
                    totalSizeBytes = sizeRow.getLong(0);
                    avgFileSizeBytes = totalSizeBytes / fileCount;

                    // Count small files (< 100MB)
                    long smallFileThreshold = 100L * 1024 * 1024; // 100MB
                    smallFileCount = spark.sql(String.format(
                            "SELECT COUNT(*) as count FROM %s.files WHERE file_size_in_bytes < %d",
                            fullTableName, smallFileThreshold
                    )).first().getLong(0);
                }
            } catch (Exception e) {
                log.warn("Could not get file statistics: {}", e.getMessage());
            }

            // Get row count
            long rowCount = 0;
            try {
                rowCount = spark.table(fullTableName).count();
            } catch (Exception e) {
                log.warn("Could not get row count: {}", e.getMessage());
            }

            // Determine health status
            String healthStatus = "HEALTHY";
            List<String> recommendations = new ArrayList<>();

            if (fileCount > 0 && smallFileCount > fileCount * 0.5) {
                healthStatus = "NEEDS_COMPACTION";
                recommendations.add("Run compaction to merge small files");
            }
            if (snapshotCount > 100) {
                if (!"NEEDS_COMPACTION".equals(healthStatus)) {
                    healthStatus = "NEEDS_SNAPSHOT_EXPIRATION";
                }
                recommendations.add("Run expire_snapshots to clean up old metadata");
            }

            // Build issues map
            Map<String, Object> issues = new HashMap<>();
            if (smallFileCount > 0) {
                Map<String, Object> smallFilesIssue = new HashMap<>();
                smallFilesIssue.put("count", smallFileCount);
                smallFilesIssue.put("percentage", String.format("%.1f%%",
                        (smallFileCount * 100.0) / Math.max(fileCount, 1)));
                issues.put("small_files", smallFilesIssue);
            }
            if (snapshotCount > 50) {
                Map<String, Object> snapshotIssue = new HashMap<>();
                snapshotIssue.put("count", snapshotCount);
                snapshotIssue.put("recommendation", "Consider running expire_snapshots");
                issues.put("snapshot_buildup", snapshotIssue);
            }

            // Build result
            Map<String, Object> stats = new HashMap<>();
            stats.put("tableName", fullTableName);
            stats.put("rowCount", rowCount);
            stats.put("fileCount", fileCount);
            stats.put("totalSizeBytes", totalSizeBytes);
            stats.put("totalSizeMB", totalSizeBytes / (1024.0 * 1024.0));
            stats.put("avgFileSizeBytes", avgFileSizeBytes);
            stats.put("avgFileSizeMB", avgFileSizeBytes / (1024.0 * 1024.0));
            stats.put("snapshotCount", snapshotCount);
            stats.put("smallFileCount", smallFileCount);
            stats.put("healthStatus", healthStatus);
            stats.put("issues", issues);
            stats.put("recommendations", recommendations);

            result.put("status", "completed");
            result.put("stats", stats);
            result.put("message", "Table statistics retrieved successfully");

            log.info("Retrieved statistics for {}.{}: {} rows, {} files, {} snapshots, health: {}",
                    database, tableName, rowCount, fileCount, snapshotCount, healthStatus);

        } catch (Exception e) {
            log.error("Failed to get table statistics for {}.{}", database, tableName, e);
            result.put("status", "failed");
            result.put("error", e.getMessage());
        }

        return result;
    }

    private String calculateOlderThanTimestamp(int retentionDays) {
        LocalDateTime cutoffDate = LocalDateTime.now().minusDays(retentionDays);
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        return cutoffDate.format(formatter);
    }
}
