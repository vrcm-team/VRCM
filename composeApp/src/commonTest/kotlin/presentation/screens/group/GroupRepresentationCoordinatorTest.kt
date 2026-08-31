package io.github.vrcmteam.vrcm.presentation.screens.group

import io.github.vrcmteam.vrcm.core.shared.AccountSessionToken
import io.github.vrcmteam.vrcm.network.api.groups.data.GroupData
import io.github.vrcmteam.vrcm.network.api.groups.data.MyMember
import io.github.vrcmteam.vrcm.presentation.screens.group.data.GroupProfileVo
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class GroupRepresentationCoordinatorTest {
    @Test
    fun onlyTheCurrentActiveMemberCanSubmit() = runTest {
        var requestCount = 0
        val coordinator = coordinator { _, _, _ ->
            requestCount++
            error("Request should not run")
        }
        val invalidProfiles = listOf(
            profile(groupStatus = "requested"),
            profile(memberStatus = "requested"),
            profile(memberGroupId = "grp_other"),
            profile(memberUserId = "usr_other"),
            GroupProfileVo(groupData().copy(myMember = null)),
        )

        invalidProfiles.forEach { group ->
            assertIs<GroupRepresentationUpdateResult.NotAllowed>(
                coordinator.update(group, isRepresenting = true, sessionToken = SESSION_TOKEN)
            )
        }

        assertEquals(0, requestCount)
    }

    @Test
    fun duplicateSubmissionRunsOnlyOneRequest() = runTest {
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        var requestCount = 0
        val coordinator = coordinator { token, _, _ ->
            requestCount++
            started.complete(Unit)
            release.await()
            successfulResponse(token, isRepresenting = true)
        }

        val first = async {
            coordinator.update(profile(), isRepresenting = true, sessionToken = SESSION_TOKEN)
        }
        started.await()
        val duplicate = coordinator.update(
            profile(),
            isRepresenting = true,
            sessionToken = SESSION_TOKEN,
        )
        release.complete(Unit)

        assertIs<GroupRepresentationUpdateResult.InFlight>(duplicate)
        assertIs<GroupRepresentationUpdateResult.Updated>(first.await())
        assertEquals(1, requestCount)
    }

    @Test
    fun refreshedStateMustMatchTheRequestedState() = runTest {
        val coordinator = coordinator { token, _, _ ->
            successfulResponse(token, isRepresenting = false)
        }

        val result = coordinator.update(
            group = profile(isRepresenting = false),
            isRepresenting = true,
            sessionToken = SESSION_TOKEN,
        )

        assertIs<GroupRepresentationUpdateResult.Failed>(result)
    }

    @Test
    fun failedRequestReleasesTheSubmissionGateForRetry() = runTest {
        var requestCount = 0
        val coordinator = coordinator { token, _, _ ->
            requestCount++
            if (requestCount == 1) {
                AuthenticatedGroupRepresentationResponse(
                    result = Result.failure(IllegalStateException("temporary failure")),
                    sessionToken = token,
                )
            } else {
                successfulResponse(token, isRepresenting = true)
            }
        }

        val first = coordinator.update(profile(), true, SESSION_TOKEN)
        val second = coordinator.update(profile(), true, SESSION_TOKEN)

        assertIs<GroupRepresentationUpdateResult.Failed>(first)
        assertIs<GroupRepresentationUpdateResult.Updated>(second)
        assertEquals(2, requestCount)
    }

    @Test
    fun staleSessionAndMismatchedRefreshAreRejected() = runTest {
        val refreshedToken = SESSION_TOKEN.copy(generation = SESSION_TOKEN.generation + 1)
        val staleCoordinator = GroupRepresentationCoordinator(
            request = GroupRepresentationRequest { _, _, _ ->
                successfulResponse(refreshedToken, isRepresenting = true)
            },
            isCurrentSession = { false },
        )

        assertIs<GroupRepresentationUpdateResult.SessionChanged>(
            staleCoordinator.update(profile(), true, SESSION_TOKEN)
        )

        val mismatchedCoordinator = coordinator { token, _, _ ->
            AuthenticatedGroupRepresentationResponse(
                result = Result.success(groupData(memberUserId = "usr_other")),
                sessionToken = token,
            )
        }
        val mismatched = mismatchedCoordinator.update(profile(), true, SESSION_TOKEN)

        assertIs<GroupRepresentationUpdateResult.Failed>(mismatched)
        assertTrue(mismatched.error is IllegalStateException)
    }

    private fun coordinator(
        request: suspend (
            AccountSessionToken,
            String,
            Boolean,
        ) -> AuthenticatedGroupRepresentationResponse?,
    ) = GroupRepresentationCoordinator(
        request = GroupRepresentationRequest(request),
        isCurrentSession = { true },
    )

    private fun successfulResponse(
        token: AccountSessionToken,
        isRepresenting: Boolean,
    ) = AuthenticatedGroupRepresentationResponse(
        result = Result.success(groupData(isRepresenting = isRepresenting)),
        sessionToken = token,
    )

    private fun profile(
        groupStatus: String = "member",
        memberStatus: String = "member",
        memberGroupId: String = GROUP_ID,
        memberUserId: String = USER_ID,
        isRepresenting: Boolean = false,
    ) = GroupProfileVo(
        groupData(
            groupStatus = groupStatus,
            memberStatus = memberStatus,
            memberGroupId = memberGroupId,
            memberUserId = memberUserId,
            isRepresenting = isRepresenting,
        )
    )

    private fun groupData(
        groupStatus: String = "member",
        memberStatus: String = "member",
        memberGroupId: String = GROUP_ID,
        memberUserId: String = USER_ID,
        isRepresenting: Boolean = false,
    ) = GroupData(
        id = GROUP_ID,
        membershipStatus = groupStatus,
        myMember = MyMember(
            groupId = memberGroupId,
            has2FA = true,
            id = "gmem_1",
            isRepresenting = isRepresenting,
            isSubscribedToAnnouncements = false,
            joinedAt = "2026-01-01T00:00:00.000Z",
            lastPostReadAt = null,
            mRoleIds = emptyList(),
            membershipStatus = memberStatus,
            permissions = emptyList(),
            roleIds = emptyList(),
            userId = memberUserId,
            visibility = "visible",
        ),
    )

    private companion object {
        const val GROUP_ID = "grp_00000000-0000-0000-0000-000000000001"
        const val USER_ID = "usr_00000000-0000-0000-0000-000000000001"
        val SESSION_TOKEN = AccountSessionToken(USER_ID, generation = 1)
    }
}
