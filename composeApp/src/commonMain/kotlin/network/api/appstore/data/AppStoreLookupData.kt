package io.github.vrcmteam.vrcm.network.api.appstore.data

import kotlinx.serialization.Serializable

/** App Store 查询结果：尚未上架或该地区商店没有上架时 results 为空。 */
@Serializable
data class AppStoreLookupData(
    val results: List<AppStoreAppData> = emptyList(),
)

@Serializable
data class AppStoreAppData(
    val trackId: Long,
    val version: String,
    /** 当前版本的"新功能"说明；首个版本没有。 */
    val releaseNotes: String = "",
)
