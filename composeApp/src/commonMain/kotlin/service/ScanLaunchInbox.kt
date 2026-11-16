package io.github.vrcmteam.vrcm.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Retains a launcher request until authentication and navigation are ready. */
class ScanLaunchInbox {
    private val _pending = MutableStateFlow(false)
    val pending = _pending.asStateFlow()

    fun requestScan() { _pending.value = true }
    internal fun consume() { _pending.value = false }
}
