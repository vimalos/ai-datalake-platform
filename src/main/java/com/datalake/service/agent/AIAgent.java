package com.datalake.service.agent;

import com.datalake.domain.chat.ChatRequest;
import com.datalake.domain.chat.ChatResponse;
import com.datalake.domain.mcp.MCPRequest;
import com.datalake.domain.mcp.MCPResponse;
import com.datalake.domain.mcp.MCPTool;
import com.datalake.service.iceberg.IcebergCatalogService;
import com.datalake.service.llm.LLMService;
import com.datalake.service.mcp.server.MCPToolExecutor;
import com.datalake.service.rag.RAGService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * AI Agent - Pure LLM-driven autonomous task planning and execution
 *
 * This agent operates with ZERO hardcoded logic:
 * - LLM analyzes user queries
 * - LLM creates execution plans
 * - LLM selects appropriate tools
 * - LLM interprets results
 * - LLM generates final responses
 *
 * Architecture: User Query → Context Discovery → LLM Planning → Dynamic Execution → LLM Synthesis
 */
@Service
@RequiredArgsConstructor
public class AIAgent {

    private static final Logger log = LoggerFactory.getLogger(AIAgent.class);

    private final LLMService llmService;
    private final MCPToolExecutor mcpToolExecutor;
    private final RAGService ragService;
    private final IcebergCatalogService icebergCatalogService;

    @Value("${agent.max-iterations:10}")
    private int maxIterations;


    /**
     * Process user query - Intelligent agent with entity extraction
     */
    public ChatResponse processQuery(ChatRequest request) {
        log.info("🤖 AI Agent processing query: {}", request.getMessage());
        long startTime = System.currentTimeMillis();
        List<String> executionTrace = new ArrayList<>();

        try {
            String query = request.getMessage();

            // Step 1: Extract entities from query (database, table, etc.)
            // Also use hints from request (e.g., from UI context)
            QueryEntities entities = extractEntities(query, request.getDatabase(), request.getTable());
            log.debug("📍 Extracted entities: {}", entities);

            // Step 2: Discover available context
            AgentContext context = discoverContext(query, entities);
            executionTrace.add("🔍 Context: " + context.getDatabases().size() + " databases, " +
                    context.getTools().size() + " tools, " + context.getKnowledge().size() + " docs");

            // Step 3: LLM creates execution plan with entities
            AgentPlan plan = llmCreatePlan(query, context, entities);
            executionTrace.add("📋 LLM Plan: " + plan.getSteps().size() + " steps");

            // Step 4: Execute plan dynamically
            ExecutionResult result = executePlanDynamically(plan, context, entities, executionTrace);

            // Step 5: LLM synthesizes final answer
            String answer = llmSynthesizeAnswer(query, context, result);
            executionTrace.add("✅ LLM generated final answer");

            long executionTime = System.currentTimeMillis() - startTime;
            log.info("✅ Completed in {}ms", executionTime);

            return ChatResponse.builder()
                    .message(answer)
                    .type("agent")
                    .metadata(Map.of(
                            "executionTrace", executionTrace,
                            "toolsUsed", result.getToolsUsed(),
                            "iterations", result.getIterations(),
                            "planSteps", plan.getSteps().size(),
                            "entities", Map.of(
                                    "database", entities.database != null ? entities.database : "auto",
                                    "table", entities.table != null ? entities.table : "auto"
                            )
                    ))
                    .responseTimeMs(executionTime)
                    .build();

        } catch (Exception e) {
            log.error("❌ Agent failed", e);
            return ChatResponse.error("I encountered an error: " + e.getMessage());
        }
    }

