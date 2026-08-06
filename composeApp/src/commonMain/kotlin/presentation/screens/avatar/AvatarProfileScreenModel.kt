package io.github.vrcmteam.vrcm.presentation.screens.avatar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.vrcmteam.vrcm.core.shared.SharedFlowCentre
import io.github.vrcmteam.vrcm.network.api.avatars.AvatarsApi
import io.github.vrcmteam.vrcm.network.api.avatars.data.AvatarData
import io.github.vrcmteam.vrcm.network.supports.VRCApiException
import io.github.vrcmteam.vrcm.presentation.compoments.ToastText
import io.github.vrcmteam.vrcm.presentation.screens.avatar.data.AvatarProfileVo
import io.github.vrcmteam.vrcm.service.AuthService
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.launch
import org.koin.core.logger.Logger

internal fun interface AvatarProfileLoader {
    suspend fun load(avatarId: String): Result<AvatarData>
}

internal class NetworkAvatarProfileLoader(
    private val avatarsApi: AvatarsApi,
    private val authService: AuthService,
) : AvatarProfileLoader {
    override suspend fun load(avatarId: String): Result<AvatarData> =
        authService.reTryAuthCatching { avatarsApi.getAvatarById(avatarId) }
}

internal data class AvatarUserContext(
    val userId: String,
    val currentAvatarId: String,
)

internal interface AvatarSelector {
    val currentUser: Flow<AvatarUserContext?>

    suspend fun select(avatarId: String): Result<Unit>
}

internal class NetworkAvatarSelector(
    private val avatarsApi: AvatarsApi,
    private val authService: AuthService,
    private val logger: Logger,
) : AvatarSelector {
    override val currentUser: Flow<AvatarUserContext?> = authService.currentUserState.map { user ->
        user?.let {
            AvatarUserContext(
                userId = it.id,
                currentAvatarId = it.currentAvatar,
            )
        }
    }

    override suspend fun select(avatarId: String): Result<Unit> =
        authService.reTryAuthCatching { avatarsApi.selectAvatar(avatarId) }
            .onSuccess { selection ->
                authService.applyCurrentAvatarUpdate(selection.currentAvatar)
            }
            .onFailure { error ->
                logger.error(avatarSelectionFailureLog(avatarId, error))
            }
            .map { }
}

private fun avatarSelectionFailureLog(avatarId: String, error: Throwable): String {
    val request = "method=PUT path=/avatars/$avatarId/select"
    return if (error is VRCApiException) {
        "Avatar selection failed: $request status=${error.code} " +
            "description=${error.description} body=${error.bodyText}"
    } else {
        "Avatar selection failed: $request error=${error.message.orEmpty()}"
    }
}

internal sealed interface AvatarActionAvailability {
    data object Checking : AvatarActionAvailability
    data object Current : AvatarActionAvailability
    data object Banned : AvatarActionAvailability
    data object Own : AvatarActionAvailability
    data object Copyable : AvatarActionAvailability
    data object NotCopyable : AvatarActionAvailability
    data object CheckFailed : AvatarActionAvailability
}

internal data class AvatarActionState(
    val availability: AvatarActionAvailability = AvatarActionAvailability.Checking,
    val isSelecting: Boolean = false,
)

private enum class AvatarValidation {
    Checking,
    Available,
    Banned,
    Failed,
}

internal sealed interface AvatarProfileNotice {
    data object Banned : AvatarProfileNotice
    data object Switched : AvatarProfileNotice
    data object Copied : AvatarProfileNotice
    data class SelectionFailed(val message: String?) : AvatarProfileNotice
}

private enum class AvatarSelectionKind {
    Switch,
    Copy,
}

