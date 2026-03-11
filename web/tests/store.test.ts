import { describe, test, expect, beforeEach, afterAll } from 'bun:test';
import { TrackStore } from '../src/store';
import type { Track } from '../src/types';

const store = new TrackStore(':memory:');

afterAll(() => {
  store.close();
});

describe('TrackStore', () => {
  beforeEach(() => {
    const allTracks = store.getAllActiveTracks();
    allTracks.forEach(track => store.deleteTrack(track.id));
    // Also clean up inactive tracks from last 24h
    const allRecent = store.getAllTracks();
    allRecent.forEach(track => store.deleteTrack(track.id));
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

      store.saveTrack(track);
      const retrieved = store.getTrack('test-track-1');

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

      store.saveTrack(track1);
      store.saveTrack(track2);

      const retrieved1 = store.getTrack('track-1');
      const retrieved2 = store.getTrack('track-2');

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

      store.saveTrack(track1);
      store.saveTrack(track2);

      const retrieved1 = store.getTrack('track-1');
      const retrieved2 = store.getTrack('track-2');

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

      store.saveTrack(track);
      const retrieved = store.getTrack('test-id');

      expect(retrieved).toBeDefined();
      expect(retrieved?.id).toBe('test-id');
    });

    test('should return undefined for non-existent track', () => {
      const retrieved = store.getTrack('non-existent');
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

      store.saveTrack(activeTrack);
      store.saveTrack(inactiveTrack);

      const activeTracks = store.getAllActiveTracks();

      expect(activeTracks).toHaveLength(1);
      expect(activeTracks[0].id).toBe('active');
    });

    test('should return empty array when no active tracks', () => {
      const tracks = store.getAllActiveTracks();
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

      store.saveTrack(track1);
      store.saveTrack(track2);
      store.saveTrack(track3);
    });

    test('should return all tracks when empty filter', () => {
      const tracks = store.getTracksByClientIds([]);
      expect(tracks).toHaveLength(3);
    });

    test('should filter by single client ID', () => {
      const tracks = store.getTracksByClientIds(['abc123']);
      expect(tracks).toHaveLength(1);
      expect(tracks[0].userId).toBe('abc123');
    });

    test('should filter by multiple client IDs', () => {
      const tracks = store.getTracksByClientIds(['abc123', 'xyz789']);
      expect(tracks).toHaveLength(2);
      expect(tracks.map(t => t.userId)).toContain('abc123');
      expect(tracks.map(t => t.userId)).toContain('xyz789');
    });

    test('should return empty array for non-existent client IDs', () => {
      const tracks = store.getTracksByClientIds(['zzz999']);
      expect(tracks).toEqual([]);
    });

    test('should only return active tracks', () => {
      store.updateTrack('track-1', { isActive: false });
      const tracks = store.getTracksByClientIds(['abc123']);
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

      store.saveTrack(track);
      const originalColor = store.getTrack('test-id')?.color;

      store.updateTrack('test-id', {
        name: 'Updated Name',
        points: [{ lat: 59.9, lon: 10.7, timestamp: Date.now() }]
      });

      const updated = store.getTrack('test-id');
      expect(updated?.name).toBe('Updated Name');
      expect(updated?.points).toHaveLength(1);
      expect(updated?.color).toBe(originalColor);
    });

    test('should return undefined for non-existent track', () => {
      const result = store.updateTrack('non-existent', { name: 'Test' });
      expect(result).toBeUndefined();
    });
  });

  describe('cleanupOldTracks', () => {
    test('should delete inactive tracks older than 24h', () => {
      const oldTime = Date.now() - 25 * 60 * 60 * 1000; // 25 hours ago
      const oldTrack: Track = {
        id: 'old-inactive',
        userId: 'user1',
        name: 'Old Track',
        points: [],
        startTime: oldTime,
        isActive: false,
        lastUpdateTime: oldTime,
      };
      store.saveTrack(oldTrack);

      const deleted = store.cleanupOldTracks();
      expect(deleted).toHaveLength(1);
      expect(deleted[0].id).toBe('old-inactive');
      expect(store.getTrack('old-inactive')).toBeUndefined();
    });

    test('should not delete active tracks even if old', () => {
      const oldTime = Date.now() - 25 * 60 * 60 * 1000;
      const activeOldTrack: Track = {
        id: 'old-active',
        userId: 'user1',
        name: 'Old Active Track',
        points: [],
        startTime: oldTime,
        isActive: true,
        lastUpdateTime: oldTime,
      };
      store.saveTrack(activeOldTrack);

      const deleted = store.cleanupOldTracks();
      expect(deleted).toHaveLength(0);
      expect(store.getTrack('old-active')).toBeDefined();
    });

    test('should not delete inactive tracks with recent lastUpdateTime', () => {
      const oldStart = Date.now() - 25 * 60 * 60 * 1000;
      const recentUpdate = Date.now() - 1 * 60 * 60 * 1000; // 1 hour ago
      const recentTrack: Track = {
        id: 'recent-inactive',
        userId: 'user1',
        name: 'Recently Updated',
        points: [],
        startTime: oldStart,
        isActive: false,
        lastUpdateTime: recentUpdate,
      };
      store.saveTrack(recentTrack);

      const deleted = store.cleanupOldTracks();
      expect(deleted).toHaveLength(0);
      expect(store.getTrack('recent-inactive')).toBeDefined();
    });

    test('should return deleted track info for broadcasting', () => {
      const oldTime = Date.now() - 25 * 60 * 60 * 1000;
      store.saveTrack({
        id: 'del-1',
        userId: 'userA',
        name: 'Track A',
        points: [],
        startTime: oldTime,
        isActive: false,
        lastUpdateTime: oldTime,
      });
      store.saveTrack({
        id: 'del-2',
        userId: 'userB',
        name: 'Track B',
        points: [],
        startTime: oldTime,
        isActive: false,
        lastUpdateTime: oldTime,
      });

      const deleted = store.cleanupOldTracks();
      expect(deleted).toHaveLength(2);
      const ids = deleted.map(d => d.id).sort();
      expect(ids).toEqual(['del-1', 'del-2']);
      expect(deleted.find(d => d.id === 'del-1')?.userId).toBe('userA');
      expect(deleted.find(d => d.id === 'del-2')?.userId).toBe('userB');
    });
  });

  describe('addPoint', () => {
    test('should add point and update track metadata', () => {
      const track: Track = {
        id: 'addpoint-test',
        userId: 'user1',
        name: 'Test',
        points: [],
        startTime: Date.now(),
        isActive: true,
      };
      store.saveTrack(track);

      const point = { lat: 59.9, lon: 10.7, timestamp: Date.now() };
      const updated = store.addPoint('addpoint-test', point, {
        lastUpdateTime: Date.now(),
      });

      expect(updated).toBeDefined();
      expect(updated?.points).toHaveLength(1);
      expect(updated?.points[0].lat).toBe(59.9);
    });

    test('should return undefined for non-existent track', () => {
      const point = { lat: 59.9, lon: 10.7, timestamp: Date.now() };
      const result = store.addPoint('nonexistent', point);
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

      store.saveTrack(track);
      const deleted = store.deleteTrack('test-id');

      expect(deleted).toBe(true);
      expect(store.getTrack('test-id')).toBeUndefined();
    });

    test('should return false for non-existent track', () => {
      const deleted = store.deleteTrack('non-existent');
      expect(deleted).toBe(false);
    });
  });
});
