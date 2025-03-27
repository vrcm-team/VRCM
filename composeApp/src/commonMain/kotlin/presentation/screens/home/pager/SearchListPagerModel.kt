package io.github.vrcmteam.vrcm.presentation.screens.home.pager

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import io.github.vrcmteam.vrcm.core.shared.SharedFlowCentre
import io.github.vrcmteam.vrcm.network.api.users.UsersApi
import io.github.vrcmteam.vrcm.network.api.users.data.SearchUserData
import io.github.vrcmteam.vrcm.network.api.worlds.WorldsApi
import io.github.vrcmteam.vrcm.network.api.worlds.data.WorldData
import io.github.vrcmteam.vrcm.presentation.compoments.SortOption
import io.github.vrcmteam.vrcm.presentation.extensions.onApiFailure
import io.github.vrcmteam.vrcm.service.AuthService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.koin.core.logger.Logger

/**
 * 搜索页面的ViewModel
 */
class SearchListPagerModel(
    private val usersApi: UsersApi,
    private val worldsApi: WorldsApi,
    private val authService: AuthService,
    private val logger: Logger
) : ScreenModel {

    // 用户搜索列表
    private val _userSearchList =  MutableStateFlow(emptyList<SearchUserData>())
    var userSearchList = _userSearchList.asStateFlow()

    // 世界搜索列表
    private val _worldSearchList =  MutableStateFlow(emptyList<WorldData>())
    var worldSearchList = _worldSearchList.asStateFlow()

    // 当前搜索类型：0表示用户，1表示世界
    private val _searchType = MutableStateFlow(0)
    val searchType  = _searchType.asStateFlow()
        
    // 世界搜索选项
    private val _worldSearchOptions = MutableStateFlow(WorldSearchOptions())
    val worldSearchOptions: StateFlow<WorldSearchOptions> = _worldSearchOptions.asStateFlow()

    private var preSearchText: String = ""

    // 搜索文本 - 两个页面共享
    private val _searchText = MutableStateFlow("")
    val searchText: StateFlow<String> = _searchText.asStateFlow()

    init {
        // 监听登录状态,用于重新登录后更新刷新状态
        screenModelScope.launch {
            SharedFlowCentre.authed.collect {
                _userSearchList.value = emptyList()
                _worldSearchList.value = emptyList()
            }
        }
    }

    /**
     * 设置搜索类型
     * @param type 0: 用户搜索, 1: 世界搜索
     */
    fun setSearchType(type: Int) {
        if (type in 0..1 && type != searchType.value) {
            _searchType.value = type
        }
    }
    
    /**
     * 更新世界搜索选项
     */
    fun updateWorldSearchOptions(options: WorldSearchOptions) {
        _worldSearchOptions.value = options
        // 如果已经有搜索文本，则刷新搜索
        if (preSearchText.isNotEmpty() && searchType.value == 1) {
            screenModelScope.launch(Dispatchers.IO) {
                searchWorlds(preSearchText)
            }
        }
    }

    /**
     * 刷新搜索列表
     * @param name 搜索文本
     * @return 是否成功获取新的搜索结果
     */
    suspend fun refreshSearchList(name: String) = this.screenModelScope.async(Dispatchers.IO) {
        if (name != preSearchText && name.isNotEmpty()) {
            return@async when (searchType.value) {
                0 -> searchUsers(name)
                1 -> searchWorlds(name)
                else -> false
            }.also {
                preSearchText = name
            }
        } else {
            preSearchText = name
            return@async false
        }
    }.await()

    /**
     * 搜索用户
     * @param name 用户名
     * @return 是否搜索成功
     */
    private suspend fun searchUsers(name: String): Boolean {
        return authService.reTryAuthCatching {
            usersApi.searchUser(name)
        }.onSuccess {
            println(it)
            _userSearchList.value = it
        }.onApiFailure("UserSearch") {
            logger.error(it)
        }.isSuccess
    }

    /**
     * 搜索世界
     * @param name 世界名称
     * @return 是否搜索成功
     */
    private suspend fun searchWorlds(name: String): Boolean {
        val options = _worldSearchOptions.value
        return authService.reTryAuthCatching {
            worldsApi.searchWorld(
                search = name,
                featured = options.featured,
                sort = options.sortOption.value,
                user = options.user,
                userId = options.userId,
                n = options.resultsCount,
                order = options.order,
                offset = options.offset,
                releaseStatus = options.releaseStatus,
                tag = options.tag,
                notag = options.notag
            )
        }.onSuccess {
            _worldSearchList.value = it
        }.onApiFailure("WorldSearch") {
            logger.error(it)
        }.isSuccess
    }
}

/**
 * 世界搜索选项数据类
 */
data class WorldSearchOptions(
    val featured: Boolean? = null,
    val sortOption: SortOption = SortOption.Popularity,
    val user: String? = null,
    val userId: String? = null,
    val resultsCount: Int = 50,
    val order: String = "descending",
    val offset: Int = 0,
    val releaseStatus: String? = null,
    val tag: String? = null,
    val notag: String? = null
)