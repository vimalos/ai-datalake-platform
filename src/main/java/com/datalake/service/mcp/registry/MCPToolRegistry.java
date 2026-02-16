package com.datalake.service.mcp.registry;

import com.datalake.domain.mcp.MCPTool;
import com.datalake.service.mcp.tooling.base.IcebergMaintenanceTool;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * MCP Tool Registry - Central Repository of Available Tools.
 *
 * This registry acts as the service discovery mechanism for the MCP protocol implementation.
 * It automatically discovers tool implementations via Spring's component scanning and maintains
 * the mapping between tool metadata (name, description, schema) and actual implementations.
 *
 * Architecture Role:
 * - Discovers all tool implementations that implement IcebergMaintenanceTool interface
 * - Registers tool metadata for LLM consumption (tool names, descriptions, parameters)
 * - Provides lookup services for MCPToolExecutor to find implementations
 * - Validates that all metadata has corresponding implementations
 *
 * Tool Categories:
 *
 * 1. CATALOG (Discovery):
 *    - list_databases: Discover available databases
 *    - list_tables: List tables in a database
 *    - get_schema: Retrieve table schema
 *    - create_table: Create new Iceberg tables
 *
 * 2. QUERY (Data Access):
 *    - query_data: Execute SELECT queries
 *    - insert_rows: Insert data into tables
 *
 * 3. ANALYSIS (Diagnostics):
 *    - get_table_stats: Table health metrics
 *    - suggest_maintenance: AI-powered recommendations
 *    - troubleshoot_table: Issue diagnosis
 *    - diagnose_performance: Performance analysis
 *
 * 4. MAINTENANCE (Optimization):
 *    - compact_table: File compaction
 *    - expire_snapshots: Snapshot cleanup
 *    - remove_orphan_files: Orphan file removal
 *    - full_maintenance: Complete maintenance workflow
 *
 * 5. OPTIMIZATION (Query Tuning):
 *    - optimize_query: Query optimization suggestions
 *    - analyze_query: Query plan analysis
 *
 * Registration Flow:
 * 1. Spring scans for @Component beans implementing IcebergMaintenanceTool
 * 2. PostConstruct triggers auto-discovery
 * 3. Tool implementations are registered by bean name
 * 4. Tool metadata is registered with matching names
 * 5. Validation ensures metadata-implementation consistency
 */
@Component
public class MCPToolRegistry {
    private static final Logger log = LoggerFactory.getLogger(MCPToolRegistry.class);

    private final Map<String, MCPTool> toolMetadata = new HashMap<>();
    private final Map<String, IcebergMaintenanceTool> toolImplementations = new HashMap<>();
    @Autowired
    private ApplicationContext applicationContext;

    /**
     * Auto-discovers and registers all MCP tools on application startup.
     *
     * Uses Spring's ApplicationContext to find all beans implementing IcebergMaintenanceTool,
     * then validates that metadata and implementations are aligned.
     */
    @PostConstruct
    public void registerTools() {
        log.info("Discovering and registering MCP tools...");

        Map<String, IcebergMaintenanceTool> discoveredTools = applicationContext.getBeansOfType(IcebergMaintenanceTool.class);

        log.info("Found {} tool implementations in Spring context", discoveredTools.size());

        for (Map.Entry<String, IcebergMaintenanceTool> entry : discoveredTools.entrySet()) {
            String beanName = entry.getKey();
            IcebergMaintenanceTool tool = entry.getValue();

            toolImplementations.put(beanName, tool);
            log.info("✅ Registered tool implementation: {} -> {}", beanName, tool.getClass().getSimpleName());
        }

        registerToolMetadata();

        log.info("✅ MCP Tool Registry initialized with {} metadata entries and {} implementations",
                toolMetadata.size(), toolImplementations.size());

        for (String toolName : toolMetadata.keySet()) {
            if (!toolImplementations.containsKey(toolName)) {
                log.warn("⚠️ Tool metadata '{}' registered but NO implementation found!", toolName);
            }
        }

        for (String implName : toolImplementations.keySet()) {
            if (!toolMetadata.containsKey(implName)) {
                log.warn("⚠️ Tool implementation '{}' found but NO metadata registered!", implName);
            }
        }
    }

    /**
     * Registers metadata for all available MCP tools.
     * Metadata includes tool name, description, category, and input schema.
     * This information is used by the LLM to understand available capabilities.
     */
    private void registerToolMetadata() {
        registerTool("list_databases", "List all databases/namespaces in the Iceberg catalog", "catalog");
        registerTool("list_tables", "List all Iceberg tables in a database", "catalog");
        registerTool("get_schema", "Get the schema/structure of an Iceberg table", "catalog");
        registerTool("create_table", "Create a new Iceberg table with a provided schema", "catalog");

        registerTool("query_data", "Execute SELECT query on an Iceberg table to retrieve data (default limit: 10 rows)", "query");
        registerTool("insert_rows", "Insert rows into an Iceberg table", "query");

        registerTool("get_table_stats", "Get detailed statistics about an Iceberg table (row count, file count, size)", "analysis");
        registerTool("suggest_maintenance", "Analyze table health and suggest maintenance operations (AI-powered)", "analysis");
        registerTool("troubleshoot_table", "Diagnose and troubleshoot table issues", "analysis");
        registerTool("diagnose_performance", "Diagnose performance issues for a table or query", "analysis");

        registerTool("compact_table", "Compact small files in an Iceberg table using Spark", "maintenance");
        registerTool("expire_snapshots", "Remove old snapshots from an Iceberg table", "maintenance");
        registerTool("remove_orphan_files", "Remove files not referenced by any snapshot", "maintenance");
        registerTool("full_maintenance", "Run complete maintenance (compact + expire + cleanup)", "maintenance");

        registerTool("optimize_query", "Get optimization recommendations for a query", "optimization");
        registerTool("analyze_query", "Analyze a SQL query for optimization opportunities", "optimization");

        log.info("Registered {} tool metadata entries", toolMetadata.size());
    }

    /**
     * Registers a single tool's metadata.
     */
    private void registerTool(String name, String description, String category) {
        MCPTool tool = MCPTool.builder()
                .name(name)
                .description(description)
                .category(category)
                .build();
        toolMetadata.put(name, tool);
        log.debug("Registered tool metadata: {} - {}", name, description);
    }

    public Optional<MCPTool> getTool(String name) {
        return Optional.ofNullable(toolMetadata.get(name));
    }

    public Optional<IcebergMaintenanceTool> getImplementation(String name) {
        return Optional.ofNullable(toolImplementations.get(name));
    }

    public List<MCPTool> getAllTools() {
        return new ArrayList<>(toolMetadata.values());
    }

    public List<MCPTool> getToolsByCategory(String category) {
        return toolMetadata.values().stream()
                .filter(tool -> tool.getCategory().equals(category))
                .toList();
    }
}