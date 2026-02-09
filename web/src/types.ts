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

export class Track {
  static fromGPX(gpxContent: string): Track | null {
    try {
      const trackName = gpxContent
        .match(/<name>(.*?)<\/name>/)?.[1]?.trim() || 'Imported Track';

      const trackPoints: TrackPoint[] = [];
      const trkptPattern = /<trkpt lat="([^"]+)" lon="([^"]+)">(.*?)<\/trkpt>/gs;

      let match;
      while ((match = trkptPattern.exec(gpxContent)) !== null) {
        const lat = parseFloat(match[1]);
        const lon = parseFloat(match[2]);
        const content = match[3];

        if (isNaN(lat) || isNaN(lon)) continue;

        const eleMatch = content.match(/<ele>(.*?)<\/ele>/);
        const altitude = eleMatch ? parseFloat(eleMatch[1]) : undefined;

        const timeMatch = content.match(/<time>(.*?)<\/time>/);
        const timestamp = timeMatch
          ? new Date(timeMatch[1]).getTime()
          : Date.now();

        trackPoints.push({
          lat,
          lon,
          timestamp,
          altitude: isNaN(altitude!) ? undefined : altitude,
          accuracy: undefined
        });
      }

      if (trackPoints.length === 0) return null;

      const startTime = Math.min(...trackPoints.map(p => p.timestamp));
      const endTime = Math.max(...trackPoints.map(p => p.timestamp));

      return {
        id: crypto.randomUUID(),
        userId: '',
        name: trackName,
        points: trackPoints,
        startTime,
        endTime,
        isActive: false
      };
    } catch (e) {
      return null;
    }
  }
}

export interface TrackUpdate {
  userId: string;
  trackId: string;
  point: TrackPoint;
}

