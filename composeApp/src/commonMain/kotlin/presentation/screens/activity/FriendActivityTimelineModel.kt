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
        types: Set<FriendActivityEventType>,
        limit: Int,
    ): Flow<List<FriendActivityEvent>>

    fun observeBefore(
        types: Set<FriendActivityEventType>,
        cursor: FriendActivityTimelineCursor,
        limit: Int,
    ): Flow<List<FriendActivityEvent>>
}

private class ServiceFriendActivityTimelineSource(
    private val service: FriendActivityService,
) : FriendActivityTimelineSource {
    override val sessionTokens = SharedFlowCentre.currentSession
        .map { it?.token }
        .distinctUntilChanged()

    override fun observeHead(
        types: Set<FriendActivityEventType>,
        limit: Int,
    ) = service.observeAllEvents(types = types, limit = limit)

    override fun observeBefore(
        types: Set<FriendActivityEventType>,
        cursor: FriendActivityTimelineCursor,
        limit: Int,
    ) = service.observeAllEventsBefore(
        types = types,
        beforeOccurredAtMillis = cursor.occurredAtMillis,
        beforeId = cursor.id,
        limit = limit,
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
    private var accumulator = TimelineAccumulator(pageSize)
    private var loadMoreJob: Job? = null

    init {
        require(pageSize > 0) { "pageSize must be positive" }
        viewModelScope.launch {
            combine(source.sessionTokens, selectedFilter, reloadRequest) { token, filter, _ ->
                token to filter
            }.collectLatest { (token, filter) ->
                val currentGeneration = ++generation
                loadMoreJob?.cancel()
                accumulator = TimelineAccumulator(pageSize)
                if (token == null) {
                    _state.value = FriendActivityTimelineState.Content(emptyList(), hasMore = false)
                    return@collectLatest
                }

                _state.value = FriendActivityTimelineState.Loading
                var emittedContent = false
                try {
                    source.observeHead(filter.eventTypes, pageSize + 1).collect { page ->
                        if (currentGeneration != generation) return@collect
                        if (page.isEmpty()) loadMoreJob?.cancel()
                        emittedContent = true
                        val current = _state.value as? FriendActivityTimelineState.Content
                        val next = accumulator.applyHead(page)
                        _state.value = if (page.isEmpty()) {
                            next
                        } else {
                            next.copy(
                                isLoadingMore = current?.isLoadingMore == true,
                                loadMoreError = current?.loadMoreError == true,
                            )
                        }
                    }
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Throwable) {
                    if (currentGeneration == generation && !emittedContent) {
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
        val cursor = accumulator.cursor ?: return
        if (!content.hasMore || content.isLoadingMore || content.loadMoreError) return

        val currentGeneration = generation
        val currentFilter = selectedFilter.value
        _state.value = content.copy(isLoadingMore = true, loadMoreError = false)
        loadMoreJob = viewModelScope.launch {
            try {
                val page = source.observeBefore(
                    types = currentFilter.eventTypes,
                    cursor = cursor,
                    limit = pageSize + 1,
                ).first()
                if (currentGeneration == generation && currentFilter == selectedFilter.value) {
                    _state.value = accumulator.applyAppend(page)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Throwable) {
                if (currentGeneration == generation && currentFilter == selectedFilter.value) {
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

    private companion object {
        const val DEFAULT_PAGE_SIZE = 50
    }
}

internal class TimelineAccumulator(
    private val pageSize: Int,
) {
    private val eventsById = linkedMapOf<Long, FriendActivityEvent>()
    private var hasLoadedMore = false

    var cursor: FriendActivityTimelineCursor? = null
        private set

    private var hasMore = false

    fun applyHead(page: List<FriendActivityEvent>): FriendActivityTimelineState.Content {
        if (page.isEmpty()) {
            eventsById.clear()
            cursor = null
            hasLoadedMore = false
            hasMore = false
            return content()
        }

        val loaded = page.take(pageSize)
        loaded.forEach { eventsById[it.id] = it }
        if (!hasLoadedMore) {
            cursor = loaded.lastOrNull()?.toCursor()
            hasMore = page.size > pageSize
        }
        return content()
    }

    fun applyAppend(page: List<FriendActivityEvent>): FriendActivityTimelineState.Content {
        val loaded = page.take(pageSize)
        loaded.forEach { eventsById[it.id] = it }
        hasLoadedMore = true
        cursor = loaded.lastOrNull()?.toCursor() ?: cursor
        hasMore = page.size > pageSize
        return content()
    }

    private fun content() = FriendActivityTimelineState.Content(
        events = eventsById.values.normalizeActivityEvents(),
        hasMore = hasMore,
    )
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
