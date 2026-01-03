// Test script with multiple clients tracking at the same time
// Demonstrates the color-coding feature

const SERVER_URL = 'http://localhost:3000';

async function createTrack(userId: string, name: string) {
  const response = await fetch(`${SERVER_URL}/api/tracks`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ userId, name })
  });
  return await response.json();
}

async function addTrackPoint(trackId: string, lat: number, lon: number, altitude?: number) {
  const response = await fetch(`${SERVER_URL}/api/tracks/${trackId}/points`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      lat, lon,
      timestamp: Date.now(),
      altitude,
      accuracy: 10.0
    })
  });
  return await response.json();
}

// Simulate 3 different clients tracking simultaneously
async function multiClientTest() {
  console.log('🚀 Starting multi-client tracking simulation...\n');

  // Client 1: abc123
  const client1Route = [
    { lat: 59.9139, lon: 10.7522, alt: 10 },  // Oslo center
    { lat: 59.9200, lon: 10.7450, alt: 50 },
    { lat: 59.9250, lon: 10.7380, alt: 100 },
  ];

  // Client 2: xyz789
  const client2Route = [
    { lat: 59.9100, lon: 10.7600, alt: 5 },   // Different start
    { lat: 59.9150, lon: 10.7650, alt: 15 },
    { lat: 59.9200, lon: 10.7700, alt: 25 },
  ];

  // Client 3: pqr456
  const client3Route = [
    { lat: 59.9300, lon: 10.7300, alt: 80 },  // Another path
    { lat: 59.9350, lon: 10.7250, alt: 120 },
    { lat: 59.9400, lon: 10.7200, alt: 160 },
  ];

  // Create tracks
  const track1 = await createTrack('abc123', 'Morning Run');
  console.log(`✅ Client abc123 started: ${track1.name} (color: ${track1.color})`);

  const track2 = await createTrack('xyz789', 'Bike Ride');
  console.log(`✅ Client xyz789 started: ${track2.name} (color: ${track2.color})`);

  const track3 = await createTrack('pqr456', 'Evening Walk');
  console.log(`✅ Client pqr456 started: ${track3.name} (color: ${track3.color})`);

  console.log('\n📍 Sending GPS points from all clients...\n');

  // Send points interleaved to simulate real-time tracking
  for (let i = 0; i < 3; i++) {
    if (client1Route[i]) {
      await addTrackPoint(track1.id, client1Route[i].lat, client1Route[i].lon, client1Route[i].alt);
      console.log(`  abc123: ${client1Route[i].lat.toFixed(4)}, ${client1Route[i].lon.toFixed(4)}`);
    }
    await new Promise(resolve => setTimeout(resolve, 500));

    if (client2Route[i]) {
      await addTrackPoint(track2.id, client2Route[i].lat, client2Route[i].lon, client2Route[i].alt);
      console.log(`  xyz789: ${client2Route[i].lat.toFixed(4)}, ${client2Route[i].lon.toFixed(4)}`);
    }
    await new Promise(resolve => setTimeout(resolve, 500));

    if (client3Route[i]) {
      await addTrackPoint(track3.id, client3Route[i].lat, client3Route[i].lon, client3Route[i].alt);
      console.log(`  pqr456: ${client3Route[i].lat.toFixed(4)}, ${client3Route[i].lon.toFixed(4)}`);
    }
    await new Promise(resolve => setTimeout(resolve, 500));
  }

  console.log('\n✅ All clients tracking!');
  console.log('\n📊 Open http://localhost:3000 to see all 3 tracks with different colors on the map');
  console.log('   Each client ID gets a unique color: abc123, xyz789, pqr456');
}

multiClientTest().catch(console.error);

