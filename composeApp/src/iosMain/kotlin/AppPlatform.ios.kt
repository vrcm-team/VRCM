package io.github.vrcmteam.vrcm
import platform.Foundation.NSBundle
import platform.UIKit.UIDevice

class IosAppPlatform: AppPlatform {
    override val name: String = UIDevice.currentDevice.systemName()
    override val version: String = UIDevice.currentDevice.systemVersion
    override val type: AppPlatformType = AppPlatformType.Ios

    // 分发渠道由构建设置 VRCM_DISTRIBUTION_CHANNEL 写入 Info.plist：只有 GitHub Release 的 IPA 才自己检查新版本，
    // App Store / TestFlight 版由商店负责更新，也不能引导用户去商店外安装。
    override val supportsGitHubUpdateCheck: Boolean =
        NSBundle.mainBundle.objectForInfoDictionaryKey("VRCMDistributionChannel") as? String == "GitHub"
}


