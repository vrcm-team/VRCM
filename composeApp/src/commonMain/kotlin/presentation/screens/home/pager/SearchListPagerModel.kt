package io.github.vrcmteam.vrcm.presentation.screens.home.pager

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.core.logger.Logger

internal enum class PublicSearchTab(val searchType: Int, val tabIndex: Int) {
    User(searchType = 0, tabIndex = 0),
    World(searchType = 1, tabIndex = 1),
    Group(searchType = 3, tabIndex = 2);

    companion object {
        fun fromSearchType(searchType: Int): PublicSearchTab =
            entries.firstOrNull { it.searchType == searchType } ?: User

        fun fromTabIndex(tabIndex: Int): PublicSearchTab =
            entries.firstOrNull { it.tabIndex == tabIndex } ?: User
    }
}

internal enum class SearchLoadPhase {
    AwaitingQuery,
    Loading,
    Success,
    Error,
}

internal data class PublicSearchLoadState(
    val phase: SearchLoadPhase = SearchLoadPhase.AwaitingQuery,
)

/** 公开搜索页面状态，查询、结果和请求代次均按标签隔离。 */
class SearchListPagerModel(
    private val usersApi: UsersApi,
    private val worldsApi: WorldsApi,
    private val groupsApi: GroupsApi,
    private val authService: AuthService,
    private val logger: Logger,
) : ViewModel() {
    private val _userSearchList = MutableStateFlow(emptyList<SearchUserData>())
    val userSearchList = _userSearchList.asStateFlow()

    private val _worldSearchList = MutableStateFlow(emptyList<WorldData>())
    val worldSearchList = _worldSearchList.asStateFlow()

    private val _groupSearchList = MutableStateFlow(emptyList<LimitedGroup>())
    val groupSearchList: StateFlow<List<LimitedGroup>> = _groupSearchList.asStateFlow()

    private val _searchType = MutableStateFlow(PublicSearchTab.User.searchType)
    val searchType = _searchType.asStateFlow()

    private val _queriesByType = MutableStateFlow(
        PublicSearchTab.entries.associate { it.searchType to "" },
    )
    private val _searchText = MutableStateFlow("")
    val searchText: StateFlow<String> = _searchText.asStateFlow()

    private val _loadStates = MutableStateFlow(
        PublicSearchTab.entries.associate { it.searchType to PublicSearchLoadState() },
    )
    internal val loadStates = _loadStates.asStateFlow()

    private val _worldSearchOptions = MutableStateFlow(WorldSearchOptions())
    val worldSearchOptions: StateFlow<WorldSearchOptions> = _worldSearchOptions.asStateFlow()

    private val _groupHasMore = MutableStateFlow(false)
    val groupHasMore: StateFlow<Boolean> = _groupHasMore.asStateFlow()

    private val groupLoadingGate = GroupLoadingGate()
    val isLoadingGroups: StateFlow<Boolean> = groupLoadingGate.owner
        .map { it != null }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val _groupLoadMoreFailed = MutableStateFlow(false)
    val groupLoadMoreFailed: StateFlow<Boolean> = _groupLoadMoreFailed.asStateFlow()

    private val groupPagingState = MutableStateFlow(GroupPagingState())
    private val requestGenerations = MutableStateFlow(
        PublicSearchTab.entries.associate { it.searchType to 0L },
    )
    private val successfulRequestKeysByType = MutableStateFlow<Map<Int, SearchRequestKey>>(emptyMap())
    private var authenticatedUserId: String? = SharedFlowCentre.currentSession.value?.account?.userId

    init {
        viewModelScope.launch {
            SharedFlowCentre.currentSession.collect { session ->
                val nextUserId = session?.account?.userId
                val accountChanged = authenticatedUserId != nextUserId
                authenticatedUserId = nextUserId
                if (accountChanged) resetForAccountChange()
            }
        }
    }

    fun setSearchType(type: Int) {
        val tab = PublicSearchTab.fromSearchType(type)
        if (tab.searchType == _searchType.value) return
        _searchType.value = tab.searchType
        _searchText.value = _queriesByType.value.getValue(tab.searchType)
    }

    fun setSearchText(text: String) {
        val type = _searchType.value
        if (_queriesByType.value[type] == text) return
        advanceRequestGeneration(type)
        _queriesByType.update { it + (type to text) }
        _searchText.value = text
        successfulRequestKeysByType.update { it - type }
        updateLoadState(type, PublicSearchLoadState())
        when (type) {
            PublicSearchTab.User.searchType -> _userSearchList.value = emptyList()
            PublicSearchTab.World.searchType -> _worldSearchList.value = emptyList()
            PublicSearchTab.Group.searchType -> {
                _groupSearchList.value = emptyList()
                invalidateGroupPaging()
            }
        }
    }

    suspend fun updateWorldSearchOptions(options: WorldSearchOptions) {
        if (_worldSearchOptions.value == options) return
        val type = PublicSearchTab.World.searchType
        advanceRequestGeneration(type)
        successfulRequestKeysByType.update { it - type }
        _worldSearchOptions.value = options
        _worldSearchList.value = emptyList()
        if (queryFor(type).isNotBlank()) refreshSearchList(type)
    }

    suspend fun loadSearchListIfNeeded() {
        val type = _searchType.value
        val requestKey = requestKeyFor(type)
        if (requestKey.searchText.isBlank()) {
            updateLoadState(type, PublicSearchLoadState())
            return
        }
        if (successfulRequestKeysByType.value[type] == requestKey) return
        refreshSearchList(type)
    }

    suspend fun refreshSearchList(): Boolean = refreshSearchList(_searchType.value)

    private suspend fun refreshSearchList(type: Int): Boolean {
        val requestKey = requestKeyFor(type)
        if (requestKey.searchText.isBlank()) {
            updateLoadState(type, PublicSearchLoadState())
            return false
        }
        val generation = generationFor(type)
        updateLoadState(type, PublicSearchLoadState(SearchLoadPhase.Loading))
        val success = withContext(Dispatchers.IO) {
            when (type) {
                PublicSearchTab.User.searchType -> searchUsers(requestKey, generation)
                PublicSearchTab.World.searchType -> searchWorlds(requestKey, generation)
                PublicSearchTab.Group.searchType -> searchFirstGroupPage(requestKey, generation)
                else -> false
            }
        }
        if (isCurrentRequest(requestKey, generation)) {
            if (success) {
                successfulRequestKeysByType.update { it + (type to requestKey) }
                updateLoadState(type, PublicSearchLoadState(SearchLoadPhase.Success))
            } else {
                updateLoadState(type, PublicSearchLoadState(SearchLoadPhase.Error))
            }
        }
        return success
    }

    fun loadMoreGroups(): Job? = startGroupPage(retryFailed = false)

    fun retryLoadMoreGroups(): Job? = startGroupPage(retryFailed = true)

    private fun startGroupPage(retryFailed: Boolean): Job? {
        val type = PublicSearchTab.Group.searchType
        val requestKey = requestKeyFor(type)
        val generation = generationFor(type)
        val pagingState = groupPagingState.value
        val pageFailed = pagingState.failedOffset == pagingState.nextOffset
        if (
            requestKey.searchText.isBlank() || !groupHasMore.value ||
            pagingState.query != requestKey.searchText || retryFailed != pageFailed
        ) return null

        val loadToken = GroupLoadToken(generation, pagingState.nextOffset, append = true)
        if (!groupLoadingGate.tryAcquire(loadToken)) return null
        if (!isCurrentRequest(requestKey, generation) || groupPagingState.value != pagingState) {
            groupLoadingGate.release(loadToken)
            return null
        }
        if (retryFailed && !groupPagingState.compareAndSet(
                pagingState,
                pagingState.copy(failedOffset = null),
            )
        ) {
            groupLoadingGate.release(loadToken)
            return null
        }
        if (retryFailed) _groupLoadMoreFailed.value = false
        return viewModelScope.launch(Dispatchers.IO) {
            searchGroups(requestKey, generation, pagingState.nextOffset, append = true, loadToken)
        }
    }

    private suspend fun searchFirstGroupPage(requestKey: SearchRequestKey, generation: Long): Boolean {
        val loadToken = GroupLoadToken(generation, offset = 0, append = false)
        if (!groupLoadingGate.tryAcquire(loadToken)) return false
        return searchGroups(requestKey, generation, offset = 0, append = false, loadToken)
    }

    private suspend fun searchUsers(requestKey: SearchRequestKey, generation: Long): Boolean =
        authService.reTryAuthCatching {
            usersApi.searchUser(requestKey.searchText)
        }.onSuccess {
            if (isCurrentRequest(requestKey, generation)) _userSearchList.value = it
        }.rethrowCancellationOrError().onApiFailure("UserSearch") { logger.error(it) }.isSuccess

    private suspend fun searchWorlds(requestKey: SearchRequestKey, generation: Long): Boolean {
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
                notag = options.notag,
            )
        }.onSuccess {
            if (isCurrentRequest(requestKey, generation)) _worldSearchList.value = it
        }.rethrowCancellationOrError().onApiFailure("WorldSearch") { logger.error(it) }.isSuccess
    }

    private suspend fun searchGroups(
        requestKey: SearchRequestKey,
        generation: Long,
        offset: Int,
        append: Boolean,
        loadToken: GroupLoadToken,
    ): Boolean {
        try {
            return authService.reTryAuthCatching {
                groupsApi.searchGroups(requestKey.searchText, GROUP_PAGE_SIZE, offset)
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
                if (append && isCurrentRequest(requestKey, generation) && canApplyGroupPage(requestKey, offset, true)) {
                    groupPagingState.update { it.copy(failedOffset = offset) }
                    _groupLoadMoreFailed.value = true
                }
            }.onApiFailure("GroupSearch") { logger.error(it) }.isSuccess
        } finally {
            groupLoadingGate.release(loadToken)
        }
    }

    private fun canApplyGroupPage(requestKey: SearchRequestKey, offset: Int, append: Boolean) =
        !append || (groupPagingState.value.query == requestKey.searchText &&
            groupPagingState.value.nextOffset == offset)

    private fun requestKeyFor(type: Int) = SearchRequestKey(
        searchText = queryFor(type),
        searchType = type,
        worldSearchOptions = _worldSearchOptions.value.takeIf { type == PublicSearchTab.World.searchType },
    )

    private fun queryFor(type: Int): String = _queriesByType.value[type].orEmpty()

    private fun isCurrentRequest(requestKey: SearchRequestKey, generation: Long): Boolean =
        generationFor(requestKey.searchType) == generation && requestKeyFor(requestKey.searchType) == requestKey

    private fun generationFor(type: Int): Long = requestGenerations.value[type] ?: 0L

    private fun advanceRequestGeneration(type: Int) {
        requestGenerations.update { it + (type to (generationFor(type) + 1)) }
    }

    private fun updateLoadState(type: Int, state: PublicSearchLoadState) {
        _loadStates.update { it + (type to state) }
    }

    private fun invalidateGroupPaging() {
        groupPagingState.value = GroupPagingState()
        _groupHasMore.value = false
        groupLoadingGate.invalidate()
        _groupLoadMoreFailed.value = false
    }

    private fun resetForAccountChange() {
        PublicSearchTab.entries.forEach { advanceRequestGeneration(it.searchType) }
        successfulRequestKeysByType.value = emptyMap()
        _userSearchList.value = emptyList()
        _worldSearchList.value = emptyList()
        _groupSearchList.value = emptyList()
        _queriesByType.value = PublicSearchTab.entries.associate { it.searchType to "" }
        _searchText.value = ""
        _loadStates.value = PublicSearchTab.entries.associate { it.searchType to PublicSearchLoadState() }
        invalidateGroupPaging()
    }

    private fun <T> Result<T>.rethrowCancellationOrError(): Result<T> = onFailure { cause ->
        when (cause) {
            is CancellationException -> throw cause
            is Error -> throw cause
        }
    }

    private companion object {
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

    fun tryAcquire(token: GroupLoadToken): Boolean = _owner.compareAndSet(null, token)

    fun release(token: GroupLoadToken) {
        _owner.compareAndSet(token, null)
    }

    fun invalidate() {
        _owner.value = null
    }
}
