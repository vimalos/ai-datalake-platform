const API_BASE_URL = process.env.REACT_APP_API_URL || 'http://localhost:8080/api';

/**
 * API Service for AI Data Lakehouse Platform
 *
 * Architecture Flow:
 * UI -> ChatController -> AIOrchestrationService -> AI Agent -> MCPToolExecutor -> Tools
 *
 * Components:
 * - ChatController: REST endpoint (/api/chat/query)
 * - AIOrchestrationService: Main orchestrator
 * - AI Agent: Autonomous planning and execution (Plan-Execute-Reflect)
 * - MCPToolExecutor: Direct tool execution
 * - MCPProtocolServer: JSON-RPC 2.0 protocol server (for external MCP clients)
 * - RAG Service: Knowledge base retrieval
 * - LLM Service: Ollama integration
 *
 * All interactions go through natural language chat interface.
 * The AI Agent autonomously plans and executes tasks using MCP tools.
 */
export const apiService = {
  /**
   * Main chat endpoint - handles ALL user queries through AI orchestration
   * @param {string} message - Natural language query from user
   * @param {string} database - Target database (optional, defaults to 'default')
   * @param {string} table - Target table (optional)
   * @param {string} conversationId - Conversation ID for context (optional)
   * @param {boolean} streamResponse - Enable streaming (optional, default false)
   * @returns {Promise<ChatResponse>} AI-generated response with tool execution results
   */
  async sendChatMessage(message, database = 'default', table = null, conversationId = null, streamResponse = false) {
    try {
      const response = await fetch(`${API_BASE_URL}/chat/query`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          message,
          database,
          table,
          conversationId,
          streamResponse,
        }),
      });
      if (!response.ok) throw new Error(`Chat API error: ${response.statusText}`);
      return await response.json();
    } catch (error) {
      console.error('Chat message error:', error);
      throw error;
    }
  },

  /**
   * Health check endpoint
   * @returns {Promise<boolean>} Service health status
   */
  async healthCheck() {
    try {
      const response = await fetch(`${API_BASE_URL}/chat/health`);
      return response.ok;
    } catch (error) {
      console.warn('Health check failed:', error);
      return false;
    }
  },

  // ==========================================
  // Helper methods for common queries
  // These are convenience wrappers around sendChatMessage
  // ==========================================

  /**
   * List all databases using natural language query
   */
  async listDatabases() {
    return this.sendChatMessage('List all databases');
  },

  /**
   * List tables in a database using natural language query
   */
  async listTables(database) {
    return this.sendChatMessage(`List all tables in database ${database}`, database);
  },

  /**
   * Get table schema using natural language query
   */
  async getTableSchema(database, table) {
    return this.sendChatMessage(`Show schema for table ${table}`, database, table);
  },

  /**
   * Get table statistics using natural language query
   */
  async getTableStats(database, table) {
    return this.sendChatMessage(`Show statistics for table ${table}`, database, table);
  },

  /**
   * Get table metadata using natural language query
   */
  async getTableMetadata(database, table) {
    return this.sendChatMessage(`Show metadata for table ${table}`, database, table);
  },

  /**
   * Run table compaction using natural language query
   */
  async compactTable(database, table) {
    return this.sendChatMessage(`Compact table ${table}`, database, table);
  },

  /**
   * Run full maintenance on a table using natural language query
   */
  async runFullMaintenance(database, table) {
    return this.sendChatMessage(`Run full maintenance on table ${table}`, database, table);
  },

  /**
   * Get optimization analysis using natural language query
   */
  async getOptimizationAnalysis(database, table) {
    return this.sendChatMessage(`Analyze optimization opportunities for table ${table}`, database, table);
  },

  /**
   * Get schema analysis using natural language query
   */
  async getSchemaAnalysis(database, table) {
    return this.sendChatMessage(`Analyze schema for table ${table}`, database, table);
  },

  /**
   * Get query performance advice using natural language query
   */
  async getQueryPerformanceAdvice(database, table, queryPattern = '') {
    const query = queryPattern
      ? `How can I optimize this query: ${queryPattern} for table ${table}?`
      : `Give me query performance advice for table ${table}`;
    return this.sendChatMessage(query, database, table);
  },

  /**
   * Get troubleshooting guide using natural language query
   */
  async getTroubleshootingGuide(database, table, issue = '') {
    const query = issue
      ? `Help me troubleshoot this issue with table ${table}: ${issue}`
      : `Help me troubleshoot table ${table}`;
    return this.sendChatMessage(query, database, table);
  },

  /**
   * Get job optimization advice using natural language query
   */
  async getJobOptimization(database, table, jobType = '') {
    const query = jobType
      ? `How can I optimize ${jobType} jobs for table ${table}?`
      : `Give me job optimization advice for table ${table}`;
    return this.sendChatMessage(query, database, table);
  },
};

