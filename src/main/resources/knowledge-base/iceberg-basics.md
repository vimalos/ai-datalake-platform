# Apache Iceberg Overview

## What is Apache Iceberg?

Apache Iceberg is an open table format for huge analytic datasets. It provides the following key features:

### ACID Transactions
- Full ACID guarantees for data lake operations
- Serializable isolation for concurrent writes
- Atomic commits and rollbacks
- Schema evolution without breaking existing queries

### Time Travel and Versioning
- Every write creates a new snapshot
- Query data as it existed at any point in time
- Rollback to previous versions easily
- Audit trail of all changes

### Schema Evolution
- Add, drop, rename, update, or reorder columns
- Promote nested fields
- Change column types safely
- No expensive table rewrites needed

### Hidden Partitioning
- Partitioning is tracked separately from the data
- Users don't need to know about partitioning
- Partition evolution without rewriting data
- Automatic partition pruning

### File-Level Operations
- Compaction to optimize file sizes
- Orphan file removal
- Snapshot expiration
- Metadata management

## Iceberg Table Format

### Metadata Layers
1. **Catalog** - Tracks current metadata pointer
2. **Metadata Files** - JSON files with schema, partition spec, snapshots
3. **Manifest Lists** - Lists of manifest files for a snapshot
4. **Manifest Files** - Lists of data files with statistics
5. **Data Files** - Actual data in Parquet, ORC, or Avro format

### Key Concepts

**Snapshot**: Immutable state of a table at a point in time
**Manifest**: List of data files with partition and column-level statistics
**Metadata File**: Contains schema, partition spec, and snapshot list
**Catalog**: Maps table names to current metadata location

## Common Operations

### Creating Tables
```sql
CREATE TABLE db.table (
  id bigint,
  data string,
  category string,
  ts timestamp
) USING iceberg
PARTITIONED BY (days(ts))
```

### Querying Tables
```sql
-- Current data
SELECT * FROM db.table;

-- Time travel query
SELECT * FROM db.table VERSION AS OF 12345;
SELECT * FROM db.table TIMESTAMP AS OF '2026-01-01 00:00:00';
```

### Maintenance Operations
```sql
-- Compact small files
CALL spark_catalog.system.rewrite_data_files('db.table');

-- Expire old snapshots
CALL spark_catalog.system.expire_snapshots('db.table', TIMESTAMP '2026-01-01 00:00:00');

-- Remove orphan files
CALL spark_catalog.system.remove_orphan_files('db.table');
```

### Schema Evolution
```sql
-- Add column
ALTER TABLE db.table ADD COLUMN new_col string;

-- Rename column
ALTER TABLE db.table RENAME COLUMN old_col TO new_col;

-- Change column type
ALTER TABLE db.table ALTER COLUMN id TYPE bigint;
```

## Performance Optimization

### File Size Optimization
- Target file size: 512 MB - 1 GB
- Use compaction to merge small files
- Avoid too many small files

### Partition Strategy
- Partition by time (day, hour) for time-series data
- Use hidden partitioning for transparent optimization
- Avoid high-cardinality partitions

### Statistics
- Iceberg tracks min/max values per column per file
- Null counts and value counts
- Used for query planning and file pruning

### Sorting
- Sort data within files for better compression
- Enables more effective min/max filtering
- Use z-ordering for multi-column filtering

## Integration with Spark

### Configuration
```
spark.sql.extensions=org.apache.iceberg.spark.extensions.IcebergSparkSessionExtensions
spark.sql.catalog.spark_catalog=org.apache.iceberg.spark.SparkSessionCatalog
spark.sql.catalog.spark_catalog.type=hive
```

### Reading Iceberg Tables
```scala
spark.table("db.table")
spark.read.format("iceberg").load("db.table")
```

### Writing to Iceberg Tables
```scala
df.writeTo("db.table").append()
df.writeTo("db.table").overwrite(lit(true))
```

## Best Practices

1. **Regular Maintenance**: Run compaction, snapshot expiration, and orphan file cleanup regularly
2. **Partition Wisely**: Choose partition columns based on query patterns
3. **Monitor Metadata**: Keep metadata files manageable by expiring old snapshots
4. **Use Statistics**: Leverage Iceberg's built-in statistics for query optimization
5. **Schema Evolution**: Plan schema changes to maintain backward compatibility
6. **File Sizes**: Target 512MB-1GB per file for optimal performance
7. **Concurrent Writes**: Use optimistic concurrency for safe concurrent operations

## Common Issues and Solutions

### Too Many Small Files
- **Cause**: Many small writes without compaction
- **Solution**: Run rewrite_data_files procedure regularly

### Slow Queries
- **Cause**: Poor partitioning or lack of file pruning
- **Solution**: Review partition strategy, ensure statistics are up-to-date

### Metadata Growth
- **Cause**: Too many snapshots retained
- **Solution**: Expire old snapshots regularly

### Orphan Files
- **Cause**: Failed writes or interrupted operations
- **Solution**: Run remove_orphan_files procedure periodically

