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
  if (!CONFIG.TRACKING_HMAC_SECRET || !signature) {
    return false;
  }

  try {
    const encoder = new TextEncoder();
    const key = await crypto.subtle.importKey(
      'raw',
      encoder.encode(CONFIG.TRACKING_HMAC_SECRET),
      { name: 'HMAC', hash: 'SHA-256' },
      false,
      ['sign']
    );

    const signatureBytes = await crypto.subtle.sign(
      'HMAC',
      key,
      encoder.encode(body)
    );

    const expectedSignature = btoa(
      String.fromCharCode(...new Uint8Array(signatureBytes))
    );

    return signature === expectedSignature;
  } catch (error) {
    console.error('HMAC verification error:', error);
    return false;
  }
}

/**
 * Reverse geocode coordinates to a place name
 */
export async function reverseGeocode(
  lat: number,
  lon: number
): Promise<string> {
  try {
    const response = await fetch(
      `https://nominatim.openstreetmap.org/reverse?lat=${lat}&lon=${lon}&format=json&addressdetails=1`,
      {
        headers: {
          'User-Agent': 'Where-App/1.0',
        },
      }
    );

    if (!response.ok) return 'Unnamed Track';

    const data = await response.json();
    const addr = data.address;

    // Try to build a nice name from available data
    if (addr.road) {
      return addr.road;
    } else if (addr.village || addr.town || addr.city) {
      return addr.village || addr.town || addr.city;
    } else if (addr.county) {
      return addr.county;
    } else if (data.display_name) {
      const parts = data.display_name.split(',');
      return parts[0].trim();
    }

    return 'Unnamed Track';
  } catch (error) {
    console.error('Geocoding error:', error);
    return 'Unnamed Track';
  }
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

