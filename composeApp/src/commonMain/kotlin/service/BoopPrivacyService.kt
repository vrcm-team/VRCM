package io.github.vrcmteam.vrcm.service

import io.github.vrcmteam.vrcm.core.shared.AccountSessionToken
import io.github.vrcmteam.vrcm.core.shared.SharedFlowCentre
import io.github.vrcmteam.vrcm.network.api.users.UsersApi
import io.github.vrcmteam.vrcm.network.api.users.data.UpdateUserInfoData
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Result of an account-bound privacy update. */
sealed interface BoopPrivacyUpdateResult {
    data class Updated(val isEnabled: Boolean) : BoopPrivacyUpdateResult
    data object Unchanged : BoopPrivacyUpdateResult
    data object Unavailable : BoopPrivacyUpdateResult
    data object InFlight : BoopPrivacyUpdateResult
    data object SessionChanged : BoopPrivacyUpdateResult
    data class Failed(val error: Throwable) : BoopPrivacyUpdateResult
}

internal data class BoopPrivacyAccountSnapshot(
    val sessionToken: AccountSessionToken,
    val userId: String,
    val isEnabled: Boolean,
)

internal interface BoopPrivacyAccountAccess {
    fun snapshot(): BoopPrivacyAccountSnapshot?

    fun isCurrentSession(sessionToken: AccountSessionToken): Boolean

    fun applyServerUpdate(
        sessionToken: AccountSessionToken,
        userId: String,
        isEnabled: Boolean,
    ): Boolean
}

internal class AuthBoopPrivacyAccountAccess(
    private val authService: AuthService,
) : BoopPrivacyAccountAccess {
    override fun snapshot(): BoopPrivacyAccountSnapshot? {
        val session = SharedFlowCentre.currentSession.value ?: return null
        val currentUser = authService.currentUserState.value ?: return null
        if (currentUser.id != session.account.userId) return null
        return BoopPrivacyAccountSnapshot(
            sessionToken = session.token,
            userId = currentUser.id,
            isEnabled = currentUser.isBoopingEnabled != false,
        )
    }

    override fun isCurrentSession(sessionToken: AccountSessionToken): Boolean =
        SharedFlowCentre.isCurrentSession(sessionToken)

    override fun applyServerUpdate(
        sessionToken: AccountSessionToken,
        userId: String,
        isEnabled: Boolean,
    ): Boolean = authService.applyBoopPrivacyUpdate(sessionToken, userId, isEnabled)
}

internal data class BoopPrivacyServerUpdate(
    val userId: String,
    val isEnabled: Boolean,
)

internal data class AuthenticatedBoopPrivacyResponse(
    val result: Result<BoopPrivacyServerUpdate>,
    val sessionToken: AccountSessionToken,
)

internal fun interface BoopPrivacyRequest {
    suspend fun update(
        sessionToken: AccountSessionToken,
        userId: String,
        isEnabled: Boolean,
    ): AuthenticatedBoopPrivacyResponse?
}

internal class NetworkBoopPrivacyRequest(
    private val authService: AuthService,
    private val usersApi: UsersApi,
) : BoopPrivacyRequest {
    override suspend fun update(
        sessionToken: AccountSessionToken,
        userId: String,
        isEnabled: Boolean,
    ): AuthenticatedBoopPrivacyResponse? {
        val response = authService.runSessionBoundCatching(sessionToken) {
            usersApi.updateUserInfo(
                userId = userId,
                updateUserInfoData = UpdateUserInfoData(isBoopingEnabled = isEnabled),
            )
        } ?: return null
        return AuthenticatedBoopPrivacyResponse(
            result = response.result.mapCatching { updatedUser ->
                check(updatedUser.id == userId) {
                    "Privacy update returned a different user"
                }
                BoopPrivacyServerUpdate(
                    userId = updatedUser.id,
                    isEnabled = checkNotNull(updatedUser.isBoopingEnabled) {
                        "Privacy update response omitted the server value"
                    },
                )
            },
            sessionToken = response.sessionToken,
        )
    }
}

