package io.github.vrcmteam.vrcm.presentation.screens.user

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.vrcmteam.vrcm.core.shared.SharedFlowCentre
import io.github.vrcmteam.vrcm.network.api.users.UsersApi
import io.github.vrcmteam.vrcm.network.api.users.data.MutualFriendData
import io.github.vrcmteam.vrcm.presentation.compoments.ToastText
import io.github.vrcmteam.vrcm.service.AuthService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch
import org.koin.core.logger.Logger

class MutualFriendsScreenModel(
    private val authService: AuthService,
    private val usersApi: UsersApi,
    private val logger: Logger,
) : ViewModel() {

    private var loadInProgress = false

    var mutualFriends by mutableStateOf<List<MutualFriendData>>(emptyList())
        private set

    var hasLoadedSuccessfully by mutableStateOf(false)
        private set

    // The screen starts in a loading state so its first frame cannot show the empty state before
    // LaunchedEffect has had a chance to start the request.
    var isLoading by mutableStateOf(true)
        private set

    var errorMessage by mutableStateOf<String?>(null)
        private set

    fun load(userId: String) {
        if (loadInProgress) return
        loadInProgress = true
        viewModelScope.launch(Dispatchers.IO) {
            isLoading = true
            errorMessage = null
            try {
                fetchAllMutualFriends(userId)
                    .onSuccess {
                        mutualFriends = it
                        hasLoadedSuccessfully = true
                    }
                    .onFailure { error ->
                        val message = error.message.orEmpty()
                        logger.error(message)
                        SharedFlowCentre.toastText.emit(ToastText.Error(message))
                        errorMessage = message
                    }
            } finally {
                isLoading = false
                loadInProgress = false
            }
        }
    }

    private suspend fun fetchAllMutualFriends(userId: String): Result<List<MutualFriendData>> {
        val all = mutableListOf<MutualFriendData>()
        var offset = 0
        val limit = 100
        while (true) {
            val pageResult = authService.reTryAuthCatching {
                usersApi.getMutualFriends(userId, n = limit, offset = offset)
            }
            if (pageResult.isFailure) {
                return Result.failure(
                    pageResult.exceptionOrNull()
                        ?: IllegalStateException("Failed to load mutual friends")
                )
            }
            val page = pageResult.getOrDefault(emptyList())
            all.addAll(page)
            if (page.size < limit) break
            offset += limit
        }
        return Result.success(all)
    }
}
