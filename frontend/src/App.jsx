import React, { useState, useEffect } from 'react';
import ChatInterface from './components/ChatInterface';
import { apiService } from './services/api';
import './App.css';

function App() {
  const [apiHealth, setApiHealth] = useState(false);

  useEffect(() => {
    checkApiHealth();
    const interval = setInterval(checkApiHealth, 30000);
    return () => clearInterval(interval);
  }, []);

  const checkApiHealth = async () => {
    try {
      const isHealthy = await apiService.healthCheck();
      setApiHealth(isHealthy);
    } catch (error) {
      setApiHealth(false);
      console.error('Health check failed:', error);
    }
  };

  return (
    <div className="app">
      <nav className="app-nav">
        <div className="nav-brand">
          <h1>🤖 AI Data Lakehouse Assistant</h1>
          <span className="tech-stack">LLM • RAG • MCP • Iceberg</span>
        </div>
        <div className="nav-status">
          <span
            className={`status-indicator ${apiHealth ? 'online' : 'offline'}`}
            title={apiHealth ? 'Backend API is connected' : 'Backend API is unavailable'}
          ></span>
          <span className="status-text">
            {apiHealth ? '🟢 Connected' : '🔴 Disconnected'}
          </span>
        </div>
      </nav>

      <main className="app-main">
        <ChatInterface apiService={apiService} />
      </main>

      <footer className="app-footer">
        <p>
          © 2026 AI Data Lakehouse Platform | Apache Iceberg • LLM • RAG • MCP
        </p>
      </footer>
    </div>
  );
}

export default App;