private data class ActiveBoopPrivacyMutation(
    val id: Long,
    val sessionToken: AccountSessionToken,
    val userId: String,
)

/** Applies the server response only while its authenticated account is still current. */
class BoopPrivacyService internal constructor(
    private val accountAccess: BoopPrivacyAccountAccess,
    private val request: BoopPrivacyRequest,
) {
    internal constructor(
        authService: AuthService,
        request: BoopPrivacyRequest,
    ) : this(AuthBoopPrivacyAccountAccess(authService), request)

    private val mutationLock = SynchronizedObject()
    private var nextMutationId = 0L
    private var activeMutation: ActiveBoopPrivacyMutation? = null
    private val _updatingUserId = MutableStateFlow<String?>(null)
    val updatingUserId: StateFlow<String?> = _updatingUserId.asStateFlow()

    suspend fun update(isEnabled: Boolean): BoopPrivacyUpdateResult {
        val snapshot = accountAccess.snapshot() ?: return BoopPrivacyUpdateResult.Unavailable
        if (snapshot.isEnabled == isEnabled) return BoopPrivacyUpdateResult.Unchanged
        val mutation = startMutation(snapshot) ?: return BoopPrivacyUpdateResult.InFlight

        return try {
            val response = request.update(
                sessionToken = snapshot.sessionToken,
                userId = snapshot.userId,
                isEnabled = isEnabled,
            ) ?: return completeAsSessionChanged(mutation)

            response.result.fold(
                onSuccess = { update -> completeSuccess(mutation, response.sessionToken, update) },
                onFailure = { error -> completeFailure(mutation, response.sessionToken, error) },
            )
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            completeFailure(mutation, snapshot.sessionToken, error)
        } finally {
            finishMutation(mutation)
        }
    }

    private fun startMutation(snapshot: BoopPrivacyAccountSnapshot): ActiveBoopPrivacyMutation? =
        synchronized(mutationLock) {
            val active = activeMutation
            if (active?.sessionToken == snapshot.sessionToken) return@synchronized null
            ActiveBoopPrivacyMutation(
                id = ++nextMutationId,
                sessionToken = snapshot.sessionToken,
                userId = snapshot.userId,
            ).also { mutation ->
                activeMutation = mutation
                _updatingUserId.value = mutation.userId
            }
        }

    private fun completeSuccess(
        mutation: ActiveBoopPrivacyMutation,
        responseToken: AccountSessionToken,
        update: BoopPrivacyServerUpdate,
    ): BoopPrivacyUpdateResult = synchronized(mutationLock) {
        if (activeMutation != mutation ||
            update.userId != mutation.userId ||
            !accountAccess.isCurrentSession(responseToken) ||
            !accountAccess.applyServerUpdate(responseToken, update.userId, update.isEnabled)
        ) {
            return@synchronized BoopPrivacyUpdateResult.SessionChanged
        }
        clearMutationLocked(mutation)
        BoopPrivacyUpdateResult.Updated(update.isEnabled)
    }

    private fun completeFailure(
        mutation: ActiveBoopPrivacyMutation,
        responseToken: AccountSessionToken,
        error: Throwable,
    ): BoopPrivacyUpdateResult = synchronized(mutationLock) {
        if (activeMutation != mutation || !accountAccess.isCurrentSession(responseToken)) {
            return@synchronized BoopPrivacyUpdateResult.SessionChanged
        }
        clearMutationLocked(mutation)
        BoopPrivacyUpdateResult.Failed(error)
    }

    private fun completeAsSessionChanged(
        mutation: ActiveBoopPrivacyMutation,
    ): BoopPrivacyUpdateResult = synchronized(mutationLock) {
        clearMutationLocked(mutation)
        BoopPrivacyUpdateResult.SessionChanged
    }

    private fun finishMutation(mutation: ActiveBoopPrivacyMutation) = synchronized(mutationLock) {
        clearMutationLocked(mutation)
    }

    private fun clearMutationLocked(mutation: ActiveBoopPrivacyMutation) {
        if (activeMutation != mutation) return
        activeMutation = null
        _updatingUserId.value = null
    }
}
