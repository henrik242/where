package no.synth.where.ui.map

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import no.synth.where.data.CrosshairInfo
import no.synth.where.data.hasElevationData
import no.synth.where.data.PlaceSearchClient
import no.synth.where.data.RulerState
import no.synth.where.data.Track
import no.synth.where.data.TrackCropState
import no.synth.where.data.geo.CoordFormat
import no.synth.where.data.geo.CoordinateFormatter
import no.synth.where.data.geo.LatLng
import no.synth.where.data.geo.compassPoint8
import no.synth.where.data.navigation.NavigationProgress
import no.synth.where.resources.Res
import no.synth.where.resources.*
import no.synth.where.util.formatDistance
import no.synth.where.util.formatElevation
import androidx.compose.foundation.shape.RoundedCornerShape
import kotlin.math.roundToInt
import no.synth.where.util.parseHexColor
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun ZoomControls(
    modifier: Modifier = Modifier,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        SmallFloatingActionButton(
            onClick = onZoomIn,
            modifier = Modifier.size(48.dp),
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Icon(painterResource(Res.drawable.ic_add), contentDescription = stringResource(Res.string.zoom_in))
        }
        SmallFloatingActionButton(
            onClick = onZoomOut,
            modifier = Modifier.size(48.dp),
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Icon(painterResource(Res.drawable.ic_remove), contentDescription = stringResource(Res.string.zoom_out))
        }
    }
}

@Composable
fun RulerCard(
    modifier: Modifier = Modifier,
    rulerState: RulerState,
    onUndo: () -> Unit,
    onClear: () -> Unit,
    onSaveAsTrack: () -> Unit
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
        )
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    val totalDistance = rulerState.getTotalDistanceMeters()
                    Text(
                        text = if (rulerState.points.isEmpty()) stringResource(Res.string.tap_to_measure) else totalDistance.formatDistance(),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (rulerState.points.size > 1) {
                        Text(
                            text = stringResource(Res.string.n_points, rulerState.points.size),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (rulerState.points.size > 1) {
                        SmallFloatingActionButton(
                            onClick = onUndo,
                            modifier = Modifier.size(32.dp),
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Icon(
                                painterResource(Res.drawable.ic_undo),
                                contentDescription = stringResource(Res.string.remove_last_point),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    SmallFloatingActionButton(
                        onClick = onClear,
                        modifier = Modifier.size(32.dp),
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Icon(
                            painterResource(Res.drawable.ic_clear),
                            contentDescription = stringResource(Res.string.clear_all),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            if (rulerState.points.size >= 2) {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onSaveAsTrack,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        painterResource(Res.drawable.ic_save),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(Res.string.save_as_track))
                }
            }
        }
    }
}

@Composable
fun RecordingCard(
    modifier: Modifier = Modifier,
    distance: Double,
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.95f)
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                painterResource(Res.drawable.ic_fiber_manual_record),
                contentDescription = null,
                tint = Color.Red,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = stringResource(Res.string.recording_distance, distance.formatDistance()),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
        }
    }
}

@Composable
fun ViewingTrackBanner(
    modifier: Modifier = Modifier,
    trackName: String,
    trackColorHex: String? = null,
    onCloseTrack: () -> Unit,
    onCollapse: () -> Unit
) {
    var confirmClose by remember { mutableStateOf(false) }
    if (confirmClose) {
        MapDialogs.ConfirmCloseTrackDialog(
            onConfirm = {
                confirmClose = false
                onCloseTrack()
            },
            onDismiss = { confirmClose = false }
        )
    }
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
        )
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (trackColorHex != null) {
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .background(parseHexColor(trackColorHex), CircleShape)
                )
            } else {
                Icon(
                    painterResource(Res.drawable.ic_map),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            Text(
                text = trackName,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onCollapse, modifier = Modifier.size(36.dp)) {
                Icon(painterResource(Res.drawable.ic_expand_more), contentDescription = stringResource(Res.string.collapse))
            }
            IconButton(onClick = { confirmClose = true }, modifier = Modifier.size(36.dp)) {
                Icon(
                    painterResource(Res.drawable.ic_close),
                    contentDescription = stringResource(Res.string.close_track)
                )
            }
        }
    }
}

