package io.github.vrcmteam.vrcm.network.api.github.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AssetsData(
    @SerialName("browser_download_url")
    val browserDownloadUrl: String,
    @SerialName("content_type")
    val contentType: String,
    @SerialName("created_at")
    val createdAt: String,
    @SerialName("download_count")
    val downloadCount: Int,
    val id: Int,
//    val label: Any,
    val name: String,
    @SerialName("node_id")
    val nodeId: String,
    val size: Int,
    val state: String,
    @SerialName("updated_at")
    val updatedAt: String,
    val uploader: UploaderData,
    val url: String
)