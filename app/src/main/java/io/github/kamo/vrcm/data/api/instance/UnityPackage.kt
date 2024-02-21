package io.github.kamo.vrcm.data.api.instance

import com.google.gson.annotations.SerializedName

data class UnityPackage(
    val assetUrl: String,
    val assetVersion: Int,
    @SerializedName("created_at")
    val createdAt: String,
    val id: String,
    val platform: String,
    val pluginUrl: String,
    val unitySortNumber: Long,
    val unityVersion: String
)