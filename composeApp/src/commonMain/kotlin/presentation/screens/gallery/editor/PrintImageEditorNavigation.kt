package io.github.vrcmteam.vrcm.presentation.screens.gallery.editor

import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.CancellationException

internal fun handoffPreparedImageToEditor(
    source: SelectedImage,
    prepared: PreparedImage,
    sessionStore: PrintImageEditorSessionStore,
    target: ImageEditorTarget = ImageEditorTarget.Print,
    croppedSource: PreparedImageSource? = null,
    releasePreview: (ImageBitmap) -> Unit = ::releasePlatformImageBitmap,
    push: (String) -> Unit,
) {
    var sessionId: String? = null
    try {
        val createdSessionId = sessionStore.create(
            source = source,
            prepared = prepared,
            target = target,
            croppedSource = croppedSource,
        )
        sessionId = createdSessionId
        push(createdSessionId)
    } catch (cause: CancellationException) {
        releaseFailedNavigationPreviews(
            prepared,
            croppedSource,
            sessionId,
            sessionStore,
            cause,
            releasePreview,
        )
        throw cause
    } catch (cause: Exception) {
        releaseFailedNavigationPreviews(
            prepared,
            croppedSource,
            sessionId,
            sessionStore,
            cause,
            releasePreview,
        )
        throw cause
    } catch (cause: Error) {
        releaseFailedNavigationPreviews(
            prepared,
            croppedSource,
            sessionId,
            sessionStore,
            cause,
            releasePreview,
        )
        throw cause
    }
}

private fun releaseFailedNavigationPreviews(
    prepared: PreparedImage,
    croppedSource: PreparedImageSource?,
    sessionId: String?,
    sessionStore: PrintImageEditorSessionStore,
    primaryFailure: Throwable,
    releasePreview: (ImageBitmap) -> Unit,
) {
    sessionId?.let(sessionStore::discard)
    val previews = buildList {
        add(prepared.preview)
        croppedSource?.prepared?.preview
            ?.takeIf { it !== prepared.preview }
            ?.let(::add)
    }
    previews.forEach { preview ->
        try {
            releasePreview(preview)
        } catch (releaseFailure: CancellationException) {
            primaryFailure.addNavigationReleaseFailure(releaseFailure)
        } catch (releaseFailure: Exception) {
            primaryFailure.addNavigationReleaseFailure(releaseFailure)
        } catch (releaseFailure: Error) {
            primaryFailure.addNavigationReleaseFailure(releaseFailure)
        }
    }
}

private fun Throwable.addNavigationReleaseFailure(releaseFailure: Throwable) {
    if (releaseFailure !== this) addSuppressed(releaseFailure)
}
