package io.github.vrcmteam.vrcm.core.extensions

fun Long.bytesToMb(): Int = if (this > 0) (toDouble() / (1024 * 1024)).toInt() else 0
