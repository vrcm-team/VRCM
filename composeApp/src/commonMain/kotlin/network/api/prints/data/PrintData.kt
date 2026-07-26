package io.github.vrcmteam.vrcm.network.api.prints.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PrintData(
    val id: String,
    val files: PrintFiles? = null,
    val note: String? = null,
    val worldId: String? = null,
    val worldName: String? = null,
    val authorId: String? = null,
    val authorName: String? = null,
    @SerialName("created_at")
    val createdAt: String? = null,
    val timestamp: String? = null,
)

@Serializable
data class PrintFiles(
    val image: String? = null,
)
