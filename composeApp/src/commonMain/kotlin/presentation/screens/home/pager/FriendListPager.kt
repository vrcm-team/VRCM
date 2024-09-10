package io.github.vrcmteam.vrcm.presentation.screens.home.pager

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Group
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import cafe.adriel.voyager.koin.getScreenModel
import io.github.vrcmteam.vrcm.presentation.compoments.UserSearchList
import io.github.vrcmteam.vrcm.presentation.supports.Pager

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
        UserSearchList(
            key = title,
            userListInit = friendListPagerModel::findFriendList,
            isRefreshing = friendListPagerModel.isRefreshing,
            doRefresh = friendListPagerModel::refreshFriendList,
        ) { searchText, userList ->
            userList.clear()
            userList += friendListPagerModel.findFriendList(searchText)
        }
    }
}

