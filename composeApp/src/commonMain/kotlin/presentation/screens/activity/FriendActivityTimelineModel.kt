package io.github.vrcmteam.vrcm.presentation.screens.activity

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.vrcmteam.vrcm.core.shared.AccountSessionToken
import io.github.vrcmteam.vrcm.core.shared.SharedFlowCentre
import io.github.vrcmteam.vrcm.service.FriendActivityEvent
import io.github.vrcmteam.vrcm.service.FriendActivityEventType
import io.github.vrcmteam.vrcm.service.FriendActivityService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

enum class FriendActivityTimelineFilter(internal val eventTypes: Set<FriendActivityEventType>) {
    All(emptySet()),
    Presence(setOf(FriendActivityEventType.Online, FriendActivityEventType.Offline)),
    Location(setOf(FriendActivityEventType.LocationChanged)),
    Profile(setOf(FriendActivityEventType.StatusChanged, FriendActivityEventType.BioChanged)),
    Meetup(setOf(FriendActivityEventType.Met, FriendActivityEventType.Left)),
}

sealed interface FriendActivityTimelineState {
    data object Loading : FriendActivityTimelineState

    data class Content(
        val events: List<FriendActivityEvent>,
        val hasMore: Boolean,
        val isLoadingMore: Boolean = false,
        val loadMoreError: Boolean = false,
    ) : FriendActivityTimelineState

    data object Error : FriendActivityTimelineState
}

internal data class FriendActivityTimelineCursor(
    val occurredAtMillis: Long,
    val id: Long,
)

internal interface FriendActivityTimelineSource {
    val sessionTokens: Flow<AccountSessionToken?>

    fun observeHead(
        token: AccountSessionToken,
        types: Set<FriendActivityEventType>,
        limit: Int,
    ): Flow<List<FriendActivityEvent>>

    fun observeBefore(
        token: AccountSessionToken,
        types: Set<FriendActivityEventType>,
        cursor: FriendActivityTimelineCursor,
        limit: Int,
    ): Flow<List<FriendActivityEvent>>

    fun observeThrough(
        token: AccountSessionToken,
        types: Set<FriendActivityEventType>,
        cursor: FriendActivityTimelineCursor,
    ): Flow<List<FriendActivityEvent>>
}

private class ServiceFriendActivityTimelineSource(
    private val service: FriendActivityService,
) : FriendActivityTimelineSource {
    override val sessionTokens = SharedFlowCentre.currentSession
        .map { it?.token }
        .distinctUntilChanged()

    override fun observeHead(
        token: AccountSessionToken,
        types: Set<FriendActivityEventType>,
        limit: Int,
    ) = service.observeAllEvents(token = token, types = types, limit = limit)

    override fun observeBefore(
        token: AccountSessionToken,
        types: Set<FriendActivityEventType>,
        cursor: FriendActivityTimelineCursor,
        limit: Int,
    ) = service.observeAllEventsBefore(
        token = token,
        types = types,
        beforeOccurredAtMillis = cursor.occurredAtMillis,
        beforeId = cursor.id,
        limit = limit,
    )

    override fun observeThrough(
        token: AccountSessionToken,
        types: Set<FriendActivityEventType>,
        cursor: FriendActivityTimelineCursor,
    ) = service.observeAllEventsThrough(
        token = token,
        types = types,
        oldestOccurredAtMillis = cursor.occurredAtMillis,
        oldestId = cursor.id,
    )
}

