package com.datalake.service.mcp.tooling.base;

import com.datalake.domain.mcp.MCPToolResult;
import com.datalake.service.mcp.tooling.tools.analysis.DiagnosePerformanceTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * Abstract base class for MCP tools
 * Provides common functionality and error handling
 */
public abstract class AbstractMCPTool implements IcebergMaintenanceTool {

    private static final Logger log = LoggerFactory.getLogger(AbstractMCPTool.class);

    protected final String name;
    protected final String description;
    protected final String category;

    /**
     * Constructor for tool
     */
    public AbstractMCPTool(String name, String description, String category) {
        this.name = name;
        this.description = description;
        this.category = category;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getDescription() {
        return description;
    }

    @Override
    public String getCategory() {
        return category;
    }

    @Override
    public MCPToolResult execute(Map<String, Object> arguments) {
        try {
            log.debug("AbstractMCPTool.execute() for tool '{}' - arguments: {}", name, arguments);
            if (arguments == null) {
                log.warn("Tool '{}' received null arguments map", name);
            } else {
                log.debug("Tool '{}' received arguments with keys: {}", name, arguments.keySet());
            }
            validateArguments(arguments);
            log.debug("Tool '{}' arguments validated successfully", name);
            return executeInternal(arguments);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid arguments for tool {}: {}", name, e.getMessage());
            return MCPToolResult.builder()
                    .success(false)
                    .message("Invalid arguments: " + e.getMessage())
                    .build();
        } catch (Exception e) {
            log.error("Error executing tool {}", name, e);
            return MCPToolResult.builder()
                    .success(false)
                    .message("Error: " + e.getMessage())
                    .build();
        }
    }

    /**
     * Validate arguments before execution
     * Override in subclasses to add specific validation
     */
    protected void validateArguments(Map<String, Object> arguments) {
        if (arguments == null) {
            throw new IllegalArgumentException("Arguments cannot be null");
        }
    }

    /**
     * Internal execute logic - to be implemented by subclasses
     */
    protected abstract MCPToolResult executeInternal(Map<String, Object> arguments);
}
