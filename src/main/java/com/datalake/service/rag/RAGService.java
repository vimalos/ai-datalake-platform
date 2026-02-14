package com.datalake.service.rag;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RAGService {

    private static final Logger log = LoggerFactory.getLogger(RAGService.class);

    @Autowired
    private final RAGEngine ragEngine;

    @Value("${rag.knowledge-base.path:src/main/resources/knowledge-base}")
    private String knowledgeBasePath;

    /**
     * Index documentation into the embedding store via RAGEngine
     */
    public void indexDocument(String documentId, String content) {
        try {
            ragEngine.indexDocument(documentId, content);
            log.info("Document indexed: {}", documentId);
        } catch (Exception e) {
            log.error("Failed to index document: {}", documentId, e);
        }
    }

    /**
     * Index batch of documents via RAGEngine
     */
    public void indexDocuments(Map<String, String> documents) {
        documents.forEach(this::indexDocument);
        log.info("Indexed {} documents", documents.size());
    }

    /**
     * Retrieve relevant documents for a query via RAGEngine
     */
    public List<String> retrieveRelevantDocuments(String query, int maxResults) {
        try {
            // Use RAGEngine for semantic search
            var result = ragEngine.query(query);
            log.debug("RAG search result for: {}", query);

            // Extract results from RAGEngine response
            if (result != null && result.containsKey("sources")) {
                @SuppressWarnings("unchecked")
                List<String> sources = (List<String>) result.get("sources");
                return sources != null ? sources : Collections.emptyList();
            }
            return Collections.emptyList();
        } catch (Exception e) {
            log.error("Failed to retrieve documents for query: {}", query, e);
            return Collections.emptyList();
        }
    }

    /**
     * Load knowledge base from markdown files in resources directory
     */
    public void loadDefaultKnowledgeBase() {
        try {
            Map<String, String> docs = new HashMap<>();

            // Load markdown files from classpath
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            Resource[] resources = resolver.getResources("classpath:knowledge-base/*.md");

            log.info("Found {} knowledge base files to load", resources.length);

            for (Resource resource : resources) {
                try {
                    String filename = resource.getFilename();
                    if (filename == null) {
                        continue;
                    }

                    // Read file content
                    String content = new BufferedReader(
                            new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))
                            .lines()
                            .collect(Collectors.joining("\n"));

                    // Use filename without extension as document ID
                    String documentId = filename.replace(".md", "");
                    docs.put(documentId, content);

                    log.debug("Loaded knowledge base file: {} ({} characters)",
                            documentId, content.length());

                } catch (Exception e) {
                    log.error("Failed to load knowledge base file: {}", resource.getFilename(), e);
                }
            }

            if (docs.isEmpty()) {
                log.warn("No knowledge base files found, loading fallback documentation");
                loadFallbackKnowledgeBase(docs);
            }

            indexDocuments(docs);
            log.info("Knowledge base loaded with {} documents", docs.size());

        } catch (Exception e) {
            log.error("Failed to load knowledge base from files, using fallback", e);
            Map<String, String> fallbackDocs = new HashMap<>();
            loadFallbackKnowledgeBase(fallbackDocs);
            indexDocuments(fallbackDocs);
        }
    }

    /**
     * Load minimal fallback knowledge base if files are not available
     */
    private void loadFallbackKnowledgeBase(Map<String, String> docs) {
        // Iceberg fundamentals
        docs.put("iceberg-basics", """
                Apache Iceberg is a table format for large analytic datasets.
                Key concepts:
                - Snapshots: Immutable view of table at a point in time
                - Manifests: List of data files in a snapshot
                - Metadata: All table state tracked in metadata files
                - ACID Transactions: Serializable isolation level
                - Schema Evolution: Add, drop, rename columns without rewriting data
                - Hidden Partitions: Partition spec is hidden from users
                - Time Travel: Query historical versions of tables
                
                Common operations:
                - CREATE TABLE: Create new Iceberg table
                - SELECT: Query current or historical data
                - INSERT/UPDATE/DELETE: Modify data with ACID guarantees
                - ALTER TABLE: Evolve schema safely
                - CALL procedures: Run maintenance operations
                """);

        // Optimization tips
        docs.put("optimization-tips", """
                Iceberg table optimization best practices:
                1. File Size: Target 128MB-512MB per file
                2. Compaction: Rewrite small files using maintenance procedures
                3. Statistics: Collect column-level statistics for predicate pushdown
                4. Partitioning: Use appropriate partition strategy
                5. Filters: Push down filters to data files
                6. Incremental Reads: Use incremental scans for time-series data
                
                Maintenance operations:
                - rewrite_data_files: Compact small files
                - expire_snapshots: Remove old snapshots
                - remove_orphan_files: Clean up unreferenced files
                """);

        // Platform guide
        docs.put("platform-guide", """
                AI Data Lakehouse Platform features:
                - Natural language queries powered by LLM
                - Apache Iceberg for ACID transactions
                - AWS Glue or Hive Metastore for metadata
                - MCP tools for table operations
                - Automated maintenance and optimization
                
                Available MCP tools:
                - list_tables: List all tables in a database
                - get_schema: Get table schema and metadata
                - get_table_stats: Get table statistics
                - compact_table: Compact table files
                - expire_snapshots: Remove old snapshots
                - remove_orphan_files: Clean orphaned files
                
                API endpoints:
                - POST /api/chat/query: Natural language queries
                - GET /api/databases: List databases
                - GET /api/tables/by-database: List tables
                - POST /api/mcp/execute: Execute MCP tools
                """);

        log.info("Loaded {} fallback knowledge documents", docs.size());
    }
}
