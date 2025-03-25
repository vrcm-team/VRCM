package io.github.vrcmteam.vrcm.network.api.files.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 文件标签类型
 */
@Serializable
enum class FileTagType(val value: String) {
    @SerialName("icon")
    Icon("icon"),
    
    @SerialName("emoji")
    Emoji("emoji"),
    
    @SerialName("sticker")
    Sticker("sticker"),
    
    @SerialName("gallery")
    Gallery("gallery");
    
    override fun toString(): String = value
}

/**
 * 文件数据模型
 *
 * @param animationStyle 贴图(Sticker)或表情(Emoji)会有此字段
 */
@Serializable
data class FileData(
    val id: String,
    val name: String,
    val ownerId: String,
    val mimeType: String,
    val extension: String,
    val animationStyle: String?,
    val tags: List<String>,
    val versions: List<FileVersion>
)

/**
 * 文件版本数据模型
 */
@Serializable
data class FileVersion(
    val version: Int,
    val status: FileStatus,
    @SerialName("created_at")
    val createdAt: String,
    val delta: Map<String, String>? = null,
    val file: Map<String, String>? = null,
    val signature: Map<String, String>? = null,
    val deleted: Boolean = false
)

/**
 * 文件状态
 */
@Serializable
enum class FileStatus {
    @SerialName("waiting")
    Waiting,
    
    @SerialName("complete")
    Complete,
    
    @SerialName("none")
    None,
    
    @SerialName("queued")
    Queued
}

/**
 * 文件详细数据
 */
@Serializable
data class FileDetailsData(
    val status: FileStatus,
    val fileName: String,
    val md5: String? = null,
    val sizeInBytes: Int,
    val uploadId: String = "",
    val url: String,
    val category: String
) 