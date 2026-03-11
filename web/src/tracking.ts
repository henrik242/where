import { trackStore } from './store';
import type { Track } from './types';
import { calculateTrackDistance } from './utils';
import { CONFIG } from './config';

/**
 * Enrich track with calculated metadata
 */
export function enrichTrack(track: Track) {
  return {
    ...track,
    distance: calculateTrackDistance(track.points),
    pointCount: track.points.length,
  };
}

/**
 * Broadcast message to subscribed WebSocket clients
 */
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

/**
 * Background job to check for stale tracks (no updates in 10 minutes)
 */
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

/**
 * Delete tracks (and cascading points) older than 24 hours
 */
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

/**
 * Start the stale track checker
 */
export function startStaleTrackChecker() {
  // Run check every minute
  setInterval(checkStaleTracks, CONFIG.STALE_CHECK_INTERVAL);

  // Run cleanup every hour
  setInterval(cleanupOldTracks, 60 * 60 * 1000);

  // Run initial check after 10 seconds
  setTimeout(() => {
    console.log('🔍 Running initial stale track check...');
    checkStaleTracks();
    cleanupOldTracks();
  }, CONFIG.INITIAL_CHECK_DELAY);
}

