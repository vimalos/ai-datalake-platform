# Table Maintenance and Optimization Guide

## Overview

Regular maintenance is essential for optimal performance of Iceberg tables. This guide covers key maintenance operations and best practices.

## File Compaction

### Why Compact?
- Many small files slow down queries
- Metadata overhead increases with file count
- I/O operations are less efficient
- Query planning takes longer

### When to Compact
- After many small writes or updates
- File count > 1000 for a partition
- Average file size < 100 MB
- Query performance degradation

### How to Compact
```sql
-- Compact all files
CALL spark_catalog.system.rewrite_data_files('db.table');

-- Compact specific partition
CALL spark_catalog.system.rewrite_data_files(
  table => 'db.table',
  where => 'date = "2026-02-12"'
);

-- Compact with options
CALL spark_catalog.system.rewrite_data_files(
  table => 'db.table',
  options => map(
    'target-file-size-bytes', '536870912',  -- 512 MB
    'min-file-size-bytes', '104857600'      -- 100 MB
  )
);
```

### Compaction Strategy
- **Target file size**: 512 MB - 1 GB
- **Frequency**: Weekly for active tables
- **Partition-level**: Compact recent partitions more often
- **Time window**: Run during low-usage periods

## Snapshot Expiration

### Why Expire Snapshots?
- Reduce metadata size
- Improve metadata read performance
- Lower storage costs
- Simplify table history

### When to Expire
- Snapshots older than retention period (default: 7 days)
- After major data migrations
- When metadata size grows too large
- Before long-term archival

### How to Expire
```sql
-- Expire snapshots older than 7 days
CALL spark_catalog.system.expire_snapshots('db.table');

-- Expire with custom retention
CALL spark_catalog.system.expire_snapshots(
  table => 'db.table',
  older_than => TIMESTAMP '2026-02-05 00:00:00'
);

-- Expire and keep minimum snapshots
CALL spark_catalog.system.expire_snapshots(
  table => 'db.table',
  older_than => TIMESTAMP '2026-02-05 00:00:00',
  retain_last => 5
);
```

### Expiration Policy
- **Retention period**: 7-30 days typical
- **Minimum snapshots**: Keep at least 3-5
- **Frequency**: Daily or weekly
- **Compliance**: Check regulatory requirements

## Orphan File Removal

### What are Orphan Files?
- Data files not referenced by any snapshot
- Created by failed writes or aborted transactions
- Uncommitted files from crashes
- Leftover files from old snapshots

### Why Remove Orphans?
- Reduce storage costs
- Clean up S3 buckets
- Prevent confusion
- Maintain data hygiene

### How to Remove
```sql
-- Remove orphan files older than 3 days
CALL spark_catalog.system.remove_orphan_files('db.table');

-- Remove with custom age
CALL spark_catalog.system.remove_orphan_files(
  table => 'db.table',
  older_than => TIMESTAMP '2026-02-09 00:00:00'
);

-- Remove from specific location
CALL spark_catalog.system.remove_orphan_files(
  table => 'db.table',
  location => 's3://bucket/path/to/table'
);
```

### Safety Considerations
- **Age threshold**: Default 3 days (be conservative!)
- **Concurrent writes**: Ensure no active writes during cleanup
- **Backup**: Have backups before first run
- **Testing**: Test on non-critical tables first

## Statistics Management

### Why Collect Statistics?
- Better query planning
- Accurate cost-based optimization
- Improved partition pruning
- Faster execution

### How to Collect
```sql
-- Analyze table
ANALYZE TABLE db.table COMPUTE STATISTICS;

-- Analyze columns
ANALYZE TABLE db.table COMPUTE STATISTICS FOR COLUMNS id, name, date;

-- Analyze partitions
ANALYZE TABLE db.table PARTITION (date='2026-02-12') COMPUTE STATISTICS;
```

### Statistics Types
- **Row count**: Number of rows per file
- **Column stats**: Min, max, null count, distinct count
- **Partition stats**: Rows per partition
- **File stats**: File sizes and counts

## Metadata Optimization

### Metadata File Management
- Consolidate metadata files periodically
- Remove old metadata versions
- Optimize manifest list sizes

### Best Practices
- Keep metadata files under 10 MB each
- Limit manifest files to 1000 per snapshot
- Use metadata compression
- Monitor metadata growth

## Maintenance Schedule

### Daily Tasks
- Monitor table statistics
- Check for failed jobs
- Review query performance
- Expire old snapshots (if policy allows)

### Weekly Tasks
- Compact active tables
- Analyze table statistics
- Review storage usage
- Check for orphan files

### Monthly Tasks
- Comprehensive orphan file cleanup
- Metadata optimization
- Performance review
- Partition strategy assessment

### Quarterly Tasks
- Long-term snapshot expiration
- Schema evolution review
- Archive old data
- Capacity planning

## Automated Maintenance

### Scheduling with Cron
```bash
# Daily snapshot expiration (2 AM)
0 2 * * * /opt/scripts/expire_snapshots.sh

# Weekly compaction (Sunday 3 AM)
0 3 * * 0 /opt/scripts/compact_tables.sh

# Monthly orphan cleanup (1st of month, 4 AM)
0 4 1 * * /opt/scripts/remove_orphans.sh
```

### Application-Level Scheduling
```java
@Scheduled(cron = "0 2 * * *")
public void dailyMaintenance() {
    maintenanceService.expireSnapshots("db.table", 7);
}

@Scheduled(cron = "0 3 * * 0")
public void weeklyCompaction() {
    maintenanceService.compactTable("db.table");
}
```

## Monitoring and Alerts

### Key Metrics
- File count per table
- Average file size
- Snapshot count
- Metadata size
- Query execution time
- Storage usage

### Alert Thresholds
- File count > 10,000
- Average file size < 50 MB
- Snapshot count > 100
- Query time > 2x baseline
- Storage growth > 20% per week

### Monitoring Tools
- Spark UI for job metrics
- CloudWatch for AWS metrics
- Application logs
- Custom dashboards

## Performance Impact

### Compaction
- **Duration**: Proportional to data size
- **Resources**: I/O and CPU intensive
- **Impact**: Can slow concurrent queries
- **Mitigation**: Run during off-peak hours

### Snapshot Expiration
- **Duration**: Fast (metadata only)
- **Resources**: Minimal
- **Impact**: Low
- **Mitigation**: None needed

### Orphan Removal
- **Duration**: Depends on file count
- **Resources**: I/O for S3 listing
- **Impact**: Minimal on queries
- **Mitigation**: Use conservative age threshold

## Troubleshooting

### Compaction Failures
- **Out of memory**: Increase executor memory
- **Timeout**: Process smaller partitions
- **Concurrent writes**: Retry or schedule differently

### Expiration Issues
- **Snapshots not expiring**: Check retention settings
- **Permission errors**: Verify IAM/S3 permissions
- **Metadata errors**: Refresh table metadata

### Orphan Removal Problems
- **Files not deleted**: Check S3 permissions
- **Too many files**: Increase batch size
- **Age threshold too low**: Increase threshold

## Best Practices Summary

1. **Regular Schedule**: Automate maintenance tasks
2. **Monitor Metrics**: Track file counts and sizes
3. **Test First**: Try on non-critical tables
4. **Off-Peak Hours**: Run intensive operations during low usage
5. **Conservative Settings**: Start with safe thresholds
6. **Document Changes**: Track maintenance activities
7. **Backup Strategy**: Ensure data recoverability
8. **Review Regularly**: Adjust based on usage patterns

