package io.github.vrcmteam.vrcm.presentation.screens.world

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.vrcmteam.vrcm.core.shared.SharedFlowCentre
import io.github.vrcmteam.vrcm.core.shared.AccountSessionToken
import io.github.vrcmteam.vrcm.core.shared.AuthenticatedAccount
import io.github.vrcmteam.vrcm.network.api.attributes.BlueprintType
import io.github.vrcmteam.vrcm.network.api.attributes.FavoriteType
import io.github.vrcmteam.vrcm.network.api.groups.GroupsApi
import io.github.vrcmteam.vrcm.network.api.instances.InstancesApi
import io.github.vrcmteam.vrcm.network.api.instances.data.InstanceCreationOptions
import io.github.vrcmteam.vrcm.network.api.invite.InviteApi
import io.github.vrcmteam.vrcm.network.api.users.UsersApi
import io.github.vrcmteam.vrcm.network.api.worlds.WorldsApi
import io.github.vrcmteam.vrcm.network.api.worlds.data.WorldData
import io.github.vrcmteam.vrcm.network.api.worlds.data.supportedPlatforms
import io.github.vrcmteam.vrcm.presentation.compoments.ToastText
import io.github.vrcmteam.vrcm.presentation.favorites.FavoriteEntrySource
import io.github.vrcmteam.vrcm.presentation.favorites.FavoriteEntryState
import io.github.vrcmteam.vrcm.presentation.favorites.FavoriteEntryStateModel
import io.github.vrcmteam.vrcm.presentation.screens.world.data.InstanceCreationDraft
import io.github.vrcmteam.vrcm.presentation.screens.world.data.InstanceCreationGroup
import io.github.vrcmteam.vrcm.presentation.screens.world.data.InstanceCreationGroupsState
import io.github.vrcmteam.vrcm.presentation.screens.world.data.InstanceCreationRole
import io.github.vrcmteam.vrcm.presentation.screens.world.data.InstanceCreationSubmissionState
import io.github.vrcmteam.vrcm.presentation.screens.world.data.InstanceVo
import io.github.vrcmteam.vrcm.presentation.screens.world.data.InstanceVo.Owner
import io.github.vrcmteam.vrcm.presentation.screens.world.data.WorldProfileVo
import io.github.vrcmteam.vrcm.presentation.screens.world.data.validationError
import io.github.vrcmteam.vrcm.presentation.settings.locale.LocaleStrings
import io.github.vrcmteam.vrcm.service.AuthService
import io.github.vrcmteam.vrcm.service.HomeWorldManager
import io.github.vrcmteam.vrcm.service.HomeWorldSessionChangedException
import io.github.vrcmteam.vrcm.service.HomeWorldUserContext
import io.github.vrcmteam.vrcm.service.InstanceCreationResult
import io.github.vrcmteam.vrcm.service.InstanceCreationService
import io.github.vrcmteam.vrcm.service.WorldPlatformService
import io.github.vrcmteam.vrcm.storage.WorldProfileCacheStore
import io.github.vrcmteam.vrcm.storage.data.WorldProfileCache
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

internal enum class HomeWorldActionAvailability {
    Unavailable,
    CanSet,
    Current,
}

internal enum class HomeWorldAction {
    Set,
    Reset,
}

internal data class HomeWorldActionState(
    val availability: HomeWorldActionAvailability = HomeWorldActionAvailability.Unavailable,
    val isUpdating: Boolean = false,
) {
    val action: HomeWorldAction?
        get() = when (availability) {
            HomeWorldActionAvailability.Unavailable -> null
            HomeWorldActionAvailability.CanSet -> HomeWorldAction.Set
            HomeWorldActionAvailability.Current -> HomeWorldAction.Reset
        }
}

internal sealed interface HomeWorldNotice {
    data object Set : HomeWorldNotice
    data object Reset : HomeWorldNotice
    data object UpdateFailed : HomeWorldNotice
}

internal data class WorldImageEditState(
    val canEdit: Boolean = false,
    val sessionToken: AccountSessionToken? = null,
)

internal sealed interface WorldProfileNotice {
    data object ImageSaved : WorldProfileNotice
}

internal fun isCurrentWorldImageUpdate(
    currentWorld: WorldProfileVo?,
    currentSession: AuthenticatedAccount?,
    update: WorldImageUpdate,
): Boolean = currentWorld?.worldId == update.world.id &&
    currentWorld.authorID == update.world.authorId &&
    update.world.authorId == currentSession?.account?.userId &&
    update.sessionToken == currentSession?.token

/**
 * 世界档案页面的ViewModel，负责处理世界数据的加载和刷新
 */
