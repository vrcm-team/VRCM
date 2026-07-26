package io.github.vrcmteam.vrcm.presentation.screens.gallery.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.RotateLeft
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.withSave
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import io.github.vrcmteam.vrcm.core.shared.SharedFlowCentre
import io.github.vrcmteam.vrcm.presentation.compoments.ATooltipBox
import io.github.vrcmteam.vrcm.presentation.compoments.ToastText
import io.github.vrcmteam.vrcm.presentation.navigation.BlockSlideBackNavigation
import io.github.vrcmteam.vrcm.presentation.settings.locale.LocaleStrings
import io.github.vrcmteam.vrcm.presentation.settings.locale.strings
import io.github.vrcmteam.vrcm.presentation.supports.AppIcons
import io.github.vrcmteam.vrcm.service.PrintUploadFailure
import kotlinx.coroutines.flow.collectLatest
import org.koin.compose.koinInject
import org.koin.core.parameter.parametersOf
import kotlin.math.roundToInt

class PrintImageEditorScreen(
    private val sessionId: String,
) : Screen {
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val calculator: CropTransformCalculator = koinInject()
        val sessionStore: PrintImageEditorSessionStore = koinInject()
        val sessionAvailable = remember(sessionId) { sessionStore.get(sessionId) != null }
        val locale = strings
        val currentLocale by rememberUpdatedState(locale)

        if (!sessionAvailable) {
            LaunchedEffect(sessionId) {
                SharedFlowCentre.toastText.emit(ToastText.Error(locale.printEditorSessionExpired))
                navigator.pop()
            }
            Box(modifier = Modifier.fillMaxSize())
            return
        }

        val screenModel: PrintImageEditorScreenModel = koinScreenModel {
            parametersOf(sessionId)
        }
        val state by screenModel.state.collectAsState()
        val snackbarHostState = remember { SnackbarHostState() }

        BlockBackNavigation(enabled = state.isBusy)
        BlockSlideBackNavigation(blocked = state.isBusy)

        LaunchedEffect(screenModel) {
            screenModel.events.collectLatest { event ->
                if (event == EditorEvent.Uploaded) {
                    sessionStore.complete(sessionId)
                    SharedFlowCentre.toastText.emit(ToastText.Success(currentLocale.printEditorUploaded))
                    navigator.pop()
                }
            }
        }

        LaunchedEffect(state.error) {
            val error = state.error ?: return@LaunchedEffect
            snackbarHostState.showSnackbar(error.localizedMessage(locale))
            screenModel.clearError()
        }

        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = { Text(locale.printEditorTitle) },
                    navigationIcon = {
                        IconButton(
                            onClick = navigator::pop,
                            enabled = !state.isBusy,
                        ) {
                            Icon(
                                imageVector = AppIcons.ArrowBackIosNew,
                                contentDescription = locale.printEditorBack,
                            )
                        }
                    },
                    actions = {
                        ATooltipBox(tooltip = { Text(locale.printEditorUpload) }) {
                            FilledTonalIconButton(
                                onClick = screenModel::upload,
                                enabled = !state.isBusy,
                            ) {
                                if (state.isBusy) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp,
                                    )
                                } else {
                                    Icon(
                                        imageVector = AppIcons.Publish,
                                        contentDescription = locale.printEditorUpload,
                                    )
                                }
                            }
                        }
                    },
                )
            },
            snackbarHost = { SnackbarHost(snackbarHostState) },
        ) { paddingValues ->
            PrintEditorContent(
                state = state,
                calculator = calculator,
                onPanAndZoom = screenModel::panAndZoom,
                onSetZoom = screenModel::setZoom,
                onRotateLeft = screenModel::rotateLeft,
                onRotateRight = screenModel::rotateRight,
                onFlipHorizontal = screenModel::flipHorizontal,
                onFlipVertical = screenModel::flipVertical,
                onReset = screenModel::reset,
                locale = locale,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
            )
        }
    }
}

