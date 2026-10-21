import { Database } from 'bun:sqlite';
import { statSync } from 'node:fs';
import { readMeta, decodeGeometry, type PlaceKind, type PlaceMeta } from './db';
import { bboxAround, containsPoint, distanceMeters } from './geometry';

/**
 * Offline stand-in for the app's two Overpass lookups: the named building within 50 m of a
 * point, and the named water polygon containing it. Polygons come pre-filtered from an OSM
 * extract (scripts/update-places.sh) with one R*Tree per kind, so a lookup is a bbox hit plus
 * an exact geometry test.
 */

export const BUILDING_RADIUS_M = 50;
const STALE_AFTER_MS = 14 * 24 * 60 * 60 * 1000;
const STALE_WARN_INTERVAL_MS = 24 * 60 * 60 * 1000;

/** `unavailable`: no index loaded. `outside`: point not covered by the extract. */
export type LookupResult = { name: string | null } | 'unavailable' | 'outside';

export interface PlaceLookup {
  lookup(kind: PlaceKind, lat: number, lon: number): LookupResult;
}

interface Candidate {
  name: string;
  geom: Uint8Array;
  area: number;
}

export class PlaceIndex implements PlaceLookup {
  readonly meta: PlaceMeta;
  private buildingQuery;
  private waterQuery;

  /** Throws if the database is not a places index. */
  constructor(private db: Database) {
    this.meta = readMeta(db);
    const select = (index: string) => `
      SELECT f.name, f.geom, (i.maxx - i.minx) * (i.maxy - i.miny) AS area
      FROM ${index} i JOIN features f ON f.id = i.id`;
    this.buildingQuery = db.prepare<Candidate, [number, number, number, number]>(
      `${select('building_idx')} WHERE i.maxx >= ? AND i.minx <= ? AND i.maxy >= ? AND i.miny <= ?`
    );
    // Smallest first, so a lake inside a river polygon (or similar overlap) wins.
    this.waterQuery = db.prepare<Candidate, [number, number, number, number]>(
      `${select('water_idx')} WHERE i.minx <= ? AND i.maxx >= ? AND i.miny <= ? AND i.maxy >= ?
       ORDER BY area`
    );
  }

  /** Throws if the file is not a places index; the handle is closed on failure. */
  static open(path: string): PlaceIndex {
    const db = new Database(path, { readonly: true });
    try {
      return new PlaceIndex(db);
    } catch (e) {
      db.close();
      throw e;
    }
  }

  lookup(kind: PlaceKind, lat: number, lon: number): LookupResult {
    if (this.meta.coverage && !containsPoint(this.meta.coverage, lat, lon)) return 'outside';
    return { name: kind === 'building' ? this.building(lat, lon) : this.water(lat, lon) };
  }

  /** Nearest named building within 50 m; when inside several, the smallest. */
  building(lat: number, lon: number): string | null {
    const box = bboxAround(lat, lon, BUILDING_RADIUS_M);
    let best: { name: string; distance: number; area: number } | null = null;
    for (const row of this.buildingQuery.all(box.minX, box.maxX, box.minY, box.maxY)) {
      const distance = distanceMeters(decodeGeometry(row.geom), lat, lon);
      if (distance > BUILDING_RADIUS_M) continue;
      if (!best || distance < best.distance || (distance === best.distance && row.area < best.area)) {
        best = { name: row.name, distance, area: row.area };
      }
    }
    return best?.name ?? null;
  }

  /** Smallest named water polygon containing the point. */
  water(lat: number, lon: number): string | null {
    for (const row of this.waterQuery.all(lon, lon, lat, lat)) {
      if (containsPoint(decodeGeometry(row.geom), lat, lon)) return row.name;
    }
    return null;
  }

  describe(): string {
    const { builtAt, source, buildings, water } = this.meta;
    return `built ${builtAt} from ${source}, ${buildings} buildings, ${water} water`;
  }

  close() {
    this.db.close();
  }
}

function mtimeOf(path: string): number {
  return statSync(path, { throwIfNoEntry: false })?.mtimeMs ?? 0;
}

/**
 * Reopens the database when the file is replaced, so a rebuild (renamed into place) is picked
 * up without a restart. The working index is kept if the replacement cannot be opened.
 */
export class ReloadingPlaceIndex implements PlaceLookup {
  private index: PlaceIndex | null = null;
  private mtime = 0;
  private lastStaleWarning = 0;

  constructor(private path: string) {}

  private current(): PlaceIndex | null {
    const mtime = mtimeOf(this.path);
    if (mtime === 0 || mtime === this.mtime) return this.index;
    // Recorded even on failure, so a bad file is retried only once it is replaced again.
    this.mtime = mtime;
    try {
      const next = PlaceIndex.open(this.path);
      this.index?.close();
      this.index = next;
      console.log(`Places index loaded: ${next.describe()}`);
    } catch (e) {
      console.error(`Failed to open places index ${this.path}, keeping previous:`, e);
    }
    return this.index;
  }

  private warnIfStale(index: PlaceIndex) {
    const now = Date.now();
    const age = now - Date.parse(index.meta.builtAt);
    if (!(age > STALE_AFTER_MS) || now - this.lastStaleWarning < STALE_WARN_INTERVAL_MS) return;
    this.lastStaleWarning = now;
    console.warn(`Places index is ${Math.floor(age / 86_400_000)} days old (${index.describe()})`);
  }

  lookup(kind: PlaceKind, lat: number, lon: number): LookupResult {
    const index = this.current();
    if (!index) return 'unavailable';
    this.warnIfStale(index);
    return index.lookup(kind, lat, lon);
  }

  /** One line for the startup log. */
  describe(): string {
    const index = this.current();
    return index ? index.describe() : `missing at ${this.path}, /api/places answers 503`;
  }

  close() {
    this.index?.close();
    this.index = null;
    this.mtime = 0;
  }
}