@OptIn(ExperimentalTime::class)
class WorldProfileScreenModel internal constructor(
    private val worldsApi: WorldsApi,
    private val instancesApi: InstancesApi,
    private val usersApi: UsersApi,
    private val groupsApi: GroupsApi,
    private val authService: AuthService,
    private val instanceCreationService: InstanceCreationService,
    favoriteEntrySource: FavoriteEntrySource,
    private val inviteApi: InviteApi,
    private val worldPlatformService: WorldPlatformService,
    private val worldProfileCacheStore: WorldProfileCacheStore,
    private val homeWorldManager: HomeWorldManager,
    worldEditor: WorldEditor,
) : ViewModel() {
    // 世界数据状态
    private val _worldProfileState = MutableStateFlow<WorldProfileVo?>(null)
    val worldProfileState: StateFlow<WorldProfileVo?> = _worldProfileState.asStateFlow()
    private val worldInstanceStateStore = WorldInstanceStateStore(_worldProfileState)

    // 加载状态
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    private val worldCacheMutex = Mutex()

    private val _instanceCreationGroups =
        MutableStateFlow<InstanceCreationGroupsState>(InstanceCreationGroupsState.Idle)
    internal val instanceCreationGroups: StateFlow<InstanceCreationGroupsState> =
        _instanceCreationGroups.asStateFlow()

    private val _instanceCreationState =
        MutableStateFlow<InstanceCreationSubmissionState>(InstanceCreationSubmissionState.Idle)
    internal val instanceCreationState: StateFlow<InstanceCreationSubmissionState> =
        _instanceCreationState.asStateFlow()

    private var instanceCreationGroupsJob: Job? = null
    private var instanceCreationJob: Job? = null
    private var instanceCreationGroupsGeneration = 0L
    private var instanceCreationGeneration = 0L

    private val metadataReady = MutableStateFlow(false)
    private val metadataEditor = WorldMetadataEditStateModel(
        editor = worldEditor,
        scope = viewModelScope,
        world = worldProfileState,
        metadataReady = metadataReady,
        session = SharedFlowCentre.currentSession,
        onAcceptedUpdate = { current, updated ->
            _worldProfileState.value = WorldProfileVo(
                world = updated,
                instancesList = current.instances,
                platformFileSizes = current.platformFileSizes,
            )
            metadataReady.value = true
            viewModelScope.launch {
                worldCacheMutex.withLock {
                    if (_worldProfileState.value?.worldId != updated.id) return@withLock
                    worldProfileCacheStore.save(
                        WorldProfileCache(
                            world = updated,
                            cachedAtEpochMilliseconds = Clock.System.now().toEpochMilliseconds(),
                            supportedPlatforms = updated.supportedPlatforms,
                            platformFileSizes = current.platformFileSizes,
                        )
                    )
                }
            }
        },
    )
    internal val metadataEditState: StateFlow<WorldMetadataEditState> = metadataEditor.state
    internal val metadataEditNotices: SharedFlow<WorldMetadataEditNotice> = metadataEditor.notices
    private var refreshMetadataAfterCurrentLoad = false

    internal val imageEditState: StateFlow<WorldImageEditState> = combine(
        worldProfileState,
        SharedFlowCentre.currentSession,
    ) { world, session ->
        val canEdit = world?.authorID?.isNotBlank() == true &&
            world.authorID == session?.account?.userId
        WorldImageEditState(
            canEdit = canEdit,
            sessionToken = session?.token?.takeIf { canEdit },
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = WorldImageEditState(),
    )

    private val _notices = MutableSharedFlow<WorldProfileNotice>(extraBufferCapacity = 1)
    internal val notices: SharedFlow<WorldProfileNotice> = _notices.asSharedFlow()
    private val platformFileSizesJob = atomic<Job?>(null)

    private val worldPersistence = WorldPersistenceStateModel(
        request = NetworkWorldPersistenceRequest(authService, usersApi),
        scope = viewModelScope,
    )
    internal val worldPersistenceState: StateFlow<WorldPersistenceUiState> = worldPersistence.state

    private val instanceCloseCoordinator = InstanceCloseCoordinator(
        currentSessionToken = { SharedFlowCentre.currentSession.value?.token },
        isCurrentSession = SharedFlowCentre::isCurrentSession,
        fetchGroupPermissions = { sessionToken, groupId ->
            authService.runSessionBoundCatching(sessionToken) {
                groupsApi.fetchGroup(groupId).myMember?.permissions.orEmpty()
            }?.let { response ->
                InstanceCloseSessionResult(response.result, response.sessionToken)
            }
        },
        fetchInstance = { sessionToken, target ->
            authService.runSessionBoundCatching(sessionToken) {
                instancesApi.closeStatus(target.worldId, target.instanceId)
            }?.let { response ->
                InstanceCloseSessionResult(response.result, response.sessionToken)
            }
        },
        closeInstance = { sessionToken, target ->
            authService.runSessionBoundCatching(sessionToken) {
                instancesApi.closeInstance(target.worldId, target.instanceId)
            }?.let { response ->
                InstanceCloseSessionResult(response.result, response.sessionToken)
            }
        },
    )
    internal val instanceCloseState: StateFlow<InstanceCloseState> = instanceCloseCoordinator.state

    private val _closedInstanceLocations = MutableSharedFlow<String>(extraBufferCapacity = 1)
    internal val closedInstanceLocations: SharedFlow<String> = _closedInstanceLocations.asSharedFlow()

    init {
        viewModelScope.launch {
            var activeUserId = SharedFlowCentre.currentSession.value?.account?.userId
            SharedFlowCentre.currentSession.collect { session ->
                instanceCloseCoordinator.onSessionChanged(session?.token)
                val userId = session?.account?.userId
                if (userId != activeUserId) {
                    activeUserId = userId
                    cancelInstanceCreationGroups()
                    cancelInstanceCreation()
                    _instanceCreationGroups.value = InstanceCreationGroupsState.Idle
                    _instanceCreationState.value = InstanceCreationSubmissionState.Idle
                }
            }
        }
        viewModelScope.launch {
            SharedFlowCentre.currentSession
                .map { it?.token?.userId }
                .distinctUntilChanged()
                .drop(1)
                .collect { userId ->
                    metadataReady.value = false
                    if (_worldProfileState.value?.authorID == userId) {
                        if (_isLoading.value) {
                            refreshMetadataAfterCurrentLoad = true
                        } else {
                            refreshWorldData()
                        }
                    }
                }
        }
    }

    private val currentHomeWorldUser = homeWorldManager.currentUser.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = null,
    )
    private val isUpdatingHomeWorld = MutableStateFlow(false)
    internal val homeWorldActionState: StateFlow<HomeWorldActionState> = combine(
        worldProfileState,
        currentHomeWorldUser,
        isUpdatingHomeWorld,
    ) { world, user, isUpdating ->
        HomeWorldActionState(
            availability = homeWorldActionAvailability(world?.worldId, user),
            isUpdating = isUpdating,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = HomeWorldActionState(),
    )
    private val _homeWorldNotices = MutableSharedFlow<HomeWorldNotice>(extraBufferCapacity = 1)
    internal val homeWorldNotices: SharedFlow<HomeWorldNotice> = _homeWorldNotices.asSharedFlow()

    private val favoriteEntry = FavoriteEntryStateModel(
        favoriteType = FavoriteType.World,
        source = favoriteEntrySource,
        scope = viewModelScope,
    )
    internal val favoriteEntryState: StateFlow<FavoriteEntryState> = favoriteEntry.state

    private val publication = WorldPublicationStateModel(
        source = NetworkWorldPublicationSource(worldsApi, authService),
        scope = viewModelScope,
        onWorldRefreshed = ::applyPublicationWorld,
    )
    internal val publicationState: StateFlow<WorldPublicationUiState> = publication.state
    internal val publicationNotices: SharedFlow<WorldPublicationNotice> = publication.notices

    private val deletion = WorldDeletionStateModel(
        source = NetworkWorldDeletionSource(worldsApi, authService),
        scope = viewModelScope,
        removeCachedWorld = { worldId ->
            worldCacheMutex.withLock { worldProfileCacheStore.delete(worldId) }
        },
    )
    internal val deletionState: StateFlow<WorldDeletionUiState> = deletion.state
    internal val deletionNotices: SharedFlow<WorldDeletionNotice> = deletion.notices

    private var worldLoadJob: Job? = null
    private var worldLoadGeneration = 0L

    internal fun retryFavoriteEntryLoad() {
        favoriteEntry.retry()
    }

    private fun cancelInstanceCreationGroups() {
        instanceCreationGroupsGeneration++
        instanceCreationGroupsJob?.cancel()
        instanceCreationGroupsJob = null
    }

    private fun cancelInstanceCreation() {
        instanceCreationGeneration++
        instanceCreationJob?.cancel()
        instanceCreationJob = null
        if (_instanceCreationState.value == InstanceCreationSubmissionState.Submitting) {
            _instanceCreationState.value = InstanceCreationSubmissionState.Idle
        }
    }

    internal fun saveMetadata(draft: WorldMetadataDraft) {
        metadataEditor.save(draft)
    }

    internal fun checkWorldPersistence() = worldPersistence.check()

    internal fun requestWorldPersistenceDeletion() = worldPersistence.requestDeletion()

    internal fun dismissWorldPersistenceDeletion() = worldPersistence.dismissDeletionConfirmation()

    internal fun confirmWorldPersistenceDeletion() = worldPersistence.confirmDeletion()
    internal fun updateHomeWorld(action: HomeWorldAction) {
        val worldId = worldProfileState.value?.worldId ?: return
        val availability = homeWorldActionState.value.availability
        if (availability == HomeWorldActionAvailability.Unavailable ||
            action == HomeWorldAction.Reset && availability != HomeWorldActionAvailability.Current
        ) {
            return
        }
        if (!isUpdatingHomeWorld.compareAndSet(expect = false, update = true)) return

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val result = when (action) {
                    HomeWorldAction.Set -> homeWorldManager.setHomeWorld(worldId)
                    HomeWorldAction.Reset -> homeWorldManager.resetHomeWorld()
                }
                result
                    .onSuccess {
                        if (_worldProfileState.value?.worldId == worldId) {
                            _homeWorldNotices.emit(
                                if (action == HomeWorldAction.Reset) {
                                    HomeWorldNotice.Reset
                                } else {
                                    HomeWorldNotice.Set
                                }
                            )
                        }
                    }
                    .onFailure { error ->
                        if (error !is HomeWorldSessionChangedException &&
                            _worldProfileState.value?.worldId == worldId
                        ) {
                            _homeWorldNotices.emit(HomeWorldNotice.UpdateFailed)
                        }
                    }
            } finally {
                isUpdatingHomeWorld.value = false
            }
        }
    }

    /**
     * 刷新世界数据
     */
    fun loadWorldData(worldProfileVO: WorldProfileVo) {
        metadataEditor.invalidate()
        metadataReady.value = false
        refreshMetadataAfterCurrentLoad = false
        if (_worldProfileState.value?.worldId != null &&
            _worldProfileState.value?.worldId != worldProfileVO.worldId
        ) {
            cancelInstanceCreation()
        }
        if (_worldProfileState.value?.worldId != worldProfileVO.worldId) {
            platformFileSizesJob.getAndSet(null)?.cancel()
        }
        worldLoadJob?.cancel()
        val loadGeneration = ++worldLoadGeneration
        _worldProfileState.value = worldProfileVO
        val worldId = worldProfileVO.worldId
        publication.setTarget(worldId, worldProfileVO.authorID)
        worldPersistence.bindWorld(worldId)
        // 路由快照可能来自列表、活动或房间事件，作者字段不作为删除权限依据。
        deletion.setTarget("", null)
        favoriteEntry.load(worldId)
        if (worldId.isBlank()) {
            _isLoading.value = false
            return
        }
        _isLoading.value = true
        // 缓存读取和后续详情刷新共享同一任务，删除开始后会取消并使其代次失效。
        worldLoadJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                applyCachedWorld(worldId, worldProfileVO, loadGeneration)
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                SharedFlowCentre.toastText.emit(
                    ToastText.Error(error.message ?: "Failed to load world data")
                )
            } finally {
                if (isCurrentWorldLoad(loadGeneration, worldId)) {
                    _isLoading.value = false
                    worldLoadJob = null
                }
            }
        }
    }

    private suspend fun applyCachedWorld(
        worldId: String,
        worldProfileVO: WorldProfileVo,
        loadGeneration: Long,
    ) {
        val cached = worldProfileCacheStore.load(worldId)
        if (!isCurrentWorldLoad(loadGeneration, worldId)) return
        if (cached != null) {
            publication.observeKnownWorld(cached.world)
            deletion.setTarget(cached.world.id, cached.world.authorId)
            metadataReady.value =
                cached.world.authorId == SharedFlowCentre.currentSession.value?.token?.userId
            _worldProfileState.value = WorldProfileVo(
                world = cached.world,
                instancesList = worldProfileVO.instances,
                supportedPlatforms =
                    cached.supportedPlatforms ?: worldProfileVO.supportedPlatforms,
                platformFileSizes = cached.platformFileSizes ?: worldProfileVO.platformFileSizes,
            )
        }

        val shouldRefreshProfile = cached == null ||
            cached.isExpired(Clock.System.now().toEpochMilliseconds()) ||
            cached.world.instances == null ||
            cached.supportedPlatforms == null ||
            cached.platformFileSizes == null
        if (shouldRefreshProfile) {
            loadWorldInfo(worldId, loadGeneration)
        } else {
            val profile = _worldProfileState.value ?: return
            publication.refreshIfOwned()
            loadInstanceData(
                collectInstanceIds(profile, cached.world.instances.orEmpty()),
                loadGeneration,
            )
        }
    }

    internal suspend fun applyWorldImageUpdate(update: WorldImageUpdate): Boolean {
        val current = _worldProfileState.value
        val session = SharedFlowCentre.currentSession.value
        if (!isCurrentWorldImageUpdate(current, session, update)) return false

        if (!worldProfileCacheStore.saveAndCommitIfCurrent(
            WorldProfileCache(
                world = update.world,
                cachedAtEpochMilliseconds = Clock.System.now().toEpochMilliseconds(),
                supportedPlatforms = update.world.supportedPlatforms,
                platformFileSizes = current?.platformFileSizes,
            ),
            canStart = {
                isCurrentWorldImageUpdate(
                    currentWorld = _worldProfileState.value,
                    currentSession = SharedFlowCentre.currentSession.value,
                    update = update,
                )
            },
            commit = {
                SharedFlowCentre.commitIfCurrentSession(update.sessionToken) { currentSession ->
                    val latest = _worldProfileState.value
                        ?: return@commitIfCurrentSession false
                    if (!isCurrentWorldImageUpdate(latest, currentSession, update)) {
                        return@commitIfCurrentSession false
                    }
                    _worldProfileState.compareAndSet(
                        expect = latest,
                        update = WorldProfileVo(
                            world = update.world,
                            instancesList = latest.instances,
                            platformFileSizes = latest.platformFileSizes,
                        ),
                    )
                }
            },
        )) return false
        _notices.emit(WorldProfileNotice.ImageSaved)
        return true
    }

    internal fun requestInstanceClose(instance: InstanceVo, strings: LocaleStrings) {
        val target = instance.closeTargetOrNull() ?: return
        viewModelScope.launch {
            when (val result = instanceCloseCoordinator.authorize(target)) {
                InstanceCloseAuthorizationResult.NotAllowed ->
                    emitInstanceCloseError(strings.instanceClosePermissionDenied)

                is InstanceCloseAuthorizationResult.Failed ->
                    emitInstanceCloseFailure(result.error, strings)

                is InstanceCloseAuthorizationResult.SessionChanged ->
                    emitInstanceCloseSessionChanged(result.userId, strings)

                InstanceCloseAuthorizationResult.Abandoned,
                InstanceCloseAuthorizationResult.Busy,
                InstanceCloseAuthorizationResult.Ready -> Unit
            }
        }
    }

    internal fun confirmInstanceClose(strings: LocaleStrings) {
        viewModelScope.launch {
            when (val result = instanceCloseCoordinator.submit()) {
                is InstanceCloseSubmissionResult.Closed -> onInstanceClosed(result, strings)
                is InstanceCloseSubmissionResult.Failed -> emitInstanceCloseFailure(result.error, strings)
                is InstanceCloseSubmissionResult.SessionChanged ->
                    emitInstanceCloseSessionChanged(result.userId, strings)

                InstanceCloseSubmissionResult.Abandoned,
                InstanceCloseSubmissionResult.Busy -> Unit
            }
        }
    }

    internal fun abandonInstanceClose(location: String) {
        instanceCloseCoordinator.abandon(location)
    }

    private suspend fun onInstanceClosed(
        result: InstanceCloseSubmissionResult.Closed,
        strings: LocaleStrings,
    ) {
        val request = result.request
        if (!SharedFlowCentre.isCurrentSession(request.sessionToken)) return
        val target = request.target
        val applied = worldInstanceStateStore.applyClose(target, result.instance) {
            SharedFlowCentre.isCurrentSession(request.sessionToken)
        }
        if (!applied) return
        _closedInstanceLocations.tryEmit(target.location)
        SharedFlowCentre.toastText.emit(ToastText.Success(strings.instanceCloseSuccess))
    }

    private suspend fun emitInstanceCloseFailure(error: Throwable, strings: LocaleStrings) {
        val message = error.message
            ?.takeIf { it.isNotBlank() }
            ?.let { "${strings.instanceCloseFailed}: $it" }
            ?: strings.instanceCloseFailed
        emitInstanceCloseError(message)
    }

    private suspend fun emitInstanceCloseSessionChanged(userId: String?, strings: LocaleStrings) {
        if (userId != null && SharedFlowCentre.currentSession.value?.token?.userId == userId) {
            emitInstanceCloseError(strings.instanceCloseSessionChanged)
        }
    }

    private suspend fun emitInstanceCloseError(message: String) {
        SharedFlowCentre.toastText.emit(ToastText.Error(message))
    }

    fun refreshWorldData() {
        val worldId = _worldProfileState.value?.worldId ?: return
        if (_isLoading.value ||
            worldId.isBlank() ||
            deletion.state.value.isDeleting ||
            deletion.state.value.isDeleted
        ) return
        worldLoadJob?.cancel()
        val loadGeneration = ++worldLoadGeneration
        val requestUserId = SharedFlowCentre.currentSession.value?.token?.userId
        _isLoading.value = true
        worldLoadJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                loadWorldInfo(worldId, loadGeneration, requestUserId)
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                SharedFlowCentre.toastText.emit(
                    ToastText.Error(error.message ?: "Failed to load world data")
                )
            } finally {
                if (isCurrentWorldLoad(loadGeneration, worldId)) {
                    _isLoading.value = false
                    worldLoadJob = null
                    if (refreshMetadataAfterCurrentLoad) {
                        refreshMetadataAfterCurrentLoad = false
                        val currentUserId = SharedFlowCentre.currentSession.value?.token?.userId
                        if (_worldProfileState.value?.authorID == currentUserId) {
                            refreshWorldData()
                        }
                    }
                }
            }
        }
    }

    private suspend fun loadWorldInfo(
        worldId: String,
        loadGeneration: Long,
        requestUserId: String? = SharedFlowCentre.currentSession.value?.token?.userId,
    ) {
        val sessionToken = SharedFlowCentre.currentSession.value?.token ?: return
        val response = authService.runSessionBoundCatching(sessionToken) {
            worldsApi.getWorldById(worldId)
        } ?: return
        response.result.onSuccess { worldData ->
            if (!SharedFlowCentre.isCurrentSession(response.sessionToken) ||
                !isCurrentWorldLoad(loadGeneration, worldId)
            ) return@onSuccess
            worldCacheMutex.withLock {
                if (!SharedFlowCentre.isCurrentSession(response.sessionToken) ||
                    !isCurrentWorldLoad(loadGeneration, worldId)
                ) return@withLock
                worldProfileCacheStore.save(
                    WorldProfileCache(
                        world = worldData,
                        cachedAtEpochMilliseconds = Clock.System.now().toEpochMilliseconds(),
                        supportedPlatforms = worldData.supportedPlatforms,
                        platformFileSizes = null,
                    )
                )
            }
            if (!SharedFlowCentre.isCurrentSession(response.sessionToken) ||
                !isCurrentWorldLoad(loadGeneration, worldId)
            ) return@onSuccess

            // 更新世界基本信息
            _worldProfileState.value =
                WorldProfileVo(
                    world = worldData,
                    instancesList = _worldProfileState.value?.instances ?: mutableListOf(),
                    platformFileSizes = _worldProfileState.value?.platformFileSizes.orEmpty(),
                )
            metadataReady.value =
                requestUserId == SharedFlowCentre.currentSession.value?.token?.userId
            deletion.setTarget(worldData.id, worldData.authorId)
            loadPlatformFileSizes(worldData, loadGeneration)
            publication.acceptVerifiedWorld(worldData, response.sessionToken)
            // 获取实例ID列表
            val mergeInstanceIds = collectInstanceIds(
                profile = _worldProfileState.value ?: return@onSuccess,
                worldInstances = worldData.instances.orEmpty(),
            )
            // 如果有实例ID，则获取实例信息
            if (mergeInstanceIds.isNotEmpty()) {
                loadInstanceData(mergeInstanceIds, loadGeneration)
            }
        }.onFailure {
            if (SharedFlowCentre.isCurrentSession(response.sessionToken) &&
                isCurrentWorldLoad(loadGeneration, worldId)
            ) {
                SharedFlowCentre.toastText.emit(ToastText.Error(it.message ?: "Failed to load world data"))
            }
        }
    }

    private fun loadPlatformFileSizes(worldData: WorldData, loadGeneration: Long) {
        val job = viewModelScope.launch(Dispatchers.IO, start = CoroutineStart.LAZY) {
            val result = worldPlatformService.getWorldPlatformFileSizes(worldData)
            if (!isCurrentWorldLoad(loadGeneration, worldData.id)) return@launch
            _worldProfileState.update { currentProfile ->
                if (isCurrentWorldLoad(loadGeneration, worldData.id) &&
                    currentProfile?.worldId == worldData.id
                ) {
                    currentProfile.copy(platformFileSizes = result.platformFileSizes)
                } else {
                    currentProfile
                }
            }
            if (result.isComplete) {
                worldCacheMutex.withLock {
                    if (!isCurrentWorldLoad(loadGeneration, worldData.id)) return@withLock
                    worldProfileCacheStore.save(
                        WorldProfileCache(
                            world = worldData,
                            cachedAtEpochMilliseconds = Clock.System.now().toEpochMilliseconds(),
                            supportedPlatforms = worldData.supportedPlatforms,
                            platformFileSizes = result.platformFileSizes,
                        )
                    )
                }
            }
        }
        platformFileSizesJob.getAndSet(job)?.cancel()
        job.invokeOnCompletion { platformFileSizesJob.compareAndSet(job, null) }
        job.start()
    }

    private suspend fun applyPublicationWorld(worldData: WorldData): Result<Unit> {
        val loadGeneration = worldLoadGeneration
        return try {
            worldCacheMutex.withLock {
                val current = _worldProfileState.value
                    ?: return@withLock Result.failure(
                        IllegalStateException("World profile is no longer active")
                    )
                if (current.worldId != worldData.id ||
                    !isCurrentWorldLoad(loadGeneration, worldData.id)
                ) {
                    return@withLock Result.failure(
                        IllegalStateException("World profile target changed before cache sync")
                    )
                }
                val cachedPlatformFileSizes =
                    worldProfileCacheStore.load(worldData.id)?.platformFileSizes
                worldProfileCacheStore.save(
                    WorldProfileCache(
                        world = worldData,
                        cachedAtEpochMilliseconds = Clock.System.now().toEpochMilliseconds(),
                        supportedPlatforms = worldData.supportedPlatforms,
                        platformFileSizes = cachedPlatformFileSizes,
                    )
                )
                if (!isCurrentWorldLoad(loadGeneration, worldData.id)) {
                    return@withLock Result.failure(
                        IllegalStateException("World profile target changed during cache sync")
                    )
                }
                _worldProfileState.value = WorldProfileVo(
                    world = worldData,
                    instancesList = current.instances,
                    platformFileSizes = current.platformFileSizes,
                )
                Result.success(Unit)
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            Result.failure(error)
        }
    }

    internal fun changeWorldPublication(action: WorldPublicationAction) {
        publication.changePublication(action)
    }

    private fun collectInstanceIds(
        profile: WorldProfileVo,
        worldInstances: List<List<String>>,
    ): Map<String, Owner?> {
        val currentInstanceIds = profile.instances
            .associate { it.instanceId to it.owner.value }
            .filterKeys { it.isNotBlank() }
        val publicInstanceIds = worldInstances
            .mapNotNull { it.firstOrNull() }
            .filter { it.isNotBlank() }
            .associateWith { null }
        return currentInstanceIds + publicInstanceIds
    }

    /**
     * 加载实例数据
     */
    private suspend fun loadInstanceData(
        instanceIds: Map<String, Owner?>,
        loadGeneration: Long? = null,
    ) {
        val currentProfile = _worldProfileState.value ?: return
        if (loadGeneration != null && !isCurrentWorldLoad(loadGeneration, currentProfile.worldId)) return
        authService.reTryAuthCatching {
            // 获取所有实例数据
            instanceIds.entries.asFlow().mapNotNull { (instanceId, initialOwner) ->
                try {
                    worldInstanceStateStore.refreshInstance(
                        worldId = currentProfile.worldId,
                        instanceId = instanceId,
                        initialOwner = initialOwner,
                        fetch = {
                            worldsApi.getWorldInstanceById(currentProfile.worldId, instanceId)
                        },
                        canCommit = {
                            loadGeneration == null ||
                                isCurrentWorldLoad(loadGeneration, currentProfile.worldId)
                        },
                    )
                } catch (error: Throwable) {
                    if (error is CancellationException) throw error
                    SharedFlowCentre.toastText.emit(
                        ToastText.Error(error.message ?: "Failed to load instance data")
                    )
                    null
                }
            }.catch {
                SharedFlowCentre.toastText.emit(ToastText.Error(it.message ?: "Failed to load instance data"))
            }.collect { (ownerId, owner) ->
                // 如果实例是活跃的，则获取实例的拥有者名称
                if (loadGeneration != null && !isCurrentWorldLoad(loadGeneration, currentProfile.worldId)) return@collect
                fetchAndSetOwner(ownerId)
                    .onSuccess {
                        if (it != null &&
                            (loadGeneration == null || isCurrentWorldLoad(loadGeneration, currentProfile.worldId))
                        ) {
                            owner.value = it
                        }
                    }
                    .onFailure { SharedFlowCentre.toastText.emit(ToastText.Error(it.message ?: "Failed to load instance Owner")) }
            }
        }
    }

    private fun isCurrentWorldLoad(loadGeneration: Long, worldId: String): Boolean =
        worldLoadGeneration == loadGeneration &&
            _worldProfileState.value?.worldId == worldId &&
            !deletion.state.value.isDeleting &&
            !deletion.state.value.isDeleted

    /**
     * 获取房间实例的拥有者名称
     */
    private suspend fun fetchAndSetOwner(
        ownerId: String?,
    ): Result<Owner?> = runCatching {
        if (ownerId == null) return@runCatching null
        return@runCatching when (BlueprintType.fromValue(ownerId)) {
            BlueprintType.User -> {
                val user = usersApi.fetchUser(ownerId)
                Owner(
                    id = user.id,
                    displayName = user.displayName,
                    type = BlueprintType.User
                )
            }

            BlueprintType.Group -> {
                val group = groupsApi.fetchGroup(ownerId)
                Owner(
                    id = group.id,
                    displayName = group.name,
                    type = BlueprintType.Group
                )
            }

            else -> {
                null
            }
        }
    }

    internal fun prepareInstanceCreation() {
        if (instanceCreationGroupsJob?.isActive == true) return
        _instanceCreationState.value = InstanceCreationSubmissionState.Idle
        val token = SharedFlowCentre.currentSession.value?.token ?: run {
            _instanceCreationGroups.value = InstanceCreationGroupsState.Failed
            return
        }
        _instanceCreationGroups.value = InstanceCreationGroupsState.Loading
        val generation = ++instanceCreationGroupsGeneration
        instanceCreationGroupsJob = viewModelScope.launch(Dispatchers.IO) {
            val response = authService.runSessionBoundCatching(token) {
                usersApi.getUserGroups(token.userId).map { limitedGroup ->
                    val groupId = limitedGroup.groupId.ifBlank { limitedGroup.id }
                    groupsApi.fetchGroup(groupId, includeRoles = true)
                }
            }
            if (generation != instanceCreationGroupsGeneration) return@launch
            if (response == null) {
                _instanceCreationGroups.value = InstanceCreationGroupsState.Failed
                return@launch
            }
            if (!SharedFlowCentre.isCurrentSession(response.sessionToken)) {
                _instanceCreationGroups.value = InstanceCreationGroupsState.Failed
                return@launch
            }
            response.result.fold(
                onSuccess = { groups ->
                    if (generation == instanceCreationGroupsGeneration) {
                        _instanceCreationGroups.value = InstanceCreationGroupsState.Ready(
                            groups.map { group ->
                                InstanceCreationGroup(
                                    id = group.id,
                                    name = group.name,
                                    permissions = group.myMember?.permissions.orEmpty().toSet(),
                                    roles = group.roles.orEmpty()
                                        .filter { it.id.isNotBlank() }
                                        .map { InstanceCreationRole(it.id, it.name.ifBlank { it.id }) },
                                )
                            }.filter { it.canCreateAny }.sortedBy { it.name.lowercase() }
                        )
                    }
                },
                onFailure = {
                    if (generation == instanceCreationGroupsGeneration) {
                        _instanceCreationGroups.value = InstanceCreationGroupsState.Failed
                    }
                },
            )
        }
    }

    internal fun resetInstanceCreationState() {
        if (_instanceCreationState.value != InstanceCreationSubmissionState.Submitting) {
            _instanceCreationState.value = InstanceCreationSubmissionState.Idle
        }
    }

    /** Creates an instance from the validated dialog draft and then invites the current account. */
    internal fun createInstanceAndInviteSelf(
        draft: InstanceCreationDraft,
        strings: LocaleStrings,
    ) {
        val worldId = _worldProfileState.value?.worldId ?: return
        if (_instanceCreationState.value == InstanceCreationSubmissionState.Submitting) return
        val groups = (_instanceCreationGroups.value as? InstanceCreationGroupsState.Ready)
            ?.groups.orEmpty()
        if (draft.validationError(groups) != null) {
            _instanceCreationState.value = InstanceCreationSubmissionState.Failed
            return
        }
        val session = SharedFlowCentre.currentSession.value ?: return
        _instanceCreationState.value = InstanceCreationSubmissionState.Submitting
        val generation = ++instanceCreationGeneration
        instanceCreationJob = viewModelScope.launch(Dispatchers.IO) {
            val options = InstanceCreationOptions(
                worldId = worldId,
                accessType = draft.accessType,
                region = draft.region,
                userId = session.account.userId,
                queueEnabled = draft.queueEnabled,
                groupId = draft.groupId,
                roleIds = draft.roleIds,
                ageGate = draft.ageGate,
                displayName = draft.displayName,
                minimumAvatarPerformance = draft.minimumAvatarPerformance,
            )
            when (val result = instanceCreationService.create(options)) {
                is InstanceCreationResult.Created -> {
                    if (generation != instanceCreationGeneration) return@launch
                    if (!SharedFlowCentre.isCurrentSession(result.sessionToken) ||
                        _worldProfileState.value?.worldId != worldId
                    ) {
                        _instanceCreationState.value = InstanceCreationSubmissionState.Idle
                        return@launch
                    }
                    val groupOwner = groups.firstOrNull { it.id == draft.groupId }
                    val owner = if (groupOwner != null) {
                        Owner(groupOwner.id, groupOwner.name, BlueprintType.Group)
                    } else {
                        val currentUserName = authService.currentUserState.value
                            ?.takeIf { it.id == result.sessionToken.userId }
                            ?.displayName
                            ?: result.sessionToken.userId
                        Owner(result.sessionToken.userId, currentUserName, BlueprintType.User)
                    }
                    val currentProfile = _worldProfileState.value ?: run {
                        _instanceCreationState.value = InstanceCreationSubmissionState.Idle
                        return@launch
                    }
                    _worldProfileState.value = currentProfile.copy(
                        instances = currentProfile.instances +
                            InstanceVo(result.instance, MutableStateFlow(owner))
                    )

                    val inviteToken = SharedFlowCentre.currentSession.value?.token
                        ?.takeIf { it.userId == result.sessionToken.userId }
                        ?: run {
                            if (generation == instanceCreationGeneration) {
                                _instanceCreationState.value = InstanceCreationSubmissionState.Idle
                            }
                            return@launch
                        }
                    val inviteResponse = authService.runSessionBoundCatching(inviteToken) {
                        inviteApi.inviteMyselfToInstance(result.instance.id)
                    }
                    if (inviteResponse != null &&
                        SharedFlowCentre.isCurrentSession(inviteResponse.sessionToken)
                    ) {
                        inviteResponse.result.fold(
                            onSuccess = {
                                SharedFlowCentre.toastText.emit(
                                    ToastText.Success(strings.instanceCreateSuccess)
                                )
                            },
                            onFailure = {
                                SharedFlowCentre.toastText.emit(
                                    ToastText.Error(
                                        strings.instanceCreateSuccessButInviteFailed + ": ${it.message}"
                                    )
                                )
                            },
                        )
                        if (generation == instanceCreationGeneration) {
                            _instanceCreationState.value = InstanceCreationSubmissionState.Created
                        }
                    } else if (SharedFlowCentre.currentSession.value?.account?.userId ==
                        result.sessionToken.userId
                    ) {
                        SharedFlowCentre.toastText.emit(
                            ToastText.Error(strings.instanceCreateSuccessButInviteFailed)
                        )
                        if (generation == instanceCreationGeneration) {
                            _instanceCreationState.value = InstanceCreationSubmissionState.Created
                        }
                    } else if (generation == instanceCreationGeneration) {
                        _instanceCreationState.value = InstanceCreationSubmissionState.Idle
                    }
                }

                InstanceCreationResult.InFlight -> {
                    if (generation == instanceCreationGeneration) {
                        _instanceCreationState.value = InstanceCreationSubmissionState.Failed
                    }
                }

                InstanceCreationResult.SessionChanged -> {
                    if (generation == instanceCreationGeneration) {
                        _instanceCreationState.value = InstanceCreationSubmissionState.Idle
                    }
                }

                is InstanceCreationResult.Failed -> {
                    if (generation == instanceCreationGeneration) {
                        _instanceCreationState.value = InstanceCreationSubmissionState.Failed
                        SharedFlowCentre.toastText.emit(
                            ToastText.Error(strings.instanceCreateFailed + ": ${result.error.message}")
                        )
                    }
                }
            }
        }
    }

    /**
     * 收藏世界
     */
    fun onWorldFavorite(favoriteGroupId: Result<String>) {
        favoriteGroupId.onSuccess {
            refreshWorldData()
        }
    }

    internal fun deleteWorld(): Boolean {
        if (!deletion.state.value.canDelete ||
            publication.state.value.isChanging ||
            worldPersistence.state.value.status == WorldPersistenceStatus.Deleting
        ) return false

        // 先取消缓存/详情加载并提升代次，再启动不可逆请求，迟到响应只能被丢弃。
        worldLoadGeneration++
        worldLoadJob?.cancel()
        worldLoadJob = null
        platformFileSizesJob.getAndSet(null)?.cancel()
        _isLoading.value = false
        return deletion.delete()
    }
}

internal fun homeWorldActionAvailability(
    worldId: String?,
    user: HomeWorldUserContext?,
): HomeWorldActionAvailability = when {
    worldId == null || !io.github.vrcmteam.vrcm.service.isWorldId(worldId) || user == null ->
        HomeWorldActionAvailability.Unavailable
    user.homeLocation == worldId -> HomeWorldActionAvailability.Current
    else -> HomeWorldActionAvailability.CanSet
}
