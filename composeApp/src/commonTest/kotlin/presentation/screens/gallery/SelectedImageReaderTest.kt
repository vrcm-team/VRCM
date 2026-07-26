package io.github.vrcmteam.vrcm.presentation.screens.gallery

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

class SelectedImageReaderTest {
    @Test
    fun successfulReadBuildsSelectedImage() = runBlocking {
        val bytes = byteArrayOf(1, 2, 3)

        val selected = readSelectedImage("photo.png") { bytes }.getOrThrow()

        assertEquals("photo.png", selected.fileName)
        assertContentEquals(bytes, selected.bytes)
    }

    @Test
    fun recoverableReadFailureIsReturned() = runBlocking {
        val failure = IllegalStateException("read failed")

        val result = readSelectedImage("photo.png") { throw failure }

        assertSame(failure, result.exceptionOrNull())
    }

    @Test
    fun cancellationAndFatalReadFailuresKeepIdentity() = runBlocking {
        val cancellation = CancellationException("cancelled")
        val fatal = AssertionError("fatal")

        val cancellationThrown = assertFailsWith<CancellationException> {
            readSelectedImage("photo.png") { throw cancellation }
        }
        val fatalThrown = assertFailsWith<AssertionError> {
            readSelectedImage("photo.png") { throw fatal }
        }

        assertSame(cancellation, cancellationThrown)
        assertSame(fatal, fatalThrown)
    }
}
