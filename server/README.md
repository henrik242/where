# Where Server

Backend component for the Where? Android navigation app. Provides live tracking visualization on a web interface.

## Features

- **Live tracking** - Real-time location updates via WebSocket
- **Client filtering** - Show only specific client IDs
- **Historical tracks** - View tracks from the last 24 hours
- **God mode** - View all tracks with a secret key
- **Auto-sync** - Tracks sync even when online tracking is enabled mid-session

## API Endpoints

### Tracks

- `GET /api/tracks` - Get tracks (with optional filters)
  - Query params:
    - `clients` - Comma-separated client IDs (e.g., `?clients=abc123,def456`)
    - `historical` - Include tracks from last 24h (e.g., `?historical=true`)
    - `godMode` - Secret key to view all tracks (e.g., `?godMode=secret-god-key`)
- `GET /api/tracks/:trackId` - Get specific track
- `POST /api/tracks` - Create new track
- `POST /api/tracks/:trackId/points` - Add point to track
- `PUT /api/tracks/:trackId/stop` - Stop a track
- `DELETE /api/tracks/:trackId` - Delete a track

### WebSocket

- `WS /ws` - WebSocket connection for live updates

## Usage

### Web Interface

By default, no tracks are shown. You must either:

1. **Filter by client ID**: `http://localhost:3000?clients=abc123`
2. **Use god mode** (if GOD_MODE_KEY is set): `http://localhost:3000?godMode=your-secret-key`

Toggle "Show historical (24h)" checkbox to include stopped tracks.

**Note:** God mode will NOT work unless `GOD_MODE_KEY` environment variable is explicitly set when starting the server.

### Environment Variables

```bash
GOD_MODE_KEY=your-secret-key  # Required for god mode (no default)
PORT=3000                      # Default: 3000
```

## Development

```bash
# Install dependencies
bun install

# Run development server
bun run index.ts

# Run with custom god mode key
GOD_MODE_KEY=my-secret bun run index.ts

# The server will start on http://localhost:3000

# Testing a track
curl -X POST http://localhost:3000/api/tracks \
  -H "Content-Type: application/json" \
  -d '{
    "userId": "test01",
    "name": "Quick Test Track",
    "color": "#2196F3",
    "points": [
      {"lat": 59.9139, "lon": 10.7522, "timestamp": 1704297600000},
      {"lat": 59.9145, "lon": 10.7530, "timestamp": 1704297610000},
      {"lat": 59.9150, "lon": 10.7540, "timestamp": 1704297620000},
      {"lat": 59.9155, "lon": 10.7550, "timestamp": 1704297630000}
    ]
  }'
```

## Data Format

### Track Point
```json
{
  "lat": 59.9139,
  "lon": 10.7522,
  "timestamp": 1704297600000,
  "altitude": 100.5,
  "accuracy": 10.0
}
```

### Track
```json
{
  "id": "uuid",
  "userId": "user-id",
  "name": "Morning Run",
  "points": [...],
  "startTime": 1704297600000,
  "endTime": 1704301200000,
  "isActive": false
}
```