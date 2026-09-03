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
import io.github.vrcmteam.vrcm.network.api.auth.data.CurrentUserData
import io.github.vrcmteam.vrcm.network.supports.VRCApiException
import io.github.vrcmteam.vrcm.presentation.compoments.ToastText
import io.github.vrcmteam.vrcm.presentation.favorites.FavoriteEntrySource
import io.github.vrcmteam.vrcm.presentation.favorites.FavoriteEntryState
import io.github.vrcmteam.vrcm.presentation.favorites.FavoriteEntryStateModel
import io.github.vrcmteam.vrcm.presentation.screens.avatar.data.AvatarProfileVo
import io.github.vrcmteam.vrcm.service.AuthService
import io.github.vrcmteam.vrcm.service.FallbackAvatarUpdateResult
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
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
import kotlinx.coroutines.flow.flowOf
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

internal data class AvatarFallbackUserContext(
    val userId: String,
    val fallbackAvatarId: String,
    val sessionToken: AccountSessionToken,
)

internal data class AvatarFallbackResponse(
    val result: Result<CurrentUserData>,
    val sessionToken: AccountSessionToken,
)

internal interface AvatarFallbackSetter {
    val currentUser: Flow<AvatarFallbackUserContext?>

    suspend fun set(
        avatarId: String,
        sessionToken: AccountSessionToken,
    ): AvatarFallbackResponse?

    suspend fun apply(
        avatarId: String,
        sessionToken: AccountSessionToken,
        response: CurrentUserData,
        commitIfCurrent: (update: () -> Unit) -> Boolean,
    ): FallbackAvatarUpdateResult

    fun isCurrentSession(sessionToken: AccountSessionToken): Boolean
}

