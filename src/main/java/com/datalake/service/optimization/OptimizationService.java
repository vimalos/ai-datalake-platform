package com.datalake.service.optimization;

import com.datalake.domain.table.TableStats;
import com.datalake.service.iceberg.IcebergCatalogService;
import com.datalake.service.llm.LLMService;
import com.datalake.service.rag.RAGService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class OptimizationService {

    private static final Logger log = org.slf4j.LoggerFactory.getLogger(OptimizationService.class);

    @Autowired
    private final IcebergCatalogService icebergService;
    @Autowired
    private final LLMService llmService;
    @Autowired
    private final RAGService ragService;

    /**
     * Get comprehensive optimization advice
     */
    public Map<String, Object> getOptimizationAnalysis(String database, String table) {
        try {
            TableStats stats = icebergService.getTableStats(database, table);
            Map<String, Object> schema = icebergService.getTableSchema(database, table);

            String context = String.format("""
                    Table: %s.%s
                    
                    Statistics: %s
                    
                    Schema: %s
                    
                    Health: %s
                    """, database, table, stats, schema, null);

            String prompt = String.format("""
                    Analyze this Iceberg table and provide detailed optimization recommendations:
                    
                    %s
                    
                    Provide recommendations for:
                    1. Partitioning strategy - current and suggested
                    2. File sizing and compaction needs (target 128MB-512MB)
                    3. Clustering columns for better performance
                    4. Data type optimizations
                    5. Index recommendations
                    6. Cost optimization opportunities
                    
                    Be specific, technical, and actionable.""", context);

            String advice = llmService.complete(prompt);

            Map<String, Object> result = new HashMap<>();
            result.put("table", database + "." + table);
            result.put("stats", stats);
            result.put("health", null);
            result.put("recommendations", advice);
            result.put("timestamp", System.currentTimeMillis());

            return result;

        } catch (Exception e) {
            log.error("Optimization analysis failed", e);
            return Map.of("error", e.getMessage());
        }
    }

    /**
     * Get schema design advice
     */
    public Map<String, Object> getSchemaDesignAdvice(String database, String table) {
        try {
            Map<String, Object> schema = icebergService.getTableSchema(database, table);

            String prompt = String.format("""
                    Analyze this Iceberg table schema and provide detailed design recommendations:
                    
                    Schema Details: %s
                    
                    Provide specific suggestions for:
                    1. Data type optimizations (avoid DECIMAL for large datasets)
                    2. Nullable columns that should be NOT NULL
                    3. Missing partitioning columns
                    4. Nested structure improvements
                    5. Metadata columns (created_at, updated_at, source, etc.)
                    6. Column ordering for query performance
                    7. Hidden/metadata columns for tracking
                    
                    Provide code examples for schema evolution.""", schema);

            String advice = llmService.complete(prompt);

            Map<String, Object> result = new HashMap<>();
            result.put("table", database + "." + table);
            result.put("current_schema", schema);
            result.put("recommendations", advice);
            result.put("timestamp", System.currentTimeMillis());

            return result;

        } catch (Exception e) {
            log.error("Schema analysis failed", e);
            return Map.of("error", e.getMessage());
        }
    }

    /**
     * Get performance optimization for specific queries
     */
    public Map<String, Object> getQueryPerformanceAdvice(String database, String table, String queryPattern) {
        try {
            Map<String, Object> schema = icebergService.getTableSchema(database, table);
            TableStats stats = icebergService.getTableStats(database, table);

            String prompt = String.format("""
                    Optimize this Iceberg query for performance:
                    
                    Table: %s.%s
                    Query Pattern: %s
                    
                    Table Schema: %s
                    Table Stats: %s
                    
                    Provide:
                    1. Index recommendations
                    2. Partitioning advice
                    3. Clustering suggestions
                    4. Query rewrite patterns
                    5. Spark configuration tuning
                    6. Statistics update strategy
                    
                    Include specific Spark SQL examples.""", database, table, queryPattern, schema, stats);

            String advice = llmService.complete(prompt);

            Map<String, Object> result = new HashMap<>();
            result.put("table", database + "." + table);
            result.put("query_pattern", queryPattern);
            result.put("recommendations", advice);
            result.put("timestamp", System.currentTimeMillis());

            return result;

        } catch (Exception e) {
            log.error("Query optimization analysis failed", e);
            return Map.of("error", e.getMessage());
        }
    }

    /**
     * Get troubleshooting guidance
     */
    public Map<String, Object> getTroubleshootingGuide(String database, String table, String issue) {
        try {
            TableStats stats = icebergService.getTableStats(database, table);
            List<Map<String, Object>> history = icebergService.getSnapshotHistory(database, table);

            // Retrieve relevant documentation from RAG
            List<String> retrievedDocs = ragService.retrieveRelevantDocuments(issue, 3);
            String knowledgeContext = String.join("\n", retrievedDocs);

            String prompt = String.format("""
                            Provide troubleshooting guidance for this Iceberg table issue:
                            
                            Table: %s.%s
                            Issue: %s
                            
                            Statistics: %s
                            Health Status: %s
                            Recent History: %s
                            
                            Knowledge Base Reference: %s
                            
                            Provide a systematic troubleshooting guide:
                            1. Root cause analysis
                            2. Diagnostic steps with specific Spark SQL queries
                            3. Step-by-step resolution instructions
                            4. Verification steps
                            5. Prevention strategies
                            6. When to escalate to platform team
                            
                            Be detailed, precise, and include code examples.""",
                    database, table, issue, stats, null, history, knowledgeContext);

            String guidance = llmService.complete(prompt);

            Map<String, Object> result = new HashMap<>();
            result.put("table", database + "." + table);
            result.put("issue", issue);
            result.put("diagnostics", null);
            result.put("guidance", guidance);
            result.put("timestamp", System.currentTimeMillis());

            return result;

        } catch (Exception e) {
            log.error("Troubleshooting guide failed", e);
            return Map.of("error", e.getMessage());
        }
    }

    /**
     * Get job execution optimization
     */
    public Map<String, Object> getJobOptimizationAdvice(String database, String table, String jobType) {
        try {
            Map<String, Object> schema = icebergService.getTableSchema(database, table);
            TableStats stats = icebergService.getTableStats(database, table);

            String prompt = String.format("""
                            Optimize %s job execution for this Iceberg table:
                            
                            Table: %s.%s
                            Job Type: %s
                            Schema: %s
                            Stats: %s
                            
                            Provide recommendations for:
                            1. Spark configuration tuning (cores, memory, shuffle)
                            2. Data partitioning strategy
                            3. Parallelism settings
                            4. Caching strategy
                            5. File format and compression
                            6. Network optimization
                            7. Resource allocation
                            8. Monitoring metrics
                            
                            Include specific Spark configuration parameters.""",
                    jobType, database, table, jobType, schema, stats);

            String advice = llmService.complete(prompt);

            Map<String, Object> result = new HashMap<>();
            result.put("table", database + "." + table);
            result.put("job_type", jobType);
            result.put("recommendations", advice);
            result.put("timestamp", System.currentTimeMillis());

            return result;

        } catch (Exception e) {
            log.error("Job optimization analysis failed", e);
            return Map.of("error", e.getMessage());
        }
    }
}
