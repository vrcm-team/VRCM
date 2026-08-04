package io.github.vrcmteam.vrcm.presentation.compoments

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.vrcmteam.vrcm.network.api.attributes.IUser
import io.github.vrcmteam.vrcm.network.api.attributes.LocationType
import io.github.vrcmteam.vrcm.network.api.attributes.UserStatus
import io.github.vrcmteam.vrcm.network.api.friends.date.FriendData
import io.github.vrcmteam.vrcm.network.api.invite.InviteApi
import io.github.vrcmteam.vrcm.presentation.animations.NoClip
import io.github.vrcmteam.vrcm.presentation.animations.TextBoundsTransform
import io.github.vrcmteam.vrcm.presentation.extensions.drawSateCircle
import io.github.vrcmteam.vrcm.presentation.extensions.enableIf
import io.github.vrcmteam.vrcm.presentation.settings.locale.strings
import io.github.vrcmteam.vrcm.presentation.supports.AppIcons
import io.github.vrcmteam.vrcm.presentation.theme.GameColor
import io.github.vrcmteam.vrcm.service.AuthService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@Composable
fun UserStateIcon(
    modifier: Modifier = Modifier,
    iconUrl: String?,
    userStatus: UserStatus? = null,
    location: String? = null,
) {
    val isHollow = userStatus != UserStatus.Offline && location != null && LocationType.fromValue(location) == LocationType.Offline
    AImage(
        modifier = Modifier
            .then(modifier)
            .enableIf(userStatus != null) { drawSateCircle(GameColor.Status.fromValue(userStatus), hollow = isHollow) }
            .aspectRatio(1f)
            .clip(CircleShape),
        imageData = iconUrl.orEmpty(),
        contentDescription = "UserStateIcon"
    )
}

