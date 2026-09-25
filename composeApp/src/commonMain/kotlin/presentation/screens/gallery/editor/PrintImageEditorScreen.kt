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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import io.github.vrcmteam.vrcm.presentation.designsystem.AppActivityIndicator
import io.github.vrcmteam.vrcm.presentation.designsystem.AppBannerHost
import io.github.vrcmteam.vrcm.presentation.designsystem.AppBannerHostState
import io.github.vrcmteam.vrcm.presentation.designsystem.AppButtonStyle
import io.github.vrcmteam.vrcm.presentation.designsystem.AppIcon
import io.github.vrcmteam.vrcm.presentation.designsystem.AppIconButton
import io.github.vrcmteam.vrcm.presentation.designsystem.AppIconToggleButton
import io.github.vrcmteam.vrcm.presentation.designsystem.AppNavBar
import io.github.vrcmteam.vrcm.presentation.designsystem.AppScaffold
import io.github.vrcmteam.vrcm.presentation.designsystem.AppShapes
import io.github.vrcmteam.vrcm.presentation.designsystem.AppSlider
import io.github.vrcmteam.vrcm.presentation.designsystem.AppSurface
import io.github.vrcmteam.vrcm.presentation.designsystem.AppText
import io.github.vrcmteam.vrcm.presentation.designsystem.AppTheme
import io.github.vrcmteam.vrcm.presentation.designsystem.AppToggle
import io.github.vrcmteam.vrcm.presentation.designsystem.AppVerticalDivider
import io.github.vrcmteam.vrcm.presentation.navigation.AppRoute
import io.github.vrcmteam.vrcm.presentation.adaptive.AppWindowWidthClass
import io.github.vrcmteam.vrcm.presentation.adaptive.LocalAppWindowWidthClass
import org.koin.compose.viewmodel.koinViewModel
import io.github.vrcmteam.vrcm.presentation.navigation.LocalNavigator
import io.github.vrcmteam.vrcm.presentation.navigation.currentOrThrow
import io.github.vrcmteam.vrcm.core.shared.SharedFlowCentre
import io.github.vrcmteam.vrcm.presentation.compoments.ATooltipBox
import io.github.vrcmteam.vrcm.presentation.compoments.ToastText
import io.github.vrcmteam.vrcm.presentation.navigation.BlockBackNavigation
import io.github.vrcmteam.vrcm.presentation.screens.avatar.AvatarCoverUpdateFailure
import io.github.vrcmteam.vrcm.presentation.screens.avatar.AvatarGalleryUploadFailure
import io.github.vrcmteam.vrcm.presentation.screens.world.WorldImageSessionChanged
import io.github.vrcmteam.vrcm.presentation.screens.world.WorldImageUpdateFailure
import io.github.vrcmteam.vrcm.presentation.settings.locale.LocaleStrings
import io.github.vrcmteam.vrcm.presentation.settings.locale.strings
import io.github.vrcmteam.vrcm.presentation.supports.AppIcons
import io.github.vrcmteam.vrcm.service.PrintUploadFailure
import kotlinx.coroutines.flow.collectLatest
import kotlinx.serialization.Serializable
import org.koin.compose.koinInject
import org.koin.core.parameter.parametersOf
import kotlin.math.roundToInt

