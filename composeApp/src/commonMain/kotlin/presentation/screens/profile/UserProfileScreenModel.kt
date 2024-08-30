package io.github.vrcmteam.vrcm.presentation.screens.profile

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import io.github.vrcmteam.vrcm.core.extensions.pretty
import io.github.vrcmteam.vrcm.core.shared.SharedFlowCentre
import io.github.vrcmteam.vrcm.network.api.users.UsersApi
import io.github.vrcmteam.vrcm.network.api.users.data.UserData
import io.github.vrcmteam.vrcm.presentation.compoments.ToastText
import io.github.vrcmteam.vrcm.presentation.screens.profile.data.UserProfileVo
import io.github.vrcmteam.vrcm.service.AuthService
import io.ktor.client.call.*
import io.ktor.client.statement.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch

class UserProfileScreenModel(
    private val authService: AuthService,
    private val usersApi: UsersApi
) : ScreenModel {

    private val _userState = mutableStateOf<UserProfileVo?>(null)
    val userState by _userState
    private val _userJson = mutableStateOf("")
    val userJson by _userJson
    fun initUserState(userProfileVO: UserProfileVo) {
        if (_userState.value == null) _userState.value = userProfileVO
    }
    suspend fun refreshUser(userId: String) =
        screenModelScope.launch(Dispatchers.IO) {
            authService.reTryAuthCatching {
                usersApi.fetchUserResponse(userId)
            }.onFailure {
                SharedFlowCentre.toastText.emit(ToastText.Error(it.message.toString()))
            }.onSuccess {
                _userState.value = UserProfileVo(it.body<UserData>())
                _userJson.value = it.bodyAsText().pretty()
            }
    }.join()
}
