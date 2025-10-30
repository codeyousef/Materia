/**
 * Materia Web Tools - Main Entry Point
 * Provides the central hub for all development tools
 */

import './styles/main.css';

class MateriaWebTools {
  constructor() {
    this.ws = null;
    this.tools = new Map();
    this.init();
  }

  async init() {
    try {
      await this.loadConfiguration();
      await this.loadTools();
      this.initializeWebSocket();
      this.setupEventListeners();
      console.log('Materia Web Tools initialized successfully');
    } catch (error) {
      console.error('Failed to initialize Materia Web Tools:', error);
      this.showError('Initialization failed. Please refresh the page.');
    }
  }

  async loadConfiguration() {
    try {
      const response = await fetch('/api/health');
      const config = await response.json();
      this.version = config.version;
      console.log(`Materia Tools v${this.version} starting...`);
    } catch (error) {
      console.warn('Could not load configuration:', error);
    }
  }

  async loadTools() {
    try {
      const response = await fetch('/api/tools');
      const data = await response.json();

      data.tools.forEach(tool => {
        this.tools.set(tool.id, tool);
      });

      console.log(`Loaded ${this.tools.size} development tools`);
    } catch (error) {
      console.error('Failed to load tools:', error);
      throw error;
    }
  }

  initializeWebSocket() {
    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
    this.ws = new WebSocket(`${protocol}//${window.location.host}`);

    this.ws.onopen = () => {
      console.log('WebSocket connected to Materia Tools server');
      this.sendMessage({
        type: 'client-connect',
        timestamp: new Date().toISOString(),
        userAgent: navigator.userAgent
      });
    };

    this.ws.onmessage = (event) => {
      try {
        const message = JSON.parse(event.data);
        this.handleWebSocketMessage(message);
      } catch (error) {
        console.error('WebSocket message parse error:', error);
      }
    };

    this.ws.onclose = () => {
      console.log('WebSocket connection closed, attempting to reconnect...');
      setTimeout(() => this.initializeWebSocket(), 3000);
    };

    this.ws.onerror = (error) => {
      console.error('WebSocket error:', error);
    };
  }

  handleWebSocketMessage(message) {
    switch (message.type) {
      case 'tool-event':
        this.handleToolEvent(message);
        break;
      case 'system-notification':
        this.showNotification(message.content);
        break;
      case 'tool-status-update':
        this.updateToolStatus(message.toolId, message.status);
        break;
      default:
        console.log('Unknown message type:', message.type);
    }
  }

  handleToolEvent(event) {
    console.log('Tool event received:', event);

    // Dispatch custom events for tool communication
    const customEvent = new CustomEvent('materia-tool-event', {
      detail: event
    });
    window.dispatchEvent(customEvent);
  }

  sendMessage(message) {
    if (this.ws && this.ws.readyState === WebSocket.OPEN) {
      this.ws.send(JSON.stringify(message));
    }
  }

  updateToolStatus(toolId, status) {
    const tool = this.tools.get(toolId);
    if (tool) {
      tool.status = status;
      // Update UI if needed
      this.refreshToolDisplay(toolId);
    }
  }

  refreshToolDisplay(toolId) {
    // Implementation would update the tool card in the UI
    console.log(`Refreshing display for tool: ${toolId}`);
  }

  setupEventListeners() {
    // Global error handler
    window.addEventListener('error', (event) => {
      console.error('Global error:', event.error);
      this.sendMessage({
        type: 'client-error',
        error: {
          message: event.error.message,
          stack: event.error.stack,
          url: event.filename,
          line: event.lineno
        }
      });
    });

    // Performance monitoring
    if ('performance' in window && 'navigation' in performance) {
      window.addEventListener('load', () => {
        setTimeout(() => {
          const perfData = performance.getEntriesByType('navigation')[0];
          this.sendMessage({
            type: 'performance-metrics',
            metrics: {
              loadTime: perfData.loadEventEnd - perfData.loadEventStart,
              domContentLoaded: perfData.domContentLoadedEventEnd - perfData.domContentLoadedEventStart,
              totalTime: perfData.loadEventEnd - perfData.fetchStart
            }
          });
        }, 1000);
      });
    }

    // Tool integration events
    window.addEventListener('materia-tool-message', (event) => {
      this.sendMessage({
        type: 'tool-event',
        source: event.detail.source,
        data: event.detail.data,
        timestamp: new Date().toISOString()
      });
    });
  }

  showNotification(message) {
    if ('Notification' in window && Notification.permission === 'granted') {
      new Notification('Materia Tools', {
        body: message,
        icon: '/favicon.ico'
      });
    } else {
      console.log('Notification:', message);
    }
  }

  showError(message) {
    console.error(message);
    // Could show a toast notification or modal
  }

  // Public API for tools to use
  static getInstance() {
    if (!window.materiaTools) {
      window.materiaTools = new MateriaWebTools();
    }
    return window.materiaTools;
  }
}

// Initialize the main application
document.addEventListener('DOMContentLoaded', () => {
  window.materiaTools = MateriaWebTools.getInstance();
});

// Export for use by other tools
export default MateriaWebTools;