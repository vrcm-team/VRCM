package io.github.vrcmteam.vrcm.service

import io.github.vrcmteam.vrcm.core.shared.AccountSessionToken
import io.github.vrcmteam.vrcm.core.shared.SharedFlowCentre
import io.github.vrcmteam.vrcm.network.api.users.UsersApi
import io.github.vrcmteam.vrcm.network.api.users.data.UpdateUserInfoData
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

internal data class HomeWorldUserContext(
    val sessionToken: AccountSessionToken,
    val homeLocation: String,
)

internal interface HomeWorldManager {
    val currentUser: Flow<HomeWorldUserContext?>

    suspend fun setHomeWorld(worldId: String): Result<String>

    suspend fun resetHomeWorld(
        expectedWorldId: String,
        expectedSessionToken: AccountSessionToken,
    ): Result<String>
}

internal class HomeWorldSessionChangedException : IllegalStateException(
    "The authenticated account changed while updating the Home World"
)

internal class HomeWorldStateChangedException : IllegalStateException(
    "The Home World changed before the reset could be submitted"
)

internal class InvalidHomeWorldException : IllegalArgumentException("Invalid VRChat world ID")

internal class HomeWorldUpdateInFlightException : IllegalStateException(
    "A Home World update is already in progress"
)

/** Updates the authenticated user's Home World without allowing stale sessions to publish state. */
internal class HomeWorldService(
    private val usersApi: UsersApi,
    private val authService: AuthService,
) : HomeWorldManager {
    private val updateInFlight = atomic(false)

    override val currentUser: Flow<HomeWorldUserContext?> = combine(
        authService.currentUserState,
        SharedFlowCentre.currentSession,
    ) { user, session ->
        if (user == null || session == null || user.id != session.account.userId) {
            null
        } else {
            HomeWorldUserContext(
                sessionToken = session.token,
                homeLocation = user.homeLocation,
            )
        }
    }

    override suspend fun setHomeWorld(worldId: String): Result<String> {
        if (!isWorldId(worldId)) return Result.failure(InvalidHomeWorldException())
        return updateHomeLocation(worldId.trim())
    }

    override suspend fun resetHomeWorld(
        expectedWorldId: String,
        expectedSessionToken: AccountSessionToken,
    ): Result<String> {
        if (!isWorldId(expectedWorldId)) {
            return Result.failure(InvalidHomeWorldException())
        }
        return updateHomeLocation(
            homeLocation = "",
            expectedWorldId = expectedWorldId.trim(),
            expectedSessionToken = expectedSessionToken,
        )
    }

    private suspend fun updateHomeLocation(
        homeLocation: String,
        expectedWorldId: String? = null,
        expectedSessionToken: AccountSessionToken? = null,
    ): Result<String> {
        if (!updateInFlight.compareAndSet(expect = false, update = true)) {
            return Result.failure(HomeWorldUpdateInFlightException())
        }
        return try {
            performHomeWorldUpdate(
                homeLocation = homeLocation,
                expectedWorldId = expectedWorldId,
                expectedSessionToken = expectedSessionToken,
            )
        } finally {
            updateInFlight.value = false
        }
    }

    private suspend fun performHomeWorldUpdate(
        homeLocation: String,
        expectedWorldId: String?,
        expectedSessionToken: AccountSessionToken?,
    ): Result<String> {
        val session = SharedFlowCentre.currentSession.value?.takeIf { current ->
            expectedSessionToken == null || current.token == expectedSessionToken
        }
            ?: return Result.failure(HomeWorldSessionChangedException())
        val response = authService.runSessionBoundCatching(session.token) {
            authService.withHomeWorldMutation {
                if (expectedWorldId != null && expectedSessionToken != null &&
                    !authService.isCurrentUserHomeWorld(
                        sessionToken = expectedSessionToken,
                        expectedWorldId = expectedWorldId,
                    )
                ) {
                    throw HomeWorldStateChangedException()
                }
                usersApi.updateUserInfo(
                    userId = session.account.userId,
                    updateUserInfoData = UpdateUserInfoData(homeLocation = homeLocation),
                )
            }
        } ?: return Result.failure(HomeWorldSessionChangedException())

        return authService.withHomeWorldMutation {
            response.result.mapCatching { updatedUser ->
                check(updatedUser.id == response.sessionToken.userId) {
                    "Home World update returned a different user"
                }
                if (!authService.applyCurrentUserHomeLocationLocked(
                        sessionToken = response.sessionToken,
                        userId = updatedUser.id,
                        homeLocation = updatedUser.homeLocation,
                    )
                ) {
                    throw HomeWorldSessionChangedException()
                }
                updatedUser.homeLocation
            }
        }
    }
}

internal fun isWorldId(value: String): Boolean {
    val target = parseOfficialId(value)
    return target?.type == OfficialLinkType.World && target.id == value.trim()
}
