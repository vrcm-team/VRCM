package io.github.vrcmteam.vrcm.presentation.screens.home.pager

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import io.github.vrcmteam.vrcm.core.shared.SharedFlowCentre
import io.github.vrcmteam.vrcm.network.api.users.UsersApi
import io.github.vrcmteam.vrcm.network.api.users.data.SearchUserData
import io.github.vrcmteam.vrcm.presentation.extensions.onApiFailure
import io.github.vrcmteam.vrcm.service.AuthService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import org.koin.core.logger.Logger

class SearchListPagerModel(
    private val usersApi: UsersApi,
    private val authService: AuthService,
    private val logger: Logger
) : ScreenModel {

    var searchList: List<SearchUserData> = emptyList()
    private set

    private var preSearchText: String = ""

    init {
        // 监听登录状态,用于重新登录后更新刷新状态
        screenModelScope.launch {
            SharedFlowCentre.authed.collect {
                searchList = emptyList()
            }
        }
    }

    suspend fun refreshSearchList(name: String) = this.screenModelScope.async(Dispatchers.IO) {
        if (name != preSearchText && name.isNotEmpty() ){
            return@async authService.reTryAuthCatching {
                usersApi.searchUser(name)
            }.onSuccess {
                searchList = it
            }.onApiFailure("SearchList"){
                logger.error(it)
            }.also {
                preSearchText = name
            }.isSuccess
        }else{
            preSearchText = name
            return@async false
        }
    }.await()


}