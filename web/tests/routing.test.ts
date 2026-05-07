import { describe, test, expect, beforeAll, afterAll } from 'bun:test';
import { $ } from 'bun';

let SERVER_URL: string;
let serverProc: ReturnType<typeof Bun.spawn>;

beforeAll(async () => {
  // Start the real server on a random port
  serverProc = Bun.spawn(['bun', 'run', 'src/server/index.ts'], {
    cwd: `${import.meta.dir}/..`,
    env: { ...process.env, PORT: '0' },
    stdout: 'pipe',
  });

  // Read the first line of stdout to get the actual port
  const reader = serverProc.stdout.getReader();
  const decoder = new TextDecoder();
  let output = '';
  while (true) {
    const { value, done } = await reader.read();
    if (done) break;
    output += decoder.decode(value);
    const match = output.match(/localhost:(\d+)/);
    if (match) {
      SERVER_URL = `http://localhost:${match[1]}`;
      reader.releaseLock();
      break;
    }
  }

  // Wait for server to be ready
  let retries = 20;
  while (retries > 0) {
    try {
      await fetch(`${SERVER_URL}/`);
      break;
    } catch {
      await new Promise(resolve => setTimeout(resolve, 100));
      retries--;
    }
  }
  if (retries === 0) throw new Error('Server failed to start');
});

afterAll(() => {
  serverProc?.kill();
});

describe('static routes', () => {
  test('/ serves index.html', async () => {
    const res = await fetch(`${SERVER_URL}/`);
    expect(res.status).toBe(200);
    expect(res.headers.get('content-type')).toContain('text/html');
    const html = await res.text();
    expect(html).toContain('app.js');
  });

  test('/about serves about.html', async () => {
    const res = await fetch(`${SERVER_URL}/about`);
    expect(res.status).toBe(200);
    const html = await res.text();
    expect(html).toContain('Open source');
    expect(html).not.toContain('app.js');
  });

  test('/privacy serves privacy.html', async () => {
    const res = await fetch(`${SERVER_URL}/privacy`);
    expect(res.status).toBe(200);
    const html = await res.text();
    expect(html).not.toContain('app.js');
  });

  test('HTML responses include OG meta tags', async () => {
    const res = await fetch(`${SERVER_URL}/`);
    const html = await res.text();
    expect(html).toContain('og:title');
  });

  test('/nonexistent returns 404', async () => {
    const res = await fetch(`${SERVER_URL}/nonexistent-page`);
    expect(res.status).toBe(404);
  });
});

describe('client ID path routing', () => {
  test('/abc123 serves index.html', async () => {
    const res = await fetch(`${SERVER_URL}/abc123`);
    expect(res.status).toBe(200);
    expect(res.headers.get('content-type')).toContain('text/html');
    const html = await res.text();
    expect(html).toContain('app.js');
    expect(html).toContain('og:title');
  });

  test('/abc123,def456 serves index.html for multiple clients', async () => {
    const res = await fetch(`${SERVER_URL}/abc123,def456`);
    expect(res.status).toBe(200);
    expect(res.headers.get('content-type')).toContain('text/html');
  });

  test('rejects IDs that are too short', async () => {
    const res = await fetch(`${SERVER_URL}/abc`);
    expect(res.status).toBe(404);
  });

  test('rejects IDs that are too long', async () => {
    const res = await fetch(`${SERVER_URL}/abcdefg`);
    expect(res.status).toBe(404);
  });

  test('rejects paths with invalid characters', async () => {
    const res = await fetch(`${SERVER_URL}/abc-12`);
    expect(res.status).toBe(404);
  });

  test('rejects paths with uppercase', async () => {
    const res = await fetch(`${SERVER_URL}/ABC123`);
    expect(res.status).toBe(404);
  });

  test('rejects trailing comma', async () => {
    const res = await fetch(`${SERVER_URL}/abc123,`);
    expect(res.status).toBe(404);
  });

  test('rejects leading comma', async () => {
    const res = await fetch(`${SERVER_URL}/,abc123`);
    expect(res.status).toBe(404);
  });

  test('rejects comma-only paths', async () => {
    const res = await fetch(`${SERVER_URL}/,,,`);
    expect(res.status).toBe(404);
  });
});

describe('legacy ?clients= redirect', () => {
  test('redirects /?clients=abc123 to /abc123', async () => {
    const res = await fetch(`${SERVER_URL}/?clients=abc123`, { redirect: 'manual' });
    expect(res.status).toBe(302);
    expect(res.headers.get('location')).toBe(`${SERVER_URL}/abc123`);
  });

  test('redirects /?clients=abc123,def456 to /abc123,def456', async () => {
    const res = await fetch(`${SERVER_URL}/?clients=abc123,def456`, { redirect: 'manual' });
    expect(res.status).toBe(302);
    expect(res.headers.get('location')).toBe(`${SERVER_URL}/abc123,def456`);
  });

  test('does not redirect invalid client IDs', async () => {
    const res = await fetch(`${SERVER_URL}/?clients=<script>`, { redirect: 'manual' });
    expect(res.status).toBe(200); // serves index.html normally
  });

  test('does not redirect empty clients param', async () => {
    const res = await fetch(`${SERVER_URL}/?clients=`, { redirect: 'manual' });
    expect(res.status).toBe(200);
  });

  test('does not redirect ?clients= on non-root paths', async () => {
    const res = await fetch(`${SERVER_URL}/about?clients=abc123`, { redirect: 'manual' });
    expect(res.status).toBe(200);
    const html = await res.text();
    expect(html).toContain('Open source');
  });
});

describe('named routes take precedence', () => {
  test('/ios redirects to App Store', async () => {
    const res = await fetch(`${SERVER_URL}/ios`, { redirect: 'manual' });
    expect(res.status).toBe(302);
    expect(res.headers.get('location')).toContain('apps.apple.com');
  });

  test('/android redirects to Play Store', async () => {
    const res = await fetch(`${SERVER_URL}/android`, { redirect: 'manual' });
    expect(res.status).toBe(302);
    expect(res.headers.get('location')).toContain('play.google.com');
  });

  test('/api routes are not treated as client IDs', async () => {
    const res = await fetch(`${SERVER_URL}/api/tracks`);
    expect(res.status).not.toBe(404);
  });
});
