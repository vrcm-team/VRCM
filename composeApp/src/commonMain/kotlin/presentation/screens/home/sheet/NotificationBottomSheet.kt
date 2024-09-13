package io.github.vrcmteam.vrcm.presentation.screens.home.sheet

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import io.github.vrcmteam.vrcm.core.extensions.capitalizeFirst
import io.github.vrcmteam.vrcm.network.api.attributes.NotificationType
import io.github.vrcmteam.vrcm.presentation.compoments.ABottomSheet
import io.github.vrcmteam.vrcm.presentation.compoments.AImage
import io.github.vrcmteam.vrcm.presentation.extensions.getInsetPadding
import io.github.vrcmteam.vrcm.presentation.extensions.ignoredFormat
import io.github.vrcmteam.vrcm.presentation.screens.home.data.NotificationItemData
import io.github.vrcmteam.vrcm.presentation.screens.profile.UserProfileScreen
import io.github.vrcmteam.vrcm.presentation.screens.profile.data.UserProfileVo
import io.github.vrcmteam.vrcm.presentation.settings.locale.strings
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationBottomSheet(
    bottomSheetIsVisible: Boolean,
    notificationList: List<NotificationItemData>,
    sheetState: SheetState = rememberModalBottomSheetState(),
    onDismissRequest: () -> Unit,
    onResponseNotification: (String, String, NotificationItemData.ActionData) -> Unit,
) {
    ABottomSheet(
        isVisible = bottomSheetIsVisible,
        sheetState = sheetState,
        onDismissRequest = onDismissRequest
    ) {
        if (notificationList.isEmpty()) {
            Text(
                modifier = Modifier.fillMaxWidth().align(Alignment.CenterHorizontally),
                text = "No Notification",
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.primaryContainer,
                style = MaterialTheme.typography.displaySmall
            )

        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = PaddingValues(
                    start = 6.dp,
                    top = 6.dp,
                    end = 6.dp,
                    bottom = getInsetPadding(6, WindowInsets::getBottom)
                )
            ) {
                items(notificationList) {
                    NotificationItem(it, onResponseNotification, onDismissRequest)
                }
            }
        }
    }
}


@Composable
private inline fun NotificationItem(
    it: NotificationItemData,
    noinline onResponse: (String, String, NotificationItemData.ActionData) -> Unit,
    noinline onDismissRequest: () -> Unit
) {
    var isExpand by remember { mutableStateOf(false) }
    val isFriendRequest = it.type == NotificationType.FriendRequest.value
    val contentText = if (isFriendRequest) "${it.message} ${strings.notificationFriendRequest}" else it.message
    val navigator = LocalNavigator.currentOrThrow
    Box(
        modifier = Modifier.fillMaxWidth()
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
                        .clickable(isFriendRequest) {
                            onDismissRequest()
                            navigator push UserProfileScreen(
                                UserProfileVo(
                                    id = it.senderUserId,
                                    profileImageUrl = it.imageUrl
                                )
                            )
                        }
                        .size(120.dp, 80.dp)
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest, MaterialTheme.shapes.medium)
                        .clip(MaterialTheme.shapes.medium),
                    imageData = it.imageUrl
                )
                Column {
                    Text(
                        modifier = Modifier.fillMaxWidth(),
                        text = contentText,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        overflow = TextOverflow.Ellipsis,
                        maxLines = 2,
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = it.type,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = remember {
                            Instant.parse(it.createdAt).toLocalDateTime(TimeZone.currentSystemDefault()).ignoredFormat
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
                it.actions.forEach { action ->
                    FilledTonalButton(
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        ),
                        onClick = { onResponse(it.id, it.type, action) }
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
                        imageVector = if (isExpand) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
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