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
import androidx.compose.material.icons.automirrored.outlined.Reply
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.vrcmteam.vrcm.network.api.attributes.NotificationType
import io.github.vrcmteam.vrcm.presentation.compoments.AImage
import io.github.vrcmteam.vrcm.presentation.compoments.ATooltipBox
import io.github.vrcmteam.vrcm.presentation.compoments.LocalSharedSuffixKey
import io.github.vrcmteam.vrcm.presentation.compoments.sharedBoundsBy
import io.github.vrcmteam.vrcm.presentation.extensions.enableIf
import io.github.vrcmteam.vrcm.presentation.extensions.ignoredFormat
import io.github.vrcmteam.vrcm.presentation.navigation.*
import io.github.vrcmteam.vrcm.presentation.screens.group.GroupProfileScreen
import io.github.vrcmteam.vrcm.presentation.screens.group.data.GroupProfileVo
import io.github.vrcmteam.vrcm.presentation.screens.gallery.GalleryPickerScreen
import io.github.vrcmteam.vrcm.presentation.screens.gallery.GallerySelectionSessionStore
import io.github.vrcmteam.vrcm.presentation.screens.home.data.*
import io.github.vrcmteam.vrcm.presentation.screens.user.BoopSelectorDialog
import io.github.vrcmteam.vrcm.presentation.screens.user.UserProfileScreen
import io.github.vrcmteam.vrcm.presentation.screens.user.data.UserProfileVo
import io.github.vrcmteam.vrcm.presentation.settings.locale.strings
import io.github.vrcmteam.vrcm.presentation.supports.AppIcons
import io.github.vrcmteam.vrcm.service.isGroupNotificationType
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
    val gallerySessions = koinInject<GallerySelectionSessionStore>()
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
    val photoResponseSuccess = strings.notificationPhotoResponseSuccess
    val photoPreparationFailed = strings.notificationPhotoPreparationFailed
    val onBoopReply: (NotificationItemData, NotificationItemData.ActionData) -> Unit = { item, action ->
        boopReply = BoopReply(item, action)
    }
    val reply = boopReply
    val replySending = reply?.let { model.pendingAction(it.item) == it.action } == true
    LaunchedEffect(reply?.item?.identity, notifications) {
        if (reply != null && notifications.none { it.identity == reply.item.identity }) boopReply = null
    }

    var pendingPhotoGallerySession by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingPhotoTargetKey by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingPhotoSessionKey by rememberSaveable { mutableStateOf<String?>(null) }
    val currentSessionKey = model.currentSessionKey

    // Consume a Gallery result only for the account and notification that opened the picker.
    LaunchedEffect(pendingPhotoGallerySession, currentSessionKey, notifications) {
        val gallerySessionId = pendingPhotoGallerySession ?: return@LaunchedEffect
        if (pendingPhotoSessionKey != currentSessionKey) {
            gallerySessions.cancel(gallerySessionId)
            pendingPhotoGallerySession = null
            pendingPhotoTargetKey = null
            pendingPhotoSessionKey = null
            return@LaunchedEffect
        }
        val selection = gallerySessions.consume(gallerySessionId)
        if (selection != null) {
            notifications.firstOrNull { it.identity.stableKey == pendingPhotoTargetKey }
                ?.takeIf(NotificationItemData::supportsInvitePhotoResponse)
                ?.let { item ->
                    model.respondToInviteWithPhoto(
                        item = item,
                        selection = selection,
                        successMessage = photoResponseSuccess,
                        preparationFailedMessage = photoPreparationFailed,
                    )
                }
            pendingPhotoGallerySession = null
            pendingPhotoTargetKey = null
            pendingPhotoSessionKey = null
        } else if (!gallerySessions.isPending(gallerySessionId)) {
            pendingPhotoGallerySession = null
            pendingPhotoTargetKey = null
            pendingPhotoSessionKey = null
        }
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
                        photoResponsePhase = model.pendingPhotoResponsePhase(item),
                        canRetryPhotoResponse = model.failedPhotoResponse(item) != null,
                        onRead = { model.markNotificationAsRead(item) },
                        onDelete = { model.deleteNotification(item) },
                        onBoopReply = onBoopReply,
                        onPhotoReply = photoReply@{
                            if (pendingPhotoGallerySession != null) return@photoReply
                            val sessionKey = model.currentSessionKey ?: return@photoReply
                            val gallerySessionId = gallerySessions.create()
                            pendingPhotoGallerySession = gallerySessionId
                            pendingPhotoTargetKey = item.identity.stableKey
                            pendingPhotoSessionKey = sessionKey
                            navigator.push(GalleryPickerScreen(gallerySessionId))
                        },
                        onPhotoRetry = {
                            model.retryInvitePhotoResponse(
                                item = item,
                                successMessage = photoResponseSuccess,
                                preparationFailedMessage = photoPreparationFailed,
                            )
                        },
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

@OptIn(
    ExperimentalSharedTransitionApi::class,
    ExperimentalLayoutApi::class,
    ExperimentalMaterial3Api::class,
)
@Composable
private fun LazyItemScope.NotificationItem(
    item: NotificationItemData,
    loadingAction: NotificationItemData.ActionData?,
    pending: Boolean,
    photoResponsePhase: InvitePhotoResponsePhase?,
    canRetryPhotoResponse: Boolean,
    onRead: () -> Unit,
    onDelete: () -> Unit,
    onBoopReply: (NotificationItemData, NotificationItemData.ActionData) -> Unit,
    onPhotoReply: () -> Unit,
    onPhotoRetry: () -> Unit,
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
            if (
                boopReplyAction != null || item.supportsInvitePhotoResponse ||
                !item.seen || item.canDelete
            ) {
                FlowRow(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (boopReplyAction != null) {
                        val loading = loadingAction == boopReplyAction
                        IconButton(
                            enabled = !pending && senderId.isNotEmpty(),
                            onClick = { onBoopReply(item, boopReplyAction) },
                        ) {
                            if (loading) {
                                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.AutoMirrored.Outlined.Reply, strings.notificationReplyBoop)
                            }
                        }
                    }
                    if (item.supportsInvitePhotoResponse) {
                        val photoLabel = when {
                            photoResponsePhase == InvitePhotoResponsePhase.PREPARING ->
                                strings.notificationPhotoPreparing
                            photoResponsePhase == InvitePhotoResponsePhase.RESPONDING ->
                                strings.notificationPhotoResponding
                            canRetryPhotoResponse -> strings.notificationRetryPhotoResponse
                            else -> strings.notificationReplyWithPhoto
                        }
                        ATooltipBox(tooltip = { Text(photoLabel) }) {
                            IconButton(
                                enabled = !pending,
                                onClick = if (canRetryPhotoResponse) onPhotoRetry else onPhotoReply,
                            ) {
                                if (photoResponsePhase != null) {
                                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                                } else {
                                    Icon(
                                        imageVector = if (canRetryPhotoResponse) {
                                            Icons.Outlined.Refresh
                                        } else {
                                            Icons.Outlined.AddPhotoAlternate
                                        },
                                        contentDescription = photoLabel,
                                    )
                                }
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
