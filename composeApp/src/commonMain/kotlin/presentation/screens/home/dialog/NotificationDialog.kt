package io.github.vrcmteam.vrcm.presentation.screens.home.dialog

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.EmojiEmotions
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.PsychologyAlt
import androidx.compose.material.icons.outlined.SentimentDissatisfied
import androidx.compose.material.icons.outlined.SentimentVerySatisfied
import androidx.compose.material.icons.outlined.ThumbUpOffAlt
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material.icons.outlined.WavingHand
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.vrcmteam.vrcm.presentation.navigation.LocalNavigator
import io.github.vrcmteam.vrcm.presentation.navigation.currentOrThrow
import io.github.vrcmteam.vrcm.presentation.navigation.rememberContainerTransformToken
import io.github.vrcmteam.vrcm.core.extensions.capitalizeFirst
import io.github.vrcmteam.vrcm.network.api.attributes.NotificationType
import io.github.vrcmteam.vrcm.presentation.compoments.AImage
import io.github.vrcmteam.vrcm.presentation.compoments.LocalSharedSuffixKey
import io.github.vrcmteam.vrcm.presentation.compoments.SharedDialog
import io.github.vrcmteam.vrcm.presentation.compoments.SharedDialogContainer
import io.github.vrcmteam.vrcm.presentation.compoments.sharedBoundsBy
import io.github.vrcmteam.vrcm.presentation.extensions.enableIf
import io.github.vrcmteam.vrcm.presentation.extensions.ignoredFormat
import io.github.vrcmteam.vrcm.presentation.screens.home.HomeScreenModel
import org.koin.compose.viewmodel.koinViewModel
import io.github.vrcmteam.vrcm.presentation.screens.home.data.NotificationItemData
import io.github.vrcmteam.vrcm.presentation.screens.user.UserProfileScreen
import io.github.vrcmteam.vrcm.presentation.screens.user.data.UserProfileVo
import io.github.vrcmteam.vrcm.presentation.settings.locale.strings
import io.github.vrcmteam.vrcm.presentation.supports.AppIcons
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.ExperimentalTime

object NotificationDialog : SharedDialog {

    @Composable
    override fun Content(animatedVisibilityScope: AnimatedVisibilityScope) {
        val homeScreenModel: HomeScreenModel = koinViewModel()
        // 每打开一次刷新一次
        LaunchedEffect(Unit) {
            homeScreenModel.refreshAllNotification()
        }
        val notifications: List<NotificationItemData> by remember {
            derivedStateOf {
                (homeScreenModel.friendRequestNotifications + homeScreenModel.notifications)
                    .sortedByDescending { it.createdAt }
            }
        }
        val boopSuccessMessage = strings.profileBoopSuccess
        val boopAlreadySentMessage = strings.profileBoopAlreadySent
        val onResponseNotification: (NotificationItemData, NotificationItemData.ActionData) -> Unit = { item, response ->
            homeScreenModel.responseAllNotification(
                item = item,
                action = response,
                boopSuccessMessage = boopSuccessMessage,
                boopAlreadySentMessage = boopAlreadySentMessage,
            )
        }

        SharedDialogContainer {
            if (notifications.isEmpty()) {
                Text(
                    modifier = Modifier.padding(6.dp),
                    text = strings.homeNotificationEmpty,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    style = MaterialTheme.typography.titleLarge
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().padding(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(notifications, key = { it.id }) { item ->
                        NotificationItem(
                            item = item,
                            loadingAction = homeScreenModel.pendingNotificationActions[item.id],
                            onResponse = onResponseNotification,
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun LazyItemScope.NotificationItem(
    item: NotificationItemData,
    loadingAction: NotificationItemData.ActionData?,
    onResponse: (NotificationItemData, NotificationItemData.ActionData) -> Unit,
) {
    var isExpand by remember { mutableStateOf(false) }
    val isFriendRequest = item.type == NotificationType.FriendRequest.value
    val senderId = item.senderId.orEmpty()
    val linkedUserId = item.linkedUserId.orEmpty()
    val sharedSuffixKey = rememberContainerTransformToken(
        "notification:${item.id}:user:$senderId",
    ) ?: LocalSharedSuffixKey.current
    val contentText = if (isFriendRequest) "${item.message} ${strings.notificationFriendRequest}" else item.message
    val navigator = LocalNavigator.currentOrThrow
    Box(
        modifier = Modifier.fillMaxWidth().animateItem()
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.height(80.dp).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                AImage(
                    modifier = Modifier
                        .enableIf(senderId.isNotEmpty()) {
                            this.clickable {
                                navigator push UserProfileScreen(
                                    userProfileVO = UserProfileVo(
                                        id = senderId,
                                        profileImageUrl = item.imageUrl
                                    ),
                                    sharedSuffixKey = sharedSuffixKey,
                                )
                            }.sharedBoundsBy(
                                key = "${senderId}UserIcon",
                                suffixKey = sharedSuffixKey,
                            )
                        }
                        .size(120.dp, 80.dp)
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest, MaterialTheme.shapes.medium)
                        .clip(MaterialTheme.shapes.medium),
                    imageData = item.imageUrl
                )
                Column {
                    TooltipBox(
                        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                        tooltip = {
                            PlainTooltip {
                                Text(text = item.title ?: item.message)
                            }
                        },
                        state = rememberTooltipState()
                    ) {
                        Text(
                            modifier = Modifier.fillMaxWidth(),
                            text = item.title ?: item.message,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            overflow = TextOverflow.Ellipsis,
                            maxLines = 2,
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    if (item.type.equals("boop", ignoreCase = true)) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = boopIcon(item.boopEmojiId),
                                contentDescription = strings.profileBoop,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.tertiary,
                            )
                            Text(
                                text = strings.profileBoop,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else {
                        Text(
                            text = item.type,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = remember {
                            @OptIn(ExperimentalTime::class)
                            Instant.parse(item.createdAt).toLocalDateTime(TimeZone.currentSystemDefault()).ignoredFormat
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outlineVariant
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().height(32.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Spacer(modifier = Modifier.weight(1f))
                item.actions.forEach { action ->
                    val isLoading = loadingAction == action
                    FilledTonalButton(
                        modifier = Modifier.animateContentSize(),
                        enabled = loadingAction == null,
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        ),
                        onClick = {
                            if (action.type.equals("link", ignoreCase = true) && linkedUserId.isNotEmpty()) {
                                navigator push UserProfileScreen(
                                    userProfileVO = UserProfileVo(
                                        id = linkedUserId,
                                        profileImageUrl = item.imageUrl
                                    ),
                                    sharedSuffixKey = sharedSuffixKey,
                                )
                            } else {
                                onResponse(item, action)
                            }
                        }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                modifier = Modifier.alpha(if (isLoading) 0f else 1f),
                                text = action.type.capitalizeFirst(),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                            if (isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                                    strokeWidth = 2.dp,
                                )
                            }
                        }
                    }
                }
                IconButton(
                    onClick = { isExpand = !isExpand }
                ) {
                    Icon(
                        imageVector = if (isExpand) AppIcons.ExpandLess else AppIcons.ExpandMore,
                        contentDescription = "ExpandIconButton"
                    )
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        shape = MaterialTheme.shapes.small
                    )
                    .animateContentSize()
            ) {
                if (isExpand) {
                    Text(
                        modifier = Modifier.padding(6.dp),
                        text = contentText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

        }
    }
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
