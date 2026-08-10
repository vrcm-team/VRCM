package io.github.vrcmteam.vrcm.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

internal data class OfficialLinkRequest(
    val id: Long,
    val url: String,
)

/** Carries official links received by a platform entry point into the shared application UI. */
class OfficialLinkInbox internal constructor() {
    private val _pendingRequest = MutableStateFlow<OfficialLinkRequest?>(null)
    internal val pendingRequest: StateFlow<OfficialLinkRequest?> = _pendingRequest.asStateFlow()
    private var nextRequestId = 0L

    internal fun submit(url: String) {
        _pendingRequest.value = OfficialLinkRequest(id = ++nextRequestId, url = url)
    }

    internal fun consume(request: OfficialLinkRequest) {
        if (_pendingRequest.value?.id == request.id) {
            _pendingRequest.value = null
        }
    }

    internal fun pendingUrl(): String? = _pendingRequest.value?.url
}
