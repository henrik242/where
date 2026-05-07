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
