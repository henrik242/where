import type { TrackPoint } from './types';

/**
 * Distance between two lat/lon points using the Haversine formula.
 * @returns metres
 */
export function calculateDistance(
  lat1: number,
  lon1: number,
  lat2: number,
  lon2: number
): number {
  const R = 6371000;
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
 * Sum of segment distances along a track.
 * @returns metres
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
