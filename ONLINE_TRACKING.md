# Online Tracking Feature

The Where app now supports **optional online tracking** that allows you to share your live location with others via a web interface.

## Features

- 🆔 **Unique Client ID**: Each device gets a persistent 6-character alphanumeric ID (e.g., `abc123`)
- 🎨 **Color-Coded Tracks**: Each client ID is assigned a unique color on the map
- 🌐 **Live Web Viewer**: View all active tracks in real-time at the server URL
- 🔒 **Optional**: Tracking is disabled by default and must be explicitly enabled
- 📡 **Real-time Updates**: Uses WebSocket for instant position updates

## How It Works

### Android App

1. **Enable Online Tracking**:
   - Open Settings
   - Toggle "Online Tracking" on
   - Your unique client ID is shown (e.g., `abc123`)

2. **Start Recording**:
   - Press the record button to start tracking
   - If online tracking is enabled, your position is sent to the server
   - Your track appears on the web interface with your unique color

3. **Privacy**:
   - Only active tracks are shared
   - Stopped tracks are removed from the public view
   - You can disable online tracking anytime in Settings

### Web Interface

Visit the server URL (default: `https://where.synth.no`) to see:
- Live map with all active tracks
- Each track in a different color
- Client ID and track name
- Total distance and point count
- Click any track to highlight and zoom to it

## Server Configuration

Default server: `https://where.synth.no`

To change the server URL:
1. Go to Settings
2. The tracking server URL can be modified in code (UserPreferences.kt)
3. Rebuild the app

## Client ID

- Generated automatically on first use
- Stored persistently in DataStore
- 6 lowercase alphanumeric characters (e.g., `a3x9z2`)
- Never changes unless app data is cleared
- Used to assign consistent color across sessions

## Color Assignment

Colors are deterministically generated from the client ID:
- Same client ID always gets the same color
- 15 distinct colors available
- Hash-based algorithm ensures even distribution

Available colors:
- Red, Pink, Purple, Deep Purple, Indigo
- Blue, Cyan, Teal, Green, Light Green
- Lime, Yellow, Amber, Orange, Brown

## Privacy & Security

⚠️ **Important Privacy Notes**:
- When enabled, your real-time location is publicly visible on the web interface
- Anyone with the server URL can see all active tracks
- No authentication is required to view tracks
- Client IDs are anonymous but persistent

**Recommended Use**:
- Hiking with friends/family who need to track you
- Group events where location sharing is desired
- Adventure races or challenges
- NOT recommended for private/sensitive journeys

## Testing

Test the feature locally:

```bash
# Start the server
cd server
bun run index.ts

# Simulate multiple clients
bun run test-multi-client.ts

# Open http://localhost:3000 in browser
```

## API Integration

The app communicates with the server via REST API:

- `POST /api/tracks` - Start tracking
- `POST /api/tracks/:id/points` - Add GPS point
- `PUT /api/tracks/:id/stop` - Stop tracking

See server/README.md for full API documentation.

## Deployment

The server is designed to run on `where.synth.no`:

1. Copy server files to production
2. Install Bun
3. Set up systemd service (see server/where-server.service)
4. Configure nginx reverse proxy
5. Ensure WebSocket support

See server/README.md for detailed deployment instructions.

