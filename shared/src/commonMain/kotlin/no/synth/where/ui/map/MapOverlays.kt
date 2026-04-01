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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import no.synth.where.data.CrosshairInfo
import no.synth.where.data.PlaceSearchClient
import no.synth.where.data.RulerState
import no.synth.where.data.geo.CoordFormat
import no.synth.where.data.geo.CoordinateFormatter
import no.synth.where.data.geo.LatLng
import no.synth.where.resources.Res
import no.synth.where.resources.*
import no.synth.where.util.formatDistance
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
    onlineTrackingEnabled: Boolean,
    onOnlineTrackingChange: (Boolean) -> Unit,
    onOnlineTrackingClick: () -> Unit = {}
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.95f)
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    painterResource(Res.drawable.ic_fiber_manual_record),
                    contentDescription = null,
                    tint = Color.Red
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(Res.string.recording),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Text(
                        text = distance.formatDistance(),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 8.dp),
                color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.2f)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOnlineTrackingClick() },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        painterResource(Res.drawable.ic_cloud_upload),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = stringResource(Res.string.online_tracking),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
                Switch(
                    checked = onlineTrackingEnabled,
                    onCheckedChange = onOnlineTrackingChange
                )
            }
        }
    }
}

@Composable
fun ViewingTrackBanner(
    modifier: Modifier = Modifier,
    trackName: String,
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
            Icon(
                painterResource(Res.drawable.ic_map),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Text(
                text = trackName,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onClose) {
                Icon(painterResource(Res.drawable.ic_close), contentDescription = stringResource(Res.string.close_track_view))
            }
        }
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
    isCompassVisible: Boolean = false,
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
    viewingTrackName: String?,
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
    onOnlineTrackingChange: (Boolean) -> Unit,
    onOnlineTrackingClick: () -> Unit = {},
    onCloseViewingTrack: () -> Unit,
    onCloseViewingPoint: () -> Unit,
    onOfflineIndicatorClick: () -> Unit = {},
    onSearchQueryChange: (String) -> Unit,
    onSearchResultClick: (PlaceSearchClient.SearchResult) -> Unit,
    onSearchResultHover: (PlaceSearchClient.SearchResult?) -> Unit = {},
    onSearchClose: () -> Unit
) {
    val hasTopOverlay = showSearch || viewingTrackName != null || (showViewingPoint && viewingPointName != null)
    val offlineChipEnd by animateDpAsState(
        targetValue = if (isCompassVisible) 56.dp else 16.dp,
        label = "offlineChipEnd"
    )

    if (!hasTopOverlay) {
        ZoomControls(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp),
            onZoomIn = onZoomIn,
            onZoomOut = onZoomOut
        )

        if (offlineModeEnabled) {
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 16.dp, end = offlineChipEnd)
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
    }

    if (rulerState.isActive || isRecording || crosshairActive) {
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 16.dp, end = 80.dp, bottom = 16.dp),
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
                RecordingCard(
                    distance = recordingDistance,
                    onlineTrackingEnabled = onlineTrackingEnabled,
                    onOnlineTrackingChange = onOnlineTrackingChange,
                    onOnlineTrackingClick = onOnlineTrackingClick
                )
            }
        }
    }

    if (viewingTrackName != null) {
        ViewingTrackBanner(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 16.dp)
                .padding(horizontal = 16.dp),
            trackName = viewingTrackName,
            onClose = onCloseViewingTrack
        )
    }

    if (showViewingPoint && viewingPointName != null) {
        ViewingPointBanner(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 16.dp)
                .padding(horizontal = 16.dp),
            pointName = viewingPointName,
            pointColor = viewingPointColor,
            onClose = onCloseViewingPoint
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
}
