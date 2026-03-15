import { Database } from 'bun:sqlite';
import { mkdirSync } from 'node:fs';
import { dirname } from 'node:path';
import type { Track, TrackPoint } from './types';

const COLORS = [
  '#FF5722', '#E91E63', '#9C27B0', '#673AB7', '#3F51B5',
  '#2196F3', '#00BCD4', '#009688', '#4CAF50', '#8BC34A',
  '#CDDC39', '#FFC107', '#FF9800', '#FF5722', '#795548'
];

function generateColor(userId: string): string {
  const hash = userId.split('').reduce((acc, char) => {
    return char.charCodeAt(0) + ((acc << 5) - acc);
  }, 0);
  return COLORS[Math.abs(hash) % COLORS.length];
}

interface TrackRow {
  id: string;
  userId: string;
  name: string;
  startTime: number;
  endTime: number | null;
  isActive: number;
  lastUpdateTime: number;
  color: string | null;
}

interface PointRow {
  id: number;
  trackId: string;
  lat: number;
  lon: number;
  timestamp: number;
  altitude: number | null;
  accuracy: number | null;
}

function rowToTrack(row: TrackRow, points: TrackPoint[]): Track {
  return {
    id: row.id,
    userId: row.userId,
    name: row.name,
    startTime: row.startTime,
    endTime: row.endTime ?? undefined,
    isActive: row.isActive === 1,
    lastUpdateTime: row.lastUpdateTime,
    color: row.color ?? undefined,
    points,
  };
}

function rowToPoint(row: PointRow): TrackPoint {
  return {
    lat: row.lat,
    lon: row.lon,
    timestamp: row.timestamp,
    altitude: row.altitude ?? undefined,
    accuracy: row.accuracy ?? undefined,
  };
}

export class TrackStore {
  private db: Database;
  private stmts: ReturnType<TrackStore['prepareStatements']>;

  constructor(dbPath: string = './data/tracking.db') {
    if (dbPath !== ':memory:') {
      mkdirSync(dirname(dbPath), { recursive: true });
    }

    this.db = new Database(dbPath);
    this.db.exec('PRAGMA journal_mode = WAL');
    this.db.exec('PRAGMA foreign_keys = ON');
    this.createTables();
    this.stmts = this.prepareStatements();
  }

  private createTables() {
    this.db.exec(`
      CREATE TABLE IF NOT EXISTS tracks (
        id TEXT PRIMARY KEY,
        userId TEXT NOT NULL,
        name TEXT NOT NULL DEFAULT '',
        startTime INTEGER NOT NULL,
        endTime INTEGER,
        isActive INTEGER NOT NULL DEFAULT 1,
        lastUpdateTime INTEGER NOT NULL,
        color TEXT
      );

      CREATE TABLE IF NOT EXISTS points (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        trackId TEXT NOT NULL REFERENCES tracks(id) ON DELETE CASCADE,
        lat REAL NOT NULL,
        lon REAL NOT NULL,
        timestamp INTEGER NOT NULL,
        altitude REAL,
        accuracy REAL
      );

      CREATE INDEX IF NOT EXISTS idx_points_trackId ON points(trackId);
      CREATE INDEX IF NOT EXISTS idx_tracks_userId ON tracks(userId);
      CREATE INDEX IF NOT EXISTS idx_tracks_startTime ON tracks(startTime);

      CREATE TABLE IF NOT EXISTS session_counts (
        date TEXT NOT NULL,
        platform TEXT NOT NULL,
        count INTEGER NOT NULL DEFAULT 0,
        PRIMARY KEY (date, platform)
      );
    `);
  }

