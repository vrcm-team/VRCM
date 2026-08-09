package io.github.vrcmteam.vrcm.presentation.screens.meetup.editor

import io.github.vrcmteam.vrcm.presentation.screens.gallery.GallerySelectionSessionStore
import io.github.vrcmteam.vrcm.presentation.screens.gallery.editor.PrintImageLimits
import io.github.vrcmteam.vrcm.service.meetup.MeetupRemoteBytesLoader
import io.github.vrcmteam.vrcm.storage.meetup.MeetupCardAssetStore
import io.github.vrcmteam.vrcm.storage.meetup.MeetupCardConfig
import io.github.vrcmteam.vrcm.storage.meetup.MeetupPhotoSource
import kotlinx.coroutines.CancellationException

/**
 * 统一三种照片来源的取字节与解码入口：成功后创建裁剪会话并返回会话 ID，
 * 失败不触碰当前配置。不做任何上传。
 */
class MeetupPhotoSelectionCoordinator(
    private val preparer: MeetupPhotoPreparer,
    private val photoSessions: MeetupPhotoSessionStore,
    private val gallerySessions: GallerySelectionSessionStore,
    private val bytesLoader: MeetupRemoteBytesLoader,
    private val assetStore: MeetupCardAssetStore,
) {
    /** 资料背景图：优先读本地已缓存素材，缺失时按来源 URL 重新下载。 */
    suspend fun prepareProfileBackground(config: MeetupCardConfig): Result<String> = runPrepare {
        val fallback = config.profileBackgroundFallback
            ?: config.photo.takeIf { it.source == MeetupPhotoSource.ProfileBackground }
        val localAsset = fallback?.localAsset?.takeIf(assetStore::exists)
        val sourceUrl = fallback?.sourceUrl?.trim().orEmpty()
        val bytes = when {
            localAsset != null -> assetStore.read(localAsset)
            sourceUrl.isNotEmpty() -> bytesLoader.load(sourceUrl, PrintImageLimits.MAX_FILE_BYTES)
            else -> throw IllegalStateException("No profile background is available yet")
        }
        prepareSession(
            source = MeetupPhotoSource.ProfileBackground,
            sourceId = null,
            sourceUrl = sourceUrl.takeIf(String::isNotEmpty),
            fileName = sourceUrl.ifEmpty { "profile-background.webp" },
            bytes = bytes,
        )
    }

    suspend fun prepareLocalAlbum(fileName: String, bytes: ByteArray): Result<String> = runPrepare {
        prepareSession(
            source = MeetupPhotoSource.LocalAlbum,
            sourceId = null,
            sourceUrl = null,
            fileName = fileName,
            bytes = bytes,
        )
    }

    fun beginGallerySelection(): String = gallerySessions.create()

    /** Gallery 会话仍在等待用户选择时为 true。 */
    fun isGallerySelectionPending(gallerySessionId: String): Boolean =
        gallerySessions.isPending(gallerySessionId)

    /**
     * 消费 Gallery 选择结果并下载原图。用户取消（无结果）返回 success(null)，
     * 下载或解码失败返回 failure。
     */
    suspend fun finishGallerySelection(gallerySessionId: String): Result<String?> {
        val selection = gallerySessions.consume(gallerySessionId)
            ?: return Result.success(null)
        return runPrepare {
            val bytes = bytesLoader.load(selection.imageUrl, PrintImageLimits.MAX_FILE_BYTES)
            prepareSession(
                source = MeetupPhotoSource.VrchatGallery,
                sourceId = selection.fileId,
                sourceUrl = selection.imageUrl,
                fileName = selection.fileName + selection.extension,
                bytes = bytes,
            )
        }
    }

    private suspend fun prepareSession(
        source: MeetupPhotoSource,
        sourceId: String?,
        sourceUrl: String?,
        fileName: String,
        bytes: ByteArray,
    ): String {
        val prepared = preparer.prepare(
            source = source,
            sourceId = sourceId,
            sourceUrl = sourceUrl,
            fileName = fileName,
            bytes = bytes,
        ).getOrThrow()
        return photoSessions.create(prepared).id
    }

    private inline fun <T> runPrepare(block: () -> T): Result<T> = try {
        Result.success(block())
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        Result.failure(error)
    }
}
