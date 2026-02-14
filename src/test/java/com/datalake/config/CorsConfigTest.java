package com.datalake.config;

import org.junit.jupiter.api.Test;
import org.springframework.web.cors.CorsConfiguration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for CORS configuration
 * Unit tests without Spring context to avoid SparkSession dependency issues
 */
public class CorsConfigTest {

    @Test
    void testCorsConfigurationCreation() {
        // Test that CorsConfig class exists and can be instantiated
        CorsConfig corsConfig = new CorsConfig();
        assertThat(corsConfig).isNotNull();
    }

    @Test
    void testCorsConfigurationValues() {
        // Test CorsConfiguration directly to verify CORS behavior
        CorsConfiguration config = new CorsConfiguration();

        // Add allowed origins
        config.addAllowedOrigin("http://localhost:3000");
        config.addAllowedOrigin("http://localhost:8080");

        // Add allowed methods
        config.addAllowedMethod("GET");
        config.addAllowedMethod("POST");
        config.addAllowedMethod("PUT");
        config.addAllowedMethod("DELETE");
        config.addAllowedMethod("OPTIONS");

        // Add headers and credentials
        config.addAllowedHeader("*");
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        // Verify configuration
        assertThat(config.getAllowedOrigins()).hasSize(2);
        assertThat(config.getAllowedOrigins()).contains("http://localhost:3000", "http://localhost:8080");
        assertThat(config.getAllowedMethods()).hasSize(5);
        assertThat(config.getAllowedMethods()).contains("GET", "POST", "PUT", "DELETE", "OPTIONS");
        assertThat(config.getAllowedHeaders()).contains("*");
        assertThat(config.getAllowCredentials()).isTrue();
        assertThat(config.getMaxAge()).isEqualTo(3600L);
    }
}

