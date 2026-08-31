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
import kotlinx.coroutines.CancellationException
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
    data object InvalidName : AvatarProfileNotice
    data object NoMetadataChanges : AvatarProfileNotice
    data object MetadataSaved : AvatarProfileNotice
    data class MetadataSaveFailed(val message: String?) : AvatarProfileNotice
    data object CoverSaved : AvatarProfileNotice
    data object Deleted : AvatarProfileNotice
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
    private val avatarDeleter: AvatarDeleter? = null,
    private val avatarDeletionResults: AvatarDeletionResultStore? = null,
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
    internal val editState: StateFlow<AvatarEditState> = combine(
        avatarProfileState,
        validation,
        currentUser,
        isSavingMetadata,
    ) { avatar, currentValidation, user, savingMetadata ->
        AvatarEditState(
            canEdit = currentValidation == AvatarValidation.Available &&
                avatar?.authorId?.isNotBlank() == true &&
                avatar.authorId == user?.userId,
            isSavingMetadata = savingMetadata,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = AvatarEditState(),
    )

    private val deletionOperation = MutableStateFlow(AvatarDeletionOperation())
    internal val deletionState: StateFlow<AvatarDeletionState> = combine(
        avatarProfileState,
        validation,
        favoriteSession,
        deletionOperation,
        isSelecting,
    ) { avatar, currentValidation, session, operation, selecting ->
        val canDelete = avatarDeleter != null && avatarDeletionResults != null &&
            !selecting &&
            currentValidation == AvatarValidation.Available &&
            avatar?.avatarId?.isNotBlank() == true &&
            avatar.authorId.isNotBlank() &&
            avatar.authorId == session?.token?.userId &&
            avatar.releaseStatus != DELETED_AVATAR_RELEASE_STATUS
        val confirmation = operation.target?.takeIf { target ->
            avatar?.avatarId == target.avatarId &&
                session?.token?.userId == target.sessionToken.userId &&
                (operation.isDeleting || session?.token == target.sessionToken)
        }
        AvatarDeletionState(
            canDelete = canDelete,
            confirmation = confirmation,
            isDeleting = operation.isDeleting,
            failure = operation.failure.takeIf { confirmation != null },
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = AvatarDeletionState(),
    )

    private val latestRequestToken = MutableStateFlow(0L)

    fun refreshAvatarData(avatarProfileVo: AvatarProfileVo) {
        val requestToken = latestRequestToken.updateAndGet { it + 1 }
        if (!deletionOperation.value.isDeleting) {
            deletionOperation.value = AvatarDeletionOperation()
        }
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
        if (deletionOperation.value.target != null) return
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
                if (!isSavingMetadata.compareAndSet(expect = false, update = true)) return
                viewModelScope.launch(requestDispatcher) {
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
                                _notices.emit(AvatarProfileNotice.MetadataSaveFailed(error.message))
                            }
                        }
                    isSavingMetadata.value = false
                }
            }
        }
    }

    internal fun requestAvatarDeletion() {
        if (deletionOperation.value.isDeleting) return
        val target = currentDeletionTarget() ?: return
        deletionOperation.value = AvatarDeletionOperation(target = target)
    }

    internal fun dismissAvatarDeletion() {
        if (deletionOperation.value.isDeleting) return
        deletionOperation.value = AvatarDeletionOperation()
    }

    internal fun confirmAvatarDeletion() {
        val deleter = avatarDeleter ?: return
        val operation = deletionOperation.value
        val target = operation.target ?: return
        if (operation.isDeleting) return
        if (currentDeletionTarget() != target) {
            deletionOperation.value = AvatarDeletionOperation()
            return
        }
        if (!deletionOperation.compareAndSet(
                expect = operation,
                update = operation.copy(isDeleting = true, failure = null),
            )
        ) {
            return
        }

        viewModelScope.launch(requestDispatcher) {
            val response = try {
                deleter.delete(target.sessionToken, target.avatarId)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                AuthenticatedAvatarDeletion(Result.failure(error), target.sessionToken)
            }
            if (response == null || !acceptDeletionSession(response.sessionToken, target)) {
                finishDeletion(target)
                return@launch
            }
            response.result.fold(
                onSuccess = { deleted -> handleDeletionSuccess(target, response.sessionToken, deleted) },
                onFailure = { error ->
                    finishDeletionFailure(
                        target = target,
                        responseToken = response.sessionToken,
                        failure = error.toAvatarDeletionFailure(),
                    )
                },
            )
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

    private fun currentDeletionTarget(): AvatarDeletionTarget? {
        if (avatarDeleter == null || avatarDeletionResults == null ||
            isSelecting.value ||
            validation.value != AvatarValidation.Available
        ) {
            return null
        }
        val avatar = avatarProfileState.value ?: return null
        val sessionToken = favoriteSession.value?.token ?: return null
        if (avatar.avatarId.isBlank() || avatar.authorId.isBlank() ||
            avatar.authorId != sessionToken.userId ||
            avatar.releaseStatus == DELETED_AVATAR_RELEASE_STATUS
        ) {
            return null
        }
        return AvatarDeletionTarget(
            sessionToken = sessionToken,
            avatarId = avatar.avatarId,
            avatarName = avatar.avatarName,
        )
    }

    private fun acceptDeletionSession(
        responseToken: AccountSessionToken,
        target: AvatarDeletionTarget,
    ): Boolean = responseToken.userId == target.sessionToken.userId &&
        favoriteSession.value?.token == responseToken &&
        avatarDeleter?.isCurrentSession(responseToken) == true

    private fun handleDeletionSuccess(
        target: AvatarDeletionTarget,
        responseToken: AccountSessionToken,
        deleted: AvatarData,
    ) {
        val validResponse = deleted.id == target.avatarId &&
            deleted.authorId == target.sessionToken.userId &&
            deleted.releaseStatus == DELETED_AVATAR_RELEASE_STATUS
        if (!validResponse) {
            finishDeletionFailure(
                target = target,
                responseToken = responseToken,
                failure = AvatarDeletionFailure.InvalidResponse,
            )
            return
        }
        val recorded = avatarDeletionResults?.record(responseToken, deleted) == true
        if (!recorded) {
            finishDeletion(target)
            return
        }

        val current = avatarProfileState.value
        if (current != null &&
            current.avatarId == target.avatarId &&
            current.authorId == responseToken.userId &&
            favoriteSession.value?.token == responseToken
        ) {
            _avatarProfileState.value = AvatarProfileVo(deleted)
            validation.value = AvatarValidation.Available
            finishDeletion(target)
            _notices.tryEmit(AvatarProfileNotice.Deleted)
        } else {
            finishDeletion(target)
        }
    }

    private fun finishDeletionFailure(
        target: AvatarDeletionTarget,
        responseToken: AccountSessionToken,
        failure: AvatarDeletionFailure,
    ) {
        val current = avatarProfileState.value
        if (current == null ||
            current.avatarId != target.avatarId ||
            current.authorId != responseToken.userId ||
            favoriteSession.value?.token != responseToken
        ) {
            finishDeletion(target)
            return
        }
        val currentOperation = deletionOperation.value
        if (currentOperation.target != target) return
        deletionOperation.value = currentOperation.copy(
            target = target.copy(sessionToken = responseToken),
            isDeleting = false,
            failure = failure,
        )
    }

    private fun finishDeletion(target: AvatarDeletionTarget) {
        if (deletionOperation.value.target == target) {
            deletionOperation.value = AvatarDeletionOperation()
        }
    }
}

private data class AvatarDeletionOperation(
    val target: AvatarDeletionTarget? = null,
    val isDeleting: Boolean = false,
    val failure: AvatarDeletionFailure? = null,
)

private data class AvatarEditTarget(
    val avatarId: String,
    val userId: String,
)

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