@Composable
private fun PrintEditorContent(
    state: PrintImageEditorState,
    calculator: CropTransformCalculator,
    onPanAndZoom: (ImageSize, Float, Float, Float) -> Unit,
    onSetZoom: (ImageSize, Float) -> Unit,
    onRotateLeft: (ImageSize) -> Unit,
    onRotateRight: (ImageSize) -> Unit,
    onFlipHorizontal: () -> Unit,
    onFlipVertical: () -> Unit,
    onReset: () -> Unit,
    locale: LocaleStrings,
    modifier: Modifier = Modifier,
) {
    var viewport by remember { androidx.compose.runtime.mutableStateOf(ImageSize(0, 0)) }

    Column(modifier = modifier) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(Color(0xFF18191B)),
            contentAlignment = Alignment.Center,
        ) {
            val availableWidth = (maxWidth - 24.dp).coerceAtLeast(1.dp)
            val availableHeight = (maxHeight - 24.dp).coerceAtLeast(1.dp)
            val cropWidth = if (availableWidth * 9f / 16f <= availableHeight) {
                availableWidth
            } else {
                availableHeight * 16f / 9f
            }

            Box(
                modifier = Modifier
                    .width(cropWidth)
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.Black),
            ) {
                PrintCropPreview(
                    state = state,
                    calculator = calculator,
                    viewport = viewport,
                    onViewportChanged = { viewport = it },
                    onPanAndZoom = onPanAndZoom,
                    modifier = Modifier.fillMaxSize(),
                )

                if (state.isBusy) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.58f)),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        CircularProgressIndicator(color = Color.White)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = if (state.phase == EditorPhase.Processing) {
                                locale.printEditorProcessing
                            } else {
                                locale.printEditorUploading
                            },
                            color = Color.White,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }

        Surface(tonalElevation = 2.dp) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    modifier = Modifier
                        .widthIn(max = 720.dp)
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = locale.printEditorZoom,
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Slider(
                        value = state.transform.zoom,
                        onValueChange = {
                            if (viewport.isValid()) onSetZoom(viewport, it)
                        },
                        modifier = Modifier.weight(1f),
                        enabled = !state.isBusy && viewport.isValid(),
                        valueRange = 1f..3f,
                    )
                }

                FlowRow(
                    modifier = Modifier
                        .widthIn(max = 720.dp)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    EditorToolButton(
                        label = locale.printEditorRotateLeft,
                        enabled = !state.isBusy && viewport.isValid(),
                        onClick = { onRotateLeft(viewport) },
                    ) {
                        Icon(Icons.AutoMirrored.Filled.RotateLeft, contentDescription = locale.printEditorRotateLeft)
                    }
                    EditorToolButton(
                        label = locale.printEditorRotateRight,
                        enabled = !state.isBusy && viewport.isValid(),
                        onClick = { onRotateRight(viewport) },
                    ) {
                        Icon(Icons.AutoMirrored.Filled.RotateRight, contentDescription = locale.printEditorRotateRight)
                    }
                    EditorToggleButton(
                        label = locale.printEditorFlipHorizontal,
                        checked = state.transform.flipHorizontal,
                        enabled = !state.isBusy,
                        onClick = onFlipHorizontal,
                    ) {
                        Icon(Icons.Default.Flip, contentDescription = locale.printEditorFlipHorizontal)
                    }
                    EditorToggleButton(
                        label = locale.printEditorFlipVertical,
                        checked = state.transform.flipVertical,
                        enabled = !state.isBusy,
                        onClick = onFlipVertical,
                    ) {
                        Icon(
                            Icons.Default.Flip,
                            contentDescription = locale.printEditorFlipVertical,
                            modifier = Modifier.rotate(90f),
                        )
                    }
                    EditorToolButton(
                        label = locale.printEditorReset,
                        enabled = !state.isBusy,
                        onClick = onReset,
                    ) {
                        Icon(Icons.Default.RestartAlt, contentDescription = locale.printEditorReset)
                    }
                }
            }
        }
    }
}

