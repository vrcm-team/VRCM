package io.github.vrcmteam.vrcm.network.api.avatars.data

import cafe.adriel.voyager.core.lifecycle.JavaSerializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonTransformingSerializer

internal object AvatarTagsSerializer : JsonTransformingSerializer<List<String>>(
    ListSerializer(String.serializer())
) {
    override fun transformDeserialize(element: JsonElement): JsonElement =
        if (element is JsonPrimitive && element.isString && element.content.isEmpty()) {
            JsonArray(emptyList())
        } else {
            element
        }
}

internal object AvatarUnityPackagesSerializer : JsonTransformingSerializer<List<AvatarUnityPackage>>(
    ListSerializer(AvatarUnityPackage.serializer())
) {
    override fun transformDeserialize(element: JsonElement): JsonElement =
        if (element is JsonPrimitive && element.isString && element.content.isEmpty()) {
            JsonArray(emptyList())
        } else {
            element
        }
}

@Serializable
data class AvatarData(
    val id: String,
    val name: String,
    val description: String? = null,
    val authorId: String = "",
    val authorName: String = "",
    val imageUrl: String = "",
    val thumbnailImageUrl: String? = null,
    val releaseStatus: String = "",
    @Serializable(with = AvatarTagsSerializer::class)
    val tags: List<String> = emptyList(),
    @SerialName("created_at")
    val createdAt: String? = null,
    @SerialName("updated_at")
    val updatedAt: String? = null,
    val version: Int? = null,
    val featured: Boolean = false,
    @Serializable(with = AvatarUnityPackagesSerializer::class)
    val unityPackages: List<AvatarUnityPackage> = emptyList(),
) : JavaSerializable

@Serializable
data class AvatarUnityPackage(
    val platform: String? = null,
    val unityVersion: String? = null,
    val performanceRating: String? = null,
) : JavaSerializable
