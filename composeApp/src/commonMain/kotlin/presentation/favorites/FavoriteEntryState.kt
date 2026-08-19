package io.github.vrcmteam.vrcm.presentation.favorites

import io.github.vrcmteam.vrcm.core.shared.AuthenticatedAccount
import io.github.vrcmteam.vrcm.core.shared.SharedFlowCentre
import io.github.vrcmteam.vrcm.network.api.attributes.FavoriteType
import io.github.vrcmteam.vrcm.network.api.favorite.data.FavoriteData
import io.github.vrcmteam.vrcm.network.api.favorite.data.FavoriteGroupData
import io.github.vrcmteam.vrcm.service.AuthService
import io.github.vrcmteam.vrcm.service.FavoriteService
import io.github.vrcmteam.vrcm.storage.FavoriteListCacheStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.launch

internal interface FavoriteEntrySource {
    fun favoritesByGroup(type: FavoriteType): StateFlow<Map<FavoriteGroupData, List<FavoriteData>>>

    suspend fun load(type: FavoriteType): Result<Unit>

    /**
     * Returns the cached membership for a target, or null when no complete list cache exists.
     * A false result is meaningful: an existing empty cache proves the target is not favorited.
     */
    suspend fun cachedFavorite(type: FavoriteType, favoriteId: String): Boolean? = null
}

internal class AuthenticatedFavoriteEntrySource(
    private val favoriteService: FavoriteService,
    private val authService: AuthService,
    private val favoriteListCacheStore: FavoriteListCacheStore,
) : FavoriteEntrySource {
    override fun favoritesByGroup(type: FavoriteType) = favoriteService.favoritesByGroup(type)

    override suspend fun load(type: FavoriteType): Result<Unit> =
        authService.reTryAuth { favoriteService.loadFavoriteByGroup(type) }

    override suspend fun cachedFavorite(type: FavoriteType, favoriteId: String): Boolean? {
        val userId = SharedFlowCentre.currentSession.value?.token?.userId ?: return null
        val cache = try {
            favoriteListCacheStore.load(userId)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            return null
        } ?: return null

        return when (type) {
            FavoriteType.World -> if (!cache.worldsLoaded) {
                null
            } else {
                cache.favoritedWorlds
                    .asSequence()
                    .flatMap { it.worlds.asSequence() }
                    .any { it.id == favoriteId }
            }

            FavoriteType.Avatar -> if (!cache.avatarsLoaded) {
                null
            } else {
                cache.favoritedAvatars.any { it.id == favoriteId }
            }
            FavoriteType.Friend -> null
        }
    }
}

internal sealed interface FavoriteEntryState {
    data object Loading : FavoriteEntryState
    data object Favorited : FavoriteEntryState
    data object NotFavorited : FavoriteEntryState
    data object LoadFailed : FavoriteEntryState
    data object Unavailable : FavoriteEntryState
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
    private val sessionFlow: StateFlow<AuthenticatedAccount?> = SharedFlowCentre.currentSession,
) {
    private enum class LoadState {
        Loading,
        Ready,
        Failed,
        Unavailable,
    }

    private val targetId = MutableStateFlow("")
    private val loadState = MutableStateFlow(LoadState.Loading)
    private val latestRequestToken = MutableStateFlow(0L)
    private val cachedFavorite = MutableStateFlow<Boolean?>(null)
    private var observedSessionToken = sessionFlow.value?.token

    init {
        scope.launch {
            sessionFlow.collect { session ->
                val sessionToken = session?.token
                if (sessionToken != observedSessionToken) {
                    observedSessionToken = sessionToken
                    targetId.value.takeIf { it.isNotBlank() }?.let { favoriteId ->
                        load(favoriteId, allowCurrentGroups = false)
                    }
                }
            }
        }
    }

    val state: StateFlow<FavoriteEntryState> = combine(
        targetId,
        loadState,
        source.favoritesByGroup(favoriteType),
        cachedFavorite,
    ) { currentTargetId, currentLoadState, favoritesByGroup, cachedMembership ->
        when (currentLoadState) {
            LoadState.Loading -> FavoriteEntryState.Loading
            LoadState.Failed -> FavoriteEntryState.LoadFailed
            LoadState.Unavailable -> FavoriteEntryState.Unavailable
            LoadState.Ready -> {
                val isFavorite = if (favoritesByGroup.isEmpty() && cachedMembership != null) {
                    cachedMembership
                } else {
                    favoritesByGroup.values.any { favorites ->
                        favorites.any { favorite -> favorite.favoriteId == currentTargetId }
                    }
                }
                if (isFavorite) FavoriteEntryState.Favorited else FavoriteEntryState.NotFavorited
            }
        }
    }.stateIn(
        scope = scope,
        started = SharingStarted.Eagerly,
        initialValue = FavoriteEntryState.Loading,
    )

    fun load(favoriteId: String) = load(favoriteId, allowCurrentGroups = true)

    private fun load(favoriteId: String, allowCurrentGroups: Boolean) {
        targetId.value = favoriteId
        // 每次切换目标都提升请求代次，之前发出的加载结果不能再改写新目标的状态。
        val requestToken = latestRequestToken.updateAndGet { it + 1 }
        if (favoriteId.isBlank()) {
            cachedFavorite.value = null
            loadState.value = LoadState.Unavailable
            return
        }

        val currentFavorites = source.favoritesByGroup(favoriteType).value
        if (allowCurrentGroups && currentFavorites.isNotEmpty()) {
            cachedFavorite.value = null
            loadState.value = LoadState.Ready
            return
        }

        val requestSessionToken = sessionFlow.value?.token
        cachedFavorite.value = null
        loadState.value = LoadState.Loading
        scope.launch(dispatcher) {
            val cachedMembership = source.cachedFavorite(favoriteType, favoriteId)
            if (requestToken != latestRequestToken.value) return@launch
            if (requestSessionToken != sessionFlow.value?.token) {
                load(favoriteId, allowCurrentGroups = false)
                return@launch
            }
            if (cachedMembership != null) {
                cachedFavorite.value = cachedMembership
                loadState.value = LoadState.Ready
                return@launch
            }

            val result = source.load(favoriteType)
            if (requestToken == latestRequestToken.value) {
                cachedFavorite.value = null
                loadState.value = if (result.isSuccess) LoadState.Ready else LoadState.Failed
            }
        }
    }

    fun retry() {
        load(targetId.value)
    }
}
