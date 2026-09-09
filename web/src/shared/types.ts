export interface TrackPoint {
  lat: number;
  lon: number;
  timestamp: number;
  altitude?: number;
  accuracy?: number;
}

export interface Track {
  id: string;
  userId: string;
  name: string;
  points: TrackPoint[];
  startTime: number;
  endTime?: number;
  isActive: boolean;
  color?: string;
  lastUpdateTime?: number;
}

export interface TrackUpdate {
  userId: string;
  trackId: string;
  point: TrackPoint;
}

/**
 * A named marker a live-sharing client drops on the map for its followers. Keyed to the sharer's
 * client id (userId) and broadcast over the same channel as tracks; expires with the session.
 */
export interface SharedPoint {
  id: string;
  userId: string;
  name: string;
  description: string;
  lat: number;
  lon: number;
  color: string;
  timestamp: number;
}

/** A {@link Track} with server-derived metrics; the wire format returned by APIs. */
export interface EnrichedTrack extends Track {
  distance: number;
  pointCount: number;
}

export interface SessionStats {
  day: Record<string, number>;
  week: Record<string, number>;
  month: Record<string, number>;
}

export interface TracksResponse {
  admin: boolean;
  tracks: EnrichedTrack[];
}