/** Top banner shown while cropping: title + Cancel (discard) and Save (overwrite the track in place). */
@Composable
fun TrackCropHeader(
    modifier: Modifier = Modifier,
    trackName: String,
    onCancel: () -> Unit,
    onSave: () -> Unit,
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
        )
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                painterResource(Res.drawable.ic_crop),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Text(
                text = stringResource(Res.string.crop_title, trackName),
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onCancel, modifier = Modifier.size(36.dp)) {
                Icon(
                    painterResource(Res.drawable.ic_close),
                    contentDescription = stringResource(Res.string.crop_cancel)
                )
            }
            IconButton(onClick = onSave, modifier = Modifier.size(36.dp)) {
                Icon(
                    painterResource(Res.drawable.ic_check),
                    contentDescription = stringResource(Res.string.crop_save),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
fun NavigationCard(
    modifier: Modifier = Modifier,
    progress: NavigationProgress?,
    onToggleReverse: () -> Unit,
    onStop: () -> Unit,
) {
    // Arrival takes priority over off-course: at the end we drop the error styling and connector.
    val offCourse = progress != null && !progress.onCourse && !progress.atEnd
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = if (offCourse)
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.95f)
            else MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
        )
    ) {
        val contentColor = if (offCourse) MaterialTheme.colorScheme.onErrorContainer
            else MaterialTheme.colorScheme.onSurface
        CompositionLocalProvider(LocalContentColor provides contentColor) {
            Column(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (progress == null) {
                    // Session started but no location fix yet: keep Stop reachable.
                    NavStatusRow(stringResource(Res.string.locating), onStop)
                } else if (progress.atEnd) {
                    NavStatusRow(stringResource(Res.string.nav_arrived), onStop)
                } else {
                    // Distance on the left, action buttons pinned to the right edge of the
                    // (full-width) card; off-course status and elevation sit on their own lines.
                    val ascent = progress.remainingAscent
                    val descent = progress.remainingDescent
                    if (offCourse) {
                        // Direction the user should head to get back to the nearest point on the route.
                        val direction = compassPoint8(
                            progress.location.bearingTo(progress.snapped),
                            stringResource(Res.string.compass_n),
                            stringResource(Res.string.compass_e),
                            stringResource(Res.string.compass_s),
                            stringResource(Res.string.compass_w)
                        )
                        val heading = stringResource(Res.string.nav_heading, direction)
                        Text(
                            stringResource(Res.string.nav_off_course, progress.offCourseMeters.formatDistance(), heading),
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stringResource(Res.string.nav_remaining, progress.remainingMeters.formatDistance()),
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1
                        )
                        Spacer(Modifier.weight(1f))
                        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            IconButton(onClick = onToggleReverse, modifier = Modifier.size(32.dp)) {
                                Icon(painterResource(Res.drawable.ic_u_turn),
                                    contentDescription = stringResource(Res.string.nav_reverse),
                                    modifier = Modifier.size(20.dp))
                            }
                            IconButton(onClick = onStop, modifier = Modifier.size(32.dp)) {
                                Icon(painterResource(Res.drawable.ic_close),
                                    contentDescription = stringResource(Res.string.nav_stop),
                                    modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                    if (ascent != null && descent != null) {
                        AscentDescent(ascent, descent)
                    }
                }
            }
        }
    }
}

@Composable
private fun NavStatusRow(text: String, onStop: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        IconButton(onClick = onStop) {
            Icon(painterResource(Res.drawable.ic_close),
                contentDescription = stringResource(Res.string.nav_stop))
        }
    }
}

@Composable
private fun AscentDescent(ascent: Double, descent: Double) {
    // Vertical meters, so use formatElevation (never km) — a "1.50 km" remaining-ascent reads wrong.
    val label = stringResource(Res.string.nav_ascent, ascent.formatElevation()) + ", " +
        stringResource(Res.string.nav_descent, descent.formatElevation())
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = label }
    ) {
        Icon(painterResource(Res.drawable.ic_expand_less), contentDescription = null,
            modifier = Modifier.size(16.dp))
        Text(ascent.formatElevation(), style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.width(6.dp))
        Icon(painterResource(Res.drawable.ic_expand_more), contentDescription = null,
            modifier = Modifier.size(16.dp))
        Text(descent.formatElevation(), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun ViewingPointBanner(
    modifier: Modifier = Modifier,
    pointName: String,
    pointColor: String,
    onClose: () -> Unit
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .background(
                        color = parseHexColor(pointColor),
                        shape = CircleShape
                    )
            )
            Text(
                text = pointName,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onClose) {
                Icon(painterResource(Res.drawable.ic_close), contentDescription = stringResource(Res.string.close_point_view))
            }
        }
    }
}

