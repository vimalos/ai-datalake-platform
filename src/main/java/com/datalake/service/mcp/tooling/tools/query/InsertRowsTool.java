package com.datalake.service.mcp.tooling.tools.query;

import com.datalake.domain.mcp.MCPToolResult;
import com.datalake.service.mcp.tooling.base.AbstractMCPTool;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SparkSession;
import org.apache.spark.sql.types.StructField;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component("insert_rows")
public class InsertRowsTool extends AbstractMCPTool {
    private static final Logger log = LoggerFactory.getLogger(InsertRowsTool.class);

    private final ObjectProvider<SparkSession> sparkProvider;
    private SparkSession spark;

    public InsertRowsTool(ObjectProvider<SparkSession> sparkProvider) {
        super("insert_rows", "Insert rows into an Iceberg table", "query");
        this.sparkProvider = sparkProvider;
        try {
            this.spark = sparkProvider.getIfAvailable();
        } catch (Exception e) {
            log.warn("SparkSession not available for InsertRowsTool");
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
        if (spark == null) {
            return MCPToolResult.builder()
                    .success(false)
                    .message("Spark is not available - cannot insert rows")
                    .build();
        }

        String database = String.valueOf(arguments.getOrDefault("database", "default"));
        Object tableNameObj = arguments.get("table_name");
        if (tableNameObj == null) {
            tableNameObj = arguments.get("table");
        }
        String tableName = String.valueOf(tableNameObj);
        String fullTableName = database + "." + tableName;

        int recordCount = 0;
        Object recordCountObj = arguments.get("record_count");
        if (recordCountObj instanceof Number) {
            recordCount = ((Number) recordCountObj).intValue();
        } else if (recordCountObj != null) {
            try {
                recordCount = Integer.parseInt(String.valueOf(recordCountObj));
            } catch (NumberFormatException ignored) {
                recordCount = 0;
            }
        }
        if (recordCount <= 0) {
            recordCount = 1;
        }

        List<List<Object>> rows = new ArrayList<>();
        Object rowsObj = arguments.get("rows");
        if (rowsObj instanceof List<?>) {
            for (Object row : (List<?>) rowsObj) {
                if (row instanceof List<?>) {
                    List<Object> rowValues = new ArrayList<>();
                    for (Object v : (List<?>) row) {
                        rowValues.add(v);
                    }
                    rows.add(rowValues);
                }
            }
        }

        try {
            if (rows.isEmpty()) {
                rows = generateRows(fullTableName, recordCount);
            }

            String insertSql = buildInsertSql(fullTableName, rows);
            log.info("🧊 Inserting rows: {}", insertSql);
            spark.sql(insertSql);

            // Query back the inserted rows to show in summary
            int insertedRowCount = rows.size();
            List<Map<String, Object>> insertedRowsData = new ArrayList<>();

            try {
                var resultDf = spark.sql("SELECT * FROM " + fullTableName + " ORDER BY ROWNUM DESC LIMIT " + insertedRowCount);
                for (Row row : resultDf.collectAsList()) {
                    Map<String, Object> rowMap = new LinkedHashMap<>();
                    String[] columns = resultDf.columns();
                    for (String col : columns) {
                        int colIndex = resultDf.schema().fieldIndex(col);
                        rowMap.put(col, row.get(colIndex));
                    }
                    insertedRowsData.add(rowMap);
                }
            } catch (Exception e) {
                log.debug("Could not retrieve inserted rows for display: {}", e.getMessage());
            }

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("action", "insert_rows");
            data.put("database", database);
            data.put("table", tableName);
            data.put("recordCount", insertedRowCount);
            data.put("insertedRows", insertedRowsData);
            data.put("status", "completed");
            data.put("summary", String.format("Inserted %d row(s) into %s", insertedRowCount, fullTableName));

            return MCPToolResult.builder()
                    .success(true)
                    .message("Inserted " + insertedRowCount + " row(s) into " + fullTableName)
                    .data(data)
                    .build();
        } catch (Exception e) {
            log.error("Failed to insert rows into {}", fullTableName, e);
            return MCPToolResult.builder()
                    .success(false)
                    .message("Insert failed: " + e.getMessage())
                    .build();
        }
    }

    private List<List<Object>> generateRows(String fullTableName, int count) {
        List<List<Object>> rows = new ArrayList<>();
        StructField[] fields = spark.table(fullTableName).schema().fields();
        for (int i = 0; i < count; i++) {
            List<Object> row = new ArrayList<>();
            for (StructField field : fields) {
                String type = field.dataType().typeName();
                if (type.contains("int") || type.contains("long")) {
                    row.add(i + 1);
                } else if (type.contains("timestamp")) {
                    row.add(Timestamp.from(Instant.now()));
                } else if (type.contains("date")) {
                    row.add(java.sql.Date.from(Instant.now()));
                } else if (type.contains("double") || type.contains("float")) {
                    row.add((double) (i + 1));
                } else {
                    row.add("sample_" + (i + 1));
                }
            }
            rows.add(row);
        }
        return rows;
    }

    private String buildInsertSql(String fullTableName, List<List<Object>> rows) {
        StringBuilder sb = new StringBuilder();
        sb.append("INSERT INTO ").append(fullTableName).append(" VALUES ");
        for (int i = 0; i < rows.size(); i++) {
            List<Object> row = rows.get(i);
            sb.append("(");
            for (int j = 0; j < row.size(); j++) {
                Object value = row.get(j);
                if (value == null) {
                    sb.append("NULL");
                } else if (value instanceof Number) {
                    sb.append(value);
                } else if (value instanceof Timestamp) {
                    // Use CAST to convert string to TIMESTAMP
                    sb.append("CAST('").append(value.toString()).append("' AS TIMESTAMP)");
                } else if (value instanceof java.sql.Date) {
                    // Use CAST to convert string to DATE
                    sb.append("CAST('").append(value.toString()).append("' AS DATE)");
                } else {
                    sb.append("'").append(String.valueOf(value).replace("'", "''")).append("'");
                }
                if (j < row.size() - 1) {
                    sb.append(", ");
                }
            }
            sb.append(")");
            if (i < rows.size() - 1) {
                sb.append(", ");
            }
        }
        return sb.toString();
    }
}

