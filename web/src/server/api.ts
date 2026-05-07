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

export function createApiHandler(deps: ApiDeps) {
  const { store, verifySignature, broadcast, getAdminKey } = deps;

  function jsonResponse(data: any, status = 200): Response {
    return new Response(JSON.stringify(data), {
      status,
      headers: CORS_HEADERS,
    });
  }

  function isAuthorized(req: Request, track: Track): boolean {
    const clientId = req.headers.get('X-Client-Id');
    const adminKey = req.headers.get('X-Admin-Key');
    const configured = getAdminKey();
    if (clientId === track.userId) return true;
    if (configured && adminKey === configured) return true;
    return false;
  }

  async function handleGetViewerCount(path: string, req: Request): Promise<Response> {
    const clientId = path.split('/')[4]!;
    const headerClientId = req.headers.get('X-Client-Id');
    const signature = req.headers.get('X-Signature');

    if (!headerClientId || headerClientId !== clientId) {
      return jsonResponse({ error: 'Unauthorized' }, 401);
    }

    if (!(await verifySignature(clientId, signature))) {
      return jsonResponse({ error: 'Invalid or missing signature' }, 401);
    }

    return jsonResponse({ viewers: getViewerCount(clientId) });
  }

  async function handleGetTracks(url: URL, req: Request): Promise<Response> {
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

  function handleGetTrack(path: string): Response {
    const trackId = path.split('/')[3]!;
    const track = store.getTrack(trackId);
    if (!track) return jsonResponse({ error: 'Track not found' }, 404);
    return jsonResponse(track);
  }

  function handleCreateTrack(req: Request): Response {
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

  function handleAddPoint(path: string, req: Request): Response {
    const trackId = path.split('/')[3]!;
    const point = (req as any).parsedBody;

    if (!validatePoint(point)) {
      return jsonResponse({ error: 'Invalid point data' }, 400);
    }

    const track = store.getTrack(trackId);
    if (!track) return jsonResponse({ error: 'Track not found' }, 404);

    if (!isAuthorized(req, track)) {
      return jsonResponse({ error: 'Unauthorized' }, 401);
    }

    const wasInactive = !track.isActive;

    const updates: Partial<Track> = {
      isActive: true,
      lastUpdateTime: Date.now(),
    };
    if (wasInactive) {
      updates.endTime = undefined;
    }

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

  function handleStopTrack(path: string, req: Request): Response {
    const trackId = path.split('/')[3]!;
    const track = store.getTrack(trackId);
    if (!track) return jsonResponse({ error: 'Track not found' }, 404);

    if (!isAuthorized(req, track)) {
      return jsonResponse({ error: 'Unauthorized' }, 401);
    }

    const updatedTrack = store.updateTrack(trackId, {
      isActive: false,
      endTime: Date.now(),
    });

    broadcast({ type: 'track_stopped', trackId, userId: track.userId }, track.userId);

    return jsonResponse(updatedTrack);
  }

  function handleDeleteTrack(path: string, req: Request): Response {
    const trackId = path.split('/')[3]!;
    const track = store.getTrack(trackId);
    if (!track) return jsonResponse({ error: 'Track not found' }, 404);

    if (!isAuthorized(req, track)) {
      return jsonResponse({ error: 'Unauthorized' }, 401);
    }

    store.deleteTrack(trackId);

    broadcast({ type: 'track_deleted', trackId, userId: track.userId }, track.userId);

    return new Response(null, { status: 204, headers: CORS_HEADERS });
  }

  return async function handleAPI(req: Request): Promise<Response> {
    const url = new URL(req.url);
    const path = url.pathname;

    if (req.method === 'OPTIONS') {
      return new Response(null, { status: 204, headers: CORS_HEADERS });
    }

    try {
      if (req.method === 'POST' || req.method === 'PUT' || req.method === 'DELETE') {
        const bodyText = req.method === 'DELETE' ? '' : await req.text();
        const signature = req.headers.get('X-Signature');

        if (!(await verifySignature(bodyText, signature))) {
          console.warn('❌ Invalid or missing HMAC signature');
          return jsonResponse({ error: 'Invalid or missing signature' }, 401);
        }

        if (bodyText) {
          (req as any).parsedBody = JSON.parse(bodyText);
        }
      }

      // Must precede the generic /api/tracks/:trackId route.
      if (path.match(/^\/api\/tracks\/viewers\/[^\/]+$/) && req.method === 'GET') {
        return handleGetViewerCount(path, req);
      }
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
        return handleStopTrack(path, req);
      }
      if (path.match(/^\/api\/tracks\/[^\/]+$/) && req.method === 'DELETE') {
        return handleDeleteTrack(path, req);
      }

      return jsonResponse({ error: 'Not found' }, 404);
    } catch (error) {
      console.error('API Error:', error);
      return jsonResponse({ error: 'Internal server error' }, 500);
    }
  };
}
