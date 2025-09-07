package io.github.vrcmteam.vrcm.presentation.screens.home.dialog

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import io.github.vrcmteam.vrcm.core.extensions.capitalizeFirst
import io.github.vrcmteam.vrcm.network.api.attributes.NotificationType
import io.github.vrcmteam.vrcm.presentation.compoments.AImage
import io.github.vrcmteam.vrcm.presentation.compoments.SharedDialog
import io.github.vrcmteam.vrcm.presentation.compoments.SharedDialogContainer
import io.github.vrcmteam.vrcm.presentation.compoments.sharedBoundsBy
import io.github.vrcmteam.vrcm.presentation.extensions.enableIf
import io.github.vrcmteam.vrcm.presentation.extensions.ignoredFormat
import io.github.vrcmteam.vrcm.presentation.extensions.koinScreenModelByLastItem
import io.github.vrcmteam.vrcm.presentation.screens.home.HomeScreenModel
import io.github.vrcmteam.vrcm.presentation.screens.home.data.NotificationItemData
import io.github.vrcmteam.vrcm.presentation.screens.user.UserProfileScreen
import io.github.vrcmteam.vrcm.presentation.screens.user.data.UserProfileVo
import io.github.vrcmteam.vrcm.presentation.settings.locale.strings
import io.github.vrcmteam.vrcm.presentation.supports.AppIcons
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

object NotificationDialog : SharedDialog {

    @Composable
    override fun Content(animatedVisibilityScope: AnimatedVisibilityScope) {
        val homeScreenModel: HomeScreenModel = koinScreenModelByLastItem()
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
        val onResponseNotification: (String, String, NotificationItemData.ActionData) -> Unit = { id, type, response ->
            homeScreenModel.responseAllNotification(id, type, response)
        }

        AnimatedContent(
            modifier = Modifier.animateContentSize(),
            targetState = notifications,
            transitionSpec = {
                (fadeIn(animationSpec = tween(220, delayMillis = 90)) +
                        slideInVertically(animationSpec = tween(220, delayMillis = 90)))
                    .togetherWith(fadeOut(animationSpec = tween(90)))
            }
        ) { notificationItemDataList ->
            if (notificationItemDataList.isEmpty()) {
                Text(
                    modifier = Modifier.padding(6.dp),
                    text = strings.homeNotificationEmpty,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    style = MaterialTheme.typography.titleLarge
                )
            } else {
                SharedDialogContainer {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().padding(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        items(notificationItemDataList) { item ->
                            NotificationItem(item, onResponseNotification)
                        }
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
    onResponse: (String, String, NotificationItemData.ActionData) -> Unit,
) {
    var isExpand by remember { mutableStateOf(false) }
    var responded by remember { mutableStateOf(false) }
    val isFriendRequest = item.type == NotificationType.FriendRequest.value
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
                        .enableIf(isFriendRequest) {
                            this.clickable {
                                navigator push UserProfileScreen(
                                    userProfileVO = UserProfileVo(
                                        id = item.senderUserId,
                                        profileImageUrl = item.imageUrl
                                    )
                                )
                            }.sharedBoundsBy("${item.senderUserId}UserIcon")
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
                    Text(
                        text = item.type,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = remember {
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
                    FilledTonalButton(
                        modifier = Modifier.animateContentSize(),
                        enabled = !responded,
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        ),
                        onClick = {
                            responded = true
                            onResponse(item.id, item.type, action)
                        }
                    ) {
                        Text(
                            text = action.type.capitalizeFirst(),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
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