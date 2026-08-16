package io.github.vrcmteam.vrcm.presentation.screens.gallery.editor

import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
    data class Submission(val failure: Throwable) : EditorError
}

sealed interface EditorEvent {
    data class Submitted(val submission: ImageEditorSubmission) : EditorEvent
}

data class PrintImageEditorState(
    val prepared: PreparedImage,
    val transform: CropTransform = CropTransform(),
    val fillWhiteBorder: Boolean = true,
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
    private val submitter: ImageEditorSubmitter,
    private val target: ImageEditorTarget,
    private val sessionId: String,
    private val sessionStore: PrintImageEditorSessionStore,
    private val workerDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val workerExceptionHandler: CoroutineExceptionHandler? = null,
    private val nowMillis: () -> Long = { Clock.System.now().toEpochMilliseconds() },
    private val releasePreview: (ImageBitmap) -> Unit = ::releasePlatformImageBitmap,
) : ViewModel() {
    private val _state = MutableStateFlow(PrintImageEditorState(prepared = prepared))
    val state: StateFlow<PrintImageEditorState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<EditorEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<EditorEvent> = _events.asSharedFlow()

    private var cachedPng: ByteArray? = null
    private var cachedFileName: String? = null
    private val previewReleaseController = PreviewBitmapReleaseController(
        bitmap = prepared.preview,
        releaseBitmap = releasePreview,
    )

    override fun onCleared() {
        sessionStore.discard(sessionId)
        previewReleaseController.dispose()
    }

    fun acquirePreviewDisplayLease() = previewReleaseController.acquireDisplayLease()

    fun releasePreviewDisplayLease() = previewReleaseController.releaseDisplayLease()

    fun panAndZoom(
        viewport: ImageSize,
        panX: Float,
        panY: Float,
        zoomChange: Float,
    ) = edit(viewport) { current ->
        current.constrainToBorderMode(
            calculator.transform(
                source = current.prepared.originalSize,
                viewport = viewport,
                current = current.transform,
                panX = panX,
                panY = panY,
                zoomChange = zoomChange,
            ),
            viewport,
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

    fun rotateLeft(viewport: ImageSize) = edit(viewport) { current ->
        current.rotateForBorderMode(viewport, turns = -1)
    }

    fun rotateRight(viewport: ImageSize) = edit(viewport) { current ->
        current.rotateForBorderMode(viewport, turns = 1)
    }

    fun flipHorizontal() = edit { calculator.flipHorizontal(it.transform) }

    fun flipVertical() = edit { calculator.flipVertical(it.transform) }

    fun reset(viewport: ImageSize) = edit(viewport) { current ->
        current.constrainToBorderMode(calculator.reset(), viewport)
    }

    fun setFillWhiteBorder(enabled: Boolean, viewport: ImageSize) {
        val current = _state.value
        if (current.isBusy || !viewport.isValid()) return
        val zoom = if (enabled) {
            1f
        } else {
            calculator.zoomLimits(
                source = current.prepared.originalSize,
                viewport = viewport,
                quarterTurns = current.transform.quarterTurns,
            ).cover
        }
        cachedPng = null
        cachedFileName = null
        _state.value = current.copy(
            transform = current.transform.copy(
                centerOffsetX = 0f,
                centerOffsetY = 0f,
                zoom = zoom,
            ),
            fillWhiteBorder = enabled,
            error = null,
        )
    }

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
        viewModelScope.launch(workerContext()) {
            var stage = if (existingPng == null) UploadStage.Processing else UploadStage.Uploading
            try {
                val png = existingPng ?: processor.render(
                    source = source,
                    originalSize = current.prepared.originalSize,
                    transform = current.transform,
                    background = target.canvasBackground,
                )
                    .getOrThrow()
                    .also {
                        cachedPng = it
                        cachedFileName = target.uploadFileName(source.fileName, nowMillis())
                    }

                stage = UploadStage.Uploading
                _state.update { it.copy(phase = EditorPhase.Uploading, error = null) }
                val submission = submitter.submit(
                    target = target,
                    imageBytes = png,
                    fileName = cachedFileName
                        ?: target.uploadFileName(source.fileName, nowMillis()),
                ).getOrThrow()

                _state.update { it.copy(phase = EditorPhase.Ready, error = null) }
                _events.emit(EditorEvent.Submitted(submission))
            } catch (cause: CancellationException) {
                throw cause
            } catch (cause: Exception) {
                val error = when (stage) {
                    UploadStage.Processing -> EditorError.Processing(
                        cause as? PrintImageFailure ?: PrintImageFailure.RenderFailed(cause),
                    )
                    UploadStage.Uploading -> EditorError.Submission(
                        if (target == ImageEditorTarget.Print) {
                            cause.toPrintUploadFailure()
                        } else {
                            cause
                        },
                    )
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

    private inline fun edit(
        viewport: ImageSize? = null,
        updateTransform: (PrintImageEditorState) -> CropTransform,
    ) {
        val current = _state.value
        if (current.isBusy || (viewport != null && !viewport.isValid())) return
        cachedPng = null
        cachedFileName = null
        _state.value = current.copy(
            transform = updateTransform(current),
            error = null,
        )
    }

    private fun PrintImageEditorState.constrainToBorderMode(
        candidate: CropTransform,
        viewport: ImageSize,
    ): CropTransform {
        if (fillWhiteBorder) return candidate
        val cover = calculator.zoomLimits(
            source = prepared.originalSize,
            viewport = viewport,
            quarterTurns = candidate.quarterTurns,
        ).cover
        if (candidate.zoom >= cover) return candidate
        return calculator.transform(
            source = prepared.originalSize,
            viewport = viewport,
            current = candidate.copy(zoom = cover),
            panX = 0f,
            panY = 0f,
            zoomChange = 1f,
        )
    }

    private fun PrintImageEditorState.rotateForBorderMode(
        viewport: ImageSize,
        turns: Int,
    ): CropTransform {
        val rotated = calculator.rotate(
            source = prepared.originalSize,
            viewport = viewport,
            current = transform,
            turns = turns,
        )
        if (fillWhiteBorder) return rotated

        val previousCover = calculator.zoomLimits(
            source = prepared.originalSize,
            viewport = viewport,
            quarterTurns = transform.quarterTurns,
        ).cover
        val rotatedLimits = calculator.zoomLimits(
            source = prepared.originalSize,
            viewport = viewport,
            quarterTurns = rotated.quarterTurns,
        )
        val relativeZoom = transform.zoom / previousCover
        return calculator.transform(
            source = prepared.originalSize,
            viewport = viewport,
            current = rotated.copy(
                zoom = (rotatedLimits.cover * relativeZoom)
                    .coerceIn(rotatedLimits.cover, rotatedLimits.maximum),
            ),
            panX = 0f,
            panY = 0f,
            zoomChange = 1f,
        )
    }
}

private fun ImageSize.isValid(): Boolean = width > 0 && height > 0

private fun ImageEditorTarget.uploadFileName(sourceFileName: String, nowMillis: Long): String = when (this) {
    ImageEditorTarget.Print -> "print-$nowMillis.png"
    is ImageEditorTarget.AvatarCover -> "avatar-cover-$nowMillis.png"
    is ImageEditorTarget.Gallery -> sourceFileName.ifBlank { "${tagType.value}-$nowMillis.png" }
}
