package io.github.vrcmteam.vrcm.service

import io.github.vrcmteam.vrcm.core.shared.AccountSessionToken
import io.github.vrcmteam.vrcm.core.shared.SharedFlowCentre
import io.github.vrcmteam.vrcm.network.api.instances.InstancesApi
import io.github.vrcmteam.vrcm.network.api.instances.data.InstanceCreationOptions
import io.github.vrcmteam.vrcm.network.api.instances.data.InstanceData
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized

sealed interface InstanceCreationResult {
    data class Created(
        val instance: InstanceData,
        val sessionToken: AccountSessionToken,
    ) : InstanceCreationResult

    data object InFlight : InstanceCreationResult
    data object SessionChanged : InstanceCreationResult
    data class Failed(val error: Throwable) : InstanceCreationResult
}

internal data class AuthenticatedInstanceCreationResponse(
    val response: Result<InstanceData>,
    val sessionToken: AccountSessionToken,
)

internal fun interface InstanceCreationRequest {
    suspend fun create(
        sessionToken: AccountSessionToken,
        options: InstanceCreationOptions,
    ): AuthenticatedInstanceCreationResponse?
}

internal class NetworkInstanceCreationRequest(
    private val authService: AuthService,
    private val instancesApi: InstancesApi,
) : InstanceCreationRequest {
    override suspend fun create(
        sessionToken: AccountSessionToken,
        options: InstanceCreationOptions,
    ): AuthenticatedInstanceCreationResponse? = authService
        .runSessionBoundCatching(sessionToken) { instancesApi.createInstance(options) }
        ?.let { AuthenticatedInstanceCreationResponse(it.result, it.sessionToken) }
}

internal class InstanceCreationSubmissionGate {
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

/** Performs one account-bound create-instance mutation at a time. */
class InstanceCreationService internal constructor(
    private val request: InstanceCreationRequest,
) {
    private val submissionGate = InstanceCreationSubmissionGate()

    suspend fun create(options: InstanceCreationOptions): InstanceCreationResult {
        val sessionToken = SharedFlowCentre.currentSession.value?.token
            ?: return InstanceCreationResult.SessionChanged
        if (!submissionGate.tryStart()) return InstanceCreationResult.InFlight

        return try {
            val response = request.create(sessionToken, options)
                ?: return InstanceCreationResult.SessionChanged
            if (!SharedFlowCentre.isCurrentSession(response.sessionToken)) {
                return InstanceCreationResult.SessionChanged
            }
            response.response.fold(
                onSuccess = { InstanceCreationResult.Created(it, response.sessionToken) },
                onFailure = { InstanceCreationResult.Failed(it) },
            )
        } finally {
            submissionGate.finish()
        }
    }
}
