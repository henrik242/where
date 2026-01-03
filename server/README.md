# Where Server

Backend component for the Where Android navigation app. Provides live tracking visualization on a web interface.

## Features

- 📍 Real-time GPS tracking display
- 🗺️ Live map visualization with MapLibre GL
- 🔄 WebSocket updates for instant tracking
- 📊 Track distance calculation
- 🎨 Clean, minimal UI

## API Endpoints

### Tracks

- `GET /api/tracks` - Get all active tracks
- `GET /api/tracks/:trackId` - Get specific track
- `POST /api/tracks` - Create new track
- `POST /api/tracks/:trackId/points` - Add point to track
- `PUT /api/tracks/:trackId/stop` - Stop a track
- `DELETE /api/tracks/:trackId` - Delete a track

### WebSocket

- `WS /ws` - WebSocket connection for live updates

## Development

```bash
# Install dependencies
bun install

# Run development server
bun run index.ts

# The server will start on http://localhost:3000
```

## Production

```bash
# Set production port
export PORT=3000

# Run server
bun run index.ts
```

## Deployment to where.synth.no

1. Copy server files to production server
2. Install Bun on the server
3. Set up systemd service or PM2
4. Configure nginx reverse proxy
5. Set environment variables

Example nginx configuration:

```nginx
server {
    server_name where.synth.no;

    location / {
        proxy_pass http://localhost:3000;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection 'upgrade';
        proxy_set_header Host $host;
        proxy_cache_bypass $http_upgrade;
    }

    location /ws {
        proxy_pass http://localhost:3000;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "Upgrade";
        proxy_set_header Host $host;
    }
}
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

## License

Same as the main Where app

