package io.github.vrcmteam.vrcm.network.api.status.data

import kotlinx.serialization.Serializable

@Serializable
data class VrchatStatusData(
    val status: Status,
) {
    @Serializable
    data class Status(
        val indicator: String,
        val description: String,
    )
}
