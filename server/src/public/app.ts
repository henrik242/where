// Type definitions
interface Point {
  lat: number;
  lon: number;
  timestamp: number;
  altitude?: number;
  accuracy?: number;
}

interface Track {
  id: string;
  userId: string;
  name: string;
  points: Point[];
  startTime: number;
  endTime?: number;
  isActive: boolean;
  color?: string;
  distance?: number;
  pointCount?: number;
}

interface TracksResponse {
  admin: boolean;
  tracks: Track[];
}

interface WebSocketMessage {
  type: 'track_update' | 'track_stopped' | 'track_started';
  trackId: string;
  userId: string;
  name?: string;
  point?: Point;
  color?: string;
}

// Global state
let map: maplibregl.Map;
let tracks: Record<string, Track> = {};
let selectedTrackId: string | null = null;
let clientFilters: string[] = [];
let showHistorical = false;
let admin = false;
let adminKey: string | undefined;

// Initialize map
function initMap(): void {
  map = new maplibregl.Map({
    container: 'map',
    style: {
      version: 8,
      sources: {
        osm: {
          type: 'raster',
          tiles: ['https://tile.openstreetmap.org/{z}/{x}/{y}.png'],
          tileSize: 256,
          attribution: '© OpenStreetMap contributors'
        }
      },
      layers: [{
        id: 'osm',
        type: 'raster',
        source: 'osm',
        minzoom: 0,
        maxzoom: 19
      }]
    },
    center: [10.7522, 59.9139], // Oslo
    zoom: 10
  });

  map.addControl(new maplibregl.NavigationControl(), 'top-left');
}

// Parse URL parameters
function parseURLParameters(): void {
  const urlParams = new URLSearchParams(window.location.search);

  const clientsParam = urlParams.get('clients');
  if (clientsParam) {
    clientFilters = clientsParam.split(',').filter(Boolean);
  }

  const adminParam = urlParams.get('admin');
  if (adminParam) {
    adminKey = adminParam;
    const url = new URL(window.location.href);
    url.searchParams.delete('admin');
    window.history.replaceState({}, '', url.toString());
  }
}

// Update admin indicator
function updateAdminIndicator(isActive: boolean): void {
  const section = document.getElementById('admin-section');
  if (!section) return;

  if (isActive) {
    section.classList.add('active');
    admin = true;
  } else {
    section.classList.remove('active');
    admin = false;
  }
}

// Format distance
function formatDistance(meters: number): string {
  if (meters < 1000) return `${meters.toFixed(0)} m`;
  if (meters < 10000) return `${(meters / 1000).toFixed(2)} km`;
  return `${(meters / 1000).toFixed(1)} km`;
}

// Format time
function formatTime(timestamp: number): string {
  return new Date(timestamp).toLocaleTimeString();
}

// Update client tags display
function updateClientTags(): void {
  const tagsContainer = document.getElementById('client-tags');
  if (!tagsContainer) return;

  if (clientFilters.length === 0) {
    tagsContainer.innerHTML = '<span style="color: #999; font-size: 12px;">Showing all clients</span>';
  } else {
    tagsContainer.innerHTML = clientFilters.map(clientId => `
      <div class="client-tag" onclick="removeClientFilter('${clientId}')">
        ${clientId}
        <span class="remove">×</span>
      </div>
    `).join('');
  }
  updateURL();
}

// Add client filter
function addClientFilter(): void {
  const input = document.getElementById('client-input') as HTMLInputElement;
  if (!input) return;

  const clientId = input.value.trim().toLowerCase();
  if (clientId && clientId.length === 6 && !clientFilters.includes(clientId)) {
    clientFilters.push(clientId);
    updateClientTags();
    fetchTracks();
    input.value = '';
  }
}

// Remove client filter
function removeClientFilter(clientId: string): void {
  clientFilters = clientFilters.filter(id => id !== clientId);
  updateClientTags();
  fetchTracks();
}

// Toggle historical tracks
function toggleHistorical(): void {
  const checkbox = document.getElementById('historical-toggle') as HTMLInputElement;
  showHistorical = checkbox?.checked || false;
  fetchTracks();
}

// Disable admin
function disableAdmin(): void {
  admin = false;
  adminKey = undefined;
  updateAdminIndicator(false);
  fetchTracks();
}

// Update URL with current filters
function updateURL(): void {
  const params = new URLSearchParams();
  if (clientFilters.length > 0) {
    params.set('clients', clientFilters.join(','));
  }
  const newURL = params.toString() ? `?${params.toString()}` : window.location.pathname;
  window.history.replaceState({}, '', newURL);
}

// Update tracks list
function updateTracksList(): void {
  const tracksList = document.getElementById('tracks-list');
  if (!tracksList) return;

  const allTracks = Object.values(tracks);

  if (allTracks.length === 0) {
    tracksList.innerHTML = '<div class="no-tracks">No tracks found</div>';
    return;
  }

  const activeTracks = allTracks.filter(t => t.isActive);
  const inactiveTracks = allTracks.filter(t => !t.isActive);

  let html = '';

  if (activeTracks.length > 0) {
    html += activeTracks.map(track => renderTrackItem(track)).join('');
  }

  if (showHistorical && inactiveTracks.length > 0) {
    html += '<div style="margin-top: 12px; padding: 8px; font-size: 12px; color: #999; border-top: 1px solid #ddd;">Historical Tracks</div>';
    html += inactiveTracks.map(track => renderTrackItem(track, true)).join('');
  }

  tracksList.innerHTML = html || '<div class="no-tracks">No tracks found</div>';
}

