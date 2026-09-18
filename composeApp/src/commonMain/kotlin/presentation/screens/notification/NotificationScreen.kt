package io.github.vrcmteam.vrcm.presentation.screens.notification

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
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
import io.github.vrcmteam.vrcm.presentation.designsystem.*
import io.github.vrcmteam.vrcm.presentation.extensions.enableIf
import io.github.vrcmteam.vrcm.presentation.extensions.ignoredFormat
import io.github.vrcmteam.vrcm.presentation.extensions.openUrl
import io.github.vrcmteam.vrcm.presentation.navigation.*
import io.github.vrcmteam.vrcm.presentation.screens.avatar.AvatarProfileScreen
import io.github.vrcmteam.vrcm.presentation.screens.avatar.data.AvatarProfileVo
import io.github.vrcmteam.vrcm.presentation.screens.group.GroupProfileScreen
import io.github.vrcmteam.vrcm.presentation.screens.group.data.GroupProfileVo
import io.github.vrcmteam.vrcm.presentation.screens.gallery.GalleryPickerScreen
import io.github.vrcmteam.vrcm.presentation.screens.gallery.GallerySelectionSessionStore
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
    var externalLink by remember { mutableStateOf<NotificationActionTarget.External?>(null) }
    val platform = getAppPlatform()
    val scope = rememberCoroutineScope()
    val boopSuccess = strings.profileBoopSuccess
    val boopAlreadySent = strings.profileBoopAlreadySent
    val boopDisabled = strings.profileBoopDisabled
    val photoResponseSuccess = strings.notificationPhotoResponseSuccess
    val photoPreparationFailed = strings.notificationPhotoPreparationFailed
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

    AppScaffold(
        modifier = modifier,
        containerColor = if (showTopBar) AppTheme.colors.groupedBackground else Color.Transparent,
        contentColor = AppTheme.colors.label,
        contentWindowInsets = if (showTopBar) {
            WindowInsets.systemBars
        } else {
            WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)
        },
        topBar = {
            if (showTopBar) {
                AppNavBar(
                    title = { AppText(strings.notificationSectionInbox) },
                    navigationIcon = {
                        if (showBackButton) {
                            AppIconButton(onClick = { navigator.pop() }) {
                                AppIcon(AppIcons.ArrowBackIosNew, strings.notificationBack)
                            }
                        }
                    },
                    actions = {
                        AppIconButton(enabled = !model.isRefreshing, onClick = model::refreshAllNotification) {
                            if (model.isRefreshing) {
                                AppActivityIndicator(Modifier.size(20.dp))
                            } else {
                                AppIcon(AppIcons.Refresh, strings.notificationRefresh)
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
            ) { AppActivityIndicator() }
            model.hasRefreshError && notifications.isEmpty() -> CenterState(
                centerStateModifier,
            ) {
                AppText(strings.notificationRefreshFailed, textAlign = TextAlign.Center)
                AppButton(onClick = model::refreshAllNotification, style = AppButtonStyle.Plain) { AppText(strings.retry) }
            }
            notifications.isEmpty() -> CenterState(centerStateModifier) {
                AppText(strings.homeNotificationEmpty, textAlign = TextAlign.Center)
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
                        AppText(
                            strings.notificationRefreshFailed,
                            Modifier.weight(1f),
                            color = AppTheme.colors.destructive,
                            style = AppTheme.type.caption1,
                        )
                        AppButton(onClick = model::refreshAllNotification, style = AppButtonStyle.Plain) { AppText(strings.retry) }
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
                        onResponse = onResponse,
                        onExternalLink = { externalLink = it },
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
    externalLink?.let { target ->
        AppAlert(
            onDismissRequest = { externalLink = null },
            icon = { AppIcon(AppIcons.OpenInNew, contentDescription = null) },
            title = { AppText(strings.notificationExternalLinkTitle) },
            text = {
                AppText(strings.notificationExternalLinkMessage.replace("%s", target.host))
            },
            confirmButton = {
                AppButton(
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
                    style = AppButtonStyle.Prominent,
                ) {
                    AppText(strings.officialLinkOpen)
                }
            },
            dismissButton = {
                AppButton(onClick = { externalLink = null }, style = AppButtonStyle.Plain) {
                    AppText(strings.cancel)
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

@OptIn(
    ExperimentalSharedTransitionApi::class,
    ExperimentalLayoutApi::class,
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
    onResponse: (NotificationItemData, NotificationItemData.ActionData) -> Unit,
    onExternalLink: (NotificationActionTarget.External) -> Unit,
    onPhotoReply: () -> Unit,
    onPhotoRetry: () -> Unit,
) {
    val identity = item.identity
    var expanded by remember(identity.stableKey) { mutableStateOf(false) }
    val isFriendRequest = item.type == NotificationType.FriendRequest.value
    val isBoop = item.type.equals("boop", ignoreCase = true)
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
    val isGroupInvite = item.isGroupInvite
    val headline = if (isGroupInvite) {
        groupName.takeIf(String::isNotBlank)
            ?: item.title?.takeIf(String::isNotBlank)
            ?: item.message
    } else {
        item.announcementTitle ?: item.title ?: item.groupName ?: item.message
    }
    val boopReplyAction = item.boopReplyAction
    val ordinaryActions = item.responseActionsForDisplay.filter { action ->
        if (item.responseTarget(action) == NotificationResponseTarget.BOOP_USER_API) return@filter false
        if (item.canDelete && action.type.equals("delete", ignoreCase = true)) return@filter false
        val actionTarget = item.actionTarget(action)
        actionTarget == null || actionTarget is NotificationActionTarget.External
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
    val profileTarget = item.displayActions.asSequence()
        .mapNotNull(item::actionTarget)
        .firstOrNull { it !is NotificationActionTarget.External }
        ?: groupId.takeIf(String::isNotEmpty)?.let { NotificationActionTarget.Group(it) }
        ?: senderId.takeIf(String::isNotEmpty)?.let { NotificationActionTarget.User(it) }
    val profileUserId = (profileTarget as? NotificationActionTarget.User)?.id
    Box(
        Modifier.fillMaxWidth().animateItem().clip(AppShapes.l)
            // 未读靠标题前的强调色圆点区分（Apple 列表的做法），卡片底色保持一致
            .background(AppTheme.colors.secondaryGroupedBackground)
            .clickable(enabled = !pending) {
                expanded = !expanded
                if (!item.seen) onRead()
            },
    ) {
        Column(Modifier.fillMaxWidth().padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth().heightIn(min = 80.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AImage(
                    modifier = Modifier
                        .enableIf(profileTarget != null) {
                            clickable(enabled = !pending) {
                                if (!item.seen) onRead()
                                profileTarget?.let(openActionTarget)
                            }
                        }
                        .enableIf(profileUserId != null) {
                            sharedBoundsBy(
                                key = "${profileUserId}UserIcon",
                                suffixKey = sharedSuffixKey,
                            )
                        }
                        .size(120.dp, 80.dp)
                        .background(AppTheme.colors.tertiaryGroupedBackground, AppShapes.m)
                        .clip(AppShapes.m),
                    imageData = item.imageUrl,
                )
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (!item.seen) Box(
                            Modifier.padding(end = 6.dp).size(8.dp).clip(AppShapes.xl)
                                .background(AppTheme.colors.tint),
                        )
                        AppText(
                            headline,
                            Modifier.weight(1f),
                            style = AppTheme.type.subheadlineEmphasized,
                            color = AppTheme.colors.tint,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (groupName.isNotEmpty() && !isGroupInvite) AppText(
                        groupName,
                        Modifier.enableIf(groupId.isNotEmpty()) { clickable(onClick = openGroup) },
                        style = AppTheme.type.caption1Emphasized,
                        color = AppTheme.colors.secondaryTint,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.weight(1f))
                    NotificationTypeLabel(item)
                    AppText(
                        remember(item.createdAt) {
                            @OptIn(ExperimentalTime::class)
                            runCatching {
                                Instant.parse(item.createdAt).toLocalDateTime(TimeZone.currentSystemDefault()).ignoredFormat
                            }.getOrDefault(item.createdAt)
                        },
                        style = AppTheme.type.caption2Emphasized,
                        color = AppTheme.colors.tertiaryLabel,
                    )
                }
            }
            if (
                ordinaryActions.isNotEmpty() || boopReplyAction != null ||
                item.supportsInvitePhotoResponse ||
                item.showStandaloneReadAction || item.canDelete
            ) {
                FlowRow(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (isBoop && item.showStandaloneReadAction) {
                        NotificationCommandButton(
                            label = strings.notificationAccept,
                            icon = AppIcons.Check,
                            loading = false,
                            enabled = !pending,
                            onClick = onRead,
                        )
                    }
                    if (boopReplyAction != null) {
                        NotificationResponseButton(
                            item = item,
                            action = boopReplyAction,
                            loading = loadingAction == boopReplyAction,
                            enabled = !pending && senderId.isNotEmpty(),
                            unavailableLink = false,
                            onClick = { onResponse(item, boopReplyAction) },
                        )
                    }
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
                    if (item.supportsInvitePhotoResponse) {
                        val photoLabel = when {
                            photoResponsePhase == InvitePhotoResponsePhase.PREPARING ->
                                strings.notificationPhotoPreparing
                            photoResponsePhase == InvitePhotoResponsePhase.RESPONDING ->
                                strings.notificationPhotoResponding
                            canRetryPhotoResponse -> strings.notificationRetryPhotoResponse
                            else -> strings.notificationReplyWithPhoto
                        }
                        ATooltipBox(tooltip = { AppText(photoLabel) }) {
                            AppIconButton(
                                enabled = !pending,
                                onClick = if (canRetryPhotoResponse) onPhotoRetry else onPhotoReply,
                            ) {
                                if (photoResponsePhase != null) {
                                    AppActivityIndicator(Modifier.size(20.dp))
                                } else {
                                    AppIcon(
                                        imageVector = if (canRetryPhotoResponse) {
                                            AppIcons.Refresh
                                        } else {
                                            AppIcons.AddPhoto
                                        },
                                        contentDescription = photoLabel,
                                    )
                                }
                            }
                        }
                    }
                    if (item.showStandaloneReadAction && !isBoop) AppIconButton(enabled = !pending, onClick = onRead) {
                        AppIcon(AppIcons.MarkRead, strings.notificationMarkRead)
                    }
                    if (item.canDelete) AppIconButton(enabled = !pending, onClick = onDelete) {
                        AppIcon(AppIcons.Delete, strings.notificationDelete)
                    }
                }
            }
            AnimatedVisibility(expanded) {
                AppText(
                    if (isFriendRequest) "${item.message} ${strings.notificationFriendRequest}" else item.message,
                    Modifier.fillMaxWidth()
                        .background(AppTheme.colors.tertiaryGroupedBackground, AppShapes.s)
                        .padding(8.dp),
                    style = AppTheme.type.caption1,
                    color = AppTheme.colors.secondaryLabel,
                )
            }
        }
    }
}

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
        NotificationCommandButton(
            label = label,
            icon = notificationActionIcon(action),
            loading = loading,
            enabled = enabled,
            onClick = onClick,
        )
    }
    if (unavailableLink) {
        ATooltipBox(tooltip = { AppText(strings.notificationUnsupportedLink) }, content = button)
    } else {
        button()
    }
}

@Composable
private fun NotificationCommandButton(
    label: String,
    icon: ImageVector,
    loading: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    AppButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.padding(start = 6.dp),
        style = AppButtonStyle.Tinted,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Row(
                Modifier.alpha(if (loading) 0f else 1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppIcon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
                AppText(label, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            if (loading) AppActivityIndicator(Modifier.size(18.dp))
        }
    }
}

@Composable
private fun notificationActionLabel(
    item: NotificationItemData,
    action: NotificationItemData.ActionData,
) = when {
    item.groupInviteActionKind(action) == GroupInviteActionKind.ACCEPT ->
        strings.notificationAccept
    item.groupInviteActionKind(action) == GroupInviteActionKind.IGNORE ->
        strings.notificationIgnore
    item.groupInviteActionKind(action) == GroupInviteActionKind.BLOCK ->
        strings.notificationBlock
    item.type == NotificationType.FriendRequest.value && action.type.equals("Accept", true) ->
        strings.notificationAccept
    item.type == NotificationType.FriendRequest.value -> strings.notificationIgnore
    item.responseTarget(action) == NotificationResponseTarget.BOOP_USER_API -> strings.notificationReply
    action.type.equals("accept", true) -> strings.notificationAccept
    action.type.equals("decline", true) -> strings.notificationDecline
    action.type.equals("delete", true) -> strings.notificationDelete
    action.type.equals("unsubscribe", true) -> strings.notificationUnsubscribe
    action.type.equals("hide", true) || action.type.equals("ignore", true) ||
        action.type.equals("reject", true) -> strings.notificationIgnore
    action.type.equals("block", true) || action.type.equals("ban", true) -> strings.notificationBlock
    action.type.equals("link", true) -> strings.officialLinkOpen
    action.type.equals("boop", true) || action.icon.equals("reply", true) -> strings.notificationReply
    action.label.isNotBlank() -> action.label
    else -> action.type.capitalizeFirst()
}

private fun notificationActionIcon(action: NotificationItemData.ActionData): ImageVector = when {
    action.type.equals("link", true) -> AppIcons.OpenInNew
    action.type.equals("accept", true) || action.icon.equals("check", true) -> AppIcons.Check
    action.type.equals("delete", true) -> AppIcons.Delete
    action.type.equals("decline", true) || action.type.equals("hide", true) ||
        action.icon.equals("cancel", true) -> AppIcons.Close
    action.type.equals("unsubscribe", true) || action.icon.equals("bell-slash", true) ->
        AppIcons.NotificationsOff
    action.icon.equals("bell", true) -> AppIcons.Notifications
    action.type.equals("block", true) || action.type.equals("ban", true) ||
        action.icon.equals("ban", true) -> AppIcons.Block
    action.type.equals("boop", true) || action.icon.equals("reply", true) -> AppIcons.Reply
    else -> AppIcons.Tag
}

@Composable
private fun NotificationTypeLabel(item: NotificationItemData) {
    if (item.type.equals("boop", true)) Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(boopIcon(item.boopEmojiId), strings.profileBoop, Modifier.size(16.dp))
        AppText(strings.profileBoop, style = AppTheme.type.caption2Emphasized)
    } else AppText(
        when {
            item.type == NotificationType.FriendRequest.value -> strings.notificationFriendRequestAlert
            item.isGroupInvite -> strings.notificationGroupInvite
            isGroupNotificationType(item.type) -> strings.notificationGroupAnnouncement
            else -> item.type
        },
        style = AppTheme.type.caption2Emphasized,
    )
}

private fun boopIcon(emojiId: String?) = when (emojiId?.lowercase()) {
    "default_heart" -> AppIcons.FavoriteBorder
    "default_hand_wave" -> AppIcons.Wave
    "default_laugh" -> AppIcons.FaceLaugh
    "default_thumbs_up" -> AppIcons.ThumbUp
    "default_thinking" -> AppIcons.FaceThinking
    "default_wow" -> AppIcons.FaceSurprised
    "default_angry" -> AppIcons.FaceAngry
    else -> AppIcons.Tap
}
