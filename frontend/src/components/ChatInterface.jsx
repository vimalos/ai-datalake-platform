import React, { useState, useRef, useEffect } from 'react';
import { apiService } from '../services/api';
import './ChatInterface.css';

const ChatInterface = () => {
  const [messages, setMessages] = useState([
    { text: 'Welcome! Ask me anything about your data.', sender: 'system', timestamp: new Date() }
  ]);
  const [input, setInput] = useState('');
  const [database, setDatabase] = useState('analytics');
  const [loading, setLoading] = useState(false);
  const [activeInfoTab, setActiveInfoTab] = useState('guide');
  const messagesEndRef = useRef(null);

  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages]);

  const formatMessageContent = (text) => {
    if (!text) return 'No response';
    const lines = text.split(/\r?\n/);
    let html = '';
    let listBuffer = [];

    const flushList = () => {
      if (listBuffer.length) {
        html += '<ul>' + listBuffer.map((li) => `<li>${li}</li>`).join('') + '</ul>';
        listBuffer = [];
      }
    };

    const isTableLine = (line) => line.includes('|');
    let i = 0;
    while (i < lines.length) {
      const line = lines[i].trim();
      const next = (lines[i + 1] || '').trim();

      if (!line) {
        flushList();
        html += '<br/>';
        i += 1;
        continue;
      }

      if (isTableLine(line) && next.includes('---')) {
        flushList();
        const headerCells = line.split('|').map((c) => c.trim()).filter(Boolean);
        const rows = [];
        i += 2;
        while (i < lines.length && isTableLine(lines[i])) {
          const rowCells = lines[i].split('|').map((c) => c.trim()).filter(Boolean);
          if (rowCells.length) rows.push(rowCells);
          i += 1;
        }
        html += '<table class="message-table"><thead><tr>';
        headerCells.forEach((h) => { html += `<th>${h}</th>`; });
        html += '</tr></thead><tbody>';
        rows.forEach((row) => {
          html += '<tr>';
          row.forEach((c) => { html += `<td>${c}</td>`; });
          html += '</tr>';
        });
        html += '</tbody></table>';
        continue;
      }

      const cleaned = line
        .replace(/^\+\s*/g, '')
        .replace(/\*\*(.+?)\*\*/g, '$1')
        .replace(/\*(.+?)\*/g, '$1');

      if (cleaned.toLowerCase().startsWith('summary:')) {
        flushList();
        html += `<h3>${cleaned}</h3>`;
      } else if (/^[A-Za-z_\s]+:\s*$/.test(cleaned)) {
        flushList();
        html += `<h3>${cleaned}</h3>`;
      } else if (cleaned.startsWith('### ')) {
        flushList();
        html += `<h3>${cleaned.slice(4)}</h3>`;
      } else if (cleaned.startsWith('## ')) {
        flushList();
        html += `<h2>${cleaned.slice(3)}</h2>`;
      } else if (cleaned.startsWith('- ')) {
        listBuffer.push(cleaned.slice(2));
      } else {
        flushList();
        html += `<p>${cleaned}</p>`;
      }
      i += 1;
    }

    flushList();
    return html;
  };

  const extractMaintenanceSummary = (text) => {
    if (!text) return null;
    const lines = text.split(/\r?\n/).map((l) => l.trim()).filter(Boolean);
    const summaryLine = lines.find((l) => l.toLowerCase().startsWith('summary:'));
    if (summaryLine) {
      return summaryLine.replace(/^summary:\s*/i, '');
    }
    const fallback = lines.find((l) =>
      /expired snapshots for|compaction completed for|removed orphan files for|full maintenance for|created table|inserted .* row/i.test(l)
    );
    return fallback || null;
  };

  const handleSendMessage = async () => {
    if (!input.trim()) return;

    setMessages(prev => [...prev, { text: input, sender: 'user', timestamp: new Date() }]);
    setInput('');
    setLoading(true);

    try {
      const data = await apiService.sendChatMessage(input, database, null, `conv_${Date.now()}`, false);
      const formattedMessage = data.message || 'No response';
      setMessages(prev => [...prev, { text: formattedMessage, sender: 'bot', timestamp: new Date(), isHtml: true }]);
    } catch (error) {
      setMessages(prev => [...prev, { text: `Error: ${error.message}`, sender: 'bot', timestamp: new Date() }]);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="app-container">
      <div className="sidebar">
        <div className="sidebar-header">
          <h2>📚 Info</h2>
        </div>
        <div className="sidebar-tabs">
          <button className={`tab-btn ${activeInfoTab === 'guide' ? 'active' : ''}`} onClick={() => setActiveInfoTab('guide')}>
            📖 How to Use
          </button>
          <button className={`tab-btn ${activeInfoTab === 'tech' ? 'active' : ''}`} onClick={() => setActiveInfoTab('tech')}>
            🏗️ Tech Stack
          </button>
        </div>
        <div className="sidebar-content">
          {activeInfoTab === 'guide' && (
            <div>
              <h3>How to Use</h3>
              <ol>
                <li><strong>Ask Questions in Natural Language (Agentic AI interprets intent)</strong></li>
                <li><strong>Get AI-Powered Answers and Iceberg actions (maintenance, compaction, cleanup)</strong></li>
              </ol>
              <h4>What You Can Ask:</h4>
              <ul>
                <li>🔍 <strong>Discover:</strong> "What databases are available?"</li>
                <li>📋 <strong>Explore:</strong> "List tables in analytics"</li>
                <li>🧱 <strong>Schema:</strong> "Show schema for users table"</li>
                <li>📊 <strong>Stats:</strong> "How many records in users?"</li>
                <li>📄 <strong>Data:</strong> "Show 5 rows from users"</li>
                <li>⚙️ <strong>Maintenance:</strong> "Does users table need maintenance?"</li>
                <li>🛠️ <strong>Action:</strong> "Compact users table"</li>
                <li>🧹 <strong>Cleanup:</strong> "Expire old snapshots for users"</li>
                <li>✨ <strong>Create:</strong> "Create table sampleai with 3 fields"</li>
                <li>📝 <strong>Insert:</strong> "Insert 10 records into sampleai"</li>
              </ul>
            </div>
          )}
          {activeInfoTab === 'tech' && (
            <div>
              <h3>Technology Stack</h3>
              <div style={{marginBottom: '10px'}}>
                <strong>🧠 LLM</strong>
                <p>Ollama - Local Language Model</p>
              </div>
              <div style={{marginBottom: '10px'}}>
                <strong>📚 RAG</strong>
                <p>Retrieval-Augmented Generation</p>
              </div>
              <div style={{marginBottom: '10px'}}>
                <strong>⚙️ MCP</strong>
                <p>Iceberg MCP Server + MCP Client (tool execution)</p>
              </div>
              <div style={{marginBottom: '10px'}}>
                <strong>📊 Iceberg</strong>
                <p>Apache Iceberg Lakehouse</p>
              </div>
            </div>
          )}
        </div>
      </div>

      <div className="chat-section">
        <div className="chat-header">
          <div className="header-left">
            <h1>🤖 AI Data Lakehouse Assistant</h1>
            <div style={{fontSize: '14px', color: '#ffaa00', marginTop: '5px'}}>
              <b>LLM • RAG • Iceberg MCP Server</b>
            </div>
          </div>
          <div className="header-right">
            <div className="control-group">
              <label>Database:</label>
              <select value={database} onChange={(e) => setDatabase(e.target.value)} className="select-input">
                <option>analytics</option>
                <option>default</option>
              </select>
            </div>
          </div>
        </div>

        <div className="messages-area">
          {messages.length === 0 ? (
            <div className="empty-state">
              <p>👋 Welcome to AI Data Lakehouse Assistant</p>
              <p>Select a database and ask your questions</p>
            </div>
          ) : (
            messages.map((msg, i) => {
              const maintenanceSummary = msg.isHtml ? extractMaintenanceSummary(msg.text) : null;
              return (
                <div key={i} className={`message message-${msg.sender}`}>
                  {maintenanceSummary && (
                    <div className="maintenance-summary">
                      <strong>Maintenance Summary:</strong> {maintenanceSummary}
                    </div>
                  )}
                  {msg.isHtml ? (
                    <div className="message-content" dangerouslySetInnerHTML={{ __html: formatMessageContent(msg.text) }} />
                  ) : (
                    <div className="message-content">{msg.text}</div>
                  )}
                  {msg.timestamp && <div className="message-time">{msg.timestamp.toLocaleTimeString()}</div>}
                </div>
              );
            })
          )}
          {loading && (
            <div className="message message-bot">
              <div className="message-content">⏳ Processing...</div>
            </div>
          )}
          <div ref={messagesEndRef} />
        </div>

        <div className="input-area">
          <input
            type="text"
            value={input}
            onChange={(e) => setInput(e.target.value)}
            onKeyPress={(e) => e.key === 'Enter' && handleSendMessage()}
            placeholder="Ask about your data... (press Enter)"
            disabled={loading}
            className="message-input"
          />
          <button onClick={handleSendMessage} disabled={loading || !input.trim()} className="send-btn">
            {loading ? '⏳' : '➤'}
          </button>
        </div>
      </div>

      <div className="examples-panel">
        <div className="examples-header">
          <h2>💡 Quick Action for AI Agent</h2>
        </div>
        <div className="examples-content">
          <button onClick={() => setInput('What databases are available?')} className="example-btn">
            📌 Databases
          </button>
          <button onClick={() => setInput('List tables in analytics')} className="example-btn">
            📋 Tables
          </button>
          <button onClick={() => setInput('Show schema for users')} className="example-btn">
            🧱 Schema
          </button>
          <button onClick={() => setInput('How many records in users table?')} className="example-btn">
            📊 Stats
          </button>
          <button onClick={() => setInput('Show 10 rows from users')} className="example-btn">
            📄 Data
          </button>
          <button onClick={() => setInput('Create table products with 4 fields')} className="example-btn">
            ✨ Create Table
          </button>
          <button onClick={() => setInput('Insert 5 records into products')} className="example-btn">
            📝 Insert Rows
          </button>
          <button onClick={() => setInput('Does users table need maintenance?')} className="example-btn">
            ⚙️ Maintenance
          </button>
          <button onClick={() => setInput('Compact users table')} className="example-btn">
            🛠️ Compact
          </button>
          <hr style={{margin: '10px 0', border: 'none', borderTop: '1px solid #ddd'}} />
          <div style={{fontSize: '11px', fontWeight: '600', color: '#667eea', margin: '8px 0 6px 0'}}>
            LLM • RAG (No Tools)
          </div>
          <button onClick={() => setInput('What is Apache Iceberg?')} className="example-btn">
            📖 What is Iceberg?
          </button>
          <button onClick={() => setInput('Why use MCP with Iceberg?')} className="example-btn">
            ❓ Why MCP?
          </button>
          <button onClick={() => setInput('How does snapshot expiration work?')} className="example-btn">
            🔄 Snapshots
          </button>
          <button onClick={() => setInput('Explain table partitioning')} className="example-btn">
            📊 Partitioning
          </button>
          <button onClick={() => setInput('What are the benefits of Iceberg?')} className="example-btn">
            ⭐ Benefits
          </button>
        </div>
      </div>
    </div>
  );
};

export default ChatInterface;

