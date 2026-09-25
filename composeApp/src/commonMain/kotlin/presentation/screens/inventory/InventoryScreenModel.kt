package io.github.vrcmteam.vrcm.presentation.screens.inventory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.vrcmteam.vrcm.core.shared.AccountSessionToken
import io.github.vrcmteam.vrcm.core.shared.SharedFlowCentre
import io.github.vrcmteam.vrcm.network.api.economy.EconomyApi
import io.github.vrcmteam.vrcm.network.api.inventory.InventoryApi
import io.github.vrcmteam.vrcm.network.api.inventory.InventoryItemType
import io.github.vrcmteam.vrcm.network.api.inventory.InventorySortOrder
import io.github.vrcmteam.vrcm.network.api.inventory.data.InventoryData
import io.github.vrcmteam.vrcm.network.api.inventory.data.InventoryItemData
import io.github.vrcmteam.vrcm.service.AuthService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.job
import kotlinx.coroutines.launch

enum class InventoryArchivedFilter(val archived: Boolean?) {
    All(null),
    Active(false),
    Archived(true),
}

data class InventoryFilters(
    val type: InventoryItemType? = null,
    val archived: InventoryArchivedFilter = InventoryArchivedFilter.Active,
    val order: InventorySortOrder = InventorySortOrder.NewestUpdated,
)

sealed interface InventoryScreenState {
    data object Loading : InventoryScreenState

    data object SessionMissing : InventoryScreenState

    data class Content(
        val items: List<InventoryItemData>,
        val totalCount: Int?,
        val hasMore: Boolean,
        val isRefreshing: Boolean = false,
        val refreshError: Boolean = false,
        val isLoadingMore: Boolean = false,
        val loadMoreError: Boolean = false,
    ) : InventoryScreenState

    data object Error : InventoryScreenState
}

internal data class InventoryPageRequest(
    val pageSize: Int,
    val offset: Int,
    val filters: InventoryFilters,
)

internal data class AuthenticatedInventoryPage(
    val result: Result<InventoryData>,
    val sessionToken: AccountSessionToken,
)

internal data class AuthenticatedCreditsBalance(
    val result: Result<Long>,
    val sessionToken: AccountSessionToken,
)

internal interface InventorySource {
    val sessionTokens: Flow<AccountSessionToken?>

    fun isCurrentSession(token: AccountSessionToken): Boolean

    suspend fun loadPage(
        sessionToken: AccountSessionToken,
        request: InventoryPageRequest,
    ): AuthenticatedInventoryPage?

    suspend fun loadCreditsBalance(
        sessionToken: AccountSessionToken,
    ): AuthenticatedCreditsBalance?
}

internal class NetworkInventorySource(
    private val authService: AuthService,
    private val inventoryApi: InventoryApi,
    private val economyApi: EconomyApi,
) : InventorySource {
    override val sessionTokens: Flow<AccountSessionToken?> = SharedFlowCentre.currentSession
        .map { it?.token }
        .distinctUntilChanged()

    override fun isCurrentSession(token: AccountSessionToken): Boolean =
        SharedFlowCentre.isCurrentSession(token)

    override suspend fun loadPage(
        sessionToken: AccountSessionToken,
        request: InventoryPageRequest,
    ): AuthenticatedInventoryPage? {
        val response = authService.runSessionBoundCatching(sessionToken) {
            inventoryApi.getInventory(
                n = request.pageSize,
                offset = request.offset,
                type = request.filters.type,
                archived = request.filters.archived.archived,
                order = request.filters.order,
            )
        } ?: return null
        return AuthenticatedInventoryPage(response.result, response.sessionToken)
    }

    override suspend fun loadCreditsBalance(
        sessionToken: AccountSessionToken,
    ): AuthenticatedCreditsBalance? {
        val response = authService.runSessionBoundCatching(sessionToken) {
            economyApi.getCreditsBalance(sessionToken.userId)
        } ?: return null
        return AuthenticatedCreditsBalance(
            result = response.result.map { it.balance },
            sessionToken = response.sessionToken,
        )
    }
}

