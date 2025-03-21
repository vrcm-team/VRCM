package io.github.vrcmteam.vrcm.presentation.screens.home.pager

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import cafe.adriel.voyager.koin.koinScreenModel
import io.github.vrcmteam.vrcm.presentation.compoments.UserSearchList
import io.github.vrcmteam.vrcm.presentation.supports.AppIcons
import io.github.vrcmteam.vrcm.presentation.supports.Pager

object SearchListPager : Pager {
    override val index: Int
        get() = 2

    override val title: String
        @Composable
        get() = "Search"

    override val icon: Painter
        @Composable
        get() = rememberVectorPainter(image = AppIcons.PersonSearch)

    @Composable
    override fun Content() {
        val searchListPagerModel:SearchListPagerModel = koinScreenModel()

        UserSearchList(
            key = title,
            userListInit = { searchListPagerModel.searchList },
        ) { searchText, _ ->
            searchListPagerModel.refreshSearchList(searchText)
        }
    }
}




