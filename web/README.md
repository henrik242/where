# Where? Server

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
- `POST /api/tracks/:trackId/points` - Add point to track (204)
- `PUT /api/tracks/:trackId/stop` - Stop a track (204)
- `DELETE /api/tracks/:trackId` - Delete a track

### Places

- `GET /api/places?lat=..&lon=..&kind=building|water` - Name of the named building within 50 m
  of the point, or of the smallest named water body containing it: `{"name": "..."}`, or
  `{"name": null}` when there is none. Answers 503 when the index is missing or the point is
  outside its coverage (Norway); the app then falls back to public Overpass.
- Limited to `PLACES_RATE_LIMIT` requests per client IP per minute (default 120), answering 429
  with `Retry-After` beyond that. Behind the local Apache proxy the client IP is the last
  `X-Forwarded-For` entry. No CORS headers, since only the apps call it.

Data © OpenStreetMap contributors, [ODbL](https://opendatacommons.org/licenses/odbl/). The index
is a derivative database; `scripts/update-places.sh` and `scripts/build-places.ts` are how it is made.

#### How the index is built

`scripts/update-places.sh` downloads the Geofabrik Norway extract and its coverage polygon, keeps
named buildings and `natural=water` with `osmium`, and writes `places/places.sqlite`. The
`Update Places Index` workflow runs it every Monday, checks a few known answers
(`scripts/check-places.ts`), and rsyncs the file to `$REMOTE_TARGET/places/` on the server. The
server notices the new file on the next request; its log shows when the index was built and
warns when it is more than 14 days old. The data lags OSM by up to about a week.

One-time server setup: the upload goes to its own folder, so the deploy key never needs write
access to `data/` (which holds the tracking database):

```bash
sudo install -d -o <deploy-user> -m 755 /opt/where-web/places
```

Optionally add an `SSH_KNOWN_HOSTS` secret (output of `ssh-keyscan -H <host>`) to pin the host key.

#### Locally

```bash
bun run places:dev     # tiny Oslo fixture, no osmium needed
bun run places:check   # known-answer checks against places/places.sqlite
curl 'localhost:3000/api/places?lat=59.9829&lon=10.78&kind=water'

# Real data (needs osmium-tool: brew/apt install osmium-tool); PLACES_BBOX cuts a small area
PLACES_BBOX=10.6,59.85,10.9,60.0 bun run places:build
```

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
TRACKING_HINT=your-secret  # Required for HMAC verification
ADMIN_KEY=your-secret-key         # Optional - for admin access to view all tracks
ANDROID_CERT_SHA256=AB:CD:...     # Optional - app signing cert SHA-256(s), comma-separated, for Android App Links
PORT=3000                          # Default: 3000
PLACES_DB=./places/places.sqlite   # Default; built by scripts/update-places.sh
PLACES_RATE_LIMIT=120              # Default; /api/places requests per client IP per minute
```

**Important:** `TRACKING_HINT` must be set or all tracking requests will be rejected.

## Setup

1. Copy `.env.example` to `.env`:
   ```bash
   cp .env.example .env
   ```

2. Generate a secret key:
   ```bash
   openssl rand -base64 32
   ```

3. Edit `.env` and set `TRACKING_HINT` to the generated key

4. Start the server:
   ```bash
   bun run dev
   ```

The same secret must be configured in the Android app build.

**Note:** The client TypeScript (`src/client/app.ts`) is built to `src/client/app.js`, and maplibre-gl is
copied from `node_modules` into `src/client/vendor/`. Both are gitignored. `bun run dev` builds them on
start, but `--hot` only reloads the server, so rerun `bun run build` after editing `src/client/app.ts`.

## Project layout

```
src/
├── server/   # Bun runtime (HTTP, WebSocket, SQLite, HMAC)
├── client/   # Browser source (TS, HTML, CSS, images) + built app.js
└── shared/   # Types and pure helpers used by both sides
tests/        # Unit + integration tests (run on Bun)
```

A file's location tells you where it runs. `src/server/` may import from `src/shared/` but never from `src/client/` (and vice versa). Static assets live alongside the client TypeScript that consumes them; `app.js` is built next to `app.ts` and gitignored.

## Development

```bash
# Install dependencies
bun install

# Run web
bun run dev

# The web will start on http://localhost:3000

# Type-check both server and client (separate tsconfigs enforce the boundary)
bun run typecheck
```

**Note:** All POST/PUT requests require HMAC-SHA256 signatures in the `X-Signature` header. See `.env.example` for setup.

## Deployment

Deployment is automated via GitHub Actions. On push to `main`:
1. GHA type-checks server and client, and fails the deploy on any error
2. GHA builds `app.ts` → `app.js` (minified)
3. GHA deploys files to server via SSH
4. Server auto-restarts when files change

Manual deployment on server:
```bash
# Server will auto-restart when new files arrive
# Just ensure .env is configured
```

## Build Artifacts

The build process generates:
- `src/client/app.js` - Compiled TypeScript client code (gitignored, built by GHA)

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