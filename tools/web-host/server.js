const express = require('express');
const cors = require('cors');
const helmet = require('helmet');
const compression = require('compression');
const path = require('path');
const { createServer } = require('http');
const { WebSocketServer } = require('ws');

const app = express();
const server = createServer(app);
const wss = new WebSocketServer({ server });

// Security middleware
app.use(helmet({
  contentSecurityPolicy: {
    directives: {
      defaultSrc: ["'self'"],
      scriptSrc: ["'self'", "'unsafe-inline'", "'unsafe-eval'"],
      styleSrc: ["'self'", "'unsafe-inline'"],
      imgSrc: ["'self'", "data:", "blob:"],
      connectSrc: ["'self'", "ws:", "wss:"],
      workerSrc: ["'self'", "blob:"]
    }
  }
}));

// CORS configuration
app.use(cors({
  origin: process.env.NODE_ENV === 'production'
    ? ['https://tools.materia.dev', 'https://materia.dev']
    : ['http://localhost:3000', 'http://localhost:8080'],
  credentials: true
}));

app.use(compression());
app.use(express.json({ limit: '50mb' }));
app.use(express.static(path.join(__dirname, 'dist')));

// API routes
app.get('/api/health', (req, res) => {
  res.json({
    status: 'healthy',
    timestamp: new Date().toISOString(),
    version: process.env.npm_package_version || '1.0.0'
  });
});

app.get('/api/tools', (req, res) => {
  res.json({
    tools: [
      {
        id: 'scene-editor',
        name: 'Scene Editor',
        description: 'Visual scene composition and object manipulation',
        url: '/tools/editor',
        status: 'active'
      },
      {
        id: 'material-editor',
        name: 'Material Editor',
        description: 'WGSL shader editor with live preview',
        url: '/tools/editor#materials',
        status: 'active'
      },
      {
        id: 'animation-editor',
        name: 'Animation Editor',
        description: 'Timeline-based animation system',
        url: '/tools/editor#animation',
        status: 'active'
      },
      {
        id: 'performance-profiler',
        name: 'Performance Profiler',
        description: 'Real-time performance monitoring and analysis',
        url: '/tools/profiler',
        status: 'active'
      },
      {
        id: 'documentation',
        name: 'Documentation',
        description: 'Interactive API documentation and examples',
        url: '/docs',
        status: 'active'
      }
    ]
  });
});

// WebSocket for real-time tool communication
wss.on('connection', (ws, req) => {
  console.log('Tool client connected:', req.url);

  ws.on('message', (data) => {
    try {
      const message = JSON.parse(data);

      // Broadcast tool events to other connected clients
      if (message.type === 'tool-event') {
        wss.clients.forEach(client => {
          if (client !== ws && client.readyState === client.OPEN) {
            client.send(JSON.stringify(message));
          }
        });
      }
    } catch (error) {
      console.error('WebSocket message error:', error);
    }
  });

  ws.on('close', () => {
    console.log('Tool client disconnected');
  });
});

// Serve tools
app.use('/tools/editor', express.static(path.join(__dirname, 'dist/tools/editor')));
app.use('/tools/profiler', express.static(path.join(__dirname, 'dist/tools/profiler')));
app.use('/docs', express.static(path.join(__dirname, 'dist/docs')));

// SPA fallback
app.get('*', (req, res) => {
  res.sendFile(path.join(__dirname, 'dist', 'index.html'));
});

const PORT = process.env.PORT || 3000;
server.listen(PORT, () => {
  console.log(`Materia Web Tools server running on port ${PORT}`);
  console.log(`Environment: ${process.env.NODE_ENV || 'development'}`);
});

// Graceful shutdown
process.on('SIGTERM', () => {
  console.log('SIGTERM received, shutting down gracefully');
  server.close(() => {
    console.log('Process terminated');
  });
});