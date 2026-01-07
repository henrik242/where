import { trackStore } from './store';
import type { Track } from './types';
import { verifyHmacSignature } from './utils';
import { enrichTrack, broadcastToAll } from './tracking';
import { CONFIG } from './config';

const CORS_HEADERS = {
  'Content-Type': 'application/json',
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Methods': 'GET, POST, PUT, DELETE, OPTIONS',
  'Access-Control-Allow-Headers':
    'Content-Type, Authorization, X-Client-Id, X-Admin-Key, X-Signature',
};

/**
 * Main API request handler
 */
export async function handleAPI(req: Request): Promise<Response> {
  const url = new URL(req.url);
  const path = url.pathname;

  // Handle CORS preflight
  if (req.method === 'OPTIONS') {
    return new Response(null, { status: 204, headers: CORS_HEADERS });
  }

  try {
    // Verify HMAC for mutating requests
    if (req.method === 'POST' || req.method === 'PUT') {
      const bodyText = await req.text();
      const signature = req.headers.get('X-Signature');

      if (!(await verifyHmacSignature(bodyText, signature))) {
        console.warn('❌ Invalid or missing HMAC signature');
        return jsonResponse(
          { error: 'Invalid or missing signature' },
          401
        );
      }

      (req as any).parsedBody = JSON.parse(bodyText);
    }

    // Route to handlers
    if (path === '/api/tracks' && req.method === 'GET') {
      return handleGetTracks(url, req);
    }

    if (path.match(/^\/api\/tracks\/[^\/]+$/) && req.method === 'GET') {
      return handleGetTrack(path);
    }

    if (path === '/api/tracks' && req.method === 'POST') {
      return handleCreateTrack(req);
    }

    if (path.match(/^\/api\/tracks\/[^\/]+\/points$/) && req.method === 'POST') {
      return handleAddPoint(path, req);
    }

    if (path.match(/^\/api\/tracks\/[^\/]+\/stop$/) && req.method === 'PUT') {
      return handleStopTrack(path);
    }

    if (path.match(/^\/api\/tracks\/[^\/]+$/) && req.method === 'DELETE') {
      return handleDeleteTrack(path);
    }

    return jsonResponse({ error: 'Not found' }, 404);
  } catch (error) {
    console.error('API Error:', error);
    return jsonResponse({ error: 'Internal server error' }, 500);
  }
}

/**
 * Helper to create JSON responses
 */
function jsonResponse(data: any, status = 200): Response {
  return new Response(JSON.stringify(data), {
    status,
    headers: CORS_HEADERS,
  });
}

/**
 * GET /api/tracks - Get all active tracks (or filtered)
 */
async function handleGetTracks(url: URL, req: Request): Promise<Response> {
  const clientIdsParam =
    url.searchParams.get('clients')?.split(',').filter(Boolean) || [];
  const includeHistorical = url.searchParams.get('historical') === 'true';
  const adminKey = req.headers.get('X-Admin-Key');

  // Validate admin key if provided
  if (adminKey && (!CONFIG.ADMIN_KEY || adminKey !== CONFIG.ADMIN_KEY)) {
    return jsonResponse({ error: 'Invalid admin key' }, 401);
  }

  const isAdmin = CONFIG.ADMIN_KEY && adminKey === CONFIG.ADMIN_KEY;

  // Get tracks based on access level
  let tracks: Track[];
  if (isAdmin) {
    tracks = includeHistorical
      ? trackStore.getAllTracks()
      : trackStore.getAllActiveTracks();
  } else if (clientIdsParam.length > 0) {
    tracks = trackStore.getTracksByClientIds(clientIdsParam, includeHistorical);
  } else {
    tracks = [];
  }

  return jsonResponse({
    admin: isAdmin,
    tracks: tracks.map(enrichTrack),
  });
}

/**
 * GET /api/tracks/:trackId - Get specific track
 */
async function handleGetTrack(path: string): Promise<Response> {
  const trackId = path.split('/')[3];
  const track = trackStore.getTrack(trackId);

  if (!track) {
    return jsonResponse({ error: 'Track not found' }, 404);
  }

  return jsonResponse(track);
}

/**
 * POST /api/tracks - Create new track
 */
async function handleCreateTrack(req: Request): Promise<Response> {
  const body = (req as any).parsedBody as Partial<Track>;

  if (!body.userId) {
    return jsonResponse({ error: 'Missing required fields' }, 400);
  }

  // Verify client ID in header matches userId
  const clientIdHeader = req.headers.get('X-Client-Id');
  if (!clientIdHeader || clientIdHeader !== body.userId) {
    return jsonResponse({ error: 'Unauthorized' }, 401);
  }

  let trackName = body.name || '';

  const track: Track = {
    id: body.id || crypto.randomUUID(),
    userId: body.userId,
    name: trackName,
    points: body.points || [],
    startTime: Date.now(),
    isActive: true,
    lastUpdateTime: Date.now(),
  };

  trackStore.saveTrack(track);

  broadcastToAll({
    type: 'track_started',
    trackId: track.id,
    userId: track.userId,
    name: track.name,
    color: track.color,
  });

  return jsonResponse(track, 201);
}

/**
 * POST /api/tracks/:trackId/points - Add point to track
 */
async function handleAddPoint(path: string, req: Request): Promise<Response> {
  const trackId = path.split('/')[3];
  const point = (req as any).parsedBody;

  const track = trackStore.getTrack(trackId);
  if (!track) {
    return jsonResponse({ error: 'Track not found' }, 404);
  }

  const wasInactive = !track.isActive;

  const updatedTrack = trackStore.updateTrack(trackId, {
    points: [...track.points, point],
    isActive: true,
    lastUpdateTime: Date.now(),
  });

  broadcastToAll({
    type: 'track_update',
    trackId,
    userId: track.userId,
    name: track.name,
    point,
    color: track.color,
  });

  // Broadcast reactivation if track was inactive
  if (wasInactive) {
    broadcastToAll({
      type: 'track_started',
      trackId,
      userId: track.userId,
      name: track.name,
      color: track.color,
    });
  }

  return jsonResponse(updatedTrack);
}

/**
 * PUT /api/tracks/:trackId/stop - Stop a track
 */
async function handleStopTrack(path: string): Promise<Response> {
  const trackId = path.split('/')[3];

  const updatedTrack = trackStore.updateTrack(trackId, {
    isActive: false,
    endTime: Date.now(),
  });

  if (!updatedTrack) {
    return jsonResponse({ error: 'Track not found' }, 404);
  }

  broadcastToAll({
    type: 'track_stopped',
    trackId,
  });

  return jsonResponse(updatedTrack);
}

/**
 * DELETE /api/tracks/:trackId - Delete a track
 */
async function handleDeleteTrack(path: string): Promise<Response> {
  const trackId = path.split('/')[3];
  const deleted = trackStore.deleteTrack(trackId);

  if (!deleted) {
    return jsonResponse({ error: 'Track not found' }, 404);
  }

  return new Response(null, { status: 204, headers: CORS_HEADERS });
}

