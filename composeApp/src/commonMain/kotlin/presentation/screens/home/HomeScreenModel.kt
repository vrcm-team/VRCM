package io.github.vrcmteam.vrcm.presentation.screens.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import io.github.vrcmteam.vrcm.network.api.attributes.LocationType
import io.github.vrcmteam.vrcm.network.api.auth.data.CurrentUserData
import io.github.vrcmteam.vrcm.network.supports.VRCApiException
import io.github.vrcmteam.vrcm.presentation.screens.home.data.FriendLocation
import io.github.vrcmteam.vrcm.presentation.screens.home.page.FriendLocationPageModel
import io.github.vrcmteam.vrcm.presentation.supports.AuthSupporter
import io.ktor.util.network.UnresolvedAddressException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch


class HomeScreenModel(
    private val authSupporter: AuthSupporter,
    private val friendLocationPageModel: FriendLocationPageModel
) : ScreenModel {

    val friendLocationMap: MutableMap<LocationType, MutableList<FriendLocation>>
        get() = friendLocationPageModel.friendLocationMap

    private val _currentUser = mutableStateOf<CurrentUserData?>(null)

    private val _errorMessage = mutableStateOf("")

    val errorMessage by _errorMessage

    val currentUser by _currentUser

    fun ini(onError: () -> Unit) = screenModelScope.launch(Dispatchers.IO) {
        authSupporter.reTryAuth { authSupporter.currentUser() }
            .onHomeFailure(onError)
            .onSuccess { _currentUser.value = it }
    }


    suspend fun refreshFriendLocationPage(onFailure: () -> Unit) {
        val onFailureCallback:Result<*>.() -> Unit = {
            onHomeFailure(onFailure)
        }
        friendLocationPageModel.refreshFriendLocationPage(onFailureCallback)
    }


    fun onErrorMessageChange(errorMessage: String) {
        _errorMessage.value = errorMessage
    }

    private fun <T> Result<T>.onHomeFailure(onFailure: () -> Unit) =
        onFailure {
            val message = when (it) {
                is UnresolvedAddressException -> {
                    "Failed to Auth: ${it.message}"
                }

                is VRCApiException -> {
                    "Failed to Auth: ${it.message}"
                }

                else -> "${it.message}"
            }
            onErrorMessageChange(message)
            screenModelScope.launch(Dispatchers.Main) {
                delay(2000L)
                onFailure()
            }
        }
}