@Serializable
class PrintImageEditorScreen(
    private val sessionId: String,
) : AppRoute {
        @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val calculator: CropTransformCalculator = koinInject()
        val sessionStore: PrintImageEditorSessionStore = koinInject()
        val session = remember(sessionId) { sessionStore.get(sessionId) }
        val locale = strings
        val currentLocale by rememberUpdatedState(locale)

        if (session == null) {
            LaunchedEffect(sessionId) {
                SharedFlowCentre.toastText.emit(ToastText.Error(locale.printEditorSessionExpired))
                navigator.pop()
            }
            Box(modifier = Modifier.fillMaxSize())
            return
        }

        val screenModel: PrintImageEditorScreenModel = koinViewModel {
            parametersOf(sessionId)
        }
        DisposableEffect(screenModel) {
            screenModel.acquirePreviewDisplayLease()
            onDispose(screenModel::releasePreviewDisplayLease)
        }
        val state by screenModel.state.collectAsState()
        val bannerHostState = remember { AppBannerHostState() }
        val title = when (session.target) {
            ImageEditorTarget.Print -> locale.printEditorTitle
            is ImageEditorTarget.AvatarCover -> locale.avatarEditCover
            is ImageEditorTarget.AvatarGallery -> locale.avatarGalleryTitle
            is ImageEditorTarget.WorldCover -> locale.worldImageEditTitle
            is ImageEditorTarget.Gallery -> locale.galleryTabUploadImage
        }
        val submitLabel = when (session.target) {
            ImageEditorTarget.Print -> locale.printEditorUpload
            is ImageEditorTarget.AvatarCover -> locale.avatarEditUploadCover
            is ImageEditorTarget.AvatarGallery -> locale.avatarGalleryUpload
            is ImageEditorTarget.WorldCover -> locale.worldImageEditUpload
            is ImageEditorTarget.Gallery -> locale.galleryTabUploadImage
        }
        val uploadingText = when (session.target) {
            ImageEditorTarget.Print -> locale.printEditorUploading
            is ImageEditorTarget.AvatarCover -> locale.avatarEditUploadingCover
            is ImageEditorTarget.AvatarGallery -> locale.avatarGalleryUploading
            is ImageEditorTarget.WorldCover -> locale.worldImageEditUploading
            is ImageEditorTarget.Gallery -> locale.galleryTabUploading
        }

        BlockBackNavigation(blocked = state.isBusy)

        LaunchedEffect(screenModel) {
            screenModel.events.collectLatest { event ->
                when (event) {
                    is EditorEvent.Submitted -> {
                        sessionStore.complete(sessionId, event.submission)
                        when (event.submission) {
                            ImageEditorSubmission.Print -> SharedFlowCentre.toastText.emit(
                                ToastText.Success(currentLocale.printEditorUploaded),
                            )
                            is ImageEditorSubmission.Gallery -> SharedFlowCentre.toastText.emit(
                                ToastText.Success(currentLocale.galleryTabUploadSuccess),
                            )
                            is ImageEditorSubmission.AvatarCover -> Unit
                            is ImageEditorSubmission.AvatarGallery -> Unit
                            is ImageEditorSubmission.WorldCover -> Unit
                        }
                        navigator.pop()
                    }
                }
            }
        }

        LaunchedEffect(state.error) {
            val error = state.error ?: return@LaunchedEffect
            bannerHostState.show(error.localizedMessage(locale, session.target))
            screenModel.clearError()
        }

        AppScaffold(
            topBar = {
                AppNavBar(
                    title = { AppText(title) },
                    navigationIcon = {
                        AppIconButton(
                            onClick = navigator::pop,
                            enabled = !state.isBusy,
                        ) {
                            AppIcon(
                                imageVector = AppIcons.ArrowBackIosNew,
                                contentDescription = locale.printEditorBack,
                            )
                        }
                    },
                    actions = {
                        ATooltipBox(tooltip = { AppText(submitLabel) }) {
                            AppIconButton(
                                onClick = screenModel::upload,
                                enabled = !state.isBusy,
                                style = AppButtonStyle.Tinted,
                            ) {
                                if (state.isBusy) {
                                    AppActivityIndicator(
                                        modifier = Modifier.size(18.dp),
                                    )
                                } else {
                                    AppIcon(
                                        imageVector = AppIcons.Publish,
                                        contentDescription = submitLabel,
                                    )
                                }
                            }
                        }
                    },
                )
            },
            bannerHost = { AppBannerHost(bannerHostState) },
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
                onCropDownloadedPrintBorderChange = screenModel::setCropDownloadedPrintBorder,
                onFillWhiteBorderChange = screenModel::setFillWhiteBorder,
                locale = locale,
                uploadingText = uploadingText,
                aspectRatio = session.target.cropAspectRatio,
                canvasBackground = session.target.canvasBackground,
                fitModeLabel = if (session.target == ImageEditorTarget.Print) {
                    locale.printEditorFillWhiteBorder
                } else {
                    locale.printEditorShowFullImage
                },
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
    onReset: (ImageSize) -> Unit,
    onCropDownloadedPrintBorderChange: (Boolean, ImageSize) -> Unit,
    onFillWhiteBorderChange: (Boolean, ImageSize) -> Unit,
    locale: LocaleStrings,
    uploadingText: String,
    aspectRatio: Float,
    canvasBackground: CanvasBackground,
    fitModeLabel: String,
    modifier: Modifier = Modifier,
) {
    var viewport by remember { androidx.compose.runtime.mutableStateOf(ImageSize(0, 0)) }
    val expanded = LocalAppWindowWidthClass.current == AppWindowWidthClass.Expanded

    val preview: @Composable (Modifier) -> Unit = { previewModifier ->
        PrintEditorPreview(
            state = state,
            calculator = calculator,
            viewport = viewport,
            onViewportChanged = { viewport = it },
            onPanAndZoom = onPanAndZoom,
            locale = locale,
            uploadingText = uploadingText,
            aspectRatio = aspectRatio,
            canvasBackground = canvasBackground,
            modifier = previewModifier,
        )
    }
    val controls: @Composable (Modifier, Boolean) -> Unit = { controlsModifier, sidePanel ->
        PrintEditorControls(
            state = state,
            calculator = calculator,
            viewport = viewport,
            onSetZoom = onSetZoom,
            onRotateLeft = onRotateLeft,
            onRotateRight = onRotateRight,
            onFlipHorizontal = onFlipHorizontal,
            onFlipVertical = onFlipVertical,
            onReset = onReset,
            onCropDownloadedPrintBorderChange = onCropDownloadedPrintBorderChange,
            onFillWhiteBorderChange = onFillWhiteBorderChange,
            locale = locale,
            fitModeLabel = fitModeLabel,
            sidePanel = sidePanel,
            modifier = controlsModifier,
        )
    }

    if (expanded) {
        Row(modifier = modifier) {
            preview(Modifier.weight(1f).fillMaxHeight())
            AppVerticalDivider(Modifier.fillMaxHeight())
            controls(Modifier.width(360.dp).fillMaxHeight(), true)
        }
    } else {
        Column(modifier = modifier) {
            preview(Modifier.fillMaxWidth().weight(1f))
            controls(Modifier.fillMaxWidth(), false)
        }
    }
}

@Composable
private fun PrintEditorPreview(
    state: PrintImageEditorState,
    calculator: CropTransformCalculator,
    viewport: ImageSize,
    onViewportChanged: (ImageSize) -> Unit,
    onPanAndZoom: (ImageSize, Float, Float, Float) -> Unit,
    locale: LocaleStrings,
    uploadingText: String,
    aspectRatio: Float,
    canvasBackground: CanvasBackground,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(
        modifier = modifier.background(Color(0xFF18191B)),
        contentAlignment = Alignment.Center,
    ) {
        val availableWidth = (maxWidth - 24.dp).coerceAtLeast(1.dp)
        val availableHeight = (maxHeight - 24.dp).coerceAtLeast(1.dp)
        val cropWidth = if (availableWidth / aspectRatio <= availableHeight) {
            availableWidth
        } else {
            availableHeight * aspectRatio
        }

        Box(
            modifier = Modifier
                .width(cropWidth)
                .aspectRatio(aspectRatio)
                .clip(AppShapes.xs),
        ) {
            EditorCanvasBackground(
                background = canvasBackground,
                modifier = Modifier.fillMaxSize(),
            )
            PrintCropPreview(
                state = state,
                calculator = calculator,
                viewport = viewport,
                onViewportChanged = onViewportChanged,
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
                    AppActivityIndicator(color = Color.White)
                    Spacer(modifier = Modifier.height(12.dp))
                    AppText(
                        text = if (state.phase == EditorPhase.Processing) {
                            locale.printEditorProcessing
                        } else if (state.phase == EditorPhase.Refreshing) {
                            locale.avatarGalleryRefreshing
                        } else {
                            val progress = state.submissionProgress as?
                                ImageEditorSubmissionProgress.Upload
                            if (progress?.totalBytes != null && progress.totalBytes > 0) {
                                "${uploadingText} ${(progress.bytesSent * 100 / progress.totalBytes).toInt()}%"
                            } else {
                                uploadingText
                            }
                        },
                        color = Color.White,
                        style = AppTheme.type.subheadline,
                    )
                }
            }
        }
    }
}

