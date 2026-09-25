package io.github.vrcmteam.vrcm.service.data

data class VersionDto(
    /** 新版本的标识，"不再提示此版本"按它记：GitHub / App Store 是版本号，Google Play 是 versionCode。 */
    val tagName: String,
    /** 更新跳转的页面：GitHub Release 页面，或 App Store / Google Play 商品页。 */
    val htmlUrl: String,
    val body: String,
    val hasNewVersion: Boolean,
    val downloadUrl: List<String> = emptyList(),
    /** 弹窗里显示的版本号；Google Play 查不到版本名时为 null，不显示。 */
    val versionName: String? = tagName,
)
