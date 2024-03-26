package io.github.vrcmteam.vrcm.presentation.screens.profile

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import io.github.vrcmteam.vrcm.network.api.users.UsersApi
import io.github.vrcmteam.vrcm.presentation.screens.profile.data.ProfileUserVO
import io.github.vrcmteam.vrcm.presentation.supports.AuthSupporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch

class ProfileScreenModel(
    private val onFailureCallback:  (String) -> Unit,
    private val authSupporter: AuthSupporter,
    private val usersApi: UsersApi
) : ScreenModel {

    private val _userState = mutableStateOf<ProfileUserVO?>(null)
    val userState by _userState

    fun initUserState(profileUserVO: ProfileUserVO) {
        if (_userState.value == null) _userState.value = profileUserVO
    }
    suspend fun refreshUser(userId: String) =
        screenModelScope.launch(Dispatchers.IO) {
            authSupporter.reTryAuth {
                usersApi.fetchUser(userId)
            }.onFailure {
                onFailureCallback(it.message.toString())
            }.onSuccess {
                _userState.value = ProfileUserVO(it)
            }
    }.join()
}