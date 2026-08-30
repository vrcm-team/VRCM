package io.github.vrcmteam.vrcm.presentation.screens.activity

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.vrcmteam.vrcm.service.FriendActivityEvent
import io.github.vrcmteam.vrcm.service.FriendActivityEventType
import io.github.vrcmteam.vrcm.service.FriendActivityService
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn

enum class FriendActivityTimelineFilter(internal val eventTypes: Set<FriendActivityEventType>) {
    All(emptySet()),
    Presence(setOf(FriendActivityEventType.Online, FriendActivityEventType.Offline)),
    Location(setOf(FriendActivityEventType.LocationChanged)),
    Profile(setOf(FriendActivityEventType.StatusChanged, FriendActivityEventType.BioChanged)),
    Meetup(setOf(FriendActivityEventType.Met, FriendActivityEventType.Left)),
}

sealed interface FriendActivityTimelineState {
    data object Loading : FriendActivityTimelineState
    data class Content(val events: List<FriendActivityEvent>) : FriendActivityTimelineState
    data object Error : FriendActivityTimelineState
}

@OptIn(ExperimentalCoroutinesApi::class)
class FriendActivityTimelineModel(
    private val friendActivityService: FriendActivityService,
) : ViewModel() {
    private val selectedFilter = MutableStateFlow(FriendActivityTimelineFilter.All)
    private val reloadRequest = MutableStateFlow(0)

    val filter = selectedFilter

    val state = combine(selectedFilter, reloadRequest) { filter, _ -> filter }
        .flatMapLatest { filter ->
            friendActivityService.observeAllEvents(
                types = filter.eventTypes,
                limit = TIMELINE_EVENT_LIMIT,
            ).map<List<FriendActivityEvent>, FriendActivityTimelineState> { events ->
                FriendActivityTimelineState.Content(events.deduplicateActivityEvents())
            }.onStart {
                emit(FriendActivityTimelineState.Loading)
            }.catch {
                emit(FriendActivityTimelineState.Error)
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = FriendActivityTimelineState.Loading,
        )

    fun selectFilter(filter: FriendActivityTimelineFilter) {
        selectedFilter.value = filter
    }

    fun retry() {
        reloadRequest.value++
    }

    private companion object {
        const val TIMELINE_EVENT_LIMIT = 500
    }
}

private data class FriendActivityEventIdentity(
    val friendUserId: String,
    val type: FriendActivityEventType,
    val occurredAtMillis: Long,
    val previousValue: String?,
    val currentValue: String?,
    val worldId: String?,
    val accessType: String?,
)

internal fun List<FriendActivityEvent>.deduplicateActivityEvents(): List<FriendActivityEvent> =
    distinctBy { event ->
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
