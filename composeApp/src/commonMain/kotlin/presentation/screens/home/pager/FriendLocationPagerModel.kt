package io.github.vrcmteam.vrcm.presentation.screens.home.pager

import androidx.compose.runtime.*
import io.github.vrcmteam.vrcm.core.shared.SharedFlowCentre
import io.github.vrcmteam.vrcm.network.api.attributes.BlueprintType
import io.github.vrcmteam.vrcm.network.api.attributes.LocationType
import io.github.vrcmteam.vrcm.network.api.friends.date.FriendData
import io.github.vrcmteam.vrcm.network.api.groups.GroupsApi
import io.github.vrcmteam.vrcm.network.api.instances.InstancesApi
import io.github.vrcmteam.vrcm.network.api.users.UsersApi
import io.github.vrcmteam.vrcm.presentation.compoments.ToastText
import io.github.vrcmteam.vrcm.presentation.extensions.onApiFailure
import io.github.vrcmteam.vrcm.presentation.screens.home.data.FriendLocation
import io.github.vrcmteam.vrcm.presentation.screens.home.data.HomeInstanceVo
import io.github.vrcmteam.vrcm.service.AuthService
import io.github.vrcmteam.vrcm.service.FriendService
import io.github.vrcmteam.vrcm.service.FriendPresence
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized

internal class FriendLocationPublishedState {
    val locationMap: MutableMap<LocationType, MutableList<FriendLocation>> = mutableStateMapOf()

    private val _locationsByUser = MutableStateFlow<Map<String, FriendLocation>>(emptyMap())
    val locationsByUser: StateFlow<Map<String, FriendLocation>> = _locationsByUser.asStateFlow()

    fun clear() {
        locationMap.clear()
        _locationsByUser.value = emptyMap()
    }

    fun publishIndex() {
        val locationsByUser = mutableMapOf<String, FriendLocation>()
        locationMap.values.flatten().forEach { location ->
            location.friends.keys.forEach { userId -> locationsByUser[userId] = location }
        }
        _locationsByUser.value = locationsByUser
    }

    fun syncSimpleLocation(type: LocationType, friends: List<FriendData>) {
        if (friends.isEmpty()) {
            locationMap.remove(type)
            return
        }
        val location = locationMap.getOrPut(type) {
            mutableStateListOf(
                when (type) {
                    LocationType.Offline -> FriendLocation.Offline
                    LocationType.Web -> FriendLocation.Web
                    else -> FriendLocation.Private
                }
            )
        }.first()
        syncFriends(location, FriendLocationGroup(friends))
    }
}

