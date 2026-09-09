import type { ServerWebSocket, WebSocketHandler } from 'bun';
import { CONFIG } from './config';
import { trackStore } from './store';
import { addSubscribedClient, enrichTrack, removeSubscribedClient } from './tracking';
import type { SharedPoint, Track } from '../shared/types';

export interface WsData {
  clients: string[];
  admin: boolean;
}

export const websocketHandlers: WebSocketHandler<WsData> = {
  open() {
    console.log('WebSocket client connected');
  },

  message(ws, message) {
    try {
      const msg = JSON.parse(String(message));
      if (msg.type === 'subscribe') {
        handleSubscribe(ws, msg);
      }
    } catch (e) {
      console.error('Failed to parse WebSocket message:', e);
    }
  },

  close(ws) {
    console.log('WebSocket client disconnected');
    removeSubscribedClient(ws);
  },
};

function handleSubscribe(
  ws: ServerWebSocket<WsData>,
  msg: { clients?: unknown; adminKey?: unknown; historical?: unknown }
) {
  const clients: string[] = Array.isArray(msg.clients) ? msg.clients : [];
  const isAdmin = !!(CONFIG.ADMIN_KEY && msg.adminKey === CONFIG.ADMIN_KEY);
  const includeHistorical = msg.historical === true;
  ws.data = { clients, admin: isAdmin };
  addSubscribedClient(ws);

  let tracks: Track[];
  let points: SharedPoint[];
  if (isAdmin) {
    tracks = includeHistorical ? trackStore.getAllTracks() : trackStore.getAllActiveTracks();
    points = trackStore.getAllSharedPoints();
  } else if (clients.length === 0) {
    tracks = [];
    points = [];
  } else {
    tracks = trackStore.getTracksByClientIds(clients, includeHistorical);
    points = trackStore.getSharedPointsByClientIds(clients);
  }

  const payload: any = {
    type: 'initial_state',
    tracks: tracks.map(enrichTrack),
    points,
    admin: isAdmin,
  };
  if (isAdmin) {
    payload.sessionStats = trackStore.getSessionStats();
  }
  ws.send(JSON.stringify(payload));
}
