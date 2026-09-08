import { describe, expect, test } from 'bun:test';

const CLIENT = `${import.meta.dir}/../src/client`;
const html = await Bun.file(`${CLIENT}/index.html`).text();
const src = await Bun.file(`${CLIENT}/app.ts`).text();

describe('client html wiring', () => {
  // app.js is an ES module, so inline on*= attributes only resolve names put on window.
  test('every inline handler is assigned to window', () => {
    const used = new Set(
      [...html.matchAll(/\son[a-z]+="([A-Za-z_$][\w$]*)\(/g), ...src.matchAll(/\son[a-z]+="([A-Za-z_$][\w$]*)\(/g)]
        .map(m => m[1])
    );
    const assigned = new Set([...src.matchAll(/\(window as any\)\.(\w+)\s*=/g)].map(m => m[1]));

    expect(used.size).toBeGreaterThanOrEqual(7); // a rotted regex must not pass vacuously
    expect([...used].filter(n => !assigned.has(n))).toEqual([]);
    expect([...assigned].filter(n => !used.has(n))).toEqual([]);
  });

  test('the import map resolves the bare maplibre-gl specifier', () => {
    expect(html).toContain('"maplibre-gl": "/vendor/maplibre-gl.mjs"');
  });
});
