import { handleAPI } from './src/api';
import { CONFIG } from './src/config';
import { trackStore } from './src/store';
import { enrichTrack, addSubscribedClient, removeSubscribedClient, startStaleTrackChecker } from './src/tracking';

interface WsData {
  clients: string[];
  admin: boolean;
}

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

    // Redirects
    if (url.pathname === '/ios') {
      return Response.redirect('https://apps.apple.com/app/where/id6760362061', 302);
    }
    if (url.pathname === '/android') {
      return Response.redirect('https://play.google.com/store/apps/details?id=no.synth.where', 302);
    }

    // Serve static files
    let filePath = url.pathname;
    if (filePath === '/') {
      filePath = '/index.html';
    }
    if (filePath === '/privacy') {
      filePath = '/privacy.html';
    }
    if (filePath === '/about') {
      filePath = '/about.html';
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
      ws.data = { clients: [], admin: false } as WsData;
    },

    message(ws, message) {
      try {
        const msg = JSON.parse(String(message));
        if (msg.type === 'subscribe') {
          const clients: string[] = Array.isArray(msg.clients) ? msg.clients : [];
          const isAdmin = !!(CONFIG.ADMIN_KEY && msg.adminKey === CONFIG.ADMIN_KEY);
          const includeHistorical = msg.historical === true;
          ws.data = { clients, admin: isAdmin } as WsData;
          addSubscribedClient(ws);

          let tracks;
          if (isAdmin) {
            tracks = includeHistorical ? trackStore.getAllTracks() : trackStore.getAllActiveTracks();
          } else if (clients.length === 0) {
            tracks = [];
          } else {
            tracks = trackStore.getTracksByClientIds(clients, includeHistorical);
          }

          ws.send(JSON.stringify({
            type: 'initial_state',
            tracks: tracks.map(enrichTrack),
            admin: isAdmin,
          }));
        }
      } catch (e) {
        console.error('Failed to parse WebSocket message:', e);
      }
    },

    close(ws) {
      console.log('WebSocket client disconnected');
      removeSubscribedClient(ws);
    },
  },
});

// Start background job for stale track checking
startStaleTrackChecker();

// Log server info
console.log(`🚀 Where Web running at http://localhost:${server.port}`);
console.log(`📡 WebSocket available at ws://localhost:${server.port}/ws`);
console.log(`🌐 Web interface at http://localhost:${server.port}`);
console.log(`🔑 Admin: ${CONFIG.ADMIN_KEY ? 'ENABLED' : 'DISABLED'}`);
console.log(`🔒 HMAC verification: ${CONFIG.TRACKING_HINT ? 'ENABLED' : 'DISABLED'}`);
console.log(`⏱️  Auto-stop inactive tracks after 10 minutes`);
