/**
 * Builds the places database from `osmium export -f geojsonseq` output.
 *
 *   bun scripts/build-places.ts <input.geojsonl> <output.sqlite>
 *       [--poly coverage.poly] [--source <url>] [--min-count N]
 *
 * Writes to <output>.tmp and renames into place only if every kind has at least
 * --min-count places, so a broken extract never replaces a working index.
 */
import { Database } from 'bun:sqlite';
import { mkdir, rename, rm } from 'node:fs/promises';
import { dirname } from 'node:path';
import { parseArgs } from 'node:util';
import { createPlaceWriter, parsePoly, readGeojsonSeq, toPlaceFeature } from '../src/server/places/build';

const { values: opts, positionals } = parseArgs({
  args: process.argv.slice(2),
  options: {
    poly: { type: 'string' },
    source: { type: 'string' },
    'min-count': { type: 'string', default: '0' },
  },
  allowPositionals: true,
});
const [input, output] = positionals;
if (!input || !output) {
  console.error(
    'usage: bun scripts/build-places.ts <input.geojsonl> <output.sqlite> [--poly file] [--source url] [--min-count N]'
  );
  process.exit(1);
}
const minCount = Number(opts['min-count']);

const tmp = `${output}.tmp`;
await mkdir(dirname(output), { recursive: true });
await rm(tmp, { force: true });
const db = new Database(tmp, { create: true });
db.exec('PRAGMA journal_mode = OFF; PRAGMA synchronous = OFF;');
const writer = createPlaceWriter(db);

let featuresRead = 0;
let skipped = 0;
let badLines = 0;
for await (const record of readGeojsonSeq(Bun.file(input).stream())) {
  if (!record.ok) {
    if (badLines++ < 10) console.warn(`line ${record.line}: ${record.error}`);
    continue;
  }
  featuresRead++;
  const place = toPlaceFeature(record.value);
  if (place) writer.insert(place);
  else skipped++;
}

const meta = writer.finish({
  builtAt: new Date().toISOString(),
  source: opts.source ?? input,
  coverage: opts.poly ? parsePoly(await Bun.file(opts.poly).text()) : undefined,
});
db.exec('VACUUM');
db.close();

console.log(
  `${meta.buildings} buildings, ${meta.water} water from ${featuresRead} features ` +
    `(${skipped} skipped, ${badLines} unparseable lines)`
);
if (meta.buildings < minCount || meta.water < minCount) {
  await rm(tmp, { force: true });
  console.error(`Fewer than ${minCount} of a kind, keeping the existing ${output}`);
  process.exit(1);
}
await rename(tmp, output);
console.log(`Wrote ${output}`);