@Composable
private fun PrintCropPreview(
    state: PrintImageEditorState,
    calculator: CropTransformCalculator,
    viewport: ImageSize,
    onViewportChanged: (ImageSize) -> Unit,
    onPanAndZoom: (ImageSize, Float, Float, Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    Canvas(
        modifier = modifier
            .onSizeChanged { size ->
                if (size.width > 0 && size.height > 0) {
                    onViewportChanged(ImageSize(size.width, size.height))
                }
            }
            .pointerInput(state.isBusy, viewport) {
                if (!state.isBusy && viewport.isValid()) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        onPanAndZoom(viewport, pan.x, pan.y, zoom)
                    }
                }
            }
            .pointerInput(state.isBusy, viewport) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Main)
                        if (!state.isBusy && viewport.isValid() && event.type == PointerEventType.Scroll) {
                            val scrollY = event.changes.firstOrNull()?.scrollDelta?.y ?: 0f
                            if (scrollY != 0f) {
                                onPanAndZoom(
                                    viewport,
                                    0f,
                                    0f,
                                    if (scrollY > 0f) 0.9f else 1.1f,
                                )
                                event.changes.forEach { it.consume() }
                            }
                        }
                    }
                }
            },
    ) {
        if (!viewport.isValid()) return@Canvas
        val geometry = calculator.geometry(
            source = state.prepared.originalSize,
            viewport = viewport,
            transform = state.transform,
        )
        val oddTurn = state.transform.quarterTurns.mod(2) != 0
        val unrotatedWidth = if (oddTurn) geometry.imageHeight else geometry.imageWidth
        val unrotatedHeight = if (oddTurn) geometry.imageWidth else geometry.imageHeight
        val paint = Paint().apply {
            isAntiAlias = true
            filterQuality = FilterQuality.High
        }

        drawIntoCanvas { canvas ->
            canvas.withSave {
                canvas.translate(
                    size.width / 2f + geometry.translationX,
                    size.height / 2f + geometry.translationY,
                )
                canvas.rotate(geometry.rotationDegrees)
                canvas.scale(geometry.scaleXSign, geometry.scaleYSign)
                canvas.drawImageRect(
                    image = state.prepared.preview,
                    dstOffset = IntOffset(
                        (-unrotatedWidth / 2f).roundToInt(),
                        (-unrotatedHeight / 2f).roundToInt(),
                    ),
                    dstSize = IntSize(
                        unrotatedWidth.roundToInt(),
                        unrotatedHeight.roundToInt(),
                    ),
                    paint = paint,
                )
            }
        }

        val gridColor = Color.White.copy(alpha = 0.58f)
        drawLine(gridColor, Offset(size.width / 3f, 0f), Offset(size.width / 3f, size.height), 1.dp.toPx())
        drawLine(gridColor, Offset(size.width * 2f / 3f, 0f), Offset(size.width * 2f / 3f, size.height), 1.dp.toPx())
        drawLine(gridColor, Offset(0f, size.height / 3f), Offset(size.width, size.height / 3f), 1.dp.toPx())
        drawLine(gridColor, Offset(0f, size.height * 2f / 3f), Offset(size.width, size.height * 2f / 3f), 1.dp.toPx())
        drawRect(Color.White.copy(alpha = 0.88f), style = Stroke(2.dp.toPx()))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditorToolButton(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    ATooltipBox(tooltip = { Text(label) }) {
        FilledTonalIconButton(onClick = onClick, enabled = enabled, content = content)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditorToggleButton(
    label: String,
    checked: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    ATooltipBox(tooltip = { Text(label) }) {
        FilledIconToggleButton(
            checked = checked,
            onCheckedChange = { onClick() },
            enabled = enabled,
            content = content,
        )
    }
}

internal fun PrintImageFailure.localizedMessage(locale: LocaleStrings): String = when (this) {
    PrintImageFailure.FileTooLarge -> locale.printEditorFileTooLarge
    PrintImageFailure.ImageDimensionsTooLarge -> locale.printEditorImageTooLarge
    PrintImageFailure.DesktopRegionDecodeUnavailable ->
        locale.printEditorDesktopRegionDecodeUnavailable
    is PrintImageFailure.UnsupportedFormat -> locale.printEditorUnsupportedFormat
    is PrintImageFailure.DecodeFailed -> locale.printEditorDecodeFailed
    is PrintImageFailure.RenderFailed,
    is PrintImageFailure.EncodeFailed -> locale.printEditorRenderFailed
}

private fun EditorError.localizedMessage(locale: LocaleStrings): String = when (this) {
    is EditorError.Processing -> failure.localizedMessage(locale)
    is EditorError.Upload -> when (failure) {
        is PrintUploadFailure.Authentication -> locale.printEditorUploadAuthenticationFailed
        is PrintUploadFailure.Permission -> locale.printEditorUploadPermissionFailed
        is PrintUploadFailure.Network -> locale.printEditorUploadNetworkFailed
        is PrintUploadFailure.Server -> locale.printEditorUploadServerFailed
        is PrintUploadFailure.Unknown -> locale.printEditorUploadUnknownFailed
    }
}

private fun ImageSize.isValid(): Boolean = width > 0 && height > 0
