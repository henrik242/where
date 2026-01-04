import { trackStore } from './src/store';
import type { Track } from './src/types';

const GOD_MODE_KEY = process.env.GOD_MODE_KEY;

async function reverseGeocode(lat: number, lon: number): Promise<string> {
  try {
    const response = await fetch(
      `https://nominatim.openstreetmap.org/reverse?lat=${lat}&lon=${lon}&format=json&addressdetails=1`,
      {
        headers: {
          'User-Agent': 'Where-App/1.0'
        }
      }
    );

    if (!response.ok) return 'Unnamed Track';

    const data = await response.json();
    const addr = data.address;

    // Try to build a nice name from available data
    if (addr.road) {
      return addr.road;
    } else if (addr.village || addr.town || addr.city) {
      return addr.village || addr.town || addr.city;
    } else if (addr.county) {
      return addr.county;
    } else if (data.display_name) {
      // Extract first meaningful part
      const parts = data.display_name.split(',');
      return parts[0].trim();
    }

    return 'Unnamed Track';
  } catch (error) {
    console.error('Geocoding error:', error);
    return 'Unnamed Track';
  }
}

const server = Bun.serve({
  port: process.env.PORT || 3000,

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
    open(ws) {
      console.log('WebSocket client connected');
    },

    message(ws, message) {
      console.log('WebSocket message:', message);
    },

    close(ws) {
      console.log('WebSocket client disconnected');
    }
  }
});

async function handleAPI(req: Request): Promise<Response> {
  const url = new URL(req.url);
  const path = url.pathname;

  // CORS headers
  const headers = {
    'Content-Type': 'application/json',
    'Access-Control-Allow-Origin': '*',
    'Access-Control-Allow-Methods': 'GET, POST, PUT, DELETE, OPTIONS',
    'Access-Control-Allow-Headers': 'Content-Type, Authorization, X-Client-Id, X-God-Mode-Key',
  };

  if (req.method === 'OPTIONS') {
    return new Response(null, { status: 204, headers });
  }

  try {
    // GET /api/tracks - Get all active tracks (or filtered by client IDs)
    if (path === '/api/tracks' && req.method === 'GET') {
      // Get client IDs from URL query params (for sharing) OR header (for god mode)
      const clientIdsParam = url.searchParams.get('clients')?.split(',').filter(Boolean) || [];
      const includeHistorical = url.searchParams.get('historical') === 'true';

      // Get god mode key from secure header
      const godModeKey = req.headers.get('X-God-Mode-Key');

      let tracks;
      if (GOD_MODE_KEY && godModeKey === GOD_MODE_KEY) {
        // God mode: show all tracks regardless of client filter (only if GOD_MODE_KEY is set)
        tracks = includeHistorical ? trackStore.getAllTracks() : trackStore.getAllActiveTracks();
      } else if (clientIdsParam.length > 0) {
        // Show only specified client IDs from URL
        tracks = trackStore.getTracksByClientIds(clientIdsParam, includeHistorical);
      } else {
        // Default: no tracks shown unless clients are specified
        tracks = [];
      }

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

      if (!body.userId) {
        return new Response(JSON.stringify({ error: 'Missing required fields' }), {
          status: 400,
          headers
        });
      }

      // Verify client ID in header matches userId in body
      const clientIdHeader = req.headers.get('X-Client-Id');
      if (!clientIdHeader || clientIdHeader !== body.userId) {
        return new Response(JSON.stringify({ error: 'Unauthorized' }), {
          status: 401,
          headers
        });
      }

      // Auto-generate name if not provided or if it's generic
      let trackName = body.name || '';
      const isGenericName = !trackName || trackName.match(/^(Track|Recording|Unnamed)/i);

      if (isGenericName && body.points && body.points.length > 0) {
        // Use first point to geocode
        const firstPoint = body.points[0];
        trackName = await reverseGeocode(firstPoint.lat, firstPoint.lon);
      } else if (!trackName) {
        trackName = 'Unnamed Track';
      }

      const track: Track = {
        id: body.id || crypto.randomUUID(),
        userId: body.userId,
        name: trackName,
        points: body.points || [],
        startTime: Date.now(),
        isActive: true
      };

      trackStore.saveTrack(track);

      // Broadcast to all WebSocket clients
      broadcastToAll({
        type: 'track_started',
        trackId: track.id,
        userId: track.userId,
        name: track.name,
        color: track.color
      });

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

      // Broadcast update to all WebSocket clients
      broadcastToAll({
        type: 'track_update',
        trackId: trackId,
        userId: track.userId,
        name: track.name,
        point: point,
        color: track.color
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

      // Broadcast to all WebSocket clients
      broadcastToAll({
        type: 'track_stopped',
        trackId: trackId
      });

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

function broadcastToAll(message: any) {
  server.publish('tracking', JSON.stringify(message));
}

console.log(`🚀 Where Server running at http://localhost:${server.port}`);
console.log(`📡 WebSocket available at ws://localhost:${server.port}/ws`);
console.log(`🌐 Web interface at http://localhost:${server.port}`);

