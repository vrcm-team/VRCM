package io.github.vrcmteam.vrcm.presentation.screens.gallery.editor

import io.github.vrcmteam.vrcm.network.api.avatars.data.AvatarData
import io.github.vrcmteam.vrcm.core.shared.AccountSessionToken
import io.github.vrcmteam.vrcm.network.api.files.data.FileTagType
import io.github.vrcmteam.vrcm.presentation.screens.avatar.AvatarCoverFile
import io.github.vrcmteam.vrcm.presentation.screens.avatar.AvatarGalleryTarget
import io.github.vrcmteam.vrcm.presentation.screens.avatar.AvatarGalleryUpdate
import io.github.vrcmteam.vrcm.presentation.screens.avatar.AvatarGalleryUploadFailure
import io.github.vrcmteam.vrcm.presentation.screens.avatar.AvatarGalleryUploader
import io.github.vrcmteam.vrcm.presentation.screens.avatar.AvatarEditor
import io.github.vrcmteam.vrcm.presentation.screens.gallery.GalleryDataSource
import io.github.vrcmteam.vrcm.service.PrintUploader
import io.github.vrcmteam.vrcm.presentation.screens.world.WorldImageEditor
import io.github.vrcmteam.vrcm.presentation.screens.world.WorldImageFile
import io.github.vrcmteam.vrcm.presentation.screens.world.WorldImageUpdate

sealed interface ImageEditorTarget {
    data object Print : ImageEditorTarget
    data class AvatarCover(val avatarId: String) : ImageEditorTarget
    data class AvatarGallery(val target: AvatarGalleryTarget) : ImageEditorTarget
    data class WorldCover(
        val worldId: String,
        val sessionToken: AccountSessionToken,
    ) : ImageEditorTarget
    data class Gallery(val tagType: FileTagType) : ImageEditorTarget {
        init {
            require(
                tagType == FileTagType.Gallery ||
                        tagType == FileTagType.Icon ||
                        tagType == FileTagType.Emoji ||
                        tagType == FileTagType.Sticker,
            ) { "Unsupported gallery editor target: $tagType" }
        }
    }
}

/** Gallery uploads preserve PNG alpha; Print and avatar cover surfaces remain opaque. */
internal val ImageEditorTarget.canvasBackground: CanvasBackground
    get() = when (this) {
        ImageEditorTarget.Print,
        is ImageEditorTarget.AvatarCover,
        is ImageEditorTarget.WorldCover -> CanvasBackground.White
        is ImageEditorTarget.AvatarGallery,
        is ImageEditorTarget.Gallery -> CanvasBackground.Transparent
    }

/** Maps each gallery upload category to the dimensions required by the product workflow. */
internal val ImageEditorTarget.Gallery.canvasSpec: PrintCanvasSpec
    get() = when (tagType) {
        FileTagType.Gallery -> GalleryCanvasSpec
        FileTagType.Icon,
        FileTagType.Emoji,
        FileTagType.Sticker -> SquareCanvasSpec
        FileTagType.AvatarImage,
        FileTagType.WorldImage,
        FileTagType.Print -> error("Unsupported gallery editor target: $tagType")
    }

sealed interface ImageEditorSubmission {
    data object Print : ImageEditorSubmission
    data class AvatarCover(val avatar: AvatarData) : ImageEditorSubmission
    data class AvatarGallery(val update: AvatarGalleryUpdate) : ImageEditorSubmission
    data class WorldCover(val update: WorldImageUpdate) : ImageEditorSubmission
    data class Gallery(val tagType: FileTagType) : ImageEditorSubmission
}

interface ImageEditorSubmitter {
    suspend fun submit(
        target: ImageEditorTarget,
        imageBytes: ByteArray,
        fileName: String,
    ): Result<ImageEditorSubmission>

    suspend fun submit(
        target: ImageEditorTarget,
        imageBytes: ByteArray,
        fileName: String,
        onProgress: (ImageEditorSubmissionProgress) -> Unit,
    ): Result<ImageEditorSubmission> = submit(target, imageBytes, fileName)

