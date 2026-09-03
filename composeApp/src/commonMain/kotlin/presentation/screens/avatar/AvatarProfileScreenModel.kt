package io.github.vrcmteam.vrcm.presentation.screens.avatar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.vrcmteam.vrcm.core.shared.AccountSessionToken
import io.github.vrcmteam.vrcm.core.shared.AuthenticatedAccount
import io.github.vrcmteam.vrcm.core.shared.SharedFlowCentre
import io.github.vrcmteam.vrcm.network.api.attributes.FavoriteType
import io.github.vrcmteam.vrcm.network.api.avatars.AvatarsApi
import io.github.vrcmteam.vrcm.network.api.avatars.data.AvatarData
import io.github.vrcmteam.vrcm.network.api.avatars.data.AvatarUpdateData
import io.github.vrcmteam.vrcm.network.supports.VRCApiException
import io.github.vrcmteam.vrcm.presentation.compoments.ToastText
import io.github.vrcmteam.vrcm.presentation.favorites.FavoriteEntrySource
import io.github.vrcmteam.vrcm.presentation.favorites.FavoriteEntryState
import io.github.vrcmteam.vrcm.presentation.favorites.FavoriteEntryStateModel
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

internal sealed interface AvatarMetadataChange {
    data object InvalidName : AvatarMetadataChange
    data object NoChanges : AvatarMetadataChange
    data class Update(val data: AvatarUpdateData) : AvatarMetadataChange
}

internal fun avatarMetadataChange(
    currentName: String,
    currentDescription: String,
    editedName: String,
    editedDescription: String,
): AvatarMetadataChange {
    val normalizedName = editedName.trim()
    if (normalizedName.isEmpty()) return AvatarMetadataChange.InvalidName

    val name = normalizedName.takeIf { it != currentName }
    val description = editedDescription.takeIf { it != currentDescription }
    if (name == null && description == null) return AvatarMetadataChange.NoChanges

    return AvatarMetadataChange.Update(
        AvatarUpdateData(
            name = name,
            description = description,
        )
    )
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
    val publication: AvatarPublicationStatus? = null,
    val isUpdatingPublication: Boolean = false,
)

internal enum class AvatarPublicationStatus(val apiValue: String) {
    Private("private"),
    Public("public"),
    ;

    companion object {
        fun fromApiValue(value: String): AvatarPublicationStatus? =
            entries.firstOrNull { it.apiValue == value }
    }
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
    data object NoMetadataChanges : AvatarProfileNotice
    data object MetadataSaved : AvatarProfileNotice
    data class MetadataSaveFailed(val message: String?) : AvatarProfileNotice
    data object CoverSaved : AvatarProfileNotice
    data object PublicationMadePublic : AvatarProfileNotice
    data object PublicationMadePrivate : AvatarProfileNotice
    data class PublicationUpdateFailed(
        val reason: AvatarPublicationFailure,
    ) : AvatarProfileNotice
}

internal enum class AvatarPublicationFailure {
    BadRequest,
    Unauthorized,
    Forbidden,
    NotFound,
    Other,
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
    avatarGalleryLoader: AvatarGalleryLoader? = null,
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

    private val avatarGallery = avatarGalleryLoader?.let {
        AvatarGalleryStateController(
            loader = it,
            scope = viewModelScope,
            dispatcher = requestDispatcher,
            session = favoriteSession,
        )
    }
    internal val avatarGalleryState: StateFlow<AvatarGalleryState> =
        avatarGallery?.state ?: MutableStateFlow(AvatarGalleryState())

    internal fun loadMoreAvatarGallery() {
        avatarGallery?.loadMore()
    }

