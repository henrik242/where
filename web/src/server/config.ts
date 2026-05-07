export const CONFIG = {
  ADMIN_KEY: process.env.ADMIN_KEY,
  TRACKING_HINT: process.env.TRACKING_HINT,
  PORT: process.env.PORT || 3000,
  STALE_TRACK_TIMEOUT: 60 * 60 * 1000,
  STALE_CHECK_INTERVAL: 60 * 1000,
  INITIAL_CHECK_DELAY: 10 * 1000,
  TRACKS_RETENTION: 24 * 60 * 60 * 1000,
} as const;

if (!CONFIG.TRACKING_HINT) {
  console.warn('⚠️  WARNING: TRACKING_HINT not set! All tracking requests will be rejected.');
}
