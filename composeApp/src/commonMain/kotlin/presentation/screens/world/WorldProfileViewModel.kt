package io.github.vrcmteam.vrcm.presentation.screens.world

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.vrcmteam.vrcm.core.shared.SharedFlowCentre
import io.github.vrcmteam.vrcm.network.api.attributes.AccessType
import io.github.vrcmteam.vrcm.network.api.attributes.BlueprintType
import io.github.vrcmteam.vrcm.network.api.attributes.FavoriteType
import io.github.vrcmteam.vrcm.network.api.attributes.RegionType
import io.github.vrcmteam.vrcm.network.api.groups.GroupsApi
import io.github.vrcmteam.vrcm.network.api.instances.InstancesApi
import io.github.vrcmteam.vrcm.network.api.invite.InviteApi
import io.github.vrcmteam.vrcm.network.api.users.UsersApi
import io.github.vrcmteam.vrcm.network.api.worlds.WorldsApi
import io.github.vrcmteam.vrcm.network.api.worlds.data.WorldData
import io.github.vrcmteam.vrcm.presentation.compoments.ToastText
import io.github.vrcmteam.vrcm.presentation.favorites.FavoriteEntrySource
import io.github.vrcmteam.vrcm.presentation.favorites.FavoriteEntryState
import io.github.vrcmteam.vrcm.presentation.favorites.FavoriteEntryStateModel
import io.github.vrcmteam.vrcm.presentation.screens.world.data.InstanceVo
import io.github.vrcmteam.vrcm.presentation.screens.world.data.InstanceVo.Owner
import io.github.vrcmteam.vrcm.presentation.screens.world.data.WorldProfileVo
import io.github.vrcmteam.vrcm.presentation.settings.locale.LocaleStrings
import io.github.vrcmteam.vrcm.service.AuthService
import io.github.vrcmteam.vrcm.service.HomeWorldManager
import io.github.vrcmteam.vrcm.service.HomeWorldSessionChangedException
import io.github.vrcmteam.vrcm.service.HomeWorldUserContext
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

internal data class HomeWorldActionState(
    val availability: HomeWorldActionAvailability = HomeWorldActionAvailability.Unavailable,
    val isUpdating: Boolean = false,
)

internal sealed interface HomeWorldNotice {
    data object Set : HomeWorldNotice
    data object Reset : HomeWorldNotice
    data object UpdateFailed : HomeWorldNotice
}

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
    favoriteEntrySource: FavoriteEntrySource,
    private val inviteApi: InviteApi,
    private val worldPlatformService: WorldPlatformService,
    private val worldProfileCacheStore: WorldProfileCacheStore,
    private val homeWorldManager: HomeWorldManager,
) : ViewModel() {
    // 世界数据状态
    private val _worldProfileState = MutableStateFlow<WorldProfileVo?>(null)
    val worldProfileState: StateFlow<WorldProfileVo?> = _worldProfileState.asStateFlow()
    private val worldInstanceStateStore = WorldInstanceStateStore(_worldProfileState)

    // 加载状态
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
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
            SharedFlowCentre.currentSession.collect { session ->
                instanceCloseCoordinator.onSessionChanged(session?.token)
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

    private val worldCacheMutex = Mutex()

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

    internal fun checkWorldPersistence() = worldPersistence.check()

    internal fun requestWorldPersistenceDeletion() = worldPersistence.requestDeletion()

    internal fun dismissWorldPersistenceDeletion() = worldPersistence.dismissDeletionConfirmation()

    internal fun confirmWorldPersistenceDeletion() = worldPersistence.confirmDeletion()
    internal fun updateHomeWorld() {
        val worldId = worldProfileState.value?.worldId ?: return
        val reset = when (homeWorldActionState.value.availability) {
            HomeWorldActionAvailability.CanSet -> false
            HomeWorldActionAvailability.Current -> true
            HomeWorldActionAvailability.Unavailable -> return
        }
        if (!isUpdatingHomeWorld.compareAndSet(expect = false, update = true)) return

        viewModelScope.launch(Dispatchers.IO) {
            try {
                homeWorldManager.updateHomeWorld(worldId.takeUnless { reset })
                    .onSuccess {
                        if (_worldProfileState.value?.worldId == worldId) {
                            _homeWorldNotices.emit(
                                if (reset) HomeWorldNotice.Reset else HomeWorldNotice.Set
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
            _worldProfileState.value = WorldProfileVo(
                world = cached.world,
                instancesList = worldProfileVO.instances,
                platformFileSizes = cached.platformFileSizes ?: worldProfileVO.platformFileSizes,
            )
        }

        val shouldRefreshProfile = cached == null ||
            cached.isExpired(Clock.System.now().toEpochMilliseconds()) ||
            cached.world.instances == null ||
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
        _isLoading.value = true
        worldLoadJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                loadWorldInfo(worldId, loadGeneration)
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

    private suspend fun loadWorldInfo(worldId: String, loadGeneration: Long) {
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

    /**
     * 创建世界实例并邀请自己
     */
    fun createInstanceAndInviteSelf(
        accessType: AccessType,
        region: RegionType,
        queueEnabled: Boolean = false,
        groupId: String? = null,
        groupName: String? = null,
        groupAccessType: String? = null,
        roleIds: List<String>? = null,
        strings: LocaleStrings,
    ) {
        val worldId = _worldProfileState.value?.worldId ?: return
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            try {
                // 获取当前用户ID
                val userId = authService.currentUser().id

                // 创建实例
                authService.reTryAuthCatching {
                    instancesApi.createInstance(
                        worldId = worldId,
                        accessType = accessType,
                        region = region,
                        userId = userId,
                        queueEnabled = queueEnabled,
                        groupId = groupId,
                        groupAccessType = groupAccessType,
                        roleIds = roleIds
                    )
                }.onSuccess { instanceData ->
                    val owner =
                        if (groupId != null && groupName != null)
                            Owner(groupId, groupName, BlueprintType.Group)
                        else
                            Owner(userId, authService.currentUser().displayName, BlueprintType.User)
                    // 更新实例列表
                    val ownerState: MutableStateFlow<Owner?> = MutableStateFlow(owner)
                    val instanceVo = InstanceVo(instanceData, ownerState)
                    val currentInstances = _worldProfileState.value?.instances?.toMutableList() ?: mutableListOf()
                    currentInstances.add(instanceVo)
                    _worldProfileState.value = _worldProfileState.value?.copy(instances = currentInstances)

                    // 邀请自己
                    authService.reTryAuthCatching {
                        inviteApi.inviteMyselfToInstance(instanceData.id)
                    }.onSuccess {
                        SharedFlowCentre.toastText.emit(ToastText.Success(strings.instanceCreateSuccess))
                    }.onFailure {
                        SharedFlowCentre.toastText.emit(ToastText.Error(strings.instanceCreateSuccessButInviteFailed + ": ${it.message}"))
                    }
                }.onFailure {
                    SharedFlowCentre.toastText.emit(ToastText.Error(strings.instanceCreateFailed + ": ${it.message}"))
                }
            } finally {
                _isLoading.value = false
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
