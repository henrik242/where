import { handleAPI } from './src/api';
import { CONFIG } from './src/config';
import { setServerInstance, startStaleTrackChecker } from './src/tracking';

const server = Bun.serve({
  port: CONFIG.PORT,

  async fetch(req, server) {
    const url = new URL(req.url);

    // WebSocket upgrade
    if (url.pathname === '/ws') {
      const upgraded = server.upgrade(req);
      if (!upgraded) {
        return new Response('WebSocket upgrade failed', { status: 400 });
      }
      return undefined;
    }

    // API Routes
    if (url.pathname.startsWith('/api')) {
      return handleAPI(req);
    }

    // Serve static files
    let filePath = url.pathname;
    if (filePath === '/') {
      filePath = '/index.html';
    }
    if (filePath === '/privacy') {
      filePath = '/privacy.html';
    }
    const file = Bun.file(`${import.meta.dir}/src/public${filePath}`);

    if (await file.exists()) {
      return new Response(file);
    }

    return new Response('Not Found', { status: 404 });
  },

  websocket: {
    open(ws) {
      console.log('WebSocket client connected');
      ws.subscribe('tracking');
    },

    message(ws, message) {
      console.log('WebSocket message:', message);
    },

    close(ws) {
      console.log('WebSocket client disconnected');
      ws.unsubscribe('tracking');
    },
  },
});

// Set server instance for broadcasting
setServerInstance(server);

// Start background job for stale track checking
startStaleTrackChecker();

// Log server info
console.log(`🚀 Where Server running at http://localhost:${server.port}`);
console.log(`📡 WebSocket available at ws://localhost:${server.port}/ws`);
console.log(`🌐 Web interface at http://localhost:${server.port}`);
console.log(`🔑 Admin: ${CONFIG.ADMIN_KEY ? 'ENABLED' : 'DISABLED'}`);
console.log(`🔒 HMAC verification: ${CONFIG.TRACKING_HMAC_SECRET ? 'ENABLED' : 'DISABLED'}`);
console.log(`⏱️  Auto-stop inactive tracks after 10 minutes`);
