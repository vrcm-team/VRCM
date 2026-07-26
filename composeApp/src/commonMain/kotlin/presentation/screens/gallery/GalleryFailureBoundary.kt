package io.github.vrcmteam.vrcm.presentation.screens.gallery

import kotlinx.coroutines.CancellationException

internal inline fun <T> runGalleryCatching(block: () -> T): Result<T> = try {
    Result.success(block())
} catch (cancellation: CancellationException) {
    throw cancellation
} catch (exception: Exception) {
    Result.failure(exception)
}