    suspend fun retry(
        target: ImageEditorTarget,
        imageBytes: ByteArray,
        fileName: String,
        previousFailure: Throwable,
        onProgress: (ImageEditorSubmissionProgress) -> Unit,
    ): Result<ImageEditorSubmission> = submit(target, imageBytes, fileName, onProgress)
}

internal val ImageEditorTarget.AvatarGallery.canvasSpec: PrintCanvasSpec
    get() = GalleryCanvasSpec

sealed interface ImageEditorSubmissionProgress {
    data class Upload(val bytesSent: Long, val totalBytes: Long?) : ImageEditorSubmissionProgress
    data object Refreshing : ImageEditorSubmissionProgress
}

internal class NetworkImageEditorSubmitter(
    private val printUploader: PrintUploader,
    private val avatarEditor: AvatarEditor,
    private val galleryDataSource: GalleryDataSource,
    private val worldImageEditor: WorldImageEditor,
    private val avatarGalleryUploader: AvatarGalleryUploader? = null,
) : ImageEditorSubmitter {
    override suspend fun submit(
        target: ImageEditorTarget,
        imageBytes: ByteArray,
        fileName: String,
    ): Result<ImageEditorSubmission> = when (target) {
        ImageEditorTarget.Print ->
            printUploader.upload(imageBytes, fileName).map { ImageEditorSubmission.Print }

        is ImageEditorTarget.AvatarCover -> avatarEditor.updateCover(
            avatarId = target.avatarId,
            cover = AvatarCoverFile(
                bytes = imageBytes,
                fileName = fileName,
                mimeType = "image/png",
            ),
        ).map(ImageEditorSubmission::AvatarCover)

        is ImageEditorTarget.AvatarGallery -> error("Avatar Gallery submissions require progress callbacks")

        is ImageEditorTarget.WorldCover -> worldImageEditor.updateImage(
            sessionToken = target.sessionToken,
            worldId = target.worldId,
            image = WorldImageFile(
                bytes = imageBytes,
                fileName = fileName,
                mimeType = "image/png",
            ),
        ).map(ImageEditorSubmission::WorldCover)

        is ImageEditorTarget.Gallery -> galleryDataSource.uploadImage(
            fileBytes = imageBytes,
            fileName = fileName,
            mimeType = "image/png",
            tagType = target.tagType,
        ).map { ImageEditorSubmission.Gallery(target.tagType) }
    }

    override suspend fun submit(
        target: ImageEditorTarget,
        imageBytes: ByteArray,
        fileName: String,
        onProgress: (ImageEditorSubmissionProgress) -> Unit,
    ): Result<ImageEditorSubmission> = when (target) {
        is ImageEditorTarget.AvatarGallery -> requireNotNull(avatarGalleryUploader)
            .uploadAndRefresh(
                target = target.target,
                imageBytes = imageBytes,
                fileName = fileName,
                mimeType = "image/png",
                onUploadProgress = { sent, total ->
                    onProgress(ImageEditorSubmissionProgress.Upload(sent, total))
                },
                onRefreshing = { onProgress(ImageEditorSubmissionProgress.Refreshing) },
            ).map(ImageEditorSubmission::AvatarGallery)
        else -> submit(target, imageBytes, fileName)
    }

    override suspend fun retry(
        target: ImageEditorTarget,
        imageBytes: ByteArray,
        fileName: String,
        previousFailure: Throwable,
        onProgress: (ImageEditorSubmissionProgress) -> Unit,
    ): Result<ImageEditorSubmission> {
        if (target is ImageEditorTarget.AvatarGallery &&
            previousFailure is AvatarGalleryUploadFailure.Refresh
        ) {
            return requireNotNull(avatarGalleryUploader).refresh(
                pending = previousFailure.pending,
                onRefreshing = { onProgress(ImageEditorSubmissionProgress.Refreshing) },
            ).map(ImageEditorSubmission::AvatarGallery)
        }
        return submit(target, imageBytes, fileName, onProgress)
    }
}