// Render single track item
function renderTrackItem(track: Track, isHistorical = false): string {
  const isSelected = track.id === selectedTrackId;
  const distance = track.distance || 0;
  const pointCount = track.pointCount || track.points.length;

  return `
    <div class="track-item ${isSelected ? 'active' : ''}" onclick="selectTrack('${track.id}')" ${isHistorical ? 'style="opacity: 0.7;"' : ''}>
      <div class="track-name">
        <span style="display: inline-block; width: 12px; height: 12px; border-radius: 50%; background: ${track.color || '#FF5722'}; margin-right: 8px;"></span>
        ${track.userId} - ${track.name}
      </div>
      <div class="track-info">${pointCount} points · ${isHistorical ? formatTime(track.startTime) + ' - ' + formatTime(track.endTime || track.startTime) : 'Started ' + formatTime(track.startTime)}</div>
      <div class="track-distance">${formatDistance(distance)}</div>
    </div>
  `;
}

// Select track
function selectTrack(trackId: string): void {
  selectedTrackId = trackId;
  updateTracksList();
  updateMap();

  const track = tracks[trackId];
  if (track && track.points.length > 0) {
    const bounds = new maplibregl.LngLatBounds();
    track.points.forEach(p => bounds.extend([p.lon, p.lat]));
    map.fitBounds(bounds, { padding: 50 });
  }
}

// Update map
function updateMap(): void {
  if (!map.loaded()) {
    map.once('load', () => updateMap());
    return;
  }

  Object.keys(tracks).forEach(trackId => {
    const track = tracks[trackId];
    const sourceId = `track-${trackId}`;
    const layerId = `track-layer-${trackId}`;

    if (track.points.length === 0) {
      cleanupTrackLayers(trackId);
      return;
    }

    if (track.points.length === 1) {
      renderSinglePoint(track, sourceId, layerId);
    } else {
      renderTrackLine(track, sourceId, layerId);
      renderStartEndPoints(track, trackId);
    }
  });
}

// Cleanup track layers
function cleanupTrackLayers(trackId: string): void {
  const layers = [
    `track-layer-${trackId}`,
    `track-start-layer-${trackId}`,
    `track-end-layer-${trackId}`
  ];
  const sources = [
    `track-${trackId}`,
    `track-start-${trackId}`,
    `track-end-${trackId}`
  ];

  layers.forEach(id => {
    if (map.getLayer(id)) map.removeLayer(id);
  });
  sources.forEach(id => {
    if (map.getSource(id)) map.removeSource(id);
  });
}

// Render single point
function renderSinglePoint(track: Track, sourceId: string, layerId: string): void {
  const geojson = {
    type: 'Feature' as const,
    geometry: {
      type: 'Point' as const,
      coordinates: [track.points[0].lon, track.points[0].lat]
    }
  };

  if (map.getSource(sourceId)) {
    (map.getSource(sourceId) as maplibregl.GeoJSONSource).setData(geojson);
  } else {
    map.addSource(sourceId, { type: 'geojson', data: geojson });
    map.addLayer({
      id: layerId,
      type: 'circle',
      source: sourceId,
      paint: {
        'circle-radius': 6,
        'circle-color': track.color || '#FF5722',
        'circle-stroke-width': 2,
        'circle-stroke-color': '#FFFFFF'
      }
    });
  }
}

// Render track line
function renderTrackLine(track: Track, sourceId: string, layerId: string): void {
  const geojson = {
    type: 'Feature' as const,
    geometry: {
      type: 'LineString' as const,
      coordinates: track.points.map(p => [p.lon, p.lat])
    }
  };

  if (map.getSource(sourceId)) {
    (map.getSource(sourceId) as maplibregl.GeoJSONSource).setData(geojson);
    // Update line width if layer exists
    const layer = map.getLayer(layerId);
    if (layer) {
      try {
        map.setPaintProperty(layerId, 'line-width', track.id === selectedTrackId ? 4 : 3);
      } catch (e) {
        // Ignore errors from setPaintProperty - layer might be in transition
        console.debug('Error setting paint property:', e);
      }
    }
  } else {
    map.addSource(sourceId, { type: 'geojson', data: geojson });
    map.addLayer({
      id: layerId,
      type: 'line',
      source: sourceId,
      paint: {
        'line-color': track.color || '#FF5722',
        'line-width': track.id === selectedTrackId ? 4 : 3,
        'line-opacity': 0.8
      }
    });
  }
}

