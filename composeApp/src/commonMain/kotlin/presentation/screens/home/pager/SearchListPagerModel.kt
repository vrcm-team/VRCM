package io.github.vrcmteam.vrcm.presentation.screens.home.pager

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import io.github.vrcmteam.vrcm.core.shared.SharedFlowCentre
import io.github.vrcmteam.vrcm.network.api.groups.GroupsApi
import io.github.vrcmteam.vrcm.network.api.groups.data.LimitedGroup
import io.github.vrcmteam.vrcm.network.api.users.UsersApi
import io.github.vrcmteam.vrcm.network.api.users.data.SearchUserData
import io.github.vrcmteam.vrcm.network.api.worlds.WorldsApi
import io.github.vrcmteam.vrcm.network.api.worlds.data.WorldData
import io.github.vrcmteam.vrcm.presentation.extensions.onApiFailure
import io.github.vrcmteam.vrcm.presentation.screens.home.data.WorldSearchOptions
import io.github.vrcmteam.vrcm.service.AuthService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.core.logger.Logger

/**
 * 搜索页面的ViewModel
 */
class SearchListPagerModel(
    private val usersApi: UsersApi,
    private val worldsApi: WorldsApi,
    private val groupsApi: GroupsApi,
    private val authService: AuthService,
    private val logger: Logger
) : ScreenModel {

    // 用户搜索列表
    private val _userSearchList =  MutableStateFlow(emptyList<SearchUserData>())
    var userSearchList = _userSearchList.asStateFlow()

    // 世界搜索列表
    private val _worldSearchList =  MutableStateFlow(emptyList<WorldData>())
    var worldSearchList = _worldSearchList.asStateFlow()

    // 群组搜索列表
    private val _groupSearchList = MutableStateFlow(emptyList<LimitedGroup>())
    val groupSearchList: StateFlow<List<LimitedGroup>> = _groupSearchList.asStateFlow()

    private val _groupHasMore = MutableStateFlow(false)
    val groupHasMore: StateFlow<Boolean> = _groupHasMore.asStateFlow()

    private val groupLoadingGate = GroupLoadingGate()
    val isLoadingGroups: StateFlow<Boolean> = groupLoadingGate.owner
        .map { it != null }
        .stateIn(screenModelScope, SharingStarted.Eagerly, false)

    private val _groupLoadMoreFailed = MutableStateFlow(false)
    val groupLoadMoreFailed: StateFlow<Boolean> = _groupLoadMoreFailed.asStateFlow()

    private val groupPagingState = MutableStateFlow(GroupPagingState())

    // 当前搜索类型：0表示用户，1表示世界，3表示群组
    private val _searchType = MutableStateFlow(0)
    val searchType = _searchType.asStateFlow()

    // 世界搜索选项
    private val _worldSearchOptions = MutableStateFlow(WorldSearchOptions())
    val worldSearchOptions: StateFlow<WorldSearchOptions> = _worldSearchOptions.asStateFlow()

    private val requestGeneration = MutableStateFlow(0L)

    // 搜索文本 - 两个页面共享
    private val _searchText = MutableStateFlow("")
    val searchText: StateFlow<String> = _searchText.asStateFlow()

    private var authenticatedUserId: String? = authService.accountDtoOrNull()?.userId

    init {
        // 监听登录状态,用于重新登录后更新刷新状态
        screenModelScope.launch {
            SharedFlowCentre.authed.collect { account ->
                val accountChanged = authenticatedUserId != account.userId
                authenticatedUserId = account.userId
                if (accountChanged) {
                    _userSearchList.value = emptyList()
                    _worldSearchList.value = emptyList()
                    _groupSearchList.value = emptyList()
                    resetGroupPaging()
                }
            }
        }
    }

    /**
     * 设置搜索类型
     * @param type 0: 用户搜索, 1: 世界搜索, 3: 群组搜索
     */
    fun setSearchType(type: Int) {
        if (!(type in 0..3 && type != searchType.value)) return
        advanceRequestGeneration()
        _searchType.value = type
        invalidateGroupPaging()
    }

    fun setSearchText(text: String) {
        if (_searchText.value == text) return
        advanceRequestGeneration()
        _searchText.value = text
        invalidateGroupPaging()
        if (text.isEmpty()) {
            _groupSearchList.value = emptyList()
        }
    }

    /**
     * 更新世界搜索选项
     */
    suspend fun updateWorldSearchOptions(options: WorldSearchOptions) {
        if (_worldSearchOptions.value == options) return
        advanceRequestGeneration()
        _worldSearchOptions.value = options
        // 如果已经有搜索文本，则刷新搜索
        if (searchText.value.isNotEmpty() && searchType.value == 1) {
            refreshSearchList()
        }
    }

    /**
     * 刷新搜索列表
     * @param name 搜索文本
     * @return 是否成功获取新的搜索结果
     */
    suspend fun refreshSearchList(): Boolean {
        val requestKey = currentRequestKey()
        val generation = requestGeneration.value

        return withContext(Dispatchers.IO) {
            when (requestKey.searchType) {
                0 -> searchUsers(requestKey.searchText, requestKey, generation)
                1 -> searchWorlds(requestKey, generation)
                3 -> if (requestKey.searchText.isNotEmpty()) {
                    searchFirstGroupPage(requestKey, generation)
                } else {
                    false
                }
                else -> false
            }
        }
    }

    fun loadMoreGroups(): Job? = startGroupPage(retryFailed = false)

    fun retryLoadMoreGroups(): Job? = startGroupPage(retryFailed = true)

    private fun startGroupPage(retryFailed: Boolean): Job? {
        val requestKey = currentRequestKey()
        val generation = requestGeneration.value
        val pagingState = groupPagingState.value
        val pageFailed = pagingState.failedOffset == pagingState.nextOffset
        if (
            requestKey.searchType != GROUP_SEARCH_TYPE ||
            requestKey.searchText.isEmpty() ||
            !groupHasMore.value ||
            pagingState.query != requestKey.searchText ||
            retryFailed != pageFailed
        ) {
            return null
        }
        val loadToken = GroupLoadToken(
            generation = generation,
            offset = pagingState.nextOffset,
            append = true,
        )
        if (!groupLoadingGate.tryAcquire(loadToken)) return null
        if (
            !isCurrentRequest(requestKey, generation) ||
            groupPagingState.value != pagingState
        ) {
            groupLoadingGate.release(loadToken)
            return null
        }
        if (
            retryFailed &&
            !groupPagingState.compareAndSet(pagingState, pagingState.copy(failedOffset = null))
        ) {
            groupLoadingGate.release(loadToken)
            return null
        }
        if (retryFailed) _groupLoadMoreFailed.value = false

        val offset = pagingState.nextOffset
        return screenModelScope.launch(Dispatchers.IO) {
            searchGroups(
                requestKey = requestKey,
                generation = generation,
                offset = offset,
                append = true,
                loadToken = loadToken,
            )
        }
    }

    private suspend fun searchFirstGroupPage(
        requestKey: SearchRequestKey,
        generation: Long,
    ): Boolean {
        val loadToken = GroupLoadToken(
            generation = generation,
            offset = 0,
            append = false,
        )
        if (!groupLoadingGate.tryAcquire(loadToken)) return false
        return searchGroups(
            requestKey = requestKey,
            generation = generation,
            offset = 0,
            append = false,
            loadToken = loadToken,
        )
    }

    /**
     * 搜索用户
     * @param name 用户名
     * @return 是否搜索成功
     */
    private suspend fun searchUsers(
        name: String,
        requestKey: SearchRequestKey,
        generation: Long,
    ): Boolean {
        return authService.reTryAuthCatching {
            usersApi.searchUser(name)
        }.onSuccess {
            if (isCurrentRequest(requestKey, generation)) {
                _userSearchList.value = it
            }
        }.rethrowCancellationOrError().onApiFailure("UserSearch") {
            logger.error(it)
        }.isSuccess
    }

    /**
     * 搜索世界
     * @param name 世界名称
     * @return 是否搜索成功
     */
    private suspend fun searchWorlds(
        requestKey: SearchRequestKey,
        generation: Long,
    ): Boolean {
        val options = requireNotNull(requestKey.worldSearchOptions)
        return authService.reTryAuthCatching {
            worldsApi.searchWorld(
                search = requestKey.searchText,
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
            if (isCurrentRequest(requestKey, generation)) {
                _worldSearchList.value = it
            }
        }.rethrowCancellationOrError().onApiFailure("WorldSearch") {
            logger.error(it)
        }.isSuccess
    }

    /**
     * 搜索群组
     * @param query 搜索关键词
     * @return 是否搜索成功
     */
    private suspend fun searchGroups(
        requestKey: SearchRequestKey,
        generation: Long,
        offset: Int,
        append: Boolean,
        loadToken: GroupLoadToken,
    ): Boolean {
        try {
            return authService.reTryAuthCatching {
                groupsApi.searchGroups(
                    query = requestKey.searchText,
                    n = GROUP_PAGE_SIZE,
                    offset = offset,
                )
            }.onSuccess { page ->
                if (isCurrentRequest(requestKey, generation) && canApplyGroupPage(requestKey, offset, append)) {
                    _groupSearchList.value = if (append) {
                        (_groupSearchList.value + page).distinctBy { it.id }
                    } else {
                        page.distinctBy { it.id }
                    }
                    groupPagingState.value = GroupPagingState(
                        query = requestKey.searchText,
                        nextOffset = offset + page.size,
                    )
                    _groupHasMore.value = page.size == GROUP_PAGE_SIZE
                    _groupLoadMoreFailed.value = false
                }
            }.rethrowCancellationOrError().onFailure {
                if (
                    append &&
                    isCurrentRequest(requestKey, generation) &&
                    canApplyGroupPage(requestKey, offset, append = true)
                ) {
                    groupPagingState.update { state -> state.copy(failedOffset = offset) }
                    _groupLoadMoreFailed.value = true
                }
            }.onApiFailure("GroupSearch") {
                logger.error(it)
            }.isSuccess
        } finally {
            groupLoadingGate.release(loadToken)
        }
    }

    private fun canApplyGroupPage(
        requestKey: SearchRequestKey,
        offset: Int,
        append: Boolean,
    ): Boolean = !append || (
        groupPagingState.value.query == requestKey.searchText &&
            groupPagingState.value.nextOffset == offset
        )

    private fun currentRequestKey(): SearchRequestKey {
        val type = searchType.value
        return SearchRequestKey(
            searchText = searchText.value,
            searchType = type,
            worldSearchOptions = worldSearchOptions.value.takeIf { type == 1 },
        )
    }

    private fun isCurrentRequest(requestKey: SearchRequestKey, generation: Long): Boolean =
        requestGeneration.value == generation && currentRequestKey() == requestKey

    private fun advanceRequestGeneration() {
        requestGeneration.update { it + 1 }
    }

    private fun invalidateGroupPaging() {
        groupPagingState.value = GroupPagingState()
        _groupHasMore.value = false
        groupLoadingGate.invalidate()
        _groupLoadMoreFailed.value = false
    }

    private fun resetGroupPaging() {
        advanceRequestGeneration()
        invalidateGroupPaging()
    }

    private fun <T> Result<T>.rethrowCancellationOrError(): Result<T> = onFailure { cause ->
        when (cause) {
            is CancellationException -> throw cause
            is Error -> throw cause
        }
    }

    private companion object {
        const val GROUP_SEARCH_TYPE = 3
        const val GROUP_PAGE_SIZE = 20
    }
}

private data class SearchRequestKey(
    val searchText: String,
    val searchType: Int,
    val worldSearchOptions: WorldSearchOptions? = null,
)

private data class GroupPagingState(
    val query: String? = null,
    val nextOffset: Int = 0,
    val failedOffset: Int? = null,
)

internal data class GroupLoadToken(
    val generation: Long,
    val offset: Int,
    val append: Boolean,
)

internal class GroupLoadingGate {
    private val _owner = MutableStateFlow<GroupLoadToken?>(null)
    val owner: StateFlow<GroupLoadToken?> = _owner.asStateFlow()

    fun tryAcquire(token: GroupLoadToken): Boolean =
        _owner.compareAndSet(expect = null, update = token)

    fun release(token: GroupLoadToken) {
        _owner.compareAndSet(expect = token, update = null)
    }

    fun invalidate() {
        _owner.value = null
    }
}
