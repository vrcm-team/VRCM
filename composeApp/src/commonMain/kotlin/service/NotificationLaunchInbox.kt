package io.github.vrcmteam.vrcm.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class NotificationLaunchDestination {
    UserProfile,
    NotificationCenter,
}

internal data class NotificationLaunchRequest(
    val requestId: Long,
    val destination: NotificationLaunchDestination,
    val targetId: String,
)

/** Carries Android system-notification taps into shared navigation after authentication completes. */
class NotificationLaunchInbox internal constructor() {
    private val _pendingRequest = MutableStateFlow<NotificationLaunchRequest?>(null)
    internal val pendingRequest: StateFlow<NotificationLaunchRequest?> = _pendingRequest.asStateFlow()
    private var nextRequestId = 0L

    internal fun submit(destination: NotificationLaunchDestination, targetId: String) {
        if (targetId.isBlank()) return
        _pendingRequest.value = NotificationLaunchRequest(
            requestId = ++nextRequestId,
            destination = destination,
            targetId = targetId,
        )
    }

    internal fun consume(request: NotificationLaunchRequest) {
        if (_pendingRequest.value?.requestId == request.requestId) _pendingRequest.value = null
    }
}
