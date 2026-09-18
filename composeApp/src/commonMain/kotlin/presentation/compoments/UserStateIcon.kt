package io.github.vrcmteam.vrcm.presentation.compoments

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.unit.dp
import io.github.vrcmteam.vrcm.network.api.attributes.IUser
import io.github.vrcmteam.vrcm.network.api.attributes.LocationType
import io.github.vrcmteam.vrcm.network.api.attributes.UserStatus
import io.github.vrcmteam.vrcm.network.api.friends.date.FriendData
import io.github.vrcmteam.vrcm.network.api.invite.InviteApi
import io.github.vrcmteam.vrcm.presentation.animations.NoClip
import io.github.vrcmteam.vrcm.presentation.animations.TextBoundsTransform
import io.github.vrcmteam.vrcm.presentation.designsystem.AppActivityIndicator
import io.github.vrcmteam.vrcm.presentation.designsystem.AppIcon
import io.github.vrcmteam.vrcm.presentation.designsystem.AppShapes
import io.github.vrcmteam.vrcm.presentation.designsystem.AppText
import io.github.vrcmteam.vrcm.presentation.designsystem.AppTheme
import io.github.vrcmteam.vrcm.presentation.extensions.drawSateCircle
import io.github.vrcmteam.vrcm.presentation.extensions.enableIf
import io.github.vrcmteam.vrcm.presentation.navigation.rememberContainerTransformToken
import io.github.vrcmteam.vrcm.presentation.settings.locale.strings
import io.github.vrcmteam.vrcm.presentation.supports.AppIcons
import io.github.vrcmteam.vrcm.presentation.theme.GameColor
import io.github.vrcmteam.vrcm.service.AuthService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

private val FriendIconItemWidth = 60.dp
private val FriendIconVerticalSpacing = 16.dp

@Composable
fun UserStateIcon(
    modifier: Modifier = Modifier,
    iconUrl: String?,
    userStatus: UserStatus? = null,
    location: String? = null,
    cachedPlaceholderKey: String? = null,
) {
    val isHollow = isHollowUserStatus(userStatus, location)
    AImage(
        modifier = Modifier
            .then(modifier)
            .enableIf(userStatus != null) { drawSateCircle(GameColor.Status.fromValue(userStatus), hollow = isHollow) }
            .aspectRatio(1f)
            .clip(CircleShape),
        imageData = iconUrl.orEmpty(),
        contentDescription = "UserStateIcon",
        cachedPlaceholderKey = cachedPlaceholderKey,
    )
}

private fun isHollowUserStatus(userStatus: UserStatus?, location: String?): Boolean =
    userStatus != null &&
        userStatus != UserStatus.Offline &&
        location != null &&
        LocationType.fromValue(location) == LocationType.Offline

@Composable
internal fun UserStatusIndicator(
    modifier: Modifier = Modifier,
    userStatus: UserStatus?,
    location: String?,
    backgroundColor: Color = AppTheme.colors.secondaryGroupedBackground,
) {
    val isHollow = isHollowUserStatus(userStatus, location)
    Canvas(modifier = modifier) {
        val statusColor = GameColor.Status.fromValue(userStatus)
        if (isHollow) {
            val strokeWidth = size.minDimension * 0.25f
            drawCircle(backgroundColor, radius = size.minDimension / 2)
            drawCircle(
                color = statusColor,
                radius = size.minDimension / 2 - strokeWidth / 2,
                style = Stroke(strokeWidth),
            )
        } else {
            drawCircle(statusColor)
        }
    }
}

@Composable
fun UserIconsRow(
    modifier: Modifier = Modifier,
    friends: List<State<FriendData>>,
    instanceId: String? = null,
    travelingIds: Set<String> = emptySet(),
    contentPadding: PaddingValues = PaddingValues(0.dp),
    onClickUserIcon: (FriendData, String) -> Unit,
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
            ) { sharedSuffixKey -> onClickUserIcon(friend, sharedSuffixKey) }
        }
    }
}

