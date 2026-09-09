package io.github.vrcmteam.vrcm.presentation.screens.world

import io.github.vrcmteam.vrcm.core.shared.AccountSessionToken
import io.github.vrcmteam.vrcm.core.shared.AuthenticatedAccount
import io.github.vrcmteam.vrcm.network.api.worlds.WorldsApi
import io.github.vrcmteam.vrcm.network.api.worlds.data.WorldData
import io.github.vrcmteam.vrcm.network.api.worlds.data.WorldUpdateData
import io.github.vrcmteam.vrcm.presentation.screens.world.data.WorldProfileVo
import io.github.vrcmteam.vrcm.service.AuthService
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

internal data class WorldMetadataUpdateResponse(
    val result: Result<WorldData>,
    val sessionToken: AccountSessionToken,
)

internal interface WorldEditor {
    suspend fun updateMetadata(
        sessionToken: AccountSessionToken,
        worldId: String,
        update: WorldUpdateData,
    ): WorldMetadataUpdateResponse?
}

internal class NetworkWorldEditor(
    private val worldsApi: WorldsApi,
    private val authService: AuthService,
) : WorldEditor {
    override suspend fun updateMetadata(
        sessionToken: AccountSessionToken,
        worldId: String,
        update: WorldUpdateData,
    ): WorldMetadataUpdateResponse? = authService.runSessionBoundCatching(sessionToken) {
        worldsApi.updateWorld(worldId, update)
    }?.let { response ->
        WorldMetadataUpdateResponse(response.result, response.sessionToken)
    }
}

internal data class WorldMetadataDraft(
    val name: String,
    val description: String,
    val capacity: String,
    val recommendedCapacity: String,
    val tags: String,
    val allowedDomains: String,
)

internal sealed interface WorldMetadataChange {
    data object InvalidName : WorldMetadataChange
    data object InvalidCapacity : WorldMetadataChange
    data object InvalidRecommendedCapacity : WorldMetadataChange
    data object NoChanges : WorldMetadataChange
    data class Update(val data: WorldUpdateData) : WorldMetadataChange
}

internal fun worldMetadataChange(
    current: WorldProfileVo,
    draft: WorldMetadataDraft,
): WorldMetadataChange {
    val name = draft.name.trim()
    if (name.isEmpty()) return WorldMetadataChange.InvalidName

    val capacity = draft.capacity.trim().toIntOrNull()
        ?.takeIf { it in 0..40 }
        ?: return WorldMetadataChange.InvalidCapacity
    val recommendedCapacity = draft.recommendedCapacity.trim().toIntOrNull()
        ?.takeIf { it in 0..capacity }
        ?: return WorldMetadataChange.InvalidRecommendedCapacity
    val tags = draft.tags.normalizedMetadataList()
    val allowedDomains = draft.allowedDomains.normalizedMetadataList()

    val update = WorldUpdateData(
        name = name.takeIf { it != current.worldName },
        description = draft.description.takeIf { it != current.worldDescription },
        capacity = capacity.takeIf { it != current.capacity },
        recommendedCapacity = recommendedCapacity.takeIf {
            it != current.recommendedCapacity
        },
        tags = tags.takeIf { it != current.rawTags },
        urlList = allowedDomains.takeIf { it != current.allowedDomains },
    )
    return if (update == WorldUpdateData()) {
        WorldMetadataChange.NoChanges
    } else {
        WorldMetadataChange.Update(update)
    }
}

private fun String.normalizedMetadataList(): List<String> =
    split(',', '\n')
        .map(String::trim)
        .filter(String::isNotEmpty)
        .distinct()

internal data class WorldMetadataEditState(
    val canEdit: Boolean = false,
    val isSaving: Boolean = false,
)

internal sealed interface WorldMetadataEditNotice {
    data object InvalidName : WorldMetadataEditNotice
    data object InvalidCapacity : WorldMetadataEditNotice
    data object InvalidRecommendedCapacity : WorldMetadataEditNotice
    data object NoChanges : WorldMetadataEditNotice
    data object Saved : WorldMetadataEditNotice
    data class SaveFailed(val message: String?) : WorldMetadataEditNotice
}

