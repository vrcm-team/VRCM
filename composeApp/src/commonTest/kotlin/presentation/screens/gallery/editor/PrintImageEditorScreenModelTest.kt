package io.github.vrcmteam.vrcm.presentation.screens.gallery.editor

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageBitmapConfig
import androidx.compose.ui.graphics.colorspace.ColorSpace
import androidx.compose.ui.graphics.colorspace.ColorSpaces
import io.github.vrcmteam.vrcm.network.api.prints.data.PrintData
import io.github.vrcmteam.vrcm.service.PrintUploader
import io.github.vrcmteam.vrcm.service.PrintUploadFailure
import io.github.vrcmteam.vrcm.testing.MainDispatcherTest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PrintImageEditorScreenModelTest : MainDispatcherTest() {
    @Test
    fun repeatedUploadWhileProcessingStartsOneJob() = runBlocking {
        val gate = CompletableDeferred<Result<ByteArray>>()
        val processor = FakePrintImageProcessor { _, _, _ -> gate.await() }
        val uploader = FakePrintUploader { _, _ -> Result.success(PrintData("print")) }
        val model = createModel(processor, uploader)

        model.upload()
        model.upload()

        assertEquals(EditorPhase.Processing, model.state.value.phase)
        assertEquals(1, processor.renderCount)
        gate.complete(Result.success(PNG_BYTES))
        yield()

        assertEquals(1, uploader.uploadCount)
    }

    @Test
    fun processingFailureReturnsToReadyAndKeepsTransform() = runBlocking {
        val failure = PrintImageFailure.RenderFailed(IllegalStateException("render"))
        val processor = FakePrintImageProcessor { _, _, _ -> Result.failure(failure) }
        val uploader = FakePrintUploader { _, _ -> Result.success(PrintData("unused")) }
        val model = createModel(processor, uploader)
        model.panAndZoom(VIEWPORT, panX = 40f, panY = 0f, zoomChange = 1.5f)
        val edited = model.state.value.transform

        model.upload()
        yield()

        assertEquals(EditorPhase.Ready, model.state.value.phase)
        assertEquals(edited, model.state.value.transform)
        assertIs<EditorError.Processing>(model.state.value.error)
        assertEquals(0, uploader.uploadCount)
    }

    @Test
    fun networkRetryReusesRenderedPng() = runBlocking {
        val processor = FakePrintImageProcessor { _, _, _ -> Result.success(PNG_BYTES) }
        var attempt = 0
        val uploader = FakePrintUploader { _, _ ->
            attempt++
            if (attempt == 1) Result.failure(IllegalStateException("offline"))
            else Result.success(PrintData("print"))
        }
        val model = createModel(processor, uploader)
        val uploaded = async(start = CoroutineStart.UNDISPATCHED) { model.events.first() }

        model.upload()
        yield()
        assertEquals(EditorPhase.Ready, model.state.value.phase)
        val error = assertIs<EditorError.Upload>(model.state.value.error)
        assertIs<PrintUploadFailure.Unknown>(error.failure)

        model.upload()
        yield()

        assertEquals(1, processor.renderCount)
        assertEquals(2, uploader.uploadCount)
        assertEquals(EditorEvent.Uploaded, uploaded.await())
    }

    @Test
    fun directUploadFailureReturnsToReadyAndRetryReusesRenderedPng() = runBlocking {
        val processor = FakePrintImageProcessor { _, _, _ -> Result.success(PNG_BYTES) }
        var attempt = 0
        val uploader = FakePrintUploader { _, _ ->
            attempt++
            if (attempt == 1) throw IllegalStateException("upload")
            Result.success(PrintData("print"))
        }
        val model = createModel(processor, uploader)
        val uploaded = async(start = CoroutineStart.UNDISPATCHED) { model.events.first() }

        model.upload()
        yield()

        assertEquals(EditorPhase.Ready, model.state.value.phase)
        val error = assertIs<EditorError.Upload>(model.state.value.error)
        assertIs<PrintUploadFailure.Unknown>(error.failure)

        model.upload()
        yield()

        assertEquals(1, processor.renderCount)
        assertEquals(2, uploader.uploadCount)
        assertEquals(EditorEvent.Uploaded, uploaded.await())
    }

    @Test
    fun cancellationDoesNotCreateErrorOrResetUploadingPhase() = runBlocking {
        val processor = FakePrintImageProcessor { _, _, _ -> Result.success(PNG_BYTES) }
        val uploader = FakePrintUploader { _, _ -> throw CancellationException("cancelled") }
        val model = createModel(processor, uploader)

        model.upload()
        yield()

        assertEquals(EditorPhase.Uploading, model.state.value.phase)
        assertNull(model.state.value.error)
    }

    @Test
    fun fatalProcessingFailureIsNotConvertedToEditorError() = runBlocking {
        val fatal = AssertionError("fatal")
        val processor = FakePrintImageProcessor { _, _, _ -> Result.failure(fatal) }
        val uploader = FakePrintUploader { _, _ -> Result.success(PrintData("unused")) }
        var uncaught: Throwable? = null
        val workerExceptionHandler = CoroutineExceptionHandler { _, cause ->
            uncaught = cause
        }
        val model = createModel(
            processor,
            uploader,
            workerExceptionHandler = workerExceptionHandler,
        )

        model.upload()
        yield()

        assertTrue(uncaught === fatal)
        assertEquals(EditorPhase.Processing, model.state.value.phase)
        assertNull(model.state.value.error)
    }

    @Test
    fun editingAfterNetworkFailureInvalidatesRenderedPng() = runBlocking {
        val processor = FakePrintImageProcessor { _, _, _ -> Result.success(PNG_BYTES) }
        var attempt = 0
        val uploader = FakePrintUploader { _, _ ->
            attempt++
            if (attempt == 1) Result.failure(IllegalStateException("offline"))
            else Result.success(PrintData("print"))
        }
        val model = createModel(processor, uploader)

        model.upload()
        yield()
        model.panAndZoom(VIEWPORT, panX = 30f, panY = 0f, zoomChange = 1f)
        model.upload()
        yield()

        assertEquals(2, processor.renderCount)
        assertEquals(2, uploader.uploadCount)
    }

    @Test
    fun successfulUploadUsesPngFileNameAndEmitsCompletion() = runBlocking {
        val processor = FakePrintImageProcessor { _, _, _ -> Result.success(PNG_BYTES) }
        val uploader = FakePrintUploader { _, _ -> Result.success(PrintData("print")) }
        val model = createModel(processor, uploader)
        val uploaded = async(start = CoroutineStart.UNDISPATCHED) { model.events.first() }

        model.upload()
        yield()

        assertEquals(EditorEvent.Uploaded, uploaded.await())
        assertTrue(requireNotNull(uploader.lastFileName).startsWith("print-"))
        assertTrue(requireNotNull(uploader.lastFileName).endsWith(".png"))
    }

    @Test
    fun disposingEditorModelReleasesSessionData() {
        val store = PrintImageEditorSessionStore()
        val source = SelectedImage("source.jpg", byteArrayOf(1))
        val prepared = PreparedImage(TestImageBitmap, ImageSize(1_920, 1_080))
        val released = mutableListOf<ImageBitmap>()
        val sessionId = store.create(source, prepared)
        val model = PrintImageEditorScreenModel(
            source = source,
            prepared = prepared,
            calculator = CropTransformCalculator(),
            processor = FakePrintImageProcessor { _, _, _ -> Result.success(PNG_BYTES) },
            uploader = FakePrintUploader { _, _ -> Result.success(PrintData("print")) },
            sessionId = sessionId,
            sessionStore = store,
            workerDispatcher = Dispatchers.Unconfined,
            releasePreview = released::add,
        )

        store.complete(sessionId)
        assertEquals(emptyList(), released)
        model.onDispose()
        model.onDispose()

        assertNull(store.get(sessionId))
        assertEquals(listOf<ImageBitmap>(TestImageBitmap), released)
    }

    @Test
    fun uploadPassesPreparedOriginalSizeToRenderer() = runBlocking {
        val processor = FakePrintImageProcessor { _, _, _ -> Result.success(PNG_BYTES) }
        val uploader = FakePrintUploader { _, _ -> Result.success(PrintData("print")) }
        val originalSize = ImageSize(6_013, 4_007)
        val model = createModel(processor, uploader, originalSize)

        model.upload()
        yield()

        assertEquals(listOf(originalSize), processor.originalSizes)
    }

    private fun createModel(
        processor: PrintImageProcessor,
        uploader: PrintUploader,
        originalSize: ImageSize = ImageSize(1_920, 1_080),
        workerDispatcher: CoroutineDispatcher = Dispatchers.Unconfined,
        workerExceptionHandler: CoroutineExceptionHandler? = null,
    ) = PrintImageEditorScreenModel(
        source = SelectedImage("source.jpg", byteArrayOf(1)),
        prepared = PreparedImage(TestImageBitmap, originalSize),
        calculator = CropTransformCalculator(),
        processor = processor,
        uploader = uploader,
        sessionId = "test-session",
        sessionStore = PrintImageEditorSessionStore(),
        workerDispatcher = workerDispatcher,
        workerExceptionHandler = workerExceptionHandler,
        nowMillis = { 123L },
    )

    private companion object {
        val VIEWPORT = ImageSize(1_600, 900)
        val PNG_BYTES = byteArrayOf(1, 2, 3)
    }
}

