package io.github.vrcmteam.vrcm.presentation.favorites

import io.github.vrcmteam.vrcm.network.api.attributes.FavoriteType
import io.github.vrcmteam.vrcm.network.api.favorite.data.FavoriteData
import io.github.vrcmteam.vrcm.network.api.favorite.data.FavoriteGroupData
import io.github.vrcmteam.vrcm.service.AuthService
import io.github.vrcmteam.vrcm.service.FavoriteService
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.launch

internal interface FavoriteEntrySource {
    fun favoritesByGroup(type: FavoriteType): StateFlow<Map<FavoriteGroupData, List<FavoriteData>>>

    suspend fun load(type: FavoriteType): Result<Unit>
}

internal class AuthenticatedFavoriteEntrySource(
    private val favoriteService: FavoriteService,
    private val authService: AuthService,
) : FavoriteEntrySource {
    override fun favoritesByGroup(type: FavoriteType) = favoriteService.favoritesByGroup(type)

    override suspend fun load(type: FavoriteType): Result<Unit> =
        authService.reTryAuth { favoriteService.loadFavoriteByGroup(type) }
}

internal sealed interface FavoriteEntryState {
    data object Loading : FavoriteEntryState
    data object Favorited : FavoriteEntryState
    data object NotFavorited : FavoriteEntryState
    data object LoadFailed : FavoriteEntryState
}

/**
 * Loads a favorite type once per requested target and keeps the entry state synchronized with
 * later group mutations from the shared favorite service.
 */
internal class FavoriteEntryStateModel(
    private val favoriteType: FavoriteType,
    private val source: FavoriteEntrySource,
    private val scope: CoroutineScope,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private enum class LoadState {
        Loading,
        Ready,
        Failed,
    }

    private val targetId = MutableStateFlow("")
    private val loadState = MutableStateFlow(LoadState.Loading)
    private val latestRequestToken = MutableStateFlow(0L)

    val state: StateFlow<FavoriteEntryState> = combine(
        targetId,
        loadState,
        source.favoritesByGroup(favoriteType),
    ) { currentTargetId, currentLoadState, favoritesByGroup ->
        when (currentLoadState) {
            LoadState.Loading -> FavoriteEntryState.Loading
            LoadState.Failed -> FavoriteEntryState.LoadFailed
            LoadState.Ready -> {
                val isFavorite = favoritesByGroup.values.any { favorites ->
                    favorites.any { favorite -> favorite.favoriteId == currentTargetId }
                }
                if (isFavorite) FavoriteEntryState.Favorited else FavoriteEntryState.NotFavorited
            }
        }
    }.stateIn(
        scope = scope,
        started = SharingStarted.Eagerly,
        initialValue = FavoriteEntryState.Loading,
    )

    fun load(favoriteId: String) {
        targetId.value = favoriteId
        if (favoriteId.isBlank()) {
            loadState.value = LoadState.Failed
            return
        }

        val requestToken = latestRequestToken.updateAndGet { it + 1 }
        loadState.value = LoadState.Loading
        scope.launch(dispatcher) {
            val result = source.load(favoriteType)
            if (requestToken == latestRequestToken.value) {
                loadState.value = if (result.isSuccess) LoadState.Ready else LoadState.Failed
            }
        }
    }

    fun retry() {
        load(targetId.value)
    }
}
