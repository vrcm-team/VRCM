package io.github.vrcmteam.vrcm
import platform.Foundation.NSBundle
import platform.Foundation.NSLocale
import platform.Foundation.NSLocaleCountryCode
import platform.Foundation.currentLocale
import platform.UIKit.UIDevice

class IosAppPlatform: AppPlatform {
    override val name: String = UIDevice.currentDevice.systemName()
    override val version: String = UIDevice.currentDevice.systemVersion
    override val type: AppPlatformType = AppPlatformType.Ios

    // 分发渠道由构建设置 VRCM_DISTRIBUTION_CHANNEL 写入 Info.plist：GitHub Release 的 IPA 查询 GitHub 新版本；
    // App Store / TestFlight 版查询商店上架版本，更新只能跳转 App Store，不能引导用户去商店外安装。
    private val distributionChannel =
        NSBundle.mainBundle.objectForInfoDictionaryKey("VRCMDistributionChannel") as? String

    override val updateSource: AppUpdateSource?
        get() = if (distributionChannel == "GitHub") {
            AppUpdateSource.GitHub
        } else {
            NSBundle.mainBundle.bundleIdentifier?.let { bundleId ->
                AppUpdateSource.AppStore(
                    bundleId = bundleId,
                    region = NSLocale.currentLocale.objectForKey(NSLocaleCountryCode) as? String,
                )
            }
        }
}
