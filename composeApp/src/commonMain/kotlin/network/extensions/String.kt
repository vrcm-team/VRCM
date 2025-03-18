package io.github.vrcmteam.vrcm.network.extensions

fun String.urlEncode(): String = buildString {
    this@urlEncode.forEach { char ->
        when {
            char.isLetterOrDigit() || "-_.~".contains(char) -> append(char)
            else -> append("%${char.code.toString(16).uppercase().padStart(2, '0')}")
        }
    }
}