import { TrackStore } from './src/store';
import type { Track } from './src/types';
import { validatePoint } from './src/utils';

export function createTestServer(port: number = 0) {
  const trackStore = new TrackStore(':memory:');

  const headers = {
    'Content-Type': 'application/json',
    'Access-Control-Allow-Origin': '*',
    'Access-Control-Allow-Methods': 'GET, POST, PUT, DELETE, OPTIONS',
    'Access-Control-Allow-Headers': 'Content-Type, Authorization, X-Client-Id, X-Admin-Key, X-Signature',
  };

  function jsonResponse(data: any, status = 200): Response {
    return new Response(JSON.stringify(data), { status, headers });
  }

  function isAuthorized(req: Request, track: Track): boolean {
    const clientId = req.headers.get('X-Client-Id');
    if (clientId === track.userId) return true;
    return false;
  }

  async function handleAPI(req: Request): Promise<Response> {
    const url = new URL(req.url);
    const path = url.pathname;

    if (req.method === 'OPTIONS') {
      return new Response(null, { status: 204, headers });
    }

    try {
      // Parse body for mutating requests (skip HMAC in test server)
      if (req.method === 'POST' || req.method === 'PUT') {
        const bodyText = await req.text();
        if (bodyText) {
          (req as any).parsedBody = JSON.parse(bodyText);
        }
      }

      // GET /api/tracks
      if (path === '/api/tracks' && req.method === 'GET') {
        const clientIds = url.searchParams.get('clients')?.split(',').filter(Boolean) || [];
        const includeHistorical = url.searchParams.get('historical') === 'true';

        let tracks: Track[];
        if (clientIds.length > 0) {
          tracks = trackStore.getTracksByClientIds(clientIds, includeHistorical);
        } else {
          tracks = [];
        }
        return jsonResponse(tracks);
      }

      // GET /api/tracks/:trackId
      if (path.match(/^\/api\/tracks\/[^\/]+$/) && req.method === 'GET') {
        const trackId = path.split('/')[3];
        const track = trackStore.getTrack(trackId);
        if (!track) return jsonResponse({ error: 'Track not found' }, 404);
        return jsonResponse(track);
      }

      // POST /api/tracks
      if (path === '/api/tracks' && req.method === 'POST') {
        const body = (req as any).parsedBody as Partial<Track>;

        if (!body.userId) {
          return jsonResponse({ error: 'Missing required fields' }, 400);
        }

        const clientIdHeader = req.headers.get('X-Client-Id');
        if (!clientIdHeader || clientIdHeader !== body.userId) {
          return jsonResponse({ error: 'Unauthorized' }, 401);
        }

        const initialPoints = body.points || [];
        if (initialPoints.some((p: any) => !validatePoint(p))) {
          return jsonResponse({ error: 'Invalid point data' }, 400);
        }

        const track: Track = {
          id: crypto.randomUUID(),
          userId: body.userId,
          name: body.name || '',
          points: initialPoints,
          startTime: Date.now(),
          isActive: true,
          lastUpdateTime: Date.now(),
        };

        trackStore.saveTrack(track);
        const savedTrack = trackStore.getTrack(track.id);
        return jsonResponse(savedTrack ?? track, 201);
      }

      // POST /api/tracks/:trackId/points
      if (path.match(/^\/api\/tracks\/[^\/]+\/points$/) && req.method === 'POST') {
        const trackId = path.split('/')[3];
        const point = (req as any).parsedBody;

        if (!validatePoint(point)) {
          return jsonResponse({ error: 'Invalid point data' }, 400);
        }

        const track = trackStore.getTrack(trackId);
        if (!track) return jsonResponse({ error: 'Track not found' }, 404);

        if (!isAuthorized(req, track)) {
          return jsonResponse({ error: 'Unauthorized' }, 401);
        }

        const updates: Partial<Track> = {
          isActive: true,
          lastUpdateTime: Date.now(),
        };
        if (!track.isActive) {
          updates.endTime = undefined;
        }

        const updatedTrack = trackStore.addPoint(trackId, point, updates);
        return jsonResponse(updatedTrack);
      }

      // PUT /api/tracks/:trackId/stop
      if (path.match(/^\/api\/tracks\/[^\/]+\/stop$/) && req.method === 'PUT') {
        const trackId = path.split('/')[3];
        const track = trackStore.getTrack(trackId);
        if (!track) return jsonResponse({ error: 'Track not found' }, 404);

        if (!isAuthorized(req, track)) {
          return jsonResponse({ error: 'Unauthorized' }, 401);
        }

        const updatedTrack = trackStore.updateTrack(trackId, {
          isActive: false,
          endTime: Date.now(),
        });
        return jsonResponse(updatedTrack);
      }

      // DELETE /api/tracks/:trackId
      if (path.match(/^\/api\/tracks\/[^\/]+$/) && req.method === 'DELETE') {
        const trackId = path.split('/')[3];
        const track = trackStore.getTrack(trackId);
        if (!track) return jsonResponse({ error: 'Track not found' }, 404);

        if (!isAuthorized(req, track)) {
          return jsonResponse({ error: 'Unauthorized' }, 401);
        }

        trackStore.deleteTrack(trackId);
        return new Response(null, { status: 204, headers });
      }

      return jsonResponse({ error: 'Not found' }, 404);
    } catch (error) {
      console.error('API Error:', error);
      return jsonResponse({ error: 'Internal server error' }, 500);
    }
  }

  return Bun.serve({
    port,

    async fetch(req, server) {
      const url = new URL(req.url);

      if (url.pathname === '/ws') {
        const upgraded = server.upgrade(req);
        if (!upgraded) {
          return new Response('WebSocket upgrade failed', { status: 400 });
        }
        return undefined;
      }

      if (url.pathname.startsWith('/api')) {
        return handleAPI(req);
      }

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
      close(ws) {},
    },
  });
}
