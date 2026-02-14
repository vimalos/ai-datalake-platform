package com.datalake.service.mcp.tooling.tools.catalog;

import com.datalake.domain.mcp.MCPToolResult;
import com.datalake.service.mcp.tooling.base.AbstractMCPTool;
import org.apache.spark.sql.SparkSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component("create_table")
public class CreateTableTool extends AbstractMCPTool {
    private static final Logger log = LoggerFactory.getLogger(CreateTableTool.class);

    private final ObjectProvider<SparkSession> sparkProvider;
    private SparkSession spark;

    @Value("${aws.s3.bucket:ai-datalake}")
    private String s3Bucket;

    @Value("${aws.s3.warehouse-path:warehouse}")
    private String warehousePath;

    @Value("${aws.s3.endpoint:}")
    private String s3Endpoint;

    @Value("${aws.region:us-east-1}")
    private String awsRegion;

    @Value("${spark.warehouse:s3a://ai-datalake/warehouse}")
    private String sparkWarehouse;

    public CreateTableTool(ObjectProvider<SparkSession> sparkProvider) {
        super("create_table", "Create a new Iceberg table with a provided schema", "catalog");
        this.sparkProvider = sparkProvider;
        try {
            this.spark = sparkProvider.getIfAvailable();
        } catch (Exception e) {
            log.warn("SparkSession not available for CreateTableTool");
            this.spark = null;
        }
    }

    // ...existing code...

    @Override
    protected MCPToolResult executeInternal(Map<String, Object> arguments) {
        if (spark == null) {
            return MCPToolResult.builder()
                    .success(false)
                    .message("Spark is not available - cannot create table")
                    .build();
        }

        String database = String.valueOf(arguments.getOrDefault("database", "default"));
        Object tableNameObj = arguments.get("table_name");
        if (tableNameObj == null) {
            tableNameObj = arguments.get("table");
        }
        String tableName = String.valueOf(tableNameObj);

        List<String> columns = resolveColumns(arguments);
        String columnsSql = String.join(", ", columns);
        String fullTableName = database + "." + tableName;

        // Build proper S3 location path using configured bucket and warehouse path
        String location = buildS3Location(database, tableName);

        log.info("🧊 Creating Iceberg table: {} at location: {}", fullTableName, location);

        try {
            // Drop existing table if it exists (for demo/re-run scenarios)
            String dropSql = String.format("DROP TABLE IF EXISTS %s", fullTableName);
            log.debug("Dropping existing table if present: {}", dropSql);
            try {
                spark.sql(dropSql);
                log.info("✅ Dropped existing table (if it existed): {}", fullTableName);
            } catch (Exception dropEx) {
                log.warn("Could not drop table (may not exist): {}", dropEx.getMessage());
            }

            // Create new table
            String createSql = String.format("CREATE TABLE %s (%s) USING iceberg LOCATION '%s'",
                    fullTableName, columnsSql, location);

            log.info("Creating new table with schema: {}", createSql);
            spark.sql(createSql);

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("action", "create_table");
            data.put("database", database);
            data.put("table", tableName);
            data.put("columns", columns);
            data.put("location", location);
            data.put("status", "completed");
            data.put("summary", String.format("Created table %s with %d columns at %s", fullTableName, columns.size(), location));

            log.info("✅ Table created successfully: {}", fullTableName);
            return MCPToolResult.builder()
                    .success(true)
                    .message("Created table " + fullTableName)
                    .data(data)
                    .build();
        } catch (Exception e) {
            log.error("Failed to create table {}", fullTableName, e);
            return MCPToolResult.builder()
                    .success(false)
                    .message("Create table failed: " + e.getMessage())
                    .build();
        }
    }

    private String buildS3Location(String database, String tableName) {
        // Use spark.warehouse as base and append database/table path
        // e.g., s3a://datalake-416573464914-dev/analytics/users
        String basePath = sparkWarehouse;
        if (basePath == null || basePath.trim().isEmpty()) {
            basePath = "s3a://ai-datalake/warehouse";
        }

        // Remove trailing slash if present
        if (basePath.endsWith("/")) {
            basePath = basePath.substring(0, basePath.length() - 1);
        }

        String path = String.format("%s/%s/%s", basePath, database, tableName);
        log.info("📍 Built S3 location from spark.warehouse: {} → {}", sparkWarehouse, path);
        return path;
    }

    // ...existing code...

    private List<String> resolveColumns(Map<String, Object> arguments) {
        List<String> columns = new ArrayList<>();

        Object columnsObj = arguments.get("columns");
        if (columnsObj instanceof List<?>) {
            for (Object c : (List<?>) columnsObj) {
                if (c != null && !String.valueOf(c).trim().isEmpty()) {
                    columns.add(String.valueOf(c).trim());
                }
            }
        } else if (columnsObj != null) {
            String raw = String.valueOf(columnsObj).trim();
            if (!raw.isEmpty()) {
                for (String c : raw.split(",")) {
                    String col = c.trim();
                    if (!col.isEmpty()) {
                        columns.add(col);
                    }
                }
            }
        }

        if (columns.isEmpty()) {
            Object fieldCountObj = arguments.get("field_count");
            int fieldCount = 0;
            if (fieldCountObj instanceof Number) {
                fieldCount = ((Number) fieldCountObj).intValue();
            } else if (fieldCountObj != null) {
                try {
                    fieldCount = Integer.parseInt(String.valueOf(fieldCountObj));
                } catch (NumberFormatException ignored) {
                    fieldCount = 0;
                }
            }
            if (fieldCount <= 0) {
                // Default schema naming
                columns.add("id BIGINT");
                columns.add("name STRING");
            } else if (fieldCount == 1) {
                columns.add("id BIGINT");
            } else if (fieldCount == 2) {
                columns.add("id BIGINT");
                columns.add("name STRING");
            } else if (fieldCount == 3) {
                columns.add("id BIGINT");
                columns.add("name STRING");
                columns.add("created_at TIMESTAMP");
            } else {
                // For >3 fields: id + name + created_at + generic fields
                columns.add("id BIGINT");
                columns.add("name STRING");
                columns.add("created_at TIMESTAMP");
                for (int i = 4; i <= fieldCount; i++) {
                    columns.add("field" + i + " STRING");
                }
            }
        }

        return columns;
    }
}

