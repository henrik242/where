import { describe, test, expect } from 'bun:test';
import { createApiHandler } from '../src/server/api';
import { TrackStore } from '../src/server/store';

function handler() {
  return createApiHandler({
    store: new TrackStore(':memory:'),
    verifySignature: async () => true,
    broadcast: () => {},
    getAdminKey: () => undefined,
  });
}

describe('Strava OAuth redirect bounce', () => {
  test('forwards the query string to the app custom scheme', async () => {
    const res = await handler()(new Request('http://x/api/strava/redirect?code=ABC&state=xyz'));
    expect(res.status).toBe(302);
    expect(res.headers.get('Location')).toBe('where://strava/connected?code=ABC&state=xyz');
  });

  test('forwards an error param too', async () => {
    const res = await handler()(new Request('http://x/api/strava/redirect?error=access_denied&state=xyz'));
    expect(res.status).toBe(302);
    expect(res.headers.get('Location')).toBe('where://strava/connected?error=access_denied&state=xyz');
  });

  test('works with no query params', async () => {
    const res = await handler()(new Request('http://x/api/strava/redirect'));
    expect(res.status).toBe(302);
    expect(res.headers.get('Location')).toBe('where://strava/connected');
  });
});
