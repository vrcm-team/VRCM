package io.github.kamo.vrcm.data.api.instance

data class UnityPackage(
    val assetUrl: String,
    val assetVersion: Int,
    val created_at: String,
    val id: String,
    val platform: String,
    val pluginUrl: String,
    val unitySortNumber: Long,
    val unityVersion: String
)