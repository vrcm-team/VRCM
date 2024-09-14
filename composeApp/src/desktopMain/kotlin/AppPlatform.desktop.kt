package io.github.vrcmteam.vrcm

class DesktopAppPlatform : AppPlatform {
    override val name: String  = System.getProperty("os.name")
    override val version: String  = System.getProperty("os.version")
    override val type: AppPlatformType  = AppPlatformType.Desktop
}