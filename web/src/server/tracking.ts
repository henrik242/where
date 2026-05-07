import { trackStore } from './store';
import type { EnrichedTrack, Track } from '../shared/types';
import { calculateTrackDistance } from '../shared/geo';
import { CONFIG } from './config';

/** Add server-derived fields (distance, pointCount) to a track for the wire format. */
export function enrichTrack(track: Track): EnrichedTrack {
  return {
    ...track,
    distance: calculateTrackDistance(track.points),
    pointCount: track.points.length,
  };
}

const subscribedClients = new Set<any>();

export function addSubscribedClient(ws: any) {
  subscribedClients.add(ws);
}

export function removeSubscribedClient(ws: any) {
  subscribedClients.delete(ws);
}

export function broadcastToAll(message: any, userId?: string) {
  const payload = JSON.stringify(message);
  for (const ws of subscribedClients) {
    const data = ws.data as { clients?: string[]; admin?: boolean };
    if (!data) continue;
    if (data.admin) {
      ws.send(payload);
    } else if (userId && data.clients?.includes(userId)) {
      ws.send(payload);
    }
  }
}

/** Number of WebSocket subscribers currently watching a given user's tracks. */
export function getViewerCount(userId: string): number {
  let count = 0;
  for (const ws of subscribedClients) {
    const data = ws.data as { clients?: string[]; admin?: boolean };
    if (!data) continue;
    if (data.clients?.includes(userId)) count++;
  }
  return count;
}

export function checkStaleTracks() {
  const cutoffTime = Date.now() - CONFIG.STALE_TRACK_TIMEOUT;
  const activeTracks = trackStore.getAllActiveTracks();

  if (activeTracks.length === 0) {
    return;
  }

  let stoppedCount = 0;

  activeTracks.forEach((track) => {
    let lastUpdate = track.lastUpdateTime;

    if (!lastUpdate && track.points.length > 0) {
      lastUpdate = track.points[track.points.length - 1].timestamp;
    }

    if (!lastUpdate) {
      lastUpdate = track.startTime;
    }

    if (lastUpdate < cutoffTime) {
      const updatedTrack = trackStore.updateTrack(track.id, {
        isActive: false,
        endTime: lastUpdate,
      });

      if (updatedTrack) {
        stoppedCount++;
        const minutesSinceUpdate = Math.floor(
          (Date.now() - lastUpdate) / 60000
        );
        console.log(
          `⏱️  Track ${track.id.substring(0, 8)}... (${
            track.userId
          }) marked as inactive after ${minutesSinceUpdate} minutes of no updates`
        );

        broadcastToAll({
          type: 'track_stopped',
          trackId: track.id,
          userId: track.userId,
        }, track.userId);
      }
    }
  });

  if (stoppedCount > 0) {
    console.log(`✅ Stopped ${stoppedCount} stale track(s)`);
  }
}

export function cleanupOldTracks() {
  const deleted = trackStore.cleanupOldTracks();
  for (const { id, userId } of deleted) {
    broadcastToAll({
      type: 'track_deleted',
      trackId: id,
      userId,
    }, userId);
  }
  if (deleted.length > 0) {
    console.log(`🗑️  Cleaned up ${deleted.length} old track(s)`);
  }
}

export function startStaleTrackChecker() {
  setInterval(checkStaleTracks, CONFIG.STALE_CHECK_INTERVAL);
  setInterval(cleanupOldTracks, 60 * 60 * 1000);

  setTimeout(() => {
    console.log('🔍 Running initial stale track check...');
    checkStaleTracks();
    cleanupOldTracks();
  }, CONFIG.INITIAL_CHECK_DELAY);
}
