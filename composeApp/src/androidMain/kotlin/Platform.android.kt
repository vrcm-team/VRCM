package io.github.vrcmteam.vrcm

actual fun getPlatform(): Platform {
    return AndroidPlatform
}
object AndroidPlatform : Platform {
    override val name: String = "Android"
    override val version: String = "1.0.0"
}
