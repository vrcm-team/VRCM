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

    // 分发渠道由构建设置 VRCM_DISTRIBUTION_CHANNEL 写入 Info.plist：只有 GitHub Release 的 IPA 是 "GitHub"，
    // App Store / TestFlight 版按商店规则来。
    private val isGitHubRelease =
        NSBundle.mainBundle.objectForInfoDictionaryKey("VRCMDistributionChannel") as? String == "GitHub"

    // GitHub 版查询 GitHub 新版本；商店版查询商店上架版本，更新只能跳转 App Store，不能引导用户去商店外安装。
    override val updateSource: AppUpdateSource?
        get() = if (isGitHubRelease) {
            AppUpdateSource.GitHub
        } else {
            NSBundle.mainBundle.bundleIdentifier?.let { bundleId ->
                AppUpdateSource.AppStore(
                    bundleId = bundleId,
                    region = NSLocale.currentLocale.objectForKey(NSLocaleCountryCode) as? String,
                )
            }
        }

    // 审核准则 3.1.1 不允许在应用里用兑换码解锁内容：商店版不提供 VRChat 奖励兑换，GitHub 版保留
    override val supportsRewardCodeRedemption: Boolean get() = isGitHubRelease
}
