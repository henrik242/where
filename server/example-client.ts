// Example client for testing the tracking API
// This demonstrates how the Android app would send tracking data

const SERVER_URL = 'http://localhost:3000';

async function createTrack(userId: string, name: string) {
  const response = await fetch(`${SERVER_URL}/api/tracks`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      userId,
      name
    })
  });
  return await response.json();
}

async function addTrackPoint(trackId: string, lat: number, lon: number, altitude?: number) {
  const response = await fetch(`${SERVER_URL}/api/tracks/${trackId}/points`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      lat,
      lon,
      timestamp: Date.now(),
      altitude,
      accuracy: 10.0
    })
  });
  return await response.json();
}

async function stopTrack(trackId: string) {
  const response = await fetch(`${SERVER_URL}/api/tracks/${trackId}/stop`, {
    method: 'PUT'
  });
  return await response.json();
}

// Example: Simulate a hiking track in Norway (Oslo to Holmenkollen)
async function simulateTrack() {
  console.log('🚀 Creating track...');
  const track = await createTrack('user123', 'Morning Hike to Holmenkollen');
  console.log('✅ Track created:', track.id);

  // Simulate GPS points along the route
  const points = [
    { lat: 59.9139, lon: 10.7522, alt: 10 },   // Oslo center
    { lat: 59.9200, lon: 10.7450, alt: 50 },
    { lat: 59.9250, lon: 10.7380, alt: 100 },
    { lat: 59.9300, lon: 10.7320, alt: 150 },
    { lat: 59.9350, lon: 10.7250, alt: 200 },
    { lat: 59.9400, lon: 10.7200, alt: 250 },
    { lat: 59.9468, lon: 10.6695, alt: 371 },  // Holmenkollen
  ];

  console.log(`📍 Sending ${points.length} GPS points...`);

  for (const point of points) {
    await addTrackPoint(track.id, point.lat, point.lon, point.alt);
    console.log(`  → Point sent: ${point.lat.toFixed(4)}, ${point.lon.toFixed(4)} (${point.alt}m)`);
    // Wait a bit between points to simulate real tracking
    await new Promise(resolve => setTimeout(resolve, 1000));
  }

  console.log('🏁 Stopping track...');
  await stopTrack(track.id);
  console.log('✅ Track completed!');
  console.log('\n📊 Open http://localhost:3000 in your browser to see the track on the map');
}

// Run the simulation
simulateTrack().catch(console.error);