    internal fun retryAvatarGallery() {
        avatarGallery?.retry()
    }

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
    private val isUpdatingPublication = MutableStateFlow(false)
    private val isEditSubmissionInFlight = MutableStateFlow(false)
    private val editProgress = combine(
        isSavingMetadata,
        isUpdatingPublication,
        ::Pair,
    )
    internal val editState: StateFlow<AvatarEditState> = combine(
        avatarProfileState,
        validation,
        currentUser,
        favoriteSession,
        editProgress,
    ) { avatar, currentValidation, user, session, progress ->
        val canEdit = currentValidation == AvatarValidation.Available &&
            avatar?.authorId?.isNotBlank() == true &&
            avatar.authorId == user?.userId
        val publication = if (
            canEdit &&
            session != null &&
            session.account.userId == avatar.authorId &&
            session.token.userId == avatar.authorId
        ) {
            AvatarPublicationStatus.fromApiValue(avatar.releaseStatus)
        } else {
            null
        }
        AvatarEditState(
            canEdit = canEdit,
            isSavingMetadata = progress.first,
            publication = publication,
            isUpdatingPublication = progress.second,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = AvatarEditState(),
    )

    private val latestRequestToken = MutableStateFlow(0L)

    fun refreshAvatarData(avatarProfileVo: AvatarProfileVo) {
        val requestToken = latestRequestToken.updateAndGet { it + 1 }
        validation.value = AvatarValidation.Checking
        _avatarProfileState.value = avatarProfileVo
        val avatarId = avatarProfileVo.avatarId
        avatarGallery?.showAvatar(avatarId)
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

    internal fun saveMetadata(name: String, description: String) {
        val avatar = avatarProfileState.value ?: return
        val editor = avatarEditor ?: return
        val target = editableTarget(avatar) ?: return
        when (val change = avatarMetadataChange(
            currentName = avatar.avatarName,
            currentDescription = avatar.avatarDescription,
            editedName = name,
            editedDescription = description,
        )) {
            AvatarMetadataChange.InvalidName -> {
                _notices.tryEmit(AvatarProfileNotice.InvalidName)
                return
            }
            AvatarMetadataChange.NoChanges -> {
                _notices.tryEmit(AvatarProfileNotice.NoMetadataChanges)
                return
            }
            is AvatarMetadataChange.Update -> {
                if (!isEditSubmissionInFlight.compareAndSet(expect = false, update = true)) return
                isSavingMetadata.value = true
                viewModelScope.launch(requestDispatcher) {
                    try {
                        editor.updateMetadata(target.avatarId, change.data)
                            .onSuccess { updated ->
                                if (updated.id == target.avatarId && isCurrentTarget(target)) {
                                    _avatarProfileState.value =
                                        requireNotNull(_avatarProfileState.value).copy(
                                            avatarName = updated.name,
                                            avatarDescription = updated.description.orEmpty(),
                                            updatedAt = updated.updatedAt,
                                            version = updated.version,
                                        )
                                    _notices.emit(AvatarProfileNotice.MetadataSaved)
                                }
                            }
                            .onFailure { error ->
                                if (isCurrentTarget(target)) {
                                    _notices.emit(
                                        AvatarProfileNotice.MetadataSaveFailed(error.message)
                                    )
                                }
                            }
                    } finally {
                        isSavingMetadata.value = false
                        isEditSubmissionInFlight.value = false
                    }
                }
            }
        }
    }

    internal fun updatePublication(publication: AvatarPublicationStatus) {
        val avatar = avatarProfileState.value ?: return
        val editor = avatarEditor ?: return
        val currentPublication = AvatarPublicationStatus.fromApiValue(avatar.releaseStatus)
            ?: return
        if (publication == currentPublication) return
        val target = publicationTarget(avatar, publication) ?: return
        if (!isEditSubmissionInFlight.compareAndSet(expect = false, update = true)) return
        isUpdatingPublication.value = true

        viewModelScope.launch(requestDispatcher) {
            try {
                val response = editor.updatePublication(
                    sessionToken = target.requestToken,
                    avatarId = target.avatarId,
                    releaseStatus = publication.apiValue,
                ) ?: return@launch
                response.result
                    .onSuccess { updated ->
                        applyPublicationResponse(target, response.sessionToken, updated)
                    }
                    .onFailure { error ->
                        if (isCurrentPublicationTarget(target, response.sessionToken)) {
                            _notices.emit(
                                AvatarProfileNotice.PublicationUpdateFailed(
                                    error.toAvatarPublicationFailure()
                                )
                            )
                        }
                    }
            } finally {
                isUpdatingPublication.value = false
                isEditSubmissionInFlight.value = false
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

    private fun editableTarget(avatar: AvatarProfileVo): AvatarEditTarget? {
        val userId = currentUser.value?.userId ?: return null
        if (!editState.value.canEdit || avatar.authorId != userId) return null
        return AvatarEditTarget(avatarId = avatar.avatarId, userId = userId)
    }

    private fun isCurrentTarget(target: AvatarEditTarget): Boolean =
        avatarProfileState.value?.avatarId == target.avatarId &&
            currentUser.value?.userId == target.userId

    private fun publicationTarget(
        avatar: AvatarProfileVo,
        publication: AvatarPublicationStatus,
    ): AvatarPublicationTarget? {
        if (validation.value != AvatarValidation.Available) return null
        if (AvatarPublicationStatus.fromApiValue(avatar.releaseStatus) == null) return null
        val currentUserId = currentUser.value?.userId ?: return null
        val session = favoriteSession.value ?: return null
        val userId = session.account.userId
        if (
            session.token.userId != userId ||
            currentUserId != userId ||
            avatar.authorId != userId
        ) {
            return null
        }
        return AvatarPublicationTarget(
            avatarId = avatar.avatarId,
            userId = userId,
            requestToken = session.token,
            publication = publication,
        )
    }

    private suspend fun applyPublicationResponse(
        target: AvatarPublicationTarget,
        responseToken: AccountSessionToken,
        updated: AvatarData,
    ) {
        if (!isCurrentPublicationTarget(target, responseToken)) return
        val publication = AvatarPublicationStatus.fromApiValue(updated.releaseStatus)
        if (
            updated.id != target.avatarId ||
            updated.authorId != target.userId ||
            publication != target.publication
        ) {
            _notices.emit(
                AvatarProfileNotice.PublicationUpdateFailed(AvatarPublicationFailure.Other)
            )
            return
        }

        _avatarProfileState.value = AvatarProfileVo(updated)
        _notices.emit(
            when (publication) {
                AvatarPublicationStatus.Private -> AvatarProfileNotice.PublicationMadePrivate
                AvatarPublicationStatus.Public -> AvatarProfileNotice.PublicationMadePublic
            }
        )
    }

    private fun isCurrentPublicationTarget(
        target: AvatarPublicationTarget,
        responseToken: AccountSessionToken,
    ): Boolean {
        val session = favoriteSession.value ?: return false
        val avatar = avatarProfileState.value ?: return false
        return responseToken == session.token &&
            responseToken.userId == target.userId &&
            session.account.userId == target.userId &&
            currentUser.value?.userId == target.userId &&
            validation.value == AvatarValidation.Available &&
            avatar.avatarId == target.avatarId &&
            avatar.authorId == target.userId
    }
}

private data class AvatarEditTarget(
    val avatarId: String,
    val userId: String,
)

private data class AvatarPublicationTarget(
    val avatarId: String,
    val userId: String,
    val requestToken: AccountSessionToken,
    val publication: AvatarPublicationStatus,
)

private fun Throwable.toAvatarPublicationFailure(): AvatarPublicationFailure =
    when ((this as? VRCApiException)?.code) {
        400 -> AvatarPublicationFailure.BadRequest
        401 -> AvatarPublicationFailure.Unauthorized
        403 -> AvatarPublicationFailure.Forbidden
        404 -> AvatarPublicationFailure.NotFound
        else -> AvatarPublicationFailure.Other
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
