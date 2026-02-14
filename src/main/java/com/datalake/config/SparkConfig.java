package com.datalake.config;

import org.apache.spark.sql.SparkSession;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spark Configuration with AWS Glue & S3 Integration
 * <p>
 * Supports:
 * - AWS Glue Data Catalog (production)
 * - Hive Metastore (local development)
 * - S3/S3A storage
 * - Apache Iceberg table format
 */
@Configuration
public class SparkConfig {

    private static final Logger log = org.slf4j.LoggerFactory.getLogger(SparkConfig.class);

    @Value("${spark.enabled:true}")
    private boolean sparkEnabled;

    @Value("${spark.master:local[*]}")
    private String sparkMaster;

    @Value("${spark.metastore.type:glue}")
    private String metastoreType;

    @Value("${spark.metastore.hive-uri:thrift://localhost:9083}")
    private String hiveMetastoreUri;

    @Value("${spark.warehouse:s3a://datalake-416573464914-dev}")
    private String warehousePath;

    @Value("${aws.region:us-east-1}")
    private String awsRegion;

    @Value("${aws.glue.catalog-id:}")
    private String glueCatalogId;

    @Value("${aws.s3.endpoint:}")
    private String s3Endpoint;

    @Value("${aws.s3.access-key:}")
    private String s3AccessKey;

    @Value("${aws.s3.secret-key:}")
    private String s3SecretKey;

    @Value("${aws.s3.path-style-access:false}")
    private boolean s3PathStyleAccess;

    @Bean
    @ConditionalOnProperty(name = "spark.enabled", havingValue = "true", matchIfMissing = true)
    public SparkSession sparkSession() {
        try {
            log.info("🚀 Initializing Spark session...");
            log.info("   Metastore Type: {}", metastoreType);
            log.info("   Warehouse Path: {}", warehousePath);
            log.info("   AWS Region: {}", awsRegion);

            SparkSession.Builder builder = SparkSession.builder()
                    .appName("ai-datalake-platform")
                    .master(sparkMaster)
                    // Iceberg extensions
                    .config("spark.sql.extensions", "org.apache.iceberg.spark.extensions.IcebergSparkSessionExtensions")
                    .config("spark.sql.catalog.spark_catalog", "org.apache.iceberg.spark.SparkSessionCatalog")
                    .config("spark.sql.catalog.spark_catalog.warehouse", warehousePath)
                    // Performance
                    .config("spark.sql.adaptive.enabled", "true")
                    .config("spark.sql.adaptive.coalescePartitions.enabled", "true")
                    .config("spark.sql.statistics.histogram.enabled", "true");

            // Configure metastore (Glue or Hive)
            if ("glue".equalsIgnoreCase(metastoreType)) {
                log.info("   Configuring AWS Glue Data Catalog");

                // Glue Catalog Configuration
                builder.config("spark.sql.catalog.spark_catalog", "org.apache.iceberg.spark.SparkCatalog")
                        .config("spark.sql.catalog.spark_catalog.warehouse", warehousePath)
                        .config("spark.sql.catalog.spark_catalog.catalog-impl", "org.apache.iceberg.aws.glue.GlueCatalog")
                        .config("spark.sql.catalog.spark_catalog.io-impl", "org.apache.iceberg.aws.s3.S3FileIO")
                        .config("spark.hadoop.aws.region", awsRegion)
                        .config("spark.hadoop.fs.s3a.impl", "org.apache.hadoop.fs.s3a.S3AFileSystem")
                        .config("spark.hadoop.fs.s3a.aws.credentials.provider",
                                "com.amazonaws.auth.DefaultAWSCredentialsProviderChain");

                if (glueCatalogId != null && !glueCatalogId.isEmpty()) {
                    builder.config("spark.sql.catalog.spark_catalog.glue.catalogid", glueCatalogId);
                }

                builder.enableHiveSupport();
            } else {
                log.info("   Configuring Hive Metastore: {}", hiveMetastoreUri);
                builder.config("spark.sql.catalog.spark_catalog.type", "hive")
                        .config("spark.sql.catalog.spark_catalog.uri", hiveMetastoreUri)
                        .config("hive.metastore.uris", hiveMetastoreUri)
                        .enableHiveSupport();
            }

            // S3 Configuration
            if (s3Endpoint != null && !s3Endpoint.isEmpty()) {
                log.info("   S3 Endpoint (MinIO/Custom): {}", s3Endpoint);
                builder.config("spark.hadoop.fs.s3a.endpoint", s3Endpoint);
            } else {
                log.info("   Using AWS S3 with region: {}", awsRegion);
            }

            builder.config("spark.hadoop.fs.s3a.path.style.access", String.valueOf(s3PathStyleAccess))
                    .config("spark.hadoop.fs.s3a.aws.credentials.provider",
                            "com.amazonaws.auth.DefaultAWSCredentialsProviderChain")
                    // S3A optimizations
                    .config("spark.hadoop.fs.s3a.connection.maximum", "100")
                    .config("spark.hadoop.fs.s3a.block.size", "128M")
                    .config("fs.s3a.impl", "org.apache.hadoop.fs.s3a.S3AFileSystem");

            // Set credentials if provided (for local/MinIO/dev)
            if (s3AccessKey != null && !s3AccessKey.isEmpty() && s3SecretKey != null && !s3SecretKey.isEmpty()) {
                log.info("   Using provided S3 credentials");
                builder.config("spark.hadoop.fs.s3a.access.key", s3AccessKey)
                        .config("spark.hadoop.fs.s3a.secret.key", s3SecretKey);
            }

            SparkSession session = builder.getOrCreate();

            log.info("✅ Spark session initialized successfully");
            log.info("   Version: {}", session.version());
            log.info("   Master: {}", session.sparkContext().master());

            // Verify connection
            try {
                session.sql("SELECT 1").show();
                log.info("✅ Spark SQL verified");
            } catch (Exception e) {
                log.warn("⚠️ Spark SQL verification failed: {}", e.getMessage());
            }

            return session;

        } catch (Throwable e) {
            log.error("❌ Failed to initialize Spark: {}", e.getMessage(), e);
            throw new IllegalStateException("Failed to initialize Spark. Check configuration and dependencies.", e);
        }
    }
}

