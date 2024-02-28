package io.github.vrcmteam.vrcm.screens.profile

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import io.github.vrcmteam.vrcm.data.api.users.UserData
import io.github.vrcmteam.vrcm.data.api.users.UsersApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch

class ProfileScreenModel(
    private val usersApi: UsersApi
) : ScreenModel {

    private val _userState = mutableStateOf<UserData?>(null)
    val userState by _userState
    suspend fun refreshUser(userId: String, onError: () -> Unit) = screenModelScope.launch(Dispatchers.IO) {
        usersApi.fetchUser(userId)
            .onFailure {
                onError()
            }.onSuccess {
                _userState.value = it
            }
    }.join()
}