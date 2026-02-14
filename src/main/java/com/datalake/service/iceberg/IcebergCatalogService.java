package com.datalake.service.iceberg;

import com.datalake.domain.table.TableInfo;
import com.datalake.domain.table.TableStats;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SparkSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class IcebergCatalogService {

    private static final Logger log = LoggerFactory.getLogger(IcebergCatalogService.class);

    private final ObjectProvider<SparkSession> sparkProvider;
    private SparkSession spark;

    @PostConstruct
    public void init() {
        try {
            this.spark = sparkProvider.getIfAvailable();
            if (spark != null) {
                log.info("✅ SparkSession available for IcebergCatalogService");
            } else {
                log.warn("⚠️ SparkSession not available - Catalog features disabled");
            }
        } catch (Exception e) {
            log.warn("⚠️ SparkSession initialization failed: {}", e.getMessage());
            this.spark = null;
        }
    }

    /**
     * List all databases in the catalog
     */
    public List<String> listDatabases() {
        if (spark == null) {
            log.warn("⚠️ SparkSession not available - cannot list databases");
            return Collections.emptyList();
        }

        try {
            log.info("📚 Listing databases from Iceberg Catalog...");

            // Use Spark SQL to list databases
            Dataset<Row> databases = spark.sql("SHOW DATABASES");
            List<String> dbList = databases.collectAsList().stream()
                    .map(row -> row.getString(0))
                    .collect(Collectors.toList());

            log.info("✅ Found {} databases", dbList.size());
            for (String db : dbList) {
                log.debug("   - {}", db);
            }

            return dbList;

        } catch (Exception e) {
            log.error("❌ Failed to list databases: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * List all tables in a specific database
     */
    public List<String> listTablesInDatabase(String database) {
        if (spark == null) {
            log.warn("⚠️ SparkSession not available - cannot list tables");
            return Collections.emptyList();
        }

        try {
            log.info("📊 Listing tables in database: {}", database);

            // Show tables from the database
            Dataset<Row> tables = spark.sql(String.format("SHOW TABLES IN %s", database));

            List<String> tableList = tables.collectAsList().stream()
                    .map(row -> row.getString(1))  // Column 1 is table name
                    .collect(Collectors.toList());

            log.info("✅ Found {} tables in database '{}'", tableList.size(), database);
            for (String table : tableList) {
                log.debug("   - {}", table);
            }

            return tableList;

        } catch (Exception e) {
            log.error("❌ Failed to list tables in database '{}': {}", database, e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * List all tables (alias method for backward compatibility)
     */
    public List<String> listTables(String database) {
        return listTablesInDatabase(database);
    }

    /**
     * List all tables in all databases
     */
    public Map<String, List<String>> listTablesMap() {
        if (spark == null) {
            log.warn("SparkSession not available");
            return Collections.emptyMap();
        }

        try {
            List<String> databases = listDatabases();
            Map<String, List<String>> result = new HashMap<>();

            for (String database : databases) {
                result.put(database, listTablesInDatabase(database));
            }

            return result;
        } catch (Exception e) {
            log.error("Failed to list all tables", e);
            return Collections.emptyMap();
        }
    }

    /**
     * Get all tables as TableInfo objects
     */
    public List<TableInfo> listTables() {
        if (spark == null) {
            log.warn("SparkSession not available");
            return Collections.emptyList();
        }

        try {
            List<String> databases = listDatabases();
            List<TableInfo> result = new ArrayList<>();

            for (String database : databases) {
                List<String> tables = listTablesInDatabase(database);
                for (String tableName : tables) {
                    try {
                        String fullTableName = database + "." + tableName;

                        // Get location and other details
                        String location = "";
                        long snapshotId = 0;
                        List<String> schemaFields = new ArrayList<>();

                        try {
                            // Get table location from DESCRIBE EXTENDED
                            Dataset<Row> extendedDesc = spark.sql(String.format(
                                    "DESCRIBE EXTENDED %s", fullTableName
                            ));
                            for (Row row : extendedDesc.collectAsList()) {
                                if (row.getString(0).equals("Location")) {
                                    location = row.getString(1);
                                    break;
                                }
                            }
                        } catch (Exception e) {
                            log.debug("Could not get location for {}: {}", fullTableName, e.getMessage());
                        }

                        try {
                            // Get current snapshot
                            Dataset<Row> snapshots = spark.sql(String.format(
                                    "SELECT snapshot_id FROM %s.snapshots ORDER BY committed_at DESC LIMIT 1",
                                    fullTableName
                            ));
                            if (!snapshots.isEmpty()) {
                                snapshotId = snapshots.first().getLong(0);
                            }
                        } catch (Exception e) {
                            log.debug("Could not get snapshot for {}: {}", fullTableName, e.getMessage());
                        }

                        try {
                            // Get schema
                            Dataset<Row> schema = spark.sql(String.format("DESCRIBE %s", fullTableName));
                            schemaFields = schema.collectAsList().stream()
                                    .map(row -> row.getString(0) + ":" + row.getString(1))
                                    .collect(Collectors.toList());
                        } catch (Exception e) {
                            log.debug("Could not get schema for {}: {}", fullTableName, e.getMessage());
                        }

                        result.add(TableInfo.builder()
                                .name(tableName)
                                .namespace(database)
                                .location(location)
                                .schema(schemaFields)
                                .snapshotId(snapshotId)
                                .build());
                    } catch (Exception e) {
                        log.warn("Error getting details for table {}.{}: {}", database, tableName, e.getMessage());
                    }
                }
            }

            return result;
        } catch (Exception e) {
            log.error("Failed to get all table info", e);
            return Collections.emptyList();
        }
    }

    /**
     * Get table schema
     */
    public Map<String, Object> getTableSchema(String database, String tableName) {
        if (spark == null) {
            return Map.of("error", "SparkSession not available");
        }

        try {
            String fullTableName = database + "." + tableName;
            Dataset<Row> schema = spark.sql(String.format("DESCRIBE %s", fullTableName));

            List<Map<String, String>> fields = schema.collectAsList().stream()
                    .map(row -> {
                        Map<String, String> field = new HashMap<>();
                        field.put("name", row.getString(0));
                        field.put("type", row.getString(1));
                        field.put("comment", row.getString(2));
                        return field;
                    })
                    .collect(Collectors.toList());

            Map<String, Object> result = new HashMap<>();
            result.put("table", fullTableName);
            result.put("fields", fields);
            return result;
        } catch (Exception e) {
            log.error("Failed to get schema for {}.{}", database, tableName, e);
            return Map.of("error", e.getMessage());
        }
    }

    /**
     * Get table statistics
     */
    public TableStats getTableStats(String database, String tableName) {
        if (spark == null) {
            log.error("SparkSession not available");
            return null;
        }

        try {
            String fullTableName = database + "." + tableName;

            // Get row count
            long rowCount = spark.table(fullTableName).count();

            // Get file count from Iceberg metadata
            long fileCount = 0;
            long totalSizeBytes = 0;
            long avgFileSizeBytes = 0;
            long smallFileCount = 0;

            try {
                Dataset<Row> files = spark.sql(String.format(
                        "SELECT COUNT(*) as count FROM %s.files", fullTableName
                ));
                fileCount = files.first().getLong(0);

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
                log.debug("Could not get file statistics: {}", e.getMessage());
            }

            // Get snapshot count
            long snapshotCount = 0;
            try {
                Dataset<Row> snapshots = spark.sql(String.format(
                        "SELECT COUNT(*) as count FROM %s.snapshots", fullTableName
                ));
                snapshotCount = snapshots.first().getLong(0);
            } catch (Exception e) {
                log.debug("Could not get snapshot count: {}", e.getMessage());
            }

            // Determine health status
            String healthStatus = "HEALTHY";
            Map<String, Object> issues = new HashMap<>();

            if (fileCount > 0 && smallFileCount > fileCount * 0.5) {
                healthStatus = "NEEDS_COMPACTION";
                issues.put("small_files", Map.of(
                        "count", smallFileCount,
                        "percentage", (smallFileCount * 100.0) / fileCount
                ));
            }
            if (snapshotCount > 100) {
                if (!"NEEDS_COMPACTION".equals(healthStatus)) {
                    healthStatus = "NEEDS_SNAPSHOT_EXPIRATION";
                }
                issues.put("snapshot_buildup", Map.of(
                        "count", snapshotCount,
                        "recommendation", "Consider running expire_snapshots"
                ));
            }

            return TableStats.builder()
                    .tableName(fullTableName)
                    .rowCount(rowCount)
                    .fileCount(fileCount)
                    .totalSizeBytes(totalSizeBytes)
                    .avgFileSizeBytes(avgFileSizeBytes)
                    .snapshotCount(snapshotCount)
                    .smallFileCount(smallFileCount)
                    .healthStatus(healthStatus)
                    .issues(issues)
                    .build();

        } catch (Exception e) {
            log.error("Failed to get stats for {}.{}", database, tableName, e);
            return null;
        }
    }

    /**
     * Get snapshot history
     */
    public List<Map<String, Object>> getSnapshotHistory(String database, String tableName) {
        if (spark == null) {
            return Collections.emptyList();
        }

        try {
            String fullTableName = database + "." + tableName;
            Dataset<Row> snapshots = spark.sql(String.format(
                    "SELECT snapshot_id, committed_at, operation, summary " +
                            "FROM %s.snapshots " +
                            "ORDER BY committed_at DESC " +
                            "LIMIT 10",
                    fullTableName
            ));

            return snapshots.collectAsList().stream()
                    .map(row -> {
                        Map<String, Object> snapshot = new HashMap<>();
                        snapshot.put("snapshot_id", row.getLong(0));
                        snapshot.put("committed_at", row.getTimestamp(1).toString());
                        snapshot.put("operation", row.getString(2));

                        // summary is a Map, not a String
                        try {
                            Object summaryObj = row.get(3);
                            if (summaryObj instanceof scala.collection.immutable.Map) {
                                // Convert Scala Map to Java Map
                                scala.collection.immutable.Map<String, String> scalaMap =
                                        (scala.collection.immutable.Map<String, String>) summaryObj;
                                Map<String, String> javaMap = scala.jdk.javaapi.CollectionConverters
                                        .asJava(scalaMap);
                                snapshot.put("summary", javaMap);
                            } else {
                                snapshot.put("summary", summaryObj);
                            }
                        } catch (Exception e) {
                            log.debug("Could not parse summary for snapshot: {}", e.getMessage());
                            snapshot.put("summary", new HashMap<>());
                        }

                        return snapshot;
                    })
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("Failed to get snapshot history for {}.{}", database, tableName, e);
            return Collections.emptyList();
        }
    }

    /**
     * Get table description from Glue Catalog via Spark
     */
    public Map<String, Object> getTableDetails(String database, String tableName) {
        if (spark == null) {
            log.warn("⚠️ SparkSession not available");
            return Collections.emptyMap();
        }

        try {
            log.info("📋 Getting table details: {}.{}", database, tableName);

            // Use DESCRIBE to get table properties
            Dataset<Row> desc = spark.sql(String.format("DESCRIBE FORMATTED %s.%s", database, tableName));

            Map<String, Object> details = new HashMap<>();
            details.put("database", database);
            details.put("table", tableName);

            List<Map<String, String>> properties = new ArrayList<>();
            desc.collectAsList().forEach(row -> {
                Map<String, String> prop = new HashMap<>();
                prop.put("key", row.getString(0) != null ? row.getString(0).trim() : "");
                prop.put("value", row.getString(1) != null ? row.getString(1).trim() : "");
                if (!prop.get("key").isEmpty()) {
                    properties.add(prop);
                }
            });

            details.put("properties", properties);

            log.info("✅ Retrieved {} properties for table {}.{}", properties.size(), database, tableName);
            return details;

        } catch (Exception e) {
            log.error("❌ Failed to get table details for {}.{}: {}", database, tableName, e.getMessage());
            return Collections.emptyMap();
        }
    }

    /**
     * Check if table is an Iceberg table
     */
    public boolean isIcebergTable(String database, String tableName) {
        try {
            Map<String, Object> details = getTableDetails(database, tableName);

            @SuppressWarnings("unchecked")
            List<Map<String, String>> properties = (List<Map<String, String>>) details.get("properties");

            if (properties != null) {
                for (Map<String, String> prop : properties) {
                    String key = prop.get("key");
                    String value = prop.get("value");

                    if ("table_type".equalsIgnoreCase(key) && "ICEBERG".equalsIgnoreCase(value)) {
                        log.info("✅ Confirmed Iceberg table: {}.{}", database, tableName);
                        return true;
                    }
                    if ("Type".equalsIgnoreCase(key) && value.contains("iceberg")) {
                        log.info("✅ Confirmed Iceberg table: {}.{}", database, tableName);
                        return true;
                    }
                }
            }

            log.warn("⚠️ Table {}.{} is not an Iceberg table", database, tableName);
            return false;

        } catch (Exception e) {
            log.warn("⚠️ Could not determine if table is Iceberg: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Get S3 location of an Iceberg table
     */
    public String getTableLocation(String database, String tableName) {
        try {
            Map<String, Object> details = getTableDetails(database, tableName);

            @SuppressWarnings("unchecked")
            List<Map<String, String>> properties = (List<Map<String, String>>) details.get("properties");

            if (properties != null) {
                for (Map<String, String> prop : properties) {
                    String key = prop.get("key");
                    String value = prop.get("value");

                    if ("Location".equalsIgnoreCase(key)) {
                        log.info("✅ Table location: {}.{} -> {}", database, tableName, value);
                        return value;
                    }
                }
            }

            return null;

        } catch (Exception e) {
            log.warn("❌ Could not get table location: {}", e.getMessage());
            return null;
        }
    }
}

