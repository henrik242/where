import type { Track, TrackPoint } from './types';

/** Parse a GPX document into a {@link Track}. Returns null if no points were found. */
export function parseGPX(gpxContent: string): Track | null {
  try {
    const trackName =
      gpxContent.match(/<name>(.*?)<\/name>/)?.[1]?.trim() || 'Imported Track';

    const trackPoints: TrackPoint[] = [];
    const trkptPattern = /<trkpt lat="([^"]+)" lon="([^"]+)">(.*?)<\/trkpt>/gs;

    let match;
    while ((match = trkptPattern.exec(gpxContent)) !== null) {
      const lat = parseFloat(match[1]!);
      const lon = parseFloat(match[2]!);
      const content = match[3] ?? '';

      if (isNaN(lat) || isNaN(lon)) continue;

      const eleMatch = content.match(/<ele>(.*?)<\/ele>/);
      const altitude = eleMatch?.[1] ? parseFloat(eleMatch[1]) : undefined;

      const timeMatch = content.match(/<time>(.*?)<\/time>/);
      const timestamp = timeMatch?.[1]
        ? new Date(timeMatch[1]).getTime()
        : Date.now();

      trackPoints.push({
        lat,
        lon,
        timestamp,
        altitude: altitude !== undefined && !isNaN(altitude) ? altitude : undefined,
        accuracy: undefined,
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
      isActive: false,
    };
  } catch {
    return null;
  }
}
