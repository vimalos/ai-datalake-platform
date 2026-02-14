# AI Data Lakehouse Platform Documentation

## Overview

The AI Data Lakehouse Platform is an enterprise-grade solution that combines Apache Iceberg, Apache Spark, AWS Glue, and LLM capabilities for intelligent data lake management.

## Architecture

### Core Components

1. **Apache Iceberg**: Table format with ACID transactions and time travel
2. **Apache Spark**: Distributed data processing engine with AWS Glue integration
3. **AWS Glue Catalog**: Serverless metadata catalog for Iceberg tables
4. **AWS S3**: Scalable object storage for data warehouse
5. **Ollama**: Local LLM (llama3) for natural language queries
6. **RAG Engine**: In-memory vector embeddings for knowledge retrieval
7. **Spring Boot**: Application framework and REST API
8. **React**: Frontend UI with chat interface

### Deployment Architecture

#### AWS EC2 Production (Primary)
- **Metadata**: AWS Glue Data Catalog (serverless)
- **Storage**: AWS S3 bucket for Iceberg data warehouse
- **Compute**: Apache Spark on EC2 with Glue integration
- **AI/LLM**: Ollama (llama3) running as systemd service
- **Application**: Spring Boot with embedded frontend (port 8080)
- **Authentication**: EC2 IAM role for S3 and Glue access
- **Management**: Systemd services for auto-restart and monitoring

#### Component Communication
```
User → React UI → Spring Boot API → AI Agent
                        ↓
                  LLM (Ollama) + RAG (Knowledge Base)
                        ↓
                  MCP Tool Router
                        ↓
            ┌───────────┴───────────┐
            ↓                       ↓
    Iceberg Catalog Service    Maintenance Service
            ↓                       ↓
        Spark SQL              Spark Actions
            ↓                       ↓
    AWS Glue Catalog ←→ AWS S3 Warehouse
```

## Features

### Natural Language Queries
Ask questions in plain English:
- "Show me all tables in the database"
- "What's the schema of the users table?"
- "Compact the events table"
- "List all databases"

### MCP (Model Context Protocol) Tools

The platform implements MCP for standardized tool communication between the LLM and Iceberg operations.

#### Catalog Operations
- `list_databases`: Discover all databases in AWS Glue Catalog
- `list_tables`: List all Iceberg tables in a database
- `get_schema`: Get table schema with column types and constraints
- `get_table_stats`: Get comprehensive table statistics (rows, files, size, snapshots)

#### Data Operations
- `query_data`: Execute SELECT queries with filters and limits
- `create_table`: Create new Iceberg tables with auto-generated schema
- `insert_rows`: Insert sample data into Iceberg tables

#### Maintenance Operations
- `compact_table`: Compact small files to improve query performance
- `expire_snapshots`: Remove old snapshots to free up storage
- `remove_orphan_files`: Clean up unreferenced data files
- `suggest_maintenance`: Get AI-powered maintenance recommendations

#### Analysis Operations
- `diagnose_performance`: Analyze table health and performance issues
- `troubleshoot_table`: Get troubleshooting recommendations for tables
- `analyze_query`: Analyze query patterns and optimization opportunities
- `optimize_query`: Get query optimization suggestions

### Table Metadata

#### Schema Information
- Column names and types
- Nullable constraints
- Comments
- Partition information

#### Statistics
- Row count
- File count
- Total size
- Snapshot count
- Partition details

#### History
- Snapshot timeline
- Schema evolution
- Data changes over time

## API Endpoints

### Chat API
```
POST /api/chat/query
{
  "message": "show tables in icebergdb",
  "database": "icebergdb",
  "table": null
}
```

### Database Operations
```
GET /api/databases
GET /api/tables/by-database?database=icebergdb
GET /api/table-metadata?database=icebergdb&table=users
```

### MCP Tools
```
GET /api/mcp/tools
POST /api/mcp/execute
{
  "toolName": "list_tables",
  "arguments": {
    "database": "icebergdb"
  }
}
```

