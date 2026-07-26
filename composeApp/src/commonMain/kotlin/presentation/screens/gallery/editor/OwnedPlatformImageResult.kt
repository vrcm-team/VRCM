package io.github.vrcmteam.vrcm.presentation.screens.gallery.editor

import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

internal inline fun <T> handoffLocalPlatformImageBitmap(
    createBitmap: () -> ImageBitmap,
    noinline releaseBitmap: (ImageBitmap) -> Unit = ::releasePlatformImageBitmap,
    block: (ImageBitmap) -> T,
): T {
    val bitmap = createBitmap()
    return try {
        block(bitmap)
    } catch (cause: CancellationException) {
        releaseAfterOwnedResultFailure(bitmap, cause, releaseBitmap)
        throw cause
    } catch (cause: Exception) {
        releaseAfterOwnedResultFailure(bitmap, cause, releaseBitmap)
        throw cause
    } catch (cause: Error) {
        releaseAfterOwnedResultFailure(bitmap, cause, releaseBitmap)
        throw cause
    }
}

internal suspend fun <T> withOwnedPlatformImageResult(
    dispatcher: CoroutineDispatcher,
    ownedBitmap: (T) -> ImageBitmap,
    releaseBitmap: (ImageBitmap) -> Unit = ::releasePlatformImageBitmap,
    block: suspend () -> T,
): T {
    var pendingBitmap: ImageBitmap? = null
    var workerFailure: Throwable? = null
    var primaryFailure: Throwable? = null
    try {
        val result = withContext(dispatcher) {
            try {
                block().also { pendingBitmap = ownedBitmap(it) }
            } catch (cause: CancellationException) {
                workerFailure = cause
                throw cause
            } catch (cause: Exception) {
                workerFailure = cause
                throw cause
            } catch (cause: Error) {
                workerFailure = cause
                throw cause
            }
        }
        pendingBitmap = null
        return result
    } catch (cause: CancellationException) {
        val failure = workerFailure ?: cause
        primaryFailure = failure
        throw failure
    } catch (cause: Exception) {
        val failure = workerFailure ?: cause
        primaryFailure = failure
        throw failure
    } catch (cause: Error) {
        val failure = workerFailure ?: cause
        primaryFailure = failure
        throw failure
    } finally {
        pendingBitmap?.let { bitmap ->
            val failure = primaryFailure
            if (failure == null) {
                releaseBitmap(bitmap)
            } else {
                releaseAfterOwnedResultFailure(bitmap, failure, releaseBitmap)
            }
        }
    }
}

private fun releaseAfterOwnedResultFailure(
    bitmap: ImageBitmap,
    primaryFailure: Throwable,
    releaseBitmap: (ImageBitmap) -> Unit,
) {
    try {
        releaseBitmap(bitmap)
    } catch (releaseFailure: CancellationException) {
        primaryFailure.addOwnedResultReleaseFailure(releaseFailure)
    } catch (releaseFailure: Exception) {
        primaryFailure.addOwnedResultReleaseFailure(releaseFailure)
    } catch (releaseFailure: Error) {
        primaryFailure.addOwnedResultReleaseFailure(releaseFailure)
    }
}

private fun Throwable.addOwnedResultReleaseFailure(releaseFailure: Throwable) {
    if (releaseFailure !== this) addSuppressed(releaseFailure)
}
