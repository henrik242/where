import { describe, test, expect, beforeEach } from 'bun:test';
import { trackStore } from '../src/store';
import type { Track } from '../src/types';

describe('TrackStore', () => {
  beforeEach(() => {
    // Clear all tracks before each test
    const allTracks = trackStore.getAllActiveTracks();
    allTracks.forEach(track => trackStore.deleteTrack(track.id));
  });

  describe('saveTrack', () => {
    test('should save a track with generated color', () => {
      const track: Track = {
        id: 'test-track-1',
        userId: 'abc123',
        name: 'Test Track',
        points: [],
        startTime: Date.now(),
        isActive: true
      };

      trackStore.saveTrack(track);
      const retrieved = trackStore.getTrack('test-track-1');

      expect(retrieved).toBeDefined();
      expect(retrieved?.userId).toBe('abc123');
      expect(retrieved?.name).toBe('Test Track');
      expect(retrieved?.color).toBeDefined();
    });

    test('should assign same color to same userId', () => {
      const track1: Track = {
        id: 'track-1',
        userId: 'abc123',
        name: 'Track 1',
        points: [],
        startTime: Date.now(),
        isActive: true
      };

      const track2: Track = {
        id: 'track-2',
        userId: 'abc123',
        name: 'Track 2',
        points: [],
        startTime: Date.now(),
        isActive: true
      };

      trackStore.saveTrack(track1);
      trackStore.saveTrack(track2);

      const retrieved1 = trackStore.getTrack('track-1');
      const retrieved2 = trackStore.getTrack('track-2');

      expect(retrieved1?.color).toBe(retrieved2?.color);
    });

    test('should assign different colors to different userIds', () => {
      const track1: Track = {
        id: 'track-1',
        userId: 'abc123',
        name: 'Track 1',
        points: [],
        startTime: Date.now(),
        isActive: true
      };

      const track2: Track = {
        id: 'track-2',
        userId: 'xyz789',
        name: 'Track 2',
        points: [],
        startTime: Date.now(),
        isActive: true
      };

      trackStore.saveTrack(track1);
      trackStore.saveTrack(track2);

      const retrieved1 = trackStore.getTrack('track-1');
      const retrieved2 = trackStore.getTrack('track-2');

      expect(retrieved1?.color).not.toBe(retrieved2?.color);
    });
  });

  describe('getTrack', () => {
    test('should return track by id', () => {
      const track: Track = {
        id: 'test-id',
        userId: 'user1',
        name: 'Test',
        points: [],
        startTime: Date.now(),
        isActive: true
      };

      trackStore.saveTrack(track);
      const retrieved = trackStore.getTrack('test-id');

      expect(retrieved).toBeDefined();
      expect(retrieved?.id).toBe('test-id');
    });

    test('should return undefined for non-existent track', () => {
      const retrieved = trackStore.getTrack('non-existent');
      expect(retrieved).toBeUndefined();
    });
  });

  describe('getAllActiveTracks', () => {
    test('should return only active tracks', () => {
      const activeTrack: Track = {
        id: 'active',
        userId: 'user1',
        name: 'Active',
        points: [],
        startTime: Date.now(),
        isActive: true
      };

      const inactiveTrack: Track = {
        id: 'inactive',
        userId: 'user2',
        name: 'Inactive',
        points: [],
        startTime: Date.now(),
        isActive: false
      };

      trackStore.saveTrack(activeTrack);
      trackStore.saveTrack(inactiveTrack);

      const activeTracks = trackStore.getAllActiveTracks();

      expect(activeTracks).toHaveLength(1);
      expect(activeTracks[0].id).toBe('active');
    });

    test('should return empty array when no active tracks', () => {
      const tracks = trackStore.getAllActiveTracks();
      expect(tracks).toEqual([]);
    });
  });

  describe('getTracksByClientIds', () => {
    beforeEach(() => {
      const track1: Track = {
        id: 'track-1',
        userId: 'abc123',
        name: 'Track 1',
        points: [],
        startTime: Date.now(),
        isActive: true
      };

      const track2: Track = {
        id: 'track-2',
        userId: 'xyz789',
        name: 'Track 2',
        points: [],
        startTime: Date.now(),
        isActive: true
      };

      const track3: Track = {
        id: 'track-3',
        userId: 'def456',
        name: 'Track 3',
        points: [],
        startTime: Date.now(),
        isActive: true
      };

      trackStore.saveTrack(track1);
      trackStore.saveTrack(track2);
      trackStore.saveTrack(track3);
    });

    test('should return all tracks when empty filter', () => {
      const tracks = trackStore.getTracksByClientIds([]);
      expect(tracks).toHaveLength(3);
    });

    test('should filter by single client ID', () => {
      const tracks = trackStore.getTracksByClientIds(['abc123']);
      expect(tracks).toHaveLength(1);
      expect(tracks[0].userId).toBe('abc123');
    });

    test('should filter by multiple client IDs', () => {
      const tracks = trackStore.getTracksByClientIds(['abc123', 'xyz789']);
      expect(tracks).toHaveLength(2);
      expect(tracks.map(t => t.userId)).toContain('abc123');
      expect(tracks.map(t => t.userId)).toContain('xyz789');
    });

    test('should return empty array for non-existent client IDs', () => {
      const tracks = trackStore.getTracksByClientIds(['zzz999']);
      expect(tracks).toEqual([]);
    });

    test('should only return active tracks', () => {
      trackStore.updateTrack('track-1', { isActive: false });
      const tracks = trackStore.getTracksByClientIds(['abc123']);
      expect(tracks).toEqual([]);
    });
  });

  describe('updateTrack', () => {
    test('should update track and preserve color', () => {
      const track: Track = {
        id: 'test-id',
        userId: 'user1',
        name: 'Original Name',
        points: [],
        startTime: Date.now(),
        isActive: true
      };

      trackStore.saveTrack(track);
      const originalColor = trackStore.getTrack('test-id')?.color;

      trackStore.updateTrack('test-id', {
        name: 'Updated Name',
        points: [{ lat: 59.9, lon: 10.7, timestamp: Date.now() }]
      });

      const updated = trackStore.getTrack('test-id');
      expect(updated?.name).toBe('Updated Name');
      expect(updated?.points).toHaveLength(1);
      expect(updated?.color).toBe(originalColor);
    });

    test('should return undefined for non-existent track', () => {
      const result = trackStore.updateTrack('non-existent', { name: 'Test' });
      expect(result).toBeUndefined();
    });
  });

  describe('deleteTrack', () => {
    test('should delete existing track', () => {
      const track: Track = {
        id: 'test-id',
        userId: 'user1',
        name: 'Test',
        points: [],
        startTime: Date.now(),
        isActive: true
      };

      trackStore.saveTrack(track);
      const deleted = trackStore.deleteTrack('test-id');

      expect(deleted).toBe(true);
      expect(trackStore.getTrack('test-id')).toBeUndefined();
    });

    test('should return false for non-existent track', () => {
      const deleted = trackStore.deleteTrack('non-existent');
      expect(deleted).toBe(false);
    });
  });
});