@Composable
fun UserIconsRow(
    modifier: Modifier = Modifier,
    friends: List<State<FriendData>>,
    instanceId: String? = null,
    travelingIds: Set<String> = emptySet(),
    contentPadding: PaddingValues = PaddingValues(0.dp),
    onClickUserIcon: (FriendData) -> Unit,
) {
    if (friends.isEmpty()) return
    LazyRow(
        modifier = modifier,
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {

        instanceId?.let {
            item {
                InviteSelf(instanceId = instanceId)
            }
        }

        items(friends, key = { it.value.id }) {
            val friend = it.value
            LocationFriend(
                id = friend.id,
                iconUrl = friend.iconUrl,
                name = friend.displayName,
                userStatus = friend.status,
                location = friend.location,
                isTraveling = friend.id in travelingIds,
            ) { onClickUserIcon(friend) }
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun LazyItemScope.InviteSelf(
    instanceId: String,
) {
    val inviteApi: InviteApi = koinInject()
    val authService: AuthService = koinInject()
    var isInvited by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val onClickInvite = {
        scope.launch(Dispatchers.IO) {
            authService.reTryAuthCatching { inviteApi.inviteMyselfToInstance(instanceId) }.onSuccess {
                isInvited = true
            }
        }
    }
    Column(
        modifier = Modifier.width(60.dp)
            .clip(MaterialTheme.shapes.small)
            .clickable(enabled = !isInvited){ onClickInvite() }
            .animateItem(),
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.secondary)
        ) {
            Icon(
                imageVector = if (isInvited) AppIcons.Check else AppIcons.Add,
                modifier = Modifier.padding(4.dp).fillMaxSize(),
                contentDescription = "InviteSelfIcon",
                tint = MaterialTheme.colorScheme.onSecondary,
            )
        }
        Text(
            modifier = Modifier.fillMaxWidth(),
            text = strings.locationInviteMe,
            maxLines = 1,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline
        )
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun LazyItemScope.LocationFriend(
    id: String,
    iconUrl: String,
    name: String,
    userStatus: UserStatus,
    location: String? = null,
    isTraveling: Boolean = false,
    onClickUserIcon: () -> Unit,
) {
    Column(
        modifier = Modifier.width(60.dp)
            .clip(MaterialTheme.shapes.small)
            .clickable(onClick = onClickUserIcon).animateItem(),
        verticalArrangement = Arrangement.Center
    ) {
        Box {
            UserStateIcon(
                modifier = Modifier.sharedBoundsBy("${id}UserIcon").fillMaxWidth(),
                iconUrl = iconUrl,
                userStatus = userStatus,
                location = location
            )
            // 正在跃迁的好友显示旋转加载圈（全遮罩）
            if (isTraveling) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.55f)),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.5.dp,
                        color = Color.White,
                    )
                }
            }
        }
        Text(
            modifier = Modifier.sharedBoundsBy(
                key = "${id}UserName",
                resizeMode = SharedTextBoundsResizeMode,
                boundsTransform = TextBoundsTransform,
                clipInOverlayDuringTransition = NoClip,
            ).fillMaxWidth(),
            text = name,
            maxLines = 1,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline
        )
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun UserInfoRow(
    modifier: Modifier = Modifier,
    canCopy: Boolean = false,
    spacedBy: Dp = 6.dp,
    iconSize: Dp = 24.dp,
    style: TextStyle = MaterialTheme.typography.headlineSmall,
    user: IUser?,
) {
    val userNameText = @Composable {
        Text(
            modifier = Modifier.sharedBoundsBy(
                key = "${user?.id}UserName",
                resizeMode = SharedTextBoundsResizeMode,
                boundsTransform = TextBoundsTransform,
                clipInOverlayDuringTransition = NoClip,
            ),
            text = user?.displayName.orEmpty(),
            style = style,
            maxLines = 1,
            color = MaterialTheme.colorScheme.primary,
        )
    }

    Row(
        modifier = modifier.offset(x = (-4).dp),
        horizontalArrangement = Arrangement.spacedBy(spacedBy),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            modifier = Modifier
                .size(iconSize),
            imageVector = AppIcons.Shield,
            contentDescription = "TrustRankIcon",
            tint = GameColor.Rank.fromValue(user?.trustRank)
        )
        if (canCopy) {
            SelectionContainer {
                userNameText()
            }
        } else {
            userNameText()
        }

        if (user?.isSupporter == true) {
            Canvas(modifier = Modifier.align(Alignment.Top).size(iconSize * 0.8f)) {
                drawOval(
                    color = GameColor.Supporter,
                    topLeft = Offset(size.width / 2f - (size.width * 0.2f / 2), size.height * 0.1f),
                    size = Size(size.width * 0.2f, size.height * 0.8f)
                )
                drawOval(
                    color = GameColor.Supporter,
                    topLeft = Offset(size.width * 0.1f, size.height / 2f - (size.height * 0.2f / 2)),
                    size = Size(size.width * 0.8f, size.height * 0.2f)
                )
            }
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun UserStatusRow(
    modifier: Modifier = Modifier,
    canCopy: Boolean = false,
    spacedBy: Dp = 6.dp,
    iconSize: Dp = 12.dp,
    style: TextStyle = MaterialTheme.typography.labelLarge,
    user: IUser?,
    animatedVisibilityScope: AnimatedVisibilityScope? =  null,
) {
    val statusText = @Composable {
        Text(
            modifier = Modifier.enableIf(animatedVisibilityScope != null){
                sharedBoundsBy(
                    key = "${user?.id}UserStatusText",
                    sharedTransitionScope = LocalSharedTransitionDialogScope.current,
                    animatedVisibilityScope = animatedVisibilityScope!!
                )
            },
            text = user?.statusDescription?.ifBlank { user.status.value }.orEmpty(),
            style = style,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            overflow = TextOverflow.Ellipsis,
            maxLines = 1
        )
    }

    Row(
        modifier = modifier.sharedBoundsBy(
            key = "${user?.id}UserStatusRow",
            resizeMode = SharedTextBoundsResizeMode,
        ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacedBy)
    ) {
        val isHollow = user != null && user.status != UserStatus.Offline && LocationType.fromValue(user.location) == LocationType.Offline
        val bgColor = MaterialTheme.colorScheme.surface
        Canvas(
            modifier = Modifier
                .size(iconSize)
                .enableIf(animatedVisibilityScope != null){
                    sharedBoundsBy(
                        key = "${user?.id}UserStatusIcon",
                        sharedTransitionScope = LocalSharedTransitionDialogScope.current,
                        animatedVisibilityScope = animatedVisibilityScope!!
                    )
                }
        ) {
            val statusColor = GameColor.Status.fromValue(user?.status)
            if (isHollow) {
                val strokeWidth = size.minDimension * 0.25f
                drawCircle(bgColor, radius = size.minDimension / 2)
                drawCircle(statusColor, radius = size.minDimension / 2 - strokeWidth / 2, style = Stroke(strokeWidth))
            } else {
                drawCircle(statusColor)
            }
        }
        if (canCopy) {
            SelectionContainer {
                statusText()
            }
        } else {
            statusText()
        }
    }
}
