package io.github.vrcmteam.vrcm.presentation.screens.notification

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.vrcmteam.vrcm.core.extensions.capitalizeFirst
import io.github.vrcmteam.vrcm.network.api.attributes.NotificationType
import io.github.vrcmteam.vrcm.presentation.compoments.AImage
import io.github.vrcmteam.vrcm.presentation.compoments.LocalSharedSuffixKey
import io.github.vrcmteam.vrcm.presentation.compoments.sharedBoundsBy
import io.github.vrcmteam.vrcm.presentation.extensions.enableIf
import io.github.vrcmteam.vrcm.presentation.extensions.ignoredFormat
import io.github.vrcmteam.vrcm.presentation.navigation.*
import io.github.vrcmteam.vrcm.presentation.screens.group.GroupProfileScreen
import io.github.vrcmteam.vrcm.presentation.screens.group.data.GroupProfileVo
import io.github.vrcmteam.vrcm.presentation.screens.home.data.*
import io.github.vrcmteam.vrcm.presentation.screens.user.BoopSelectorDialog
import io.github.vrcmteam.vrcm.presentation.screens.user.UserProfileScreen
import io.github.vrcmteam.vrcm.presentation.screens.user.data.UserProfileVo
import io.github.vrcmteam.vrcm.presentation.screens.world.WorldProfileScreen
import io.github.vrcmteam.vrcm.presentation.screens.world.data.WorldProfileVo
import io.github.vrcmteam.vrcm.presentation.settings.locale.strings
import io.github.vrcmteam.vrcm.presentation.supports.AppIcons
import io.github.vrcmteam.vrcm.service.isGroupNotificationType
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.Serializable
import org.koin.compose.koinInject
import kotlin.time.ExperimentalTime

@Serializable
data class NotificationScreen(val targetNotificationId: String? = null) : AppDetailRoute {
    override val key = "NotificationScreen:${targetNotificationId.orEmpty()}"

    @Composable
    override fun Content() = NotificationCenterContent(
        targetNotificationId = targetNotificationId,
        showBackButton = true,
    )
}

