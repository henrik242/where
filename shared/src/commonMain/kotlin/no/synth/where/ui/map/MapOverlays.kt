package no.synth.where.ui.map

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import no.synth.where.data.PlaceSearchClient
import no.synth.where.data.RulerState
import no.synth.where.resources.Res
import no.synth.where.resources.*
import no.synth.where.util.formatDistance
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
    onOnlineTrackingChange: (Boolean) -> Unit
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
                modifier = Modifier.fillMaxWidth(),
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
fun BoxScope.MapOverlays(
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
    isSearching: Boolean,
    showViewingPoint: Boolean,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    onRulerUndo: () -> Unit,
    onRulerClear: () -> Unit,
    onRulerSaveAsTrack: () -> Unit,
    onOnlineTrackingChange: (Boolean) -> Unit,
    onCloseViewingTrack: () -> Unit,
    onCloseViewingPoint: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onSearchResultClick: (PlaceSearchClient.SearchResult) -> Unit,
    onSearchClose: () -> Unit
) {
    val hasTopOverlay = showSearch || viewingTrackName != null || (showViewingPoint && viewingPointName != null)

    if (!hasTopOverlay) {
        ZoomControls(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp),
            onZoomIn = onZoomIn,
            onZoomOut = onZoomOut
        )
    }

    if (rulerState.isActive || isRecording) {
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 16.dp, end = 80.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
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
                    onOnlineTrackingChange = onOnlineTrackingChange
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
            focusRequester = searchFocusRequester,
            onResultClick = onSearchResultClick,
            onClose = onSearchClose
        )
    }
}
