package com.datalake.domain.table;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

/**
 * Statistics and health information for an Iceberg table
 */
@Data
@Builder
public class TableStats {
    /**
     * Full table name (database.table)
     */
    private String tableName;

    /**
     * Total number of rows in the table
     */
    private long rowCount;

    /**
     * Total number of data files
     */
    private long fileCount;

    /**
     * Total size of all data files in bytes
     */
    private long totalSizeBytes;

    /**
     * Average file size in bytes
     */
    private long avgFileSizeBytes;

    /**
     * Number of snapshots
     */
    private long snapshotCount;

    /**
     * Number of small files (< 100MB)
     */
    private long smallFileCount;

    /**
     * Health status: HEALTHY, NEEDS_COMPACTION, NEEDS_SNAPSHOT_EXPIRATION, etc.
     */
    private String healthStatus;

    /**
     * Detailed issues found (e.g., small_files, snapshot_buildup, partition_skew)
     */
    private Map<String, Object> issues;
}