@Composable
private fun EditorCanvasBackground(
    background: CanvasBackground,
    modifier: Modifier = Modifier,
) {
    val lightTile = AppTheme.colors.secondaryGroupedBackground
    val darkTile = AppTheme.colors.fill
    Canvas(modifier = modifier) {
        if (background == CanvasBackground.White) {
            drawRect(Color.White)
            return@Canvas
        }

        val tileSize = 12.dp.toPx()
        drawRect(lightTile)
        var row = 0
        var top = 0f
        while (top < size.height) {
            var column = row.mod(2)
            var left = column * tileSize
            while (left < size.width) {
                drawRect(
                    color = darkTile,
                    topLeft = Offset(left, top),
                    size = androidx.compose.ui.geometry.Size(tileSize, tileSize),
                )
                column += 2
                left = column * tileSize
            }
            row++
            top += tileSize
        }
    }
}

@Composable
private fun PrintEditorControls(
    state: PrintImageEditorState,
    calculator: CropTransformCalculator,
    viewport: ImageSize,
    onSetZoom: (ImageSize, Float) -> Unit,
    onRotateLeft: (ImageSize) -> Unit,
    onRotateRight: (ImageSize) -> Unit,
    onFlipHorizontal: () -> Unit,
    onFlipVertical: () -> Unit,
    onReset: (ImageSize) -> Unit,
    onCropDownloadedPrintBorderChange: (Boolean, ImageSize) -> Unit,
    onFillWhiteBorderChange: (Boolean, ImageSize) -> Unit,
    locale: LocaleStrings,
    fitModeLabel: String,
    sidePanel: Boolean,
    modifier: Modifier = Modifier,
) {
    AppSurface(modifier = modifier) {
        Column(
            modifier = (if (sidePanel) Modifier.fillMaxSize() else Modifier.fillMaxWidth())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = if (sidePanel) {
                Arrangement.spacedBy(18.dp, Alignment.CenterVertically)
            } else {
                Arrangement.spacedBy(10.dp)
            },
        ) {
            Row(
                modifier = Modifier
                    .widthIn(max = 720.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                val zoomRange = if (viewport.isValid()) {
                    val limits = calculator.zoomLimits(
                        source = state.prepared.originalSize,
                        viewport = viewport,
                        quarterTurns = state.transform.quarterTurns,
                    )
                    if (state.fillWhiteBorder) {
                        limits.valueRange
                    } else {
                        limits.cover..limits.maximum
                    }
                } else {
                    1f..3f
                }
                AppText(
                    text = locale.printEditorZoom,
                    style = AppTheme.type.subheadlineEmphasized,
                )
                AppSlider(
                    value = state.transform.zoom,
                    onValueChange = {
                        if (viewport.isValid()) onSetZoom(viewport, it)
                    },
                    modifier = Modifier.weight(1f),
                    enabled = !state.isBusy && viewport.isValid(),
                    valueRange = zoomRange,
                )
            }

            Row(
                modifier = Modifier
                    .widthIn(max = 720.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                AppText(
                    text = fitModeLabel,
                    style = AppTheme.type.subheadlineEmphasized,
                    modifier = Modifier.weight(1f),
                )
                AppToggle(
                    checked = state.fillWhiteBorder,
                    onCheckedChange = { onFillWhiteBorderChange(it, viewport) },
                    enabled = !state.isBusy && viewport.isValid(),
                )
            }

            if (state.canCropDownloadedPrintBorder) {
                Row(
                    modifier = Modifier
                        .widthIn(max = 720.dp)
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    AppText(
                        text = locale.galleryTabCropPrintBorder,
                        style = AppTheme.type.subheadlineEmphasized,
                        modifier = Modifier.weight(1f),
                    )
                    AppToggle(
                        checked = state.cropDownloadedPrintBorder,
                        onCheckedChange = {
                            onCropDownloadedPrintBorderChange(it, viewport)
                        },
                        enabled = !state.isBusy && viewport.isValid(),
                    )
                }
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
                    AppIcon(AppIcons.RotateLeft, contentDescription = locale.printEditorRotateLeft)
                }
                EditorToolButton(
                    label = locale.printEditorRotateRight,
                    enabled = !state.isBusy && viewport.isValid(),
                    onClick = { onRotateRight(viewport) },
                ) {
                    AppIcon(AppIcons.RotateRight, contentDescription = locale.printEditorRotateRight)
                }
                EditorToggleButton(
                    label = locale.printEditorFlipHorizontal,
                    checked = state.transform.flipHorizontal,
                    enabled = !state.isBusy,
                    onClick = onFlipHorizontal,
                ) {
                    AppIcon(AppIcons.FlipHorizontal, contentDescription = locale.printEditorFlipHorizontal)
                }
                EditorToggleButton(
                    label = locale.printEditorFlipVertical,
                    checked = state.transform.flipVertical,
                    enabled = !state.isBusy,
                    onClick = onFlipVertical,
                ) {
                    AppIcon(
                        AppIcons.FlipHorizontal,
                        contentDescription = locale.printEditorFlipVertical,
                        modifier = Modifier.rotate(90f),
                    )
                }
                EditorToolButton(
                    label = locale.printEditorReset,
                    enabled = !state.isBusy && viewport.isValid(),
                    onClick = { if (viewport.isValid()) onReset(viewport) },
                ) {
                    AppIcon(AppIcons.Reset, contentDescription = locale.printEditorReset)
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

@Composable
private fun EditorToolButton(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    ATooltipBox(tooltip = { AppText(label) }) {
        AppIconButton(onClick = onClick, enabled = enabled, content = content, style = AppButtonStyle.Tinted)
    }
}

@Composable
private fun EditorToggleButton(
    label: String,
    checked: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    ATooltipBox(tooltip = { AppText(label) }) {
        AppIconToggleButton(
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
    PrintImageFailure.EncodedOutputTooLarge -> locale.printEditorOutputTooLarge
    PrintImageFailure.DesktopRegionDecodeUnavailable ->
        locale.printEditorDesktopRegionDecodeUnavailable
    is PrintImageFailure.UnsupportedFormat -> locale.printEditorUnsupportedFormat
    is PrintImageFailure.DecodeFailed -> locale.printEditorDecodeFailed
    is PrintImageFailure.RenderFailed,
    is PrintImageFailure.EncodeFailed -> locale.printEditorRenderFailed
}

private fun EditorError.localizedMessage(
    locale: LocaleStrings,
    target: ImageEditorTarget,
): String = when (this) {
    is EditorError.Processing -> failure.localizedMessage(locale)
    is EditorError.Submission -> when (val cause = failure) {
        is PrintUploadFailure.Authentication -> locale.printEditorUploadAuthenticationFailed
        is PrintUploadFailure.Permission -> locale.printEditorUploadPermissionFailed
        is PrintUploadFailure.Network -> locale.printEditorUploadNetworkFailed
        is PrintUploadFailure.Server -> locale.printEditorUploadServerFailed
        is PrintUploadFailure.Unknown -> locale.printEditorUploadUnknownFailed
        is AvatarCoverUpdateFailure.Upload -> locale.avatarEditCoverUploadFailed
        is AvatarCoverUpdateFailure.Assignment -> locale.avatarEditCoverAssignmentFailed
        is AvatarGalleryUploadFailure.Upload -> locale.avatarGalleryUploadFailed
        is AvatarGalleryUploadFailure.Refresh -> locale.avatarGalleryRefreshFailed
        is AvatarGalleryUploadFailure.SessionChanged -> locale.avatarGallerySessionChanged
        is AvatarGalleryUploadFailure.Permission -> locale.avatarGalleryPermissionDenied
        is WorldImageUpdateFailure -> when {
            cause.cause is WorldImageSessionChanged -> locale.worldImageEditSessionChanged
            cause is WorldImageUpdateFailure.Upload -> locale.worldImageEditUploadFailed
            cause is WorldImageUpdateFailure.Assignment -> locale.worldImageEditAssignmentFailed
            else -> locale.worldImageEditRefreshFailed
        }
        else -> when (target) {
            ImageEditorTarget.Print -> locale.printEditorUploadUnknownFailed
            is ImageEditorTarget.AvatarCover -> locale.avatarEditCoverUploadFailed
            is ImageEditorTarget.AvatarGallery -> locale.avatarGalleryUploadFailed
            is ImageEditorTarget.WorldCover -> locale.worldImageEditUploadFailed
            is ImageEditorTarget.Gallery -> locale.galleryTabUploadFailed
        }
    }
}

private val ImageEditorTarget.cropAspectRatio: Float
    get() = when (this) {
        ImageEditorTarget.Print,
        is ImageEditorTarget.AvatarCover,
        is ImageEditorTarget.WorldCover -> 16f / 9f
        is ImageEditorTarget.AvatarGallery -> canvasSpec.aspectRatio
        is ImageEditorTarget.Gallery -> canvasSpec.aspectRatio
    }

private fun ImageSize.isValid(): Boolean = width > 0 && height > 0