class FriendActivityTimelineModel internal constructor(
    private val source: FriendActivityTimelineSource,
    private val pageSize: Int = DEFAULT_PAGE_SIZE,
) : ViewModel() {
    constructor(service: FriendActivityService) : this(ServiceFriendActivityTimelineSource(service))

    private val selectedFilter = MutableStateFlow(FriendActivityTimelineFilter.All)
    private val reloadRequest = MutableStateFlow(0L)
    private val _state = MutableStateFlow<FriendActivityTimelineState>(FriendActivityTimelineState.Loading)

    val filter = selectedFilter.asStateFlow()
    val state = _state.asStateFlow()

    private var generation = 0L
    private var activeToken: AccountSessionToken? = null
    private var oldestCursor: FriendActivityTimelineCursor? = null
    private var hasMore = false
    private var snapshotJob: Job? = null
    private var loadMoreJob: Job? = null

    init {
        require(pageSize > 0) { "pageSize must be positive" }
        viewModelScope.launch {
            combine(source.sessionTokens, selectedFilter, reloadRequest) { token, filter, _ ->
                token to filter
            }.collectLatest { (token, filter) ->
                val currentGeneration = ++generation
                activeToken = token
                oldestCursor = null
                hasMore = false
                snapshotJob?.cancel()
                loadMoreJob?.cancel()
                if (token == null) {
                    _state.value = FriendActivityTimelineState.Content(emptyList(), hasMore = false)
                    return@collectLatest
                }

                _state.value = FriendActivityTimelineState.Loading
                var emittedContent = false
                try {
                    source.observeHead(token, filter.eventTypes, pageSize + 1).collect { page ->
                        if (!isCurrent(currentGeneration, token, filter)) return@collect
                        if (oldestCursor != null) return@collect
                        emittedContent = true
                        if (page.isEmpty()) {
                            _state.value = FriendActivityTimelineState.Content(emptyList(), hasMore = false)
                            return@collect
                        }

                        val loaded = page.take(pageSize)
                        oldestCursor = loaded.last().toCursor()
                        hasMore = page.size > pageSize
                        _state.value = FriendActivityTimelineState.Content(
                            events = loaded.normalizeActivityEvents(),
                            hasMore = hasMore,
                        )
                        observeLoadedSnapshot(currentGeneration, token, filter)
                    }
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Throwable) {
                    if (isCurrent(currentGeneration, token, filter) && !emittedContent) {
                        _state.value = FriendActivityTimelineState.Error
                    }
                }
            }
        }
    }

    fun selectFilter(filter: FriendActivityTimelineFilter) {
        if (selectedFilter.value != filter) selectedFilter.value = filter
    }

    fun retry() {
        reloadRequest.value++
    }

    fun loadMore() {
        val content = _state.value as? FriendActivityTimelineState.Content ?: return
        val cursor = oldestCursor ?: return
        val token = activeToken ?: return
        val currentFilter = selectedFilter.value
        val currentGeneration = generation
        if (!content.hasMore || content.isLoadingMore || content.loadMoreError) return

        _state.value = content.copy(isLoadingMore = true, loadMoreError = false)
        loadMoreJob = viewModelScope.launch {
            try {
                val page = source.observeBefore(
                    token = token,
                    types = currentFilter.eventTypes,
                    cursor = cursor,
                    limit = pageSize + 1,
                ).first()
                if (!isCurrent(currentGeneration, token, currentFilter)) return@launch
                val loaded = page.take(pageSize)
                hasMore = page.size > pageSize
                if (loaded.isEmpty()) {
                    _state.value = content.copy(
                        hasMore = false,
                        isLoadingMore = false,
                        loadMoreError = false,
                    )
                } else {
                    oldestCursor = loaded.last().toCursor()
                    observeLoadedSnapshot(
                        currentGeneration,
                        token,
                        currentFilter,
                        completesLoadMore = true,
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Throwable) {
                if (isCurrent(currentGeneration, token, currentFilter)) {
                    val latest = _state.value as? FriendActivityTimelineState.Content ?: return@launch
                    _state.value = latest.copy(isLoadingMore = false, loadMoreError = true)
                }
            }
        }
    }

    fun retryLoadMore() {
        val content = _state.value as? FriendActivityTimelineState.Content ?: return
        if (!content.loadMoreError) return
        _state.value = content.copy(loadMoreError = false)
        loadMore()
    }

    private fun observeLoadedSnapshot(
        currentGeneration: Long,
        token: AccountSessionToken,
        filter: FriendActivityTimelineFilter,
        completesLoadMore: Boolean = false,
    ) {
        val cursor = oldestCursor ?: return
        snapshotJob?.cancel()
        snapshotJob = viewModelScope.launch {
            var firstSnapshot = true
            try {
                source.observeThrough(token, filter.eventTypes, cursor).collect { snapshot ->
                    if (!isCurrent(currentGeneration, token, filter) || cursor != oldestCursor) {
                        return@collect
                    }
                    val current = _state.value as? FriendActivityTimelineState.Content
                    if (snapshot.isEmpty()) hasMore = false
                    _state.value = FriendActivityTimelineState.Content(
                        events = snapshot.normalizeActivityEvents(),
                        hasMore = hasMore,
                        isLoadingMore = if (completesLoadMore && firstSnapshot) false else {
                            current?.isLoadingMore == true
                        },
                        loadMoreError = current?.loadMoreError == true,
                    )
                    firstSnapshot = false
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Throwable) {
                if (completesLoadMore && isCurrent(currentGeneration, token, filter)) {
                    val current = _state.value as? FriendActivityTimelineState.Content ?: return@launch
                    _state.value = current.copy(isLoadingMore = false, loadMoreError = true)
                }
            }
        }
    }

    private fun isCurrent(
        expectedGeneration: Long,
        expectedToken: AccountSessionToken,
        expectedFilter: FriendActivityTimelineFilter,
    ): Boolean = generation == expectedGeneration &&
        activeToken == expectedToken &&
        selectedFilter.value == expectedFilter

    private companion object {
        const val DEFAULT_PAGE_SIZE = 50
    }
}

private fun FriendActivityEvent.toCursor() = FriendActivityTimelineCursor(
    occurredAtMillis = occurredAtMillis,
    id = id,
)

private data class FriendActivityEventIdentity(
    val friendUserId: String,
    val type: FriendActivityEventType,
    val occurredAtMillis: Long,
    val previousValue: String?,
    val currentValue: String?,
    val worldId: String?,
    val accessType: String?,
)

internal fun Iterable<FriendActivityEvent>.normalizeActivityEvents(): List<FriendActivityEvent> =
    sortedWith(
        compareByDescending<FriendActivityEvent>(FriendActivityEvent::occurredAtMillis)
            .thenByDescending(FriendActivityEvent::id)
    ).distinctBy { event ->
        FriendActivityEventIdentity(
            friendUserId = event.friendUserId,
            type = event.type,
            occurredAtMillis = event.occurredAtMillis,
            previousValue = event.previousValue,
            currentValue = event.currentValue,
            worldId = event.worldId,
            accessType = event.accessType?.name,
        )
    }

internal fun List<FriendActivityEvent>.deduplicateActivityEvents(): List<FriendActivityEvent> =
    normalizeActivityEvents()