@Composable
fun FollowingFriendBanner(
    modifier: Modifier = Modifier,
    clientId: String,
    isConnecting: Boolean,
    isActive: Boolean,
    onClick: () -> Unit,
    onClose: () -> Unit
) {
    Card(
        modifier = modifier.clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.95f)
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                painterResource(Res.drawable.ic_visibility),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(Res.string.following_friend, clientId),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = when {
                        isConnecting -> stringResource(Res.string.following_connecting)
                        isActive -> stringResource(Res.string.following_live)
                        else -> stringResource(Res.string.following_no_tracks)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                )
            }
            IconButton(onClick = onClose) {
                Icon(
                    painterResource(Res.drawable.ic_close),
                    contentDescription = stringResource(Res.string.stop_following),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}

@Composable
fun CrosshairOverlay(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize().drawBehind {
            val cx = size.width / 2
            val cy = size.height / 2
            val center = Offset(cx, cy)
            val armLen = 20.dp.toPx()
            val gap = 9.5.dp.toPx()
            val dotRadius = 2.dp.toPx()
            val strokeWidth = 1.5.dp.toPx()
            val shadowWidth = strokeWidth + 2.dp.toPx()
            val shadowColor = Color.White.copy(alpha = 0.5f)
            val lineColor = Color.Black

            // Shadow
            drawLine(shadowColor, Offset(cx - armLen, cy), Offset(cx - gap, cy), strokeWidth = shadowWidth, cap = StrokeCap.Round)
            drawLine(shadowColor, Offset(cx + gap, cy), Offset(cx + armLen, cy), strokeWidth = shadowWidth, cap = StrokeCap.Round)
            drawLine(shadowColor, Offset(cx, cy - armLen), Offset(cx, cy - gap), strokeWidth = shadowWidth, cap = StrokeCap.Round)
            drawLine(shadowColor, Offset(cx, cy + gap), Offset(cx, cy + armLen), strokeWidth = shadowWidth, cap = StrokeCap.Round)
            drawCircle(shadowColor, dotRadius + 1.dp.toPx(), center)

            // Cross lines and dot
            drawLine(lineColor, Offset(cx - armLen, cy), Offset(cx - gap, cy), strokeWidth = strokeWidth, cap = StrokeCap.Round)
            drawLine(lineColor, Offset(cx + gap, cy), Offset(cx + armLen, cy), strokeWidth = strokeWidth, cap = StrokeCap.Round)
            drawLine(lineColor, Offset(cx, cy - armLen), Offset(cx, cy - gap), strokeWidth = strokeWidth, cap = StrokeCap.Round)
            drawLine(lineColor, Offset(cx, cy + gap), Offset(cx, cy + armLen), strokeWidth = strokeWidth, cap = StrokeCap.Round)
            drawCircle(lineColor, dotRadius, center)
        }
    )
}