### Maintenance
```
POST /api/maintenance/compact?database=icebergdb&table=events
POST /api/maintenance/expire-snapshots?database=icebergdb&table=events&days=7
```

## Configuration

### Environment Variables

#### AWS Production Deployment (EC2 with Glue Catalog)
```bash
# AWS Configuration
AWS_REGION=us-east-1
AWS_GLUE_ENABLED=true
AWS_S3_BUCKET=datalake-012573464914-dev
AWS_S3_WAREHOUSE_PATH=warehouse

# Spark Configuration
SPARK_ENABLED=true
METASTORE_TYPE=glue
SPARK_MASTER=local[*]
SPARK_WAREHOUSE=s3a://datalake-012573464914-dev/warehouse

# LLM Configuration
OLLAMA_URL=http://localhost:11434
OLLAMA_MODEL=llama3
OLLAMA_EMBEDDING_MODEL=llama3

# Application
SPRING_PROFILES_ACTIVE=prod
SERVER_PORT=8080
```

### Application Properties
```yaml
# AWS Glue Catalog Integration
aws:
  region: us-east-1
  glue:
    enabled: true
    catalog-id: ${AWS_ACCOUNT_ID}
  s3:
    bucket: datalake-012573464914-dev
    warehouse-path: warehouse
    endpoint: ""  # Empty for real AWS S3
    path-style-access: false

# Spark with AWS Glue
spark:
  enabled: true
  master: local[*]
  metastore:
    type: glue
  warehouse: s3a://datalake-012573464914-dev/warehouse

# Local LLM
llm:
  ollama:
    url: http://localhost:11434
    model: llama3
    embedding-model: llama3
    timeout: 60s
  temperature: 0.3

# RAG Knowledge Base
rag:
  knowledge-base:
    path: src/main/resources/knowledge-base
```

### AWS IAM Permissions Required

The EC2 instance must have an IAM role with these permissions:

#### S3 Access
```json
{
  "Effect": "Allow",
  "Action": [
    "s3:GetObject",
    "s3:PutObject",
    "s3:DeleteObject",
    "s3:ListBucket"
  ],
  "Resource": [
    "arn:aws:s3:::datalake-012573464914-dev/*",
    "arn:aws:s3:::datalake-012573464914-dev"
  ]
}
```

#### AWS Glue Catalog Access
```json
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
    "glue:DeleteTable"
  ],
  "Resource": "*"
}
```

## Usage Examples

### List All Databases
**Query**: "Show me all databases"
**Action**: Calls `IcebergCatalogService.listDatabases()`
**Response**: List of database names

### Get Table Schema
**Query**: "What's the schema of the users table?"
**Arguments**: 
- database: "default"
- table: "users"
**Action**: Calls `get_schema` MCP tool
**Response**: Column definitions with types

### Compact Table
**Query**: "Compact the events table"
**Arguments**:
- database: "default"
- table: "events"
**Action**: Runs Iceberg compaction via Spark
**Response**: Files compacted, execution time

### Performance Diagnosis
**Query**: "Why is the sales table slow?"
**Action**: 
1. Gets table statistics
2. Analyzes file sizes
3. Checks partition strategy
4. Provides recommendations

## Best Practices

### Query Patterns
- Be specific about database and table names
- Use natural language for complex operations
- Ask follow-up questions for clarification

### Maintenance Schedule
- Compact tables weekly for active tables
- Expire snapshots older than 7 days
- Remove orphan files monthly
- Monitor table statistics regularly

### Performance Optimization
- Partition large tables by date/time
- Keep file sizes between 512MB-1GB
- Use appropriate compression (snappy, zstd)
- Run ANALYZE TABLE for statistics

### Security
- Use IAM roles for AWS resources
- Enable encryption at rest (S3-SSE)
- Configure CORS for production
- Audit access with CloudTrail

## Troubleshooting

### Common Issues

#### LLM Not Responding
- Check Ollama service: `systemctl status ollama`
- Verify model is downloaded: `ollama list`
- Pull model if needed: `ollama pull llama3`
- Check logs: `sudo journalctl -u ollama -f`

