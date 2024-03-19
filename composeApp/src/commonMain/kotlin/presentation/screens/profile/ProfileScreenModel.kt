package io.github.vrcmteam.vrcm.presentation.screens.profile

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import io.github.vrcmteam.vrcm.network.api.attributes.IUser
import io.github.vrcmteam.vrcm.network.api.users.UsersApi
import io.github.vrcmteam.vrcm.presentation.supports.AuthSupporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch

class ProfileScreenModel(
    private val authSupporter: AuthSupporter,
    private val usersApi: UsersApi
) : ScreenModel {

    private val _userState = mutableStateOf<IUser?>(null)
    val userState by _userState

   fun initUserState(user:IUser){
       if ( _userState.value == null) _userState.value = user
   }
    suspend fun refreshUser(userId: String, onError: () -> Unit) =
        screenModelScope.launch(Dispatchers.IO) {
            authSupporter.reTryAuth {
                usersApi.fetchUser(userId)
            }.onFailure {
                onError()
            }.onSuccess {
                _userState.value = it
            }
    }.join()
}