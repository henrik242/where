import type { ServerWebSocket, WebSocketHandler } from 'bun';
import { CONFIG } from './config';
import { trackStore } from './store';
import { addSubscribedClient, enrichTrack, removeSubscribedClient } from './tracking';

export interface WsData {
  clients: string[];
  admin: boolean;
}

export const websocketHandlers: WebSocketHandler<WsData> = {
  open(ws) {
    console.log('WebSocket client connected');
    ws.data = { clients: [], admin: false };
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
