import type { Database } from 'bun:sqlite';
import { SCHEMA, encodeGeometry, writeMeta, type PlaceKind, type PlaceMeta } from './db';
import { bbox, toPolygons, type Polygon, type Ring } from './geometry';

/** Build-time code: turns `osmium export` output into a places database. */

export interface PlaceFeature {
  name: string;
  kinds: PlaceKind[];
  polygons: Polygon[];
}

interface GeoJsonFeature {
  properties?: Record<string, unknown> | null;
  geometry?: { type?: unknown; coordinates?: unknown } | null;
}

/**
 * Maps one GeoJSON feature (tags as properties) to a place, or null when it is unnamed,
 * not a valid polygon, or neither a building nor water.
 */
export function toPlaceFeature(feature: unknown): PlaceFeature | null {
  if (typeof feature !== 'object' || feature === null) return null;
  const { properties, geometry } = feature as GeoJsonFeature;
  const tags = properties ?? {};
  const name = typeof tags.name === 'string' ? tags.name.trim() : '';
  if (!name) return null;
  const kinds: PlaceKind[] = [];
  if (tags.building && tags.building !== 'no') kinds.push('building');
  if (tags.natural === 'water') kinds.push('water');
  if (kinds.length === 0) return null;
  const polygons = toPolygons(geometry?.type, geometry?.coordinates);
  return polygons ? { name, kinds, polygons } : null;
}

export type SeqRecord = { ok: true; value: unknown } | { ok: false; line: number; error: string };

/**
 * Parses GeoJSON text sequences: one feature per line, optionally prefixed by an RS (0x1E)
 * record separator. Bad lines are reported, not thrown, so one broken feature can't sink a build.
 */
export async function* readGeojsonSeq(stream: ReadableStream<Uint8Array>): AsyncGenerator<SeqRecord> {
  const decoder = new TextDecoder();
  let pending = '';
  let lineNumber = 0;

  function* parse(raw: string): Generator<SeqRecord> {
    lineNumber++;
    const line = raw.replace(/^\x1e/, '').trim();
    if (!line) return;
    try {
      yield { ok: true, value: JSON.parse(line) };
    } catch (e) {
      yield { ok: false, line: lineNumber, error: e instanceof Error ? e.message : String(e) };
    }
  }

  for await (const chunk of stream) {
    pending += decoder.decode(chunk, { stream: true });
    const lines = pending.split('\n');
    pending = lines.pop() ?? '';
    for (const line of lines) yield* parse(line);
  }
  yield* parse(pending + decoder.decode());
}

/**
 * Parses an Osmosis .poly file (Geofabrik publishes one per extract). Sections whose name
 * starts with "!" are holes and are attached to the preceding outer section.
 */
export function parsePoly(text: string): Polygon[] {
  const lines = text.split(/\r?\n/).map(line => line.trim());
  const polygons: Polygon[] = [];
  let i = 1; // first line is the file name
  while (i < lines.length) {
    const header = lines[i++];
    if (header === undefined || header === 'END') break;
    if (header === '') continue;
    const ring: Ring = [];
    while (i < lines.length && lines[i] !== 'END') {
      const [lon, lat] = (lines[i++] ?? '').split(/\s+/).map(Number);
      if (lon !== undefined && lat !== undefined && Number.isFinite(lon) && Number.isFinite(lat)) {
        ring.push([lon, lat]);
      }
    }
    i++; // section END
    if (ring.length < 3) throw new Error(`poly section "${header}" has too few points`);
    if (header.startsWith('!')) {
      const last = polygons[polygons.length - 1];
      if (!last) throw new Error(`poly hole "${header}" has no outer section`);
      last.push(ring);
    } else {
      polygons.push([ring]);
    }
  }
  if (polygons.length === 0) throw new Error('poly file has no sections');
  return polygons;
}

/** Writes a fresh database in one transaction. Used by the build script and tests. */
export function createPlaceWriter(db: Database) {
  db.exec(SCHEMA);
  db.exec('BEGIN');
  const insertFeature = db.prepare('INSERT INTO features (name, geom) VALUES (?, ?)');
  const insertIdx = {
    building: db.prepare('INSERT INTO building_idx VALUES (?, ?, ?, ?, ?)'),
    water: db.prepare('INSERT INTO water_idx VALUES (?, ?, ?, ?, ?)'),
  };
  const counts = { buildings: 0, water: 0 };
  return {
    counts,
    insert(feature: PlaceFeature) {
      const { lastInsertRowid } = insertFeature.run(feature.name, encodeGeometry(feature.polygons));
      const { minX, maxX, minY, maxY } = bbox(feature.polygons);
      for (const kind of feature.kinds) {
        insertIdx[kind].run(lastInsertRowid, minX, maxX, minY, maxY);
        if (kind === 'building') counts.buildings++;
        else counts.water++;
      }
    },
    finish(meta: Omit<PlaceMeta, 'buildings' | 'water'>): PlaceMeta {
      const full = { ...meta, ...counts };
      writeMeta(db, full);
      db.exec('COMMIT');
      return full;
    },
  };
}

export function writePlaces(
  db: Database,
  features: Iterable<PlaceFeature>,
  meta: Partial<Omit<PlaceMeta, 'buildings' | 'water'>> = {}
): PlaceMeta {
  const writer = createPlaceWriter(db);
  for (const feature of features) writer.insert(feature);
  return writer.finish({ builtAt: new Date().toISOString(), source: 'test', ...meta });
}
