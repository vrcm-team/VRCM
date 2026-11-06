package io.github.vrcmteam.vrcm.presentation.designsystem

internal actual fun supportsProgressiveBackdropBlur(): Boolean =
    !System.getProperty("os.name").startsWith("Windows", ignoreCase = true)
