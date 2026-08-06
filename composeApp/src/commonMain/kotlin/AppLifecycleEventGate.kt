package io.github.vrcmteam.vrcm

internal class AppLifecycleEventGate {
    private var isBackground = false

    fun onStop(isConfigurationChange: Boolean): Boolean {
        if (isConfigurationChange || isBackground) return false
        isBackground = true
        return true
    }

    fun onResume(): Boolean {
        if (!isBackground) return false
        isBackground = false
        return true
    }
}
