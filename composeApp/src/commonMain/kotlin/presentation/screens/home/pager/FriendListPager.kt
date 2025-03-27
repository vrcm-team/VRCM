package io.github.vrcmteam.vrcm.presentation.screens.home.pager

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import cafe.adriel.voyager.koin.koinScreenModel
import io.github.vrcmteam.vrcm.presentation.compoments.UserSearchList
import io.github.vrcmteam.vrcm.presentation.supports.AppIcons
import io.github.vrcmteam.vrcm.presentation.supports.Pager

object FriendListPager : Pager {

    override val index: Int
        get() = 1
    override val title: String
        @Composable
        get() = "Friend"

    override val icon: Painter
        @Composable get() = rememberVectorPainter(image = AppIcons.Group)

    @Composable
    override fun Content() {
        val friendListPagerModel: FriendListPagerModel = koinScreenModel()
        val (searchText, updateSearchText) = remember { mutableStateOf("") }

        LaunchedEffect(Unit) {
            // 未clear()的同步刷新一次
            friendListPagerModel. doRefreshFriendList()
        }
        UserSearchList(
            key = title,
            userListInit = friendListPagerModel::findFriendList,
            searchText = searchText,
            updateSearchText = updateSearchText,
            isRefreshing = friendListPagerModel.isRefreshing,
            doRefresh = friendListPagerModel::refreshFriendList,
            onUpdateSearch = { searchText, userList ->
                userList.clear()
                userList += friendListPagerModel.findFriendList(searchText)
            },
            headerContent = { 
                // 好友列表页面不需要额外的头部内容
            }
        )
    }
}