internal class NetworkAvatarFallbackSetter(
    private val avatarsApi: AvatarsApi,
    private val authService: AuthService,
    private val logger: Logger,
) : AvatarFallbackSetter {
    override val currentUser: Flow<AvatarFallbackUserContext?> = combine(
        authService.currentUserState,
        SharedFlowCentre.currentSession,
    ) { user, session ->
        if (user == null || session == null || user.id != session.account.userId) {
            null
        } else {
            AvatarFallbackUserContext(
                userId = user.id,
                fallbackAvatarId = user.fallbackAvatar,
                sessionToken = session.token,
            )
        }
    }

    override suspend fun set(
        avatarId: String,
        sessionToken: AccountSessionToken,
    ): AvatarFallbackResponse? {
        val response = authService.runSessionBoundCatching(sessionToken) {
            avatarsApi.selectFallbackAvatar(avatarId)
        } ?: return null
        response.result.onFailure { error ->
            logger.error(avatarFallbackFailureLog(avatarId, error))
        }
        return AvatarFallbackResponse(response.result, response.sessionToken)
    }

    override suspend fun apply(
        avatarId: String,
        sessionToken: AccountSessionToken,
        response: CurrentUserData,
        commitIfCurrent: (update: () -> Unit) -> Boolean,
    ): FallbackAvatarUpdateResult = authService.applyFallbackAvatarUpdate(
        sessionToken = sessionToken,
        avatarId = avatarId,
        response = response,
        commitIfCurrent = commitIfCurrent,
    )

    override fun isCurrentSession(sessionToken: AccountSessionToken): Boolean =
        SharedFlowCentre.isCurrentSession(sessionToken)
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

private fun avatarFallbackFailureLog(avatarId: String, error: Throwable): String {
    val request = "method=PUT path=/avatars/$avatarId/selectFallback"
    return if (error is VRCApiException) {
        "Fallback avatar selection failed: $request status=${error.code} " +
            "description=${error.description} body=${error.bodyText}"
    } else {
        "Fallback avatar selection failed: $request error=${error.message.orEmpty()}"
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

internal sealed interface AvatarFallbackAvailability {
    data object Hidden : AvatarFallbackAvailability
    data object Available : AvatarFallbackAvailability
    data object Current : AvatarFallbackAvailability
    data object Ineligible : AvatarFallbackAvailability
}

internal data class AvatarFallbackActionState(
    val availability: AvatarFallbackAvailability = AvatarFallbackAvailability.Hidden,
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
    data object FallbackSelected : AvatarProfileNotice
    data object FallbackIneligible : AvatarProfileNotice
    data object FallbackNotFound : AvatarProfileNotice
    data object FallbackUnauthorized : AvatarProfileNotice
    data object FallbackSelectionFailed : AvatarProfileNotice
    data object InvalidName : AvatarProfileNotice
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
    private val avatarFallbackSetter: AvatarFallbackSetter? = null,
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
    private val fallbackCurrentUser: StateFlow<AvatarFallbackUserContext?> =
        (avatarFallbackSetter?.currentUser ?: flowOf(null)).stateIn(
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

    private val pendingFallbackTarget = MutableStateFlow<AvatarFallbackTarget?>(null)
    private val fallbackIneligibleTarget = MutableStateFlow<AvatarFallbackTargetKey?>(null)
    private val latestFallbackRequestToken = MutableStateFlow(0L)
    // Serializes page replacement with target creation and final current-user publication.
    private val fallbackTargetLock = SynchronizedObject()
    internal val fallbackActionState: StateFlow<AvatarFallbackActionState> = combine(
        avatarProfileState,
        validation,
        fallbackCurrentUser,
        pendingFallbackTarget,
        fallbackIneligibleTarget,
    ) { avatar, currentValidation, user, pending, ineligible ->
        AvatarFallbackActionState(
            availability = avatarFallbackAvailability(
                avatar = avatar,
                validation = currentValidation,
                user = user,
                ineligible = ineligible,
            ),
            isSelecting = avatar != null && user != null &&
                pending?.avatarId == avatar.avatarId && pending.userId == user.userId,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = AvatarFallbackActionState(),
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

    private val latestRequestToken = MutableStateFlow(0L)

    fun refreshAvatarData(avatarProfileVo: AvatarProfileVo) {
        val requestToken = latestRequestToken.updateAndGet { it + 1 }
        synchronized(fallbackTargetLock) {
            pendingFallbackTarget.value?.invalidate()
            pendingFallbackTarget.value = null
            fallbackIneligibleTarget.value = null
            validation.value = AvatarValidation.Checking
            _avatarProfileState.value = avatarProfileVo
        }
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

    internal fun selectFallbackAvatar() {
        val setter = avatarFallbackSetter ?: return
        val target = synchronized(fallbackTargetLock) {
            val avatar = avatarProfileState.value ?: return@synchronized null
            val user = fallbackCurrentUser.value ?: return@synchronized null
            val availability = avatarFallbackAvailability(
                avatar = avatar,
                validation = validation.value,
                user = user,
                ineligible = fallbackIneligibleTarget.value,
            )
            if (availability != AvatarFallbackAvailability.Available) {
                return@synchronized null
            }

            pendingFallbackTarget.value?.let { pending ->
                if (pending.avatarId == avatar.avatarId && pending.userId == user.userId) {
                    return@synchronized null
                }
                pending.invalidate()
                pendingFallbackTarget.value = null
            }
            AvatarFallbackTarget(
                avatarId = avatar.avatarId,
                userId = user.userId,
                requestSessionToken = user.sessionToken,
                requestToken = latestFallbackRequestToken.updateAndGet { it + 1 },
            ).also { pendingFallbackTarget.value = it }
        } ?: return

        viewModelScope.launch(requestDispatcher) {
            try {
                val response = setter.set(target.avatarId, target.requestSessionToken)
                    ?: return@launch
                if (!isCurrentFallbackTarget(setter, target, response.sessionToken)) return@launch

                response.result
                    .onSuccess { currentUser ->
                        when (setter.apply(
                            avatarId = target.avatarId,
                            sessionToken = response.sessionToken,
                            response = currentUser,
                            commitIfCurrent = { update ->
                                commitFallbackIfCurrent(
                                    setter = setter,
                                    target = target,
                                    responseSessionToken = response.sessionToken,
                                    update = update,
                                )
                            },
                        )) {
                            FallbackAvatarUpdateResult.Applied -> emitFallbackNoticeIfCurrent(
                                setter = setter,
                                target = target,
                                responseSessionToken = response.sessionToken,
                                notice = AvatarProfileNotice.FallbackSelected,
                            )
                            FallbackAvatarUpdateResult.InvalidResponse ->
                                emitFallbackNoticeIfCurrent(
                                    setter = setter,
                                    target = target,
                                    responseSessionToken = response.sessionToken,
                                    notice = AvatarProfileNotice.FallbackSelectionFailed,
                                )
                            FallbackAvatarUpdateResult.Stale -> Unit
                        }
                    }
                    .onFailure { error ->
                        handleFallbackFailure(
                            setter = setter,
                            target = target,
                            responseSessionToken = response.sessionToken,
                            error = error,
                        )
                    }
            } finally {
                synchronized(fallbackTargetLock) {
                    target.invalidate()
                    pendingFallbackTarget.compareAndSet(target, null)
                }
            }
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

    private fun isCurrentFallbackTarget(
        setter: AvatarFallbackSetter,
        target: AvatarFallbackTarget,
        responseSessionToken: AccountSessionToken,
    ): Boolean = synchronized(fallbackTargetLock) {
        isCurrentFallbackTargetLocked(setter, target, responseSessionToken)
    }

    private fun isCurrentFallbackTargetLocked(
        setter: AvatarFallbackSetter,
        target: AvatarFallbackTarget,
        responseSessionToken: AccountSessionToken,
    ): Boolean = pendingFallbackTarget.value == target &&
        avatarProfileState.value?.avatarId == target.avatarId &&
        responseSessionToken.userId == target.userId &&
        setter.isCurrentSession(responseSessionToken)

    private fun commitFallbackIfCurrent(
        setter: AvatarFallbackSetter,
        target: AvatarFallbackTarget,
        responseSessionToken: AccountSessionToken,
        update: () -> Unit,
    ): Boolean = synchronized(fallbackTargetLock) {
        if (!isCurrentFallbackTargetLocked(setter, target, responseSessionToken) ||
            !target.tryClaim()
        ) {
            return@synchronized false
        }
        update()
        true
    }

    private fun emitFallbackNoticeIfCurrent(
        setter: AvatarFallbackSetter,
        target: AvatarFallbackTarget,
        responseSessionToken: AccountSessionToken,
        notice: AvatarProfileNotice,
    ) {
        synchronized(fallbackTargetLock) {
            if (isCurrentFallbackTargetLocked(setter, target, responseSessionToken)) {
                _notices.tryEmit(notice)
            }
        }
    }

    private fun handleFallbackFailure(
        setter: AvatarFallbackSetter,
        target: AvatarFallbackTarget,
        responseSessionToken: AccountSessionToken,
        error: Throwable,
    ) {
        synchronized(fallbackTargetLock) {
            if (!isCurrentFallbackTargetLocked(setter, target, responseSessionToken)) return

            val notice = when {
                error is VRCApiException && error.code == 403 -> {
                    fallbackIneligibleTarget.value = target.key
                    AvatarProfileNotice.FallbackIneligible
                }
                error is VRCApiException && error.code == 404 -> {
                    validation.value = AvatarValidation.Banned
                    AvatarProfileNotice.FallbackNotFound
                }
                error is VRCApiException && error.code == 401 -> {
                    AvatarProfileNotice.FallbackUnauthorized
                }
                else -> AvatarProfileNotice.FallbackSelectionFailed
            }
            _notices.tryEmit(notice)
        }
    }
}

private data class AvatarEditTarget(
    val avatarId: String,
    val userId: String,
)

private data class AvatarFallbackTargetKey(
    val avatarId: String,
    val userId: String,
)

private data class AvatarFallbackTarget(
    val avatarId: String,
    val userId: String,
    val requestSessionToken: AccountSessionToken,
    val requestToken: Long,
) {
    private val canCommit = atomic(true)
    val key = AvatarFallbackTargetKey(avatarId, userId)

    fun tryClaim(): Boolean = canCommit.compareAndSet(expect = true, update = false)

    fun invalidate() {
        canCommit.value = false
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

private fun avatarFallbackAvailability(
    avatar: AvatarProfileVo?,
    validation: AvatarValidation,
    user: AvatarFallbackUserContext?,
    ineligible: AvatarFallbackTargetKey?,
): AvatarFallbackAvailability {
    if (avatar == null || user == null || validation != AvatarValidation.Available) {
        return AvatarFallbackAvailability.Hidden
    }
    if (avatar.avatarId == user.fallbackAvatarId) return AvatarFallbackAvailability.Current
    if (ineligible == AvatarFallbackTargetKey(avatar.avatarId, user.userId)) {
        return AvatarFallbackAvailability.Ineligible
    }
    return AvatarFallbackAvailability.Available
}
