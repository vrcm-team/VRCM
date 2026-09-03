package io.github.vrcmteam.vrcm.presentation.screens.avatar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.vrcmteam.vrcm.core.shared.AccountSessionToken
import io.github.vrcmteam.vrcm.core.shared.AuthenticatedAccount
import io.github.vrcmteam.vrcm.core.shared.SharedFlowCentre
import io.github.vrcmteam.vrcm.network.api.attributes.FavoriteType
import io.github.vrcmteam.vrcm.network.api.avatars.AvatarsApi
import io.github.vrcmteam.vrcm.network.api.avatars.data.AvatarData
import io.github.vrcmteam.vrcm.network.api.avatars.data.AvatarStyle
import io.github.vrcmteam.vrcm.network.supports.VRCApiException
import io.github.vrcmteam.vrcm.presentation.compoments.ToastText
import io.github.vrcmteam.vrcm.presentation.favorites.FavoriteEntrySource
import io.github.vrcmteam.vrcm.presentation.favorites.FavoriteEntryState
import io.github.vrcmteam.vrcm.presentation.favorites.FavoriteEntryStateModel
import io.github.vrcmteam.vrcm.presentation.screens.avatar.data.AvatarProfileVo
import io.github.vrcmteam.vrcm.service.AuthService
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
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

internal data class AvatarEditState(
    val canEdit: Boolean = false,
    val isSavingMetadata: Boolean = false,
    val styles: AvatarStylesLoadState = AvatarStylesLoadState.NotLoaded,
)

internal sealed interface AvatarStylesLoadState {
    data object NotLoaded : AvatarStylesLoadState
    data object Loading : AvatarStylesLoadState
    data object Empty : AvatarStylesLoadState
    data class Ready(val options: List<AvatarStyle>) : AvatarStylesLoadState
    data class Failed(val message: String?) : AvatarStylesLoadState
}

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
    data object InvalidName : AvatarProfileNotice
    data object InvalidContentTags : AvatarProfileNotice
    data object InvalidPrimaryStyle : AvatarProfileNotice
    data object InvalidSecondaryStyle : AvatarProfileNotice
    data object NoMetadataChanges : AvatarProfileNotice
    data object MetadataSaved : AvatarProfileNotice
    data class MetadataSaveFailed(val message: String?) : AvatarProfileNotice
    data object CoverSaved : AvatarProfileNotice
}

private enum class AvatarSelectionKind {
    Switch,
    Copy,
}

