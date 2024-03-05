package io.github.vrcmteam.vrcm.network.api.worlds.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class UnityPackage(
    val assetUrl: String,
    val assetVersion: Int,
    @SerialName("created_at")
    val createdAt: String?,
    val id: String,
    val platform: String,
    val pluginUrl: String,
    val unitySortNumber: Long,
    val unityVersion: String
)