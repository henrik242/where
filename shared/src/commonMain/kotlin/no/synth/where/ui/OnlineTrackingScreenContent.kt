package no.synth.where.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import no.synth.where.resources.Res
import no.synth.where.resources.*
import no.synth.where.util.currentTimeMillis
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnlineTrackingScreenContent(
    isTrackingEnabled: Boolean,
    clientId: String,
    viewerCount: Int = 0,
    showRegenerateDialog: Boolean,
    showTrackingInfoDialog: Boolean,
    onBackClick: () -> Unit,
    onToggleTracking: (Boolean) -> Unit,
    onViewOnWeb: () -> Unit,
    onShare: () -> Unit,
    onRegenerateClick: () -> Unit,
    onConfirmRegenerate: () -> Unit,
    onDismissRegenerate: () -> Unit,
    onConfirmTrackingInfo: () -> Unit,
    onDismissTrackingInfo: () -> Unit,
    liveShareUntilMillis: Long = 0L,
    onStartLiveShare: (Long) -> Unit = {},
    onStopLiveShare: () -> Unit = {},
    followedClientId: String? = null,
    followClientIdInput: String = "",
    followHistory: List<String> = emptyList(),
    onFollowClientIdChange: (String) -> Unit = {},
    onStartFollowing: () -> Unit = {},
    onStopFollowing: () -> Unit = {}
) {
    var showDurationDialog by remember { mutableStateOf(false) }
    var nowMillis by remember { mutableStateOf(currentTimeMillis()) }
    LaunchedEffect(liveShareUntilMillis) {
        while (liveShareUntilMillis > nowMillis) {
            delay(1000L)
            nowMillis = currentTimeMillis()
        }
    }
    val remainingMillis = (liveShareUntilMillis - nowMillis).coerceAtLeast(0L)
    val isLiveShareActive = remainingMillis > 0L
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.online_tracking)) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(painterResource(Res.drawable.ic_arrow_back), contentDescription = stringResource(Res.string.back))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (isTrackingEnabled) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    }
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(Res.string.enable_online_tracking),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (isTrackingEnabled) stringResource(Res.string.tracking_active) else stringResource(Res.string.tracking_disabled),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = isTrackingEnabled,
                        onCheckedChange = onToggleTracking
                    )
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth().alpha(if (isTrackingEnabled) 1f else 0.5f),
                colors = CardDefaults.cardColors(
                    containerColor = if (isLiveShareActive) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    }
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(Res.string.always_share_location),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = when {
                                    !isTrackingEnabled -> stringResource(Res.string.always_share_disabled_hint)
                                    isLiveShareActive -> stringResource(Res.string.always_share_remaining, formatRemaining(remainingMillis))
                                    else -> stringResource(Res.string.always_share_description)
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = isLiveShareActive,
                            enabled = isTrackingEnabled,
                            onCheckedChange = { checked ->
                                if (checked) {
                                    showDurationDialog = true
                                } else {
                                    onStopLiveShare()
                                }
                            }
                        )
                    }
                }
            }

            if (viewerCount > 0) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            painterResource(Res.drawable.ic_visibility),
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Text(
                            text = if (viewerCount == 1) {
                                stringResource(Res.string.viewers_watching)
                            } else {
                                stringResource(Res.string.viewers_watching_plural, viewerCount)
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = stringResource(Res.string.your_client_id),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = clientId,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(Res.string.client_id_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Text(
                text = stringResource(Res.string.actions),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Button(
                onClick = onViewOnWeb,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    painterResource(Res.drawable.ic_open_in_browser),
                    contentDescription = stringResource(Res.string.open_in_browser),
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(Res.string.view_on_web))
            }

            OutlinedButton(
                onClick = onShare,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    painterResource(Res.drawable.ic_share),
                    contentDescription = stringResource(Res.string.share),
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(Res.string.share_tracking_link))
            }

            OutlinedButton(
                onClick = onRegenerateClick,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    painterResource(Res.drawable.ic_refresh),
                    contentDescription = stringResource(Res.string.regenerate_id),
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(Res.string.regenerate_client_id))
            }

            HorizontalDivider()

            Text(
                text = stringResource(Res.string.follow_a_friend),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = stringResource(Res.string.follow_friend_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (followedClientId != null) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    text = stringResource(Res.string.following_friend, followedClientId),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        Button(
                            onClick = onStopFollowing,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Text(stringResource(Res.string.stop_following))
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            OutlinedTextField(
                                value = followClientIdInput,
                                onValueChange = { value ->
                                    if (value.length <= 6) {
                                        onFollowClientIdChange(value.lowercase().filter { it in 'a'..'z' || it in '0'..'9' })
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                label = { Text(stringResource(Res.string.enter_friend_client_id)) },
                                placeholder = { Text("abc123") },
                                singleLine = true
                            )
                            Button(
                                onClick = onStartFollowing,
                                modifier = Modifier.padding(top = 8.dp),
                                enabled = followClientIdInput.length == 6
                            ) {
                                Text(stringResource(Res.string.follow))
                            }
                        }
                        if (followHistory.isNotEmpty()) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                for (historyId in followHistory) {
                                    Text(
                                        text = historyId,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier
                                            .clickable { onFollowClientIdChange(historyId) }
                                            .padding(vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = stringResource(Res.string.how_it_works),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(Res.string.how_it_works_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
            }
        }
    }

    if (showTrackingInfoDialog) {
        AlertDialog(
            onDismissRequest = onDismissTrackingInfo,
            title = { Text(stringResource(Res.string.tracking_info_title)) },
            text = { Text(stringResource(Res.string.tracking_info_message)) },
            confirmButton = {
                TextButton(onClick = onConfirmTrackingInfo) {
                    Text(stringResource(Res.string.got_it))
                }
            }
        )
    }

    if (showDurationDialog) {
        AlertDialog(
            onDismissRequest = { showDurationDialog = false },
            title = { Text(stringResource(Res.string.share_duration_title)) },
            text = {
                Column {
                    DurationOption(stringResource(Res.string.share_duration_15min)) {
                        showDurationDialog = false
                        onStartLiveShare(15.minutes.inWholeMilliseconds)
                    }
                    DurationOption(stringResource(Res.string.share_duration_1hour)) {
                        showDurationDialog = false
                        onStartLiveShare(1.hours.inWholeMilliseconds)
                    }
                    DurationOption(stringResource(Res.string.share_duration_4hours)) {
                        showDurationDialog = false
                        onStartLiveShare(4.hours.inWholeMilliseconds)
                    }
                    DurationOption(stringResource(Res.string.share_duration_8hours)) {
                        showDurationDialog = false
                        onStartLiveShare(8.hours.inWholeMilliseconds)
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showDurationDialog = false }) {
                    Text(stringResource(Res.string.cancel))
                }
            }
        )
    }

    if (showRegenerateDialog) {
        AlertDialog(
            onDismissRequest = onDismissRegenerate,
            title = { Text(stringResource(Res.string.regenerate_client_id_title)) },
            text = {
                Text(stringResource(Res.string.regenerate_client_id_message, clientId))
            },
            confirmButton = {
                TextButton(onClick = onConfirmRegenerate) {
                    Text(stringResource(Res.string.regenerate))
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissRegenerate) {
                    Text(stringResource(Res.string.cancel))
                }
            }
        )
    }
}

@Composable
private fun DurationOption(label: String, onClick: () -> Unit) {
    Text(
        text = label,
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 12.dp)
    )
}

