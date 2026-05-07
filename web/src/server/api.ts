import type { Track } from '../shared/types';
import { validatePoint } from '../shared/validation';
import { detectPlatform } from './platform';
import type { TrackStore } from './store';
import { enrichTrack, getViewerCount } from './tracking';

const CORS_HEADERS = {
  'Content-Type': 'application/json',
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Methods': 'GET, POST, PUT, DELETE, OPTIONS',
  'Access-Control-Allow-Headers':
    'Content-Type, Authorization, X-Client-Id, X-Admin-Key, X-Signature',
};

export interface ApiDeps {
  store: TrackStore;
  verifySignature: (body: string, signature: string | null) => Promise<boolean>;
  broadcast: (message: unknown, userId?: string) => void;
  /** Returns the configured admin key, or undefined if admin mode is disabled. */
  getAdminKey: () => string | undefined;
}

function jsonResponse(data: unknown, status = 200): Response {
  return new Response(JSON.stringify(data), { status, headers: CORS_HEADERS });
}

function isAuthorized(
  req: Request,
  track: Track,
  configuredAdminKey: string | undefined
): boolean {
  const clientId = req.headers.get('X-Client-Id');
  const adminKey = req.headers.get('X-Admin-Key');
  if (clientId === track.userId) return true;
  if (configuredAdminKey && adminKey === configuredAdminKey) return true;
  return false;
}

async function viewerCount(
  { verifySignature }: ApiDeps,
  clientId: string,
  req: Request
): Promise<Response> {
  const headerClientId = req.headers.get('X-Client-Id');
  if (!headerClientId || headerClientId !== clientId) {
    return jsonResponse({ error: 'Unauthorized' }, 401);
  }
  const signature = req.headers.get('X-Signature');
  if (!(await verifySignature(clientId, signature))) {
    return jsonResponse({ error: 'Invalid or missing signature' }, 401);
  }
  return jsonResponse({ viewers: getViewerCount(clientId) });
}

function getTracks(
  { store, getAdminKey }: ApiDeps,
  url: URL,
  req: Request
): Response {
  const clientIdsParam =
    url.searchParams.get('clients')?.split(',').filter(Boolean) || [];
  const includeHistorical = url.searchParams.get('historical') === 'true';
  const adminKey = req.headers.get('X-Admin-Key');
  const configured = getAdminKey();

  if (adminKey && (!configured || adminKey !== configured)) {
    return jsonResponse({ error: 'Invalid admin key' }, 401);
  }

  const isAdmin = !!(configured && adminKey === configured);

  let tracks: Track[];
  if (isAdmin) {
    tracks = includeHistorical ? store.getAllTracks() : store.getAllActiveTracks();
  } else if (clientIdsParam.length > 0) {
    tracks = store.getTracksByClientIds(clientIdsParam, includeHistorical);
  } else {
    tracks = [];
  }

  return jsonResponse({
    admin: isAdmin,
    tracks: tracks.map(enrichTrack),
  });
}

function getTrack({ store }: ApiDeps, trackId: string): Response {
  const track = store.getTrack(trackId);
  if (!track) return jsonResponse({ error: 'Track not found' }, 404);
  return jsonResponse(track);
}

