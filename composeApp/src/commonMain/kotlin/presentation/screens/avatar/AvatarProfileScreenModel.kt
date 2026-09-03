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
import io.github.vrcmteam.vrcm.network.api.avatars.data.AvatarImpostorServiceStatus
import io.github.vrcmteam.vrcm.network.api.avatars.data.AvatarUpdateData
import io.github.vrcmteam.vrcm.network.api.avatars.data.hasImpostor
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
    val publication: AvatarPublicationStatus? = null,
    val isUpdatingPublication: Boolean = false,
)

internal sealed interface AvatarStylesLoadState {
    data object NotLoaded : AvatarStylesLoadState
    data object Loading : AvatarStylesLoadState
    data object Empty : AvatarStylesLoadState
    data class Ready(val options: List<AvatarStyle>) : AvatarStylesLoadState
    data class Failed(val message: String?) : AvatarStylesLoadState
}

internal data class AvatarImpostorState(
    val canBuild: Boolean = false,
    val hasImpostor: Boolean = false,
    val isSubmitting: Boolean = false,
    val taskState: String? = null,
    val isLoadingQueueEstimate: Boolean = false,
    val estimatedQueueSeconds: Int? = null,
    val queueEstimateFailed: Boolean = false,
    val failure: AvatarImpostorFailure? = null,
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
    data object InvalidContentTags : AvatarProfileNotice
    data object InvalidPrimaryStyle : AvatarProfileNotice
    data object InvalidSecondaryStyle : AvatarProfileNotice
    data object NoMetadataChanges : AvatarProfileNotice
    data object MetadataSaved : AvatarProfileNotice
    data class MetadataSaveFailed(val message: String?) : AvatarProfileNotice
    data object CoverSaved : AvatarProfileNotice
    data object Deleted : AvatarProfileNotice
    data object GalleryUploaded : AvatarProfileNotice
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
    avatarImpostorDeletionSource: AvatarImpostorDeletionSource,
    private val favoriteSession: StateFlow<AuthenticatedAccount?> = SharedFlowCentre.currentSession,
    private val avatarDeleter: AvatarDeleter? = null,
    private val avatarDeletionResults: AvatarDeletionResultStore? = null,
    private val avatarImpostorBuilder: AvatarImpostorBuilder? = null,
    avatarGalleryLoader: AvatarGalleryLoader? = null,
) : ViewModel() {

    private val _avatarProfileState = MutableStateFlow<AvatarProfileVo?>(null)
    val avatarProfileState: StateFlow<AvatarProfileVo?> = _avatarProfileState.asStateFlow()

    private val impostorDeletion = AvatarImpostorDeletionStateModel(
        source = avatarImpostorDeletionSource,
        scope = viewModelScope,
        onAvatarReloaded = { updated -> _avatarProfileState.value = AvatarProfileVo(updated) },
        requestDispatcher = requestDispatcher,
        sessionFlow = favoriteSession,
    )
    internal val impostorDeletionState: StateFlow<AvatarImpostorDeletionUiState> =
        impostorDeletion.state
    internal val impostorDeletionNotices: SharedFlow<AvatarImpostorDeletionNotice> =
        impostorDeletion.notices

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
    private val stylesLoadState = MutableStateFlow<AvatarStylesLoadState>(
        AvatarStylesLoadState.NotLoaded
    )
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
        val canEdit = avatarEditor != null &&
            currentValidation == AvatarValidation.Available &&
            avatar?.authorId?.isNotBlank() == true &&
            avatar.authorId == user?.userId &&
            session?.let { authenticated ->
                avatar.authorId == authenticated.account.userId &&
                    avatar.authorId == authenticated.token.userId
            } == true
        val publication = if (canEdit) {
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
    }.combine(stylesLoadState) { state, styles ->
        state.copy(styles = styles)
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

    private val impostorOperation = MutableStateFlow(AvatarImpostorOperation())
    private val latestImpostorRequestToken = MutableStateFlow(0L)
    internal val impostorState: StateFlow<AvatarImpostorState> = combine(
        avatarProfileState,
        validation,
        favoriteSession,
        impostorOperation,
        isSelecting,
    ) { avatar, currentValidation, session, operation, selecting ->
        val visibleOperation = operation.takeIf { current ->
            val target = current.target ?: return@takeIf false
            val sameUserAndAvatar = avatar?.avatarId == target.avatarId &&
                session?.token?.userId == target.sessionToken.userId
            val operationInProgress = current.isSubmitting ||
                current.isLoadingQueueEstimate ||
                current.status?.state.isActiveImpostorState()
            sameUserAndAvatar && (operationInProgress || session.token == target.sessionToken)
        }
        val taskState = visibleOperation?.status?.state
        val hasImpostor = avatar?.hasImpostor == true || taskState.isSuccessfulImpostorState()
        val ownsAvatar = currentValidation == AvatarValidation.Available &&
            avatar?.avatarId?.isNotBlank() == true &&
            avatar.authorId.isNotBlank() &&
            avatar.authorId == session?.token?.userId
        AvatarImpostorState(
            canBuild = avatarImpostorBuilder != null &&
                ownsAvatar &&
                !selecting &&
                visibleOperation?.isSubmitting != true &&
                visibleOperation?.isLoadingQueueEstimate != true &&
                !taskState.isActiveImpostorState(),
            hasImpostor = hasImpostor,
            isSubmitting = visibleOperation?.isSubmitting == true,
            taskState = taskState,
            isLoadingQueueEstimate = visibleOperation?.isLoadingQueueEstimate == true,
            estimatedQueueSeconds = visibleOperation?.estimatedQueueSeconds,
            queueEstimateFailed = visibleOperation?.queueEstimateFailed == true,
            failure = visibleOperation?.failure,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = AvatarImpostorState(),
    )

    private val latestRequestToken = MutableStateFlow(0L)
    private var metadataSaveGeneration = 0L
    private var stylesLoadGeneration = 0L
    private var metadataSaveJob: Job? = null
    private var stylesLoadJob: Job? = null

    init {
        viewModelScope.launch {
            favoriteSession.map { it?.token?.userId }
                .distinctUntilChanged()
                .drop(1)
                .collect { invalidateMetadataOperations() }
        }
    }

    fun refreshAvatarData(avatarProfileVo: AvatarProfileVo) {
        invalidateMetadataOperations()
        val requestToken = latestRequestToken.updateAndGet { it + 1 }
        if (!deletionOperation.value.isDeleting) {
            deletionOperation.value = AvatarDeletionOperation()
        }
        if (!impostorOperation.value.isSubmitting) {
            impostorOperation.value = AvatarImpostorOperation()
        }
        validation.value = AvatarValidation.Checking
        impostorDeletion.clearTarget()
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
                        impostorDeletion.setTarget(
                            avatarId = avatarData.id,
                            authorId = avatarData.authorId,
                            hasImpostor = avatarData.hasImpostor,
                        )
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
                val response = editor.loadStyles(sessionToken) ?: run {
                    if (acceptsStylesRequest(sessionToken, avatarId, generation)) {
                        stylesLoadState.value = AvatarStylesLoadState.Failed(null)
                    }
                    return@launch
                }
                if (!acceptsStylesResponse(response.sessionToken, avatarId, generation)) {
                    return@launch
                }
                response.result.fold(
                    onSuccess = { styles ->
                        if (!acceptsStylesResponse(response.sessionToken, avatarId, generation)) {
                            return@fold
                        }
                        val options = normalizedAvatarStyles(styles)
                        stylesLoadState.value = if (options.isEmpty()) {
                            AvatarStylesLoadState.Empty
                        } else {
                            AvatarStylesLoadState.Ready(options)
                        }
                    },
                    onFailure = { error ->
                        if (error is CancellationException) throw error
                        if (acceptsStylesResponse(response.sessionToken, avatarId, generation)) {
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
                if (!isEditSubmissionInFlight.compareAndSet(expect = false, update = true)) return
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
                        if (!acceptsMetadataResponse(response.sessionToken, avatarId, generation)) {
                            return@launch
                        }
                        response.result.fold(
                            onSuccess = { updated ->
                                if (updated.id == avatarId &&
                                    acceptsMetadataResponse(
                                        response.sessionToken,
                                        avatarId,
                                        generation,
                                    )
                                ) {
                                    _avatarProfileState.value = AvatarProfileVo(updated)
                                    validation.value = AvatarValidation.Available
                                    _notices.emit(AvatarProfileNotice.MetadataSaved)
                                }
                            },
                            onFailure = { error ->
                                if (error is CancellationException) throw error
                                if (acceptsMetadataResponse(
                                        response.sessionToken,
                                        avatarId,
                                        generation,
                                    )
                                ) {
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
                        isEditSubmissionInFlight.value = false
                    }
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

    internal fun enqueueImpostor() {
        val builder = avatarImpostorBuilder ?: return
        if (!impostorState.value.canBuild) return
        val avatar = avatarProfileState.value ?: return
        val sessionToken = favoriteSession.value?.token ?: return
        if (avatar.authorId != sessionToken.userId) return

        val current = impostorOperation.value
        if (current.isSubmitting ||
            current.isLoadingQueueEstimate ||
            current.status?.state.isActiveImpostorState()
        ) {
            return
        }

        val target = AvatarImpostorTarget(
            sessionToken = sessionToken,
            avatarId = avatar.avatarId,
            requestToken = latestImpostorRequestToken.updateAndGet { it + 1 },
        )
        if (!impostorOperation.compareAndSet(
                expect = current,
                update = AvatarImpostorOperation(target = target, isSubmitting = true),
            )
        ) {
            return
        }

        viewModelScope.launch(requestDispatcher) {
            val response = try {
                builder.enqueue(target.sessionToken, target.avatarId)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                AuthenticatedAvatarImpostorResult(Result.failure(error), target.sessionToken)
            }
            if (response == null || !acceptImpostorSession(response.sessionToken, target)) {
                finishImpostorOperation(target)
                return@launch
            }
            response.result.fold(
                onSuccess = { status ->
                    handleImpostorEnqueueSuccess(
                        target = target,
                        responseToken = response.sessionToken,
                        status = status,
                        builder = builder,
                    )
                },
                onFailure = { error ->
                    updateImpostorOperation(target) { operation ->
                        operation.copy(
                            target = target.copy(sessionToken = response.sessionToken),
                            isSubmitting = false,
                            failure = error.toAvatarImpostorFailure(),
                        )
                    }
                },
            )
        }
    }

    private suspend fun handleImpostorEnqueueSuccess(
        target: AvatarImpostorTarget,
        responseToken: AccountSessionToken,
        status: AvatarImpostorServiceStatus,
        builder: AvatarImpostorBuilder,
    ) {
        if (!status.isValidFor(target.avatarId, responseToken.userId)) {
            updateImpostorOperation(target) { operation ->
                operation.copy(
                    target = target.copy(sessionToken = responseToken),
                    isSubmitting = false,
                    failure = AvatarImpostorFailure.InvalidResponse,
                )
            }
            return
        }

        val refreshedTarget = target.copy(sessionToken = responseToken)
        val accepted = updateImpostorOperation(target) { operation ->
            operation.copy(
                target = refreshedTarget,
                isSubmitting = false,
                status = status,
                isLoadingQueueEstimate = true,
                failure = null,
            )
        }
        if (!accepted) return

        val queueResponse = try {
            builder.queueStats(responseToken)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            AuthenticatedAvatarImpostorResult(Result.failure(error), responseToken)
        }
        if (queueResponse == null ||
            !acceptImpostorSession(queueResponse.sessionToken, refreshedTarget)
        ) {
            finishQueueEstimate(refreshedTarget, status.id)
            return
        }
        val queueTarget = refreshedTarget.copy(sessionToken = queueResponse.sessionToken)
        queueResponse.result.fold(
            onSuccess = { stats ->
                updateQueueEstimate(refreshedTarget, status.id) { operation ->
                    if (stats.estimatedServiceDurationSeconds < 0) {
                        operation.copy(
                            target = queueTarget,
                            isLoadingQueueEstimate = false,
                            queueEstimateFailed = true,
                        )
                    } else {
                        operation.copy(
                            target = queueTarget,
                            isLoadingQueueEstimate = false,
                            estimatedQueueSeconds = stats.estimatedServiceDurationSeconds,
                            queueEstimateFailed = false,
                        )
                    }
                }
            },
            onFailure = {
                updateQueueEstimate(refreshedTarget, status.id) { operation ->
                    operation.copy(
                        target = queueTarget,
                        isLoadingQueueEstimate = false,
                        queueEstimateFailed = true,
                    )
                }
            },
        )
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

    internal fun applyGalleryUpdate(update: AvatarGalleryUpdate): Boolean {
        val current = _avatarProfileState.value ?: return false
        val session = favoriteSession.value ?: return false
        if (current.avatarId != update.avatarId ||
            current.authorId != session.account.userId ||
            !SharedFlowCentre.isCurrentSession(update.sessionToken) ||
            update.sessionToken != session.token
        ) return false

        _notices.tryEmit(AvatarProfileNotice.GalleryUploaded)
        return true
    }

    private fun editableSessionToken(avatar: AvatarProfileVo): AccountSessionToken? {
        val sessionToken = favoriteSession.value?.token ?: return null
        if (!editState.value.canEdit || avatar.authorId != sessionToken.userId) return null
        return sessionToken
    }

    private fun acceptsStylesResponse(
        sessionToken: AccountSessionToken,
        avatarId: String,
        operationGeneration: Long,
    ): Boolean = acceptsStylesRequest(sessionToken, avatarId, operationGeneration)

    private fun acceptsStylesRequest(
        sessionToken: AccountSessionToken,
        avatarId: String,
        operationGeneration: Long,
    ): Boolean = acceptsMetadataTarget(sessionToken, avatarId) &&
        stylesLoadGeneration == operationGeneration

    private fun acceptsMetadataResponse(
        sessionToken: AccountSessionToken,
        avatarId: String,
        operationGeneration: Long,
    ): Boolean = acceptsMetadataTarget(sessionToken, avatarId) &&
        metadataSaveGeneration == operationGeneration

    private fun acceptsMetadataTarget(
        sessionToken: AccountSessionToken,
        avatarId: String,
    ): Boolean = favoriteSession.value?.token?.userId == sessionToken.userId &&
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
        isEditSubmissionInFlight.value = false
        stylesLoadState.value = AvatarStylesLoadState.NotLoaded
    }
    internal fun deleteImpostor(): Boolean = impostorDeletion.delete()

    internal fun retryImpostorVerification(): Boolean = impostorDeletion.retryVerification()

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

    private fun acceptImpostorSession(
        responseToken: AccountSessionToken,
        target: AvatarImpostorTarget,
    ): Boolean = responseToken.userId == target.sessionToken.userId &&
        favoriteSession.value?.token == responseToken &&
        avatarImpostorBuilder?.isCurrentSession(responseToken) == true

    private fun updateImpostorOperation(
        target: AvatarImpostorTarget,
        transform: (AvatarImpostorOperation) -> AvatarImpostorOperation,
    ): Boolean {
        val operation = impostorOperation.value
        if (operation.target?.requestToken != target.requestToken) return false
        impostorOperation.value = transform(operation)
        return true
    }

    private fun updateQueueEstimate(
        target: AvatarImpostorTarget,
        serviceId: String,
        transform: (AvatarImpostorOperation) -> AvatarImpostorOperation,
    ): Boolean {
        val operation = impostorOperation.value
        if (operation.target?.requestToken != target.requestToken ||
            operation.status?.id != serviceId
        ) {
            return false
        }
        impostorOperation.value = transform(operation)
        return true
    }

    private fun finishImpostorOperation(target: AvatarImpostorTarget) {
        updateImpostorOperation(target) { AvatarImpostorOperation() }
    }

    private fun finishQueueEstimate(target: AvatarImpostorTarget, serviceId: String) {
        updateQueueEstimate(target, serviceId) { operation ->
            operation.copy(isLoadingQueueEstimate = false, queueEstimateFailed = true)
        }
    }
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

private data class AvatarDeletionOperation(
    val target: AvatarDeletionTarget? = null,
    val isDeleting: Boolean = false,
    val failure: AvatarDeletionFailure? = null,
)

private data class AvatarImpostorTarget(
    val sessionToken: AccountSessionToken,
    val avatarId: String,
    val requestToken: Long,
)

private data class AvatarImpostorOperation(
    val target: AvatarImpostorTarget? = null,
    val isSubmitting: Boolean = false,
    val status: AvatarImpostorServiceStatus? = null,
    val isLoadingQueueEstimate: Boolean = false,
    val estimatedQueueSeconds: Int? = null,
    val queueEstimateFailed: Boolean = false,
    val failure: AvatarImpostorFailure? = null,
)

private data class AvatarEditTarget(
    val avatarId: String,
    val userId: String,
)

private fun AvatarImpostorServiceStatus.isValidFor(avatarId: String, userId: String): Boolean =
    id.isNotBlank() &&
        state.isNotBlank() &&
        subjectId == avatarId &&
        requesterUserId == userId &&
        subjectType.isNotBlank() &&
        type.isNotBlank()

private fun String?.isSuccessfulImpostorState(): Boolean = when (this?.lowercase()) {
    "complete", "completed", "success", "succeeded" -> true
    else -> false
}

private fun String?.isActiveImpostorState(): Boolean {
    if (this == null) return false
    return when (lowercase()) {
        "complete", "completed", "success", "succeeded",
        "failed", "failure", "error", "cancelled", "canceled" -> false
        else -> true
    }
}
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
