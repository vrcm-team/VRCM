package io.github.vrcmteam.vrcm.service.meetup

import io.github.vrcmteam.vrcm.network.api.profile.ProfileAppearanceApi
import io.github.vrcmteam.vrcm.network.api.users.UsersApi
import io.github.vrcmteam.vrcm.presentation.screens.gallery.editor.PrintImageLimits
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/** A compact remote profile used to refresh a meetup card snapshot. */
data class MeetupRemoteProfile(
    val id: String,
    val displayName: String,
    val avatarUrl: String,
    val pronouns: String,
    val languages: List<String>,
    val status: String,
    val statusDescription: String,
    val profileBackgroundUrl: String,
    val links: List<String> = emptyList(),
    val representedGroup: MeetupRemoteGroup? = null,
)

/** 用户主选展示的群组。 */
data class MeetupRemoteGroup(
    val id: String,
    val name: String,
    val bannerUrl: String,
    val iconUrl: String,
)

/** Equipped profile decoration IDs returned by the profile appearance endpoint. */
data class MeetupRemoteAppearance(
    val id: String,
    val iconFrame: String? = null,
    val profileEffect: String? = null,
    val nameplateEffect: String? = null,
)

/** Narrow remote boundary required by the meetup card repository. */
interface MeetupCardRemoteDataSource {
    suspend fun getProfile(ownerId: String): MeetupRemoteProfile

    suspend fun getAppearance(ownerId: String): MeetupRemoteAppearance

    suspend fun loadImage(url: String): ByteArray
}

/** Production adapter over the existing authenticated VRChat APIs and bounded byte loader. */
class DefaultMeetupCardRemoteDataSource(
    private val usersApi: UsersApi,
    private val profileAppearanceApi: ProfileAppearanceApi,
    private val remoteBytesLoader: MeetupRemoteBytesLoader,
) : MeetupCardRemoteDataSource {
    override suspend fun getProfile(ownerId: String): MeetupRemoteProfile = coroutineScope {
        val user = async { usersApi.fetchUser(ownerId) }
        // 主选群组只是卡片上的可选装饰：单独拉、单独失败，
        // 群组接口不可用时照样要把名字和照片刷出来。
        val representedGroup = async { fetchRepresentedGroup(ownerId) }
        user.await().let { user ->
            MeetupRemoteProfile(
                id = user.id,
                displayName = user.displayName,
                avatarUrl = user.iconUrl,
                pronouns = user.pronouns.orEmpty(),
                languages = user.speakLanguages,
                status = user.status.value,
                statusDescription = user.statusDescription,
                profileBackgroundUrl = user.profileImageUrl,
                links = user.bioLinks,
                representedGroup = representedGroup.await(),
            )
        }
    }

    private suspend fun fetchRepresentedGroup(ownerId: String): MeetupRemoteGroup? = try {
        usersApi.getUserGroups(ownerId)
            .firstOrNull { it.isRepresenting }
            ?.let { group ->
                MeetupRemoteGroup(
                    id = group.groupId.takeIf(String::isNotBlank) ?: group.id,
                    name = group.name,
                    bannerUrl = group.bannerUrl.orEmpty(),
                    iconUrl = group.iconUrl.orEmpty(),
                )
            }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        null
    }

    override suspend fun getAppearance(ownerId: String): MeetupRemoteAppearance =
        profileAppearanceApi.get(ownerId).let { appearance ->
            MeetupRemoteAppearance(
                id = appearance.id,
                iconFrame = appearance.iconFrame,
                profileEffect = appearance.profileEffect,
                nameplateEffect = appearance.nameplateEffect,
            )
        }

    override suspend fun loadImage(url: String): ByteArray {
        val bytes = remoteBytesLoader.load(url, PrintImageLimits.MAX_FILE_BYTES)
        // CDN 可能对图片 URL 返回 200 + 错误页；损坏字节不得替换已缓存的有效照片。
        require(looksLikeSupportedImage(bytes)) {
            "Remote meetup image bytes are not a supported image format"
        }
        return bytes
    }
}

/** 用文件头快速排除明显不是图片的响应；完整解码校验由展示/编辑管线承担。 */
internal fun looksLikeSupportedImage(bytes: ByteArray): Boolean {
    if (bytes.size < 12) return false
    return when {
        // JPEG: FF D8 FF
        bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() && bytes[2] == 0xFF.toByte() -> true
        // PNG: 89 'P' 'N' 'G'
        bytes[0] == 0x89.toByte() && bytes.matchesAscii(1, "PNG") -> true
        // WebP: "RIFF"...."WEBP"
        bytes.matchesAscii(0, "RIFF") && bytes.matchesAscii(8, "WEBP") -> true
        // GIF: "GIF8"
        bytes.matchesAscii(0, "GIF8") -> true
        // HEIC/HEIF/AVIF: ISO BMFF，第 4 字节起为 "ftyp"
        bytes.matchesAscii(4, "ftyp") -> true
        else -> false
    }
}

private fun ByteArray.matchesAscii(offset: Int, text: String): Boolean {
    if (offset + text.length > size) return false
    return text.indices.all { index -> this[offset + index] == text[index].code.toByte() }
}
