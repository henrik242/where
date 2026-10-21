export const CONFIG = {
  ADMIN_KEY: process.env.ADMIN_KEY,
  TRACKING_HINT: process.env.TRACKING_HINT,
  PORT: process.env.PORT || 3000,
  // Uploaded by the Update Places Index workflow; /api/places answers 503 until it exists.
  // Kept out of data/ so the upload key can't touch the tracking database.
  PLACES_DB: process.env.PLACES_DB || './places/places.sqlite',
  // Comma-separated SHA-256 signing cert fingerprints for assetlinks.json.
  ANDROID_CERT_SHA256: process.env.ANDROID_CERT_SHA256,
  STALE_TRACK_TIMEOUT: 60 * 60 * 1000,
  STALE_CHECK_INTERVAL: 60 * 1000,
  INITIAL_CHECK_DELAY: 10 * 1000,
  TRACKS_RETENTION: 24 * 60 * 60 * 1000,
} as const;

if (!CONFIG.TRACKING_HINT) {
  console.warn('WARNING: TRACKING_HINT not set! All tracking requests will be rejected.');
}