/** Notification center UI shared by a root navigation destination and the detail route. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationCenterContent(
    modifier: Modifier = Modifier,
    targetNotificationId: String? = null,
    showBackButton: Boolean = false,
    showTopBar: Boolean = true,
) {
    val model = koinInject<NotificationCenterModel>()
    val navigator = LocalNavigator.currentOrThrow
    LaunchedEffect(Unit) { model.refreshAllNotification() }
    val notifications by remember {
        derivedStateOf {
            (model.friendRequestNotifications + model.notifications).sortedByDescending { it.createdAt }
        }
    }
    val listState = rememberLazyListState()
    var lastTargetListIndex by remember(targetNotificationId) { mutableIntStateOf(-1) }
    LaunchedEffect(targetNotificationId, notifications, model.hasRefreshError) {
        val targetIndex = notifications.indexOfNotificationTarget(targetNotificationId)
        val targetListIndex = notificationListIndex(targetIndex, model.hasRefreshError)
        if (targetIndex < 0 || targetListIndex == lastTargetListIndex) return@LaunchedEffect
        listState.animateScrollToItem(targetListIndex)
        lastTargetListIndex = targetListIndex
        notifications.getOrNull(targetIndex)?.let(model::markNotificationAsRead)
    }

    var boopReply by remember { mutableStateOf<BoopReply?>(null) }
    val boopSuccess = strings.profileBoopSuccess
    val boopAlreadySent = strings.profileBoopAlreadySent
    val boopDisabled = strings.profileBoopDisabled
    val onResponse: (NotificationItemData, NotificationItemData.ActionData) -> Unit = { item, action ->
        if (item.responseTarget(action) == NotificationResponseTarget.BOOP_USER_API) {
            boopReply = BoopReply(item, action)
        } else {
            model.respondToNotification(item, action, null, boopSuccess, boopAlreadySent, boopDisabled)
        }
    }
    val reply = boopReply
    val replySending = reply?.let { model.pendingNotificationActions[it.item.id] == it.action } == true
    LaunchedEffect(reply?.item?.id, notifications) {
        if (reply != null && notifications.none { it.id == reply.item.id }) boopReply = null
    }

    Scaffold(
        modifier = modifier,
        contentWindowInsets = if (showTopBar) {
            ScaffoldDefaults.contentWindowInsets
        } else {
            WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)
        },
        topBar = {
            if (showTopBar) {
                TopAppBar(
                    title = { Text(strings.notificationSectionInbox) },
                    navigationIcon = {
                        if (showBackButton) {
                            IconButton(onClick = { navigator.pop() }) {
                                Icon(AppIcons.ArrowBackIosNew, strings.notificationBack)
                            }
                        }
                    },
                    actions = {
                        IconButton(enabled = !model.isRefreshing, onClick = model::refreshAllNotification) {
                            if (model.isRefreshing) {
                                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(AppIcons.Update, strings.notificationRefresh)
                            }
                        }
                    },
                )
            }
        },
    ) { padding ->
        when {
            model.isRefreshing && notifications.isEmpty() -> CenterState(
                Modifier.fillMaxSize().padding(padding),
            ) { CircularProgressIndicator() }
            model.hasRefreshError && notifications.isEmpty() -> CenterState(
                Modifier.fillMaxSize().padding(padding),
            ) {
                Text(strings.notificationRefreshFailed, textAlign = TextAlign.Center)
                TextButton(onClick = model::refreshAllNotification) { Text(strings.retry) }
            }
            notifications.isEmpty() -> CenterState(Modifier.fillMaxSize().padding(padding)) {
                Text(strings.homeNotificationEmpty, textAlign = TextAlign.Center)
            }
            else -> LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().navigationBarsPadding(),
                contentPadding = PaddingValues(
                    start = 12.dp,
                    top = padding.calculateTopPadding() + 12.dp,
                    end = 12.dp,
                    bottom = padding.calculateBottomPadding() + 12.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (model.hasRefreshError) item(key = "refresh-error") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            strings.notificationRefreshFailed,
                            Modifier.weight(1f),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                        TextButton(onClick = model::refreshAllNotification) { Text(strings.retry) }
                    }
                }
                items(notifications, key = { "${it.source}:${it.id}" }) { item ->
                    NotificationItem(
                        item = item,
                        loadingAction = model.pendingNotificationActions[item.id],
                        pending = model.isNotificationPending(item.id),
                        onRead = { model.markNotificationAsRead(item) },
                        onDelete = { model.deleteNotification(item) },
                        onResponse = onResponse,
                    )
                }
            }
        }
    }
    BoopSelectorDialog(
        visible = reply != null,
        targetName = reply?.item?.title ?: reply?.item?.message.orEmpty(),
        sending = replySending,
        onDismiss = { boopReply = null },
        onSend = { emojiId ->
            reply?.let {
                model.respondToNotification(
                    it.item, it.action, emojiId, boopSuccess, boopAlreadySent, boopDisabled,
                )
            }
        },
    )
}

internal fun notificationListIndex(notificationIndex: Int, hasRefreshError: Boolean): Int =
    notificationIndex + if (hasRefreshError) 1 else 0

@Composable
private fun CenterState(modifier: Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
        content = content,
    )
}

private data class BoopReply(
    val item: NotificationItemData,
    val action: NotificationItemData.ActionData,
)

@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalLayoutApi::class)
@Composable
private fun LazyItemScope.NotificationItem(
    item: NotificationItemData,
    loadingAction: NotificationItemData.ActionData?,
    pending: Boolean,
    onRead: () -> Unit,
    onDelete: () -> Unit,
    onResponse: (NotificationItemData, NotificationItemData.ActionData) -> Unit,
) {
    var expanded by remember(item.source, item.id) { mutableStateOf(false) }
    val isFriendRequest = item.type == NotificationType.FriendRequest.value
    val senderId = item.senderId.orEmpty()
    val groupId = item.groupId.orEmpty()
    val groupName = item.groupName.orEmpty()
    val navigator = LocalNavigator.currentOrThrow
    val sharedSuffixKey = rememberContainerTransformToken("notification:${item.id}:user:$senderId")
        ?: LocalSharedSuffixKey.current
    val openGroup = {
        if (groupId.isNotEmpty()) {
            navigator push GroupProfileScreen(GroupProfileVo(groupId = groupId, name = groupName))
        }
    }
    val headline = item.announcementTitle ?: item.title ?: item.groupName ?: item.message
    Box(
        Modifier.fillMaxWidth().animateItem().clip(MaterialTheme.shapes.large)
            .background(
                if (item.seen) MaterialTheme.colorScheme.surface
                else MaterialTheme.colorScheme.secondaryContainer,
            )
            .clickable(enabled = !pending) {
                expanded = !expanded
                if (!item.seen) onRead()
            },
    ) {
        Column(Modifier.fillMaxWidth().padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth().height(80.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AImage(
                    modifier = Modifier
                        .enableIf(senderId.isNotEmpty() || groupId.isNotEmpty()) {
                            clickable {
                                if (groupId.isNotEmpty()) openGroup() else navigator push UserProfileScreen(
                                    UserProfileVo(id = senderId, profileImageUrl = item.imageUrl),
                                    sharedSuffixKey,
                                )
                            }
                        }
                        .enableIf(senderId.isNotEmpty() && groupId.isEmpty()) {
                            sharedBoundsBy(
                                key = "${senderId}UserIcon",
                                suffixKey = sharedSuffixKey,
                            )
                        }
                        .size(120.dp, 80.dp)
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest, MaterialTheme.shapes.medium)
                        .clip(MaterialTheme.shapes.medium),
                    imageData = item.imageUrl,
                )
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (!item.seen) Box(
                            Modifier.padding(end = 6.dp).size(8.dp).clip(MaterialTheme.shapes.extraLarge)
                                .background(MaterialTheme.colorScheme.primary),
                        )
                        Text(
                            headline,
                            Modifier.weight(1f),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (groupName.isNotEmpty()) Text(
                        groupName,
                        Modifier.enableIf(groupId.isNotEmpty()) { clickable(onClick = openGroup) },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.tertiary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.weight(1f))
                    NotificationTypeLabel(item)
                    Text(
                        remember(item.createdAt) {
                            @OptIn(ExperimentalTime::class)
                            runCatching {
                                Instant.parse(item.createdAt).toLocalDateTime(TimeZone.currentSystemDefault()).ignoredFormat
                            }.getOrDefault(item.createdAt)
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
            }
            FlowRow(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                item.actions.forEach { action ->
                    val loading = loadingAction == action
                    FilledTonalButton(
                        onClick = {
                            if (action.type.equals("link", true)) {
                                if (!item.seen) onRead()
                                when (val target = item.actionTarget(action)) {
                                    is NotificationActionTarget.User -> navigator push UserProfileScreen(
                                        UserProfileVo(id = target.id, profileImageUrl = item.imageUrl),
                                        sharedSuffixKey,
                                    )
                                    is NotificationActionTarget.Group -> navigator push GroupProfileScreen(
                                        GroupProfileVo(groupId = target.id),
                                    )
                                    is NotificationActionTarget.World -> navigator push WorldProfileScreen(
                                        WorldProfileVo(worldId = target.id),
                                    )
                                    null -> Unit
                                }
                            } else onResponse(item, action)
                        },
                        enabled = !pending,
                        modifier = Modifier.padding(start = 6.dp).animateContentSize(),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                notificationActionLabel(item, action),
                                Modifier.alpha(if (loading) 0f else 1f),
                            )
                            if (loading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        }
                    }
                }
                if (!item.seen) IconButton(enabled = !pending, onClick = onRead) {
                    Icon(Icons.Outlined.MarkEmailRead, strings.notificationMarkRead)
                }
                if (item.canDelete) IconButton(enabled = !pending, onClick = onDelete) {
                    Icon(Icons.Default.DeleteOutline, strings.notificationDelete)
                }
                IconButton(enabled = !pending, onClick = { expanded = !expanded }) {
                    Icon(
                        if (expanded) AppIcons.ExpandLess else AppIcons.ExpandMore,
                        if (expanded) strings.notificationCollapse else strings.notificationExpand,
                    )
                }
            }
            AnimatedVisibility(expanded) {
                Text(
                    if (isFriendRequest) "${item.message} ${strings.notificationFriendRequest}" else item.message,
                    Modifier.fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceContainer, MaterialTheme.shapes.small)
                        .padding(8.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun NotificationTypeLabel(item: NotificationItemData) {
    if (item.type.equals("boop", true)) Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(boopIcon(item.boopEmojiId), strings.profileBoop, Modifier.size(16.dp))
        Text(strings.profileBoop, style = MaterialTheme.typography.labelSmall)
    } else Text(
        when {
            item.type == NotificationType.FriendRequest.value -> strings.notificationFriendRequestAlert
            isGroupNotificationType(item.type) -> strings.notificationGroupAnnouncement
            else -> item.type
        },
        style = MaterialTheme.typography.labelSmall,
    )
}

@Composable
private fun notificationActionLabel(
    item: NotificationItemData,
    action: NotificationItemData.ActionData,
) = when {
    item.type == NotificationType.FriendRequest.value && action.type.equals("Accept", true) ->
        strings.notificationAccept
    item.type == NotificationType.FriendRequest.value -> strings.notificationIgnore
    action.label.isNotBlank() -> action.label
    else -> action.type.capitalizeFirst()
}

private fun boopIcon(emojiId: String?) = when (emojiId?.lowercase()) {
    "default_heart" -> Icons.Outlined.FavoriteBorder
    "default_hand_wave" -> Icons.Outlined.WavingHand
    "default_laugh" -> Icons.Outlined.SentimentVerySatisfied
    "default_thumbs_up" -> Icons.Outlined.ThumbUpOffAlt
    "default_thinking" -> Icons.Outlined.PsychologyAlt
    "default_wow" -> Icons.Outlined.EmojiEmotions
    "default_angry" -> Icons.Outlined.SentimentDissatisfied
    else -> Icons.Outlined.TouchApp
}
