package io.github.vrcmteam.vrcm.presentation.screens.home.pager

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.koin.getScreenModel
import io.github.vrcmteam.vrcm.core.shared.SharedFlowCentre
import io.github.vrcmteam.vrcm.network.api.attributes.IUser
import io.github.vrcmteam.vrcm.network.api.attributes.UserStatus
import io.github.vrcmteam.vrcm.network.api.friends.date.FriendData
import io.github.vrcmteam.vrcm.presentation.compoments.ITextField
import io.github.vrcmteam.vrcm.presentation.compoments.RefreshBox
import io.github.vrcmteam.vrcm.presentation.compoments.UserStateIcon
import io.github.vrcmteam.vrcm.presentation.extensions.animateScrollToFirst
import io.github.vrcmteam.vrcm.presentation.extensions.currentNavigator
import io.github.vrcmteam.vrcm.presentation.extensions.getInsetPadding
import io.github.vrcmteam.vrcm.presentation.extensions.ignoredFormat
import io.github.vrcmteam.vrcm.presentation.screens.profile.UserProfileScreen
import io.github.vrcmteam.vrcm.presentation.screens.profile.data.UserProfileVo
import io.github.vrcmteam.vrcm.presentation.settings.locale.strings
import io.github.vrcmteam.vrcm.presentation.supports.Pager
import io.github.vrcmteam.vrcm.presentation.theme.GameColor
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

object FriendListPager : Pager {

    override val index: Int
        get() = 1
    override val title: String
        @Composable
        get() = "Friend"

    override val icon: Painter
        @Composable get() = rememberVectorPainter(image = Icons.Rounded.Group)

    @Composable
    override fun Content() {
        val friendListPagerModel: FriendListPagerModel = getScreenModel()
        // 控制只有第一次跳转到当前页面时自动刷新
        val lazyListState = rememberLazyListState()
        var searchText by rememberSaveable(title) { mutableStateOf("") }
        val friendList = friendListPagerModel.friendList
            .filter {
                searchText.isEmpty() || it.displayName.contains(searchText)
            }.sortedByDescending {
                (if (it.status == UserStatus.Offline) "0" else "1") + it.lastLogin + it.displayName
            }
        LaunchedEffect(Unit){
            SharedFlowCentre.toPagerTop.collect{
                // 防止滑动时手动阻止滑动动画导致任务取消,监听失效的bug
                kotlin.runCatching {
                    lazyListState.animateScrollToFirst()
                }
            }
        }
        FriendListPager(
            friendList = friendList,
            isRefreshing = friendListPagerModel.isRefreshing,
            state = lazyListState,
            doRefresh = friendListPagerModel::refreshFriendList,
            searchBar = {
                val focusManager = LocalFocusManager.current
                ITextField(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    imageVector = Icons.Rounded.Search,
                    hintText = strings.fiendListPagerSearch,
                    textValue = searchText,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                    onValueChange = { searchText = it }
                )
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
    doRefresh: suspend () -> Unit,
    searchBar: @Composable () -> Unit
) {
    val topPadding = getInsetPadding(WindowInsets::getTop) + 80.dp
    RefreshBox(
        refreshContainerOffsetY = topPadding, isStartRefresh = isRefreshing, doRefresh = doRefresh
    ) {
        val currentNavigator = currentNavigator
        val toProfile = { user: IUser ->
            if (currentNavigator.size <= 1) currentNavigator push UserProfileScreen(
                UserProfileVo(user)
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
            item{
                searchBar()
            }
            items(friendList, key = { it.id }) {
                FriendListItem(it, toProfile)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LazyItemScope.FriendListItem(friend: FriendData, toProfile: (FriendData) -> Unit) {
    ListItem(
        modifier = Modifier
            .fillMaxWidth()
            .height(68.dp)
            .padding(horizontal = 6.dp)
            .clip(MaterialTheme.shapes.large)
            .clickable { toProfile(friend) }
            .animateItemPlacement(),
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
            // 离线好友最后登录时间
            // 例如: lastLogin = 2023-04-01T09:03:04.000Z
            if (friend.status != UserStatus.Offline || friend.lastLogin.isBlank()) return@ListItem
            val lastLogin = Instant.parse(friend.lastLogin).toLocalDateTime(TimeZone.currentSystemDefault())
            Text(
                text = lastLogin.ignoredFormat,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1
            )
        },
    )
}