#### Spark Connection Failed
- Verify AWS Glue Catalog access (check IAM role)
- Check S3 bucket permissions
- Validate network connectivity to AWS
- Review Spark logs in application output
- Confirm AWS region is correct

#### Table Not Found
- Verify database exists in AWS Glue Catalog
- Check table name spelling (case-sensitive in Glue)
- Confirm you're using correct AWS region
- Use AWS Glue Console to verify table exists
- Refresh Glue Catalog cache if needed

#### Slow Queries
- Check file count and sizes in S3
- Review partition strategy in Glue table
- Run table compaction
- Analyze Spark query plan
- Check network latency to S3

#### Access Denied Errors
- Verify EC2 IAM role has S3 permissions
- Check Glue Catalog permissions
- Ensure S3 bucket policy allows access
- Verify KMS key permissions (if using encryption)

### Logs and Monitoring

#### Application Logs
```bash
# Check service status
sudo systemctl status ai-datalake

# View real-time logs
sudo journalctl -u ai-datalake -f

# View recent logs
sudo journalctl -u ai-datalake -n 100

# Search for errors
sudo journalctl -u ai-datalake | grep ERROR
```

#### Ollama Logs
```bash
# Check Ollama service
sudo systemctl status ollama

# View Ollama logs
sudo journalctl -u ollama -f
```

#### Health Check
```bash
curl http://localhost:8080/actuator/health
```

#### AWS Glue Catalog Verification
```bash
# List databases
aws glue get-databases --region us-east-1

# List tables in a database
aws glue get-tables --database-name analytics --region us-east-1

# Get specific table
aws glue get-table --database-name analytics --name users --region us-east-1
```

#### S3 Data Verification
```bash
# List warehouse contents
aws s3 ls s3://datalake-012573464914-dev/warehouse/ --recursive

# Check specific table path
aws s3 ls s3://datalake-012573464914-dev/warehouse/analytics/users/
```

## Advanced Features

### Time Travel Queries
Query data as it existed in the past:
```sql
SELECT * FROM db.table VERSION AS OF 12345;
SELECT * FROM db.table TIMESTAMP AS OF '2026-01-01';
```

### Schema Evolution
Safely evolve table schemas:
```sql
ALTER TABLE db.table ADD COLUMN new_col STRING;
ALTER TABLE db.table RENAME COLUMN old TO new;
```

### Incremental Reads
Read only new data since last query:
```scala
df.read
  .option("start-snapshot-id", "12345")
  .option("end-snapshot-id", "12346")
  .table("db.table")
```

### Partition Evolution
Change partitioning without rewriting data:
```sql
ALTER TABLE db.table 
REPLACE PARTITION FIELD days(ts) WITH hours(ts);
```

## Support and Resources

### Documentation
- **README.md** - Main documentation with architecture and setup
- **AWS_SECURITY_GROUP_SETUP.md** - AWS Security Group configuration
- **DEPLOYMENT_SCRIPTS_COMPARISON.md** - Deployment options comparison
- **deploy-ec2-aws-glue.sh** - AWS Glue production deployment script

### Deployment
- **AWS EC2 with Glue**: Use `deploy-ec2-aws-glue.sh` for production
- **Single Port**: Only port 8080 needs to be opened
- **IAM Role**: EC2 instance must have S3 and Glue permissions
- **S3 Bucket**: Required for Iceberg data warehouse

### External Resources
- **Apache Iceberg**: https://iceberg.apache.org
- **Apache Spark**: https://spark.apache.org
- **AWS Glue**: https://aws.amazon.com/glue
- **Ollama**: https://ollama.ai
- **Model Context Protocol**: https://modelcontextprotocol.io

### Getting Help
- Check application logs: `sudo journalctl -u ai-datalake -f`
- Review error messages in UI
- Consult knowledge base (ask the AI assistant)
- Test with simple queries first
- Verify AWS IAM permissions
- Check S3 bucket access

