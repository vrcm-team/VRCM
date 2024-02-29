package io.github.vrcmteam.vrcm

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform