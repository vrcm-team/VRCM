package io.github.vrcmteam.vrcm.presentation.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.vrcmteam.vrcm.core.shared.AccountSessionToken
import io.github.vrcmteam.vrcm.core.shared.AuthenticatedAccount
import io.github.vrcmteam.vrcm.core.shared.SharedFlowCentre
import io.github.vrcmteam.vrcm.network.api.playermoderation.PlayerModerationApi
import io.github.vrcmteam.vrcm.network.api.playermoderation.PlayerModerationData
import io.github.vrcmteam.vrcm.service.AuthService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.time.Instant

internal sealed interface PlayerModerationListState {
    data object Unavailable : PlayerModerationListState
    data object Loading : PlayerModerationListState
    data class Failed(val cause: Throwable) : PlayerModerationListState

    data class Ready(
        val records: List<PlayerModerationData>,
        val selectedType: String? = null,
    ) : PlayerModerationListState {
        val availableTypes: List<String> = records.map { it.type }.distinct()
        private val keyedRecords: List<PlayerModerationListItem> = records.withStableKeys()
        val visibleRecords: List<PlayerModerationListItem> = selectedType?.let { selected ->
            keyedRecords.filter { it.record.type == selected }
        } ?: keyedRecords
    }
}

internal data class PlayerModerationListItem(
    val record: PlayerModerationData,
    val key: String,
)

internal data class PlayerModerationListResponse(
    val result: Result<List<PlayerModerationData>>,
    val sessionToken: AccountSessionToken,
)

/** Keeps list state bound to the exact authenticated session that initiated each request. */
internal class PlayerModerationListController(
    private val scope: CoroutineScope,
    private val sessions: StateFlow<AuthenticatedAccount?>,
    private val isCurrentSession: (AccountSessionToken) -> Boolean,
    private val load: suspend (AccountSessionToken) -> PlayerModerationListResponse?,
) {
    private val _state = MutableStateFlow<PlayerModerationListState>(
        if (sessions.value == null) {
            PlayerModerationListState.Unavailable
        } else {
            PlayerModerationListState.Loading
        },
    )
    val state: StateFlow<PlayerModerationListState> = _state.asStateFlow()

    private var activeSessionToken: AccountSessionToken? = null
    private var selectedType: String? = null
    private var requestId = 0L
    private var loadJob: Job? = null

    init {
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            sessions.collect { session ->
                val nextToken = session?.token
                if (nextToken != activeSessionToken) {
                    activeSessionToken = nextToken
                    selectedType = null
                    startLoad(nextToken)
                }
            }
        }
    }

    fun selectType(type: String?) {
        val ready = _state.value as? PlayerModerationListState.Ready ?: return
        if (type != null && type !in ready.availableTypes) return
        selectedType = type
        _state.value = ready.copy(selectedType = type)
    }

    fun refresh() {
        if (_state.value is PlayerModerationListState.Loading) return
        startLoad(sessions.value?.token)
    }

    fun retry() {
        if (_state.value is PlayerModerationListState.Failed) refresh()
    }

    private fun startLoad(sessionToken: AccountSessionToken?) {
        val currentRequestId = ++requestId
        loadJob?.cancel()
        if (sessionToken == null) {
            activeSessionToken = null
            selectedType = null
            _state.value = PlayerModerationListState.Unavailable
            return
        }

        _state.value = PlayerModerationListState.Loading
        loadJob = scope.launch {
            val response = load(sessionToken) ?: return@launch
            if (!accepts(currentRequestId, response.sessionToken)) return@launch

            response.result.fold(
                onSuccess = { records ->
                    if (!accepts(currentRequestId, response.sessionToken)) return@fold
                    val ordered = records.stableNewestFirst()
                    selectedType = selectedType?.takeIf { selected ->
                        ordered.any { it.type == selected }
                    }
                    _state.value = PlayerModerationListState.Ready(
                        records = ordered,
                        selectedType = selectedType,
                    )
                },
                onFailure = { error ->
                    if (accepts(currentRequestId, response.sessionToken)) {
                        _state.value = PlayerModerationListState.Failed(error)
                    }
                },
            )
        }
    }

    private fun accepts(expectedRequestId: Long, responseToken: AccountSessionToken): Boolean =
        requestId == expectedRequestId &&
            sessions.value?.token == responseToken &&
            isCurrentSession(responseToken)
}

internal class PlayerModerationListScreenModel(
    authService: AuthService,
    moderationApi: PlayerModerationApi,
) : ViewModel() {
    private val controller = PlayerModerationListController(
        scope = viewModelScope,
        sessions = SharedFlowCentre.currentSession,
        isCurrentSession = SharedFlowCentre::isCurrentSession,
        load = { sessionToken ->
            authService.runSessionBoundCatching(sessionToken) {
                moderationApi.getAll()
            }?.let { response ->
                PlayerModerationListResponse(response.result, response.sessionToken)
            }
        },
    )

    val state: StateFlow<PlayerModerationListState> = controller.state

    fun selectType(type: String?) = controller.selectType(type)

    fun refresh() = controller.refresh()

    fun retry() = controller.retry()
}

/** Sorts only when every record has a valid time; otherwise preserves the complete server order. */
@OptIn(kotlin.time.ExperimentalTime::class)
internal fun List<PlayerModerationData>.stableNewestFirst(): List<PlayerModerationData> {
    val dated = mapIndexed { index, record ->
        IndexedValue(index, runCatching { Instant.parse(record.created) }.getOrNull())
    }
    if (dated.any { it.value == null }) return this

    return indices.sortedWith(
        compareByDescending<Int> { dated[it].value!! }
            .thenBy { dated[it].index },
    ).map(::get)
}

private fun List<PlayerModerationData>.withStableKeys(): List<PlayerModerationListItem> {
    val idCounts = groupingBy { it.id }.eachCount()
    return mapIndexed { index, record ->
        val uniqueId = record.id.takeIf { it.isNotBlank() && idCounts[it] == 1 }
        PlayerModerationListItem(
            record = record,
            key = uniqueId ?: buildString {
                append("fallback:")
                append(record.id)
                append(':')
                append(record.targetUserId)
                append(':')
                append(record.type)
                append(':')
                append(record.created)
                append(':')
                append(index)
            },
        )
    }
}
