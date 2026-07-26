package io.github.vrcmteam.vrcm.presentation.screens.gallery.editor

import androidx.compose.ui.graphics.ImageBitmap
import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import io.github.vrcmteam.vrcm.service.PrintUploader
import io.github.vrcmteam.vrcm.service.PrintUploadFailure
import io.github.vrcmteam.vrcm.service.toPrintUploadFailure
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

enum class EditorPhase {
    Ready,
    Processing,
    Uploading,
}

private enum class UploadStage {
    Processing,
    Uploading,
}

sealed interface EditorError {
    data class Processing(val failure: PrintImageFailure) : EditorError
    data class Upload(val failure: PrintUploadFailure) : EditorError
}

sealed interface EditorEvent {
    data object Uploaded : EditorEvent
}

data class PrintImageEditorState(
    val prepared: PreparedImage,
    val transform: CropTransform = CropTransform(),
    val phase: EditorPhase = EditorPhase.Ready,
    val error: EditorError? = null,
) {
    val isBusy: Boolean get() = phase != EditorPhase.Ready
}

@OptIn(ExperimentalTime::class)
class PrintImageEditorScreenModel(
    private val source: SelectedImage,
    prepared: PreparedImage,
    private val calculator: CropTransformCalculator,
    private val processor: PrintImageProcessor,
    private val uploader: PrintUploader,
    private val sessionId: String,
    private val sessionStore: PrintImageEditorSessionStore,
    private val workerDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val workerExceptionHandler: CoroutineExceptionHandler? = null,
    private val nowMillis: () -> Long = { Clock.System.now().toEpochMilliseconds() },
    private val releasePreview: (ImageBitmap) -> Unit = ::releasePlatformImageBitmap,
) : ScreenModel {
    private val _state = MutableStateFlow(PrintImageEditorState(prepared = prepared))
    val state: StateFlow<PrintImageEditorState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<EditorEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<EditorEvent> = _events.asSharedFlow()

    private var cachedPng: ByteArray? = null
    private var cachedFileName: String? = null
    private var previewReleased = false

    override fun onDispose() {
        sessionStore.discard(sessionId)
        if (!previewReleased) {
            previewReleased = true
            releasePreview(_state.value.prepared.preview)
        }
    }

    fun panAndZoom(
        viewport: ImageSize,
        panX: Float,
        panY: Float,
        zoomChange: Float,
    ) = edit { current ->
        calculator.transform(
            source = current.prepared.originalSize,
            viewport = viewport,
            current = current.transform,
            panX = panX,
            panY = panY,
            zoomChange = zoomChange,
        )
    }

    fun setZoom(viewport: ImageSize, zoom: Float) {
        val currentZoom = _state.value.transform.zoom
        panAndZoom(
            viewport = viewport,
            panX = 0f,
            panY = 0f,
            zoomChange = zoom / currentZoom,
        )
    }

    fun rotateLeft(viewport: ImageSize) = edit { current ->
        calculator.rotate(
            source = current.prepared.originalSize,
            viewport = viewport,
            current = current.transform,
            turns = -1,
        )
    }

    fun rotateRight(viewport: ImageSize) = edit { current ->
        calculator.rotate(
            source = current.prepared.originalSize,
            viewport = viewport,
            current = current.transform,
            turns = 1,
        )
    }

    fun flipHorizontal() = edit { calculator.flipHorizontal(it.transform) }

    fun flipVertical() = edit { calculator.flipVertical(it.transform) }

    fun reset() = edit { calculator.reset() }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }

    fun upload() {
        val current = _state.value
        if (current.isBusy) return

        val existingPng = cachedPng
        _state.value = current.copy(
            phase = if (existingPng == null) EditorPhase.Processing else EditorPhase.Uploading,
            error = null,
        )
        screenModelScope.launch(workerContext()) {
            var stage = if (existingPng == null) UploadStage.Processing else UploadStage.Uploading
            try {
                val png = existingPng ?: processor.render(
                    source = source,
                    originalSize = current.prepared.originalSize,
                    transform = current.transform,
                )
                    .getOrThrow()
                    .also {
                        cachedPng = it
                        cachedFileName = "print-${nowMillis()}.png"
                    }

                stage = UploadStage.Uploading
                _state.update { it.copy(phase = EditorPhase.Uploading, error = null) }
                uploader.upload(png, cachedFileName ?: "print-${nowMillis()}.png").getOrThrow()

                _state.update { it.copy(phase = EditorPhase.Ready, error = null) }
                _events.emit(EditorEvent.Uploaded)
            } catch (cause: CancellationException) {
                throw cause
            } catch (cause: Exception) {
                val error = when (stage) {
                    UploadStage.Processing -> EditorError.Processing(
                        cause as? PrintImageFailure ?: PrintImageFailure.RenderFailed(cause),
                    )
                    UploadStage.Uploading -> EditorError.Upload(cause.toPrintUploadFailure())
                }

                _state.update {
                    it.copy(
                        phase = EditorPhase.Ready,
                        error = error,
                    )
                }
            }
        }
    }

    private fun workerContext(): CoroutineContext =
        workerExceptionHandler?.let(workerDispatcher::plus) ?: workerDispatcher

    private inline fun edit(updateTransform: (PrintImageEditorState) -> CropTransform) {
        val current = _state.value
        if (current.isBusy) return
        cachedPng = null
        cachedFileName = null
        _state.value = current.copy(
            transform = updateTransform(current),
            error = null,
        )
    }
}
