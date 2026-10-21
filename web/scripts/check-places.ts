/**
 * Known-answer checks for a built places database, run before it is deployed.
 *
 *   bun scripts/check-places.ts <places.sqlite>
 *
 * The probes mirror app/src/test/.../GeocodingIntegrationTest.kt. Writes a summary to
 * $GITHUB_STEP_SUMMARY when set.
 */
import { appendFile, stat } from 'node:fs/promises';
import type { PlaceKind } from '../src/server/places/db';
import { PlaceIndex, type LookupResult } from '../src/server/places/lookup';

interface Probe {
  label: string;
  kind: PlaceKind;
  lat: number;
  lon: number;
  expected: LookupResult;
}

const PROBES: Probe[] = [
  // A defibrillator node on the museum wall
  { label: 'Munchmuseet', kind: 'building', lat: 59.9056239, lon: 10.7551554, expected: { name: 'Munchmuseet' } },
  // The cape Nestangen; Nominatim knows the cape, only the polygon knows the lake
  { label: 'Maridalsvannet', kind: 'water', lat: 59.9829, lon: 10.78, expected: { name: 'Maridalsvannet' } },
  // Stockholm, outside the Norway extract
  { label: 'Stockholm', kind: 'water', lat: 59.3293, lon: 18.0686, expected: 'outside' },
];

const path = process.argv[2];
if (!path) {
  console.error('usage: bun scripts/check-places.ts <places.sqlite>');
  process.exit(1);
}

const index = PlaceIndex.open(path);
const show = (result: LookupResult) => (typeof result === 'string' ? result : JSON.stringify(result.name));
const rows = PROBES.map(probe => {
  const actual = index.lookup(probe.kind, probe.lat, probe.lon);
  return { probe, actual, ok: show(actual) === show(probe.expected) };
});
const { size } = await stat(path);

const summary = [
  `### Places index`,
  `${index.describe()}, ${(size / 1024 / 1024).toFixed(1)} MB`,
  '',
  '| Probe | Kind | Expected | Actual | |',
  '| --- | --- | --- | --- | --- |',
  ...rows.map(({ probe, actual, ok }) =>
    `| ${probe.label} | ${probe.kind} | ${show(probe.expected)} | ${show(actual)} | ${ok ? 'ok' : 'FAIL'} |`
  ),
].join('\n');
console.log(summary);
if (process.env.GITHUB_STEP_SUMMARY) await appendFile(process.env.GITHUB_STEP_SUMMARY, `${summary}\n`);
index.close();

if (rows.some(row => !row.ok)) {
  console.error('Known-answer checks failed, not deploying');
  process.exit(1);
}
