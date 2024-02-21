package io.github.kamo.vrcm.data.api.auth

import com.google.gson.annotations.SerializedName

data class PastDisplayName(
    val displayName: String,
    @SerializedName("updated_at")
    val updatedAt: String
)