    /**
     * Extract database and table names from query using LLM
     * Also uses hints from ChatRequest (e.g., from UI storing last queried table)
     */
    private QueryEntities extractEntities(String query, String databaseHint, String tableHint) {
        String prompt = String.format("""
                Extract database and table names from this user query. Be precise.
                
                Query: "%s"
                
                Context hints (if user said "this table" or "that table"):
                - Last database: %s
                - Last table: %s
                
                Rules:
                - Look for EXPLICIT database names like: "analytics", "default", "icebergdb"
                - Look for EXPLICIT table names like: "users", "orders", "sales"
                - If database.table pattern exists like "analytics.users", extract both
                - If the query is asking ABOUT databases in general (e.g., "what databases", "show databases", "list databases"), DO NOT extract a specific database name
                - If the query is asking ABOUT tables in general (e.g., "what tables", "show tables", "list tables"), DO NOT extract a specific table name
                - If query refers to "this table" or "that table" and context hints are provided, USE the hints
                - Only extract if a SPECIFIC name is mentioned OR context hints suggest one
                
                Output format (exactly 2 lines, no quotes):
                DATABASE: <specific_name_only or leave blank>
                TABLE: <specific_name_only or leave blank>
                
                Examples:
                Query: "show databases" → DATABASE: \nTABLE: 
                Query: "list tables in analytics" → DATABASE: analytics\nTABLE: 
                Query: "get schema of users table" → DATABASE: \nTABLE: users
                Query: "get schema of analytics.users" → DATABASE: analytics\nTABLE: users
                Query: "does this table need maintenance?" with hint table=users → DATABASE: \nTABLE: users
                
                Extract now:
                """, query,
                databaseHint != null ? databaseHint : "not provided",
                tableHint != null ? tableHint : "not provided");

        String response = llmService.complete(prompt);
        log.debug("Entity extraction response: {}", response);

        String database = null;
        String table = null;

        for (String line : response.split("\n")) {
            line = line.trim();
            if (line.startsWith("DATABASE:")) {
                String db = line.substring(9).trim();
                if (!db.isEmpty() && !db.equalsIgnoreCase("NONE") && !db.equals("None") && !db.contains("blank")) {
                    database = db;
                }
            } else if (line.startsWith("TABLE:")) {
                String tbl = line.substring(6).trim();
                if (!tbl.isEmpty() && !tbl.equalsIgnoreCase("NONE") && !tbl.equals("None") && !tbl.contains("blank")) {
                    table = tbl;
                }
            }
        }

        // Fallback: if LLM didn't extract and hints are provided, use them
        if (database == null && databaseHint != null && !databaseHint.trim().isEmpty()) {
            database = databaseHint;
            log.info("📍 Using database hint from context: {}", database);
        }

        if (table == null && tableHint != null && !tableHint.trim().isEmpty()) {
            table = tableHint;
            log.info("📍 Using table hint from context: {}", table);
        }

        log.info("📍 Extracted: database={}, table={}", database != null ? database : "not specified", table != null ? table : "not specified");
        return new QueryEntities(database, table);
    }

    /**
     * Discover available context - local knowledge only (no internet)
     */
    private AgentContext discoverContext(String query, QueryEntities entities) {
        List<String> databases = icebergCatalogService.listDatabases();

        // Local knowledge (RAG from in-house documents)
        List<String> localKnowledge = ragService.retrieveRelevantDocuments(query, 5);

        List<MCPTool> tools = mcpToolExecutor.listTools();

        return new AgentContext(databases, tools, localKnowledge);
    }

    /**
     * LLM creates execution plan - ZERO hardcoded decision logic
     */
    private AgentPlan llmCreatePlan(String query, AgentContext context, QueryEntities entities) {
        String prompt = buildPlanningPrompt(query, context, entities);
        String planText = llmService.complete(prompt);
        log.debug("🧠 LLM Plan:\n{}", planText);

        return parseLLMPlan(planText, context.getTools());
    }

