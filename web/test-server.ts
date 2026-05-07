import { TrackStore } from './src/server/store';
import { createApiHandler } from './src/server/api';

/**
 * Spin up a minimal Bun.serve instance backed by an in-memory store, reusing the
 * production API handler with HMAC verification disabled. Used by tests that
 * need an HTTP boundary without managing the on-disk DB.
 */
export function createTestServer(port: number = 0) {
  const trackStore = new TrackStore(':memory:');

  const handleAPI = createApiHandler({
    store: trackStore,
    verifySignature: async () => true,
    broadcast: () => {},
    getAdminKey: () => undefined,
  });

  return Bun.serve({
    port,

    async fetch(req, server) {
      const url = new URL(req.url);

      if (url.pathname === '/ws') {
        return server.upgrade(req)
          ? undefined
          : new Response('WebSocket upgrade failed', { status: 400 });
      }

      // Test-only: expose session stats over HTTP for verification
      if (url.pathname === '/api/session-stats' && req.method === 'GET') {
        return new Response(JSON.stringify(trackStore.getSessionStats()), {
          headers: { 'Content-Type': 'application/json' },
        });
      }

      if (url.pathname.startsWith('/api')) {
        return handleAPI(req);
      }

      const filePath = url.pathname === '/' ? '/index.html' : url.pathname;
      const file = Bun.file(`${import.meta.dir}/src/client${filePath}`);

      if (await file.exists()) {
        return new Response(file);
      }

      return new Response('Not Found', { status: 404 });
    },

    websocket: {
      open() {},
      message() {},
      close() {},
    },
  });
}
