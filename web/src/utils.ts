import { timingSafeEqual } from 'crypto';
import type { TrackPoint } from './types';
import { CONFIG } from './config';

/**
 * Calculate distance between two points using Haversine formula
 * @returns Distance in meters
 */
export function calculateDistance(
  lat1: number,
  lon1: number,
  lat2: number,
  lon2: number
): number {
  const R = 6371000; // Earth radius in meters
  const dLat = ((lat2 - lat1) * Math.PI) / 180;
  const dLon = ((lon2 - lon1) * Math.PI) / 180;
  const a =
    Math.sin(dLat / 2) * Math.sin(dLat / 2) +
    Math.cos((lat1 * Math.PI) / 180) *
      Math.cos((lat2 * Math.PI) / 180) *
      Math.sin(dLon / 2) *
      Math.sin(dLon / 2);
  const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
  return R * c;
}

/**
 * Calculate total distance for a track
 * @returns Distance in meters
 */
export function calculateTrackDistance(points: TrackPoint[]): number {
  if (points.length < 2) return 0;

  let distance = 0;
  for (let i = 1; i < points.length; i++) {
    distance += calculateDistance(
      points[i - 1].lat,
      points[i - 1].lon,
      points[i].lat,
      points[i].lon
    );
  }
  return distance;
}

/**
 * Verify HMAC signature for request body
 */
export async function verifyHmacSignature(
  body: string,
  signature: string | null
): Promise<boolean> {
  if (!CONFIG.TRACKING_HINT || !signature) {
    return false;
  }

  try {
    const encoder = new TextEncoder();
    const key = await crypto.subtle.importKey(
      'raw',
      encoder.encode(CONFIG.TRACKING_HINT),
      { name: 'HMAC', hash: 'SHA-256' },
      false,
      ['sign']
    );

    const signatureBytes = await crypto.subtle.sign(
      'HMAC',
      key,
      encoder.encode(body)
    );

    const expected = Buffer.from(new Uint8Array(signatureBytes));
    const actual = Buffer.from(signature, 'base64');
    if (expected.byteLength !== actual.byteLength) return false;
    return timingSafeEqual(expected, actual);
  } catch (error) {
    console.error('HMAC verification error:', error);
    return false;
  }
}

export function detectPlatform(userAgent: string): string | null {
  if (/iPhone|iPad|iOS/i.test(userAgent)) return 'ios';
  if (/Android/i.test(userAgent)) return 'android';
  return null;
}

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

/**
 * Determine the last update time for a track
 */
export function getTrackLastUpdate(track: {
  lastUpdateTime?: number;
  points: TrackPoint[];
  startTime: number;
}): number {
  if (track.lastUpdateTime) {
    return track.lastUpdateTime;
  }

  if (track.points.length > 0) {
    return track.points[track.points.length - 1].timestamp;
  }

  return track.startTime;
}

