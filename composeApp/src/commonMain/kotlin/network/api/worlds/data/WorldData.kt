package io.github.vrcmteam.vrcm.network.api.worlds.data

import cafe.adriel.voyager.core.lifecycle.JavaSerializable
import io.github.vrcmteam.vrcm.network.api.files.FileApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class WorldData(
    val authorId: String,
    val authorName: String,
    val capacity: Int,
    @SerialName("created_at")
    val createdAt: String?,
    val description: String?,
    val favorites: Int?,
    val featured: Boolean?,
    val heat: Int,
    val id: String,
    val imageUrl: String,
    val labsPublicationDate: String,
    val name: String,
    val namespace: String?,
    val organization: String,
    val popularity: Int,
    val previewYoutubeId: String? = null,
    val privateOccupants: Int? = null,
    val publicOccupants: Int? = null,
    val publicationDate: String,
    val recommendedCapacity: Int,
    val releaseStatus: String,
    val tags: List<String>,
    val thumbnailImageUrl: String?,
    val udonProducts: List<String>,
    val unityPackages: List<UnityPackage>,
    @SerialName(value = "instances")
    val instances: List<List<String>>? = null,
    @SerialName("updated_at")
    val updatedAt: String?,
    val version: Int?,
    val visits: Int?
) : JavaSerializable{
    val imageUrlThumbnail:String = FileApi.convertFileUrl(imageUrl)
}