/** Owns the session- and target-bound lifecycle of world metadata mutations. */
internal class WorldMetadataEditStateModel(
    private val editor: WorldEditor,
    private val scope: CoroutineScope,
    private val world: StateFlow<WorldProfileVo?>,
    private val metadataReady: StateFlow<Boolean>,
    private val session: StateFlow<AuthenticatedAccount?>,
    private val onAcceptedUpdate: (WorldProfileVo, WorldData) -> Unit,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val saving = MutableStateFlow(false)
    private val _notices = MutableSharedFlow<WorldMetadataEditNotice>(extraBufferCapacity = 1)
    val notices: SharedFlow<WorldMetadataEditNotice> = _notices.asSharedFlow()
    val state: StateFlow<WorldMetadataEditState> = combine(
        world,
        metadataReady,
        session,
        saving,
    ) { currentWorld, isReady, currentSession, isSaving ->
        WorldMetadataEditState(
            canEdit = isReady &&
                currentWorld?.authorID?.isNotBlank() == true &&
                currentWorld.authorID == currentSession?.token?.userId,
            isSaving = isSaving,
        )
    }.stateIn(
        scope = scope,
        started = SharingStarted.Eagerly,
        initialValue = WorldMetadataEditState(),
    )

    private val saveGeneration = atomic(0L)
    private var saveJob: Job? = null

    init {
        scope.launch {
            session.map { it?.token?.userId }
                .distinctUntilChanged()
                .drop(1)
                .collect { invalidate() }
        }
    }

    fun invalidate() {
        saveGeneration.incrementAndGet()
        saveJob?.cancel()
        saveJob = null
        saving.value = false
    }

    fun save(draft: WorldMetadataDraft) {
        val currentWorld = world.value ?: return
        val currentSession = session.value ?: return
        if (!metadataReady.value || currentWorld.authorID != currentSession.token.userId) return

        when (val change = worldMetadataChange(currentWorld, draft)) {
            WorldMetadataChange.InvalidName ->
                _notices.tryEmit(WorldMetadataEditNotice.InvalidName)
            WorldMetadataChange.InvalidCapacity ->
                _notices.tryEmit(WorldMetadataEditNotice.InvalidCapacity)
            WorldMetadataChange.InvalidRecommendedCapacity ->
                _notices.tryEmit(WorldMetadataEditNotice.InvalidRecommendedCapacity)
            WorldMetadataChange.NoChanges ->
                _notices.tryEmit(WorldMetadataEditNotice.NoChanges)
            is WorldMetadataChange.Update -> startSave(
                currentWorld = currentWorld,
                sessionToken = currentSession.token,
                update = change.data,
            )
        }
    }

    private fun startSave(
        currentWorld: WorldProfileVo,
        sessionToken: AccountSessionToken,
        update: WorldUpdateData,
    ) {
        if (!saving.compareAndSet(expect = false, update = true)) return
        val generation = saveGeneration.incrementAndGet()
        val worldId = currentWorld.worldId
        saveJob = scope.launch(dispatcher) {
            try {
                val response = editor.updateMetadata(sessionToken, worldId, update) ?: return@launch
                if (!accepts(response.sessionToken, worldId)) return@launch

                response.result.fold(
                    onSuccess = { updated ->
                        if (updated.id != worldId || !accepts(response.sessionToken, worldId)) {
                            return@fold
                        }
                        val latest = world.value ?: return@fold
                        onAcceptedUpdate(latest, updated)
                        _notices.tryEmit(WorldMetadataEditNotice.Saved)
                    },
                    onFailure = { error ->
                        if (error is CancellationException) throw error
                        if (accepts(response.sessionToken, worldId)) {
                            _notices.tryEmit(WorldMetadataEditNotice.SaveFailed(error.message))
                        }
                    },
                )
            } finally {
                if (saveGeneration.value == generation) {
                    saveJob = null
                    saving.value = false
                }
            }
        }
    }

    private fun accepts(sessionToken: AccountSessionToken, worldId: String): Boolean =
        session.value?.token == sessionToken &&
            world.value?.let { current ->
                current.worldId == worldId && current.authorID == sessionToken.userId
            } == true
}