    /**
     * Build planning prompt - give LLM full context and autonomy
     */
    private String buildPlanningPrompt(String query, AgentContext context, QueryEntities entities) {
        String entityInfo = String.format("Extracted Entities: database=%s, table=%s",
                entities.database != null ? entities.database : "not specified",
                entities.table != null ? entities.table : "not specified");

        return String.format("""
                You are an intelligent AI agent for Apache Iceberg data lakehouse operations.
                
                USER QUERY: "%s"
                
                %s
                
                AVAILABLE RESOURCES:
                
                Databases in Catalog: %s
                
                Available MCP Tools:
                %s
                
                Knowledge Base: %d relevant documents available
                
                🚨 CRITICAL DECISION RULES - READ CAREFULLY:
                
                ⭐ HIGHEST PRIORITY - CONCEPTUAL/WHY/HOW QUESTIONS:
                IF query starts with WHY/HOW/WHAT or asks to EXPLAIN something (not data, not actions):
                  → ALWAYS USE KNOWLEDGE tool
                  → Examples: "why iceberg mcp server?", "what is Apache Iceberg?", "how does MCP work?", "explain snapshot expiration"
                  → Keywords: why, how, what is, explain, describe, tell me about, difference between
                  → These NEVER use data tools - use knowledge base with KNOWLEDGE tool
                
                IF query asks about "databases" or "what databases" or "available databases":
                  → USE list_databases tool (NOT knowledge base)
                  → Example: "what databases are available?" → list_databases
                
                IF query asks about "tables" in a specific database:
                  → USE list_tables tool with database parameter
                  → Example: "show tables in analytics" → list_tables(database=analytics)
                
                IF query asks to SHOW DATA or SELECT or VIEW or DISPLAY data from a table:
                  → USE query_data tool
                  → Example: "show data from users" → query_data(database=X, table_name=users)
                  → Example: "select * from users" → query_data(database=X, table_name=users)
                  → Example: "display users table" → query_data(database=X, table_name=users, limit=10)
                  → Optional args: limit (default 10), columns (default *), where (filter condition)
                
                IF query asks about table structure/schema/columns:
                  → USE get_schema tool
                  → Example: "schema of users" → get_schema(database=X, table_name=users)
                
                IF query asks about counts/stats/size/records (metadata, not data):
                  → USE get_table_stats tool
                  → Example: "how many records in users?" → get_table_stats(database=X, table_name=users)
                
                IF query asks about maintenance/compaction/optimization:
                  → USE suggest_maintenance tool
                  → Example: "should I compact users?" → suggest_maintenance(database=X, table_name=users)
                
                IF query asks to DO something (compact, cleanup, expire):
                  → USE action tool (compact_table, expire_snapshots, etc.)
                  → Example: "compact users table" → compact_table(database=X, table_name=users)
                
                IF query asks to CREATE a new table:
                  → USE create_table tool
                  → Example: "create table sampleai with 2 fields" → create_table(database=X, table_name=sampleai, field_count=2)
                  → If columns are specified, pass columns list: columns=id BIGINT,name STRING
                
                IF query asks to INSERT data or "insert N records":
                  → USE insert_rows tool
                  → Example: "insert 2 records into sampleai" → insert_rows(database=X, table_name=sampleai, record_count=2)
                  → If values are provided, pass rows=[[...],[...]]
                
                🎯 YOUR TASK:
                1. Read the user query carefully
                2. ⭐ FIRST CHECK: Does it start with WHY/HOW/WHAT or ask to EXPLAIN (not about YOUR data)?
                   - If YES → Use KNOWLEDGE tool, NOT data tools
                3. Decide: Is this asking about OUR data (use tools) OR general concepts (use knowledge)?
                4. If user says "this table" or "that table" without specifying which one:
                   - The system will auto-chain from previous tool results
                   - Still call the appropriate tool, the table_name will be resolved automatically
                5. Create a precise execution plan
                
                PLANNING RULES:
                - Use entity extraction results when available
                - If no database specified and one is needed, use the first available: %s
                - Each step should have ONE clear action
                - Chain steps logically (list databases → list tables → get schema)
                - DO NOT pass "None" or "NONE" as argument values
                - When in doubt about databases/tables, USE TOOLS not KNOWLEDGE
                - ⭐ Always prefer KNOWLEDGE for why/how/what/explain questions
                
                ⚠️ OUTPUT FORMAT - FOLLOW EXACTLY:
                You MUST use this EXACT format (no markdown, no bold, no extra text):
                
                STEP 1: <action description>
                TOOL: <tool_name>
                ARGS: <key=value,key2=value2>
                
                DO NOT use **Step**, use STEP
                DO NOT use **TOOL**, use TOOL  
                DO NOT use **ARGS**, use ARGS
                DO NOT add extra explanations or reasoning
                
                EXAMPLES:
                
                Query: "why iceberg mcp server?"
                STEP 1: Explain why MCP is used with Iceberg
                TOOL: KNOWLEDGE
                ARGS:
                
                Query: "what is Apache Iceberg?"
                STEP 1: Explain Apache Iceberg
                TOOL: KNOWLEDGE
                ARGS:
                
                Query: "how does snapshot expiration work?"
                STEP 1: Explain Iceberg snapshot expiration
                TOOL: KNOWLEDGE
                ARGS:
                
                Query: "what databases are available in data lake?"
                STEP 1: List all databases in the catalog
                TOOL: list_databases
                ARGS:
                
                Query: "show databases"
                STEP 1: List all databases
                TOOL: list_databases
                ARGS:
                
                Query: "show tables in analytics"
                STEP 1: List all tables in analytics database
                TOOL: list_tables
                ARGS: database=analytics
                
                Query: "get schema of analytics.users"
                STEP 1: Get schema of users table
                TOOL: get_schema
                ARGS: database=analytics,table_name=users
                
                Query: "show data from users table"
                STEP 1: Query data from users table
                TOOL: query_data
                ARGS: database=analytics,table_name=users,limit=10
                
                Query: "select * from analytics.users where age > 25"
                STEP 1: Query users table with filter
                TOOL: query_data
                ARGS: database=analytics,table_name=users,where=age > 25,limit=10
                
                Query: "how many records in users table?"
                STEP 1: Get statistics for users table
                TOOL: get_table_stats
                ARGS: database=analytics,table_name=users
                
                Query: "create new table sampleai with 2 fields and insert 2 records"
                STEP 1: Create table sampleai
                TOOL: create_table
                ARGS: database=analytics,table_name=sampleai,field_count=2
                
                STEP 2: Insert rows into sampleai
                TOOL: insert_rows
                ARGS: database=analytics,table_name=sampleai,record_count=2
                
                CREATE YOUR PLAN NOW (be precise with tool selection):
                """,
                query,
                entityInfo,
                formatDatabases(context.getDatabases()),
                formatTools(context.getTools()),
                context.getKnowledge().size(),
                context.getDatabases().isEmpty() ? "none" : context.getDatabases().get(0),
                context.getDatabases().isEmpty() ? "default" : context.getDatabases().get(0));
    }