class AvatarProfileScreenModel internal constructor(
    private val avatarProfileLoader: AvatarProfileLoader,
    private val avatarSelector: AvatarSelector,
    favoriteEntrySource: FavoriteEntrySource,
    private val requestDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val avatarEditor: AvatarEditor? = null,
    private val favoriteSession: StateFlow<AuthenticatedAccount?> = SharedFlowCentre.currentSession,
) : ViewModel() {

    private val _avatarProfileState = MutableStateFlow<AvatarProfileVo?>(null)
    val avatarProfileState: StateFlow<AvatarProfileVo?> = _avatarProfileState.asStateFlow()

    private val favoriteEntry = FavoriteEntryStateModel(
        favoriteType = FavoriteType.Avatar,
        source = favoriteEntrySource,
        scope = viewModelScope,
        dispatcher = requestDispatcher,
        sessionFlow = favoriteSession,
    )
    internal val favoriteEntryState: StateFlow<FavoriteEntryState> = favoriteEntry.state

    internal fun retryFavoriteEntryLoad() {
        favoriteEntry.retry()
    }

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _notices = MutableSharedFlow<AvatarProfileNotice>(extraBufferCapacity = 1)
    internal val notices: SharedFlow<AvatarProfileNotice> = _notices.asSharedFlow()

    private val validation = MutableStateFlow(AvatarValidation.Checking)
    private val isSelecting = MutableStateFlow(false)
    private val currentUser = avatarSelector.currentUser.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = null,
    )
    internal val actionState: StateFlow<AvatarActionState> = combine(
        avatarProfileState,
        validation,
        currentUser,
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

    private val isSavingMetadata = MutableStateFlow(false)
    private val stylesLoadState = MutableStateFlow<AvatarStylesLoadState>(
        AvatarStylesLoadState.NotLoaded
    )
    internal val editState: StateFlow<AvatarEditState> = combine(
        avatarProfileState,
        validation,
        favoriteSession,
        isSavingMetadata,
        stylesLoadState,
    ) { avatar, currentValidation, session, savingMetadata, styles ->
        AvatarEditState(
            canEdit = avatarEditor != null &&
                currentValidation == AvatarValidation.Available &&
                avatar?.authorId?.isNotBlank() == true &&
                avatar.authorId == session?.token?.userId,
            isSavingMetadata = savingMetadata,
            styles = styles,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = AvatarEditState(),
    )

    private val latestRequestToken = MutableStateFlow(0L)
    private var metadataSaveGeneration = 0L
    private var stylesLoadGeneration = 0L
    private var metadataSaveJob: Job? = null
    private var stylesLoadJob: Job? = null

    init {
        viewModelScope.launch {
            favoriteSession.map { it?.token }
                .distinctUntilChanged()
                .drop(1)
                .collect { invalidateMetadataOperations() }
        }
    }

    fun refreshAvatarData(avatarProfileVo: AvatarProfileVo) {
        invalidateMetadataOperations()
        val requestToken = latestRequestToken.updateAndGet { it + 1 }
        validation.value = AvatarValidation.Checking
        _avatarProfileState.value = avatarProfileVo
        val avatarId = avatarProfileVo.avatarId
        favoriteEntry.load(avatarId)
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

    internal fun loadAvatarStyles() {
        val avatar = avatarProfileState.value ?: return
        val editor = avatarEditor ?: return
        val sessionToken = editableSessionToken(avatar) ?: return
        if (stylesLoadState.value == AvatarStylesLoadState.Loading ||
            stylesLoadState.value is AvatarStylesLoadState.Ready
        ) return

        val generation = ++stylesLoadGeneration
        val avatarId = avatar.avatarId
        stylesLoadState.value = AvatarStylesLoadState.Loading
        stylesLoadJob = viewModelScope.launch(requestDispatcher) {
            try {
                val response = editor.loadStyles(sessionToken) ?: return@launch
                if (!acceptsMetadataResponse(response.sessionToken, avatarId)) return@launch
                response.result.fold(
                    onSuccess = { styles ->
                        if (!acceptsMetadataResponse(response.sessionToken, avatarId)) return@fold
                        val options = normalizedAvatarStyles(styles)
                        stylesLoadState.value = if (options.isEmpty()) {
                            AvatarStylesLoadState.Empty
                        } else {
                            AvatarStylesLoadState.Ready(options)
                        }
                    },
                    onFailure = { error ->
                        if (error is CancellationException) throw error
                        if (acceptsMetadataResponse(response.sessionToken, avatarId)) {
                            stylesLoadState.value = AvatarStylesLoadState.Failed(error.message)
                        }
                    },
                )
            } finally {
                if (stylesLoadGeneration == generation) stylesLoadJob = null
            }
        }
    }

    internal fun saveMetadata(draft: AvatarMetadataDraft) {
        val avatar = avatarProfileState.value ?: return
        val editor = avatarEditor ?: return
        val sessionToken = editableSessionToken(avatar) ?: return
        val allowedStyles = (stylesLoadState.value as? AvatarStylesLoadState.Ready)
            ?.options
            .orEmpty()
        when (val change = avatarMetadataChange(avatar, draft, allowedStyles)) {
            AvatarMetadataChange.InvalidName -> {
                _notices.tryEmit(AvatarProfileNotice.InvalidName)
                return
            }
            AvatarMetadataChange.InvalidContentTags -> {
                _notices.tryEmit(AvatarProfileNotice.InvalidContentTags)
                return
            }
            AvatarMetadataChange.InvalidPrimaryStyle -> {
                _notices.tryEmit(AvatarProfileNotice.InvalidPrimaryStyle)
                return
            }
            AvatarMetadataChange.InvalidSecondaryStyle -> {
                _notices.tryEmit(AvatarProfileNotice.InvalidSecondaryStyle)
                return
            }
            AvatarMetadataChange.NoChanges -> {
                _notices.tryEmit(AvatarProfileNotice.NoMetadataChanges)
                return
            }
            is AvatarMetadataChange.Update -> {
                if (!isSavingMetadata.compareAndSet(expect = false, update = true)) return
                val generation = ++metadataSaveGeneration
                val avatarId = avatar.avatarId
                metadataSaveJob = viewModelScope.launch(requestDispatcher) {
                    try {
                        val response = editor.updateMetadata(
                            sessionToken,
                            avatarId,
                            change.data,
                        ) ?: return@launch
                        if (!acceptsMetadataResponse(response.sessionToken, avatarId)) return@launch
                        response.result.fold(
                            onSuccess = { updated ->
                                if (updated.id == avatarId &&
                                    acceptsMetadataResponse(response.sessionToken, avatarId)
                                ) {
                                    _avatarProfileState.value = AvatarProfileVo(updated)
                                    validation.value = AvatarValidation.Available
                                    _notices.emit(AvatarProfileNotice.MetadataSaved)
                                }
                            },
                            onFailure = { error ->
                                if (error is CancellationException) throw error
                                if (acceptsMetadataResponse(response.sessionToken, avatarId)) {
                                    _notices.emit(
                                        AvatarProfileNotice.MetadataSaveFailed(error.message)
                                    )
                                }
                            },
                        )
                    } finally {
                        if (metadataSaveGeneration == generation) {
                            metadataSaveJob = null
                            isSavingMetadata.value = false
                        }
                    }
                }
            }
        }
    }

    internal fun applyCoverUpdate(updated: AvatarData): Boolean {
        val current = _avatarProfileState.value ?: return false
        if (current.avatarId != updated.id) return false

        _avatarProfileState.value = current.copy(
            avatarImageUrl = updated.imageUrl,
            thumbnailImageUrl = updated.thumbnailImageUrl,
            updatedAt = updated.updatedAt,
            version = updated.version,
        )
        _notices.tryEmit(AvatarProfileNotice.CoverSaved)
        return true
    }

    private fun editableSessionToken(avatar: AvatarProfileVo): AccountSessionToken? {
        val sessionToken = favoriteSession.value?.token ?: return null
        if (!editState.value.canEdit || avatar.authorId != sessionToken.userId) return null
        return sessionToken
    }

    private fun acceptsMetadataResponse(
        sessionToken: AccountSessionToken,
        avatarId: String,
    ): Boolean = favoriteSession.value?.token == sessionToken &&
        avatarProfileState.value?.let { avatar ->
            avatar.avatarId == avatarId && avatar.authorId == sessionToken.userId
        } == true

    private fun invalidateMetadataOperations() {
        metadataSaveGeneration++
        stylesLoadGeneration++
        metadataSaveJob?.cancel()
        stylesLoadJob?.cancel()
        metadataSaveJob = null
        stylesLoadJob = null
        isSavingMetadata.value = false
        stylesLoadState.value = AvatarStylesLoadState.NotLoaded
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
