package io.github.vrcmteam.vrcm.presentation.screens.home.pager

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import io.github.vrcmteam.vrcm.network.api.users.UsersApi
import io.github.vrcmteam.vrcm.network.api.users.data.SearchUserData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch

class SearchListPagerModel(
    private val usersApi: UsersApi
) : ScreenModel {

    var searchList: List<SearchUserData> = emptyList()
    private set

    suspend fun refreshSearchList(name: String) = this.screenModelScope.launch(Dispatchers.IO) {
        searchList = usersApi.searchUser(name)
    }.join()


}