    /**
     * Execute plan dynamically - LLM interprets results and decides next steps
     */
    private ExecutionResult executePlanDynamically(AgentPlan plan, AgentContext context, QueryEntities entities, List<String> trace) {
        List<String> toolsUsed = new ArrayList<>();
        Map<String, Object> executionMemory = new HashMap<>(); // Store results for chaining
        int iteration = 0;

        for (AgentStep step : plan.getSteps()) {
            if (iteration++ >= maxIterations) {
                trace.add("⚠️ Max iterations reached");
                break;
            }

            trace.add(String.format("Step %d: %s", iteration, step.getDescription()));

            try {
                if ("KNOWLEDGE".equalsIgnoreCase(step.getToolName())) {
                    // Knowledge-based step
                    String knowledge = String.join("\n\n", context.getKnowledge());
                    executionMemory.put("step_" + iteration, Map.of("type", "knowledge", "data", knowledge));
                    trace.add("  📚 Using knowledge base");

                } else if (step.getToolName() != null && !"NONE".equalsIgnoreCase(step.getToolName())) {
                    // Tool execution step
                    Map<String, Object> resolvedArgs = resolveArguments(step, context, entities, executionMemory);

                    if (requiresTableName(step.getToolName())
                            && !resolvedArgs.containsKey("table_name")
                            && !resolvedArgs.containsKey("table")) {
                        String db = String.valueOf(resolvedArgs.getOrDefault(
                                "database",
                                context.getDatabases().isEmpty() ? "default" : context.getDatabases().get(0)
                        ));
                        MCPRequest listTables = new MCPRequest();
                        listTables.setToolName("list_tables");
                        listTables.setArguments(Map.of("database", db));
                        MCPResponse listResp = mcpToolExecutor.executeTool(listTables);
                        if (listResp.isSuccess()) {
                            String tableName = extractFirstTable(listResp.getData());
                            if (tableName != null) {
                                resolvedArgs.put("table_name", tableName);
                                trace.add("  🔗 Auto-selected table: " + tableName + " from " + db);
                            }
                        } else {
                            trace.add("  ⚠️ Could not auto-select table: " + listResp.getMessage());
                        }
                    }

                    MCPRequest mcpRequest = new MCPRequest();
                    mcpRequest.setToolName(step.getToolName());
                    mcpRequest.setArguments(resolvedArgs);

                    MCPResponse response = mcpToolExecutor.executeTool(mcpRequest);
                    toolsUsed.add(step.getToolName());

                    if (response.isSuccess()) {
                        executionMemory.put("step_" + iteration, Map.of(
                                "type", "tool",
                                "tool", step.getToolName(),
                                "data", response.getData()
                        ));
                        trace.add("  ✅ " + step.getToolName() + " succeeded");
                    } else {
                        trace.add("  ❌ " + step.getToolName() + " failed: " + response.getMessage());
                        executionMemory.put("step_" + iteration, Map.of(
                                "type", "error",
                                "message", response.getMessage()
                        ));
                    }
                }
            } catch (Exception e) {
                trace.add("  ❌ Error: " + e.getMessage());
                log.error("Step execution failed", e);
            }
        }

        return new ExecutionResult(toolsUsed, executionMemory, iteration);
    }

