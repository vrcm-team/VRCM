package io.github.vrcmteam.vrcm.network.api.notification.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ResponseData(
    @SerialName("data")
    val responseData: String,
    val icon: String,
    val text: String,
    val textKey: String,
    val type: String
)