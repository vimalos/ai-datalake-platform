# AWS Glue Data Catalog

## Overview

AWS Glue Data Catalog is a fully managed metadata repository that stores structural and operational metadata for data assets.

## Key Components

### Databases
Logical container for tables and views
- Created automatically or manually
- No cost for database creation
- Can contain unlimited tables

### Tables
Metadata about data structure:
- Schema (column names and types)
- Storage location (S3 path)
- Input/output formats
- Partition information
- Table properties

### Partitions
Subdivisions of table data:
- Defined by partition keys
- Stored in separate S3 locations
- Improves query performance
- Added automatically or manually

## Integration with Apache Iceberg

### Configuration
```scala
SparkSession.builder()
  .config("spark.sql.catalog.spark_catalog", "org.apache.iceberg.spark.SparkSessionCatalog")
  .config("spark.sql.catalog.spark_catalog.type", "hive")
  .config("spark.hadoop.hive.metastore.client.factory.class", 
          "com.amazonaws.glue.catalog.metastore.AWSGlueDataCatalogHiveClientFactory")
  .config("spark.hadoop.aws.region", "us-east-1")
  .enableHiveSupport()
  .getOrCreate()
```

### Creating Iceberg Tables in Glue
```sql
CREATE TABLE glue_db.iceberg_table (
  id bigint,
  name string,
  created_at timestamp
) USING iceberg
LOCATION 's3://bucket/path/to/table'
```

### Querying Glue Tables
```sql
-- List databases
SHOW DATABASES;

-- List tables in database
SHOW TABLES IN glue_db;

-- Describe table
DESCRIBE FORMATTED glue_db.table_name;

-- Query table
SELECT * FROM glue_db.table_name;
```

## Best Practices

### Naming Conventions
- Use lowercase for database and table names
- Use underscores instead of hyphens
- Keep names descriptive but concise
- Avoid special characters

### Organization
- Group related tables in the same database
- Use consistent partition strategies
- Document table purposes in descriptions
- Tag resources for cost tracking

### Security
- Use IAM policies for access control
- Enable encryption for data at rest
- Use VPC endpoints for private access
- Audit access with CloudTrail

### Performance
- Partition large tables by common query filters
- Use columnar formats (Parquet, ORC)
- Keep metadata up to date
- Leverage partition pruning

## IAM Permissions

### Required Permissions for Iceberg with Glue
```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": [
        "glue:GetDatabase",
        "glue:GetDatabases",
        "glue:CreateDatabase",
        "glue:GetTable",
        "glue:GetTables",
        "glue:CreateTable",
        "glue:UpdateTable",
        "glue:DeleteTable",
        "glue:GetPartition",
        "glue:GetPartitions",
        "glue:CreatePartition",
        "glue:UpdatePartition",
        "glue:DeletePartition"
      ],
      "Resource": "*"
    }
  ]
}
```

### S3 Permissions
```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": [
        "s3:GetObject",
        "s3:PutObject",
        "s3:DeleteObject",
        "s3:ListBucket"
      ],
      "Resource": [
        "arn:aws:s3:::your-bucket/*",
        "arn:aws:s3:::your-bucket"
      ]
    }
  ]
}
```

## Common Operations

### Create Database
```sql
CREATE DATABASE IF NOT EXISTS my_database
COMMENT 'My database description'
LOCATION 's3://my-bucket/databases/my_database';
```

### Create Table
```sql
CREATE TABLE my_database.my_table (
  id bigint COMMENT 'Unique identifier',
  name string COMMENT 'Name field',
  created_date date COMMENT 'Creation date'
)
USING iceberg
PARTITIONED BY (created_date)
LOCATION 's3://my-bucket/tables/my_table'
TBLPROPERTIES (
  'format-version' = '2',
  'write.parquet.compression-codec' = 'snappy'
);
```

### List Tables
```sql
SHOW TABLES IN my_database;
```

### Describe Table
```sql
DESCRIBE EXTENDED my_database.my_table;
```

### Drop Table
```sql
DROP TABLE IF EXISTS my_database.my_table;
```

## Troubleshooting

### Access Denied Errors
- Check IAM permissions for Glue and S3
- Verify resource-based policies
- Ensure role is attached to EC2/EMR cluster

### Table Not Found
- Verify database name is correct
- Check if table exists in Glue Console
- Confirm you're using the right catalog

### Slow Metadata Operations
- Check network connectivity to Glue
- Verify no throttling on Glue API calls
- Consider caching metadata locally

### Partition Not Found
- Ensure partitions are registered in Glue
- Run MSCK REPAIR TABLE if needed
- Check partition naming convention

## Monitoring

### CloudWatch Metrics
- API call count
- Error count
- Request latency

### CloudTrail Logs
- Track who accessed which tables
- Audit schema changes
- Monitor permission changes

## Cost Optimization

### Pricing
- First 1 million requests/month: Free
- $1 per 100,000 requests after that
- No charge for storage

### Tips to Reduce Costs
- Cache metadata when possible
- Batch operations instead of individual calls
- Use partition pruning to reduce metadata reads
- Clean up unused tables and databases