    /**
     * Resolve arguments dynamically - use extracted entities
     */
    private Map<String, Object> resolveArguments(AgentStep step, AgentContext context, QueryEntities entities, Map<String, Object> memory) {
        Map<String, Object> args = new HashMap<>(step.getArguments());

        // Remove any "None" or "NONE" values
        args.entrySet().removeIf(entry ->
            entry.getValue() instanceof String &&
            ("None".equalsIgnoreCase((String)entry.getValue()) || "NONE".equalsIgnoreCase((String)entry.getValue()))
        );

        // Inject extracted entities
        if (entities.database != null && !args.containsKey("database")) {
            args.put("database", entities.database);
            log.debug("🔗 Using extracted database: {}", entities.database);
        }

        if (entities.table != null && !args.containsKey("table_name") && !args.containsKey("table")) {
            args.put("table_name", entities.table);
            log.debug("🔗 Using extracted table: {}", entities.table);
        }

        // Check previous steps for relevant data (chaining)
        for (Map.Entry<String, Object> entry : memory.entrySet()) {
            if (entry.getValue() instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> stepData = (Map<String, Object>) entry.getValue();

                // Smart chaining: if we need table_name and don't have it, look for it in previous steps
                if (!args.containsKey("table_name") && !args.containsKey("table")) {

                    // Previous step listed tables - use first one
                    if ("tool".equals(stepData.get("type")) && "list_tables".equals(stepData.get("tool"))) {
                        Object data = stepData.get("data");
                        String tableName = extractFirstTable(data);
                        if (tableName != null) {
                            args.put("table_name", tableName);
                            log.info("🔗 Auto-chained: using table '{}' from list_tables", tableName);
                        }
                    }

                    // Previous step queried a table - use that table
                    else if ("tool".equals(stepData.get("type")) && "query_data".equals(stepData.get("tool"))) {
                        Object data = stepData.get("data");
                        String tableName = extractTableFromQueryResult(data);
                        if (tableName != null) {
                            args.put("table_name", tableName);
                            log.info("🔗 Auto-chained: using table '{}' from previous query", tableName);
                        }
                    }

                    // Previous step got schema - use that table
                    else if ("tool".equals(stepData.get("type")) && "get_schema".equals(stepData.get("tool"))) {
                        Object data = stepData.get("data");
                        String tableName = extractTableFromSchemaResult(data);
                        if (tableName != null) {
                            args.put("table_name", tableName);
                            log.info("🔗 Auto-chained: using table '{}' from previous schema query", tableName);
                        }
                    }
                }
            }
        }

        // If still missing database, use first available (only for tools that need it)
        if (!args.containsKey("database") && needsDatabase(step.getToolName()) && !context.getDatabases().isEmpty()) {
            args.put("database", context.getDatabases().get(0));
            log.debug("🔗 Using first available database: {}", context.getDatabases().get(0));
        }

        return args;
    }

