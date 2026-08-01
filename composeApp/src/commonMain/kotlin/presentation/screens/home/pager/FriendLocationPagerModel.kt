package io.github.vrcmteam.vrcm.presentation.screens.home.pager

import androidx.compose.runtime.*
import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import io.github.vrcmteam.vrcm.core.shared.AccountSessionToken
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
import io.github.vrcmteam.vrcm.service.AccountBoundTask
import io.github.vrcmteam.vrcm.service.AccountGenerationToken
import io.github.vrcmteam.vrcm.service.AccountGenerationTracker
import io.github.vrcmteam.vrcm.service.AuthService
import io.github.vrcmteam.vrcm.service.FriendService
import io.github.vrcmteam.vrcm.service.FriendPresence
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal class FriendUpdateSessionGate(
    initialSessionToken: AccountSessionToken? = null,
) {
    private var sessionToken: AccountSessionToken? = initialSessionToken

    fun activate(token: AccountSessionToken) {
        sessionToken = token
    }

    fun clear() {
        sessionToken = null
    }

    fun accepts(token: AccountSessionToken): Boolean = sessionToken == token
}

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
) : ScreenModel {
    private val initialSession = SharedFlowCentre.currentSession.value
    private val publishedState = FriendLocationPublishedState()
    val friendLocationMap = publishedState.locationMap
    val friendLocationsByUser = publishedState.locationsByUser
    val currentUserId: String
        get() = authService.accountDto().userId

    private val presenceStore = FriendLocationPresenceStore()
    private var currentUserPresence: FriendPresence? = null
    private val friendUpdateSessionGate = FriendUpdateSessionGate(initialSession?.token)
    private val accountTracker = AccountGenerationTracker(initialSession?.account?.userId)
    private val updateMutex = Mutex()
    private val refreshMutex = Mutex()
    private val preloadTask = AccountBoundTask(
        scope = screenModelScope,
        isCurrent = accountTracker::isCurrent,
        runTask = { token ->
            doRefreshFriendLocationForAccount(
                removeNotIncluded = true,
                token = token,
            )
        },
    )
    private var hasCompletedInitialRefresh = false

    /**
     * 刷新状态,一次登录成功后只会自动刷新一次
     */
    var isRefreshing by mutableStateOf(true)
        private set

    init {
        accountTracker.currentToken()?.let(preloadTask::start)
        screenModelScope.launch {
            friendService.friendState.collect { friends ->
                if (!hasCompletedInitialRefresh) return@collect
                updateMutex.withLock { presenceStore.replaceFriends(friends.values) }
                syncFriendLocations()
            }
        }
        screenModelScope.launch {
            friendService.currentUserLocation.collect { presence ->
                var shouldSync = false
                updateMutex.withLock {
                    currentUserPresence = presence
                    shouldSync = hasCompletedInitialRefresh
                }
                if (shouldSync) syncFriendLocations()
            }
        }
        screenModelScope.launch {
            friendService.friendUpdateFlow.collect { update ->
                val refreshRequired = updateMutex.withLock {
                    if (!acceptsFriendUpdate(update.sessionToken)) return@withLock null
                    presenceStore.apply(update.event)
                } ?: return@collect
                if (!SharedFlowCentre.isCurrentSession(update.sessionToken)) return@collect
                if (refreshRequired) {
                    doRefreshFriendLocation(removeNotIncluded = true)
                } else {
                    syncFriendLocations()
                }
            }
        }
        screenModelScope.launch {
            SharedFlowCentre.currentSession.collect { session ->
                if (session == null) {
                    accountTracker.clear()
                    updateMutex.withLock {
                        friendUpdateSessionGate.clear()
                        clearFriendLocations()
                        hasCompletedInitialRefresh = false
                    }
                    isRefreshing = true
                    preloadTask.cancelAndJoin()
                } else {
                    val activation = accountTracker.activate(session.account.userId)
                    updateMutex.withLock {
                        friendUpdateSessionGate.activate(session.token)
                        if (activation.changed) {
                            clearFriendLocations()
                            hasCompletedInitialRefresh = false
                        }
                    }
                    if (!activation.changed) return@collect
                    isRefreshing = true
                    preloadTask.cancelAndJoin()
                    preloadTask.start(activation.token)
                }
            }
        }
    }

    private fun acceptsFriendUpdate(sessionToken: AccountSessionToken): Boolean =
        friendUpdateSessionGate.accepts(sessionToken) &&
            SharedFlowCentre.isCurrentSession(sessionToken)

    fun preloadFriendLocations() {
        if (hasCompletedInitialRefresh) return
        val token = accountTracker.currentToken() ?: return
        preloadTask.start(token)
    }

    fun findFriendLocation(userId: String, location: String): FriendLocation? =
        friendLocationsByUser.value[userId]?.takeIf { it.location == location }


    suspend fun refreshFriendLocation() {
        val token = accountTracker.currentToken() ?: return
        // 只有在clear时设置true,用来触发刷新状态动画
        // 不然切换一个Page就触发动画
        isRefreshing = true
        updateMutex.withLock {
            if (!accountTracker.isCurrent(token)) return
            clearFriendLocations()
        }
        doRefreshFriendLocationForAccount(
            removeNotIncluded = false,
            token = token,
        )
        // 刷新后更新刷新状态, 防止页面重新加载时自动刷新
    }

    /**
     * 刷新好友位置
     * 未clear()的刷新会因为ws接口失效导致好友下线时未同步产生数据残留, 请让removeNotIncluded = true
     * @param removeNotIncluded 是否移除不在这一次刷新好友在线列表中的好友
     */
    suspend fun doRefreshFriendLocation(removeNotIncluded: Boolean = false) {
        val token = accountTracker.currentToken() ?: return
        doRefreshFriendLocationForAccount(removeNotIncluded, token)
    }

    private suspend fun doRefreshFriendLocationForAccount(
        removeNotIncluded: Boolean,
        token: AccountGenerationToken,
    ) = refreshMutex.withLock refresh@{
        if (!accountTracker.isCurrent(token)) return@refresh
        val includedIds = mutableSetOf<String>()
        updateMutex.withLock { presenceStore.beginRefresh() }
        try {
            val currentUser = authService.currentUser(isRefresh = true)
            updateMutex.withLock {
                if (accountTracker.isCurrent(token)) {
                    presenceStore.setActiveFriends(currentUser.activeFriends)
                }
            }
        } catch (e: CancellationException) {
            updateMutex.withLock {
                if (accountTracker.isCurrent(token)) presenceStore.cancelRefresh()
            }
            throw e
        } catch (_: Exception) {
            // Presence events keep this cache current if the account refresh is temporarily unavailable.
        }
        if (!accountTracker.isCurrent(token)) return@refresh
        var completed = false
        try {
            completed = withContext(Dispatchers.IO) {
                friendService.refreshFriendList(offline = false) page@{ friends ->
                    if (!accountTracker.isCurrent(token)) return@page
                    updateMutex.withLock {
                        if (accountTracker.isCurrent(token)) {
                            presenceStore.addPage(friends)
                            includedIds.addAll(friends.map(FriendData::id))
                        }
                    }
                    syncFriendLocations(token)
                }
            }
            updateMutex.withLock {
                if (accountTracker.isCurrent(token)) {
                    presenceStore.finishRefresh(includedIds, reconcile = removeNotIncluded && completed)
                }
            }
            if (completed && accountTracker.isCurrent(token)) hasCompletedInitialRefresh = true
        } finally {
            if (accountTracker.isCurrent(token)) {
                if (!completed) updateMutex.withLock { presenceStore.cancelRefresh() }
                syncFriendLocations(token)
                isRefreshing = false
            }
        }
    }

    private suspend fun syncFriendLocations(token: AccountGenerationToken? = null) = updateMutex.withLock {
        if (token != null && !accountTracker.isCurrent(token)) return@withLock
        runCatching {
            val snapshot = presenceStore.snapshot()
            publishedState.syncSimpleLocation(LocationType.Offline, snapshot.offline)
            publishedState.syncSimpleLocation(LocationType.Web, snapshot.web)
            val own = currentUserPresence?.let { presence ->
                ownEffectiveLocation(presence)?.let { effectiveLocation ->
                    authService.currentUser().toFriendData(presence, effectiveLocation)
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
                val ownIsTraveling = currentUserPresence?.location?.startsWith(LocationType.Traveling.value) == true
                instances[own.location] = if (group == null) {
                    FriendLocationGroup(
                        friends = listOf(own),
                        travelingIds = if (ownIsTraveling) setOf(own.id) else emptySet(),
                    )
                } else {
                    FriendLocationGroup(
                        friends = group.friends.filterNot { it.id == own.id } + own,
                        travelingIds = if (ownIsTraveling) group.travelingIds + own.id else group.travelingIds - own.id,
                    )
                }
            }
            syncInstanceLocations(instances)
            publishFriendLocationIndex()
        }.onApiFailure("FriendLocation") {
            SharedFlowCentre.toastText.emit(ToastText.Error(it))
        }
    }

    private fun clearFriendLocations() {
        presenceStore.clear()
        publishedState.clear()
    }

    private fun publishFriendLocationIndex() {
        publishedState.publishIndex()
    }

    private fun syncInstanceLocations(groups: Map<String, FriendLocationGroup>) {
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
            fetchInstants(
                location = locationId,
                oldInstants = location.instants.value,
                forceRefresh = presenceChanged && group.friends.isNotEmpty(),
            ) {
                location.instants.value = it
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
        screenModelScope.launch(Dispatchers.IO) {
            authService.reTryAuthCatching {
                instancesApi.instanceByLocation(location)
            }.onFailure {
                SharedFlowCentre.toastText.emit(ToastText.Error(it.message.toString()))
            }.onSuccess { instance ->
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
        }
    }

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
