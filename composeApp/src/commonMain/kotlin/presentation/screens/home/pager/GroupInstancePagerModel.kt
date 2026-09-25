package io.github.vrcmteam.vrcm.presentation.screens.home.pager

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.vrcmteam.vrcm.core.shared.AccountSessionToken
import io.github.vrcmteam.vrcm.core.shared.SharedFlowCentre
import io.github.vrcmteam.vrcm.network.api.attributes.BlueprintType
import io.github.vrcmteam.vrcm.network.api.groups.GroupsApi
import io.github.vrcmteam.vrcm.network.api.instances.data.InstanceData
import io.github.vrcmteam.vrcm.network.api.users.UsersApi
import io.github.vrcmteam.vrcm.presentation.screens.home.data.HomeInstanceVo
import io.github.vrcmteam.vrcm.service.AuthService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** 位置页显示哪一种房间：好友所在的房间，或自己加入的群组开着的房间。 */
enum class HomeLocationSource {
    Friends,
    Groups,
}

/** 一个群组房间：[instants] 是卡片要的实例信息，[groupName] 用来分组显示。 */
data class GroupInstanceVo(
    val location: String,
    val groupId: String,
    val groupName: String,
    val instants: HomeInstanceVo,
)

data class GroupInstancesState(
    val instances: List<GroupInstanceVo> = emptyList(),
    val isLoading: Boolean = false,
    val hasLoaded: Boolean = false,
    val error: String? = null,
)

/**
 * 自己加入的群组当前开着的房间。
 *
 * 实例接口只给 `ownerId`（群组 ID），群组名要另外查一次自己的群组列表来对照。
 */
class GroupInstancePagerModel(
    private val groupsApi: GroupsApi,
    private val usersApi: UsersApi,
    private val authService: AuthService,
) : ViewModel() {
    private val _state = MutableStateFlow(GroupInstancesState())
    val state: StateFlow<GroupInstancesState> = _state.asStateFlow()
    private var refreshJob: Job? = null
    private var activeSessionToken: AccountSessionToken? = SharedFlowCentre.currentSession.value?.token
    private var requestGeneration = 0L

    init {
        viewModelScope.launch {
            SharedFlowCentre.currentSession.collect { session ->
                val nextToken = session?.token
                if (activeSessionToken == nextToken) return@collect

                val shouldReload = _state.value.hasLoaded || refreshJob?.isActive == true
                val userChanged = activeSessionToken?.userId != nextToken?.userId
                requestGeneration++
                activeSessionToken = nextToken
                refreshJob?.cancel()
                refreshJob = null
                _state.value = if (userChanged) {
                    GroupInstancesState()
                } else {
                    _state.value.copy(isLoading = false, error = null)
                }
                if (nextToken != null && shouldReload) refresh()
            }
        }
    }

    fun loadIfNeeded() {
        if (!_state.value.hasLoaded && refreshJob?.isActive != true) refresh()
    }

    fun refresh() {
        if (refreshJob?.isActive == true) return
        val sessionToken = activeSessionToken ?: SharedFlowCentre.currentSession.value?.token ?: return
        val generation = ++requestGeneration
        refreshJob = viewModelScope.launch(Dispatchers.IO) {
            if (!accepts(sessionToken, generation)) return@launch
            _state.value = _state.value.copy(isLoading = true, error = null)
            val result = authService.reTryAuthCatching {
                val instances = groupsApi.getUserGroupInstances(sessionToken.userId).instances
                instances.toGroupInstanceVoList(fetchGroupNames(sessionToken.userId, instances))
            }
            if (!accepts(sessionToken, generation)) return@launch
            result.onSuccess { instances ->
                _state.value = _state.value.copy(
                    instances = instances,
                    isLoading = false,
                    hasLoaded = true,
                    error = null,
                )
            }.onFailure { error ->
                if (error is CancellationException) throw error
                _state.value = _state.value.copy(
                    isLoading = false,
                    hasLoaded = true,
                    error = error.message.orEmpty(),
                )
            }
        }
    }

    /**
     * 群组名只是房间的标题，查不到就让房间以群组 ID 归类照常显示，
     * 不要因为这一次失败把整页房间都换成错误页。
     */
    private suspend fun fetchGroupNames(
        userId: String,
        instances: List<InstanceData>,
    ): Map<String, String> {
        if (instances.isEmpty()) return emptyMap()
        return try {
            usersApi.getUserGroups(userId)
                .filter { it.groupId.isNotBlank() && it.name.isNotBlank() }
                .associate { it.groupId to it.name }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            emptyMap()
        }
    }

    private fun accepts(token: AccountSessionToken, generation: Long): Boolean =
        requestGeneration == generation && activeSessionToken == token
}

private fun List<InstanceData>.toGroupInstanceVoList(
    groupNames: Map<String, String>,
): List<GroupInstanceVo> {
    val groupId: (InstanceData) -> String = { it.ownerId.orEmpty() }
    val groupName: (InstanceData) -> String = { groupNames[groupId(it)] ?: groupId(it) }
    return asSequence()
        .filter { it.location.isNotBlank() && groupId(it).isNotBlank() }
        .distinctBy { it.location }
        .sortedWith(
            compareBy<InstanceData> { groupName(it).lowercase() }
                .thenByDescending { it.nUsers }
                .thenBy { it.name },
        )
        .map { instance ->
            GroupInstanceVo(
                location = instance.location,
                groupId = groupId(instance),
                groupName = groupName(instance),
                instants = HomeInstanceVo(instance).apply {
                    owner = HomeInstanceVo.Owner(
                        id = groupId(instance),
                        displayName = groupName(instance),
                        type = BlueprintType.Group,
                    )
                },
            )
        }
        .toList()
}
