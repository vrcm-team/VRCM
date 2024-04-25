package io.github.vrcmteam.vrcm.presentation.screens.home.pager

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.ListItem
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.unit.dp
import io.github.vrcmteam.vrcm.core.shared.SharedFlowCentre
import io.github.vrcmteam.vrcm.network.api.attributes.IUser
import io.github.vrcmteam.vrcm.network.api.friends.date.FriendData
import io.github.vrcmteam.vrcm.presentation.compoments.RefreshBox
import io.github.vrcmteam.vrcm.presentation.compoments.UserStateIcon
import io.github.vrcmteam.vrcm.presentation.extensions.currentNavigator
import io.github.vrcmteam.vrcm.presentation.extensions.getInsetPadding
import io.github.vrcmteam.vrcm.presentation.screens.profile.UserProfileScreen
import io.github.vrcmteam.vrcm.presentation.screens.profile.data.UserProfileVO
import io.github.vrcmteam.vrcm.presentation.supports.ListPagerProvider
import io.github.vrcmteam.vrcm.presentation.theme.GameColor
import org.koin.compose.koinInject

object FriendListPagerProvider : ListPagerProvider {

    override val index: Int
        get() = 1
    override val title: String
        @Composable
        get() = "Friend"

    override val icon: Painter
        @Composable get() = rememberVectorPainter(image = Icons.Rounded.Group)

    @Composable
    override fun createPager(lazyListState: LazyListState): @Composable () -> Unit {
        val isRefreshing = rememberSaveable { mutableStateOf(true) }
        LaunchedEffect(Unit) {
            SharedFlowCentre.error.collect {
                // 如果报错跳转登录，并重制刷新标记
                isRefreshing.value = true
            }
        }
        val friendListPagerModel: FriendListPagerModel = koinInject()

        return remember {
            {
                FriendListPager(
                    friendList = friendListPagerModel.friendList,
                    isRefreshing = isRefreshing.value,
                    state = lazyListState,
                ) {
                    isRefreshing.value = false
                    friendListPagerModel.refreshFriendList()
                }
            }
        }
    }

}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FriendListPager(
    friendList: List<FriendData>,
    isRefreshing: Boolean,
    state: LazyListState = rememberLazyListState(),
    doRefresh: suspend () -> Unit
) {
    val topPadding = getInsetPadding(WindowInsets::getTop) + 80.dp
    RefreshBox(
        refreshContainerOffsetY = topPadding, isStartRefresh = isRefreshing, doRefresh = doRefresh
    ) {
        val currentNavigator = currentNavigator
        val toProfile = { user: IUser ->
            if (currentNavigator.size <= 1) currentNavigator push UserProfileScreen(
                UserProfileVO(
                    user
                )
            )
        }
        // 如果没有底部系统手势条，默认12dp
        val bottomPadding = getInsetPadding(12, WindowInsets::getBottom) + 80.dp
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = state,
            contentPadding = PaddingValues(
                top = topPadding,
                bottom = bottomPadding
            ),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(friendList, key = { it.id }) {
                FriendListItem(it, toProfile)
            }
        }
    }
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun FriendListItem(friend: FriendData, toProfile: (FriendData) -> Unit) {
    ListItem(
        modifier = Modifier
            .fillMaxWidth()
            .height(68.dp)
            .clickable { toProfile(friend) }
            .padding(horizontal = 6.dp),
        icon = {
            UserStateIcon(
                modifier = Modifier
                    .size(60.dp)
                    .clickable { toProfile(friend) },
                iconUrl = friend.iconUrl,
                userStatus = friend.status
            )
        },
        overlineText = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = friend.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    color = MaterialTheme.colorScheme.primary,
                )
                Icon(
                    modifier = Modifier.size(16.dp),
                    imageVector = Icons.Rounded.Shield,
                    contentDescription = "TrustRankIcon",
                    tint = GameColor.Rank.fromValue(friend.trustRank)
                )
            }
        },
        secondaryText = {
            Text(
                text = friend.statusDescription.ifBlank { friend.status.value },
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1
            )
        },
        text = {

        },
        trailing = {
            // 截取最后登录时间
            // 例如: lastLogin = 2023-04-01T09:03:04.000Z
            val dateTime = friend.lastLogin.split('T')
            if (dateTime.size == 2) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = dateTime[0] ,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1
                    )
                    Text(
                        text = dateTime[1].slice(0..4),
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1
                    )
                }
            }
        },
    )
    Box(Modifier.fillMaxSize().padding(6.dp).clip(MaterialTheme.shapes.medium))

//    Card (
//        colors = CardDefaults.cardColors(
//            containerColor = MaterialTheme.colorScheme.surface,
//            contentColor = MaterialTheme.colorScheme.primary
//        ),
//        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
//    ){
//        Row(
//            modifier = Modifier
//                .fillMaxWidth()
//                .clip(MaterialTheme.shapes.medium)
//                .clickable { toProfile(friend) }
//                .padding(6.dp),
//            verticalAlignment = Alignment.CenterVertically,
//            horizontalArrangement = Arrangement.spacedBy(6.dp)
//        ) {
//            UserStateIcon(
//                modifier = Modifier
//                    .height(56.dp)
//                    .clip(CircleShape),
//                iconUrl = friend.iconUrl,
//                userStatus = friend.status
//            )
//            Column{
//                Row(
//                    horizontalArrangement = Arrangement.spacedBy(2.dp),
//                    verticalAlignment = Alignment.CenterVertically
//                ) {
//                    Icon(
//                        modifier = Modifier
//                            .size(16.dp),
//                        imageVector = Icons.Rounded.Shield,
//                        contentDescription = "TrustRankIcon",
//                        tint = GameColor.Rank.fromValue(friend.trustRank)
//                    )
//                    Text(
//                        text = friend.displayName,
//                        style = MaterialTheme.typography.titleMedium,
//                        maxLines = 1
//                    )
//                }
//
//                Text(
//                    modifier = Modifier.alpha(0.6f),
//                    text = friend.statusDescription.ifBlank { friend.status.value },
//                    style = MaterialTheme.typography.labelMedium,
//                    maxLines = 1
//                )
//            }
//            Spacer(Modifier.weight(1f))
//        }
//    }
}
