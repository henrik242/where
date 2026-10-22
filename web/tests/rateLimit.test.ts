import { describe, test, expect, afterAll, spyOn } from 'bun:test';
import { createApiHandler } from '../src/server/api';
import { RateLimiter, clientAddress } from '../src/server/rateLimit';
import { TrackStore } from '../src/server/store';
import type { PlaceLookup } from '../src/server/places/lookup';

describe('RateLimiter', () => {
  test('allows up to the limit, then reports seconds until the window resets', () => {
    let now = 1_000_000;
    const limiter = new RateLimiter(3, 60_000, () => now);
    expect([limiter.hit('a'), limiter.hit('a'), limiter.hit('a')]).toEqual([null, null, null]);
    expect(limiter.hit('a')).toBe(60);
    now += 45_500;
    expect(limiter.hit('a')).toBe(15);
  });

  test('counts each key separately', () => {
    const limiter = new RateLimiter(1, 60_000, () => 0);
    expect(limiter.hit('a')).toBeNull();
    expect(limiter.hit('b')).toBeNull();
    expect(limiter.hit('a')).not.toBeNull();
  });

  test('a new window starts after the old one ends', () => {
    let now = 0;
    const limiter = new RateLimiter(1, 60_000, () => now);
    limiter.hit('a');
    expect(limiter.hit('a')).not.toBeNull();
    now = 60_000;
    expect(limiter.hit('a')).toBeNull();
  });

  test('expired windows are swept so memory stays bounded', () => {
    let now = 0;
    const limiter = new RateLimiter(10, 60_000, () => now);
    for (let i = 0; i < 1000; i++) limiter.hit(`ip-${i}`);
    expect(limiter.size).toBe(1000);
    now = 60_000;
    limiter.hit('fresh');
    expect(limiter.size).toBe(1);
  });
});

describe('clientAddress', () => {
  const req = (forwarded?: string) =>
    new Request('http://localhost/api/places', forwarded ? { headers: { 'X-Forwarded-For': forwarded } } : {});

  test.each(['127.0.0.1', '::1', '::ffff:127.0.0.1'])('trusts X-Forwarded-For from loopback peer %s', peer => {
    expect(clientAddress(req('203.0.113.7'), peer)).toBe('203.0.113.7');
  });

  test('uses the last entry, the one the proxy appended', () => {
    expect(clientAddress(req('1.2.3.4, 203.0.113.7'), '127.0.0.1')).toBe('203.0.113.7');
  });

  test('ignores X-Forwarded-For from a non-loopback peer', () => {
    expect(clientAddress(req('1.2.3.4'), '198.51.100.9')).toBe('198.51.100.9');
  });

  test('falls back to the peer, then to a shared key', () => {
    expect(clientAddress(req(), '127.0.0.1')).toBe('127.0.0.1');
    expect(clientAddress(req(), undefined)).toBe('unknown');
  });

  test('ignores X-Forwarded-For when the peer is unknown', () => {
    expect(clientAddress(req('203.0.113.7'), undefined)).toBe('unknown');
  });
});

describe('/api/places limits', () => {
  const store = new TrackStore(':memory:');
  afterAll(() => store.close());
  const places: PlaceLookup = { lookup: () => ({ name: 'Maridalsvannet' }) };
  const handler = (limit = 2, overrides: Partial<Parameters<typeof createApiHandler>[0]> = {}) =>
    createApiHandler({
      store,
      verifySignature: async () => true,
      broadcast: () => {},
      getAdminKey: () => undefined,
      places,
      placesRateLimit: new RateLimiter(limit, 60_000),
      ...overrides,
    });
  const get = (h: ReturnType<typeof handler>, forwarded: string, query = 'lat=59.98&lon=10.78&kind=water') =>
    h(new Request(`http://localhost/api/places?${query}`, { headers: { 'X-Forwarded-For': forwarded } }), '127.0.0.1');

  test('answers 429 with Retry-After once a client is over the limit', async () => {
    const h = handler();
    expect((await get(h, '203.0.113.7')).status).toBe(200);
    expect((await get(h, '203.0.113.7')).status).toBe(200);
    const limited = await get(h, '203.0.113.7');
    expect(limited.status).toBe(429);
    expect(Number(limited.headers.get('Retry-After'))).toBeGreaterThan(0);
    expect(limited.headers.get('Access-Control-Allow-Origin')).toBeNull();
  });

  test('other clients behind the same proxy are not affected', async () => {
    const h = handler(1);
    await get(h, '203.0.113.7');
    expect((await get(h, '203.0.113.7')).status).toBe(429);
    expect((await get(h, '198.51.100.9')).status).toBe(200);
  });

  test('bad requests count toward the limit', async () => {
    const h = handler(1);
    expect((await get(h, '203.0.113.7', 'lat=x')).status).toBe(400);
    expect((await get(h, '203.0.113.7')).status).toBe(429);
  });

  test('preflight for /api/places gets no CORS, other routes keep it', async () => {
    const h = handler();
    const places = await h(new Request('http://localhost/api/places', { method: 'OPTIONS' }));
    expect(places.status).toBe(204);
    expect(places.headers.get('Access-Control-Allow-Origin')).toBeNull();
    const tracks = await h(new Request('http://localhost/api/tracks', { method: 'OPTIONS' }));
    expect(tracks.headers.get('Access-Control-Allow-Origin')).toBe('*');
  });

  test('rotating X-Forwarded-For without a known peer shares one bucket', async () => {
    const h = handler(1);
    const req = (ip: string) =>
      new Request('http://localhost/api/places?lat=59.98&lon=10.78&kind=water', { headers: { 'X-Forwarded-For': ip } });
    expect((await h(req('203.0.113.1'))).status).toBe(200);
    expect((await h(req('203.0.113.2'))).status).toBe(429);
  });

  test.each([
    ['signature failure', { verifySignature: async () => false }, 'POST', 401],
    ['unsupported method', {}, 'DELETE', 404],
    ['lookup exception', { places: { lookup: () => { throw new Error('boom'); } } }, 'GET', 500],
  ] as const)('no CORS on /api/places %s', async (_label, overrides, method, status) => {
    const spy = spyOn(console, 'error').mockImplementation(() => {});
    const warn = spyOn(console, 'warn').mockImplementation(() => {});
    const res = await handler(10, overrides)(
      new Request('http://localhost/api/places?lat=59.98&lon=10.78&kind=water', {
        method,
        ...(method === 'POST' ? { body: '{}' } : {}),
      }),
      '127.0.0.1'
    );
    spy.mockRestore();
    warn.mockRestore();
    expect(res.status).toBe(status);
    expect(res.headers.get('Access-Control-Allow-Origin')).toBeNull();
    expect(res.headers.get('Access-Control-Allow-Methods')).toBeNull();
  });

  test('tracks API responses keep CORS', async () => {
    const res = await handler()(new Request('http://localhost/api/tracks'));
    expect(res.headers.get('Access-Control-Allow-Origin')).toBe('*');
  });
});
