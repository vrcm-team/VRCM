package io.github.vrcmteam.vrcm.presentation.compoments

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.vrcmteam.vrcm.network.api.attributes.IUser
import io.github.vrcmteam.vrcm.network.api.friends.date.FriendData
import io.github.vrcmteam.vrcm.presentation.designsystem.AppActivityIndicator
import io.github.vrcmteam.vrcm.presentation.designsystem.AppIcon
import io.github.vrcmteam.vrcm.presentation.designsystem.AppShapes
import io.github.vrcmteam.vrcm.presentation.designsystem.AppSurface
import io.github.vrcmteam.vrcm.presentation.designsystem.AppText
import io.github.vrcmteam.vrcm.presentation.designsystem.AppTheme
import io.github.vrcmteam.vrcm.presentation.extensions.enableIf
import io.github.vrcmteam.vrcm.presentation.navigation.rememberContainerTransformToken
import io.github.vrcmteam.vrcm.presentation.screens.home.data.FriendLocation
import io.github.vrcmteam.vrcm.presentation.screens.home.data.HomeInstanceVo
import io.github.vrcmteam.vrcm.presentation.settings.locale.strings
import androidx.compose.ui.graphics.Color

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun LocationCard(
    modifier: Modifier = Modifier,
    location: FriendLocation,
    isSelected: Boolean,
    onClickWorldImage: (String) -> Unit,
    /** 参数是这张卡的共享元素后缀，点卡片直接跳世界时要带上它才能接上转场。 */
    onClickLocationCard: (String) -> Unit,
    travelingIds: Set<String> = emptySet(),
    isCurrentUserLocation: Boolean = false,
    content: @Composable (List<State<FriendData>>) -> Unit,
) {
    val instants by location.instants
    val friendList = location.friendList
    val sharedSuffixKey = rememberContainerTransformToken(
        "location:${location.location}:${instants.worldId}",
    ) ?: LocalSharedSuffixKey.current
    AppSurface(
        modifier = modifier
            .fillMaxWidth(),
        shape = AppShapes.l
    ) {
        Box {
            Column(
                modifier = Modifier.padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(112.dp)
                    .clip(AppShapes.m),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                AImage(
                    modifier = Modifier
                        .sharedElementBy(
                            key = location.location + "WorldImage",
                            suffixKey = sharedSuffixKey,
                        )
                        .weight(0.5f)
                        .clip(
                            RoundedCornerShape(
                                topStart = 16.dp,
                                topEnd = 8.dp,
                                bottomStart = 16.dp,
                                bottomEnd = 8.dp
                            )
                        )
                        .enableIf(instants.worldId.isNotEmpty()) {
                            clickable { onClickWorldImage(sharedSuffixKey) }
                        },
                    imageData = instants.worldImageUrl,
                    contentDescription = "WorldImage"
                )
                Column(
                    modifier = Modifier
                        .weight(0.5f)
                        .clip(
                            RoundedCornerShape(
                                topStart = 8.dp,
                                topEnd = 16.dp,
                                bottomStart = 8.dp,
                                bottomEnd = 16.dp
                            )
                        )
                        .clickable { onClickLocationCard(sharedSuffixKey) },
                ) {
                    AppText(
                        text = instants.worldName,
                        style = AppTheme.type.headline,
                        maxLines = 1,
                        color = AppTheme.colors.tint,
                    )
                    Row(
                        modifier = Modifier
                            .height(20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        RegionIcon(
                            modifier = Modifier.align(Alignment.CenterVertically),
                            region = instants.region
                        )
                        AppText(
                            text = instants.accessType.displayName,
                            style = AppTheme.type.caption1Emphasized,
                            maxLines = 1,
                            color = AppTheme.colors.tertiaryLabel
                        )
                        AppText(
                            text = "#${instants.name}",
                            style = AppTheme.type.caption1Emphasized,
                            maxLines = 1,
                            color = AppTheme.colors.tertiaryLabel
                        )
                    }
                    AppText(
                        modifier = Modifier
                            .fillMaxWidth(),
                        text = instants.worldDescription,
                        style = AppTheme.type.caption1,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        color = AppTheme.colors.secondaryLabel
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    // 房间好友头像/房间持有者与房间人数比
                    // 没有好友在场（群组房间）时直接显示房主，不然这行是空的
                    MemberInfoRow(isSelected || friendList.isEmpty(), friendList, instants, travelingIds)
                }
            }
            AnimatedVisibility(isSelected) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                ) {
                    content(friendList)
                }
            }
            }
            if (isCurrentUserLocation) {
                AppSurface(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(4.dp),
                    shape = AppShapes.s,
                    color = AppTheme.colors.secondaryGroupedBackground.copy(alpha = 0.8f),
                ) {
                    AppText(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        text = strings.currentUserLocation,
                        style = AppTheme.type.caption2Emphasized,
                        color = AppTheme.colors.label,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}


/**
 * 房间好友头像/房间持有者与房间人数比
 */
@Composable
private inline fun MemberInfoRow(
    showUser: Boolean,
    friendList: List<State<IUser>>,
    instants: HomeInstanceVo,
    travelingIds: Set<String> = emptySet(),
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(24.dp),
    ) {
        // 房间好友头像/房间持有者
        Box(modifier = Modifier.fillMaxHeight().weight(0.6f)) {
            AnimatedContent(
                targetState = showUser,
                transitionSpec = {
                    (fadeIn() + expandHorizontally()) togetherWith (fadeOut() + shrinkHorizontally())
                }
            ) {
                if (!it) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(if (friendList.size == 1) 6.dp else (-8).dp)
                    ) {
                        friendList.take(5).forEach { friendState ->
                            Box(contentAlignment = Alignment.Center) {
                                UserStateIcon(
                                    modifier = Modifier
                                        .border(1.dp, AppTheme.colors.secondaryGroupedBackground, CircleShape),
                                    iconUrl = friendState.value.iconUrl,
                                )
                                if (friendState.value.id in travelingIds) {
                                    Box(
                                        modifier = Modifier
                                            .matchParentSize()
                                            .clip(CircleShape)
                                            .background(Color.Black.copy(alpha = 0.55f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        AppActivityIndicator(
                                            modifier = Modifier.size(12.dp),
                                            color = Color.White,
                                        )
                                    }
                                }
                            }
                        }
                        friendList.singleOrNull()?.let { friendState ->
                            AppText(
                                text = friendState.value.displayName,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = AppTheme.type.caption2Emphasized,
                                color = AppTheme.colors.label
                            )
                        }
                    }
                } else {
                    val owner = instants.owner ?: return@AnimatedContent
                    Row(
                        modifier = Modifier.fillMaxHeight().background(
                            AppTheme.colors.fill,
                            AppShapes.m
                        )
                            .clip(AppShapes.m)
                            .padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AppIcon(
                            modifier = Modifier.size(16.dp),
                            imageVector = owner.iconVector,
                            contentDescription = "OwnerIcon",
                            tint = AppTheme.colors.tertiaryLabel
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        AppText(
                            text = owner.displayName,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = AppTheme.type.caption2Emphasized,
                            color = AppTheme.colors.tertiaryLabel
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.weight(0.1f))
        // 房间人数比行
        if (instants.userCount.isEmpty()) return@Row
        TextLabel(
            modifier = Modifier.fillMaxHeight().weight(0.3f),
            text = instants.userCount,
        )
    }
}
