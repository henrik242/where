import { describe, test, expect, beforeEach, afterAll } from 'bun:test';
import { TrackStore } from '../src/store';
import type { Track } from '../src/types';

const store = new TrackStore(':memory:');

afterAll(() => {
  store.close();
});

describe('TrackStore', () => {
  beforeEach(() => {
    // Wipe all tracks (including old ones outside the 24h window)
    (store as any).db.exec('DELETE FROM points');
    (store as any).db.exec('DELETE FROM tracks');
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

  describe('getAllTracks (historical)', () => {
    test('should return inactive tracks with recent lastUpdateTime', () => {
      const track: Track = {
        id: 'hist-recent',
        userId: 'user1',
        name: 'Recently stopped',
        points: [],
        startTime: Date.now() - 20 * 60 * 60 * 1000, // started 20h ago
        isActive: false,
        lastUpdateTime: Date.now() - 1 * 60 * 60 * 1000, // updated 1h ago
      };
      store.saveTrack(track);

      const all = store.getAllTracks();
      expect(all.some(t => t.id === 'hist-recent')).toBe(true);
    });

    test('should not return inactive tracks with old lastUpdateTime', () => {
      const track: Track = {
        id: 'hist-old',
        userId: 'user1',
        name: 'Old stopped',
        points: [],
        startTime: Date.now() - 48 * 60 * 60 * 1000, // started 48h ago
        isActive: false,
        lastUpdateTime: Date.now() - 30 * 60 * 60 * 1000, // updated 30h ago
      };
      store.saveTrack(track);

      const all = store.getAllTracks();
      expect(all.some(t => t.id === 'hist-old')).toBe(false);
    });

    test('should return active tracks regardless of age', () => {
      const track: Track = {
        id: 'hist-active-old',
        userId: 'user1',
        name: 'Old but active',
        points: [],
        startTime: Date.now() - 48 * 60 * 60 * 1000,
        isActive: true,
        lastUpdateTime: Date.now() - 48 * 60 * 60 * 1000,
      };
      store.saveTrack(track);

      const all = store.getAllTracks();
      expect(all.some(t => t.id === 'hist-active-old')).toBe(true);
    });
  });

  describe('getTracksByClientIds (historical)', () => {
    test('should return historical tracks by lastUpdateTime not startTime', () => {
      const track: Track = {
        id: 'client-hist',
        userId: 'cli001',
        name: 'Client historical',
        points: [],
        startTime: Date.now() - 26 * 60 * 60 * 1000, // started 26h ago
        isActive: false,
        lastUpdateTime: Date.now() - 2 * 60 * 60 * 1000, // updated 2h ago
      };
      store.saveTrack(track);

      const tracks = store.getTracksByClientIds(['cli001'], true);
      expect(tracks.some(t => t.id === 'client-hist')).toBe(true);
    });

    test('should not return old historical tracks for client', () => {
      const track: Track = {
        id: 'client-old',
        userId: 'cli002',
        name: 'Old client track',
        points: [],
        startTime: Date.now() - 48 * 60 * 60 * 1000,
        isActive: false,
        lastUpdateTime: Date.now() - 30 * 60 * 60 * 1000,
      };
      store.saveTrack(track);

      const tracks = store.getTracksByClientIds(['cli002'], true);
      expect(tracks.some(t => t.id === 'client-old')).toBe(false);
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

  describe('sessionCounts', () => {
    test('should increment and retrieve session counts', () => {
      const s = new TrackStore(':memory:');
      s.incrementSessionCount('ios');
      s.incrementSessionCount('ios');
      s.incrementSessionCount('android');

      const stats = s.getSessionStats();
      expect(stats.day['ios']).toBe(2);
      expect(stats.day['android']).toBe(1);
      expect(stats.week['ios']).toBe(2);
      expect(stats.week['android']).toBe(1);
      expect(stats.month['ios']).toBe(2);
      expect(stats.month['android']).toBe(1);
      s.close();
    });

    test('should return empty objects when no sessions', () => {
      const s = new TrackStore(':memory:');
      const stats = s.getSessionStats();
      expect(stats.day).toEqual({});
      expect(stats.week).toEqual({});
      expect(stats.month).toEqual({});
      s.close();
    });

    test('should separate counts by date and aggregate by time window', () => {
      const s = new TrackStore(':memory:');
      const db = (s as any).db;

      const today = new Date().toISOString().slice(0, 10);
      const threeDaysAgo = new Date(Date.now() - 3 * 24 * 60 * 60 * 1000).toISOString().slice(0, 10);
      const tenDaysAgo = new Date(Date.now() - 10 * 24 * 60 * 60 * 1000).toISOString().slice(0, 10);
      const sixtyDaysAgo = new Date(Date.now() - 60 * 24 * 60 * 60 * 1000).toISOString().slice(0, 10);

      // Insert counts for different dates directly
      db.prepare('INSERT INTO session_counts (date, platform, count) VALUES (?, ?, ?)').run(today, 'ios', 5);
      db.prepare('INSERT INTO session_counts (date, platform, count) VALUES (?, ?, ?)').run(today, 'android', 3);
      db.prepare('INSERT INTO session_counts (date, platform, count) VALUES (?, ?, ?)').run(threeDaysAgo, 'ios', 10);
      db.prepare('INSERT INTO session_counts (date, platform, count) VALUES (?, ?, ?)').run(threeDaysAgo, 'android', 7);
      db.prepare('INSERT INTO session_counts (date, platform, count) VALUES (?, ?, ?)').run(tenDaysAgo, 'ios', 20);
      db.prepare('INSERT INTO session_counts (date, platform, count) VALUES (?, ?, ?)').run(tenDaysAgo, 'android', 15);
      db.prepare('INSERT INTO session_counts (date, platform, count) VALUES (?, ?, ?)').run(sixtyDaysAgo, 'ios', 100);

      const stats = s.getSessionStats();

      // Day: only today
      expect(stats.day['ios']).toBe(5);
      expect(stats.day['android']).toBe(3);

      // Week: today + 3 days ago (within 7 days)
      expect(stats.week['ios']).toBe(15);
      expect(stats.week['android']).toBe(10);

      // Month: today + 3 days ago + 10 days ago (within 30 days)
      expect(stats.month['ios']).toBe(35);
      expect(stats.month['android']).toBe(25);

      // 60 days ago should not be in any window
      s.close();
    });

    test('should accumulate with multiple increments on same day', () => {
      const s = new TrackStore(':memory:');
      for (let i = 0; i < 10; i++) {
        s.incrementSessionCount('ios');
      }
      for (let i = 0; i < 5; i++) {
        s.incrementSessionCount('android');
      }

      const stats = s.getSessionStats();
      expect(stats.day['ios']).toBe(10);
      expect(stats.day['android']).toBe(5);
      s.close();
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
