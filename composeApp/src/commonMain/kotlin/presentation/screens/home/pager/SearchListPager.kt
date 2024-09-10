package io.github.vrcmteam.vrcm.presentation.screens.home.pager

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PersonSearch
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import cafe.adriel.voyager.koin.getScreenModel
import io.github.vrcmteam.vrcm.presentation.compoments.UserSearchList
import io.github.vrcmteam.vrcm.presentation.supports.Pager

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
        val searchListPagerModel:SearchListPagerModel = getScreenModel()

        UserSearchList(
            key = title,
            userListInit = { searchListPagerModel.searchList },
        ) { searchText, userList ->
            if (searchListPagerModel.refreshSearchList(searchText)){
                userList.clear()
                userList += searchListPagerModel.searchList
            }
        }
    }
}




