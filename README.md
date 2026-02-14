# 🤖 AI Data Lakehouse Assistant - Apache Iceberg MCP POC

## 📌 POC Intention

This proof-of-concept demonstrates an **agentic AI system** for Apache Iceberg data lakehouse operations.
Users can directly query Iceberg tables, troubleshoot problems, and get fast, AI-driven solutions through natural language interaction. It showcases:

- **Intelligent AI Agent** that understands user intent and decides dynamically whether to:
  - Query real data using MCP tools (discover databases, tables, schemas, stats)
  - Answer conceptual questions from knowledge base (what is Iceberg, why MCP, etc.)
  - Execute actions on data (create tables, insert records, compact files, expire snapshots)

- **Model Context Protocol (MCP)** integration for standardized tool communication
- **LLM + RAG** for knowledge-based responses without querying tools
- **AWS Glue Catalog + S3** as the metadata and data store
- **Apache Iceberg** as the table format with ACID transactions and time travel

Perfect for demos showing how AI agents can intelligently manage data lakehouse operations.

---

## 🏗️ High-Level Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                    WEB UI (React)                           │
│  ┌──────────────┬──────────────────┬──────────────┐        │
│  │  📚 Info     │   🤖 Chat        │  💡 Examples │        │
│  │  Section    │   Interface      │  Panel       │        │
│  └──────────────┴──────────────────┴──────────────┘        │
└─────────────────────────────────────────────────────────────┘
                         ↓
┌─────────────────────────────────────────────────────────────┐
│              Spring Boot API (8080)                          │
│  ┌────────────────────────────────────────────────┐        │
│  │         ChatController                          │        │
│  │  (Receives user queries & returns responses)   │        │
│  └────────────────────────────────────────────────┘        │
└─────────────────────────────────────────────────────────────┘
                         ↓
┌─────────────────────────────────────────────────────────────┐
│           🧠 AI Agent (Intent Recognition)                   │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐      │
│  │ Entity       │  │ Context      │  │ Plan         │      │
│  │ Extraction   │  │ Discovery    │  │ Generation   │      │
│  └──────────────┘  └──────────────┘  └──────────────┘      │
└─────────────────────────────────────────────────────────────┘
         ↓ (Dynamically chooses path)
    ┌─────────┬──────────────────┬───────────┐
    ↓         ↓                  ↓           ↓
┌───────┐  ┌──────────┐  ┌─────────────┐  ┌────────┐
│ Tools │  │ Knowledge│  │  LLM Only   │  │ Action │
│ (MCP) │  │  (RAG)   │  │  (Synthesis)│  │ Exec   │
└───────┘  └──────────┘  └─────────────┘  └────────┘
    ↓         ↓              ↓                ↓
    ├─────────┴──────────────┴────────────────┤
    ↓
┌─────────────────────────────────────────────────────────────┐
│            🎯 Response Generation & Display                  │
│  ┌────────────────────────────────────────────┐            │
│  │ Format (Tables, Lists, Summaries)          │            │
│  │ Maintenance Summary Highlights              │            │
│  │ Return to UI                                │            │
│  └────────────────────────────────────────────┘            │
└─────────────────────────────────────────────────────────────┘
                         ↓
