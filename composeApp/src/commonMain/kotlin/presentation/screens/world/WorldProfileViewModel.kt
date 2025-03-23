package io.github.vrcmteam.vrcm.presentation.screens.world

import androidx.compose.runtime.toMutableStateList
import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import io.github.vrcmteam.vrcm.core.extensions.removeFirst
import io.github.vrcmteam.vrcm.core.shared.SharedFlowCentre
import io.github.vrcmteam.vrcm.network.api.attributes.BlueprintType
import io.github.vrcmteam.vrcm.network.api.groups.GroupsApi
import io.github.vrcmteam.vrcm.network.api.users.UsersApi
import io.github.vrcmteam.vrcm.network.api.worlds.WorldsApi
import io.github.vrcmteam.vrcm.presentation.compoments.ToastText
import io.github.vrcmteam.vrcm.presentation.screens.world.data.InstanceVo
import io.github.vrcmteam.vrcm.presentation.screens.world.data.InstanceVo.Owner
import io.github.vrcmteam.vrcm.presentation.screens.world.data.WorldProfileVo
import io.github.vrcmteam.vrcm.service.AuthService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * 世界档案页面的ViewModel，负责处理世界数据的加载和刷新
 */
class WorldProfileScreenModel(
    private val worldsApi: WorldsApi,
    private val usersApi: UsersApi,
    private val groupsApi: GroupsApi,
    private val authService: AuthService,
): ScreenModel {
    // 世界数据状态
    private val _worldProfileState = MutableStateFlow<WorldProfileVo?>(null)
    val worldProfileState: StateFlow<WorldProfileVo?> =  _worldProfileState.asStateFlow()

    // 加载状态
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    /**
     * 刷新世界数据
     */
    fun refreshWorldData(worldProfileVO: WorldProfileVo) {
        _worldProfileState.value = worldProfileVO
        val worldId = _worldProfileState.value?.worldId ?: return
        if (_isLoading.value || worldId.isBlank()) return
        _isLoading.value = true
        screenModelScope.launch(Dispatchers.IO) {
            authService.reTryAuthCatching {
                worldsApi.getWorldById(worldId)
            }.onSuccess { worldData ->
                // 更新世界基本信息

                _worldProfileState.value = WorldProfileVo(worldData, _worldProfileState.value?.instances ?: mutableListOf())

                // 获取实例ID列表
                val instanceIds = _worldProfileState.value?.instances?.map { it.instanceId }?.toMutableSet() ?: mutableSetOf()
                instanceIds.addAll(worldData.instances?.mapNotNull { it.firstOrNull() }?.filter { it.isNotBlank() } ?: emptySet())
                // 如果有实例ID，则获取实例信息
                if (instanceIds.isNotEmpty()) {
                    loadInstanceData(instanceIds)
                }
            }.onFailure {
                SharedFlowCentre.toastText.emit(ToastText.Error(it.message ?: "Failed to load world data"))
            }
            
            _isLoading.value = false
        }
    }

    /**
     * 加载实例数据
     */
    private suspend fun loadInstanceData(instanceIds: Collection<String>) {
        val currentProfile = _worldProfileState.value ?: return
        val instanceVos = currentProfile.instances.toMutableStateList()
        _worldProfileState.value =  _worldProfileState.value?.copy(instances = instanceVos)
        authService.reTryAuthCatching {
            // 获取所有实例数据
            instanceIds.asFlow().map {
                worldsApi.getWorldInstanceById(currentProfile.worldId, it)
            }.map { instanceData ->
               val owner: MutableStateFlow<Owner?> = MutableStateFlow(null)
               val instanceVo = InstanceVo(instanceData, owner)
               instanceVos.removeFirst { it.id == instanceData.id }
               instanceVos.add(instanceVo)
               instanceVo.ownerId to owner
           }.collect { (ownerId, owner) ->
                // 如果实例是活跃的，则获取实例的拥有者名称
                owner.update {
                    fetchAndSetOwner(ownerId)
                }
            }

        }
    }
    /**
     * 获取房间实例的拥有者名称
     *
     */
    private suspend fun fetchAndSetOwner(
        ownerId: String?,
    ):Owner? {
        if (ownerId == null) return null
       return when (BlueprintType.fromValue(ownerId)) {
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
                else -> { null }
            }
    }

} 