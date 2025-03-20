package io.github.vrcmteam.vrcm.presentation.screens.world

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.toMutableStateList
import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import io.github.vrcmteam.vrcm.network.api.attributes.BlueprintType
import io.github.vrcmteam.vrcm.network.api.groups.GroupsApi
import io.github.vrcmteam.vrcm.network.api.users.UsersApi
import io.github.vrcmteam.vrcm.network.api.worlds.WorldsApi
import io.github.vrcmteam.vrcm.presentation.screens.world.data.InstanceVo
import io.github.vrcmteam.vrcm.presentation.screens.world.data.WorldProfileVo
import io.github.vrcmteam.vrcm.service.AuthService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    val worldProfileState = mutableStateOf<WorldProfileVo?>(null)

    // 加载状态
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    /**
     * 刷新世界数据
     */
    fun refreshWorldData() {
        val currentProfile = worldProfileState.value ?: return
        if (_isLoading.value || currentProfile.worldId.isBlank()) return

        _isLoading.value = true
        screenModelScope.launch(Dispatchers.IO) {
            authService.reTryAuthCatching {
                worldsApi.getWorldById(currentProfile.worldId)
            }.onSuccess { worldData ->
                // 更新世界基本信息
                worldProfileState.value =  WorldProfileVo(worldData, currentProfile.instances)

                // 获取实例ID列表
                val instanceIds = worldData.instances?.mapNotNull { it.firstOrNull() }
                    ?.filter { it.isNotBlank() } ?: emptyList()

                // 如果有实例ID，则获取实例信息
                if (instanceIds.isNotEmpty()) {
                    loadInstanceData(instanceIds)
                }
            }.onFailure {
                println("获取世界数据失败: ${it.message}")
            }
            
            _isLoading.value = false
        }
    }

    /**
     * 加载实例数据
     */
    private suspend fun loadInstanceData(instanceIds: List<String>) {
        val currentProfile = worldProfileState.value ?: return
        authService.reTryAuthCatching {
            // 获取所有实例数据
            val instancesData = instanceIds.map {
                worldsApi.getWorldInstanceById(currentProfile.worldId, it)
            }.map {
                // 如果实例是活跃的，则获取实例的拥有者名称
                val owner =  fetchAndSetOwner(it.ownerId)
                InstanceVo(it,owner)
            }

            // 合并现有和新获取的实例s
            val instanceVoList = currentProfile.instances.associateBy { it.instanceId } +
                    instancesData.associateBy { it.instanceId }

            // 在主线程上一次性更新状态
            withContext(Dispatchers.Main) {
                worldProfileState.value =  currentProfile.copy(instances = instanceVoList.values.toMutableStateList())
            }
        }
    }
    /**
     * 获取房间实例的拥有者名称
     *
     */
    private suspend fun fetchAndSetOwner(
        ownerId: String?,
    ):InstanceVo.Owner? {
        if (ownerId == null) return null
       return when (BlueprintType.fromValue(ownerId)) {
                BlueprintType.User -> {

                        val user = usersApi.fetchUser(ownerId)
                        InstanceVo.Owner(
                            id = user.id,
                            displayName = user.displayName,
                            type = BlueprintType.User
                        )

                }

                BlueprintType.Group -> {

                        val group = groupsApi.fetchGroup(ownerId)
                        InstanceVo.Owner(
                            id = group.id,
                            displayName = group.name,
                            type = BlueprintType.Group
                        )


                }
                else -> { null }
            }
    }
} 