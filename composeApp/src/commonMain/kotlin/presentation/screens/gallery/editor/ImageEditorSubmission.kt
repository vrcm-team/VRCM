package io.github.vrcmteam.vrcm.presentation.screens.gallery.editor

import io.github.vrcmteam.vrcm.network.api.avatars.data.AvatarData
import io.github.vrcmteam.vrcm.presentation.screens.avatar.AvatarCoverFile
import io.github.vrcmteam.vrcm.presentation.screens.avatar.AvatarEditor
import io.github.vrcmteam.vrcm.service.PrintUploader

sealed interface ImageEditorTarget {
    data object Print : ImageEditorTarget
    data class AvatarCover(val avatarId: String) : ImageEditorTarget
}

sealed interface ImageEditorSubmission {
    data object Print : ImageEditorSubmission
    data class AvatarCover(val avatar: AvatarData) : ImageEditorSubmission
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
    }
}
