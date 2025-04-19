package io.github.vrcmteam.vrcm.network.api.files.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 文件API的响应
 */
@Serializable
data class FileResponse(
    val id: String,
    val name: String,
    val ownerId: String,
    val mimeType: String,
    val extension: String,
    val versions: List<FileVersionResponse>
)

/**
 * 响应中的文件版本
 */
@Serializable
data class FileVersionResponse(
    val version: Int,
    val status: String,
    @SerialName("created_at")
    val createdAt: String,
    val file: FileDetailsResponse
)

/**
 * 响应中的文件详情
 */
@Serializable
data class FileDetailsResponse(
    val category: String,
    val fileName: String,
    val md5: String,
    val sizeInBytes: Long,
    val status: String,
    val uploadId: String,
    val url: String
)
