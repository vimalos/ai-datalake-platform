package com.datalake.domain.mcp;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * MCP Tool definition
 * Represents a tool that the LLM can invoke
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MCPTool {

    private String name;
    private String description;
    private String category;
    private List<String> requiredArguments;
    private Map<String, String> optionalArguments;
    private String outputSchema;

    /**
     * Generate JSON schema for this tool
     */
    public String getSchema() {
        return String.format("""
                        {
                          "name": "%s",
                          "description": "%s",
                          "category": "%s",
                          "parameters": {
                            "type": "object",
                            "required": %s,
                            "properties": %s
                          }
                        }
                        """, name, description, category,
                formatRequired(), formatProperties());
    }

    private String formatRequired() {
        return "[" + String.join(",",
                requiredArguments.stream()
                        .map(arg -> "\"" + arg + "\"")
                        .toList()) + "]";
    }

    private String formatProperties() {
        StringBuilder props = new StringBuilder("{");

        requiredArguments.forEach(arg -> {
            props.append(String.format("\"%s\": {\"type\": \"string\"},", arg));
        });

        optionalArguments.forEach((key, desc) -> {
            props.append(String.format(
                    "\"%s\": {\"type\": \"string\", \"description\": \"%s\"},",
                    key, desc));
        });

        // Remove trailing comma
        if (props.length() > 1) {
            props.setLength(props.length() - 1);
        }

        props.append("}");
        return props.toString();
    }
}