  private prepareStatements() {
    return {
      insertTrack: this.db.prepare<void, [string, string, string, number, number | null, number, number, string | null]>(
        `INSERT OR REPLACE INTO tracks (id, userId, name, startTime, endTime, isActive, lastUpdateTime, color)
         VALUES (?, ?, ?, ?, ?, ?, ?, ?)`
      ),
      getTrack: this.db.prepare<TrackRow, [string]>(
        'SELECT * FROM tracks WHERE id = ?'
      ),
      getPoints: this.db.prepare<PointRow, [string]>(
        'SELECT * FROM points WHERE trackId = ? ORDER BY timestamp'
      ),
      getActiveTracks: this.db.prepare<TrackRow, []>(
        'SELECT * FROM tracks WHERE isActive = 1'
      ),
      getRecentTracks: this.db.prepare<TrackRow, [number]>(
        'SELECT * FROM tracks WHERE lastUpdateTime >= ? OR isActive = 1'
      ),
      insertPoint: this.db.prepare<void, [string, number, number, number, number | null, number | null]>(
        `INSERT INTO points (trackId, lat, lon, timestamp, altitude, accuracy)
         VALUES (?, ?, ?, ?, ?, ?)`
      ),
      deleteTrack: this.db.prepare<void, [string]>(
        'DELETE FROM tracks WHERE id = ?'
      ),
      getActiveByUser: this.db.prepare<TrackRow, [string]>(
        'SELECT * FROM tracks WHERE userId = ? AND isActive = 1'
      ),
      cleanupOld: this.db.prepare<TrackRow, [number]>(
        'SELECT * FROM tracks WHERE isActive = 0 AND lastUpdateTime < ?'
      ),
      deleteOld: this.db.prepare<void, [number]>(
        'DELETE FROM tracks WHERE isActive = 0 AND lastUpdateTime < ?'
      ),
    };
  }

  saveTrack(track: Track): void {
    const color = track.color || generateColor(track.userId);
    this.stmts.insertTrack.run(
      track.id,
      track.userId,
      track.name,
      track.startTime,
      track.endTime ?? null,
      track.isActive ? 1 : 0,
      track.lastUpdateTime ?? track.startTime,
      color,
    );

    if (track.points.length > 0) {
      const insertMany = this.db.transaction(() => {
        for (const p of track.points) {
          this.stmts.insertPoint.run(
            track.id, p.lat, p.lon, p.timestamp,
            p.altitude ?? null, p.accuracy ?? null,
          );
        }
      });
      insertMany();
    }
  }

  getTrack(trackId: string): Track | undefined {
    const row = this.stmts.getTrack.get(trackId);
    if (!row) return undefined;
    const points = this.stmts.getPoints.all(trackId).map(rowToPoint);
    return rowToTrack(row, points);
  }

  private buildTracksWithPoints(rows: TrackRow[]): Track[] {
    if (rows.length === 0) return [];
    const trackIds = rows.map(r => r.id);
    const placeholders = trackIds.map(() => '?').join(',');
    const allPoints = this.db.prepare<PointRow, any[]>(
      `SELECT * FROM points WHERE trackId IN (${placeholders}) ORDER BY timestamp`
    ).all(...trackIds);

    const pointsByTrack = new Map<string, TrackPoint[]>();
    for (const p of allPoints) {
      const list = pointsByTrack.get(p.trackId);
      if (list) list.push(rowToPoint(p));
      else pointsByTrack.set(p.trackId, [rowToPoint(p)]);
    }

    return rows.map(row => rowToTrack(row, pointsByTrack.get(row.id) || []));
  }

  getAllActiveTracks(): Track[] {
    const rows = this.stmts.getActiveTracks.all();
    return this.buildTracksWithPoints(rows);
  }

  getAllTracks(): Track[] {
    const cutoff = Date.now() - 24 * 60 * 60 * 1000;
    const rows = this.stmts.getRecentTracks.all(cutoff);
    return this.buildTracksWithPoints(rows);
  }

  getTracksByClientIds(clientIds: string[], includeHistorical: boolean = false): Track[] {
    if (clientIds.length === 0) {
      return includeHistorical ? this.getAllTracks() : this.getAllActiveTracks();
    }

    const cutoff = Date.now() - 24 * 60 * 60 * 1000;
    const placeholders = clientIds.map(() => '?').join(',');

    let sql: string;
    let params: any[];
    if (includeHistorical) {
      sql = `SELECT * FROM tracks WHERE userId IN (${placeholders}) AND (lastUpdateTime >= ? OR isActive = 1)`;
      params = [...clientIds, cutoff];
    } else {
      sql = `SELECT * FROM tracks WHERE userId IN (${placeholders}) AND isActive = 1`;
      params = clientIds;
    }

    const rows = this.db.prepare<TrackRow, any[]>(sql).all(...params);
    return this.buildTracksWithPoints(rows);
  }