class AvatarProfileScreenModel internal constructor(
    private val avatarProfileLoader: AvatarProfileLoader,
    private val avatarSelector: AvatarSelector,
    private val requestDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {

    private val _avatarProfileState = MutableStateFlow<AvatarProfileVo?>(null)
    val avatarProfileState: StateFlow<AvatarProfileVo?> = _avatarProfileState.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _notices = MutableSharedFlow<AvatarProfileNotice>(extraBufferCapacity = 1)
    internal val notices: SharedFlow<AvatarProfileNotice> = _notices.asSharedFlow()

    private val validation = MutableStateFlow(AvatarValidation.Checking)
    private val isSelecting = MutableStateFlow(false)
    internal val actionState: StateFlow<AvatarActionState> = combine(
        avatarProfileState,
        validation,
        avatarSelector.currentUser,
        isSelecting,
    ) { avatar, currentValidation, user, selecting ->
        AvatarActionState(
            availability = avatarActionAvailability(avatar, currentValidation, user),
            isSelecting = selecting,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = AvatarActionState(),
    )

    private val latestRequestToken = MutableStateFlow(0L)

    fun refreshAvatarData(avatarProfileVo: AvatarProfileVo) {
        val requestToken = latestRequestToken.updateAndGet { it + 1 }
        validation.value = AvatarValidation.Checking
        _avatarProfileState.value = avatarProfileVo
        val avatarId = avatarProfileVo.avatarId
        if (avatarId.isBlank()) {
            _isLoading.value = false
            validation.value = AvatarValidation.Failed
            return
        }
        _isLoading.value = true
        viewModelScope.launch(requestDispatcher) {
            avatarProfileLoader.load(avatarId)
                .onSuccess { avatarData ->
                    if (requestToken == latestRequestToken.value) {
                        _avatarProfileState.value = AvatarProfileVo(avatarData)
                        validation.value = AvatarValidation.Available
                    }
                }
                .onFailure { error ->
                    if (requestToken != latestRequestToken.value) return@onFailure

                    if (error is VRCApiException && error.code == 404) {
                        validation.value = AvatarValidation.Banned
                        _notices.emit(AvatarProfileNotice.Banned)
                    } else {
                        validation.value = AvatarValidation.Failed
                        SharedFlowCentre.toastText.emit(
                            ToastText.Error(error.message ?: "Failed to load avatar data")
                        )
                    }
                }
            if (requestToken == latestRequestToken.value) {
                _isLoading.value = false
            }
        }
    }

    fun selectAvatar() {
        val avatarId = avatarProfileState.value?.avatarId ?: return
        val selectionKind = when (actionState.value.availability) {
            AvatarActionAvailability.Own -> AvatarSelectionKind.Switch
            AvatarActionAvailability.Copyable -> AvatarSelectionKind.Copy
            else -> return
        }
        if (!isSelecting.compareAndSet(expect = false, update = true)) return

        viewModelScope.launch(requestDispatcher) {
            avatarSelector.select(avatarId)
                .onSuccess {
                    _notices.emit(
                        when (selectionKind) {
                            AvatarSelectionKind.Switch -> AvatarProfileNotice.Switched
                            AvatarSelectionKind.Copy -> AvatarProfileNotice.Copied
                        }
                    )
                }
                .onFailure { error ->
                    if (error is VRCApiException && error.code == 404) {
                        validation.value = AvatarValidation.Banned
                        _notices.emit(AvatarProfileNotice.Banned)
                    } else {
                        _notices.emit(AvatarProfileNotice.SelectionFailed(error.message))
                    }
                }
            isSelecting.value = false
        }
    }
}

private fun avatarActionAvailability(
    avatar: AvatarProfileVo?,
    validation: AvatarValidation,
    user: AvatarUserContext?,
): AvatarActionAvailability {
    if (avatar == null || user == null) return AvatarActionAvailability.Checking
    if (avatar.avatarId == user.currentAvatarId) return AvatarActionAvailability.Current

    return when (validation) {
        AvatarValidation.Checking -> AvatarActionAvailability.Checking
        AvatarValidation.Banned -> AvatarActionAvailability.Banned
        AvatarValidation.Failed -> AvatarActionAvailability.CheckFailed
        AvatarValidation.Available -> when {
            avatar.authorId == user.userId -> AvatarActionAvailability.Own
            avatar.releaseStatus == "public" -> AvatarActionAvailability.Copyable
            else -> AvatarActionAvailability.NotCopyable
        }
    }
}
