package io.github.kamo.vrcm.data.api.instance

import com.google.gson.annotations.SerializedName

data class WorldInfo(
    val authorId: String,
    val authorName: String,
    val capacity: Int,
    @SerializedName("created_at")
    val createdAt: String,
    val description: String,
    val favorites: Int,
    val featured: Boolean,
    val heat: Int,
    val id: String,
    val imageUrl: String,
    val labsPublicationDate: String,
    val name: String?,
    val namespace: String,
    val organization: String,
    val popularity: Int,
    val previewYoutubeId: String? = null,
    val publicationDate: String,
    val recommendedCapacity: Int,
    val releaseStatus: String,
    val tags: List<String>,
    val thumbnailImageUrl: String?,
    val udonProducts: List<Any>,
    val unityPackages: List<UnityPackage>,
    @SerializedName("updated_at")
    val updatedAt: String,
    val version: Int,
    val visits: Int
)