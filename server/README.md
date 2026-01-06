# Where Server

Backend component for the Where? Android navigation app. Provides live tracking visualization on a web interface.

## Features

- **Live tracking** - Real-time location updates via WebSocket
- **Client filtering** - Show only specific client IDs
- **Historical tracks** - View tracks from the last 24 hours
- **Auto-sync** - Tracks sync even when online tracking is enabled mid-session

## API Endpoints

### Tracks

- `GET /api/tracks` - Get tracks (with optional filters)
  - Query params:
    - `clients` - Comma-separated client IDs (e.g., `?clients=abc123,def456`)
    - `historical` - Include tracks from last 24h (e.g., `?historical=true`)
  - Header:
    - `X-Admin-Key` - Secret key to view all tracks
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
2. **Use admin** (if ADMIN_KEY is set): `http://localhost:3000?admin=your-secret-key`

Toggle "Show historical (24h)" checkbox to include stopped tracks.

**Note:** Admin will NOT work unless `ADMIN_KEY` environment variable is explicitly set when starting the server.

### Environment Variables

```bash
TRACKING_HMAC_SECRET=your-secret  # Required for HMAC verification
ADMIN_KEY=your-secret-key         # Optional - for admin access to view all tracks
PORT=3000                          # Default: 3000
```

**Important:** `TRACKING_HMAC_SECRET` must be set or all tracking requests will be rejected.

## Setup

1. Copy `.env.example` to `.env`:
   ```bash
   cp .env.example .env
   ```

2. Generate a secret key:
   ```bash
   openssl rand -base64 32
   ```

3. Edit `.env` and set `TRACKING_HMAC_SECRET` to the generated key

4. Start the server:
   ```bash
   bun run dev
   ```

The same secret must be configured in the Android app build.

**Note:** The client TypeScript (`src/public/app.ts`) is automatically built to `app.js` by GitHub Actions during deployment. For local development, just run `bun run dev` - the pre-built `app.js` will be used.

## Development

```bash
# Install dependencies
bun install

# Run server
bun run dev

# The server will start on http://localhost:3000
```

**Note:** All POST/PUT requests require HMAC-SHA256 signatures in the `X-Signature` header. See `.env.example` for setup.

## Deployment

Deployment is automated via GitHub Actions. On push to `main`:
1. GHA builds `app.ts` → `app.js` (minified)
2. GHA deploys files to server via SSH
3. Server auto-restarts when files change

Manual deployment on server:
```bash
# Server will auto-restart when new files arrive
# Just ensure .env is configured
```

## Build Artifacts

The build process generates:
- `src/public/app.js` - Compiled TypeScript client code (gitignored, built by GHA)

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