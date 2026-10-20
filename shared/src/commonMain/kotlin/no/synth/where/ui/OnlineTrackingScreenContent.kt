package no.synth.where.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import no.synth.where.resources.Res
import no.synth.where.resources.*
import no.synth.where.data.LiveTrackingFollower.Companion.MAX_FOLLOWED
import no.synth.where.ui.map.FollowedFriend
import no.synth.where.ui.map.recentFollowIds
import no.synth.where.util.currentTimeMillis
import no.synth.where.util.parseHexColor
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
    offlineModeEnabled: Boolean = false,
    onDisableOfflineMode: () -> Unit = {},
    backgroundLocationMissing: Boolean = false,
    onOpenLocationSettings: () -> Unit = {},
    followedFriends: List<FollowedFriend> = emptyList(),
    followClientIdInput: String = "",
    followNicknameInput: String = "",
    followHistory: List<String> = emptyList(),
    clientNicknames: Map<String, String> = emptyMap(),
    onFollowClientIdChange: (String) -> Unit = {},
    onFollowNicknameChange: (String) -> Unit = {},
    onStartFollowing: () -> Unit = {},
    onFollow: (String) -> Unit = {},
    onUnfollow: (String) -> Unit = {},
    onSetNickname: (clientId: String, nickname: String) -> Unit = { _, _ -> },
    onForget: (String) -> Unit = {}
) {
    var showDurationDialog by remember { mutableStateOf(false) }
    // The client id whose nickname is being edited, or null when the rename dialog is closed.
    var nicknameEditId by remember { mutableStateOf<String?>(null) }
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

            // Both warnings cover the same trap: the countdown keeps ticking while the share
            // reaches nobody. Offline mode blocks every upload outright; without background
            // location (iOS "While Using the App") the fixes stop the moment you leave the app.
            if (isTrackingEnabled && offlineModeEnabled) {
                SharingWarningCard(
                    icon = painterResource(Res.drawable.ic_cloud_off),
                    text = stringResource(Res.string.sharing_blocked_offline),
                    actionLabel = stringResource(Res.string.turn_off_offline_mode),
                    onAction = onDisableOfflineMode,
                )
            }
            if (isLiveShareActive && backgroundLocationMissing) {
                SharingWarningCard(
                    icon = painterResource(Res.drawable.ic_my_location),
                    text = stringResource(Res.string.sharing_stops_in_background),
                    actionLabel = stringResource(Res.string.open_settings),
                    onAction = onOpenLocationSettings,
                )
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

                    if (followedFriends.size >= MAX_FOLLOWED) {
                        Text(
                            text = stringResource(Res.string.follow_max_reached, MAX_FOLLOWED),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
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
                        // Naming while adding saves a trip to the menu; shown once the id is complete.
                        if (followClientIdInput.length == 6) {
                            OutlinedTextField(
                                value = followNicknameInput,
                                onValueChange = { onFollowNicknameChange(it.take(MAX_NICKNAME_LENGTH)) },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text(stringResource(Res.string.nickname_optional)) },
                                singleLine = true
                            )
                        }
                    }

                    // Followed friends first, then recent and nicknamed ones, which can be renamed or
                    // followed again without typing the id.
                    val canFollowMore = followedFriends.size < MAX_FOLLOWED
                    for (friend in followedFriends) {
                        key(friend.clientId) {
                            FriendRow(
                                clientId = friend.clientId,
                                nickname = friend.nickname,
                                followed = friend,
                                canFollow = true,
                                onFollowButton = { onUnfollow(friend.clientId) },
                                onEditNickname = { nicknameEditId = friend.clientId },
                                onForget = { onForget(friend.clientId) }
                            )
                        }
                    }
                    for (id in recentFollowIds(followHistory, clientNicknames, followedFriends.map { it.clientId })) {
                        key(id) {
                            FriendRow(
                                clientId = id,
                                nickname = clientNicknames[id],
                                followed = null,
                                canFollow = canFollowMore,
                                onFollowButton = { onFollow(id) },
                                onEditNickname = { nicknameEditId = id },
                                onForget = { onForget(id) }
                            )
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

    nicknameEditId?.let { id ->
        NicknameDialog(
            clientId = id,
            nickname = clientNicknames[id],
            onSetNickname = onSetNickname,
            onDismiss = { nicknameEditId = null }
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

/** A share is running but cannot reach anyone: what is wrong, and the one tap that fixes it. */
@Composable
private fun SharingWarningCard(
    icon: Painter,
    text: String,
    actionLabel: String,
    onAction: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.onErrorContainer
                )
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
            TextButton(onClick = onAction, modifier = Modifier.align(Alignment.End)) {
                Text(text = actionLabel, color = MaterialTheme.colorScheme.onErrorContainer)
            }
        }
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

/** A known friend: [followed] when following them, else a recent or nicknamed id. */
@Composable
private fun FriendRow(
    clientId: String,
    nickname: String?,
    followed: FollowedFriend?,
    canFollow: Boolean,
    onFollowButton: () -> Unit,
    onEditNickname: () -> Unit,
    onForget: () -> Unit
) {
    val name = nickname ?: clientId
    val status = when {
        followed == null -> null
        followed.isActive -> stringResource(Res.string.following_live)
        else -> stringResource(Res.string.following_no_tracks)
    }
    // The id stays visible under a nickname, so it can still be read off to share or compare.
    val subtitle = listOfNotNull(clientId.takeIf { nickname != null }, status).joinToString(" \u00b7 ")
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                Modifier
                    .size(12.dp)
                    .then(
                        if (followed != null) {
                            Modifier.background(parseHexColor(followed.color), CircleShape)
                        } else {
                            Modifier.border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                        }
                    )
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (followed != null) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (subtitle.isNotEmpty()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        if (followed != null) {
            OutlinedButton(onClick = onFollowButton) {
                Text(stringResource(Res.string.unfollow))
            }
        } else {
            Button(onClick = onFollowButton, enabled = canFollow) {
                Text(stringResource(Res.string.follow))
            }
        }
        Box {
            IconButton(onClick = { menuOpen = true }) {
                Icon(
                    painterResource(Res.drawable.ic_more_vert),
                    contentDescription = stringResource(Res.string.friend_actions, name)
                )
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(nicknameActionLabel(nickname))) },
                    leadingIcon = { Icon(painterResource(Res.drawable.ic_edit), contentDescription = null) },
                    onClick = {
                        menuOpen = false
                        onEditNickname()
                    }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(Res.string.forget)) },
                    leadingIcon = { Icon(painterResource(Res.drawable.ic_delete), contentDescription = null) },
                    onClick = {
                        menuOpen = false
                        onForget()
                    }
                )
            }
        }
    }
}
