package io.github.vrcmteam.vrcm.service.data

data class VersionDto(
    val tagName: String,
    /** 更新跳转的页面：GitHub Release 页面，或 App Store 商品页。 */
    val htmlUrl: String,
    val body: String,
    val hasNewVersion: Boolean,
    val downloadUrl: List<String> = emptyList(),
)
