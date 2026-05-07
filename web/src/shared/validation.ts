import type { TrackPoint } from './types';

/** One or more comma-separated 6-character lowercase alphanumeric client IDs. */
export const CLIENT_IDS_RE = /^[a-z0-9]{6}(,[a-z0-9]{6})*$/;

export function validatePoint(point: any): point is TrackPoint {
  if (!point || typeof point !== 'object') return false;
  return (
    typeof point.lat === 'number' && point.lat >= -90 && point.lat <= 90 &&
    typeof point.lon === 'number' && point.lon >= -180 && point.lon <= 180 &&
    typeof point.timestamp === 'number' && point.timestamp > 0 &&
    (point.altitude === undefined || typeof point.altitude === 'number') &&
    (point.accuracy === undefined || typeof point.accuracy === 'number')
  );
}
