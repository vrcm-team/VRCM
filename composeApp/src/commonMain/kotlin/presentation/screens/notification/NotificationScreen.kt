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
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.automirrored.outlined.Reply
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.vrcmteam.vrcm.getAppPlatform
import io.github.vrcmteam.vrcm.core.extensions.capitalizeFirst
import io.github.vrcmteam.vrcm.core.shared.SharedFlowCentre
import io.github.vrcmteam.vrcm.network.api.attributes.NotificationType
import io.github.vrcmteam.vrcm.presentation.compoments.AImage
import io.github.vrcmteam.vrcm.presentation.compoments.ATooltipBox
import io.github.vrcmteam.vrcm.presentation.compoments.LocalSharedSuffixKey
import io.github.vrcmteam.vrcm.presentation.compoments.ToastText
import io.github.vrcmteam.vrcm.presentation.compoments.sharedBoundsBy
import io.github.vrcmteam.vrcm.presentation.extensions.enableIf
import io.github.vrcmteam.vrcm.presentation.extensions.ignoredFormat
import io.github.vrcmteam.vrcm.presentation.extensions.openUrl
import io.github.vrcmteam.vrcm.presentation.navigation.*
import io.github.vrcmteam.vrcm.presentation.screens.avatar.AvatarProfileScreen
import io.github.vrcmteam.vrcm.presentation.screens.avatar.data.AvatarProfileVo
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
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.Serializable
import org.koin.compose.koinInject
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

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
    bottomNavigationPadding: Dp = 0.dp,
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
    var externalLink by remember { mutableStateOf<NotificationActionTarget.External?>(null) }
    val platform = getAppPlatform()
    val scope = rememberCoroutineScope()
    val boopSuccess = strings.profileBoopSuccess
    val boopAlreadySent = strings.profileBoopAlreadySent
    val boopDisabled = strings.profileBoopDisabled
    val externalLinkFailed = strings.notificationExternalLinkFailed
    val onResponse: (NotificationItemData, NotificationItemData.ActionData) -> Unit = { item, action ->
        if (item.responseTarget(action) == NotificationResponseTarget.BOOP_USER_API) {
            boopReply = BoopReply(item, action)
        } else {
            model.respondToNotification(item, action, null, boopSuccess, boopAlreadySent, boopDisabled)
        }
    }
    val reply = boopReply
    val replySending = reply?.let { model.pendingAction(it.item) == it.action } == true
    LaunchedEffect(reply?.item?.identity, notifications) {
        if (reply != null && notifications.none { it.identity == reply.item.identity }) boopReply = null
    }

    Scaffold(
        modifier = modifier,
        containerColor = if (showTopBar) MaterialTheme.colorScheme.background else Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onBackground,
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
        val centerStateModifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(bottom = bottomNavigationPadding)
        when {
            model.isRefreshing && notifications.isEmpty() -> CenterState(
                centerStateModifier,
            ) { CircularProgressIndicator() }
            model.hasRefreshError && notifications.isEmpty() -> CenterState(
                centerStateModifier,
            ) {
                Text(strings.notificationRefreshFailed, textAlign = TextAlign.Center)
                TextButton(onClick = model::refreshAllNotification) { Text(strings.retry) }
            }
            notifications.isEmpty() -> CenterState(centerStateModifier) {
                Text(strings.homeNotificationEmpty, textAlign = TextAlign.Center)
            }
            else -> LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().navigationBarsPadding(),
                contentPadding = PaddingValues(
                    start = 12.dp,
                    top = padding.calculateTopPadding() + 12.dp,
                    end = 12.dp,
                    bottom = padding.calculateBottomPadding() + bottomNavigationPadding + 12.dp,
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
                items(notifications, key = { it.identity.stableKey }) { item ->
                    NotificationItem(
                        item = item,
                        loadingAction = model.pendingAction(item),
                        pending = model.isNotificationPending(item),
                        onRead = { model.markNotificationAsRead(item) },
                        onDelete = { model.deleteNotification(item) },
                        onResponse = onResponse,
                        onExternalLink = { externalLink = it },
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
    externalLink?.let { target ->
        AlertDialog(
            onDismissRequest = { externalLink = null },
            icon = { Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = null) },
            title = { Text(strings.notificationExternalLinkTitle) },
            text = {
                Text(strings.notificationExternalLinkMessage.replace("%s", target.host))
            },
            confirmButton = {
                Button(
                    onClick = {
                        externalLink = null
                        runCatching { platform.openUrl(target.url) }
                            .onFailure {
                                scope.launch {
                                    SharedFlowCentre.toastText.emit(
                                        ToastText.Error(externalLinkFailed),
                                    )
                                }
                            }
                    },
                ) {
                    Text(strings.officialLinkOpen)
                }
            },
            dismissButton = {
                TextButton(onClick = { externalLink = null }) {
                    Text(strings.cancel)
                }
            },
        )
    }
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
    onExternalLink: (NotificationActionTarget.External) -> Unit,
) {
    val identity = item.identity
    var expanded by remember(identity.stableKey) { mutableStateOf(false) }
    val isFriendRequest = item.type == NotificationType.FriendRequest.value
    val senderId = item.senderId.orEmpty()
    val groupId = item.groupId.orEmpty()
    val groupName = item.groupName.orEmpty()
    val navigator = LocalNavigator.currentOrThrow
    val sharedSuffixKey = rememberContainerTransformToken("notification:${identity.stableKey}:user:$senderId")
        ?: LocalSharedSuffixKey.current
    val openGroup = {
        if (groupId.isNotEmpty()) {
            navigator push GroupProfileScreen(GroupProfileVo(groupId = groupId, name = groupName))
        }
    }
    val headline = item.announcementTitle ?: item.title ?: item.groupName ?: item.message
    val boopReplyAction = item.boopReplyAction
    val ordinaryActions = item.displayActions.filter { action ->
        item.responseTarget(action) != NotificationResponseTarget.BOOP_USER_API
    }
    val openActionTarget: (NotificationActionTarget) -> Unit = { target ->
        when (target) {
            is NotificationActionTarget.User -> navigator push UserProfileScreen(
                UserProfileVo(id = target.id, profileImageUrl = item.imageUrl),
                sharedSuffixKey,
            )
            is NotificationActionTarget.Group -> navigator push GroupProfileScreen(
                GroupProfileVo(
                    groupId = target.id,
                    name = groupName.takeIf { groupId == target.id }.orEmpty(),
                ),
            )
            is NotificationActionTarget.World -> navigator push WorldProfileScreen(
                WorldProfileVo(worldId = target.id),
            )
            is NotificationActionTarget.Avatar -> navigator push AvatarProfileScreen(
                AvatarProfileVo(avatarId = target.id),
            )
            is NotificationActionTarget.External -> onExternalLink(target)
        }
    }
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
            Row(Modifier.fillMaxWidth().heightIn(min = 80.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
            if (ordinaryActions.isNotEmpty() || boopReplyAction != null || !item.seen || item.canDelete) {
                FlowRow(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    ordinaryActions.forEach { action ->
                        val isLink = action.type.equals("link", ignoreCase = true)
                        val actionTarget = if (isLink) item.actionTarget(action) else null
                        NotificationResponseButton(
                            item = item,
                            action = action,
                            loading = loadingAction == action,
                            enabled = !pending && (!isLink || actionTarget != null),
                            unavailableLink = isLink && actionTarget == null,
                            onClick = {
                                if (isLink) {
                                    if (!item.seen) onRead()
                                    actionTarget?.let(openActionTarget)
                                } else {
                                    onResponse(item, action)
                                }
                            },
                        )
                    }
                    if (boopReplyAction != null) {
                        val loading = loadingAction == boopReplyAction
                        IconButton(
                            enabled = !pending && senderId.isNotEmpty(),
                            onClick = { onResponse(item, boopReplyAction) },
                        ) {
                            if (loading) {
                                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.AutoMirrored.Outlined.Reply, strings.notificationReplyBoop)
                            }
                        }
                    }
                    if (!item.seen) IconButton(enabled = !pending, onClick = onRead) {
                        Icon(Icons.Outlined.MarkEmailRead, strings.notificationMarkRead)
                    }
                    if (item.canDelete) IconButton(enabled = !pending, onClick = onDelete) {
                        Icon(Icons.Default.DeleteOutline, strings.notificationDelete)
                    }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NotificationResponseButton(
    item: NotificationItemData,
    action: NotificationItemData.ActionData,
    loading: Boolean,
    enabled: Boolean,
    unavailableLink: Boolean,
    onClick: () -> Unit,
) {
    val label = notificationActionLabel(item, action)
    val button = @Composable {
        FilledTonalButton(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier.padding(start = 6.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Row(
                    Modifier.alpha(if (loading) 0f else 1f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(notificationActionIcon(action), contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(label, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                if (loading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            }
        }
    }
    if (unavailableLink) {
        ATooltipBox(tooltip = { Text(strings.notificationUnsupportedLink) }, content = button)
    } else {
        button()
    }
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
    action.type.equals("link", true) -> strings.officialLinkOpen
    else -> action.type.capitalizeFirst()
}

private fun notificationActionIcon(action: NotificationItemData.ActionData): ImageVector = when {
    action.type.equals("link", true) -> Icons.AutoMirrored.Outlined.OpenInNew
    action.type.equals("accept", true) || action.icon.equals("check", true) -> Icons.Outlined.Check
    action.type.equals("delete", true) -> Icons.Default.DeleteOutline
    action.type.equals("decline", true) || action.type.equals("hide", true) ||
        action.icon.equals("cancel", true) -> Icons.Outlined.Close
    action.type.equals("unsubscribe", true) || action.icon.equals("bell-slash", true) ->
        Icons.Outlined.NotificationsOff
    action.icon.equals("bell", true) -> Icons.Outlined.Notifications
    action.icon.equals("ban", true) -> Icons.Outlined.Block
    action.icon.equals("reply", true) -> Icons.AutoMirrored.Outlined.Reply
    else -> Icons.Outlined.Tag
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
