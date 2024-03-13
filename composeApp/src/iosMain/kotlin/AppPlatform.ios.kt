package io.github.vrcmteam.vrcm
import platform.UIKit.UIDevice

class IosAppPlatform: AppPlatform {
    override val name: String = UIDevice.currentDevice.systemName()
    override val version: String = UIDevice.currentDevice.systemVersion
}