class FriendLocationPagerModel(
    private val friendService: FriendService,
    private val usersApi: UsersApi,
    private val groupsApi: GroupsApi,
    private val instancesApi: InstancesApi,
    private val authService: AuthService,
) {
    // This model is a Koin singleton because its derived location index is shared by
    // the home pager and profile screens.
    private val modelScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val initialSession = SharedFlowCentre.currentSession.value
    private val publishedState = FriendLocationPublishedState()
    val friendLocationMap = publishedState.locationMap
    val friendLocationsByUser = publishedState.locationsByUser
    val currentUserId: String
        get() = authService.accountDto().userId

    private val updateMutex = Mutex()
    private val instanceFetchSemaphore = Semaphore(4)
    private val instanceJobsLock = SynchronizedObject()
    private val instanceJobs = mutableMapOf<String, Job>()
    private val foregroundGeneration = atomic(0L)
    private val refreshGeneration = atomic(0L)
    private val isForeground = atomic(true)
    private var activeAccountUserId = initialSession?.account?.userId
    private var refreshJob: Job? = null
    private val _isRefreshing = MutableStateFlow(false)

    val isRefreshing: StateFlow<Boolean> = combine(
        _isRefreshing,
        friendService.isRefreshing,
    ) { locationRefreshing, friendsRefreshing ->
        locationRefreshing || friendsRefreshing
    }.stateIn(
        scope = modelScope,
        started = SharingStarted.Eagerly,
        initialValue = friendService.isRefreshing.value,
    )

    fun close() {
        modelScope.cancel()
    }

    init {
        modelScope.launch {
            combine(
                friendService.friendState,
                friendService.currentUserLocation,
                authService.currentUserState,
            ) { _, _, _ -> Unit }.collect {
                syncFriendLocations()
            }
        }
        modelScope.launch {
            SharedFlowCentre.currentSession.collect { session ->
                val nextUserId = session?.account?.userId
                if (activeAccountUserId != nextUserId) {
                    activeAccountUserId = nextUserId
                    refreshGeneration.incrementAndGet()
                    refreshJob?.cancel()
                    refreshJob = null
                    _isRefreshing.value = false
                    updateMutex.withLock {
                        clearFriendLocations()
                    }
                }
            }
        }
    }

    fun onBackground() {
        isForeground.value = false
        foregroundGeneration.incrementAndGet()
        synchronized(instanceJobsLock) {
            instanceJobs.values.forEach(Job::cancel)
            instanceJobs.clear()
        }
        refreshGeneration.incrementAndGet()
        refreshJob?.cancel()
        refreshJob = null
        _isRefreshing.value = false
    }

    fun onForeground() {
        if (isForeground.getAndSet(true)) return
        foregroundGeneration.incrementAndGet()
        startFriendLocationRefresh()
    }

    fun findFriendLocation(userId: String, location: String): FriendLocation? =
        friendLocationsByUser.value[userId]?.takeIf { it.location == location }


    fun refreshFriendLocation() {
        startFriendLocationRefresh()
    }

    private fun startFriendLocationRefresh() {
        if (refreshJob?.isActive == true) return
        val sessionToken = SharedFlowCentre.currentSession.value?.token ?: return
        val generation = refreshGeneration.incrementAndGet()
        _isRefreshing.value = true
        refreshJob = modelScope.launch(Dispatchers.IO) {
            try {
                try {
                    friendService.refreshCurrentUserLocation()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    Result.failure<Unit>(error).onApiFailure("FriendLocation") {
                        SharedFlowCentre.toastText.emit(ToastText.Error(it))
                    }
                }
                if (!SharedFlowCentre.isCurrentSession(sessionToken)) return@launch
                friendService.refreshFriendList()
            } finally {
                if (refreshGeneration.value == generation) {
                    if (SharedFlowCentre.isCurrentSession(sessionToken)) syncFriendLocations()
                    _isRefreshing.value = false
                }
            }
        }
    }

    /**
     * 已发布给 Compose 的位置状态只能在主线程改写：分页刷新是在 IO 上下文里调用这里的，
     * 后台线程往 [FriendLocation.friends] 这类 SnapshotStateMap 插好友，会和主线程读
     * [FriendLocation.friendList] 的排序撞车——排序先读 size 再迭代，中途被插入就数组越界。
     */
    private suspend fun syncFriendLocations(fetchInstanceDetails: Boolean = true) =
        withContext(Dispatchers.Main) {
            updateMutex.withLock {
                runCatching {
                    val snapshot = friendService.friendState.value.values.toFriendLocationSnapshot()
                    publishedState.syncSimpleLocation(LocationType.Offline, snapshot.offline)
                    publishedState.syncSimpleLocation(LocationType.Web, snapshot.web)
                    val own = friendService.currentUserLocation.value?.let { presence ->
                        ownEffectiveLocation(presence)?.let { effectiveLocation ->
                            authService.currentUserState.value?.toFriendData(presence, effectiveLocation)
                        }
                    }
                    val privateFriends = if (own?.location == LocationType.Private.value) {
                        snapshot.private.filterNot { it.id == own.id } + own
                    } else {
                        snapshot.private
                    }
                    publishedState.syncSimpleLocation(LocationType.Private, privateFriends)
                    val instances = snapshot.instances.toMutableMap()
                    if (own != null) {
                        val group = instances[own.location]
                        val ownIsTraveling =
                            friendService.currentUserLocation.value
                                ?.location?.startsWith(LocationType.Traveling.value) == true
                        instances[own.location] = if (group == null) {
                            FriendLocationGroup(
                                friends = listOf(own),
                                travelingIds = if (ownIsTraveling) setOf(own.id) else emptySet(),
                            )
                        } else {
                            FriendLocationGroup(
                                friends = group.friends.filterNot { it.id == own.id } + own,
                                travelingIds = if (ownIsTraveling) {
                                    group.travelingIds + own.id
                                } else {
                                    group.travelingIds - own.id
                                },
                            )
                        }
                    }
                    syncInstanceLocations(instances, fetchInstanceDetails)
                    publishFriendLocationIndex()
                }.onApiFailure("FriendLocation") {
                    SharedFlowCentre.toastText.emit(ToastText.Error(it))
                }
            }
        }

    private fun clearFriendLocations() {
        publishedState.clear()
    }

    private fun publishFriendLocationIndex() {
        publishedState.publishIndex()
    }

    private fun syncInstanceLocations(
        groups: Map<String, FriendLocationGroup>,
        fetchInstanceDetails: Boolean,
    ) {
        if (groups.isEmpty()) {
            friendLocationMap.remove(LocationType.Instance)
            return
        }
        val locations = friendLocationMap.getOrPut(LocationType.Instance, ::mutableStateListOf)
        locations.removeAll { it.location !in groups }
        groups.forEach { (locationId, group) ->
            val location = locations.find { it.location == locationId } ?: FriendLocation(
                location = locationId,
                friends = mutableStateMapOf(),
            ).also(locations::add)
            val presenceChanged =
                location.friends.keys != group.friends.mapTo(mutableSetOf(), FriendData::id) ||
                    location.travelingIds.value != group.travelingIds
            syncFriends(location, group)
            if (fetchInstanceDetails) {
                fetchInstants(
                    location = locationId,
                    oldInstants = location.instants.value,
                    forceRefresh = presenceChanged && group.friends.isNotEmpty(),
                ) {
                    location.instants.value = it
                }
            }
        }
    }

    private inline fun fetchInstants(
        location: String,
        oldInstants: HomeInstanceVo,
        forceRefresh: Boolean = false,
        crossinline updateInstants: (HomeInstanceVo) -> Unit
    ) {
        // 已加载过实例信息则跳过网络请求
        if (!forceRefresh && oldInstants.worldId.isNotEmpty()) return
        if (!isForeground.value) return
        val generation = foregroundGeneration.value
        val job = synchronized(instanceJobsLock) {
            if (instanceJobs[location]?.isActive == true) return
            modelScope.launch(Dispatchers.IO, start = kotlinx.coroutines.CoroutineStart.LAZY) {
                try {
                    instanceFetchSemaphore.withPermit {
                        val instance = authService.reTryAuthCatching {
                            instancesApi.instanceByLocation(location)
                        }.getOrNull() ?: return@withPermit
                        if (!isCurrentForegroundGeneration(generation)) return@withPermit
                val instantsVo = HomeInstanceVo(instance)
                updateInstants(instantsVo)
                // owner不会变所以不用更新
                if (oldInstants.owner != null){
                    instantsVo.owner = oldInstants.owner
                    updateInstants(instantsVo)
                }else{
                    // 先刷新房间实例信息，提高用户体验，再更新房间实例的拥有者信息，不然会一直空白在加载
                    updateInstants(instantsVo)
                    fetchAndSetOwner(instance.ownerId, instantsVo)
                }
                    }
                } finally {
                    synchronized(instanceJobsLock) {
                        if (instanceJobs[location] == coroutineContext[Job]) {
                            instanceJobs.remove(location)
                        }
                    }
                }
            }.also { instanceJobs[location] = it }
        }
        job.start()
    }

    private fun isCurrentForegroundGeneration(generation: Long): Boolean =
        isForeground.value && foregroundGeneration.value == generation

    /**
     * 获取房间实例的拥有者名称
     *
     * @param instance 房间实例
     * @param instantsVo 房间实例的视图对象
     */
    private suspend fun fetchAndSetOwner(
        ownerId: String?,
        instantsVo: HomeInstanceVo,
    ) {
        val ownerId = ownerId ?: return
        val fetchOwner: suspend (String) -> HomeInstanceVo.Owner =
            when (BlueprintType.fromValue(ownerId)) {
                BlueprintType.User -> {
                    {
                        val user = usersApi.fetchUser(ownerId)
                        HomeInstanceVo.Owner(
                            id = user.id,
                            displayName = user.displayName,
                            type = BlueprintType.User
                        )
                    }
                }

                BlueprintType.Group -> {
                    {
                        val group = groupsApi.fetchGroup(ownerId)
                        HomeInstanceVo.Owner(
                            id = group.id,
                            displayName = group.name,
                            type = BlueprintType.Group
                        )

                }
            }
            else -> return
        }
        authService.reTryAuthCatching {
            fetchOwner(ownerId)
        }.onSuccess {
            instantsVo.owner = it
        }.onFailure {
            SharedFlowCentre.toastText.emit(ToastText.Error(it.message.toString()))
        }
    }

}

