package io.github.vrcmteam.vrcm.presentation.compoments

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.vrcmteam.vrcm.network.api.friends.date.FriendData
import io.github.vrcmteam.vrcm.presentation.extensions.enableIf
import io.github.vrcmteam.vrcm.presentation.screens.home.data.FriendLocation
import io.github.vrcmteam.vrcm.presentation.screens.home.data.HomeInstanceVo

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun LocationCard(
    location: FriendLocation,
    isSelected: Boolean,
    onClickWorldImage: () -> Unit,
    onClickLocationCard: () -> Unit,
    content: @Composable (List<State<FriendData>>) -> Unit,
) {
    val instants by location.instants
    val friendList = location.friendList
    Surface(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth(),
        tonalElevation = (-2).dp,
        shape = MaterialTheme.shapes.large
    ) {
        Column(
            modifier = Modifier
                .animateContentSize()
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(112.dp)
                    .clip(MaterialTheme.shapes.medium),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                AImage(
                    modifier = Modifier
                        .sharedElementBy(
                            key = location.location + "WorldImage",
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
                            clickable(onClick = onClickWorldImage)
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
                        .clickable(onClick = onClickLocationCard),
                ) {
                    Text(
                        text = instants.worldName,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        color = MaterialTheme.colorScheme.primary,
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
                        Text(
                            text = instants.accessType.displayName,
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            color = MaterialTheme.colorScheme.outline
                        )
                        Text(
                            text = "#${instants.name}",
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                    Text(
                        modifier = Modifier
                            .fillMaxWidth(),
                        text = instants.worldDescription,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    // 房间好友头像/房间持有者与房间人数比
                    MemberInfoRow(isSelected, friendList, instants)
                }
            }
            AnimatedVisibility(isSelected) {
                content(friendList)
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
    friendList: List<State<FriendData>>,
    instants: HomeInstanceVo,
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
                        horizontalArrangement = Arrangement.spacedBy((-8).dp)
                    ) {
                        friendList.take(5).forEach { friendState ->
                            UserStateIcon(
                                modifier = Modifier
                                    .align(Alignment.CenterVertically)
                                    .border(1.dp, MaterialTheme.colorScheme.surface, CircleShape),
                                iconUrl = friendState.value.iconUrl,
                            )
                        }
                    }
                } else {
                    val owner = instants.owner ?: return@AnimatedContent
                    Row(
                        modifier = Modifier.fillMaxHeight().background(
                            MaterialTheme.colorScheme.inverseOnSurface,
                            MaterialTheme.shapes.medium
                        )
                            .clip(MaterialTheme.shapes.medium)
                            .padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            modifier = Modifier.size(16.dp),
                            imageVector = owner.iconVector,
                            contentDescription = "OwnerIcon",
                            tint = MaterialTheme.colorScheme.outline
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(
                            text = owner.displayName,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
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

