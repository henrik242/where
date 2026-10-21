import type { Database } from 'bun:sqlite';
import type { Polygon } from './geometry';

/** Shared by the build (writes) and the server (reads). */

export const PLACE_KINDS = ['building', 'water'] as const;
export type PlaceKind = (typeof PLACE_KINDS)[number];

export function isPlaceKind(value: string | null): value is PlaceKind {
  return PLACE_KINDS.includes(value as PlaceKind);
}

// Each R*Tree's id is a features.id; columns match the BBox field order.
export const SCHEMA = `
  CREATE TABLE features (id INTEGER PRIMARY KEY, name TEXT NOT NULL, geom BLOB NOT NULL);
  CREATE VIRTUAL TABLE building_idx USING rtree(id, minx, maxx, miny, maxy);
  CREATE VIRTUAL TABLE water_idx USING rtree(id, minx, maxx, miny, maxy);
  CREATE TABLE meta (key TEXT PRIMARY KEY, value);
`;

export interface PlaceMeta {
  /** ISO timestamp of the build. */
  builtAt: string;
  /** Where the data came from, e.g. the extract URL. */
  source: string;
  buildings: number;
  water: number;
  /** Area the extract covers; absent means everywhere. */
  coverage?: Polygon[];
}

// Float32 [polygonCount, (ringCount, (pointCount, lon, lat, ...)...)...]. Float32 keeps
// positions within about a metre at Nordic latitudes and halves the file.
export function encodeGeometry(polygons: Polygon[]): Uint8Array {
  const values: number[] = [polygons.length];
  for (const polygon of polygons) {
    values.push(polygon.length);
    for (const ring of polygon) {
      values.push(ring.length);
      for (const [lon, lat] of ring) values.push(lon, lat);
    }
  }
  return new Uint8Array(new Float32Array(values).buffer);
}

export function decodeGeometry(blob: Uint8Array): Polygon[] {
  const f = new Float32Array(blob.buffer, blob.byteOffset, blob.byteLength / 4);
  let i = 0;
  const next = () => f[i++] ?? NaN;
  const polygons: Polygon[] = [];
  for (let p = next(); p > 0; p--) {
    const polygon: Polygon = [];
    for (let r = next(); r > 0; r--) {
      const ring: Polygon[number] = [];
      for (let k = next(); k > 0; k--) ring.push([next(), next()]);
      polygon.push(ring);
    }
    polygons.push(polygon);
  }
  return polygons;
}

export function writeMeta(db: Database, meta: PlaceMeta) {
  const insert = db.prepare('INSERT INTO meta (key, value) VALUES (?, ?)');
  insert.run('builtAt', meta.builtAt);
  insert.run('source', meta.source);
  insert.run('buildings', meta.buildings);
  insert.run('water', meta.water);
  if (meta.coverage) insert.run('coverage', encodeGeometry(meta.coverage));
}

/** Throws if the database has no meta table. */
export function readMeta(db: Database): PlaceMeta {
  const rows = db.query<{ key: string; value: unknown }, []>('SELECT key, value FROM meta').all();
  const values = new Map(rows.map(row => [row.key, row.value]));
  const coverage = values.get('coverage');
  return {
    builtAt: String(values.get('builtAt') ?? ''),
    source: String(values.get('source') ?? ''),
    buildings: Number(values.get('buildings') ?? 0),
    water: Number(values.get('water') ?? 0),
    coverage: coverage instanceof Uint8Array ? decodeGeometry(coverage) : undefined,
  };
}