@Composable
fun UserIconsFlowRow(
    modifier: Modifier = Modifier,
    friends: List<State<FriendData>>,
    onClickUserIcon: (FriendData, String) -> Unit,
) {
    if (friends.isEmpty()) return
    BoxWithConstraints(modifier = modifier) {
        // 按头像宽度确定最大列数，再补齐末行，让每一行复用同一组列位。
        val maxItemsInEachRow = (maxWidth / FriendIconItemWidth).toInt().coerceAtLeast(1)
        val trailingSlotCount =
            (maxItemsInEachRow - friends.size % maxItemsInEachRow) % maxItemsInEachRow
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalArrangement = Arrangement.spacedBy(FriendIconVerticalSpacing),
            maxItemsInEachRow = maxItemsInEachRow,
        ) {
            friends.forEach { friendState ->
                val friend = friendState.value
                key(friend.id) {
                    LocationFriendContent(
                        id = friend.id,
                        iconUrl = friend.iconUrl,
                        name = friend.displayName,
                        userStatus = friend.status,
                        location = friend.location,
                    ) { sharedSuffixKey -> onClickUserIcon(friend, sharedSuffixKey) }
                }
            }
            repeat(trailingSlotCount) {
                Spacer(modifier = Modifier.width(FriendIconItemWidth))
            }
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
            .clip(AppShapes.s)
            .clickable(enabled = !isInvited){ onClickInvite() }
            .animateItem(),
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(CircleShape)
                .background(AppTheme.colors.tintSoft)
        ) {
            AppIcon(
                imageVector = if (isInvited) AppIcons.Check else AppIcons.Add,
                modifier = Modifier.padding(4.dp).fillMaxSize(),
                contentDescription = "InviteSelfIcon",
                tint = AppTheme.colors.onTintSoft,
            )
        }
        AppText(
            modifier = Modifier.fillMaxWidth(),
            text = strings.locationInviteMe,
            maxLines = 1,
            textAlign = TextAlign.Center,
            style = AppTheme.type.caption2Emphasized,
            color = AppTheme.colors.tertiaryLabel
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
    onClickUserIcon: (String) -> Unit,
) {
    LocationFriendContent(
        modifier = Modifier.animateItem(),
        id = id,
        iconUrl = iconUrl,
        name = name,
        userStatus = userStatus,
        location = location,
        isTraveling = isTraveling,
        onClickUserIcon = onClickUserIcon,
    )
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun LocationFriendContent(
    modifier: Modifier = Modifier,
    id: String,
    iconUrl: String,
    name: String,
    userStatus: UserStatus,
    location: String? = null,
    isTraveling: Boolean = false,
    onClickUserIcon: (String) -> Unit,
) {
    val sharedSuffixKey = rememberContainerTransformToken("location-user:$id")
        ?: LocalSharedSuffixKey.current
    Column(
        modifier = Modifier.width(FriendIconItemWidth)
            .clip(AppShapes.s)
            .clickable { onClickUserIcon(sharedSuffixKey) }
            .then(modifier),
        verticalArrangement = Arrangement.Center
    ) {
        Box {
            UserStateIcon(
                modifier = Modifier.sharedBoundsBy(
                    key = "${id}UserIcon",
                    suffixKey = sharedSuffixKey,
                ).fillMaxWidth(),
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
                    AppActivityIndicator(
                        modifier = Modifier.size(20.dp),
                        color = Color.White,
                    )
                }
            }
        }
        AppText(
            modifier = Modifier.sharedBoundsBy(
                key = "${id}UserName",
                suffixKey = sharedSuffixKey,
                resizeMode = SharedTextBoundsResizeMode,
                boundsTransform = TextBoundsTransform,
                clipInOverlayDuringTransition = NoClip,
            ).fillMaxWidth(),
            text = name,
            maxLines = 1,
            textAlign = TextAlign.Center,
            style = AppTheme.type.caption2Emphasized,
            color = AppTheme.colors.tertiaryLabel
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
    style: TextStyle = AppTheme.type.title2,
    user: IUser?,
    sharedUserId: String? = user?.id,
    sharedSuffixKey: String? = null,
    pronouns: String? = null,
) {
    val userNameText = @Composable {
        AppText(
            modifier = Modifier.sharedBoundsBy(
                key = "${sharedUserId}UserName",
                suffixKey = sharedSuffixKey,
                resizeMode = SharedTextBoundsResizeMode,
                boundsTransform = TextBoundsTransform,
                clipInOverlayDuringTransition = NoClip,
            ),
            text = user?.displayName.orEmpty(),
            style = style,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = AppTheme.colors.tint,
        )
    }

    val hasPronouns = !pronouns.isNullOrBlank()
    val isSupporter = user?.isSupporter == true
    Layout(
        modifier = modifier.offset(x = (-4).dp),
        content = {
            AppIcon(
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
            if (hasPronouns) {
                AppText(
                    text = pronouns.orEmpty(),
                    style = AppTheme.type.caption1,
                    color = AppTheme.colors.secondaryLabel,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (isSupporter) {
                VrcPlusIcon(modifier = Modifier.size(iconSize * 0.8f))
            }
        },
    ) { measurables, constraints ->
        val childConstraints = constraints.copy(minWidth = 0, minHeight = 0)
        var childIndex = 0
        val trustRank = measurables[childIndex++].measure(childConstraints)
        val userNameMeasurable = measurables[childIndex++]
        val pronounsMeasurable = if (hasPronouns) measurables[childIndex++] else null
        val supporter = if (isSupporter) {
            measurables[childIndex].measure(childConstraints)
        } else {
            null
        }
        val spacing = spacedBy.roundToPx()
        val fixedSpacingCount = 1 + if (supporter != null) 1 else 0
        val fixedWidth = trustRank.width + (supporter?.width ?: 0) + spacing * fixedSpacingCount

        // 固定图标和用户名优先，代词仅使用剩余宽度。
        val userNameMaxWidth = if (constraints.hasBoundedWidth) {
            (constraints.maxWidth - fixedWidth).coerceAtLeast(0)
        } else {
            childConstraints.maxWidth
        }
        val userName = userNameMeasurable.measure(
            childConstraints.copy(maxWidth = userNameMaxWidth),
        )
        val pronounsMaxWidth = if (constraints.hasBoundedWidth) {
            (constraints.maxWidth - fixedWidth - userName.width - spacing).coerceAtLeast(0)
        } else {
            childConstraints.maxWidth
        }
        val pronounsText = pronounsMeasurable?.takeIf {
            !constraints.hasBoundedWidth || pronounsMaxWidth > 0
        }?.measure(
            childConstraints.copy(maxWidth = pronounsMaxWidth),
        )

        val contentWidth = trustRank.width + spacing + userName.width +
            (pronounsText?.let { spacing + it.width } ?: 0) +
            (supporter?.let { spacing + it.width } ?: 0)
        val contentHeight = maxOf(
            trustRank.height,
            userName.height,
            pronounsText?.height ?: 0,
            supporter?.height ?: 0,
        )

        layout(
            width = constraints.constrainWidth(contentWidth),
            height = constraints.constrainHeight(contentHeight),
        ) {
            var x = 0
            trustRank.placeRelative(x, (contentHeight - trustRank.height) / 2)
            x += trustRank.width + spacing
            userName.placeRelative(x, (contentHeight - userName.height) / 2)
            x += userName.width
            pronounsText?.let {
                x += spacing
                it.placeRelative(x, (contentHeight - it.height) / 2)
                x += it.width
            }
            supporter?.let {
                x += spacing
                it.placeRelative(x, 0)
            }
        }
    }
}

@Composable
fun VrcPlusIcon(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        drawOval(
            color = GameColor.Supporter,
            topLeft = Offset(size.width / 2f - (size.width * 0.2f / 2), size.height * 0.1f),
            size = Size(size.width * 0.2f, size.height * 0.8f),
        )
        drawOval(
            color = GameColor.Supporter,
            topLeft = Offset(size.width * 0.1f, size.height / 2f - (size.height * 0.2f / 2)),
            size = Size(size.width * 0.8f, size.height * 0.2f),
        )
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun UserStatusRow(
    modifier: Modifier = Modifier,
    canCopy: Boolean = false,
    spacedBy: Dp = 6.dp,
    iconSize: Dp = 12.dp,
    style: TextStyle = AppTheme.type.subheadlineEmphasized,
    user: IUser?,
    animatedVisibilityScope: AnimatedVisibilityScope? =  null,
    sharedUserId: String? = user?.id,
    sharedSuffixKey: String? = null,
) {
    val statusText = @Composable {
        AppText(
            modifier = Modifier
                .sharedBoundsBy(
                    key = "${sharedUserId}UserStatusRow",
                    suffixKey = sharedSuffixKey,
                    resizeMode = SharedTextBoundsResizeMode,
                    boundsTransform = TextBoundsTransform,
                    clipInOverlayDuringTransition = NoClip,
                )
                .enableIf(animatedVisibilityScope != null) {
                    sharedBoundsBy(
                        key = "${sharedUserId}UserStatusText",
                        sharedTransitionScope = LocalSharedTransitionDialogScope.current,
                        animatedVisibilityScope = animatedVisibilityScope!!,
                    )
                },
            text = user?.statusDescription?.ifBlank { user.status.value }.orEmpty(),
            style = style,
            color = AppTheme.colors.secondaryLabel,
            overflow = TextOverflow.Ellipsis,
            maxLines = 1
        )
    }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacedBy)
    ) {
        UserStatusIndicator(
            modifier = Modifier
                .size(iconSize)
                .enableIf(animatedVisibilityScope != null){
                    sharedBoundsBy(
                        key = "${sharedUserId}UserStatusIcon",
                        sharedTransitionScope = LocalSharedTransitionDialogScope.current,
                        animatedVisibilityScope = animatedVisibilityScope!!
                    )
                },
            userStatus = user?.status,
            location = user?.location,
        )
        if (canCopy) {
            SelectionContainer {
                statusText()
            }
        } else {
            statusText()
        }
    }
}
