package com.datalake.service.rag;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for RAG (Retrieval Augmented Generation) Service
 */
@SpringBootTest
@TestPropertySource(properties = {
        "spark.enabled=false",
        "mcp.client.enabled=false",
        "mcp.llm.integration.enabled=false",
        "llm.ollama.url=http://localhost:11434",
        "aws.glue.enabled=false",
        "rag.knowledge-base.path=src/main/resources/knowledge-base"
})
public class RAGServiceTest {

    @Autowired
    RAGService ragService;

    @Test
    void testRAGServiceLoads() {
        assertThat(ragService).isNotNull();
    }

    @Test
    void testIndexDocument() {
        // Test indexing a single document
        String docId = "test-doc";
        String content = "This is a test document about Apache Iceberg tables.";

        // Should not throw exception
        ragService.indexDocument(docId, content);
    }

    @Test
    void testIndexMultipleDocuments() {
        Map<String, String> docs = new HashMap<>();
        docs.put("doc1", "Content about Iceberg");
        docs.put("doc2", "Content about Spark");
        docs.put("doc3", "Content about maintenance");

        // Should not throw exception
        ragService.indexDocuments(docs);
    }

    @Test
    void testRetrieveRelevantDocuments() {
        // Index some test documents first
        Map<String, String> docs = new HashMap<>();
        docs.put("iceberg-info", "Apache Iceberg is a table format for data lakes with ACID transactions.");
        docs.put("spark-info", "Apache Spark is a distributed computing framework.");
        ragService.indexDocuments(docs);

        // Retrieve documents - should return a list (even if empty in test environment)
        List<String> results = ragService.retrieveRelevantDocuments("What is Iceberg?", 5);

        assertThat(results).isNotNull();
    }

    @Test
    void testLoadDefaultKnowledgeBase() {
        // This should load knowledge base files from classpath
        // Should not throw exception
        ragService.loadDefaultKnowledgeBase();
    }
}

