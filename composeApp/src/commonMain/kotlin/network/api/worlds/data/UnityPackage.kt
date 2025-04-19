package io.github.vrcmteam.vrcm.network.api.worlds.data

import cafe.adriel.voyager.core.lifecycle.JavaSerializable
import io.github.vrcmteam.vrcm.network.api.files.data.PlatformType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class UnityPackage(
    val assetUrl: String? = null,
    val assetVersion: Int? = null,
    @SerialName("created_at")
    val createdAt: String?,
    val id: String? = null,
    val platform: PlatformType,
    val pluginUrl: String?,
    val unitySortNumber: Long? = null,
    val unityVersion: String
) : JavaSerializable