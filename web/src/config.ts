/**
 * Configuration and constants
 */

export const CONFIG = {
  ADMIN_KEY: process.env.ADMIN_KEY,
  TRACKING_HMAC_SECRET: process.env.TRACKING_HMAC_SECRET,
  PORT: process.env.PORT || 3000,
  STALE_TRACK_TIMEOUT: 10 * 60 * 1000, // 10 minutes
  STALE_CHECK_INTERVAL: 60 * 1000, // 1 minute
  INITIAL_CHECK_DELAY: 10 * 1000, // 10 seconds
  TRACKS_RETENTION: 24 * 60 * 60 * 1000 // 24 hours
} as const;

// Validate required configuration
if (!CONFIG.TRACKING_HMAC_SECRET) {
  console.warn('⚠️  WARNING: TRACKING_HMAC_SECRET not set! All tracking requests will be rejected.');
}