internal data class InventoryPagingSnapshot(
    val items: List<InventoryItemData> = emptyList(),
    val nextOffset: Int = 0,
    val totalCount: Int? = null,
    val hasMore: Boolean = true,
)

internal fun appendInventoryPage(
    current: InventoryPagingSnapshot,
    page: InventoryData,
    pageSize: Int,
): InventoryPagingSnapshot {
    val seenIds = current.items.mapNotNullTo(mutableSetOf()) { item ->
        item.id.takeIf(String::isNotBlank)
    }
    val merged = buildList {
        addAll(current.items)
        page.data.forEach { item ->
            if (item.id.isBlank() || seenIds.add(item.id)) add(item)
        }
    }
    val nextOffset = current.nextOffset + page.data.size
    val totalCount = page.totalCount?.coerceAtLeast(0) ?: current.totalCount
    val hasMore = page.data.isNotEmpty() && when (totalCount) {
        null -> page.data.size >= pageSize
        else -> nextOffset < totalCount
    }
    return InventoryPagingSnapshot(
        items = merged,
        nextOffset = nextOffset,
        totalCount = totalCount,
        hasMore = hasMore,
    )
}

class InventoryScreenModel internal constructor(
    private val source: InventorySource,
    private val pageSize: Int = DEFAULT_PAGE_SIZE,
) : ViewModel() {
    constructor(
        authService: AuthService,
        inventoryApi: InventoryApi,
        economyApi: EconomyApi,
    ) : this(
        source = NetworkInventorySource(authService, inventoryApi, economyApi),
    )

    private val _filters = MutableStateFlow(InventoryFilters())
    private val _state = MutableStateFlow<InventoryScreenState>(InventoryScreenState.Loading)

    val filters = _filters.asStateFlow()
    val state = _state.asStateFlow()

    private val creditsBalanceStateMachine = CreditsBalanceStateMachine()
    internal val creditsBalanceState = creditsBalanceStateMachine.state

    private var activeToken: AccountSessionToken? = null
    private var sessionObserved = false
    private var requestGeneration = 0L
    private var paging = InventoryPagingSnapshot()
    private var activeRequest: Job? = null
    private var pendingSessionReload: AccountSessionToken? = null
    private var creditsBalanceRequest: Job? = null
    private var pendingCreditsSessionReload: AccountSessionToken? = null

    init {
        require(pageSize > 0) { "Inventory page size must be positive" }
        viewModelScope.launch {
            source.sessionTokens.distinctUntilChanged().collect(::onSessionChanged)
        }
    }

    fun selectType(type: InventoryItemType?) {
        updateFilters(_filters.value.copy(type = type))
    }

    fun selectArchived(filter: InventoryArchivedFilter) {
        updateFilters(_filters.value.copy(archived = filter))
    }

    fun selectOrder(order: InventorySortOrder) {
        updateFilters(_filters.value.copy(order = order))
    }

    fun refresh() {
        refreshCreditsBalance()
        if (activeRequest?.isActive == true) return
        val token = activeToken ?: return
        val content = _state.value as? InventoryScreenState.Content
        requestGeneration++
        pendingSessionReload = null
        _state.value = content?.copy(
            isRefreshing = true,
            refreshError = false,
            isLoadingMore = false,
            loadMoreError = false,
        ) ?: InventoryScreenState.Loading
        startInitialRequest(token, requestGeneration, _filters.value, preserveContent = content != null)
    }

    fun refreshCreditsBalance() {
        val token = activeToken ?: return
        startCreditsBalanceRequest(token)
    }

    fun retry() {
        if (creditsBalanceState.value == CreditsBalanceState.Error) {
            refreshCreditsBalance()
        }
        when (val current = _state.value) {
            InventoryScreenState.Error -> restartForCurrentContext()
            is InventoryScreenState.Content -> if (current.refreshError) refresh()
            InventoryScreenState.Loading,
            InventoryScreenState.SessionMissing,
            -> Unit
        }
    }

    fun loadMore() {
        val content = _state.value as? InventoryScreenState.Content ?: return
        val token = activeToken ?: return
        if (activeRequest?.isActive == true || !content.hasMore || content.loadMoreError) return

        val generation = requestGeneration
        val filters = _filters.value
        val offset = paging.nextOffset
        _state.value = content.copy(isLoadingMore = true, loadMoreError = false)
        startRequest {
            val response = loadPage(token, InventoryPageRequest(pageSize, offset, filters)) ?: return@startRequest
            if (!acceptResponse(response, generation, filters)) return@startRequest
            response.result.fold(
                onSuccess = { page ->
                    val latest = _state.value as? InventoryScreenState.Content ?: return@fold
                    paging = appendInventoryPage(paging, page, pageSize)
                    _state.value = latest.copy(
                        items = paging.items,
                        totalCount = paging.totalCount,
                        hasMore = paging.hasMore,
                        isLoadingMore = false,
                        loadMoreError = false,
                    )
                },
                onFailure = {
                    val latest = _state.value as? InventoryScreenState.Content ?: return@fold
                    _state.value = latest.copy(isLoadingMore = false, loadMoreError = true)
                },
            )
        }
    }

    fun retryLoadMore() {
        val content = _state.value as? InventoryScreenState.Content ?: return
        if (!content.loadMoreError || activeRequest?.isActive == true) return
        _state.value = content.copy(loadMoreError = false)
        loadMore()
    }

    private fun onSessionChanged(token: AccountSessionToken?) {
        val previous = activeToken
        if (sessionObserved && previous == token) return
        sessionObserved = true
        activeToken = token

        if (previous != null && token != null && previous.userId == token.userId &&
            creditsBalanceRequest?.isActive == true
        ) {
            pendingCreditsSessionReload = token
        } else {
            restartCreditsBalanceForCurrentContext(token)
        }

        if (previous != null && token != null && previous.userId == token.userId &&
            activeRequest?.isActive == true
        ) {
            pendingSessionReload = token
            return
        }
        restartForCurrentContext()
    }

    private fun updateFilters(filters: InventoryFilters) {
        if (_filters.value == filters) return
        _filters.value = filters
        restartForCurrentContext()
    }

    private fun restartForCurrentContext() {
        cancelActiveRequest()
        requestGeneration++
        paging = InventoryPagingSnapshot()
        pendingSessionReload = null
        val token = activeToken
        if (token == null) {
            _state.value = InventoryScreenState.SessionMissing
            return
        }
        _state.value = InventoryScreenState.Loading
        startInitialRequest(token, requestGeneration, _filters.value, preserveContent = false)
    }

    private fun startInitialRequest(
        token: AccountSessionToken,
        generation: Long,
        filters: InventoryFilters,
        preserveContent: Boolean,
    ) {
        startRequest {
            val response = loadPage(
                token,
                InventoryPageRequest(pageSize = pageSize, offset = 0, filters = filters),
            ) ?: return@startRequest
            if (!acceptResponse(response, generation, filters)) return@startRequest
            response.result.fold(
                onSuccess = { page ->
                    paging = appendInventoryPage(InventoryPagingSnapshot(), page, pageSize)
                    _state.value = InventoryScreenState.Content(
                        items = paging.items,
                        totalCount = paging.totalCount,
                        hasMore = paging.hasMore,
                    )
                },
                onFailure = {
                    val current = _state.value as? InventoryScreenState.Content
                    _state.value = if (preserveContent && current != null) {
                        current.copy(isRefreshing = false, refreshError = true)
                    } else {
                        InventoryScreenState.Error
                    }
                },
            )
        }
    }

    private suspend fun loadPage(
        token: AccountSessionToken,
        request: InventoryPageRequest,
    ): AuthenticatedInventoryPage? = try {
        source.loadPage(token, request)
    } catch (error: CancellationException) {
        throw error
    } catch (error: Throwable) {
        if (source.isCurrentSession(token)) {
            AuthenticatedInventoryPage(Result.failure(error), token)
        } else {
            null
        }
    }

    private fun restartCreditsBalanceForCurrentContext(token: AccountSessionToken?) {
        cancelCreditsBalanceRequest()
        pendingCreditsSessionReload = null
        creditsBalanceStateMachine.invalidate()
        if (token != null) startCreditsBalanceRequest(token)
    }

    private fun startCreditsBalanceRequest(token: AccountSessionToken) {
        val requestId = creditsBalanceStateMachine.tryStart() ?: return
        val job = viewModelScope.launch(start = CoroutineStart.LAZY) {
            try {
                val response = loadCreditsBalance(token)
                if (response == null) {
                    if (source.isCurrentSession(token) && activeToken?.userId == token.userId) {
                        creditsBalanceStateMachine.failDropped(requestId)
                    }
                    return@launch
                }
                if (!acceptCreditsBalanceResponse(response)) return@launch
                response.result.fold(
                    onSuccess = { balance ->
                        creditsBalanceStateMachine.complete(requestId, balance)
                    },
                    onFailure = { error ->
                        creditsBalanceStateMachine.fail(requestId, error)
                    },
                )
            } finally {
                finishCreditsBalanceRequest(coroutineContext.job)
            }
        }
        creditsBalanceRequest = job
        job.start()
    }

    private suspend fun loadCreditsBalance(
        token: AccountSessionToken,
    ): AuthenticatedCreditsBalance? = try {
        source.loadCreditsBalance(token)
    } catch (error: CancellationException) {
        throw error
    } catch (error: Throwable) {
        if (source.isCurrentSession(token)) {
            AuthenticatedCreditsBalance(Result.failure(error), token)
        } else {
            null
        }
    }

    private fun acceptCreditsBalanceResponse(response: AuthenticatedCreditsBalance): Boolean {
        if (!source.isCurrentSession(response.sessionToken)) return false
        if (activeToken?.userId != response.sessionToken.userId) return false
        activeToken = response.sessionToken
        if (pendingCreditsSessionReload == response.sessionToken) {
            pendingCreditsSessionReload = null
        }
        return true
    }

    private fun acceptResponse(
        response: AuthenticatedInventoryPage,
        generation: Long,
        filters: InventoryFilters,
    ): Boolean {
        if (requestGeneration != generation || _filters.value != filters) return false
        if (!source.isCurrentSession(response.sessionToken)) return false
        if (activeToken?.userId != response.sessionToken.userId) return false
        activeToken = response.sessionToken
        if (pendingSessionReload == response.sessionToken) pendingSessionReload = null
        return true
    }

    private fun startRequest(block: suspend () -> Unit) {
        val job = viewModelScope.launch(start = CoroutineStart.LAZY) {
            try {
                block()
            } finally {
                finishRequest(coroutineContext.job)
            }
        }
        activeRequest = job
        job.start()
    }

    private fun finishRequest(job: Job) {
        if (activeRequest !== job) return
        activeRequest = null
        val pending = pendingSessionReload ?: return
        if (activeToken == pending && source.isCurrentSession(pending)) {
            pendingSessionReload = null
            restartForCurrentContext()
        }
    }

    private fun finishCreditsBalanceRequest(job: Job) {
        if (creditsBalanceRequest !== job) return
        creditsBalanceRequest = null
        val pending = pendingCreditsSessionReload ?: return
        if (activeToken == pending && source.isCurrentSession(pending)) {
            pendingCreditsSessionReload = null
            restartCreditsBalanceForCurrentContext(pending)
        }
    }

    private fun cancelActiveRequest() {
        val request = activeRequest
        activeRequest = null
        request?.cancel()
    }

    private fun cancelCreditsBalanceRequest() {
        val request = creditsBalanceRequest
        creditsBalanceRequest = null
        request?.cancel()
    }

    private companion object {
        const val DEFAULT_PAGE_SIZE = 60
    }
}
