package io.github.vrcmteam.vrcm.network.api.avatars.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement

private fun <T> decodeListAllowingEmptyJsonString(
    decoder: Decoder,
    delegate: KSerializer<List<T>>,
): List<T> {
    if (decoder !is JsonDecoder) return delegate.deserialize(decoder)

    val element = decoder.decodeJsonElement()
    if (element is JsonPrimitive && element.isString && element.content.isEmpty()) {
        return emptyList()
    }
    return decoder.json.decodeFromJsonElement(delegate, element)
}

internal object AvatarTagsSerializer : KSerializer<List<String>> {
    private val delegate = ListSerializer(String.serializer())
    override val descriptor: SerialDescriptor = delegate.descriptor

    override fun deserialize(decoder: Decoder): List<String> =
        decodeListAllowingEmptyJsonString(decoder, delegate)

    override fun serialize(encoder: Encoder, value: List<String>) =
        delegate.serialize(encoder, value)
}

internal object AvatarUnityPackagesSerializer : KSerializer<List<AvatarUnityPackage>> {
    private val delegate = ListSerializer(AvatarUnityPackage.serializer())
    override val descriptor: SerialDescriptor = delegate.descriptor

    override fun deserialize(decoder: Decoder): List<AvatarUnityPackage> =
        decodeListAllowingEmptyJsonString(decoder, delegate)

    override fun serialize(encoder: Encoder, value: List<AvatarUnityPackage>) =
        delegate.serialize(encoder, value)
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
    val styles: AvatarStyles = AvatarStyles(),
    @Serializable(with = AvatarUnityPackagesSerializer::class)
    val unityPackages: List<AvatarUnityPackage> = emptyList(),
)

@Serializable
data class AvatarStyles(
    val primary: String? = null,
    val secondary: String? = null,
    val supplementary: List<String> = emptyList(),
)

@Serializable
data class AvatarStyle(
    val id: String,
    val styleName: String,
)

@Serializable
data class AvatarUnityPackage(
    val platform: String? = null,
    val unityVersion: String? = null,
    val performanceRating: String? = null,
)