┌─────────────────────────────────────────────────────────────┐
│         Data Layer (AWS Glue + S3 + Iceberg)                 │
│  ┌─────────────┐  ┌──────────────┐  ┌─────────────┐        │
│  │ AWS Glue    │  │ Apache        │  │ S3 Data     │        │
│  │ Catalog     │  │ Iceberg       │  │ Location    │        │
│  │ (Metadata)  │  │ (Tables)      │  │ (s3a://...)│        │
│  └─────────────┘  └──────────────┘  └─────────────┘        │
└─────────────────────────────────────────────────────────────┘
```

### Architecture Flow

1. **User Query** → UI sends question to backend
2. **AI Agent** processes query intent:
   - Extracts database/table names
   - Discovers available context (databases, tools, knowledge)
   - Creates execution plan (what tool/knowledge to use)
3. **Dynamic Routing**:
   - **Data Tools** (MCP): list_tables, get_schema, get_table_stats, query_data, etc.
   - **Knowledge Base** (RAG): Iceberg concepts, best practices, explanations
   - **Action Tools**: create_table, insert_rows, compact_table, expire_snapshots
4. **Response Synthesis** → LLM generates human-readable answer
5. **UI Display** → Formatted response with summaries and highlights

---

## 🛠️ Tech Stack

### Frontend
- **React 18** - UI framework
- **JavaScript (ES6+)** - Logic and interactivity
- **CSS3** - Styling with responsive design
- **Axios** - HTTP client for API calls

### Backend
- **Spring Boot 3.x** - REST API framework
- **Java 17+** - Backend language
- **Maven** - Build tool

### AI/LLM
- **Ollama** (Local) - Open-source LLM inference
- **LangChain4j** - LLM integration library
- **RAG Engine** - Retrieval-Augmented Generation for knowledge base
- **Nomic Embeddings** - Vector embeddings for document retrieval

### Data Lakehouse
- **Apache Iceberg** - Table format with ACID + time travel
- **AWS Glue Catalog** - Metadata repository
- **Apache Spark 3.x** - Query execution engine
- **S3** - Data storage (s3a:// protocol)

### Integration
- **Model Context Protocol (MCP)** - Standardized tool communication
- **JSON-RPC 2.0** - MCP protocol specification

### Infrastructure
- **Docker** - Containerization
- **Docker Compose** - Local orchestration
- **AWS EC2** - Production deployment target

---

## 🎯 Key Features

### 1. **Intelligent AI Agent**
- Understands user intent without hardcoded rules
- Dynamically decides: tools vs knowledge vs LLM-only
- Multi-step planning and execution
- Smart chaining (previous results feed into next steps)

### 2. **MCP Tools** (Real Data Operations)
| Tool | Purpose | Example |
|------|---------|---------|
| `list_databases` | Discover all databases in Glue Catalog | "What databases exist?" |
| `list_tables` | List tables in a database | "Show tables in analytics" |
| `get_schema` | Get table structure | "What is the schema of users?" |
| `get_table_stats` | Get table metadata (rows, files, size) | "How many records in users?" |
| `query_data` | Execute SELECT with filters | "Show 10 rows from users" |
| `create_table` | Create new Iceberg table | "Create table products with 3 fields" |
| `insert_rows` | Insert sample data | "Insert 5 records into products" |
| `compact_table` | Compact small files | "Compact users table" |
| `expire_snapshots` | Remove old snapshots | "Expire old snapshots" |
| `remove_orphan_files` | Clean orphaned files | "Remove orphan files" |

### 3. **Knowledge Base** (RAG)
- 5 knowledge documents covering:
  - Apache Iceberg basics & features
  - Table maintenance best practices
  - AWS Glue Catalog integration
  - Spark setup & configuration
  - Platform guide

### 4. **Smart Responses**
- Tables rendered properly with borders
- Lists with bullets
- Summaries highlighted separately
- Timestamps for tracking
- Maintenance operation summaries show deltas

---

## 🎮 How to Use the UI

### UI Layout (3 Panels)

```
┌──────────────┬────────────────────────┬──────────────┐
│   📚 Info    │   🤖 Chat Interface    │  💡 Examples │
│   Section   │   (Main Interaction)   │   (Quick     │
│             │                        │   Actions)   │
├─────────────┤                        │              │
│ 📖 How to   │ Welcome message        │ 📌 Databases │
│    Use      │ Chat messages          │ 📋 Tables    │
│             │ (auto-scroll)          │ 🧱 Schema    │
│ 🏗️ Tech     │                        │ 📊 Stats     │
│    Stack    │ Database selector      │ 📄 Data      │
│             │ [Send button]          │ ✨ Create    │
│             │                        │ 📝 Insert    │
│             │                        │ ⚙️ Maintain  │
│             │                        │ 🛠️ Compact   │
│             │                        │              │
│             │                        │ ─────────    │
│             │                        │ LLM • RAG    │
│             │                        │ 📖 Iceberg   │
│             │                        │ ❓ Why MCP?  │
│             │                        │ 🔄 Snapshots │
│             │                        │ 📊 Partition │
│             │                        │ ⭐ Benefits  │
└──────────────┴────────────────────────┴──────────────┘
```

### Getting Started

#### 1. **Select Database** (Top Right)
- Choose: `analytics` or `default`
- Default is `analytics` (contains sample data)

#### 2. **Ask a Question** (3 Ways)

**Way 1: Type Naturally**
```
Type in input box:
"What databases are available?"
"List tables in analytics"
"Show schema for users"
"How many records in users?"
"Create table products with 3 fields"
```

**Way 2: Click Examples (Right Panel - Top Section)**
- Click any button to auto-fill the input
- Perfect for discovering capabilities
- Examples include discovery, exploration, creation, maintenance

**Way 3: Conceptual Questions (Right Panel - Bottom Section)**
- "What is Apache Iceberg?" → Uses knowledge base
- "Why use MCP with Iceberg?" → Explains concepts
- "How does snapshot expiration work?" → Technical explanation

#### 3. **View Response**
- Response appears in chat area
- Tables are formatted with proper styling
- Lists use bullets
- Maintenance operations show summaries in yellow highlight
- Timestamp shows when response was generated

---

## 📋 Example Queries

### Data Discovery (Tools)
```
User: "What databases are available?"
Agent: Lists all databases from Glue Catalog using list_databases tool

User: "List tables in analytics"
Agent: Uses list_tables tool with database=analytics

User: "Show schema for users"
Agent: Uses get_schema tool, displays column names & types

User: "How many records in users table?"
Agent: Uses get_table_stats tool, shows row count & file statistics
```

### Data Operations (Tools)
```
User: "Create table products with 4 fields"
Agent: Uses create_table tool, creates table with: id, name, created_at, field4
Result: Table created at s3a://bucket/analytics/products

User: "Insert 5 records into products"
Agent: Uses insert_rows tool, auto-generates 5 sample rows
Result: Shows inserted data summary

User: "Compact products table"
Agent: Uses compact_table tool, consolidates small files
Result: Shows summary - files reduced, size reduced
```

### Knowledge Questions (RAG)
```
User: "What is Apache Iceberg?"
Agent: Uses KNOWLEDGE tool, retrieves from knowledge base
Result: Explanation of Iceberg features, benefits, use cases

User: "Why use MCP with Iceberg?"
Agent: Explains Model Context Protocol standardization
Result: Benefits of MCP abstraction, tool communication

User: "How does snapshot expiration work?"
Agent: Technical explanation from knowledge base
Result: How Iceberg manages snapshots and retention
```

---

## 🚀 Quick Start

### Prerequisites
- Docker & Docker Compose
- Java 17+
- Node.js 16+
- AWS credentials (for S3 access)

### Local Development

1. **Start Infrastructure**
```bash
docker-compose up -d
# Starts: Hive Metastore, Spark, Ollama, etc.
```

2. **Start Backend**
```bash
cd /Users/vimalks/workspace/ai-datalake-platform
mvn clean package -DskipTests
java -jar target/ai-datalake-platform-1.0.0-SNAPSHOT.jar
```

3. **Start Frontend**
```bash
cd frontend
npm install
npm start
# Opens http://localhost:3000
```

4. **Access UI**
```
Open browser: http://localhost:3000
```

### 🌐 AWS EC2 Deployment (One Command)

Deploy directly to AWS EC2 without any code changes:

```bash
# SSH into your EC2 instance, then run:
sudo bash deploy-ec2.sh https://github.com/yourusername/ai-datalake-platform.git
```

**What this does:**
- ✅ Installs Docker & Docker Compose
- ✅ Clones repository
- ✅ Configures all services (PostgreSQL, MinIO, Ollama, Backend, Frontend)
- ✅ Starts everything automatically
- ✅ Displays access URLs

**Time:** ~5-10 minutes

**Access:** Open `http://<YOUR-EC2-PUBLIC-IP>:3000` in browser

See [AWS_EC2_DEPLOYMENT.md](AWS_EC2_DEPLOYMENT.md) for detailed instructions.

### 📋 Manual AWS EC2 Setup

If you prefer manual setup:

```bash
sudo apt-get update && sudo apt-get upgrade -y
curl -fsSL https://get.docker.com | sudo sh
sudo curl -L "https://github.com/docker/compose/releases/latest/download/docker-compose-$(uname -s)-$(uname -m)" -o /usr/local/bin/docker-compose
sudo chmod +x /usr/local/bin/docker-compose
cd /opt && sudo git clone https://github.com/yourusername/ai-datalake-platform.git
cd ai-datalake-platform && docker-compose up -d
```

Then access at: `http://<PUBLIC-IP>:3000`
````