internal fun ownEffectiveLocation(presence: FriendPresence): String? = when {
    presence.location.startsWith("wrld_") -> presence.location
    presence.location.startsWith(LocationType.Traveling.value) &&
        presence.travelingToLocation.startsWith("wrld_") -> presence.travelingToLocation
    presence.location == LocationType.Private.value -> LocationType.Private.value
    else -> null
}

private fun syncFriends(location: FriendLocation, group: FriendLocationGroup) {
    val incoming = group.friends.associateBy(FriendData::id)
    location.friends.keys.filter { it !in incoming }.forEach(location.friends::remove)
    incoming.forEach { (friendId, friend) ->
        val state = location.friends[friendId]
        if (state == null) location.friends[friendId] = mutableStateOf(friend)
        else state.value = friend
    }
    location.travelingIds.value = group.travelingIds
}

private fun io.github.vrcmteam.vrcm.network.api.auth.data.CurrentUserData.toFriendData(
    presence: FriendPresence,
    effectiveLocation: String,
) = FriendData(
    bio = bio,
    bioLinks = bioLinks,
    currentAvatarImageUrl = currentAvatarImageUrl,
    currentAvatarTags = currentAvatarTags,
    currentAvatarThumbnailImageUrl = currentAvatarThumbnailImageUrl,
    developerType = developerType,
    displayName = displayName,
    friendKey = friendKey,
    id = id,
    imageUrl = profilePicOverride,
    isFriend = false,
    lastLogin = lastLogin,
    lastActivity = lastActivity,
    lastPlatform = lastPlatform,
    location = effectiveLocation,
    travelingToLocation = presence.travelingToLocation,
    profilePicOverride = profilePicOverride,
    status = status,
    statusDescription = statusDescription,
    tags = tags,
    userIcon = userIcon,
    pronouns = pronouns,
)
