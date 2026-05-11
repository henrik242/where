import type {
  Track,
  TrackPoint,
  SessionStats,
  TracksResponse,
} from '../shared/types';
import { calculateTrackDistance } from '../shared/geo';

type Point = TrackPoint;

interface WebSocketMessage {
  type: 'track_update' | 'track_stopped' | 'track_started' | 'track_deleted' | 'initial_state';
  trackId: string;
  userId: string;
  name?: string;
  point?: Point;
  color?: string;
  tracks?: Track[];
  admin?: boolean;
  sessionStats?: SessionStats;
}

// Escape HTML to prevent XSS
function escapeHtml(str: string): string {
  return str.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;').replace(/'/g, '&#39;');
}

// Global state
let map: maplibregl.Map;
let tracks: Record<string, Track> = {};
let selectedTrackId: string | null = null;
const renderTypeFor = new Map<string, 'single' | 'line'>();
let clientFilters: string[] = [];
let showHistorical = false;
let admin = false;
let adminKey: string | undefined;

function initMap(): void {
  const defaultCenter: [number, number] = [10.7522, 59.9139]; // Oslo
  const defaultZoom = 10;

  map = new maplibregl.Map({
    container: 'map',
    style: {
      version: 8,
      sources: {
        osm: {
          type: 'raster',
          tiles: ['https://tile.openstreetmap.org/{z}/{x}/{y}.png'],
          tileSize: 256,
          attribution: '© <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'
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
    center: defaultCenter,
    zoom: defaultZoom,
    minZoom: 0,
    maxZoom: 18
  });

  map.addControl(new maplibregl.NavigationControl(), 'top-left');

  // Try to get user's location (only if no client filters specified in URL)
  // Client filters take priority - we'll zoom to those tracks instead
  if ('geolocation' in navigator && clientFilters.length === 0) {
    navigator.geolocation.getCurrentPosition(
      (position) => {
        // Successfully got position - fly to it
        map.flyTo({
          center: [position.coords.longitude, position.coords.latitude],
          zoom: 14,
          essential: true
        });
      },
      (error) => {
        // Failed to get position - already using Oslo fallback
        console.debug('Geolocation unavailable, using default location:', error.message);
      },
      {
        timeout: 5000,
        maximumAge: 300000, // Accept cached position up to 5 minutes old
        enableHighAccuracy: false
      }
    );
  }
}

// Parse URL parameters
function parseURLParameters(): void {
  const urlParams = new URLSearchParams(window.location.search);

  // Parse client IDs from path (e.g. /abc123 or /abc123,def456)
  const path = window.location.pathname.slice(1);
  if (path && /^[a-z0-9]{6}(,[a-z0-9]{6})*$/.test(path)) {
    clientFilters = path.split(',');
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

// Update session stats display
function updateSessionStats(stats: SessionStats): void {
  const el = document.getElementById('session-stats');
  if (!el) return;

  const fmt = (counts: Record<string, number>) => {
    const ios = counts['ios'] || 0;
    const android = counts['android'] || 0;
    return `iOS: ${ios}, Android: ${android}`;
  };

  el.innerHTML = `
    <div style="margin-bottom: 4px; font-weight: 600;">Tracking Sessions</div>
    <div>24h: ${fmt(stats.day)}</div>
    <div>7d: ${fmt(stats.week)}</div>
    <div>30d: ${fmt(stats.month)}</div>
  `;
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

  if (clientFilters.length !== 0) {
    tagsContainer.innerHTML = clientFilters.map(clientId => {
      const safe = escapeHtml(clientId);
      return `
      <div class="client-tag" onclick="removeClientFilter('${safe}')">
        ${safe}
        <span class="remove">×</span>
      </div>
    `;
    }).join('');
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
    sendSubscribe();
    input.value = '';
  }
}

// Remove client filter
function removeClientFilter(clientId: string): void {
  clientFilters = clientFilters.filter(id => id !== clientId);
  updateClientTags();
  sendSubscribe();
}

// Toggle historical tracks
function toggleHistorical(): void {
  const checkbox = document.getElementById('historical-toggle') as HTMLInputElement;
  showHistorical = checkbox?.checked || false;
  sendSubscribe();
}

// Disable admin
function disableAdmin(): void {
  admin = false;
  adminKey = undefined;
  updateAdminIndicator(false);
  sendSubscribe();
}

// Update URL with current filters
function updateURL(): void {
  const newURL = clientFilters.length > 0 ? `/${clientFilters.join(',')}` : '/';
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
  const distance = calculateTrackDistance(track.points);
  const pointCount = track.points.length;

  return `
    <div class="track-item ${isSelected ? 'active' : ''}" onclick="selectTrack('${escapeHtml(track.id)}')" ${isHistorical ? 'style="opacity: 0.7;"' : ''}>
      <div class="track-name">
        <span style="display: inline-block; width: 12px; height: 12px; border-radius: 50%; background: ${escapeHtml(track.color || '#FF5722')}; margin-right: 8px;"></span>
        ${escapeHtml(track.userId)} - ${escapeHtml(track.name)}
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
    if (track.points.length === 1) {
      const p = track.points[0];
      map.flyTo({ center: [p.lon, p.lat], zoom: 15 });
    } else {
      const bounds = new maplibregl.LngLatBounds();
      track.points.forEach(p => bounds.extend([p.lon, p.lat]));
      map.fitBounds(bounds, { padding: 50, maxZoom: 16 });
    }
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

    // Layer types differ between single (circle) and multi (line). When the
    // render type changes the existing layers must be torn down or paint
    // properties will be applied to the wrong layer type.
    const newType: 'single' | 'line' = track.points.length === 1 ? 'single' : 'line';
    if (renderTypeFor.get(trackId) !== newType) {
      cleanupTrackLayers(trackId);
    }
    renderTypeFor.set(trackId, newType);

    if (newType === 'single') {
      renderSinglePoint(track, sourceId, layerId);
    } else {
      renderTrackLine(track, sourceId, layerId);
      renderStartEndPoints(track, trackId);
    }
  });
}

// Cleanup track layers
function cleanupTrackLayers(trackId: string): void {
  renderTypeFor.delete(trackId);
  const layers = [
    `track-layer-${trackId}-halo`,
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
    // Outer halo for visibility
    map.addLayer({
      id: `${layerId}-halo`,
      type: 'circle',
      source: sourceId,
      paint: {
        'circle-radius': 14,
        'circle-color': track.color || '#FF5722',
        'circle-opacity': 0.25
      }
    });
    map.addLayer({
      id: layerId,
      type: 'circle',
      source: sourceId,
      paint: {
        'circle-radius': 8,
        'circle-color': track.color || '#FF5722',
        'circle-stroke-width': 2.5,
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

    // Get IDs of tracks that will be removed
    const oldTrackIds = Object.keys(tracks);
    const newTracks = data.tracks.reduce((acc, track) => {
      acc[track.id] = track;
      return acc;
    }, {} as Record<string, Track>);
    const newTrackIds = Object.keys(newTracks);

    // Remove layers for tracks that are no longer in the response
    oldTrackIds.forEach(trackId => {
      if (!newTrackIds.includes(trackId)) {
        cleanupTrackLayers(trackId);
      }
    });

    tracks = newTracks;

    updateTracksList();
    updateMap();

    // If client filters are specified, zoom to show all those tracks
    if (clientFilters.length > 0) {
      zoomToClientTracks();
    }
  } catch (error) {
    console.error('Error fetching tracks:', error);
  }
}

// Zoom map to show all client tracks
function zoomToClientTracks(): void {
  const allPoints: Point[] = [];

  Object.values(tracks).forEach(track => {
    allPoints.push(...track.points);
  });

  if (allPoints.length > 0) {
    const bounds = new maplibregl.LngLatBounds();
    allPoints.forEach(point => {
      bounds.extend([point.lon, point.lat]);
    });

    // Bias the point toward the upper portion so it's not covered if the
    // panel is open: extra bottom padding on mobile (panel docks bottom),
    // extra right padding on desktop (panel docks top-right).
    // Breakpoint matches the @media (max-width: 768px) rule in app.css.
    const isMobile = window.matchMedia('(max-width: 768px)').matches;
    const padding = isMobile
      ? { top: 50, bottom: 250, left: 50, right: 50 }
      : { top: 50, bottom: 50, left: 50, right: 350 };

    map.fitBounds(bounds, {
      padding,
      maxZoom: 15 // Don't zoom in too close if there's only one point
    });
  }
}

// Send subscribe message to server with current filters
function sendSubscribe(): void {
  if (!currentWs || currentWs.readyState !== WebSocket.OPEN) return;
  const msg: any = {
    type: 'subscribe',
    clients: clientFilters,
    historical: showHistorical,
  };
  if (adminKey) msg.adminKey = adminKey;
  currentWs.send(JSON.stringify(msg));
}

// Setup WebSocket with exponential backoff reconnection
let wsReconnectDelay = 1000;
let currentWs: WebSocket | null = null;

function setupWebSocket(): void {
  const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
  const ws = new WebSocket(`${protocol}//${window.location.host}/ws`);
  currentWs = ws;

  ws.onopen = () => {
    wsReconnectDelay = 1000;
    sendSubscribe();
  };

  ws.onmessage = (event) => {
    let data: WebSocketMessage;
    try {
      data = JSON.parse(event.data);
    } catch (e) {
      console.error('Failed to parse WebSocket message:', e);
      return;
    }

    if (data.type === 'initial_state') {
      updateAdminIndicator(data.admin === true);
      if (data.sessionStats) updateSessionStats(data.sessionStats);

      const oldTrackIds = Object.keys(tracks);
      const newTracks: Record<string, Track> = {};
      (data.tracks || []).forEach(track => {
        newTracks[track.id] = track;
      });

      oldTrackIds.forEach(trackId => {
        if (!newTracks[trackId]) cleanupTrackLayers(trackId);
      });

      tracks = newTracks;
      updateTracksList();
      updateMap();

      if (clientFilters.length > 0) zoomToClientTracks();
      return;
    }

    if (data.type === 'track_started') {
      tracks[data.trackId] = {
        id: data.trackId,
        userId: data.userId,
        name: data.name || 'Unnamed Track',
        points: data.point ? [data.point] : [],
        startTime: data.point?.timestamp || Date.now(),
        isActive: true,
        color: data.color
      };
      updateTracksList();
      updateMap();
    } else if (data.type === 'track_update') {
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
    } else if (data.type === 'track_deleted') {
      if (tracks[data.trackId]) {
        cleanupTrackLayers(data.trackId);
        delete tracks[data.trackId];
        if (selectedTrackId === data.trackId) selectedTrackId = null;
        updateTracksList();
      }
    }
  };

  ws.onerror = (error) => {
    console.error('WebSocket error:', error);
  };

  ws.onclose = () => {
    currentWs = null;
    console.log(`WebSocket closed, reconnecting in ${wsReconnectDelay / 1000}s...`);
    setTimeout(setupWebSocket, wsReconnectDelay);
    wsReconnectDelay = Math.min(wsReconnectDelay * 2, 30000);
  };
}

// UI Functions (exposed globally for onclick handlers)
(window as any).addClientFilter = addClientFilter;
(window as any).removeClientFilter = removeClientFilter;
(window as any).toggleHistorical = toggleHistorical;
(window as any).disableAdmin = disableAdmin;
(window as any).selectTrack = selectTrack;
function hidePanel(): void {
  document.getElementById('info-panel')?.classList.add('hidden');
  document.getElementById('show-panel-btn')?.classList.add('visible');
}
function showPanel(): void {
  document.getElementById('info-panel')?.classList.remove('hidden');
  document.getElementById('show-panel-btn')?.classList.remove('visible');
}
(window as any).hidePanel = hidePanel;
(window as any).showPanel = showPanel;

// Initialize on load
document.addEventListener('DOMContentLoaded', () => {
  parseURLParameters(); // Parse URL first so clientFilters is set
  initMap();
  updateClientTags();

  // When arriving via a clientId link, collapse the panel so it doesn't
  // cover the track point. The user can re-open it with the Live Tracks button.
  if (clientFilters.length > 0) hidePanel();

  document.getElementById('client-input')?.addEventListener('keypress', (e) => {
    if ((e as KeyboardEvent).key === 'Enter') addClientFilter();
  });

  // Display version info if available
  const versionInfo = document.getElementById('version-info');
  const versionDisplay = document.getElementById('version-display');
  if (versionDisplay) {
    const version = versionInfo?.textContent?.trim();
    versionDisplay.textContent = version || 'dev';
  }

  setupWebSocket();
});