    /**
     * Check if a tool requires a database parameter
     */
    private boolean needsDatabase(String toolName) {
        return toolName != null && (
            toolName.equals("list_tables") ||
            toolName.equals("get_schema") ||
            toolName.equals("get_table_stats") ||
            toolName.equals("query_data") ||
            toolName.equals("compact_table") ||
            toolName.equals("expire_snapshots")
        );
    }

    private boolean requiresTableName(String toolName) {
        return toolName != null && (
            toolName.equals("get_schema") ||
            toolName.equals("get_table_stats") ||
            toolName.equals("query_data") ||
            toolName.equals("suggest_maintenance") ||
            toolName.equals("compact_table") ||
            toolName.equals("expire_snapshots") ||
            toolName.equals("remove_orphan_files") ||
            toolName.equals("full_maintenance")
        );
    }

    /**
     * LLM synthesizes final answer - pure LLM intelligence, no templates
     */
    private String llmSynthesizeAnswer(String query, AgentContext context, ExecutionResult result) {
        String prompt = buildSynthesisPrompt(query, context, result);
        return llmService.complete(prompt);
    }

    /**
     * Build synthesis prompt - give LLM all execution results
     */
    private String buildSynthesisPrompt(String query, AgentContext context, ExecutionResult result) {
        StringBuilder resultsSection = new StringBuilder();

        for (Map.Entry<String, Object> entry : result.getExecutionMemory().entrySet()) {
            if (entry.getValue() instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> stepData = (Map<String, Object>) entry.getValue();

                resultsSection.append(entry.getKey()).append(":\n");
                resultsSection.append("  Type: ").append(stepData.get("type")).append("\n");

                if (stepData.containsKey("tool")) {
                    resultsSection.append("  Tool: ").append(stepData.get("tool")).append("\n");
                }

                if (stepData.containsKey("data")) {
                    resultsSection.append("  Data: ").append(formatDataForLLM(stepData.get("data"))).append("\n");
                }

                resultsSection.append("\n");
            }
        }

        return String.format("""
                You are synthesizing a final answer for the user.
                
                ORIGINAL QUERY: "%s"
                
                EXECUTION RESULTS:
                %s
                
                AVAILABLE CONTEXT:
                - Databases: %s
                - Knowledge: %s
                
                YOUR TASK:
                Create a clear, helpful answer that:
                1. Directly addresses the user's query
                2. Uses the execution results as evidence
                3. Uses plain text with short sections
                4. Uses bullet lists starting with '-' when listing items
                5. Avoids bold/italic/markdown styling
                
                IMPORTANT RULES:
                - Do NOT use **bold**, *italic*, or markdown headings
                - Do NOT add filler like "Here's the final answer"
                - If user asked "what databases", list them plainly
                - If user asked "show tables", list tables plainly
                - If you show tabular data, use a simple pipe table
                - Use ACTUAL data from execution results only
                
                OUTPUT EXAMPLE (PLAIN TEXT):
                Available databases:
                - analytics
                
                Tables in analytics:
                - users
                
                Generate your answer now:
                """,
                query,
                resultsSection.toString(),
                String.join(", ", context.getDatabases()),
                context.getKnowledge().isEmpty() ? "none" : context.getKnowledge().size() + " documents"
        );
    }

