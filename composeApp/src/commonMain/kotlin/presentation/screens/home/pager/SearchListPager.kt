package io.github.vrcmteam.vrcm.presentation.screens.home.pager

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PersonSearch
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import io.github.vrcmteam.vrcm.network.api.users.UsersApi
import io.github.vrcmteam.vrcm.presentation.compoments.UserSearchList
import io.github.vrcmteam.vrcm.presentation.supports.Pager
import org.koin.compose.koinInject

object SearchListPager : Pager {
    override val index: Int
        get() = 2

    override val title: String
        @Composable
        get() = "Search"

    override val icon: Painter
        @Composable
        get() = rememberVectorPainter(image = Icons.Rounded.PersonSearch)

    @Composable
    override fun Content() {
        val usersApi: UsersApi = koinInject()

        UserSearchList(title) { searchText, userList ->
            userList.clear()
            if (searchText.isEmpty()) return@UserSearchList
            userList += usersApi.searchUser(searchText)
        }
    }
}




