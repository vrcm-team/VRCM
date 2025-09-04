package io.github.vrcmteam.vrcm.presentation.screens.user

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import io.github.vrcmteam.vrcm.core.extensions.pretty
import io.github.vrcmteam.vrcm.core.shared.SharedFlowCentre
import io.github.vrcmteam.vrcm.network.api.attributes.BlueprintType
import io.github.vrcmteam.vrcm.network.api.attributes.LocationType
import io.github.vrcmteam.vrcm.network.api.attributes.NotificationType
import io.github.vrcmteam.vrcm.network.api.groups.GroupsApi
import io.github.vrcmteam.vrcm.network.api.instances.InstancesApi
import io.github.vrcmteam.vrcm.network.api.notification.NotificationApi
import io.github.vrcmteam.vrcm.network.api.users.UsersApi
import io.github.vrcmteam.vrcm.network.api.users.data.UserData
import io.github.vrcmteam.vrcm.presentation.compoments.ToastText
import io.github.vrcmteam.vrcm.presentation.screens.home.data.FriendLocation
import io.github.vrcmteam.vrcm.presentation.screens.home.data.HomeInstanceVo
import io.github.vrcmteam.vrcm.presentation.screens.user.data.UserProfileVo
import io.github.vrcmteam.vrcm.service.AuthService
import io.github.vrcmteam.vrcm.service.FriendService
import io.ktor.client.call.*
import io.ktor.client.statement.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import org.koin.core.logger.Logger

class UserProfileScreenModel(
    userProfileVO: UserProfileVo,
    private val authService: AuthService,
    private val usersApi: UsersApi,
    private val groupsApi: GroupsApi,
    private val friendService: FriendService,
    private val notificationApi: NotificationApi,
    private val logger: Logger,
    private val instancesApi: InstancesApi,
) : ScreenModel {

    private val _userState = mutableStateOf(userProfileVO)
    val userState by _userState

    private val _friendLocation = mutableStateOf<FriendLocation?>(null)
    val friendLocation by _friendLocation

    private val _userJson = mutableStateOf("")
    val userJson by _userJson

    fun initUserState(userProfileVO: UserProfileVo) {
        if (userProfileVO.id != _userState.value.id){
            _userState.value = userProfileVO
        }
    }

    fun refreshUser(userId: String) =
        screenModelScope.launch(Dispatchers.IO) {
            authService.reTryAuthCatching {
                usersApi.fetchUserResponse(userId)
            }.onFailure {
                handleError(it)
            }.onSuccess { response ->
                // 防止body序列化异常
                runCatching { UserProfileVo(response.body<UserData>()) }
                    .onSuccess {
                        _userState.value = it
                         computeFriendLocation(it.location)
                    }
                    .onFailure { handleError(it) }
                _userJson.value = response.bodyAsText().pretty()
            }
        }


    suspend fun sendFriendRequest(userId: String, message: String): Boolean =
        friendAction(message) {
            friendService.sendFriendRequest(userId)
        }

    suspend fun deleteFriendRequest(userId: String, message: String): Boolean = friendAction(message) {
        friendService.deleteFriendRequest(userId)
    }

    suspend fun unfriend(userId: String, message: String): Boolean = friendAction(message) {
        friendService.unfriend(userId)
    }

    suspend fun acceptFriendRequest(userId: String, message: String) = friendAction(message) {
        // 看看要不要加载大于 100 条的通知
        // 先看没有hidden的, 如果没有再看hidden的 TODO:是不是要单独抽成一个独立方法
        return@friendAction authService.reTryAuthCatching {
            (notificationApi.fetchNotificationsV2(
                type = NotificationType.FriendRequest.value,
            ).firstOrNull { it.senderUserId == userId }
                ?: notificationApi.fetchNotificationsV2(
                    type = NotificationType.FriendRequest.value,
                    hidden = true
                ).firstOrNull { it.senderUserId == userId })
                ?.let {
                    notificationApi.acceptFriendRequest(it.id).isSuccess
                } ?: error("Not found notification")
        }
    }

    private suspend fun friendAction(message: String, action: suspend () -> Result<*>): Boolean =
        screenModelScope.async(Dispatchers.IO) {
            action()
                .onFailure {
                    handleError(it)
                }.onSuccess {
                    SharedFlowCentre.toastText.emit(ToastText.Success(message))
                    runCatching { _userState.value.id.also { refreshUser(it) } }
                        .onFailure { handleError(it) }
                }.isSuccess
        }.await()

    private suspend fun handleError(it: Throwable) {
        logger.error(it.message.toString())
        SharedFlowCentre.toastText.emit(ToastText.Error(it.message.toString()))
    }

    fun computeFriendLocation(location: String) {
        val type = LocationType.fromValue(location)
        if (location.isEmpty() || type != LocationType.Instance || _friendLocation.value != null) {
            return
        }
        // Build FriendLocation with friends in the same instance
        val friendsInSameRoom = friendService.friendMap.values
            .filter { it.location == location }
            .associate { it.id to mutableStateOf(it) }
            .toMutableMap()
        val friendLocation = FriendLocation(
            location = location,
            friends = friendsInSameRoom
        )
        _friendLocation.value = friendLocation
        // Fetch instance details
        screenModelScope.launch(Dispatchers.IO) {
            authService.reTryAuthCatching {
                instancesApi.instanceByLocation(location)
            }.onSuccess { instance ->
                val homeInstanceVo = HomeInstanceVo(instance)
                friendLocation.instants.value = homeInstanceVo
                fetchAndSetOwner(instance.ownerId, homeInstanceVo)
            }.onFailure {
                handleError(it)
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