    /**
     * Parse LLM plan output into structured steps
     * Handles various formats: "STEP 1:", "**Step 1:**", "Step 1:", etc.
     */
    private AgentPlan parseLLMPlan(String planText, List<MCPTool> availableTools) {
        List<AgentStep> steps = new ArrayList<>();
        Set<String> validToolNames = availableTools.stream()
                .map(MCPTool::getName)
                .collect(Collectors.toSet());

        String[] lines = planText.split("\n");
        String currentDescription = null;
        String currentTool = null;
        Map<String, Object> currentArgs = null;

        for (String line : lines) {
            // Clean the line: remove markdown, trim, normalize
            String cleanLine = line.trim()
                    .replaceAll("\\*\\*", "") // Remove markdown bold
                    .replaceAll("^[•\\-*]\\s*", ""); // Remove bullet points

            String upperLine = cleanLine.toUpperCase();

            // Match STEP pattern (case-insensitive, with/without markdown)
            if (upperLine.matches("^STEP\\s+\\d+.*")) {
                // Save previous step
                if (currentDescription != null) {
                    steps.add(new AgentStep(currentDescription, currentTool, currentArgs));
                }

                // Start new step
                int colonIndex = cleanLine.indexOf(":");
                if (colonIndex != -1) {
                    currentDescription = cleanLine.substring(colonIndex + 1).trim();
                    currentTool = null;
                    currentArgs = new HashMap<>();
                } else {
                    currentDescription = cleanLine.replaceAll("(?i)^STEP\\s+\\d+:?\\s*", "");
                    currentTool = null;
                    currentArgs = new HashMap<>();
                }

            } else if (upperLine.startsWith("TOOL:")) {
                currentTool = cleanLine.substring(cleanLine.indexOf(":") + 1).trim();

                // Clean up tool name
                if (currentTool.contains(",")) {
                    currentTool = currentTool.split(",")[0].trim();
                }

                // Validate tool exists
                if (!"KNOWLEDGE".equalsIgnoreCase(currentTool) &&
                        !"NONE".equalsIgnoreCase(currentTool) &&
                        !validToolNames.contains(currentTool)) {
                    log.warn("Tool '{}' not found in registry. Available tools: {}", currentTool, validToolNames);
                    currentTool = "KNOWLEDGE";
                }

            } else if (upperLine.startsWith("ARGS:")) {
                String argsText = cleanLine.substring(cleanLine.indexOf(":") + 1).trim();
                if (!argsText.isEmpty() && !argsText.equalsIgnoreCase("auto")) {
                    currentArgs = parseArguments(argsText);
                } else if (argsText.equalsIgnoreCase("auto")) {
                    currentArgs = Map.of("auto", "true");
                }
            }
        }

        // Add last step
        if (currentDescription != null) {
            steps.add(new AgentStep(currentDescription, currentTool, currentArgs));
        }

        if (steps.isEmpty()) {
            log.warn("No steps parsed from LLM plan, using fallback");
            steps.add(new AgentStep("Answer using available knowledge", "KNOWLEDGE", new HashMap<>()));
        }

        log.debug("Parsed {} steps from plan", steps.size());
        return new AgentPlan(steps);
    }

    // Helper methods

    private String formatDatabases(List<String> databases) {
        return databases.isEmpty() ? "None" : String.join(", ", databases);
    }

    private String formatTools(List<MCPTool> tools) {
        return tools.stream()
                .map(tool -> String.format("  - %s: %s", tool.getName(), tool.getDescription()))
                .collect(Collectors.joining("\n"));
    }

    private String formatDataForLLM(Object data) {
        if (data == null) return "null";

        // Handle MCPToolResult wrapper
        if (data instanceof com.datalake.domain.mcp.MCPToolResult) {
            com.datalake.domain.mcp.MCPToolResult result = (com.datalake.domain.mcp.MCPToolResult) data;
            data = result.getData();
        }

        if (data instanceof Map || data instanceof List) {
            return data.toString(); // LLM can parse JSON-like structures
        }

        return String.valueOf(data);
    }

    private String extractFirstTable(Object data) {
        if (data instanceof com.datalake.domain.mcp.MCPToolResult) {
            data = ((com.datalake.domain.mcp.MCPToolResult) data).getData();
        }

        if (data instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) data;
            if (map.containsKey("tables")) {
                Object tables = map.get("tables");
                if (tables instanceof List && !((List<?>) tables).isEmpty()) {
                    return String.valueOf(((List<?>) tables).get(0));
                }
            }
        }