function createTrack(
  { store, broadcast }: ApiDeps,
  req: Request,
  body: Partial<Track>
): Response {
  if (!body.userId) {
    return jsonResponse({ error: 'Missing required fields' }, 400);
  }

  const clientIdHeader = req.headers.get('X-Client-Id');
  if (!clientIdHeader || clientIdHeader !== body.userId) {
    return jsonResponse({ error: 'Unauthorized' }, 401);
  }

  const initialPoints = body.points || [];
  if (initialPoints.some(p => !validatePoint(p))) {
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

  store.saveTrack(track);

  const platform = detectPlatform(req.headers.get('User-Agent') || '');
  if (platform) store.incrementSessionCount(platform);

  const savedTrack = store.getTrack(track.id);

  broadcast({
    type: 'track_started',
    trackId: track.id,
    userId: track.userId,
    name: track.name,
    color: savedTrack?.color,
  }, track.userId);

  return jsonResponse(savedTrack ?? track, 201);
}

function addPoint(
  { store, broadcast, getAdminKey }: ApiDeps,
  trackId: string,
  req: Request,
  point: unknown
): Response {
  if (!validatePoint(point)) {
    return jsonResponse({ error: 'Invalid point data' }, 400);
  }

  const track = store.getTrack(trackId);
  if (!track) return jsonResponse({ error: 'Track not found' }, 404);

  if (!isAuthorized(req, track, getAdminKey())) {
    return jsonResponse({ error: 'Unauthorized' }, 401);
  }

  const wasInactive = !track.isActive;
  const updates: Partial<Track> = {
    isActive: true,
    lastUpdateTime: Date.now(),
  };
  if (wasInactive) updates.endTime = undefined;

  const updatedTrack = store.addPoint(trackId, point, updates);

  // Broadcast reactivation before the point update so clients see the track as active
  if (wasInactive) {
    broadcast({
      type: 'track_started',
      trackId,
      userId: track.userId,
      name: track.name,
      color: updatedTrack?.color ?? track.color,
    }, track.userId);
  }

  broadcast({
    type: 'track_update',
    trackId,
    userId: track.userId,
    name: track.name,
    point,
    color: updatedTrack?.color ?? track.color,
  }, track.userId);

  return jsonResponse(updatedTrack);
}

function stopTrack(
  { store, broadcast, getAdminKey }: ApiDeps,
  trackId: string,
  req: Request
): Response {
  const track = store.getTrack(trackId);
  if (!track) return jsonResponse({ error: 'Track not found' }, 404);

  if (!isAuthorized(req, track, getAdminKey())) {
    return jsonResponse({ error: 'Unauthorized' }, 401);
  }

  const updatedTrack = store.updateTrack(trackId, {
    isActive: false,
    endTime: Date.now(),
  });

  broadcast({ type: 'track_stopped', trackId, userId: track.userId }, track.userId);

  return jsonResponse(updatedTrack);
}

function deleteTrack(
  { store, broadcast, getAdminKey }: ApiDeps,
  trackId: string,
  req: Request
): Response {
  const track = store.getTrack(trackId);
  if (!track) return jsonResponse({ error: 'Track not found' }, 404);

  if (!isAuthorized(req, track, getAdminKey())) {
    return jsonResponse({ error: 'Unauthorized' }, 401);
  }

  store.deleteTrack(trackId);

  broadcast({ type: 'track_deleted', trackId, userId: track.userId }, track.userId);

  return new Response(null, { status: 204, headers: CORS_HEADERS });
}

export function createApiHandler(deps: ApiDeps) {
  return async function handleAPI(req: Request): Promise<Response> {
    const url = new URL(req.url);
    const path = url.pathname;

    if (req.method === 'OPTIONS') {
      return new Response(null, { status: 204, headers: CORS_HEADERS });
    }

    let parsedBody: unknown;
    try {
      if (req.method === 'POST' || req.method === 'PUT' || req.method === 'DELETE') {
        const bodyText = req.method === 'DELETE' ? '' : await req.text();
        const signature = req.headers.get('X-Signature');

        if (!(await deps.verifySignature(bodyText, signature))) {
          console.warn('❌ Invalid or missing HMAC signature');
          return jsonResponse({ error: 'Invalid or missing signature' }, 401);
        }

        if (bodyText) parsedBody = JSON.parse(bodyText);
      }

      let m: RegExpMatchArray | null;

      // Must precede the generic /api/tracks/:trackId route.
      if ((m = path.match(/^\/api\/tracks\/viewers\/([^\/]+)$/)) && req.method === 'GET') {
        return viewerCount(deps, m[1]!, req);
      }
      if (path === '/api/tracks' && req.method === 'GET') {
        return getTracks(deps, url, req);
      }
      if ((m = path.match(/^\/api\/tracks\/([^\/]+)$/)) && req.method === 'GET') {
        return getTrack(deps, m[1]!);
      }
      if (path === '/api/tracks' && req.method === 'POST') {
        return createTrack(deps, req, (parsedBody ?? {}) as Partial<Track>);
      }
      if ((m = path.match(/^\/api\/tracks\/([^\/]+)\/points$/)) && req.method === 'POST') {
        return addPoint(deps, m[1]!, req, parsedBody);
      }
      if ((m = path.match(/^\/api\/tracks\/([^\/]+)\/stop$/)) && req.method === 'PUT') {
        return stopTrack(deps, m[1]!, req);
      }
      if ((m = path.match(/^\/api\/tracks\/([^\/]+)$/)) && req.method === 'DELETE') {
        return deleteTrack(deps, m[1]!, req);
      }

      return jsonResponse({ error: 'Not found' }, 404);
    } catch (error) {
      console.error('API Error:', error);
      return jsonResponse({ error: 'Internal server error' }, 500);
    }
  };
}
