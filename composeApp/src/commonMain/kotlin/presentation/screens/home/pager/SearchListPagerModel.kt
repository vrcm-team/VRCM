package io.github.vrcmteam.vrcm.presentation.screens.home.pager

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import io.github.vrcmteam.vrcm.network.api.users.UsersApi
import io.github.vrcmteam.vrcm.network.api.users.data.SearchUserData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.async

class SearchListPagerModel(
    private val usersApi: UsersApi
) : ScreenModel {

    var searchList: List<SearchUserData> = emptyList()
    private set

    private var preSearchText: String = ""


    suspend fun refreshSearchList(name: String) = this.screenModelScope.async(Dispatchers.IO) {
        if (name != preSearchText && name.isNotEmpty() ){
            searchList = usersApi.searchUser(name)
            preSearchText = name
            return@async true
        }else{
            preSearchText = name
            return@async false
        }
    }.await()


}