  updateTrack(trackId: string, updates: Partial<Track>): Track | undefined {
    const existing = this.stmts.getTrack.get(trackId);
    if (!existing) return undefined;

    const setClauses: string[] = [];
    const values: any[] = [];

    if (updates.name !== undefined) {
      setClauses.push('name = ?');
      values.push(updates.name);
    }
    if (updates.isActive !== undefined) {
      setClauses.push('isActive = ?');
      values.push(updates.isActive ? 1 : 0);
    }
    if (updates.endTime !== undefined) {
      setClauses.push('endTime = ?');
      values.push(updates.endTime);
    } else if ('endTime' in updates && updates.endTime === undefined) {
      setClauses.push('endTime = NULL');
    }
    if (updates.lastUpdateTime !== undefined) {
      setClauses.push('lastUpdateTime = ?');
      values.push(updates.lastUpdateTime);
    }

    if (setClauses.length > 0) {
      values.push(trackId);
      this.db.prepare(`UPDATE tracks SET ${setClauses.join(', ')} WHERE id = ?`).run(...values);
    }

    // Handle points replacement if provided in updates
    if (updates.points !== undefined) {
      this.db.prepare('DELETE FROM points WHERE trackId = ?').run(trackId);
      if (updates.points.length > 0) {
        const insertMany = this.db.transaction(() => {
          for (const p of updates.points!) {
            this.stmts.insertPoint.run(
              trackId, p.lat, p.lon, p.timestamp,
              p.altitude ?? null, p.accuracy ?? null,
            );
          }
        });
        insertMany();
      }
    }

    return this.getTrack(trackId);
  }

  addPoint(trackId: string, point: TrackPoint, updates: Partial<Track> = {}): Track | undefined {
    const existing = this.stmts.getTrack.get(trackId);
    if (!existing) return undefined;

    this.db.transaction(() => {
      this.stmts.insertPoint.run(
        trackId, point.lat, point.lon, point.timestamp,
        point.altitude ?? null, point.accuracy ?? null,
      );

      const setClauses: string[] = [];
      const values: any[] = [];

      if (updates.isActive !== undefined) {
        setClauses.push('isActive = ?');
        values.push(updates.isActive ? 1 : 0);
      }
      if (updates.lastUpdateTime !== undefined) {
        setClauses.push('lastUpdateTime = ?');
        values.push(updates.lastUpdateTime);
      }
      if (updates.endTime !== undefined) {
        setClauses.push('endTime = ?');
        values.push(updates.endTime);
      } else if ('endTime' in updates && updates.endTime === undefined) {
        setClauses.push('endTime = NULL');
      }

      if (setClauses.length > 0) {
        values.push(trackId);
        this.db.prepare(`UPDATE tracks SET ${setClauses.join(', ')} WHERE id = ?`).run(...values);
      }
    })();

    return this.getTrack(trackId);
  }

  deleteTrack(trackId: string): boolean {
    const existing = this.stmts.getTrack.get(trackId);
    if (!existing) return false;
    this.stmts.deleteTrack.run(trackId);
    return true;
  }

  getActiveTracksByUser(userId: string): Track[] {
    const rows = this.stmts.getActiveByUser.all(userId);
    return this.buildTracksWithPoints(rows);
  }

  cleanupOldTracks(): { id: string; userId: string }[] {
    const cutoff = Date.now() - 24 * 60 * 60 * 1000;
    const rows = this.stmts.cleanupOld.all(cutoff);
    if (rows.length > 0) {
      this.stmts.deleteOld.run(cutoff);
    }
    return rows.map(r => ({ id: r.id, userId: r.userId }));
  }

  incrementSessionCount(platform: string): void {
    const date = new Date().toISOString().slice(0, 10); // YYYY-MM-DD
    this.db.prepare(
      `INSERT INTO session_counts (date, platform, count) VALUES (?, ?, 1)
       ON CONFLICT(date, platform) DO UPDATE SET count = count + 1`
    ).run(date, platform);
  }

  getSessionStats(): { day: Record<string, number>; week: Record<string, number>; month: Record<string, number> } {
    const now = new Date();
    const today = now.toISOString().slice(0, 10);
    const weekAgo = new Date(now.getTime() - 7 * 24 * 60 * 60 * 1000).toISOString().slice(0, 10);
    const monthAgo = new Date(now.getTime() - 30 * 24 * 60 * 60 * 1000).toISOString().slice(0, 10);

    const sumByRange = (since: string) => {
      const rows = this.db.prepare<{ platform: string; total: number }, [string]>(
        'SELECT platform, SUM(count) as total FROM session_counts WHERE date >= ? GROUP BY platform'
      ).all(since);
      const result: Record<string, number> = {};
      for (const row of rows) {
        result[row.platform] = row.total;
      }
      return result;
    };

    return {
      day: sumByRange(today),
      week: sumByRange(weekAgo),
      month: sumByRange(monthAgo),
    };
  }

  close(): void {
    this.db.close();
  }
}

export const trackStore = new TrackStore();
