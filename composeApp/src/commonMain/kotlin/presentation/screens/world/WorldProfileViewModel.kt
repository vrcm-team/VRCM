package io.github.vrcmteam.vrcm.presentation.screens.world

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.vrcmteam.vrcm.core.extensions.removeFirst
import io.github.vrcmteam.vrcm.core.shared.SharedFlowCentre
import io.github.vrcmteam.vrcm.network.api.attributes.BlueprintType
import io.github.vrcmteam.vrcm.network.api.attributes.FavoriteType
import io.github.vrcmteam.vrcm.network.api.groups.GroupsApi
import io.github.vrcmteam.vrcm.network.api.invite.InviteApi
import io.github.vrcmteam.vrcm.network.api.instances.data.InstanceCreationOptions
import io.github.vrcmteam.vrcm.network.api.users.UsersApi
import io.github.vrcmteam.vrcm.network.api.worlds.WorldsApi
import io.github.vrcmteam.vrcm.presentation.compoments.ToastText
import io.github.vrcmteam.vrcm.presentation.favorites.FavoriteEntrySource
import io.github.vrcmteam.vrcm.presentation.favorites.FavoriteEntryState
import io.github.vrcmteam.vrcm.presentation.favorites.FavoriteEntryStateModel
import io.github.vrcmteam.vrcm.presentation.screens.world.data.InstanceVo
import io.github.vrcmteam.vrcm.presentation.screens.world.data.InstanceVo.Owner
import io.github.vrcmteam.vrcm.presentation.screens.world.data.InstanceCreationDraft
import io.github.vrcmteam.vrcm.presentation.screens.world.data.InstanceCreationGroup
import io.github.vrcmteam.vrcm.presentation.screens.world.data.InstanceCreationGroupsState
import io.github.vrcmteam.vrcm.presentation.screens.world.data.InstanceCreationRole
import io.github.vrcmteam.vrcm.presentation.screens.world.data.InstanceCreationSubmissionState
import io.github.vrcmteam.vrcm.presentation.screens.world.data.WorldProfileVo
import io.github.vrcmteam.vrcm.presentation.screens.world.data.validationError
import io.github.vrcmteam.vrcm.presentation.settings.locale.LocaleStrings
import io.github.vrcmteam.vrcm.service.AuthService
import io.github.vrcmteam.vrcm.service.InstanceCreationResult
import io.github.vrcmteam.vrcm.service.InstanceCreationService
import io.github.vrcmteam.vrcm.service.WorldPlatformService
import io.github.vrcmteam.vrcm.storage.WorldProfileCacheStore
import io.github.vrcmteam.vrcm.storage.data.WorldProfileCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * 世界档案页面的ViewModel，负责处理世界数据的加载和刷新
 */
@OptIn(ExperimentalTime::class)
class WorldProfileScreenModel internal constructor(
    private val worldsApi: WorldsApi,
    private val usersApi: UsersApi,
    private val groupsApi: GroupsApi,
    private val authService: AuthService,
    private val instanceCreationService: InstanceCreationService,
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

    private val favoriteEntry = FavoriteEntryStateModel(
        favoriteType = FavoriteType.World,
        source = favoriteEntrySource,
        scope = viewModelScope,
    )
    internal val favoriteEntryState: StateFlow<FavoriteEntryState> = favoriteEntry.state

    init {
        viewModelScope.launch {
            var activeUserId = SharedFlowCentre.currentSession.value?.account?.userId
            SharedFlowCentre.currentSession
                .map { it?.account?.userId }
                .distinctUntilChanged()
                .collect { userId ->
                    if (userId == activeUserId) return@collect
                    activeUserId = userId
                    cancelInstanceCreationGroups()
                    cancelInstanceCreation()
                    _instanceCreationGroups.value = InstanceCreationGroupsState.Idle
                    _instanceCreationState.value = InstanceCreationSubmissionState.Idle
                }
        }
    }

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

    /**
     * 刷新世界数据
     */
    fun loadWorldData(worldProfileVO: WorldProfileVo) {
        if (_worldProfileState.value?.worldId != null &&
            _worldProfileState.value?.worldId != worldProfileVO.worldId
        ) {
            cancelInstanceCreation()
        }
        _worldProfileState.value = worldProfileVO
        val worldId = worldProfileVO.worldId
        favoriteEntry.load(worldId)
        if (worldId.isBlank()) return
        // 缓存读取改为挂起（Room），先出网络前的占位状态，缓存到达后再回填。
        viewModelScope.launch { applyCachedWorld(worldId, worldProfileVO) }
    }

    private suspend fun applyCachedWorld(worldId: String, worldProfileVO: WorldProfileVo) {
        val cached = worldProfileCacheStore.load(worldId)
        if (cached != null) {
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
            refreshWorldData()
        } else {
            loadCachedInstances(cached.world.instances.orEmpty())
        }
    }

    private fun loadCachedInstances(worldInstances: List<List<String>>) {
        val profile = _worldProfileState.value ?: return
        val instanceIds = collectInstanceIds(profile, worldInstances)
        if (instanceIds.isEmpty() || _isLoading.value) return

        _isLoading.value = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                loadInstanceData(instanceIds)
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                SharedFlowCentre.toastText.emit(
                    ToastText.Error(error.message ?: "Failed to load instance data")
                )
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun refreshWorldData() {
        val worldId = _worldProfileState.value?.worldId ?: return
        if (_isLoading.value || worldId.isBlank()) return
        _isLoading.value = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                loadWorldInfo(worldId)
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                SharedFlowCentre.toastText.emit(
                    ToastText.Error(error.message ?: "Failed to load world data")
                )
            } finally {
                _isLoading.value = false
            }
        }
    }

    private suspend fun loadWorldInfo(worldId: String) {
        authService.reTryAuthCatching {
            worldsApi.getWorldById(worldId)
        }.onSuccess { worldData ->
            worldProfileCacheStore.save(
                WorldProfileCache(
                    world = worldData,
                    cachedAtEpochMilliseconds = Clock.System.now().toEpochMilliseconds(),
                )
            )
            if (_worldProfileState.value?.worldId != worldId) return@onSuccess

            // TODO
//            val platformFileSizes = mutableStateListOf<PlatformFileSize>()
            // 更新世界基本信息
            _worldProfileState.value =
                WorldProfileVo(
                    world = worldData, 
                    instancesList = _worldProfileState.value?.instances ?: mutableListOf(),
//                    platformFileSizes = platformFileSizes,
                )
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
                loadInstanceData(mergeInstanceIds)
            }
        }.onFailure {
            SharedFlowCentre.toastText.emit(ToastText.Error(it.message ?: "Failed to load world data"))
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
    private suspend fun loadInstanceData(instanceIds: Map<String, Owner?>) {
        val currentProfile = _worldProfileState.value ?: return
        val instanceVos = currentProfile.instances.toMutableList()
        _worldProfileState.value = currentProfile.copy(instances = instanceVos)
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
                _worldProfileState.value = _worldProfileState.value?.copy(instances = instanceVos.toList())
                instanceData.ownerId to owner
            }.collect { (ownerId, owner) ->
                // 如果实例是活跃的，则获取实例的拥有者名称
                fetchAndSetOwner(ownerId)
                    .onSuccess { if (it != null) owner.value = it }
                    .onFailure { SharedFlowCentre.toastText.emit(ToastText.Error(it.message ?: "Failed to load instance Owner")) }
            }
        }
    }

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
} 
