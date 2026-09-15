package io.github.vrcmteam.vrcm.service

import io.github.vrcmteam.vrcm.core.shared.AccountSessionToken
import io.github.vrcmteam.vrcm.core.shared.SharedFlowCentre
import io.github.vrcmteam.vrcm.network.api.attributes.FavoriteType
import io.github.vrcmteam.vrcm.network.api.favorite.data.FavoriteData
import kotlinx.coroutines.CancellationException

internal data class FavoriteRemovalSelection(
    val selectionId: String,
    val favoriteType: FavoriteType,
    val favorite: FavoriteData,
)

internal data class FavoriteRemovalResult(
    val selection: FavoriteRemovalSelection,
    val errorMessage: String? = null,
) {
    val succeeded: Boolean get() = errorMessage == null
}

internal data class FavoriteBulkRemovalResponse(
    val results: List<FavoriteRemovalResult>,
    val sessionToken: AccountSessionToken,
)

internal interface FavoriteRemovalCall {
    suspend fun remove(
        sessionToken: AccountSessionToken,
        selection: FavoriteRemovalSelection,
    ): SessionBoundResponse<Unit>?
}

private class NetworkFavoriteRemovalCall(
    private val authService: AuthService,
    private val favoriteService: FavoriteService,
) : FavoriteRemovalCall {
    override suspend fun remove(
        sessionToken: AccountSessionToken,
        selection: FavoriteRemovalSelection,
    ): SessionBoundResponse<Unit>? {
        val response = authService.runSessionBoundCatching(sessionToken) {
            favoriteService.removeFavorite(selection.favorite)
        } ?: return null
        if (response.result.isFailure) return response

        val commitResult = try {
            favoriteService.commitFavoriteRemoval(
                sessionToken = response.sessionToken,
                favoriteType = selection.favoriteType,
                favorite = selection.favorite,
            )
            Result.success(Unit)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            Result.failure(error)
        }
        return SessionBoundResponse(commitResult, response.sessionToken)
    }
}

/** Executes favorite removals sequentially so an authentication renewal can be reused safely. */
internal class FavoriteBulkRemovalService(
    private val call: FavoriteRemovalCall,
    private val isCurrentSession: (AccountSessionToken) -> Boolean,
) {
    constructor(authService: AuthService, favoriteService: FavoriteService) : this(
        call = NetworkFavoriteRemovalCall(authService, favoriteService),
        isCurrentSession = SharedFlowCentre::isCurrentSession,
    )

    suspend fun remove(
        sessionToken: AccountSessionToken,
        selections: List<FavoriteRemovalSelection>,
        onResult: (FavoriteRemovalResult) -> Unit = {},
    ): FavoriteBulkRemovalResponse? {
        var currentToken = sessionToken
        val results = mutableListOf<FavoriteRemovalResult>()
        selections.distinctBy { it.favoriteType to it.selectionId }.forEach { selection ->
            val response = try {
                call.remove(currentToken, selection)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                SessionBoundResponse(Result.failure(error), currentToken)
            } ?: return null
            if (!isCurrentSession(response.sessionToken)) return null

            currentToken = response.sessionToken
            val result = FavoriteRemovalResult(
                selection = selection,
                errorMessage = response.result.exceptionOrNull()?.message
                    ?.ifBlank { "Request failed" }
                    ?: if (response.result.isFailure) "Request failed" else null,
            )
            results += result
            onResult(result)
        }
        return FavoriteBulkRemovalResponse(results, currentToken)
    }
}
