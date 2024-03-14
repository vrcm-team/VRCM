package io.github.vrcmteam.vrcm

class DesktopAppPlatform : AppPlatform {
    override val name: String
        get() = System.getProperty("os.name")
    override val version: String
        get() = System.getProperty("os.version")
}