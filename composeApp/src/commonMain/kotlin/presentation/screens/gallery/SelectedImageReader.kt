package io.github.vrcmteam.vrcm.presentation.screens.gallery

import io.github.vrcmteam.vrcm.presentation.screens.gallery.editor.SelectedImage
import kotlinx.coroutines.CancellationException

internal suspend fun readSelectedImage(
    fileName: String,
    readBytes: suspend () -> ByteArray,
): Result<SelectedImage> = try {
    Result.success(
        SelectedImage(
            fileName = fileName,
            bytes = readBytes(),
        ),
    )
} catch (cause: CancellationException) {
    throw cause
} catch (cause: Exception) {
    Result.failure(cause)
}