private data object TestImageBitmap : ImageBitmap {
    override val width: Int = 16
    override val height: Int = 9
    override val colorSpace: ColorSpace = ColorSpaces.Srgb
    override val hasAlpha: Boolean = true
    override val config: ImageBitmapConfig = ImageBitmapConfig.Argb8888

    override fun readPixels(
        buffer: IntArray,
        startX: Int,
        startY: Int,
        width: Int,
        height: Int,
        bufferOffset: Int,
        stride: Int,
    ) = error("The editor state-machine tests do not read preview pixels")

    override fun prepareToDraw() = Unit
}

private class FakePrintImageProcessor(
    private val renderBlock: suspend (SelectedImage, ImageSize, CropTransform) -> Result<ByteArray>,
) : PrintImageProcessor {
    var renderCount = 0
    val originalSizes = mutableListOf<ImageSize>()

    override suspend fun prepare(source: SelectedImage): Result<PreparedImage> =
        error("prepare is not used by the editor model")

    override suspend fun render(
        source: SelectedImage,
        originalSize: ImageSize,
        transform: CropTransform,
    ): Result<ByteArray> {
        renderCount++
        originalSizes += originalSize
        return renderBlock(source, originalSize, transform)
    }
}

private class FakePrintUploader(
    private val uploadBlock: suspend (ByteArray, String) -> Result<PrintData>,
) : PrintUploader {
    var uploadCount = 0
    var lastFileName: String? = null

    override suspend fun upload(imageBytes: ByteArray, fileName: String): Result<PrintData> {
        uploadCount++
        lastFileName = fileName
        return uploadBlock(imageBytes, fileName)
    }
}
