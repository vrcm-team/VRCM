package io.github.vrcmteam.vrcm.presentation.screens.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import io.github.vrcmteam.vrcm.core.shared.SharedFlowCentre
import io.github.vrcmteam.vrcm.network.api.auth.data.CurrentUserData
import io.github.vrcmteam.vrcm.network.api.notification.NotificationApi
import io.github.vrcmteam.vrcm.network.api.notification.data.NotificationData
import io.github.vrcmteam.vrcm.network.supports.VRCApiException
import io.github.vrcmteam.vrcm.presentation.supports.AuthSupporter
import io.ktor.util.network.UnresolvedAddressException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch
import network.api.notification.data.ResponseData


class HomeScreenModel(
    private val authSupporter: AuthSupporter,
    private val notificationApi: NotificationApi
) : ScreenModel {

    private val _currentUser = mutableStateOf<CurrentUserData?>(null)

    val currentUser by _currentUser

    private val _notifications = mutableStateOf<List<NotificationData>>(emptyList())
    val notifications by _notifications

    fun ini()  {
        refreshCurrentUser()
        refreshNotifications()
    }

    fun refreshNotifications() =
        screenModelScope.launch(Dispatchers.IO) {
            authSupporter.reTryAuth { notificationApi.fetchNotifications() }
                .onHomeFailure()
                .onSuccess {
                    _notifications.value = it
                }
        }


    fun responseNotification(id: String, response: ResponseData) {
        screenModelScope.launch(Dispatchers.IO) {
            authSupporter.reTryAuth { notificationApi.responseNotification(id,response) }
                .onHomeFailure()
                .onSuccess {
                    refreshNotifications()
                }
        }
    }

    private fun refreshCurrentUser() =
        screenModelScope.launch(Dispatchers.IO) {
            authSupporter.reTryAuth { authSupporter.currentUser(isRefresh = true) }
                .onHomeFailure()
                .onSuccess {
                    _currentUser.value = it
                }
        }


    private fun <T> Result<T>.onHomeFailure() =
        onFailure {
            val message = when (it) {
                is UnresolvedAddressException -> {
                    "Failed to Home: ${it.message}"
                }

                is VRCApiException -> {
                    "Failed to Home: ${it.message}"
                }

                else -> "${it.message}"
            }
            screenModelScope.launch {
                SharedFlowCentre.error.emit(message)
            }
        }
}






