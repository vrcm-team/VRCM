package io.github.vrcmteam.vrcm.presentation.screens.group

import io.github.vrcmteam.vrcm.core.shared.AccountSessionToken
import io.github.vrcmteam.vrcm.core.shared.SharedFlowCentre
import io.github.vrcmteam.vrcm.network.api.groups.GroupsApi
import io.github.vrcmteam.vrcm.network.api.groups.data.GroupData
import io.github.vrcmteam.vrcm.presentation.screens.group.data.GroupProfileVo
import io.github.vrcmteam.vrcm.service.AuthService
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized

internal sealed interface GroupRepresentationUpdateResult {
    data class Updated(
        val group: GroupData,
        val sessionToken: AccountSessionToken,
    ) : GroupRepresentationUpdateResult

    data class Failed(
        val error: Throwable,
        val sessionToken: AccountSessionToken,
    ) : GroupRepresentationUpdateResult

    data object InFlight : GroupRepresentationUpdateResult
    data object NotAllowed : GroupRepresentationUpdateResult
    data object Unchanged : GroupRepresentationUpdateResult
    data object SessionChanged : GroupRepresentationUpdateResult
}

internal data class AuthenticatedGroupRepresentationResponse(
    val result: Result<GroupData>,
    val sessionToken: AccountSessionToken,
)

internal fun interface GroupRepresentationRequest {
    suspend fun update(
        sessionToken: AccountSessionToken,
        groupId: String,
        isRepresenting: Boolean,
    ): AuthenticatedGroupRepresentationResponse?
}

internal class NetworkGroupRepresentationRequest(
    private val groupsApi: GroupsApi,
    private val authService: AuthService,
) : GroupRepresentationRequest {
    override suspend fun update(
        sessionToken: AccountSessionToken,
        groupId: String,
        isRepresenting: Boolean,
    ): AuthenticatedGroupRepresentationResponse? {
        val response = authService.runSessionBoundCatching(sessionToken) {
            groupsApi.updateRepresentation(groupId, isRepresenting)
            groupsApi.fetchGroup(groupId, includeRoles = true).also { refreshedGroup ->
                check(refreshedGroup.hasActiveMembership(groupId, sessionToken.userId)) {
                    "Refreshed group membership does not match the representation request"
                }
            }
        } ?: return null
        return AuthenticatedGroupRepresentationResponse(
            result = response.result,
            sessionToken = response.sessionToken,
        )
    }
}

internal class GroupRepresentationCoordinator(
    private val request: GroupRepresentationRequest,
    private val isCurrentSession: (AccountSessionToken) -> Boolean = SharedFlowCentre::isCurrentSession,
) {
    private val submissionGate = GroupRepresentationSubmissionGate()

    suspend fun update(
        group: GroupProfileVo,
        isRepresenting: Boolean,
        sessionToken: AccountSessionToken,
    ): GroupRepresentationUpdateResult {
        if (!group.hasActiveMembership(sessionToken)) {
            return GroupRepresentationUpdateResult.NotAllowed
        }
        if (group.myMember?.isRepresenting == isRepresenting) {
            return GroupRepresentationUpdateResult.Unchanged
        }
        if (!submissionGate.tryStart()) {
            return GroupRepresentationUpdateResult.InFlight
        }
        return try {
            val response = request.update(
                sessionToken = sessionToken,
                groupId = group.groupId,
                isRepresenting = isRepresenting,
            ) ?: return GroupRepresentationUpdateResult.SessionChanged
            if (!isCurrentSession(response.sessionToken)) {
                return GroupRepresentationUpdateResult.SessionChanged
            }
            response.result.fold(
                onSuccess = { refreshedGroup ->
                    if (refreshedGroup.hasActiveMembership(group.groupId, sessionToken.userId) &&
                        refreshedGroup.myMember?.isRepresenting == isRepresenting
                    ) {
                        GroupRepresentationUpdateResult.Updated(
                            group = refreshedGroup,
                            sessionToken = response.sessionToken,
                        )
                    } else {
                        GroupRepresentationUpdateResult.Failed(
                            error = IllegalStateException(
                                "Refreshed group representation does not match the request"
                            ),
                            sessionToken = response.sessionToken,
                        )
                    }
                },
                onFailure = { error ->
                    GroupRepresentationUpdateResult.Failed(error, response.sessionToken)
                },
            )
        } finally {
            submissionGate.finish()
        }
    }
}

internal fun GroupProfileVo.hasActiveMembership(sessionToken: AccountSessionToken): Boolean {
    val member = myMember ?: return false
    return groupId.isNotBlank() &&
        sessionToken.userId.isNotBlank() &&
        membershipStatus.equals(ACTIVE_MEMBERSHIP_STATUS, ignoreCase = true) &&
        member.membershipStatus.equals(ACTIVE_MEMBERSHIP_STATUS, ignoreCase = true) &&
        member.groupId == groupId &&
        member.userId == sessionToken.userId
}

private fun GroupData.hasActiveMembership(expectedGroupId: String, expectedUserId: String): Boolean {
    val member = myMember ?: return false
    return id == expectedGroupId &&
        membershipStatus.equals(ACTIVE_MEMBERSHIP_STATUS, ignoreCase = true) &&
        member.membershipStatus.equals(ACTIVE_MEMBERSHIP_STATUS, ignoreCase = true) &&
        member.groupId == expectedGroupId &&
        member.userId == expectedUserId
}

private class GroupRepresentationSubmissionGate {
    private val lock = SynchronizedObject()
    private var inFlight = false

    fun tryStart(): Boolean = synchronized(lock) {
        if (inFlight) return@synchronized false
        inFlight = true
        true
    }

    fun finish() = synchronized(lock) {
        inFlight = false
    }
}

private const val ACTIVE_MEMBERSHIP_STATUS = "member"
