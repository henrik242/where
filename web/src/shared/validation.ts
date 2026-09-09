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

export const SHARED_POINT_NAME_MAX = 100;
export const SHARED_POINT_DESC_MAX = 500;

/** A hex color like #FF5722 (3 or 6 digits). */
const HEX_COLOR_RE = /^#([0-9a-fA-F]{3}|[0-9a-fA-F]{6})$/;

/** Validate the coordinate part shared by create and update; name/description/color checked here too. */
export function validateSharedPointFields(p: any): boolean {
  if (!p || typeof p !== 'object') return false;
  if (typeof p.lat !== 'number' || p.lat < -90 || p.lat > 90) return false;
  if (typeof p.lon !== 'number' || p.lon < -180 || p.lon > 180) return false;
  if (typeof p.name !== 'string' || p.name.length > SHARED_POINT_NAME_MAX) return false;
  if (p.description !== undefined &&
    (typeof p.description !== 'string' || p.description.length > SHARED_POINT_DESC_MAX)) return false;
  if (p.color !== undefined && (typeof p.color !== 'string' || !HEX_COLOR_RE.test(p.color))) return false;
  return true;
}
