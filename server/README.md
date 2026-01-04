# Where Server

Backend component for the Where? Android navigation app. Provides live tracking visualization on a web interface.

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