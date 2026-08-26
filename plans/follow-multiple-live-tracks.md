# Implementation Plan: Follow Several Live Tracks at Once

> Status: implemented, with two decisions reversed during review. Friends are coloured by their
> position in the followed list from `TrackColors`, not by the server's per-client colour (that
> colour is always hash-derived, collides for ~5 followed ids more often than not, and its Material
> palette reads badly on Kartverket topo). Adding or removing a friend reconnects rather than
> re-subscribing on the open socket. Kept for rationale; the code is the spec.

Feature request: "Er det mulig å ønske seg å kunne se flere enn én direktesporing om gangen?
Noen ganger er man 3 eller fler på tur."

Goal: let the app follow N client IDs simultaneously (target cap 5), each drawn in its own color
with its own label, instead of the current single `followedClientId`.

## Repo context (verified)

**The server and web viewer are already multi-client. No protocol or backend change is needed.**

- `web/src/server/ws.ts:38` — `subscribe` takes `clients: string[]`; `handleSubscribe` sets
  `ws.data = { clients, admin }` and replies `initial_state`. Re-sending `subscribe` on an *open*
  socket replaces the list and resends `initial_state`, so adding/removing an ID needs no reconnect.
- `web/src/server/store.ts:216` — `getTracksByClientIds(clientIds, includeHistorical)` already takes
  a list (`userId IN (...)`, historical = last 24h or still active).
- Every broadcast carries `userId`: `track_started` (`api.ts:143`, `api.ts:182`), `track_update`
  (`api.ts:191`), `track_stopped` (`api.ts:219`, `tracking.ts:88`). `initial_state` tracks are
  `EnrichedTrack`, which extends `Track` with `userId` (`shared/types.ts:11`).
- `web/src/client/app.ts:597` already subscribes with a comma-split list from the `/:clientIds` path.

Only the mobile app is hardcoded to one ID:

- `LiveTrackingFollower` (`shared/.../data/LiveTrackingFollower.kt`) — `follow(clientId: String)`,
  `FollowState.Following(clientId, tracks)`, `FriendTrack` has no `userId`, and `updateGeoJson`
  stamps the *followed* id onto every feature (line ~283). Caps: `MAX_TRACKS = 50`,
  `MAX_POINTS_PER_TRACK = 10_000`.
- `UserPreferences` — `followedClientId: StateFlow<String?>` (line 109), key
  `FOLLOWED_CLIENT_ID = "followed_client_id"` (line 503), `updateFollowedClientId(value: String?)`
  (line 407). `followHistory: StateFlow<List<String>>` already exists, comma-joined under
  `FOLLOW_HISTORY`, capped by `MAX_FOLLOW_HISTORY = 5`.
- Rendering hardcodes one color: `MapRenderUtils.FRIEND_TRACK_COLOR = "#8D6E63"`
  (`shared/src/androidMain/.../MapRenderUtils.kt:45`), and iOS takes it as a parameter —
  `MapViewProvider.updateFriendTrackLine(geoJson, color)`, called with the literal `"#8D6E63"` in
  `IosMapScreen.kt:887`. `FriendTrack.color` is parsed from the server and then thrown away.
- UI: `FollowingFriendBanner(clientId, isConnecting, isActive, onClick, onClose)`
  (`MapOverlays.kt:608`, used at `:1068`); settings section in
  `OnlineTrackingScreenContent.kt:300-390` (one `OutlinedTextField` + Follow button, or a
  "Following X" row + "Stop Following" button, plus a follow-history chip row).
- Callers: `MapScreen.kt:142-153` (auto-follow), `:203-215` (zoom on first data),
  `:660-671` (banner wiring); iOS mirrors at `IosMapScreen.kt:143-167`, `:859-870`;
  settings at `OnlineTrackingScreenViewModel.kt` (Android) and `IosApp.kt:573-643` (iOS).
- Self-follow is already blocked (`OnlineTrackingScreenViewModel.startFollowing()`,
  `IosApp.kt:630`). Keep that check per added ID.

**Precedent to copy for per-track color:** multi-track rendering is already data-driven.
`MapGeoJson.buildTracksGeoJson` emits `color`/`width`/`opacity` per feature
(`MapGeoJson.kt:100-110`); Android reads `Expression.get("color")` (`MapRenderUtils.kt:73-75`) and
iOS reads `NSExpression(forKeyPath: "color")` (`MapViewFactory.swift:629-631`) — hex strings in
feature properties, working on both platforms today. The friend layer just needs the same treatment.
Palette: `TrackColors.palette` (10 colors) with `forId(id)` for a stable pick.

