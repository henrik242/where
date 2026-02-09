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
 * Broadcast message to all WebSocket clients
 */
let serverInstance: any = null;

export function setServerInstance(server: any) {
  serverInstance = server;
}

export function broadcastToAll(message: any) {
  if (serverInstance) {
    serverInstance.publish('tracking', JSON.stringify(message));
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
        });
      }
    }
  });

  if (stoppedCount > 0) {
    console.log(`✅ Stopped ${stoppedCount} stale track(s)`);
  }
}

/**
 * Start the stale track checker
 */
export function startStaleTrackChecker() {
  // Run check every minute
  setInterval(checkStaleTracks, CONFIG.STALE_CHECK_INTERVAL);

  // Run initial check after 10 seconds
  setTimeout(() => {
    console.log('🔍 Running initial stale track check...');
    checkStaleTracks();
  }, CONFIG.INITIAL_CHECK_DELAY);
}

