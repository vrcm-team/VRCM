package io.github.vrcmteam.vrcm.network.supports

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface ApiNotice {
    data object RateLimited : ApiNotice
}

class ApiNoticeCenter {
    private val _activeNotice = MutableStateFlow<ApiNotice?>(null)
    val activeNotice: StateFlow<ApiNotice?> = _activeNotice.asStateFlow()

    fun publish(notice: ApiNotice): Boolean =
        _activeNotice.compareAndSet(null, notice)

    fun consume(notice: ApiNotice?): Boolean =
        notice != null && _activeNotice.compareAndSet(notice, null)
}
