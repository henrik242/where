import { trackStore } from './src/store';
import type { Track, TrackUpdate } from './src/types';

export function createTestServer(port: number = 0) {
  const server = Bun.serve({
    port, // port 0 = random available port

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
      const filePath = url.pathname === '/' ? '/index.html' : url.pathname;
      const file = Bun.file(`${import.meta.dir}/src/public${filePath}`);

      if (await file.exists()) {
        return new Response(file);
      }

      return new Response('Not Found', { status: 404 });
    },

    websocket: {
      open(ws) {},
      message(ws, message) {},
      close(ws) {}
    }
  });

  async function handleAPI(req: Request): Promise<Response> {
    const url = new URL(req.url);
    const path = url.pathname;

    const headers = {
      'Content-Type': 'application/json',
      'Access-Control-Allow-Origin': '*',
      'Access-Control-Allow-Methods': 'GET, POST, PUT, DELETE, OPTIONS',
      'Access-Control-Allow-Headers': 'Content-Type, Authorization',
    };

    if (req.method === 'OPTIONS') {
      return new Response(null, { status: 204, headers });
    }

    try {
      // GET /api/tracks - Get all active tracks (or filtered by client IDs)
      if (path === '/api/tracks' && req.method === 'GET') {
        const clientIds = url.searchParams.get('clients')?.split(',').filter(Boolean) || [];
        const tracks = clientIds.length > 0
          ? trackStore.getTracksByClientIds(clientIds)
          : trackStore.getAllActiveTracks();
        return new Response(JSON.stringify(tracks), { headers });
      }

      // GET /api/tracks/:trackId - Get specific track
      if (path.match(/^\/api\/tracks\/[^\/]+$/) && req.method === 'GET') {
        const trackId = path.split('/')[3];
        const track = trackStore.getTrack(trackId);

        if (!track) {
          return new Response(JSON.stringify({ error: 'Track not found' }), {
            status: 404,
            headers
          });
        }

        return new Response(JSON.stringify(track), { headers });
      }

      // POST /api/tracks - Create new track
      if (path === '/api/tracks' && req.method === 'POST') {
        const body = await req.json() as Partial<Track>;

        if (!body.userId || !body.name) {
          return new Response(JSON.stringify({ error: 'Missing required fields' }), {
            status: 400,
            headers
          });
        }

        const track: Track = {
          id: body.id || crypto.randomUUID(),
          userId: body.userId,
          name: body.name,
          points: body.points || [],
          startTime: Date.now(),
          isActive: true
        };

        trackStore.saveTrack(track);

        return new Response(JSON.stringify(track), {
          status: 201,
          headers
        });
      }

      // POST /api/tracks/:trackId/points - Add point to track
      if (path.match(/^\/api\/tracks\/[^\/]+\/points$/) && req.method === 'POST') {
        const trackId = path.split('/')[3];
        const point = await req.json();

        const track = trackStore.getTrack(trackId);
        if (!track) {
          return new Response(JSON.stringify({ error: 'Track not found' }), {
            status: 404,
            headers
          });
        }

        const updatedTrack = trackStore.updateTrack(trackId, {
          points: [...track.points, point]
        });

        return new Response(JSON.stringify(updatedTrack), { headers });
      }

      // PUT /api/tracks/:trackId/stop - Stop a track
      if (path.match(/^\/api\/tracks\/[^\/]+\/stop$/) && req.method === 'PUT') {
        const trackId = path.split('/')[3];

        const updatedTrack = trackStore.updateTrack(trackId, {
          isActive: false,
          endTime: Date.now()
        });

        if (!updatedTrack) {
          return new Response(JSON.stringify({ error: 'Track not found' }), {
            status: 404,
            headers
          });
        }

        return new Response(JSON.stringify(updatedTrack), { headers });
      }

      // DELETE /api/tracks/:trackId - Delete a track
      if (path.match(/^\/api\/tracks\/[^\/]+$/) && req.method === 'DELETE') {
        const trackId = path.split('/')[3];
        const deleted = trackStore.deleteTrack(trackId);

        if (!deleted) {
          return new Response(JSON.stringify({ error: 'Track not found' }), {
            status: 404,
            headers
          });
        }

        return new Response(null, { status: 204, headers });
      }

      return new Response(JSON.stringify({ error: 'Not found' }), {
        status: 404,
        headers
      });

    } catch (error) {
      console.error('API Error:', error);
      return new Response(JSON.stringify({ error: 'Internal server error' }), {
        status: 500,
        headers
      });
    }
  }

  return server;
}