## Design decisions

1. **One WebSocket, many IDs.** Keep a single session; `follow(ids)` sends one `subscribe` with the
   full list. Changing the set re-sends `subscribe` on the same socket (server replaces `ws.data`
   and resends `initial_state`, which `handleInitialState` already treats as "clear and refill").
2. **Attribute tracks by `userId`.** `FriendTrack` gains `userId: String`; read it from
   `initial_state` tracks and from `track_started` / `track_update`. This is what makes per-person
   color and labels possible, and it fixes the existing bug where all features are labelled with the
   followed id.
3. **Color per person, not per track.** `TrackColors.forId(userId)` so the same friend keeps the same
   color across their tracks and across app restarts. Prefer the server's `track.color` when set
   (that is the sharer's own choice), else the palette. Put the resolution in `commonMain` so both
   platforms and the banner agree.
4. **Persistence reuses the existing key.** `FOLLOWED_CLIENT_ID` becomes a comma-joined list; a
   stored single id parses as a one-element list, so no migration step and old builds still read the
   first entry. Cap at `MAX_FOLLOWED = 5`.
5. **Map chrome stays small.** The banner becomes a summary — "Following 3" with one colored dot per
   person, tap = fit bounds over everyone, X = stop following all. Per-person add/remove lives in
   the settings screen (where there is room), not in the banner. Alternative considered and rejected
   for now: one banner row per person, which eats vertical space on a phone and fights the existing
   banner stack in `TopOverlayState`.

## Steps

### 1. `UserPreferences`: single id → list

- `followedClientIds: StateFlow<List<String>>` replacing `followedClientId`.
- Load: `prefs[FOLLOWED_CLIENT_ID]?.split(",")?.filter { it.isNotBlank() } ?: emptyList()` — mirrors
  the `FOLLOW_HISTORY` line right below it.
- Writers: `addFollowedClientId(id)` (dedupe, cap at `MAX_FOLLOWED`), `removeFollowedClientId(id)`,
  `clearFollowedClientIds()`. Each persists the comma-joined value, removing the key when empty.

### 2. `LiveTrackingFollower`: multi-subscribe

- `follow(clientIds: List<String>)`; validate each against `CLIENT_ID_REGEX` and drop invalid ones.
  Empty list → `stopFollowing()`.
- `currentClientId: String?` → `currentClientIds: List<String>`; the identity checks in
  `connectWithRetry` / `handleMessage` / `emitState` compare the list (guard against a stale
  connection job emitting into a new set).
- `FollowState.Connecting(clientIds)`, `FollowState.Following(clientIds, tracks)`.
- `FriendTrack(userId, trackId, name, points, isActive, color)`; populate `userId` from the message
  (`initial_state` track `userId`, and the `userId` field on `track_started` / `track_update`).
  Drop tracks whose `userId` is missing or not in the followed set.
- Set changes: if a session is open, send a fresh `subscribe` frame instead of tearing the socket
  down. `initial_state` then rebuilds `tracks` wholesale, which is already the existing behaviour.
- `updateGeoJson`: per feature emit `"clientId":"<track.userId>"` and
  `"color":"<resolved hex>"` (line + endpoint + label share the color). Drop the single-point
  special case — the general loop already handles a one-point track's endpoint marker.
- Caps: make `MAX_TRACKS` per-client rather than global (e.g. `MAX_TRACKS_PER_CLIENT = 20`) so one
  chatty friend cannot starve the others out of the 50-track budget. 24h of history times 5 people
  is the new worst case for memory and draw cost.

### 3. Rendering: data-driven color on both platforms

- Android `MapRenderUtils.updateFriendTrackOnMap`: replace `FRIEND_TRACK_COLOR` in
  `lineColor` / `circleColor` / `textColor` with `Expression.get("color")`. Delete the constant.
  `textField(Expression.get("clientId"))` already exists and now shows the right person per point.
- iOS `applyFriendTrackLine`: drop the `color: String` parameter; use
  `NSExpression(forKeyPath: "color")` for `lineColor`, `circleColor`, `textColor` — same as
  `applyTracks`. Update `MapViewProvider.updateFriendTrackLine(geoJson)` (drop the param), the
  `pendingFriendTrackColor` field, the style-reload replay at `MapViewFactory.swift:592`, and the
  call in `IosMapScreen.kt:887`.
- Keep line width/opacity/dash identical so followed tracks still read as "someone else's live
  track" and not as a saved track. Note in both files that the styling constants stay in sync
  (per `CLAUDE.md`).

### 4. UI

**Banner** (`MapOverlays.kt`): `FollowingFriendBanner(clientIds: List<String>, colors: (String) -> String, …)`,
or simpler, take a small `FollowedFriend(clientId, color, isActive)` list built in `commonMain`.
Render `following_friends` ("Følger %1$d") with a colored dot per person when `size > 1`, and keep
the existing single-name text when `size == 1`. `showFriendBanner` becomes `clientIds.isNotEmpty()`.
Height is already measured via `onSizeChanged`, so the banner stack absorbs the extra row.

**Settings** (`OnlineTrackingScreenContent.kt`): the `if (followedClientId != null) … else …` branch
becomes: a list of followed IDs, each row showing a color swatch, the id, and a per-row remove
button; below it the existing text field + Follow button (hidden or disabled at `MAX_FOLLOWED`),
then the follow-history chips (filtered to exclude already-followed IDs). Keep "Stop Following"
as a clear-all at the bottom when the list is non-empty.

**Strings** (`values/strings.xml` + `values-nb/strings.xml`): add `following_friends` ("Following %1$d"
/ "Følger %1$d"), `follow_max_reached`, `remove_followed_friend` (content description). Reword
`follow_friend_description` to mention several friends.

### 5. Call sites

- `OnlineTrackingScreenViewModel`: `startFollowing()` → `addFollowedClientId` +
  `addFollowHistoryEntry` + `liveTrackingFollower.follow(newList)`; add
  `stopFollowing(clientId)` for a single removal (calls `follow(remaining)` or `stopFollowing()`
  when empty). Keep the self-follow guard.
- `IosApp.kt:573-643`: same changes inline (no ViewModel on iOS).
- `MapScreen.kt` / `IosMapScreen.kt`: auto-follow keys on the list; zoom-on-first-data and the
  banner tap already `flatMap { it.points }` over all tracks, so bounds-fitting is unchanged.
  Reset `hasZoomedToFriend` when the set changes (it already keys on the followed id).

### 6. Tests

`shared/src/commonTest/.../data/LiveTrackingFollowerTest.kt` — new; there is no coverage today.
The class talks to a `HttpClient` directly, so either extract the frame handling
(`handleMessage` + `updateGeoJson` over an injected clock-free state) into something testable or
inject a session factory. Cases worth covering:

- `initial_state` with tracks from three `userId`s → three lines, three labels, three colors
- `track_update` for an unknown track from a followed client → new track attributed correctly
- `track_update` for a `userId` that is not followed → ignored
- adding / removing an id mid-session → subscribe re-sent, tracks for the removed id gone
- per-client track cap
- one-point track still yields an endpoint marker

Run with `./gradlew :shared:testAndroidHostTest`.

### 7. Docs

- `README.md:20` — "Follow a friend's live track" → several friends.
- `web/src/client/guide.html` — the follow section mentions one ID; note the comma-separated form
  (the web viewer already supports it) and that the app now takes several.
- `RELEASES.md` — bullet under `## Unreleased`.

## Risks / notes

- **Color collisions.** `TrackColors.forId` is `hashCode`-based, so two followed IDs can land on the
  same palette entry. With a max of 5 followed, assign by index into the followed list instead
  (stable as long as the list order is stable), falling back to `forId` for tracks whose owner has
  since been removed.
- **Historical volume.** `historical: true` pulls 24h per client. Five clients means five times the
  points on first connect; the per-client cap in step 2 bounds it, but watch first-paint cost on
  older phones.
- **Stale `initial_state` on re-subscribe.** The server sends a full snapshot for the new list, and
  `handleInitialState` clears `tracks` first, so a removed friend disappears correctly — but any
  `track_update` frames already in flight for the removed id must be filtered by the followed-set
  check, not just by socket state.
- **Old builds.** The comma-joined `followed_client_id` value read by a downgraded build yields a
  single bogus 20-char id that fails `CLIENT_ID_REGEX`, so following silently stops rather than
  breaking. Acceptable; mention it if downgrade matters.
