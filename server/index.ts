import { trackStore } from './src/store';
import type { Track } from './src/types';

const ADMIN_KEY = process.env.ADMIN_KEY;
const TRACKING_HMAC_SECRET = process.env.TRACKING_HMAC_SECRET;

if (!TRACKING_HMAC_SECRET) {
  console.warn('⚠️  WARNING: TRACKING_HMAC_SECRET not set! All tracking requests will be rejected.');
}

/**
 * Calculate distance between two points using Haversine formula
 */
function calculateDistance(lat1: number, lon1: number, lat2: number, lon2: number): number {
  const R = 6371000; // Earth radius in meters
  const dLat = (lat2 - lat1) * Math.PI / 180;
  const dLon = (lon2 - lon1) * Math.PI / 180;
  const a = Math.sin(dLat/2) * Math.sin(dLat/2) +
           Math.cos(lat1 * Math.PI / 180) * Math.cos(lat2 * Math.PI / 180) *
           Math.sin(dLon/2) * Math.sin(dLon/2);
  const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1-a));
  return R * c;
}

/**
 * Calculate total distance for a track
 */
function calculateTrackDistance(points: any[]): number {
  if (points.length < 2) return 0;
  let distance = 0;
  for (let i = 1; i < points.length; i++) {
    distance += calculateDistance(
      points[i - 1].lat,
      points[i - 1].lon,
      points[i].lat,
      points[i].lon
    );
  }
  return distance;
}

/**
 * Enrich track with calculated metadata
 */
function enrichTrack(track: Track) {
  return {
    ...track,
    distance: calculateTrackDistance(track.points),
    pointCount: track.points.length
  };
}

/**
 * Verify HMAC signature for request body
 */
async function verifyHmacSignature(body: string, signature: string | null): Promise<boolean> {
  if (!TRACKING_HMAC_SECRET) {
    return false; // Reject if secret not configured
  }

  if (!signature) {
    return false; // Reject if no signature provided
  }

  try {
    const encoder = new TextEncoder();
    const key = await crypto.subtle.importKey(
      'raw',
      encoder.encode(TRACKING_HMAC_SECRET),
      { name: 'HMAC', hash: 'SHA-256' },
      false,
      ['sign']
    );

    const signatureBytes = await crypto.subtle.sign(
      'HMAC',
      key,
      encoder.encode(body)
    );

    // Convert to base64 for comparison
    const expectedSignature = btoa(String.fromCharCode(...new Uint8Array(signatureBytes)));

    return signature === expectedSignature;
  } catch (error) {
    console.error('HMAC verification error:', error);
    return false;
  }
}

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
      ws.subscribe('tracking');
    },

    message(ws, message) {
      console.log('WebSocket message:', message);
    },

    close(ws) {
      console.log('WebSocket client disconnected');
      ws.unsubscribe('tracking');
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
    'Access-Control-Allow-Headers': 'Content-Type, Authorization, X-Client-Id, X-Admin-Key, X-Signature',
  };

  if (req.method === 'OPTIONS') {
    return new Response(null, { status: 204, headers });
  }

  try {
    // Verify HMAC signature for mutating requests (POST, PUT)
    if (req.method === 'POST' || req.method === 'PUT') {
      const bodyText = await req.text();
      const signature = req.headers.get('X-Signature');

      const isValid = await verifyHmacSignature(bodyText, signature);

      if (!isValid) {
        console.warn('❌ Invalid or missing HMAC signature');
        return new Response(JSON.stringify({ error: 'Invalid or missing signature' }), {
          status: 401,
          headers
        });
      }

      // Re-parse body as JSON for later use
      const body = JSON.parse(bodyText);
      (req as any).parsedBody = body;
    }
    // GET /api/tracks - Get all active tracks (or filtered by client IDs)
    if (path === '/api/tracks' && req.method === 'GET') {
      // Get client IDs from URL query params (for sharing) OR header (for admin)
      const clientIdsParam = url.searchParams.get('clients')?.split(',').filter(Boolean) || [];
      const includeHistorical = url.searchParams.get('historical') === 'true';

      // Get admin key from secure header
      const adminKey = req.headers.get('X-Admin-Key');

      // If admin key is provided but invalid, return 401
      if (adminKey && (!ADMIN_KEY || adminKey !== ADMIN_KEY)) {
        return new Response(JSON.stringify({ error: 'Invalid admin key' }), {
          status: 401,
          headers
        });
      }

      const isAdmin = ADMIN_KEY && adminKey === ADMIN_KEY;

      let tracks;
      if (isAdmin) {
        // Admin: show all tracks regardless of client filter (only if ADMIN_KEY is set)
        tracks = includeHistorical ? trackStore.getAllTracks() : trackStore.getAllActiveTracks();
      } else if (clientIdsParam.length > 0) {
        // Show only specified client IDs from URL
        tracks = trackStore.getTracksByClientIds(clientIdsParam, includeHistorical);
      } else {
        // Default: no tracks shown unless clients are specified
        tracks = [];
      }

      // Enrich tracks with calculated metadata
      const enrichedTracks = tracks.map(enrichTrack);

      // Return tracks with admin status
      const response = {
        admin: isAdmin,
        tracks: enrichedTracks
      };

      return new Response(JSON.stringify(response), { headers });
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
      const body = (req as any).parsedBody as Partial<Track>;

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
        isActive: true,
        lastUpdateTime: Date.now()
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
      const point = (req as any).parsedBody;

      const track = trackStore.getTrack(trackId);
      if (!track) {
        return new Response(JSON.stringify({ error: 'Track not found' }), {
          status: 404,
          headers
        });
      }

      // If track was inactive, reactivate it
      const wasInactive = !track.isActive;

      const updatedTrack = trackStore.updateTrack(trackId, {
        points: [...track.points, point],
        isActive: true, // Reactivate if receiving updates
        lastUpdateTime: Date.now()
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

      // If track was reactivated, also broadcast that
      if (wasInactive) {
        broadcastToAll({
          type: 'track_started',
          trackId: trackId,
          userId: track.userId,
          name: track.name,
          color: track.color
        });
      }

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

// Background job to check for stale tracks (no updates in 10 minutes)
function checkStaleTracks() {
  const tenMinutesAgo = Date.now() - 10 * 60 * 1000;
  const activeTracks = trackStore.getAllActiveTracks();

  activeTracks.forEach(track => {
    const lastUpdate = track.lastUpdateTime || track.startTime;
    if (lastUpdate < tenMinutesAgo) {
      // Mark track as inactive
      const updatedTrack = trackStore.updateTrack(track.id, {
        isActive: false,
        endTime: lastUpdate
      });

      if (updatedTrack) {
        console.log(`⏱️  Track ${track.id} (${track.userId}) marked as inactive after 10 minutes of no updates`);

        // Broadcast that track stopped
        broadcastToAll({
          type: 'track_stopped',
          trackId: track.id,
          userId: track.userId
        });
      }
    }
  });
}

// Run stale track check every minute
setInterval(checkStaleTracks, 60 * 1000);

console.log(`🚀 Where Server running at http://localhost:${server.port}`);
console.log(`📡 WebSocket available at ws://localhost:${server.port}/ws`);
console.log(`🌐 Web interface at http://localhost:${server.port}`);
console.log(`🔑 Admin: ${ADMIN_KEY ? 'ENABLED' : 'DISABLED'}`);
console.log(`🔒 HMAC verification: ${TRACKING_HMAC_SECRET ? 'ENABLED' : 'DISABLED'}`);
console.log(`⏱️  Auto-stop inactive tracks after 10 minutes`);

