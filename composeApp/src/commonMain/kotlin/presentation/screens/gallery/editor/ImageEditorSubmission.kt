package io.github.vrcmteam.vrcm.presentation.screens.gallery.editor

import io.github.vrcmteam.vrcm.network.api.avatars.data.AvatarData
import io.github.vrcmteam.vrcm.network.api.files.data.FileTagType
import io.github.vrcmteam.vrcm.presentation.screens.avatar.AvatarCoverFile
import io.github.vrcmteam.vrcm.presentation.screens.avatar.AvatarEditor
import io.github.vrcmteam.vrcm.presentation.screens.gallery.GalleryDataSource
import io.github.vrcmteam.vrcm.service.PrintUploader

sealed interface ImageEditorTarget {
    data object Print : ImageEditorTarget
    data class AvatarCover(val avatarId: String) : ImageEditorTarget
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

sealed interface ImageEditorSubmission {
    data object Print : ImageEditorSubmission
    data class AvatarCover(val avatar: AvatarData) : ImageEditorSubmission
    data class Gallery(val tagType: FileTagType) : ImageEditorSubmission
}

interface ImageEditorSubmitter {
    suspend fun submit(
        target: ImageEditorTarget,
        imageBytes: ByteArray,
        fileName: String,
    ): Result<ImageEditorSubmission>
}

internal class NetworkImageEditorSubmitter(
    private val printUploader: PrintUploader,
    private val avatarEditor: AvatarEditor,
    private val galleryDataSource: GalleryDataSource,
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

        is ImageEditorTarget.Gallery -> galleryDataSource.uploadImage(
            fileBytes = imageBytes,
            fileName = fileName,
            mimeType = "image/png",
            tagType = target.tagType,
        ).map { ImageEditorSubmission.Gallery(target.tagType) }
    }
}
