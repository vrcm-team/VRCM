package io.github.vrcmteam.vrcm.presentation.screens.home.pager

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.unit.dp
import io.github.vrcmteam.vrcm.network.api.attributes.IUser
import io.github.vrcmteam.vrcm.network.api.attributes.UserStatus
import io.github.vrcmteam.vrcm.network.api.friends.date.FriendData
import io.github.vrcmteam.vrcm.presentation.compoments.RefreshBox
import io.github.vrcmteam.vrcm.presentation.compoments.UserStateIcon
import io.github.vrcmteam.vrcm.presentation.extensions.currentNavigator
import io.github.vrcmteam.vrcm.presentation.extensions.getInsetPadding
import io.github.vrcmteam.vrcm.presentation.screens.profile.UserProfileScreen
import io.github.vrcmteam.vrcm.presentation.screens.profile.data.UserProfileVO
import io.github.vrcmteam.vrcm.presentation.supports.PagerProvider
import io.github.vrcmteam.vrcm.presentation.theme.GameColor
import org.koin.compose.koinInject

object FriendListPagerProvider : PagerProvider {

    override val index: Int
        get() = 1
    override val title: String
        @Composable
        get() = "Friend"

    override val icon: Painter
        @Composable get() = rememberVectorPainter(image = Icons.Rounded.Group)

    @Composable
    override fun Content(state: LazyListState) {
        val friendListPagerModel: FriendListPagerModel = koinInject()
        // 控制只有第一次跳转到当前页面时自动刷新
        var isRefreshing by rememberSaveable(title) { mutableStateOf(true) }
        val friendList = friendListPagerModel.friendList.sortedByDescending {
            (if (it.status == UserStatus.Offline) "0" else "1") + it.lastLogin + it.displayName
        }
        FriendListPager(
            friendList = friendList,
            isRefreshing = isRefreshing,
            state = state,
            doRefresh = {
                friendListPagerModel.refreshFriendList()
                isRefreshing = false
            }
        )
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
                UserProfileVO(user)
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

@Composable
fun FriendListItem(friend: FriendData, toProfile: (FriendData) -> Unit) {
    ListItem(
        modifier = Modifier
            .fillMaxWidth()
            .height(68.dp)
            .padding(horizontal = 6.dp)
            .clip(MaterialTheme.shapes.large)
            .clickable { toProfile(friend) },
        leadingContent = {
            UserStateIcon(
                modifier = Modifier
                    .size(60.dp),
                iconUrl = friend.iconUrl,
                userStatus = friend.status
            )
        },
        headlineContent = {
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
        supportingContent = {
            Text(
                text = friend.statusDescription.ifBlank { friend.status.value },
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1
            )
        },

        trailingContent = {
            // 截取最后登录时间
            // 例如: lastLogin = 2023-04-01T09:03:04.000Z
            val dateTime = friend.lastLogin.split('T')
            if (dateTime.size == 2) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = dateTime[0],
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
}
