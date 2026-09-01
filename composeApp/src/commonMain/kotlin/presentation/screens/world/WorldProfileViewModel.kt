package io.github.vrcmteam.vrcm.presentation.screens.world

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.vrcmteam.vrcm.core.extensions.removeFirst
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
import io.github.vrcmteam.vrcm.presentation.compoments.ToastText
import io.github.vrcmteam.vrcm.presentation.favorites.FavoriteEntrySource
import io.github.vrcmteam.vrcm.presentation.favorites.FavoriteEntryState
import io.github.vrcmteam.vrcm.presentation.favorites.FavoriteEntryStateModel
import io.github.vrcmteam.vrcm.presentation.screens.world.data.InstanceVo
import io.github.vrcmteam.vrcm.presentation.screens.world.data.InstanceVo.Owner
import io.github.vrcmteam.vrcm.presentation.screens.world.data.WorldProfileVo
import io.github.vrcmteam.vrcm.presentation.settings.locale.LocaleStrings
import io.github.vrcmteam.vrcm.service.AuthService
import io.github.vrcmteam.vrcm.service.WorldPlatformService
import io.github.vrcmteam.vrcm.storage.WorldProfileCacheStore
import io.github.vrcmteam.vrcm.storage.data.WorldProfileCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

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
) : ViewModel() {
    // 世界数据状态
    private val _worldProfileState = MutableStateFlow<WorldProfileVo?>(null)
    val worldProfileState: StateFlow<WorldProfileVo?> = _worldProfileState.asStateFlow()

    // 加载状态
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val favoriteEntry = FavoriteEntryStateModel(
        favoriteType = FavoriteType.World,
        source = favoriteEntrySource,
        scope = viewModelScope,
    )
    internal val favoriteEntryState: StateFlow<FavoriteEntryState> = favoriteEntry.state

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

    /**
     * 刷新世界数据
     */
    fun loadWorldData(worldProfileVO: WorldProfileVo) {
        worldLoadJob?.cancel()
        val loadGeneration = ++worldLoadGeneration
        _worldProfileState.value = worldProfileVO
        val worldId = worldProfileVO.worldId
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
            deletion.setTarget(cached.world.id, cached.world.authorId)
            _worldProfileState.value = WorldProfileVo(
                world = cached.world,
                instancesList = worldProfileVO.instances,
                platformFileSizes = worldProfileVO.platformFileSizes,
            )
        }

        val shouldRefreshProfile = cached == null ||
            cached.isExpired(Clock.System.now().toEpochMilliseconds()) ||
            cached.world.instances == null
        if (shouldRefreshProfile) {
            loadWorldInfo(worldId, loadGeneration)
        } else {
            val profile = _worldProfileState.value ?: return
            loadInstanceData(
                collectInstanceIds(profile, cached.world.instances.orEmpty()),
                loadGeneration,
            )
        }
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
        authService.reTryAuthCatching {
            worldsApi.getWorldById(worldId)
        }.onSuccess { worldData ->
            worldCacheMutex.withLock {
                if (!isCurrentWorldLoad(loadGeneration, worldId)) return@withLock
                worldProfileCacheStore.save(
                    WorldProfileCache(
                        world = worldData,
                        cachedAtEpochMilliseconds = Clock.System.now().toEpochMilliseconds(),
                    )
                )
            }
            if (!isCurrentWorldLoad(loadGeneration, worldId)) return@onSuccess

            // TODO
//            val platformFileSizes = mutableStateListOf<PlatformFileSize>()
            // 更新世界基本信息
            _worldProfileState.value =
                WorldProfileVo(
                    world = worldData, 
                    instancesList = _worldProfileState.value?.instances ?: mutableListOf(),
//                    platformFileSizes = platformFileSizes,
                )
            deletion.setTarget(worldData.id, worldData.authorId)
//            viewModelScope.launch(Dispatchers.IO) {
//                // 获取平台文件大小信息
//                runCatching {
//                    platformFileSizes.addAll(worldPlatformService.getWorldPlatformFileSizes(worldData))
//                }.onFailure {
//                    SharedFlowCentre.toastText.emit(ToastText.Error("Failed to load platform file sizes: ${it.message}"))
//                }
//            }
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
            if (isCurrentWorldLoad(loadGeneration, worldId)) {
                SharedFlowCentre.toastText.emit(ToastText.Error(it.message ?: "Failed to load world data"))
            }
        }
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
        val instanceVos = currentProfile.instances.toMutableList()
        if (loadGeneration == null || isCurrentWorldLoad(loadGeneration, currentProfile.worldId)) {
            _worldProfileState.value = currentProfile.copy(instances = instanceVos)
        }
        authService.reTryAuthCatching {
            // 获取所有实例数据
            instanceIds.keys.asFlow().mapNotNull { instanceId ->
                try {
                    worldsApi.getWorldInstanceById(currentProfile.worldId, instanceId)
                } catch (error: Throwable) {
                    if (error is CancellationException) throw error
                    SharedFlowCentre.toastText.emit(
                        ToastText.Error(error.message ?: "Failed to load instance data")
                    )
                    null
                }
            }.catch {
                SharedFlowCentre.toastText.emit(ToastText.Error(it.message ?: "Failed to load instance data"))
            }.map { instanceData ->
                val owner: MutableStateFlow<Owner?> = MutableStateFlow(instanceIds[instanceData.instanceId])
                val instanceVo = InstanceVo(instanceData, owner)
                instanceVos.removeFirst { it.id == instanceData.id }
                instanceVos.add(instanceVo)
                if (loadGeneration == null || isCurrentWorldLoad(loadGeneration, currentProfile.worldId)) {
                    _worldProfileState.value = _worldProfileState.value?.copy(instances = instanceVos.toList())
                }
                instanceData.ownerId to owner
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
        if (!deletion.state.value.canDelete) return false

        // 先取消缓存/详情加载并提升代次，再启动不可逆请求，迟到响应只能被丢弃。
        worldLoadGeneration++
        worldLoadJob?.cancel()
        worldLoadJob = null
        _isLoading.value = false
        return deletion.delete()
    }
}
