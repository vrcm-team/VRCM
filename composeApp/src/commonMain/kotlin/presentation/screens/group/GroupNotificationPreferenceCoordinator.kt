package io.github.vrcmteam.vrcm.presentation.screens.group

import io.github.vrcmteam.vrcm.core.shared.AccountSessionToken
import io.github.vrcmteam.vrcm.core.shared.SharedFlowCentre
import io.github.vrcmteam.vrcm.network.api.groups.GroupsApi
import io.github.vrcmteam.vrcm.network.api.groups.data.GroupData
import io.github.vrcmteam.vrcm.presentation.screens.group.data.GroupProfileVo
import io.github.vrcmteam.vrcm.service.AuthService
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized

internal sealed interface GroupNotificationPreferenceUpdateResult {
    data class Updated(
        val group: GroupData,
        val sessionToken: AccountSessionToken,
    ) : GroupNotificationPreferenceUpdateResult

    data class Failed(
        val error: Throwable,
        val sessionToken: AccountSessionToken,
    ) : GroupNotificationPreferenceUpdateResult

    data object InFlight : GroupNotificationPreferenceUpdateResult
    data object NotAllowed : GroupNotificationPreferenceUpdateResult
    data object Unchanged : GroupNotificationPreferenceUpdateResult
    data object SessionChanged : GroupNotificationPreferenceUpdateResult
}

internal data class AuthenticatedGroupNotificationPreferenceResponse(
    val result: Result<GroupData>,
    val sessionToken: AccountSessionToken,
)

internal fun interface GroupNotificationPreferenceRequest {
    suspend fun update(
        sessionToken: AccountSessionToken,
        groupId: String,
        userId: String,
        enabled: Boolean,
    ): AuthenticatedGroupNotificationPreferenceResponse?
}

internal class NetworkGroupNotificationPreferenceRequest(
    private val groupsApi: GroupsApi,
    private val authService: AuthService,
) : GroupNotificationPreferenceRequest {
    override suspend fun update(
        sessionToken: AccountSessionToken,
        groupId: String,
        userId: String,
        enabled: Boolean,
    ): AuthenticatedGroupNotificationPreferenceResponse? {
        val response = authService.runSessionBoundCatching(sessionToken) {
            groupsApi.updateGroupNotifications(
                groupId = groupId,
                userId = userId,
                enabled = enabled,
            ).also { member ->
                check(
                    member.groupId == groupId &&
                        member.userId == userId &&
                        member.membershipStatus.equals(ACTIVE_GROUP_MEMBER_STATUS, ignoreCase = true) &&
                        member.isSubscribedToAnnouncements == enabled
                ) {
                    "Updated group member does not match the notification request"
                }
            }
            groupsApi.fetchGroup(groupId, includeRoles = true).also { refreshedGroup ->
                check(
                    refreshedGroup.id == groupId &&
                        GroupProfileVo(refreshedGroup).hasActiveMembership(sessionToken)
                ) {
                    "Refreshed group membership does not match the notification request"
                }
            }
        } ?: return null
        return AuthenticatedGroupNotificationPreferenceResponse(
            result = response.result,
            sessionToken = response.sessionToken,
        )
    }
}

internal class GroupNotificationPreferenceCoordinator(
    private val request: GroupNotificationPreferenceRequest,
    private val isCurrentSession: (AccountSessionToken) -> Boolean = SharedFlowCentre::isCurrentSession,
) {
    private val submissionGate = GroupNotificationPreferenceSubmissionGate()

    suspend fun update(
        group: GroupProfileVo,
        enabled: Boolean,
        sessionToken: AccountSessionToken,
    ): GroupNotificationPreferenceUpdateResult {
        if (!group.hasActiveMembership(sessionToken)) {
            return GroupNotificationPreferenceUpdateResult.NotAllowed
        }
        if (group.myMember?.isSubscribedToAnnouncements == enabled) {
            return GroupNotificationPreferenceUpdateResult.Unchanged
        }
        if (!submissionGate.tryStart()) {
            return GroupNotificationPreferenceUpdateResult.InFlight
        }
        return try {
            val response = request.update(
                sessionToken = sessionToken,
                groupId = group.groupId,
                userId = sessionToken.userId,
                enabled = enabled,
            ) ?: return GroupNotificationPreferenceUpdateResult.SessionChanged
            if (!isCurrentSession(response.sessionToken)) {
                return GroupNotificationPreferenceUpdateResult.SessionChanged
            }
            response.result.fold(
                onSuccess = { refreshedGroup ->
                    if (
                        refreshedGroup.id == group.groupId &&
                        GroupProfileVo(refreshedGroup).hasActiveMembership(sessionToken) &&
                        refreshedGroup.myMember?.isSubscribedToAnnouncements == enabled
                    ) {
                        GroupNotificationPreferenceUpdateResult.Updated(
                            group = refreshedGroup,
                            sessionToken = response.sessionToken,
                        )
                    } else {
                        GroupNotificationPreferenceUpdateResult.Failed(
                            error = IllegalStateException(
                                "Refreshed group notification preference does not match the request"
                            ),
                            sessionToken = response.sessionToken,
                        )
                    }
                },
                onFailure = { error ->
                    GroupNotificationPreferenceUpdateResult.Failed(error, response.sessionToken)
                },
            )
        } finally {
            submissionGate.finish()
        }
    }
}

private class GroupNotificationPreferenceSubmissionGate {
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

private const val ACTIVE_GROUP_MEMBER_STATUS = "member"
