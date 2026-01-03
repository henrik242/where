import { describe, test, expect, beforeAll, afterAll } from 'bun:test';
import { createTestServer } from '../test-server';

let SERVER_URL: string;
let server: any;

beforeAll(async () => {
  // Start test server on random port
  server = createTestServer(0);
  const port = server.port;
  SERVER_URL = `http://localhost:${port}`;

  // Wait for server to be ready
  await new Promise(resolve => setTimeout(resolve, 100));

  // Verify server is responding
  let retries = 10;
  while (retries > 0) {
    try {
      await fetch(`${SERVER_URL}/api/tracks`);
      break;
    } catch {
      await new Promise(resolve => setTimeout(resolve, 100));
      retries--;
    }
  }

  if (retries === 0) {
    throw new Error('Test server failed to start');
  }
});

afterAll(() => {
  if (server) {
    server.stop();
  }
});

describe('API Integration Tests', () => {

  describe('POST /api/tracks', () => {
    test('should create a new track', async () => {
      const response = await fetch(`${SERVER_URL}/api/tracks`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          userId: 'test123',
          name: 'Integration Test Track'
        })
      });

      expect(response.status).toBe(201);
      const data = await response.json();
      expect(data.id).toBeDefined();
      expect(data.userId).toBe('test123');
      expect(data.name).toBe('Integration Test Track');
      expect(data.isActive).toBe(true);
    });

    test('should return 400 for missing userId', async () => {
      const response = await fetch(`${SERVER_URL}/api/tracks`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          name: 'Test Track'
        })
      });

      expect(response.status).toBe(400);
    });

    test('should return 400 for missing name', async () => {
      const response = await fetch(`${SERVER_URL}/api/tracks`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          userId: 'test123'
        })
      });

      expect(response.status).toBe(400);
    });
  });

  describe('GET /api/tracks', () => {
    test('should return all active tracks', async () => {
      // Create some test tracks first
      await fetch(`${SERVER_URL}/api/tracks`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ userId: 'user1', name: 'Track 1' })
      });

      const response = await fetch(`${SERVER_URL}/api/tracks`);
      expect(response.status).toBe(200);

      const data = await response.json();
      expect(Array.isArray(data)).toBe(true);
      expect(data.length).toBeGreaterThanOrEqual(1);
    });

    test('should filter by single client ID', async () => {
      // Create track for specific user
      await fetch(`${SERVER_URL}/api/tracks`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ userId: 'filter1', name: 'Filter Test 1' })
      });

      const response = await fetch(`${SERVER_URL}/api/tracks?clients=filter1`);
      expect(response.status).toBe(200);

      const data = await response.json();
      expect(data.length).toBeGreaterThanOrEqual(1);
      expect(data.some((t: any) => t.userId === 'filter1')).toBe(true);
    });

    test('should filter by multiple client IDs', async () => {
      // Create tracks for multiple users
      await fetch(`${SERVER_URL}/api/tracks`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ userId: 'multi1', name: 'Multi Test 1' })
      });
      await fetch(`${SERVER_URL}/api/tracks`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ userId: 'multi2', name: 'Multi Test 2' })
      });

      const response = await fetch(`${SERVER_URL}/api/tracks?clients=multi1,multi2`);
      expect(response.status).toBe(200);

      const data = await response.json();
      expect(data.length).toBeGreaterThanOrEqual(2);
      const userIds = data.map((t: any) => t.userId);
      expect(userIds).toContain('multi1');
      expect(userIds).toContain('multi2');
    });

    test('should return empty array for non-existent client', async () => {
      const response = await fetch(`${SERVER_URL}/api/tracks?clients=zzz999`);
      expect(response.status).toBe(200);

      const data = await response.json();
      expect(data).toEqual([]);
    });

    test('should handle empty clients parameter', async () => {
      const response = await fetch(`${SERVER_URL}/api/tracks?clients=`);
      expect(response.status).toBe(200);

      const data = await response.json();
      expect(Array.isArray(data)).toBe(true);
    });
  });

  describe('GET /api/tracks/:trackId', () => {
    test('should return specific track', async () => {
      const track = await fetch(`${SERVER_URL}/api/tracks`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ userId: 'gettest', name: 'Get Test Track' })
      }).then(r => r.json());

      const response = await fetch(`${SERVER_URL}/api/tracks/${track.id}`);
      expect(response.status).toBe(200);

      const data = await response.json();
      expect(data.id).toBe(track.id);
      expect(data.userId).toBe('gettest');
    });

    test('should return 404 for non-existent track', async () => {
      const response = await fetch(`${SERVER_URL}/api/tracks/non-existent-id`);
      expect(response.status).toBe(404);
    });
  });

  describe('POST /api/tracks/:trackId/points', () => {
    test('should add point to track', async () => {
      const track = await fetch(`${SERVER_URL}/api/tracks`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ userId: 'pointtest', name: 'Point Test Track' })
      }).then(r => r.json());

      const response = await fetch(`${SERVER_URL}/api/tracks/${track.id}/points`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          lat: 59.9139,
          lon: 10.7522,
          timestamp: Date.now(),
          altitude: 100,
          accuracy: 10
        })
      });

      expect(response.status).toBe(200);
      const data = await response.json();
      expect(data.points).toHaveLength(1);
      expect(data.points[0].lat).toBe(59.9139);
    });

    test('should return 404 for non-existent track', async () => {
      const response = await fetch(`${SERVER_URL}/api/tracks/non-existent/points`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          lat: 59.9,
          lon: 10.7,
          timestamp: Date.now()
        })
      });

      expect(response.status).toBe(404);
    });
  });

  describe('PUT /api/tracks/:trackId/stop', () => {
    test('should stop active track', async () => {
      const track = await fetch(`${SERVER_URL}/api/tracks`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ userId: 'stoptest', name: 'Stop Test Track' })
      }).then(r => r.json());

      const response = await fetch(`${SERVER_URL}/api/tracks/${track.id}/stop`, {
        method: 'PUT'
      });

      expect(response.status).toBe(200);
      const data = await response.json();
      expect(data.isActive).toBe(false);
      expect(data.endTime).toBeDefined();
    });

    test('should return 404 for non-existent track', async () => {
      const response = await fetch(`${SERVER_URL}/api/tracks/non-existent/stop`, {
        method: 'PUT'
      });

      expect(response.status).toBe(404);
    });
  });

  describe('DELETE /api/tracks/:trackId', () => {
    test('should delete track', async () => {
      const track = await fetch(`${SERVER_URL}/api/tracks`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ userId: 'deletetest', name: 'Delete Test Track' })
      }).then(r => r.json());

      const response = await fetch(`${SERVER_URL}/api/tracks/${track.id}`, {
        method: 'DELETE'
      });

      expect(response.status).toBe(204);

      // Verify deletion
      const getResponse = await fetch(`${SERVER_URL}/api/tracks/${track.id}`);
      expect(getResponse.status).toBe(404);
    });

    test('should return 404 for non-existent track', async () => {
      const response = await fetch(`${SERVER_URL}/api/tracks/non-existent`, {
        method: 'DELETE'
      });

      expect(response.status).toBe(404);
    });
  });

  describe('CORS', () => {
    test('should handle OPTIONS request', async () => {
      const response = await fetch(`${SERVER_URL}/api/tracks`, {
        method: 'OPTIONS'
      });

      expect(response.status).toBe(204);
      expect(response.headers.get('Access-Control-Allow-Origin')).toBe('*');
      expect(response.headers.get('Access-Control-Allow-Methods')).toContain('GET');
    });
  });
});

