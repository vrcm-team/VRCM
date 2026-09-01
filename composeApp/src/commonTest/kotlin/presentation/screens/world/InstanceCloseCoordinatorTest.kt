package io.github.vrcmteam.vrcm.presentation.screens.world

import io.github.vrcmteam.vrcm.core.shared.AccountSessionToken
import io.github.vrcmteam.vrcm.network.api.attributes.RegionType
import io.github.vrcmteam.vrcm.network.api.instances.data.InstanceCloseResponse
import io.github.vrcmteam.vrcm.network.api.instances.data.InstanceData
import io.github.vrcmteam.vrcm.network.api.instances.data.Platforms
import io.github.vrcmteam.vrcm.network.api.worlds.data.WorldData
import io.github.vrcmteam.vrcm.network.supports.VRCApiException
import io.github.vrcmteam.vrcm.presentation.screens.world.data.InstanceVo
import io.github.vrcmteam.vrcm.presentation.screens.world.data.WorldProfileVo
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class InstanceCloseCoordinatorTest {
    @Test
    fun personalInstanceRequiresTheCurrentOwner() = runTest {
        var currentToken: AccountSessionToken? = OWNER_TOKEN
        val coordinator = coordinator(currentSession = { currentToken })

        assertIs<InstanceCloseAuthorizationResult.NotAllowed>(
            coordinator.authorize(personalTarget(ownerId = OTHER_USER_ID))
        )
        assertIs<InstanceCloseState.Idle>(coordinator.state.value)

        assertIs<InstanceCloseAuthorizationResult.Ready>(
            coordinator.authorize(personalTarget(ownerId = OWNER_ID))
        )
        assertEquals(
            OWNER_TOKEN,
            assertIs<InstanceCloseState.AwaitingConfirmation>(coordinator.state.value)
                .request.sessionToken,
        )
    }

    @Test
    fun groupPermissionAndWildcardAllowConfirmation() = runTest {
        listOf(listOf(GROUP_INSTANCE_MANAGE_PERMISSION), listOf("*")).forEach { permissions ->
            var currentToken: AccountSessionToken? = OWNER_TOKEN
            val coordinator = coordinator(
                currentSession = { currentToken },
                groupPermissions = { token, _ ->
                    InstanceCloseSessionResult(Result.success(permissions), token)
                },
            )

            assertIs<InstanceCloseAuthorizationResult.Ready>(
                coordinator.authorize(groupTarget())
            )
            assertIs<InstanceCloseState.AwaitingConfirmation>(coordinator.state.value)
        }
    }

    @Test
    fun missingGroupPermissionDoesNotOpenConfirmation() = runTest {
        var currentToken: AccountSessionToken? = OWNER_TOKEN
        val coordinator = coordinator(
            currentSession = { currentToken },
            groupPermissions = { token, _ ->
                InstanceCloseSessionResult(Result.success(listOf("group-instance-view")), token)
            },
        )

        assertIs<InstanceCloseAuthorizationResult.NotAllowed>(
            coordinator.authorize(groupTarget())
        )
        assertIs<InstanceCloseState.Idle>(coordinator.state.value)
    }

    @Test
    fun repeatedConfirmationOnlySubmitsOnce() = runTest {
        var currentToken: AccountSessionToken? = OWNER_TOKEN
        val closeStarted = CompletableDeferred<Unit>()
        val finishClose = CompletableDeferred<Unit>()
        var closeCount = 0
        val coordinator = coordinator(
            currentSession = { currentToken },
            closeInstance = { token, _ ->
                closeCount++
                closeStarted.complete(Unit)
                finishClose.await()
                InstanceCloseSessionResult(Result.success(closedInstance()), token)
            },
        )
        coordinator.authorize(personalTarget())

        val first = async(start = CoroutineStart.UNDISPATCHED) { coordinator.submit() }
        closeStarted.await()

        assertIs<InstanceCloseSubmissionResult.Busy>(coordinator.submit())
        assertEquals(1, closeCount)

        finishClose.complete(Unit)
        assertIs<InstanceCloseSubmissionResult.Closed>(first.await())
        assertIs<InstanceCloseState.Idle>(coordinator.state.value)
    }

    @Test
    fun permissionFailureKeepsConfirmationRetryable() = runTest {
        var currentToken: AccountSessionToken? = OWNER_TOKEN
        var reject = true
        val coordinator = coordinator(
            currentSession = { currentToken },
            closeInstance = { token, _ ->
                val result = if (reject) {
                    Result.failure(VRCApiException("Forbidden", 403, "permission denied"))
                } else {
                    Result.success(closedInstance())
                }
                InstanceCloseSessionResult(result, token)
            },
        )
        coordinator.authorize(personalTarget())

        val failed = assertIs<InstanceCloseSubmissionResult.Failed>(coordinator.submit())
        assertEquals(403, assertIs<VRCApiException>(failed.error).code)
        assertIs<InstanceCloseState.AwaitingConfirmation>(coordinator.state.value)

        reject = false
        assertIs<InstanceCloseSubmissionResult.Closed>(coordinator.submit())
        assertIs<InstanceCloseState.Idle>(coordinator.state.value)
    }

    @Test
    fun forbiddenCloseConvergesToSuccessWhenRecheckShowsClosed() = runTest {
        var currentToken: AccountSessionToken? = OWNER_TOKEN
        var recheckCount = 0
        val coordinator = coordinator(
            currentSession = { currentToken },
            fetchInstance = { token, _ ->
                recheckCount++
                InstanceCloseSessionResult(Result.success(closedInstance()), token)
            },
            closeInstance = { token, _ ->
                InstanceCloseSessionResult(
                    Result.failure(VRCApiException("Forbidden", 403, "already closed")),
                    token,
                )
            },
        )
        coordinator.authorize(personalTarget())

        assertIs<InstanceCloseSubmissionResult.Closed>(coordinator.submit())
        assertEquals(1, recheckCount)
        assertIs<InstanceCloseState.Idle>(coordinator.state.value)
    }

    @Test
    fun forbiddenCloseRemainsRetryableWhenRecheckShowsActive() = runTest {
        var currentToken: AccountSessionToken? = OWNER_TOKEN
        val coordinator = coordinator(
            currentSession = { currentToken },
            fetchInstance = { token, _ ->
                InstanceCloseSessionResult(
                    Result.success(closedInstance(active = true, closedAt = null)),
                    token,
                )
            },
            closeInstance = { token, _ ->
                InstanceCloseSessionResult(
                    Result.failure(VRCApiException("Forbidden", 403, "permission denied")),
                    token,
                )
            },
        )
        coordinator.authorize(personalTarget())

        val failed = assertIs<InstanceCloseSubmissionResult.Failed>(coordinator.submit())

        assertEquals(403, assertIs<VRCApiException>(failed.error).code)
        assertIs<InstanceCloseState.AwaitingConfirmation>(coordinator.state.value)
    }

    @Test
    fun forbiddenCloseRemainsRetryableWhenRecheckOmitsCloseState() = runTest {
        var currentToken: AccountSessionToken? = OWNER_TOKEN
        val coordinator = coordinator(
            currentSession = { currentToken },
            fetchInstanceResponse = { token, _ ->
                InstanceCloseSessionResult(
                    Result.success(
                        closedInstance(active = true, closedAt = null)
                            .asInstanceCloseResponse()
                            .copy(active = null, closedAt = null),
                    ),
                    token,
                )
            },
            closeInstance = { token, _ ->
                InstanceCloseSessionResult(
                    Result.failure(VRCApiException("Forbidden", 403, "permission denied")),
                    token,
                )
            },
        )
        coordinator.authorize(personalTarget())

        val failed = assertIs<InstanceCloseSubmissionResult.Failed>(coordinator.submit())

        assertEquals(403, assertIs<VRCApiException>(failed.error).code)
        assertIs<InstanceCloseState.AwaitingConfirmation>(coordinator.state.value)
    }

    @Test
    fun authenticationRenewalCanCompleteTheOriginalRequest() = runTest {
        var currentToken: AccountSessionToken? = OWNER_TOKEN
        val renewedToken = AccountSessionToken(OWNER_ID, generation = 2)
        lateinit var coordinator: InstanceCloseCoordinator
        coordinator = coordinator(
            currentSession = { currentToken },
            closeInstance = { token, _ ->
                assertEquals(OWNER_TOKEN, token)
                currentToken = renewedToken
                coordinator.onSessionChanged(renewedToken)
                InstanceCloseSessionResult(Result.success(closedInstance()), renewedToken)
            },
        )
        coordinator.authorize(personalTarget())

        val closed = assertIs<InstanceCloseSubmissionResult.Closed>(coordinator.submit())

        assertEquals(renewedToken, closed.request.sessionToken)
        assertIs<InstanceCloseState.Idle>(coordinator.state.value)
    }

    @Test
    fun authenticationRenewalDuringAuthorizationCanOpenConfirmation() = runTest {
        var currentToken: AccountSessionToken? = OWNER_TOKEN
        val renewedToken = AccountSessionToken(OWNER_ID, generation = 2)
        lateinit var coordinator: InstanceCloseCoordinator
        coordinator = coordinator(
            currentSession = { currentToken },
            groupPermissions = { token, _ ->
                assertEquals(OWNER_TOKEN, token)
                currentToken = renewedToken
                coordinator.onSessionChanged(renewedToken)
                InstanceCloseSessionResult(
                    Result.success(listOf(GROUP_INSTANCE_MANAGE_PERMISSION)),
                    renewedToken,
                )
            },
        )

        assertIs<InstanceCloseAuthorizationResult.Ready>(coordinator.authorize(groupTarget()))
        assertEquals(
            renewedToken,
            assertIs<InstanceCloseState.AwaitingConfirmation>(coordinator.state.value)
                .request.sessionToken,
        )
    }

    @Test
    fun unrelatedSessionChangeDismissesIdleConfirmationImmediately() = runTest {
        var currentToken: AccountSessionToken? = OWNER_TOKEN
        val coordinator = coordinator(currentSession = { currentToken })
        coordinator.authorize(personalTarget())

        currentToken = AccountSessionToken(OWNER_ID, generation = 7)
        coordinator.onSessionChanged(currentToken)

        assertIs<InstanceCloseState.Idle>(coordinator.state.value)
    }

    @Test
    fun unrelatedSameAccountTokenReplacementPreventsSubmission() = runTest {
        var currentToken: AccountSessionToken? = OWNER_TOKEN
        var closeCalled = false
        val coordinator = coordinator(
            currentSession = { currentToken },
            closeInstance = { token, _ ->
                closeCalled = true
                InstanceCloseSessionResult(Result.success(closedInstance()), token)
            },
        )
        coordinator.authorize(personalTarget())
        currentToken = AccountSessionToken(OWNER_ID, generation = 99)

        assertIs<InstanceCloseSubmissionResult.SessionChanged>(coordinator.submit())
        assertFalse(closeCalled)
        assertIs<InstanceCloseState.Idle>(coordinator.state.value)
    }

    @Test
    fun logoutAndNullResponseClearPendingState() = runTest {
        var currentToken: AccountSessionToken? = OWNER_TOKEN
        val logoutCoordinator = coordinator(currentSession = { currentToken })
        logoutCoordinator.authorize(personalTarget())
        currentToken = null

        assertIs<InstanceCloseSubmissionResult.SessionChanged>(logoutCoordinator.submit())
        assertIs<InstanceCloseState.Idle>(logoutCoordinator.state.value)

        currentToken = OWNER_TOKEN
        val nullResponseCoordinator = coordinator(
            currentSession = { currentToken },
            closeInstance = { _, _ -> null },
        )
        nullResponseCoordinator.authorize(personalTarget())

        assertIs<InstanceCloseSubmissionResult.SessionChanged>(nullResponseCoordinator.submit())
        assertIs<InstanceCloseState.Idle>(nullResponseCoordinator.state.value)
    }

    @Test
    fun responseFromAnOldSessionCannotReportSuccess() = runTest {
        var currentToken: AccountSessionToken? = OWNER_TOKEN
        val otherToken = AccountSessionToken(OTHER_USER_ID, generation = 2)
        val coordinator = coordinator(
            currentSession = { currentToken },
            closeInstance = { token, _ ->
                currentToken = otherToken
                InstanceCloseSessionResult(Result.success(closedInstance()), token)
            },
        )
        coordinator.authorize(personalTarget())

        assertIs<InstanceCloseSubmissionResult.SessionChanged>(coordinator.submit())
        assertIs<InstanceCloseState.Idle>(coordinator.state.value)
    }

    @Test
    fun responseForAnotherLocationCannotReportSuccess() = runTest {
        var currentToken: AccountSessionToken? = OWNER_TOKEN
        val coordinator = coordinator(
            currentSession = { currentToken },
            closeInstance = { token, _ ->
                InstanceCloseSessionResult(
                    Result.success(closedInstance(location = "$WORLD_ID:other")),
                    token,
                )
            },
        )
        coordinator.authorize(personalTarget())

        val failed = assertIs<InstanceCloseSubmissionResult.Failed>(coordinator.submit())

        assertIs<InstanceCloseResponseMismatchException>(failed.error)
        assertIs<InstanceCloseState.AwaitingConfirmation>(coordinator.state.value)
    }

    @Test
    fun mismatchedRawLocationCannotBecomeARequestTarget() {
        val instance = InstanceVo(
            id = "unexpected",
            instanceId = INSTANCE_ID,
            worldId = WORLD_ID,
            location = "$WORLD_ID:other",
            ownerId = OWNER_ID,
        )

        assertNull(instance.closeTargetOrNull())
    }

    @Test
    fun successfulDeleteRemovesInstanceWhenCloseStatusFieldsAreOmitted() = runTest {
        val target = personalTarget()
        val profileState = MutableStateFlow<WorldProfileVo?>(
            WorldProfileVo(
                worldId = WORLD_ID,
                instances = listOf(InstanceVo(closedInstance(active = true, closedAt = null))),
            )
        )
        val response = closedInstance(active = true, closedAt = null)
            .asInstanceCloseResponse()
            .copy(active = null, closedAt = null)

        WorldInstanceStateStore(profileState).applyClose(target, response)

        assertTrue(profileState.value!!.instances.isEmpty())
    }

    private fun coordinator(
        currentSession: () -> AccountSessionToken?,
        groupPermissions: suspend (
            AccountSessionToken,
            String,
        ) -> InstanceCloseSessionResult<List<String>>? = { token, _ ->
            InstanceCloseSessionResult(Result.success(emptyList()), token)
        },
        fetchInstance: suspend (
            AccountSessionToken,
            InstanceCloseTarget,
        ) -> InstanceCloseSessionResult<InstanceData>? = { token, _ ->
            InstanceCloseSessionResult(
                Result.success(closedInstance(active = true, closedAt = null)),
                token,
            )
        },
        fetchInstanceResponse: suspend (
            AccountSessionToken,
            InstanceCloseTarget,
        ) -> InstanceCloseSessionResult<InstanceCloseResponse>? = { _, _ -> null },
        closeInstance: suspend (
            AccountSessionToken,
            InstanceCloseTarget,
        ) -> InstanceCloseSessionResult<InstanceData>? = { token, _ ->
            InstanceCloseSessionResult(Result.success(closedInstance()), token)
        },
    ) = InstanceCloseCoordinator(
        currentSessionToken = currentSession,
        isCurrentSession = { it == currentSession() },
        fetchGroupPermissions = groupPermissions,
        fetchInstance = { token, target ->
            fetchInstanceResponse(token, target)
                ?: fetchInstance(token, target)?.let { response ->
                    InstanceCloseSessionResult(
                        result = response.result.map { it.asInstanceCloseResponse() },
                        sessionToken = response.sessionToken,
                    )
                }
        },
        closeInstance = { token, target ->
            closeInstance(token, target)?.let { response ->
                InstanceCloseSessionResult(
                    result = response.result.map { it.asInstanceCloseResponse() },
                    sessionToken = response.sessionToken,
                )
            }
        },
    )

    private fun personalTarget(ownerId: String = OWNER_ID) = InstanceCloseTarget(
        worldId = WORLD_ID,
        instanceId = INSTANCE_ID,
        ownerId = ownerId,
    )

    private fun groupTarget() = InstanceCloseTarget(
        worldId = WORLD_ID,
        instanceId = INSTANCE_ID,
        ownerId = GROUP_ID,
    )

    private fun closedInstance(
        worldId: String = WORLD_ID,
        instanceId: String = INSTANCE_ID,
        location: String = "$worldId:$instanceId",
        active: Boolean = false,
        closedAt: String? = "2026-08-31T08:00:00.000Z",
    ) = InstanceData(
        active = active,
        canRequestInvite = false,
        capacity = 16,
        clientNumber = "1",
        closedAt = closedAt,
        full = false,
        hidden = null,
        id = location,
        instanceId = instanceId,
        location = location,
        nUsers = 0,
        name = "12345",
        ownerId = OWNER_ID,
        permanent = false,
        photonRegion = "us",
        platforms = Platforms(),
        queueEnabled = false,
        queueSize = 0,
        recommendedCapacity = 16,
        region = RegionType.Us,
        secureName = "secure",
        strict = false,
        tags = emptyList(),
        type = "private",
        userCount = 0,
        world = WorldData(
            authorId = OWNER_ID,
            authorName = "Author",
            capacity = 16,
            createdAt = null,
            description = null,
            favorites = 0,
            featured = false,
            heat = 0,
            id = worldId,
            imageUrl = "",
            labsPublicationDate = "",
            name = "World",
            namespace = null,
            organization = "",
            popularity = 0,
            publicationDate = "",
            recommendedCapacity = 16,
            releaseStatus = "public",
            tags = emptyList(),
            thumbnailImageUrl = null,
            udonProducts = emptyList(),
            unityPackages = emptyList(),
            updatedAt = null,
            version = 1,
            visits = 0,
        ),
        worldId = worldId,
    )

    private companion object {
        const val OWNER_ID = "usr_00000000-0000-0000-0000-000000000001"
        const val OTHER_USER_ID = "usr_00000000-0000-0000-0000-000000000002"
        const val GROUP_ID = "grp_00000000-0000-0000-0000-000000000003"
        const val WORLD_ID = "wrld_00000000-0000-0000-0000-000000000004"
        const val INSTANCE_ID = "12345~region(us)"
        val OWNER_TOKEN = AccountSessionToken(OWNER_ID, generation = 1)
    }
}
