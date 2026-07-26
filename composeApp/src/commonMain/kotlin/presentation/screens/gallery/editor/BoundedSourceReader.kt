package io.github.vrcmteam.vrcm.presentation.screens.gallery.editor

import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.size
import io.github.vinceglb.filekit.source
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.io.Buffer
import kotlinx.io.RawSource
import kotlinx.io.readByteArray

internal suspend fun PlatformFile.readBoundedBytes(maxBytes: Long): ByteArray =
    withContext(Dispatchers.IO) {
        val coroutineContext = currentCoroutineContext()
        readBoundedBytes(
            declaredSize = readDeclaredFileSize { size() },
            maxBytes = maxBytes,
            openSource = { source() },
            ensureActive = { coroutineContext.ensureActive() },
        )
    }

internal inline fun readDeclaredFileSize(readSize: () -> Long): Long? = try {
    readSize()
} catch (cause: CancellationException) {
    throw cause
} catch (_: Exception) {
    null
}

internal fun readBoundedBytes(
    declaredSize: Long?,
    maxBytes: Long,
    openSource: () -> RawSource,
    ensureActive: () -> Unit = {},
): ByteArray {
    require(maxBytes in 0 until Int.MAX_VALUE.toLong())
    if (declaredSize != null && declaredSize > maxBytes) {
        throw PrintImageFailure.FileTooLarge
    }

    val buffer = Buffer()
    return openSource().use { source ->
        var remaining = maxBytes + 1
        while (remaining > 0) {
            ensureActive()
            val readCount = source.readAtMostTo(buffer, remaining)
            if (readCount == -1L) return buffer.readByteArray()
            remaining -= readCount
        }
        throw PrintImageFailure.FileTooLarge
    }
}