// Render start and end points
function renderStartEndPoints(track: Track, trackId: string): void {
  // Start point (green)
  const startSourceId = `track-start-${trackId}`;
  const startLayerId = `track-start-layer-${trackId}`;
  const startGeoJson = {
    type: 'Feature' as const,
    geometry: {
      type: 'Point' as const,
      coordinates: [track.points[0].lon, track.points[0].lat]
    }
  };

  if (map.getSource(startSourceId)) {
    (map.getSource(startSourceId) as maplibregl.GeoJSONSource).setData(startGeoJson);
  } else {
    map.addSource(startSourceId, { type: 'geojson', data: startGeoJson });
    map.addLayer({
      id: startLayerId,
      type: 'circle',
      source: startSourceId,
      paint: {
        'circle-radius': 5,
        'circle-color': '#4CAF50',
        'circle-stroke-width': 2,
        'circle-stroke-color': '#FFFFFF'
      }
    });
  }

  // End point (blue for active, red for stopped)
  const endSourceId = `track-end-${trackId}`;
  const endLayerId = `track-end-layer-${trackId}`;
  const lastPoint = track.points[track.points.length - 1];
  const endGeoJson = {
    type: 'Feature' as const,
    geometry: {
      type: 'Point' as const,
      coordinates: [lastPoint.lon, lastPoint.lat]
    }
  };

  if (map.getSource(endSourceId)) {
    (map.getSource(endSourceId) as maplibregl.GeoJSONSource).setData(endGeoJson);
  } else {
    map.addSource(endSourceId, { type: 'geojson', data: endGeoJson });
    map.addLayer({
      id: endLayerId,
      type: 'circle',
      source: endSourceId,
      paint: {
        'circle-radius': 5,
        'circle-color': track.isActive ? '#2196F3' : '#F44336',
        'circle-stroke-width': 2,
        'circle-stroke-color': '#FFFFFF'
      }
    });
  }
}

// Fetch tracks from server
async function fetchTracks(): Promise<void> {
  try {
    const params = new URLSearchParams();
    if (clientFilters.length > 0) {
      params.set('clients', clientFilters.join(','));
    }
    if (showHistorical) {
      params.set('historical', 'true');
    }
    const url = `/api/tracks${params.toString() ? '?' + params.toString() : ''}`;

    const headers: Record<string, string> = {};
    if (adminKey) {
      headers['X-Admin-Key'] = adminKey;
    }

    const response = await fetch(url, { headers });

    // Handle 401 - invalid admin key
    if (response.status === 401) {
      console.error('Invalid admin key');
      alert('Invalid admin key');
      // Disable admin
      admin = false;
      adminKey = undefined;
      updateAdminIndicator(false);
      // Retry without admin
      return fetchTracks();
    }

    const data: TracksResponse = await response.json();

    updateAdminIndicator(data.admin);

    tracks = data.tracks.reduce((acc, track) => {
      acc[track.id] = track;
      return acc;
    }, {} as Record<string, Track>);

    updateTracksList();
    updateMap();
  } catch (error) {
    console.error('Error fetching tracks:', error);
  }
}

// Setup WebSocket
function setupWebSocket(): void {
  const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
  const ws = new WebSocket(`${protocol}//${window.location.host}/ws`);

  ws.onmessage = (event) => {
    const data: WebSocketMessage = JSON.parse(event.data);

    if (data.type === 'track_update') {
      if (clientFilters.length > 0 && !clientFilters.includes(data.userId)) {
        return;
      }

      if (!tracks[data.trackId]) {
        tracks[data.trackId] = {
          id: data.trackId,
          userId: data.userId,
          name: data.name || 'Unnamed Track',
          points: [],
          startTime: data.point?.timestamp || Date.now(),
          isActive: true,
          color: data.color
        };
      }
      if (data.point) {
        tracks[data.trackId].points.push(data.point);
      }
      updateTracksList();
      updateMap();
    } else if (data.type === 'track_stopped') {
      if (tracks[data.trackId]) {
        tracks[data.trackId].isActive = false;
        updateTracksList();
      }
    }
  };

  ws.onerror = (error) => {
    console.error('WebSocket error:', error);
  };

  ws.onclose = () => {
    console.log('WebSocket connection closed, attempting to reconnect...');
    setTimeout(() => location.reload(), 5000);
  };
}

// UI Functions (exposed globally for onclick handlers)
(window as any).addClientFilter = addClientFilter;
(window as any).removeClientFilter = removeClientFilter;
(window as any).toggleHistorical = toggleHistorical;
(window as any).disableAdmin = disableAdmin;
(window as any).selectTrack = selectTrack;
(window as any).hidePanel = function() {
  document.getElementById('info-panel')?.classList.add('hidden');
  document.getElementById('show-panel-btn')?.classList.add('visible');
};
(window as any).showPanel = function() {
  document.getElementById('info-panel')?.classList.remove('hidden');
  document.getElementById('show-panel-btn')?.classList.remove('visible');
};

// Initialize on load
document.addEventListener('DOMContentLoaded', () => {
  initMap();
  parseURLParameters();
  updateClientTags();

  document.getElementById('client-input')?.addEventListener('keypress', (e) => {
    if ((e as KeyboardEvent).key === 'Enter') addClientFilter();
  });

  fetchTracks();
  setupWebSocket();

  // Refresh every 30 seconds as backup
  setInterval(fetchTracks, 30000);
});

