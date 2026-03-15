import { handleAPI } from './src/api';
import { CONFIG } from './src/config';
import { trackStore } from './src/store';
import { enrichTrack, addSubscribedClient, removeSubscribedClient, startStaleTrackChecker } from './src/tracking';

const OG_META = `<meta name="description" content="Free, open-source outdoor navigation with offline maps, GPS tracking, and live location sharing. Available for iOS and Android.">

    <!-- Open Graph -->
    <meta property="og:title" content="Where? - Outdoor Navigation & Live Tracking">
    <meta property="og:description" content="Free, open-source outdoor navigation with offline maps, GPS tracking, and live location sharing. Available for iOS and Android.">
    <meta property="og:image" content="https://where.synth.no/og-image.png">
    <meta property="og:image:width" content="1200">
    <meta property="og:image:height" content="630">
    <meta property="og:image:alt" content="Where? app — free, open-source outdoor navigation with offline maps, GPS tracking, and live sharing">
    <meta property="og:url" content="https://where.synth.no/about">
    <meta property="og:type" content="website">
    <meta property="og:site_name" content="Where?">

    <!-- Twitter Card -->
    <meta name="twitter:card" content="summary_large_image">
    <meta name="twitter:title" content="Where? - Outdoor Navigation & Live Tracking">
    <meta name="twitter:description" content="Free, open-source outdoor navigation with offline maps, GPS tracking, and live location sharing. Available for iOS and Android.">
    <meta name="twitter:image" content="https://where.synth.no/og-image.png">
    <meta name="twitter:image:alt" content="Where? app — free, open-source outdoor navigation with offline maps, GPS tracking, and live sharing">

    <meta name="theme-color" content="#1a73e8">`;

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
      if (filePath.endsWith('.html')) {
        const html = await file.text();
        return new Response(html.replace('<!-- OG_META -->', OG_META), {
          headers: { 'Content-Type': 'text/html; charset=utf-8' },
        });
      }
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

          const payload: any = {
            type: 'initial_state',
            tracks: tracks.map(enrichTrack),
            admin: isAdmin,
          };
          if (isAdmin) {
            payload.sessionStats = trackStore.getSessionStats();
          }
          ws.send(JSON.stringify(payload));
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
