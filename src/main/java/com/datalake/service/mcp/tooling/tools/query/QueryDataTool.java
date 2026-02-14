package com.datalake.service.mcp.tooling.tools.query;

import com.datalake.domain.mcp.MCPToolResult;
import com.datalake.service.mcp.tooling.base.AbstractMCPTool;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SparkSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * MCP Tool: Query data from Iceberg tables
 *
 * Executes SELECT queries and returns sample data
 */
@Component("query_data")
public class QueryDataTool extends AbstractMCPTool {

    private static final Logger log = LoggerFactory.getLogger(QueryDataTool.class);
    private final ObjectProvider<SparkSession> sparkProvider;
    private SparkSession spark;

    public QueryDataTool(ObjectProvider<SparkSession> sparkProvider) {
        super(
                "query_data",
                "Execute SELECT query on an Iceberg table to retrieve data (default limit: 10 rows)",
                "query"
        );
        this.sparkProvider = sparkProvider;
        try {
            this.spark = sparkProvider.getIfAvailable();
        } catch (Exception e) {
            log.warn("SparkSession not available for QueryDataTool");
            this.spark = null;
        }
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

        if (spark == null) {
            return MCPToolResult.builder()
                    .success(false)
                    .message("Spark is not available - cannot query data")
                    .executionTimeMs(System.currentTimeMillis() - startTime)
                    .build();
        }

        String database = String.valueOf(arguments.getOrDefault("database", "default"));
        Object tableNameObj = arguments.get("table_name");
        if (tableNameObj == null) {
            tableNameObj = arguments.get("table");
        }
        String tableName = String.valueOf(tableNameObj);

        // Parse limit (default 10, max 1000 for safety)
        int limit = 10;
        if (arguments.containsKey("limit")) {
            try {
                limit = Math.min(Integer.parseInt(String.valueOf(arguments.get("limit"))), 1000);
            } catch (NumberFormatException e) {
                log.warn("Invalid limit value, using default: 10");
            }
        }

        // Parse columns (default all)
        String columns = "*";
        if (arguments.containsKey("columns")) {
            columns = String.valueOf(arguments.get("columns"));
        }

        // Parse WHERE clause (optional)
        String whereClause = "";
        if (arguments.containsKey("where")) {
            whereClause = " WHERE " + arguments.get("where");
        }

        String fullTableName = database + "." + tableName;
        String query = String.format("SELECT %s FROM %s%s LIMIT %d", columns, fullTableName, whereClause, limit);

        log.info("🔍 Executing query: {}", query);

        try {
            Dataset<Row> result = spark.sql(query);

            // Convert to List of Maps for JSON serialization
            List<Map<String, Object>> rows = new ArrayList<>();
            List<String> columnNames = Arrays.asList(result.columns());

            for (Row row : result.collectAsList()) {
                Map<String, Object> rowMap = new LinkedHashMap<>();
                for (int i = 0; i < columnNames.size(); i++) {
                    rowMap.put(columnNames.get(i), row.get(i));
                }
                rows.add(rowMap);
            }

            Map<String, Object> resultData = new LinkedHashMap<>();
            resultData.put("table", fullTableName);
            resultData.put("query", query);
            resultData.put("columns", columnNames);
            resultData.put("rows", rows);
            resultData.put("rowCount", rows.size());
            resultData.put("limit", limit);

            long executionTime = System.currentTimeMillis() - startTime;
            log.info("✅ Query returned {} rows from {}", rows.size(), fullTableName);

            return MCPToolResult.builder()
                    .success(true)
                    .data(resultData)
                    .message(String.format("Retrieved %d rows from %s", rows.size(), fullTableName))
                    .executionTimeMs(executionTime)
                    .build();

        } catch (Exception e) {
            long executionTime = System.currentTimeMillis() - startTime;
            log.error("❌ Query failed: {}", query, e);
            return MCPToolResult.builder()
                    .success(false)
                    .message("Query failed: " + e.getMessage())
                    .executionTimeMs(executionTime)
                    .build();
        }
    }
}

