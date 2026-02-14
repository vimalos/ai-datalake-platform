# Apache Spark Overview

## What is Apache Spark?

Apache Spark is a unified analytics engine for large-scale data processing with built-in modules for SQL, streaming, machine learning, and graph processing.

## Core Concepts

### SparkSession
The entry point for Spark functionality:
```scala
val spark = SparkSession.builder()
  .appName("MyApp")
  .master("local[*]")
  .getOrCreate()
```

### RDD (Resilient Distributed Dataset)
- Immutable distributed collection of objects
- Fault-tolerant through lineage
- Lazy evaluation

### DataFrame
- Distributed collection with named columns
- Similar to database tables or pandas DataFrames
- Optimized execution with Catalyst optimizer

### Dataset
- Typed version of DataFrame (Scala/Java)
- Compile-time type safety
- Object-oriented programming interface

## Spark SQL

### Creating DataFrames
```scala
// From data
val df = spark.createDataFrame(data)

// From file
val df = spark.read.parquet("path/to/file")
val df = spark.read.json("path/to/file")

// From SQL
val df = spark.sql("SELECT * FROM table")
```

### DataFrame Operations
```scala
// Select columns
df.select("col1", "col2")

// Filter rows
df.filter($"age" > 21)
df.where("age > 21")

// Aggregate
df.groupBy("category").agg(sum("amount"))

// Join
df1.join(df2, df1("id") === df2("id"))

// Sort
df.orderBy($"date".desc)
```

### SQL Queries
```sql
-- Register DataFrame as temp view
df.createOrReplaceTempView("my_table")

-- Query with SQL
SELECT category, COUNT(*) as count
FROM my_table
GROUP BY category
ORDER BY count DESC
```

## Spark with Iceberg

### Configuration
```scala
SparkSession.builder()
  .config("spark.sql.extensions", "org.apache.iceberg.spark.extensions.IcebergSparkSessionExtensions")
  .config("spark.sql.catalog.spark_catalog", "org.apache.iceberg.spark.SparkSessionCatalog")
  .config("spark.sql.catalog.spark_catalog.type", "hive")
  .enableHiveSupport()
  .getOrCreate()
```

### Reading Iceberg Tables
```scala
// Read current snapshot
val df = spark.table("db.table")

// Read specific snapshot
val df = spark.read
  .option("snapshot-id", "12345")
  .table("db.table")

// Read as of timestamp
val df = spark.read
  .option("as-of-timestamp", "1609459200000")
  .table("db.table")
```

### Writing to Iceberg Tables
```scala
// Append
df.writeTo("db.table").append()

// Overwrite
df.writeTo("db.table").overwrite(lit(true))

// Dynamic overwrite (partition-level)
df.writeTo("db.table")
  .overwritePartitions()
```

## Performance Optimization

### Caching
```scala
// Cache in memory
df.cache()
df.persist(StorageLevel.MEMORY_AND_DISK)

// Unpersist when done
df.unpersist()
```

### Partitioning
```scala
// Repartition for better parallelism
df.repartition(100)
df.repartition($"date")

// Coalesce to reduce partitions
df.coalesce(10)
```

### Broadcast Joins
```scala
import org.apache.spark.sql.functions.broadcast
large_df.join(broadcast(small_df), "id")
```

### Adaptive Query Execution (AQE)
```
spark.sql.adaptive.enabled=true
spark.sql.adaptive.coalescePartitions.enabled=true
spark.sql.adaptive.skewJoin.enabled=true
```

## Best Practices

1. **Use DataFrames over RDDs**: Better optimization with Catalyst
2. **Avoid Wide Transformations**: Minimize shuffles
3. **Cache Wisely**: Only cache when reusing data multiple times
4. **Partition Data**: Based on query patterns
5. **Use Broadcast for Small Tables**: Avoid shuffles in joins
6. **Monitor with Spark UI**: Check for data skew and bottlenecks
7. **Tune Memory**: Configure executor and driver memory appropriately
8. **Enable AQE**: For dynamic optimization at runtime

## Common Spark SQL Functions

### Aggregations
```scala
import org.apache.spark.sql.functions._

df.agg(
  count("*"),
  sum("amount"),
  avg("price"),
  min("date"),
  max("date"),
  stddev("value")
)
```

### Window Functions
```scala
import org.apache.spark.sql.expressions.Window

val windowSpec = Window
  .partitionBy("category")
  .orderBy($"date".desc)

df.withColumn("rank", rank().over(windowSpec))
  .withColumn("row_num", row_number().over(windowSpec))
```

### Date/Time Functions
```scala
df.withColumn("year", year($"date"))
  .withColumn("month", month($"date"))
  .withColumn("day_of_week", dayofweek($"date"))
  .withColumn("date_add", date_add($"date", 7))
```

## Troubleshooting

### Out of Memory Errors
- Increase executor/driver memory
- Reduce data cached in memory
- Optimize data skew
- Use more partitions

### Slow Queries
- Check Spark UI for bottlenecks
- Look for data skew
- Optimize joins (broadcast small tables)
- Enable AQE

### Shuffle Spill
- Increase shuffle partition size
- Configure shuffle memory
- Reduce shuffle operations

### Task Failures
- Check for data skew
- Increase executor memory
- Review error logs in Spark UI