        return null;
    }

    /**
     * Extract table name from query_data result
     */
    private String extractTableFromQueryResult(Object data) {
        if (data instanceof com.datalake.domain.mcp.MCPToolResult) {
            data = ((com.datalake.domain.mcp.MCPToolResult) data).getData();
        }

        if (data instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) data;
            if (map.containsKey("table")) {
                String fullTable = String.valueOf(map.get("table"));
                // Extract just table name from "database.table" format
                if (fullTable.contains(".")) {
                    return fullTable.substring(fullTable.indexOf(".") + 1);
                }
                return fullTable;
            }
        }

        return null;
    }

    /**
     * Extract table name from get_schema result
     */
    private String extractTableFromSchemaResult(Object data) {
        if (data instanceof com.datalake.domain.mcp.MCPToolResult) {
            data = ((com.datalake.domain.mcp.MCPToolResult) data).getData();
        }

        if (data instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) data;
            if (map.containsKey("table")) {
                String fullTable = String.valueOf(map.get("table"));
                if (fullTable.contains(".")) {
                    return fullTable.substring(fullTable.indexOf(".") + 1);
                }
                return fullTable;
            }
            // Also check for tableName field
            if (map.containsKey("tableName")) {
                String fullTable = String.valueOf(map.get("tableName"));
                if (fullTable.contains(".")) {
                    return fullTable.substring(fullTable.indexOf(".") + 1);
                }
                return fullTable;
            }
        }

        return null;
    }

    private Map<String, Object> parseArguments(String argsText) {
        Map<String, Object> args = new HashMap<>();
        if (argsText == null || argsText.isEmpty()) return args;

        for (String pair : argsText.split(",")) {
            String[] kv = pair.split("=", 2);
            if (kv.length == 2) {
                args.put(kv[0].trim(), kv[1].trim());
            }
        }
        return args;
    }

    // Data classes

    private static class QueryEntities {
        final String database;
        final String table;

        public QueryEntities(String database, String table) {
            this.database = database;
            this.table = table;
        }

        @Override
        public String toString() {
            return String.format("QueryEntities{database='%s', table='%s'}", database, table);
        }
    }

    private static class AgentContext {
        private final List<String> databases;
        private final List<MCPTool> tools;
        private final List<String> knowledge;

        public AgentContext(List<String> databases, List<MCPTool> tools, List<String> knowledge) {
            this.databases = databases;
            this.tools = tools;
            this.knowledge = knowledge;
        }

        public List<String> getDatabases() { return databases; }
        public List<MCPTool> getTools() { return tools; }
        public List<String> getKnowledge() { return knowledge; }
    }

    private static class AgentPlan {
        private final List<AgentStep> steps;

        public AgentPlan(List<AgentStep> steps) {
            this.steps = steps;
        }

        public List<AgentStep> getSteps() { return steps; }
    }

    private static class AgentStep {
        private final String description;
        private final String toolName;
        private final Map<String, Object> arguments;

        public AgentStep(String description, String toolName, Map<String, Object> arguments) {
            this.description = description;
            this.toolName = toolName;
            this.arguments = arguments != null ? arguments : new HashMap<>();
        }

        public String getDescription() { return description; }
        public String getToolName() { return toolName; }
        public Map<String, Object> getArguments() { return arguments; }
    }

    private static class ExecutionResult {
        private final List<String> toolsUsed;
        private final Map<String, Object> executionMemory;
        private final int iterations;

        public ExecutionResult(List<String> toolsUsed, Map<String, Object> executionMemory, int iterations) {
            this.toolsUsed = toolsUsed;
            this.executionMemory = executionMemory;
            this.iterations = iterations;
        }

        public List<String> getToolsUsed() { return toolsUsed; }
        public Map<String, Object> getExecutionMemory() { return executionMemory; }
        public int getIterations() { return iterations; }
    }
}
