import { describe, test, expect, beforeAll, afterAll, afterEach, spyOn } from 'bun:test';
import { Database } from 'bun:sqlite';
import { mkdtemp, rm, utimes, writeFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { createApiHandler } from '../src/server/api';
import { TrackStore } from '../src/server/store';
import {
  parsePoly,
  readGeojsonSeq,
  toPlaceFeature,
  writePlaces,
  type PlaceFeature,
} from '../src/server/places/build';
import { decodeGeometry, encodeGeometry, type PlaceMeta } from '../src/server/places/db';
import { containsPoint, distanceMeters, type Polygon, type Ring } from '../src/server/places/geometry';
import { PlaceIndex, ReloadingPlaceIndex, type PlaceLookup } from '../src/server/places/lookup';
import { readJson } from './helpers';

const WEB_DIR = `${import.meta.dir}/..`;

/** Closed axis-aligned ring in [lon, lat]. */
function box(minLon: number, minLat: number, maxLon: number, maxLat: number): Ring {
  return [[minLon, minLat], [maxLon, minLat], [maxLon, maxLat], [minLon, maxLat], [minLon, minLat]];
}

function building(name: string, ring: Ring): PlaceFeature {
  return { name, kinds: ['building'], polygons: [[ring]] };
}

function water(name: string, ...rings: Ring[]): PlaceFeature {
  return { name, kinds: ['water'], polygons: [rings] };
}

const openIndexes: PlaceIndex[] = [];
function indexOf(features: PlaceFeature[], meta: Partial<PlaceMeta> = {}): PlaceIndex {
  const db = new Database(':memory:');
  writePlaces(db, features, meta);
  const index = new PlaceIndex(db);
  openIndexes.push(index);
  return index;
}
afterAll(() => openIndexes.forEach(index => index.close()));

// At 59.9N one metre is about 0.000009 degrees of latitude and 0.0000179 of longitude.
const M_LAT = 1 / 110_540;
const M_LON = 1 / (111_320 * Math.cos((59.9 * Math.PI) / 180));

// A 0.0002 x 0.0002 degree building (about 11 x 22 m) north-east of 59.9N 10.7E.
const TOWN_HALL = box(10.7001, 59.9001, 10.7003, 59.9003);

describe('geometry', () => {
  test('holes count as outside', () => {
    const lake: Polygon[] = [[box(10, 59, 11, 60), box(10.4, 59.4, 10.6, 59.6)]];
    expect(containsPoint(lake, 59.2, 10.2)).toBe(true);
    expect(containsPoint(lake, 59.5, 10.5)).toBe(false);
  });

  test('distance is 0 inside and metric outside', () => {
    const polygons: Polygon[] = [[TOWN_HALL]];
    expect(distanceMeters(polygons, 59.9002, 10.7002)).toBe(0);
    const d = distanceMeters(polygons, 59.9001 - 20 * M_LAT, 10.7002);
    expect(d).toBeCloseTo(20, 1);
  });

  test('encode/decode keeps multipolygon structure, holes and precision', () => {
    const polygons: Polygon[] = [
      [box(10.123456, 59.654321, 10.2, 59.7), box(10.15, 59.66, 10.16, 59.67)],
      [box(11, 60, 11.1, 60.1)],
    ];
    const decoded = decodeGeometry(encodeGeometry(polygons));
    expect(decoded.map(p => p.map(r => r.length))).toEqual([[5, 5], [5]]);
    expect(decoded[0]?.[0]?.[0]?.[0]).toBeCloseTo(10.123456, 5);
    expect(decoded[0]?.[0]?.[0]?.[1]).toBeCloseTo(59.654321, 5);
    expect(containsPoint(decoded, 59.665, 10.155)).toBe(false); // in the hole
    expect(containsPoint(decoded, 59.69, 10.19)).toBe(true);
    expect(containsPoint(decoded, 60.05, 11.05)).toBe(true); // second polygon
  });
});

describe('PlaceIndex.building', () => {
  const index = () => indexOf([building('Rådhuset', TOWN_HALL)]);

  test('inside the building', () => {
    expect(index().building(59.9002, 10.7002)).toBe('Rådhuset');
  });

  test.each([
    ['22 m south', 59.9001 - 22 * M_LAT, 10.7002, 'Rådhuset'],
    ['110 m south', 59.9001 - 110 * M_LAT, 10.7002, null],
    ['40 m east', 59.9002, 10.7003 + 40 * M_LON, 'Rådhuset'],
    ['60 m east', 59.9002, 10.7003 + 60 * M_LON, null],
    ['40 m west', 59.9002, 10.7001 - 40 * M_LON, 'Rådhuset'],
    // 30 m on each axis is 42 m diagonally; 40 m on each is 57 m, inside the bbox prefilter but too far.
    ['42 m diagonally', 59.9003 + 30 * M_LAT, 10.7003 + 30 * M_LON, 'Rådhuset'],
    ['57 m diagonally', 59.9003 + 40 * M_LAT, 10.7003 + 40 * M_LON, null],
  ])('%s', (_label, lat, lon, expected) => {
    expect(index().building(lat, lon)).toBe(expected);
  });

  test('nearest wins regardless of insert order', () => {
    const south = building('Sør', box(10.7001, 59.8996, 10.7003, 59.8998)); // ~22 m from the point
    const north = building('Nord', box(10.7001, 59.9001, 10.7003, 59.9003)); // ~11 m
    for (const features of [[south, north], [north, south]]) {
      expect(indexOf(features).building(59.9, 10.7002)).toBe('Nord');
    }
  });

  test('inside nested buildings, the smallest wins regardless of insert order', () => {
    const complex = building('Kvartalet', box(10.699, 59.899, 10.702, 59.902));
    const shop = building('Butikken', TOWN_HALL);
    for (const features of [[complex, shop], [shop, complex]]) {
      expect(indexOf(features).building(59.9002, 10.7002)).toBe('Butikken');
    }
  });
});

describe('PlaceIndex.water', () => {
  const lake = water('Storvannet', box(10.6, 59.8, 10.8, 60.0));

  test('enclosing lake', () => {
    expect(indexOf([lake]).water(59.85, 10.65)).toBe('Storvannet');
  });

  test('island hole is not in the lake', () => {
    const withIsland = water('Storvannet', box(10.6, 59.8, 10.8, 60.0), box(10.69, 59.89, 10.71, 59.91));
    expect(indexOf([withIsland]).water(59.9, 10.7)).toBeNull();
  });

  test('when both contain the point, the smaller polygon wins regardless of insert order', () => {
    const pond = water('Tjernet', box(10.69, 59.89, 10.71, 59.91));
    for (const features of [[lake, pond], [pond, lake]]) {
      expect(indexOf(features).water(59.9, 10.7)).toBe('Tjernet');
    }
  });

  test('a smaller bbox that does not contain the point falls through to the containing one', () => {
    // Triangle whose bbox holds the point but whose area does not
    const bay = water('Bukta', [[10.69, 59.89], [10.71, 59.89], [10.69, 59.91], [10.69, 59.89]]);
    expect(indexOf([lake, bay]).water(59.905, 10.705)).toBe('Storvannet');
  });

  test('no water outside everything', () => {
    expect(indexOf([lake]).water(61, 11)).toBeNull();
  });
});

describe('PlaceIndex.lookup and coverage', () => {
  const coverage: Polygon[] = [[box(10, 59, 11, 61)]];
  const features = [building('Rådhuset', TOWN_HALL), water('Storvannet', box(10.6, 59.8, 10.8, 60.0))];

  test('answers inside coverage, including a null name', () => {
    const index = indexOf(features, { coverage });
    expect(index.lookup('building', 59.9002, 10.7002)).toEqual({ name: 'Rådhuset' });
    expect(index.lookup('water', 59.85, 10.65)).toEqual({ name: 'Storvannet' });
    expect(index.lookup('water', 60.5, 10.5)).toEqual({ name: null });
  });

  test('reports outside coverage instead of a null name', () => {
    expect(indexOf(features, { coverage }).lookup('water', 59.33, 18.07)).toBe('outside');
  });

  test('without coverage, everywhere is covered', () => {
    expect(indexOf(features).lookup('water', 59.33, 18.07)).toEqual({ name: null });
  });

  test('meta records counts and build info', () => {
    const index = indexOf(features, { source: 'unit', coverage });
    expect(index.meta).toMatchObject({ source: 'unit', buildings: 1, water: 1 });
    expect(Number.isNaN(Date.parse(index.meta.builtAt))).toBe(false);
    expect(index.meta.coverage?.length).toBe(1);
  });
});

describe('toPlaceFeature', () => {
  const polygon = { type: 'Polygon', coordinates: [box(0, 0, 1, 1)] };

  test('maps named buildings and water', () => {
    expect(toPlaceFeature({ properties: { name: 'A', building: 'yes' }, geometry: polygon })?.kinds).toEqual(['building']);
    expect(toPlaceFeature({ properties: { name: 'B', natural: 'water' }, geometry: polygon })?.kinds).toEqual(['water']);
    expect(toPlaceFeature({ properties: { name: ' C ', building: 'yes', natural: 'water' }, geometry: polygon }))
      .toMatchObject({ name: 'C', kinds: ['building', 'water'] });
  });

  test('keeps multipolygons', () => {
    const mp = { type: 'MultiPolygon', coordinates: [[box(0, 0, 1, 1)], [box(2, 2, 3, 3)]] };
    expect(toPlaceFeature({ properties: { name: 'C', natural: 'water' }, geometry: mp })?.polygons.length).toBe(2);
  });

  test.each([
    ['unnamed', { building: 'yes' }, polygon],
    ['building=no', { name: 'D', building: 'no' }, polygon],
    ['other tags', { name: 'E', highway: 'road' }, polygon],
    ['line', { name: 'F', building: 'yes' }, { type: 'LineString', coordinates: [[0, 0], [1, 1]] }],
    ['no coordinates', { name: 'G', building: 'yes' }, { type: 'Polygon' }],
    ['empty polygon', { name: 'H', building: 'yes' }, { type: 'Polygon', coordinates: [] }],
    ['empty multipolygon', { name: 'I', natural: 'water' }, { type: 'MultiPolygon', coordinates: [] }],
    ['too short ring', { name: 'J', building: 'yes' }, { type: 'Polygon', coordinates: [[[0, 0], [1, 1], [0, 0]]] }],
    ['non-numeric', { name: 'K', building: 'yes' }, { type: 'Polygon', coordinates: [[[0, 0], ['x', 1], [1, 1], [0, 0]]] }],
    ['no geometry', { name: 'L', building: 'yes' }, null],
  ])('drops %s', (_label, properties, geometry) => {
    expect(toPlaceFeature({ properties, geometry })).toBeNull();
  });

  test('drops non-objects', () => {
    for (const value of [null, 42, 'feature', []]) expect(toPlaceFeature(value)).toBeNull();
  });
});

describe('readGeojsonSeq', () => {
  const text = '\x1e{"a":1}\r\n\n{"b":"Rådhuset"}\nnot json\n\x1e{"c":3}';

  function streamOf(bytes: Uint8Array, chunkSize: number): ReadableStream<Uint8Array> {
    return new ReadableStream({
      start(controller) {
        for (let i = 0; i < bytes.length; i += chunkSize) controller.enqueue(bytes.slice(i, i + chunkSize));
        controller.close();
      },
    });
  }

  // Chunk size 1 splits every record and the multibyte "å"; the others split at odd places.
  test.each([1, 3, 7, 1000])('parses records split into %i-byte chunks', async chunkSize => {
    const records = [];
    for await (const record of readGeojsonSeq(streamOf(new TextEncoder().encode(text), chunkSize))) {
      records.push(record);
    }
    expect(records).toEqual([
      { ok: true, value: { a: 1 } },
      { ok: true, value: { b: 'Rådhuset' } },
      { ok: false, line: 4, error: expect.any(String) },
      { ok: true, value: { c: 3 } },
    ]);
  });
});

describe('parsePoly', () => {
  test('reads outer sections and attaches holes', () => {
    const poly = [
      'norway', 'main', '  1.0E+01  5.9E+01', '11 59', '11 60', '10 60', 'END',
      '!hole', '10.4 59.4', '10.6 59.4', '10.6 59.6', 'END',
      'second', '20 70', '21 70', '21 71', 'END', 'END', '',
    ].join('\n');
    const polygons = parsePoly(poly);
    expect(polygons.map(p => p.length)).toEqual([2, 1]);
    expect(polygons[0]?.[0]?.[0]).toEqual([10, 59]);
    expect(containsPoint(polygons, 59.5, 10.5)).toBe(false);
    expect(containsPoint(polygons, 59.2, 10.2)).toBe(true);
    expect(containsPoint(polygons, 70.2, 20.5)).toBe(true);
  });

  test('accepts CRLF line endings', () => {
    expect(parsePoly('x\r\n1\r\n0 0\r\n1 0\r\n1 1\r\nEND\r\nEND\r\n').length).toBe(1);
  });

  test.each([
    ['no sections', 'x\nEND\n'],
    ['hole first', 'x\n!1\n0 0\n1 0\n1 1\nEND\nEND\n'],
    ['too few points', 'x\n1\n0 0\n1 1\nEND\nEND\n'],
  ])('rejects %s', (_label, text) => {
    expect(() => parsePoly(text)).toThrow();
  });
});

describe('GET /api/places', () => {
  const store = new TrackStore(':memory:');
  afterAll(() => store.close());
  const baseDeps = {
    store,
    verifySignature: async () => true,
    broadcast: () => {},
    getAdminKey: () => undefined,
  };
  const stub = (result: ReturnType<PlaceLookup['lookup']>): PlaceLookup => ({ lookup: () => result });
  const get = (places: PlaceLookup | undefined, query: string) =>
    createApiHandler({ ...baseDeps, places })(new Request(`http://localhost/api/places?${query}`));

  test('returns names from the real index', async () => {
    const index = indexOf([building('Rådhuset', TOWN_HALL), water('Storvannet', box(10.6, 59.8, 10.8, 60.0))]);
    const b = await get(index, 'lat=59.9002&lon=10.7002&kind=building');
    expect(b.status).toBe(200);
    expect(b.headers.get('Access-Control-Allow-Origin')).toBeNull();
    expect(await readJson(b)).toEqual({ name: 'Rådhuset' });
    expect(await readJson(await get(index, 'lat=59.85&lon=10.65&kind=water'))).toEqual({ name: 'Storvannet' });
  });

  test('returns a null name when nothing matches', async () => {
    const res = await get(stub({ name: null }), 'lat=61&lon=11&kind=water');
    expect(res.status).toBe(200);
    expect(await readJson(res)).toEqual({ name: null });
  });

  test.each([
    'lat=59.9&lon=10.7',
    'lat=abc&lon=10.7&kind=water',
    'lon=10.7&kind=water',
    'lat=&lon=10.7&kind=water',
    'lat=59.9&lon=%20&kind=water',
    'lat=90.0001&lon=10&kind=water',
    'lat=-90.0001&lon=10&kind=water',
    'lat=59&lon=181&kind=water',
    'lat=59&lon=10&kind=road',
  ])('rejects %s with 400', async query => {
    const res = await get(stub({ name: 'x' }), query);
    expect(res.status).toBe(400);
    expect(res.headers.get('Access-Control-Allow-Origin')).toBeNull();
  });

  test.each(['lat=90&lon=180&kind=water', 'lat=-90&lon=-180&kind=building'])('accepts boundary %s', async query => {
    expect((await get(stub({ name: null }), query)).status).toBe(200);
  });

  test.each([
    ['no index configured', undefined],
    ['index unavailable', stub('unavailable')],
    ['point outside coverage', stub('outside')],
  ])('answers 503 when %s, so the app falls back', async (_label, places) => {
    const res = await get(places, 'lat=59.9&lon=10.7&kind=water');
    expect(res.status).toBe(503);
    expect(res.headers.get('Access-Control-Allow-Origin')).toBeNull();
  });
});

describe('ReloadingPlaceIndex', () => {
  let dir: string;
  let tick = 0;
  const reloaders: ReloadingPlaceIndex[] = [];
  const lake = water('Storvannet', box(10.6, 59.8, 10.8, 60.0));

  beforeAll(async () => {
    dir = await mkdtemp(`${tmpdir()}/places-test-`);
  });
  afterEach(() => reloaders.splice(0).forEach(r => r.close()));
  afterAll(async () => {
    await rm(dir, { recursive: true, force: true });
  });

  function reloader(path: string) {
    const r = new ReloadingPlaceIndex(path);
    reloaders.push(r);
    return r;
  }

  // Distinct mtimes per write, since reloads key on the file's modification time.
  async function touch(path: string) {
    const t = new Date(Date.UTC(2026, 0, 1, 0, 0, ++tick));
    await utimes(path, t, t);
  }

  async function writeIndex(path: string, features: PlaceFeature[]) {
    await rm(path, { force: true });
    const db = new Database(path, { create: true });
    writePlaces(db, features);
    db.close();
    await touch(path);
  }

  async function writeBroken(path: string, kind: 'text' | 'no-tables') {
    await rm(path, { force: true });
    if (kind === 'text') await writeFile(path, 'not a database');
    else {
      const db = new Database(path, { create: true });
      db.exec('CREATE TABLE other (x)');
      db.close();
    }
    await touch(path);
  }

  const silenceLogs = () => {
    const error = spyOn(console, 'error').mockImplementation(() => {});
    const log = spyOn(console, 'log').mockImplementation(() => {});
    return { error, restore: () => { error.mockRestore(); log.mockRestore(); } };
  };

  test('unavailable until the file exists, then loads it', async () => {
    const logs = silenceLogs();
    const path = `${dir}/a.sqlite`;
    const places = reloader(path);
    expect(places.lookup('water', 59.85, 10.65)).toBe('unavailable');
    expect(places.describe()).toContain('missing');
    await writeIndex(path, [lake]);
    expect(places.lookup('water', 59.85, 10.65)).toEqual({ name: 'Storvannet' });
    logs.restore();
  });

  test('picks up a replaced file immediately', async () => {
    const logs = silenceLogs();
    const path = `${dir}/b.sqlite`;
    const places = reloader(path);
    await writeIndex(path, [lake]);
    expect(places.lookup('water', 59.85, 10.65)).toEqual({ name: 'Storvannet' });
    await writeIndex(path, [{ ...lake, name: 'Nyvannet' }]);
    expect(places.lookup('water', 59.85, 10.65)).toEqual({ name: 'Nyvannet' });
    logs.restore();
  });

  test.each(['text', 'no-tables'] as const)('keeps the working index when a %s replacement fails', async kind => {
    const logs = silenceLogs();
    const path = `${dir}/c-${kind}.sqlite`;
    const places = reloader(path);
    await writeIndex(path, [lake]);
    expect(places.lookup('water', 59.85, 10.65)).toEqual({ name: 'Storvannet' });
    await writeBroken(path, kind);
    expect(places.lookup('water', 59.85, 10.65)).toEqual({ name: 'Storvannet' });
    expect(logs.error).toHaveBeenCalledTimes(1);
    // Not retried until the file changes again
    places.lookup('water', 59.85, 10.65);
    expect(logs.error).toHaveBeenCalledTimes(1);
    logs.restore();
  });

  test('stays unavailable when the first file is broken', async () => {
    const logs = silenceLogs();
    const path = `${dir}/d.sqlite`;
    await writeBroken(path, 'text');
    expect(reloader(path).lookup('water', 59.85, 10.65)).toBe('unavailable');
    expect(logs.error).toHaveBeenCalled();
    logs.restore();
  });
});

describe('build and check scripts', () => {
  let dir: string;
  beforeAll(async () => {
    dir = await mkdtemp(`${tmpdir()}/places-scripts-`);
  });
  afterAll(async () => {
    await rm(dir, { recursive: true, force: true });
  });

  async function run(...args: string[]) {
    const proc = Bun.spawn(['bun', ...args], { cwd: WEB_DIR, stdout: 'pipe', stderr: 'pipe' });
    const [stdout, stderr] = await Promise.all([new Response(proc.stdout).text(), new Response(proc.stderr).text()]);
    return { code: await proc.exited, output: stdout + stderr };
  }

  const build = (out: string, ...extra: string[]) =>
    run('scripts/build-places.ts', 'tests/fixtures/places.geojsonl', out, '--poly', 'tests/fixtures/oslo.poly', ...extra);

  test('builds the fixture and passes the known-answer checks', async () => {
    const out = `${dir}/ok/places.sqlite`;
    const built = await build(out, '--min-count', '2');
    expect(built.code).toBe(0);
    expect(built.output).toContain('2 buildings, 2 water');
    const checked = await run('scripts/check-places.ts', out);
    expect(checked.output).toContain('| ok |');
    expect(checked.code).toBe(0);
  });

  test('refuses to write an index below --min-count and leaves no files', async () => {
    const out = `${dir}/few/places.sqlite`;
    const built = await build(out, '--min-count', '3');
    expect(built.code).toBe(1);
    expect(await Bun.file(out).exists()).toBe(false);
    expect(await Bun.file(`${out}.tmp`).exists()).toBe(false);
  });

  test('check fails when a known answer is missing', async () => {
    const out = `${dir}/empty.sqlite`;
    const db = new Database(out, { create: true });
    writePlaces(db, [water('Storvannet', box(10.6, 59.8, 10.8, 60.0))]);
    db.close();
    const checked = await run('scripts/check-places.ts', out);
    expect(checked.code).toBe(1);
    expect(checked.output).toContain('FAIL');
  });
});
