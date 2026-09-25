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

class GroupNotificationPreferenceCoordinatorTest {
    @Test
    fun onlyTheCurrentActiveMemberCanSubmit() = runTest {
        var requestCount = 0
        val coordinator = coordinator { _, _, _, _ ->
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
            assertIs<GroupNotificationPreferenceUpdateResult.NotAllowed>(
                coordinator.update(group, enabled = true, sessionToken = SESSION_TOKEN)
            )
        }

        assertEquals(0, requestCount)
    }

    @Test
    fun duplicateSubmissionRunsOnlyOneRequest() = runTest {
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        var requestCount = 0
        val coordinator = coordinator { token, _, _, _ ->
            requestCount++
            started.complete(Unit)
            release.await()
            successfulResponse(token, enabled = true)
        }

        val first = async {
            coordinator.update(profile(), enabled = true, sessionToken = SESSION_TOKEN)
        }
        started.await()
        val duplicate = coordinator.update(
            group = profile(),
            enabled = true,
            sessionToken = SESSION_TOKEN,
        )
        release.complete(Unit)

        assertIs<GroupNotificationPreferenceUpdateResult.InFlight>(duplicate)
        assertIs<GroupNotificationPreferenceUpdateResult.Updated>(first.await())
        assertEquals(1, requestCount)
    }

    @Test
    fun refreshedStateMustMatchTheRequestedPreference() = runTest {
        val coordinator = coordinator { token, _, _, _ ->
            successfulResponse(token, enabled = false)
        }

        val result = coordinator.update(
            group = profile(enabled = false),
            enabled = true,
            sessionToken = SESSION_TOKEN,
        )

        assertIs<GroupNotificationPreferenceUpdateResult.Failed>(result)
    }

    @Test
    fun staleSessionIsRejected() = runTest {
        val refreshedToken = SESSION_TOKEN.copy(generation = SESSION_TOKEN.generation + 1)
        val coordinator = GroupNotificationPreferenceCoordinator(
            request = GroupNotificationPreferenceRequest { _, _, _, _ ->
                successfulResponse(refreshedToken, enabled = true)
            },
            isCurrentSession = { false },
        )

        assertIs<GroupNotificationPreferenceUpdateResult.SessionChanged>(
            coordinator.update(profile(), enabled = true, sessionToken = SESSION_TOKEN)
        )
    }

    private fun coordinator(
        request: suspend (
            AccountSessionToken,
            String,
            String,
            Boolean,
        ) -> AuthenticatedGroupNotificationPreferenceResponse?,
    ) = GroupNotificationPreferenceCoordinator(
        request = GroupNotificationPreferenceRequest(request),
        isCurrentSession = { true },
    )

    private fun successfulResponse(
        token: AccountSessionToken,
        enabled: Boolean,
    ) = AuthenticatedGroupNotificationPreferenceResponse(
        result = Result.success(groupData(enabled = enabled)),
        sessionToken = token,
    )

    private fun profile(
        groupStatus: String = "member",
        memberStatus: String = "member",
        memberGroupId: String = GROUP_ID,
        memberUserId: String = USER_ID,
        enabled: Boolean = false,
    ) = GroupProfileVo(
        groupData(
            groupStatus = groupStatus,
            memberStatus = memberStatus,
            memberGroupId = memberGroupId,
            memberUserId = memberUserId,
            enabled = enabled,
        )
    )

    private fun groupData(
        groupStatus: String = "member",
        memberStatus: String = "member",
        memberGroupId: String = GROUP_ID,
        memberUserId: String = USER_ID,
        enabled: Boolean = false,
    ) = GroupData(
        id = GROUP_ID,
        membershipStatus = groupStatus,
        myMember = MyMember(
            groupId = memberGroupId,
            has2FA = true,
            id = "gmem_1",
            isRepresenting = false,
            isSubscribedToAnnouncements = enabled,
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