@Composable
fun CrosshairInfoCard(
    modifier: Modifier = Modifier,
    centerLatLng: LatLng?,
    crosshairInfo: CrosshairInfo,
    coordFormat: CoordFormat,
    onToggleCoordFormat: () -> Unit,
    userLocation: LatLng? = null
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f)
        )
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            if (centerLatLng != null) {
                Text(
                    text = when (coordFormat) {
                        CoordFormat.UTM -> CoordinateFormatter.formatUtm(centerLatLng)
                        CoordFormat.MGRS -> CoordinateFormatter.formatMgrs(centerLatLng)
                        CoordFormat.LATLNG -> CoordinateFormatter.formatLatLng(centerLatLng)
                        CoordFormat.DMS -> CoordinateFormatter.formatDms(centerLatLng)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.clickable { onToggleCoordFormat() }
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                val hasData = crosshairInfo.elevation != null || crosshairInfo.slopeDegrees != null
                val elevText = when {
                    crosshairInfo.isLoading -> stringResource(Res.string.loading_dots)
                    crosshairInfo.elevation != null -> "\u2191 " + stringResource(
                        Res.string.elevation_format,
                        crosshairInfo.elevation.roundToInt().toString()
                    )
                    else -> stringResource(Res.string.no_data)
                }
                val slopeText = when {
                    crosshairInfo.isLoading -> stringResource(Res.string.loading_dots)
                    crosshairInfo.slopeDegrees != null -> stringResource(
                        Res.string.slope_format,
                        crosshairInfo.slopeDegrees.roundToInt().toString()
                    )
                    else -> stringResource(Res.string.no_data)
                }
                val dataStyle = if (hasData) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium
                val dataColor = if (hasData) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                Text(
                    text = elevText,
                    style = dataStyle,
                    color = dataColor
                )
                Text(
                    text = slopeText,
                    style = dataStyle,
                    color = dataColor
                )
            }
            if (userLocation != null && centerLatLng != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        painterResource(Res.drawable.ic_my_location),
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = centerLatLng.distanceTo(userLocation).formatDistance(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun BoxScope.MapOverlays(
    offlineModeEnabled: Boolean = false,
    isLocating: Boolean = false,
    crosshairActive: Boolean = false,
    crosshairInfo: CrosshairInfo = CrosshairInfo(),
    centerLatLng: LatLng? = null,
    userLocation: LatLng? = null,
    coordFormat: CoordFormat = CoordFormat.LATLNG,
    onToggleCoordFormat: () -> Unit = {},
    rulerState: RulerState,
    isRecording: Boolean,
    recordingDistance: Double?,
    onlineTrackingEnabled: Boolean,
    viewerCount: Int = 0,
    viewingTracks: List<Track> = emptyList(),
    focusedTrackId: String? = null,
    cropState: TrackCropState? = null,
    onCropChange: (Int, Int) -> Unit = { _, _ -> },
    onCancelCrop: () -> Unit = {},
    onApplyCrop: () -> Unit = {},
    elevationMarker: Int? = null,
    onElevationScrub: (Int?) -> Unit = {},
    onBottomChartHeightChanged: (Dp) -> Unit = {},
    navigation: NavigationUiState = NavigationUiState(),
    viewingPointName: String?,
    viewingPointColor: String,
    showSearch: Boolean,
    searchQuery: String,
    searchResults: List<PlaceSearchClient.SearchResult>,
    searchHistory: List<PlaceSearchClient.SearchResult> = emptyList(),
    isSearching: Boolean,
    showViewingPoint: Boolean,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    onRulerUndo: () -> Unit,
    onRulerClear: () -> Unit,
    onRulerSaveAsTrack: () -> Unit,
    onOnlineTrackingClick: () -> Unit = {},
    onCloseTrack: () -> Unit,
    onCollapseTrack: () -> Unit = {},
    onCloseViewingPoint: () -> Unit,
    onOfflineIndicatorClick: () -> Unit = {},
    onSearchQueryChange: (String) -> Unit,
    onSearchResultClick: (PlaceSearchClient.SearchResult) -> Unit,
    onSearchResultHover: (PlaceSearchClient.SearchResult?) -> Unit = {},
    onSearchClose: () -> Unit,
    followedClientId: String? = null,
    isFollowConnecting: Boolean = false,
    isFollowedTrackActive: Boolean = false,
    onFollowBannerClick: () -> Unit = {},
    onStopFollowing: () -> Unit = {},
    liveShareUntilMillis: Long = 0L,
    isLiveSharing: Boolean = false
) {
    // The track whose name banner + altitude chart are shown, or null when nothing is focused.
    val focusedTrack = viewingTracks.firstOrNull { it.id == focusedTrackId }
    // Non-null only while the focused track is being cropped; the header + crop chart then replace
    // the banner + read-only chart.
    val activeCrop = focusedTrack?.let { ft -> cropState?.takeIf { it.trackId == ft.id } }
    val focusedTrackColor = viewingTracks.indexOfFirst { it.id == focusedTrackId }
        .takeIf { it >= 0 }?.let { TrackColors.forIndex(it) }
    val topOverlay = topOverlayState(
        showSearch = showSearch,
        hasFocusedTrack = focusedTrack != null,
        hasViewingPoint = showViewingPoint && viewingPointName != null,
        isFollowing = followedClientId != null,
        isNavigating = navigation.isNavigating,
    )
    val hideCornerControls = topOverlay.hidesCornerControls
    val hasTopOverlay = topOverlay.hidesTopCenter

    // The altitude chart is pinned bottom-center; when it is shown the bottom-left
    // cards (recording/ruler/crosshair) are lifted above its measured height so they
    // don't overlap it. Height is 0 for tracks without elevation, so no phantom gap.
    val density = LocalDensity.current
    // The crop chart is always shown while cropping (even without elevation), so the bottom-left
    // cards lift above it too.
    val chartVisible = activeCrop != null || focusedTrack?.hasElevationData() == true
    var chartHeight by remember { mutableStateOf(0.dp) }
    val bottomCardsOffset = if (chartVisible) chartHeight else 0.dp
    // Report the effective bottom-chart height (0 when no chart shows) so the screen can lift the
    // snackbar above it, keeping the crop-undo snackbar from covering the chart it refers to.
    LaunchedEffect(bottomCardsOffset) { onBottomChartHeightChanged(bottomCardsOffset) }

    // While navigating, the full-width NavigationCard occupies the top band, so the top-right chips
    // and any secondary top-center banner are pushed below it. navCardHeight is measured without the
    // card's 16dp top inset, so add it back plus an 8dp gap to land just under the card.
    var navCardHeight by remember { mutableStateOf(0.dp) }
    val belowNavCard = navCardHeight + 16.dp + 8.dp

    if (isLocating && !hasTopOverlay) {
        LocatingPill(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 16.dp)
        )
    }
    // The always-visible compass sits top-right; reserve space so the offline chip clears it.
    val offlineChipEnd = 56.dp

    AnimatedVisibility(
        visible = !hideCornerControls,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = Modifier.align(Alignment.TopStart)
    ) {
        ZoomControls(
            modifier = Modifier.padding(16.dp),
            onZoomIn = onZoomIn,
            onZoomOut = onZoomOut
        )
    }

    AnimatedVisibility(
        visible = !hideCornerControls,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = Modifier.align(Alignment.TopEnd)
    ) {
        Column(
            modifier = Modifier
                .padding(
                    top = if (navigation.isNavigating) belowNavCard else 16.dp,
                    end = offlineChipEnd
                ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.End
        ) {
            if (offlineModeEnabled) {
                Row(
                    modifier = Modifier
                        .background(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f),
                            shape = RoundedCornerShape(16.dp)
                        )
                        .clickable { onOfflineIndicatorClick() }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        painterResource(Res.drawable.ic_cloud_off),
                        contentDescription = stringResource(Res.string.offline_mode),
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = stringResource(Res.string.offline_mode),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (isLiveSharing || isRecording) {
                val chipEnabled = isLiveSharing ||
                    (onlineTrackingEnabled && !offlineModeEnabled)
                LiveSharingChip(
                    enabled = chipEnabled,
                    untilMillis = if (isLiveSharing) liveShareUntilMillis else null,
                    viewerCount = if (chipEnabled) viewerCount else 0,
                    onClick = onOnlineTrackingClick,
                )
            }
        }
    }

    if (rulerState.isActive || isRecording || crosshairActive) {
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 16.dp, end = 80.dp, bottom = 16.dp + bottomCardsOffset),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (crosshairActive) {
                CrosshairInfoCard(
                    centerLatLng = centerLatLng,
                    crosshairInfo = crosshairInfo,
                    coordFormat = coordFormat,
                    onToggleCoordFormat = onToggleCoordFormat,
                    userLocation = userLocation
                )
            }
            if (rulerState.isActive) {
                RulerCard(
                    rulerState = rulerState,
                    onUndo = onRulerUndo,
                    onClear = onRulerClear,
                    onSaveAsTrack = onRulerSaveAsTrack
                )
            }
            if (isRecording && recordingDistance != null) {
                RecordingCard(distance = recordingDistance)
            }
        }
    }

    if (navigation.isNavigating) {
        // Full width, but inset on the left to clear the top-left zoom controls
        // (16dp inset + 48dp button + 8dp gap = 72dp), so the card fills the available
        // width instead of floating narrow in the center.
        NavigationCard(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(top = 16.dp)
                .padding(start = 72.dp, end = 16.dp)
                .onSizeChanged { navCardHeight = with(density) { it.height.toDp() } },
            progress = navigation.progress,
            onToggleReverse = navigation.onToggleReverse,
            onStop = navigation.onStop,
        )
    } else if (focusedTrack != null) {
        if (activeCrop != null) {
            TrackCropHeader(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 16.dp)
                    .padding(horizontal = 16.dp),
                trackName = focusedTrack.name,
                onCancel = onCancelCrop,
                onSave = onApplyCrop
            )
        } else {
            ViewingTrackBanner(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 16.dp)
                    .padding(horizontal = 16.dp),
                trackName = focusedTrack.name,
                trackColorHex = focusedTrackColor,
                onCloseTrack = onCloseTrack,
                onCollapse = onCollapseTrack
            )
        }
    }

    if (showViewingPoint && viewingPointName != null) {
        ViewingPointBanner(
            modifier = Modifier
                .align(Alignment.TopCenter)
                // Sits under the NavigationCard while navigating so the two don't overlap.
                .padding(top = if (navigation.isNavigating) belowNavCard else 16.dp)
                .padding(horizontal = 16.dp),
            pointName = viewingPointName,
            pointColor = viewingPointColor,
            onClose = onCloseViewingPoint
        )
    }

    if (followedClientId != null && !showSearch) {
        val hasOtherBanner = focusedTrack != null || (showViewingPoint && viewingPointName != null)
        // Stack below the NavigationCard while navigating, and a further step down when a point
        // banner also shows (its ~64dp height), so the friend banner never lands on either.
        val friendBannerTop = when {
            navigation.isNavigating && hasOtherBanner -> belowNavCard + 64.dp
            navigation.isNavigating -> belowNavCard
            hasOtherBanner -> 80.dp
            else -> 16.dp
        }
        FollowingFriendBanner(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = friendBannerTop)
                .padding(horizontal = 16.dp),
            clientId = followedClientId,
            isConnecting = isFollowConnecting,
            isActive = isFollowedTrackActive,
            onClick = onFollowBannerClick,
            onClose = onStopFollowing
        )
    }

    if (showSearch) {
        val searchFocusRequester = remember { FocusRequester() }
        LaunchedEffect(Unit) {
            searchFocusRequester.requestFocus()
        }
        SearchOverlay(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 16.dp)
                .padding(start = 16.dp, end = 16.dp)
                .fillMaxWidth(),
            query = searchQuery,
            onQueryChange = onSearchQueryChange,
            isSearching = isSearching,
            results = searchResults,
            searchHistory = searchHistory,
            focusRequester = searchFocusRequester,
            onResultClick = onSearchResultClick,
            onResultHover = onSearchResultHover,
            onClose = onSearchClose
        )
    }

    if (crosshairActive) {
        CrosshairOverlay()
    }

    if (focusedTrack != null) {
        if (activeCrop != null) {
            TrackCropChart(
                track = focusedTrack,
                startIndex = activeCrop.startIndex,
                endIndex = activeCrop.endIndex,
                onCropChange = onCropChange,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .onSizeChanged { chartHeight = with(density) { it.height.toDp() } }
            )
        } else {
            TrackAltitudeChart(
                track = focusedTrack,
                onScrub = onElevationScrub,
                markerIndex = elevationMarker,
                markerColorHex = focusedTrackColor,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .onSizeChanged { chartHeight = with(density) { it.height.toDp() } }
            )
        }
    }
}

@Composable
private fun LocatingPill(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.92f),
                shape = RoundedCornerShape(16.dp)
            )
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = stringResource(Res.string.locating),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
