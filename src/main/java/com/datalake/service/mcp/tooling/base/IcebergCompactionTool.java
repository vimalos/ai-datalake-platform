package com.datalake.service.mcp.tooling.base;

/**
 * Specialized interface for Iceberg compaction tool
 * Extends base IcebergMaintenanceTool with compaction-specific operations
 */
public interface IcebergCompactionTool extends IcebergMaintenanceTool {

    /**
     * Get the default target file size in MB
     */
    default long getDefaultTargetFileSizeMB() {
        return 512;
    }

    /**
     * Get the default max concurrent tasks
     */
    default int getDefaultMaxConcurrentTasks() {
        return 4;
    }
}
