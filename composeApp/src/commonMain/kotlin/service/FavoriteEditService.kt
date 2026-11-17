package io.github.vrcmteam.vrcm.service

import io.github.vrcmteam.vrcm.core.shared.AccountSessionToken
import io.github.vrcmteam.vrcm.core.shared.SharedFlowCentre
import io.github.vrcmteam.vrcm.network.api.attributes.FavoriteType
import io.github.vrcmteam.vrcm.network.api.favorite.data.FavoriteData
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal data class FavoriteEditRequest(
    val type: FavoriteType,
    val favoriteId: String,
    val originalRecordId: String?,
    val targetGroupName: String?,
)

/** Retries individual writes and publishes confirmed changes, including a partially completed move. */
internal class FavoriteEditService(
    private val authService: AuthService,
    private val favoriteService: FavoriteService,
) {
    private val editMutex = Mutex()

    suspend fun edit(sessionToken: AccountSessionToken, request: FavoriteEditRequest): Result<Unit> =
        editMutex.withLock { editLocked(sessionToken, request) }

    private suspend fun editLocked(sessionToken: AccountSessionToken, request: FavoriteEditRequest): Result<Unit> {
        var token = sessionToken
        var removed: FavoriteData? = null
        var added: FavoriteData? = null
        return try {
            check(SharedFlowCentre.isCurrentSession(token)) { "Authenticated session changed" }
            val groups = favoriteService.favoritesByGroup(request.type).value
            check(groups.keys.all { it.ownerId == "local" || it.ownerId == token.userId }) {
                "Favorite groups belong to a different account"
            }
            val original = groups.values.flatten().firstOrNull { it.id == request.originalRecordId }
            val target = request.targetGroupName?.let { name ->
                groups.keys.firstOrNull { it.name == name }
                    ?: error("Favorite group is unavailable")
            }
            val alreadyInTarget = target != null && groups.getValue(target).any {
                it.favoriteId == request.favoriteId
            }

            suspend fun remove() {
                if (original == null || (target != null && target.name in original.tags)) return
                val response = authService.runSessionBoundCatching(token) {
                    favoriteService.removeFavorite(original)
                } ?: error("Authenticated session changed")
                token = response.sessionToken
                response.result.getOrThrow()
                removed = original
                check(SharedFlowCentre.isCurrentSession(token)) { "Authenticated session changed" }
            }

            suspend fun add() {
                if (target == null || alreadyInTarget) return
                val response = authService.runSessionBoundCatching(token) {
                    favoriteService.addFavorite(request.favoriteId, request.type, target.name)
                } ?: error("Authenticated session changed")
                token = response.sessionToken
                added = response.result.getOrThrow()
                check(SharedFlowCentre.isCurrentSession(token)) { "Authenticated session changed" }
            }

            // Keep a local copy until the remote addition succeeds. Remote moves require deletion first.
            if (original?.id?.let(favoriteService::parseLocalFavoriteId)?.first == true) {
                add()
                remove()
            } else {
                remove()
                add()
            }
            check(SharedFlowCentre.isCurrentSession(token)) { "Authenticated session changed" }
            Result.success(Unit)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Result.failure(error)
        } finally {
            // A completed write must reach observers even if the next step fails or the sheet closes.
            withContext(NonCancellable) {
                if ((removed != null || added != null) && SharedFlowCentre.isCurrentSession(token)) {
                    favoriteService.commitFavoriteEdit(token, request.type, removed, added)
                }
            }
        }
    }
}
