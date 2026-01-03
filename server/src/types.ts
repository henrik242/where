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
}

export interface TrackUpdate {
  userId: string;
  trackId: string;
  point: TrackPoint;
}

