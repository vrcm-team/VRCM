package io.github.vrcmteam.vrcm.presentation.screens.gallery.editor

import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.CancellationException

internal fun handoffPreparedImageToEditor(
    source: SelectedImage,
    prepared: PreparedImage,
    sessionStore: PrintImageEditorSessionStore,
    releasePreview: (ImageBitmap) -> Unit = ::releasePlatformImageBitmap,
    push: (String) -> Unit,
) {
    var sessionId: String? = null
    try {
        val createdSessionId = sessionStore.create(source, prepared)
        sessionId = createdSessionId
        push(createdSessionId)
    } catch (cause: CancellationException) {
        releaseFailedNavigationPreview(prepared, sessionId, sessionStore, cause, releasePreview)
        throw cause
    } catch (cause: Exception) {
        releaseFailedNavigationPreview(prepared, sessionId, sessionStore, cause, releasePreview)
        throw cause
    } catch (cause: Error) {
        releaseFailedNavigationPreview(prepared, sessionId, sessionStore, cause, releasePreview)
        throw cause
    }
}

private fun releaseFailedNavigationPreview(
    prepared: PreparedImage,
    sessionId: String?,
    sessionStore: PrintImageEditorSessionStore,
    primaryFailure: Throwable,
    releasePreview: (ImageBitmap) -> Unit,
) {
    sessionId?.let(sessionStore::discard)
    try {
        releasePreview(prepared.preview)
    } catch (releaseFailure: CancellationException) {
        primaryFailure.addNavigationReleaseFailure(releaseFailure)
    } catch (releaseFailure: Exception) {
        primaryFailure.addNavigationReleaseFailure(releaseFailure)
    } catch (releaseFailure: Error) {
        primaryFailure.addNavigationReleaseFailure(releaseFailure)
    }
}

private fun Throwable.addNavigationReleaseFailure(releaseFailure: Throwable) {
    if (releaseFailure !== this) addSuppressed(releaseFailure)
}
