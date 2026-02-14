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
 * Registry of all MCP tools available to the LLM
 * Auto-discovers tool implementations via Spring's component scanning
 */
@Component
public class MCPToolRegistry {
    private static final Logger log = LoggerFactory.getLogger(MCPToolRegistry.class);

    private final Map<String, MCPTool> toolMetadata = new HashMap<>();
    private final Map<String, IcebergMaintenanceTool> toolImplementations = new HashMap<>();
    @Autowired
    private ApplicationContext applicationContext;

    @PostConstruct
    public void registerTools() {
        log.info("Discovering and registering MCP tools...");

        // Auto-discover all tool implementations from Spring context
        Map<String, IcebergMaintenanceTool> discoveredTools = applicationContext.getBeansOfType(IcebergMaintenanceTool.class);

        log.info("Found {} tool implementations in Spring context", discoveredTools.size());

        for (Map.Entry<String, IcebergMaintenanceTool> entry : discoveredTools.entrySet()) {
            String beanName = entry.getKey();
            IcebergMaintenanceTool tool = entry.getValue();

            // Register implementation using bean name (which matches tool name from @Component)
            toolImplementations.put(beanName, tool);
            log.info("✅ Registered tool implementation: {} -> {}", beanName, tool.getClass().getSimpleName());
        }

        // Register tool metadata (must match bean names)
        registerToolMetadata();

        log.info("✅ MCP Tool Registry initialized with {} metadata entries and {} implementations",
                toolMetadata.size(), toolImplementations.size());

        // Verify all metadata has implementations
        for (String toolName : toolMetadata.keySet()) {
            if (!toolImplementations.containsKey(toolName)) {
                log.warn("⚠️ Tool metadata '{}' registered but NO implementation found!", toolName);
            }
        }

        // Verify all implementations have metadata
        for (String implName : toolImplementations.keySet()) {
            if (!toolMetadata.containsKey(implName)) {
                log.warn("⚠️ Tool implementation '{}' found but NO metadata registered!", implName);
            }
        }
    }

    private void registerToolMetadata() {
        // Catalog/Discovery tools
        registerTool("list_databases", "List all databases/namespaces in the Iceberg catalog", "catalog");
        registerTool("list_tables", "List all Iceberg tables in a database", "catalog");
        registerTool("get_schema", "Get the schema/structure of an Iceberg table", "catalog");
        registerTool("create_table", "Create a new Iceberg table with a provided schema", "catalog");

        // Query tools
        registerTool("query_data", "Execute SELECT query on an Iceberg table to retrieve data (default limit: 10 rows)", "query");
        registerTool("insert_rows", "Insert rows into an Iceberg table", "query");

        // Analysis tools
        registerTool("get_table_stats", "Get detailed statistics about an Iceberg table (row count, file count, size)", "analysis");
        registerTool("suggest_maintenance", "Analyze table health and suggest maintenance operations (AI-powered)", "analysis");
        registerTool("troubleshoot_table", "Diagnose and troubleshoot table issues", "analysis");
        registerTool("diagnose_performance", "Diagnose performance issues for a table or query", "analysis");

        // Maintenance tools
        registerTool("compact_table", "Compact small files in an Iceberg table using Spark", "maintenance");
        registerTool("expire_snapshots", "Remove old snapshots from an Iceberg table", "maintenance");
        registerTool("remove_orphan_files", "Remove files not referenced by any snapshot", "maintenance");
        registerTool("full_maintenance", "Run complete maintenance (compact + expire + cleanup)", "maintenance");

        // Optimization tools
        registerTool("optimize_query", "Get optimization recommendations for a query", "optimization");
        registerTool("analyze_query", "Analyze a SQL query for optimization opportunities", "optimization");

        log.info("Registered {} tool metadata entries", toolMetadata.size());
    }

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