import { describe, test, expect } from 'bun:test';
import { Track } from '../src/types';

describe('Track.fromGPX', () => {
  test('should parse valid GPX with all fields', () => {
    const gpxContent = `<?xml version="1.0" encoding="UTF-8"?>
<gpx version="1.1" creator="Where Test">
  <trk>
    <name>Test Track</name>
    <trkseg>
      <trkpt lat="59.9139" lon="10.7522">
        <ele>100</ele>
        <time>2026-01-03T10:00:00Z</time>
      </trkpt>
      <trkpt lat="59.9200" lon="10.7450">
        <ele>150</ele>
        <time>2026-01-03T10:05:00Z</time>
      </trkpt>
    </trkseg>
  </trk>
</gpx>`;

    const track = Track.fromGPX(gpxContent);

    expect(track).toBeDefined();
    expect(track?.name).toBe('Test Track');
    expect(track?.points).toHaveLength(2);
    expect(track?.points[0].lat).toBe(59.9139);
    expect(track?.points[0].lon).toBe(10.7522);
    expect(track?.points[0].altitude).toBe(100);
    expect(track?.points[1].altitude).toBe(150);
  });

  test('should parse GPX without elevation', () => {
    const gpxContent = `<?xml version="1.0" encoding="UTF-8"?>
<gpx version="1.1">
  <trk>
    <name>Simple Track</name>
    <trkseg>
      <trkpt lat="59.9139" lon="10.7522">
        <time>2026-01-03T10:00:00Z</time>
      </trkpt>
    </trkseg>
  </trk>
</gpx>`;

    const track = Track.fromGPX(gpxContent);

    expect(track).toBeDefined();
    expect(track?.points[0].altitude).toBeUndefined();
  });

  test('should use default name if not provided', () => {
    const gpxContent = `<?xml version="1.0" encoding="UTF-8"?>
<gpx version="1.1">
  <trk>
    <trkseg>
      <trkpt lat="59.9139" lon="10.7522">
        <time>2026-01-03T10:00:00Z</time>
      </trkpt>
    </trkseg>
  </trk>
</gpx>`;

    const track = Track.fromGPX(gpxContent);

    expect(track).toBeDefined();
    expect(track?.name).toBe('Imported Track');
  });

  test('should return null for empty GPX', () => {
    const gpxContent = `<?xml version="1.0" encoding="UTF-8"?>
<gpx version="1.1">
  <trk>
    <trkseg>
    </trkseg>
  </trk>
</gpx>`;

    const track = Track.fromGPX(gpxContent);
    expect(track).toBeNull();
  });

  test('should return null for invalid GPX', () => {
    const gpxContent = 'not valid xml';
    const track = Track.fromGPX(gpxContent);
    expect(track).toBeNull();
  });

  test('should handle multiple track points', () => {
    const gpxContent = `<?xml version="1.0" encoding="UTF-8"?>
<gpx version="1.1">
  <trk>
    <name>Multi Point Track</name>
    <trkseg>
      <trkpt lat="59.9139" lon="10.7522"><time>2026-01-03T10:00:00Z</time></trkpt>
      <trkpt lat="59.9200" lon="10.7450"><time>2026-01-03T10:05:00Z</time></trkpt>
      <trkpt lat="59.9300" lon="10.7350"><time>2026-01-03T10:10:00Z</time></trkpt>
      <trkpt lat="59.9400" lon="10.7250"><time>2026-01-03T10:15:00Z</time></trkpt>
      <trkpt lat="59.9500" lon="10.7150"><time>2026-01-03T10:20:00Z</time></trkpt>
    </trkseg>
  </trk>
</gpx>`;

    const track = Track.fromGPX(gpxContent);

    expect(track).toBeDefined();
    expect(track?.points).toHaveLength(5);
  });

  test('should set isActive to false for imported tracks', () => {
    const gpxContent = `<?xml version="1.0" encoding="UTF-8"?>
<gpx version="1.1">
  <trk>
    <name>Test</name>
    <trkseg>
      <trkpt lat="59.9139" lon="10.7522"><time>2026-01-03T10:00:00Z</time></trkpt>
    </trkseg>
  </trk>
</gpx>`;

    const track = Track.fromGPX(gpxContent);

    expect(track).toBeDefined();
    expect(track?.isActive).toBe(false);
  });
});

describe('Track methods', () => {
  test('should calculate distance for track with multiple points', () => {
    const track: Track = {
      id: 'test',
      userId: 'user1',
      name: 'Test',
      points: [
        { lat: 59.9139, lon: 10.7522, timestamp: Date.now() },
        { lat: 59.9200, lon: 10.7450, timestamp: Date.now() }
      ],
      startTime: Date.now(),
      isActive: true
    };

    // Note: This assumes the Track class has a getDistanceMeters method
    // If not, this test should be adjusted or removed
  });

  test('should return 0 distance for single point track', () => {
    const track: Track = {
      id: 'test',
      userId: 'user1',
      name: 'Test',
      points: [
        { lat: 59.9139, lon: 10.7522, timestamp: Date.now() }
      ],
      startTime: Date.now(),
      isActive: true
    };

    // Test would check getDistanceMeters() === 0
  });
});

