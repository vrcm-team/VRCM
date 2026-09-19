package io.github.vrcmteam.vrcm.presentation.screens.meetup.editor

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.vinceglb.filekit.dialogs.compose.rememberFilePickerLauncher
import io.github.vinceglb.filekit.name
import io.github.vrcmteam.vrcm.core.shared.SharedFlowCentre
import io.github.vrcmteam.vrcm.presentation.adaptive.AppWindowWidthClass
import io.github.vrcmteam.vrcm.presentation.adaptive.LocalAppWindowWidthClass
import io.github.vrcmteam.vrcm.presentation.compoments.ToastText
import io.github.vrcmteam.vrcm.presentation.compoments.sharedBoundsBy
import io.github.vrcmteam.vrcm.presentation.designsystem.AppButton
import io.github.vrcmteam.vrcm.presentation.designsystem.AppButtonStyle
import io.github.vrcmteam.vrcm.presentation.designsystem.AppIcon
import io.github.vrcmteam.vrcm.presentation.designsystem.AppIconButton
import io.github.vrcmteam.vrcm.presentation.designsystem.AppNavBar
import io.github.vrcmteam.vrcm.presentation.designsystem.AppScaffold
import io.github.vrcmteam.vrcm.presentation.designsystem.AppSegment
import io.github.vrcmteam.vrcm.presentation.designsystem.AppSegmentedRow
import io.github.vrcmteam.vrcm.presentation.designsystem.AppShapes
import io.github.vrcmteam.vrcm.presentation.designsystem.AppSurface
import io.github.vrcmteam.vrcm.presentation.designsystem.AppText
import io.github.vrcmteam.vrcm.presentation.designsystem.AppTheme
import io.github.vrcmteam.vrcm.presentation.designsystem.AppVerticalDivider
import io.github.vrcmteam.vrcm.presentation.navigation.LocalNavigator
import io.github.vrcmteam.vrcm.presentation.navigation.currentOrThrow
import io.github.vrcmteam.vrcm.presentation.screens.gallery.GalleryPickerScreen
import io.github.vrcmteam.vrcm.presentation.screens.gallery.editor.PrintImageLimits
import io.github.vrcmteam.vrcm.presentation.screens.gallery.editor.readBoundedBytes
import io.github.vrcmteam.vrcm.presentation.screens.gallery.galleryImagePickerType
import io.github.vrcmteam.vrcm.presentation.screens.meetup.MeetupCardCanvas
import io.github.vrcmteam.vrcm.presentation.screens.meetup.MeetupCardScreenModel
import io.github.vrcmteam.vrcm.presentation.screens.meetup.MeetupCardUiState
import io.github.vrcmteam.vrcm.presentation.screens.meetup.MeetupCardResizeMode
import io.github.vrcmteam.vrcm.presentation.screens.meetup.MeetupEditorError
import io.github.vrcmteam.vrcm.presentation.screens.meetup.meetupCardPageSize
import io.github.vrcmteam.vrcm.presentation.screens.meetup.meetupCardSharedKey
import io.github.vrcmteam.vrcm.presentation.settings.locale.strings
import io.github.vrcmteam.vrcm.presentation.supports.AppIcons
import io.github.vrcmteam.vrcm.service.meetup.MeetupPhotoTarget
import io.github.vrcmteam.vrcm.storage.meetup.MeetupOrientation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/**
 * 身份卡编辑页：实时预览 + 四个工具页；无保存按钮，离散操作立即提交，
 * 页面返回前刷新一次草稿。
 */
@Composable
fun MeetupCardEditorContent(
    model: MeetupCardScreenModel,
    onBack: () -> Unit,
    onDone: () -> Unit,
) {
    val navigator = LocalNavigator.currentOrThrow
    val coordinator: MeetupPhotoSelectionCoordinator = koinInject()
    val photoSessions: MeetupPhotoSessionStore = koinInject()
    val state by model.state.collectAsState()
    val scope = rememberCoroutineScope()
    val locale = strings

    var cropSessionId by remember { mutableStateOf<String?>(null) }
    var pendingGallerySession by rememberSaveable { mutableStateOf<String?>(null) }
    var picking by remember { mutableStateOf(false) }
    var photoTarget by remember { mutableStateOf(MeetupPhotoTarget.Both) }

    val reportPhotoFailure: suspend (Throwable?) -> Unit = {
        SharedFlowCentre.toastText.emit(ToastText.Error(locale.meetupCardPhotoFailed))
    }

    val albumPicker = rememberFilePickerLauncher(
        type = galleryImagePickerType(listOf("jpg", "jpeg", "png", "webp", "heic", "heif")),
    ) { file ->
        if (file != null && !picking) {
            scope.launch {
                picking = true
                try {
                    val bytes = try {
                        file.readBoundedBytes(PrintImageLimits.MAX_FILE_BYTES)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (error: Exception) {
                        reportPhotoFailure(error)
                        return@launch
                    }
                    coordinator.prepareLocalAlbum(file.name, bytes).fold(
                        onSuccess = { cropSessionId = it },
                        onFailure = { reportPhotoFailure(it) },
                    )
                } finally {
                    picking = false
                }
            }
        }
    }

    // 从 Gallery 选择页返回后消费一次性结果；等待中则保持挂起状态。
    LaunchedEffect(pendingGallerySession) {
        val gallerySessionId = pendingGallerySession ?: return@LaunchedEffect
        coordinator.finishGallerySelection(gallerySessionId).fold(
            onSuccess = { photoSessionId ->
                when {
                    photoSessionId != null -> {
                        cropSessionId = photoSessionId
                        pendingGallerySession = null
                    }
                    !coordinator.isGallerySelectionPending(gallerySessionId) ->
                        pendingGallerySession = null
                    else -> Unit
                }
            },
            onFailure = { error ->
                reportPhotoFailure(error)
                pendingGallerySession = null
            },
        )
    }

    // 照片提交成功后会话被移除，此时关闭裁剪对话框；失败则保留。
    LaunchedEffect(state.savingPhoto, cropSessionId) {
        val sessionId = cropSessionId ?: return@LaunchedEffect
        if (!state.savingPhoto && photoSessions.get(sessionId) == null) {
            cropSessionId = null
        }
    }

    // 非内联展示的错误通过 toast 报告后清除。
    LaunchedEffect(state.editorError) {
        when (val error = state.editorError) {
            is MeetupEditorError.PhotoFailed -> {
                reportPhotoFailure(error.reason)
                model.clearError()
            }
            is MeetupEditorError.SaveFailed -> {
                SharedFlowCentre.toastText.emit(ToastText.Error(locale.meetupCardSaveFailed))
                model.clearError()
            }
            else -> Unit
        }
    }

    DisposableEffect(Unit) {
        onDispose { model.flushDrafts() }
    }

    val actions = remember(model) {
        MeetupEditorActions(
            onTemplate = model::setTemplate,
            onShowAvatar = model::setShowAvatar,
            onShowPronouns = model::setShowPronouns,
            onShowLanguages = model::setShowLanguages,
            onShowStatus = model::setShowStatus,
            onShowStatusDescription = model::setShowStatusDescription,
            onShowRepresentedGroup = model::setShowRepresentedGroup,
            onGroupDisplayStyle = model::setGroupDisplayStyle,
            onShowShortText = model::setShowShortText,
            onShortText = model::setShortText,
            onShowQrCode = model::setShowQrCode,
            onQrLinkTypeToggle = model::toggleQrLinkType,
            onQrProfileLinkToggle = model::toggleQrProfileLink,
            onShowIconFrame = model::setShowIconFrame,
            onShowProfileEffect = model::setShowProfileEffect,
            onShowNameplateEffect = model::setShowNameplateEffect,
            onAccent = model::setAccentArgb,
            onScrim = model::setScrimAlpha,
            onPickProfileBackground = {
                if (!picking) {
                    scope.launch {
                        picking = true
                        try {
                            coordinator.prepareProfileBackground(model.state.value.config).fold(
                                onSuccess = { cropSessionId = it },
                                onFailure = { reportPhotoFailure(it) },
                            )
                        } finally {
                            picking = false
                        }
                    }
                }
            },
            onPickLocalAlbum = { if (!picking) albumPicker.launch() },
            onPickGallery = {
                if (pendingGallerySession == null) {
                    val gallerySessionId = coordinator.beginGallerySelection()
                    pendingGallerySession = gallerySessionId
                    navigator.push(GalleryPickerScreen(gallerySessionId))
                }
            },
        )
    }

    AppScaffold(
        topBar = {
            AppNavBar(
                title = {
                    AppText(
                        text = locale.meetupCardTitle,
                    )
                },
                navigationIcon = {
                    AppIconButton(
                        onClick = {
                            model.flushDrafts()
                            onBack()
                        },
                    ) {
                        AppIcon(
                            painter = rememberVectorPainter(AppIcons.ArrowBackIosNew),
                            contentDescription = "back",
                        )
                    }
                },
                actions = {
                    // 编辑页此前没有通往展示页的出口，首次配置完只能退回首页再长按。
                    AppButton(
                        onClick = {
                            model.finishSetup()
                            onDone()
                        },
                        enabled = !state.savingPhoto,
                        style = AppButtonStyle.Plain,
                    ) {
                        AppText(locale.meetupCardDone)
                    }
                },
            )
        },
    ) { paddingValues ->
        // 顶部留白做外边距；预览与工具面板铺到屏幕底，系统导航条的高度交给工具面板的滚动内容
        BoxWithConstraints(modifier = Modifier.fillMaxSize().padding(top = paddingValues.calculateTopPadding())) {
            // 与项目其他编辑页一致：Expanded 宽度类才并列预览与设置栏。
            val expanded = LocalAppWindowWidthClass.current == AppWindowWidthClass.Expanded &&
                maxWidth >= 840.dp
            val toolsWidth = if (maxWidth >= 1080.dp) 420.dp else 380.dp
            val preview: @Composable (Modifier) -> Unit = { previewModifier ->
                // 预览衬在低一层的表面上，与右侧/下方的设置区形成层次。
                AppSurface(
                    modifier = previewModifier,
                    color = AppTheme.colors.secondaryGroupedBackground,
                ) {
                    EditorPreview(
                        state = state,
                        onOrientation = model::setOrientation,
                        // 并列布局下预览也到屏幕底：卡片在系统导航条以上的区域里居中
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(bottom = if (expanded) paddingValues.calculateBottomPadding() else 0.dp),
                    )
                }
            }
            val tools: @Composable (Modifier) -> Unit = { toolsModifier ->
                AppSurface(
                    modifier = toolsModifier,
                    color = AppTheme.colors.secondaryGroupedBackground,
                ) {
                    MeetupEditorTools(
                        state = state,
                        actions = actions,
                        photoTarget = photoTarget,
                        onPhotoTarget = { photoTarget = it },
                        modifier = Modifier.fillMaxSize(),
                        bottomContentPadding = paddingValues.calculateBottomPadding(),
                    )
                }
            }
            if (!expanded) {
                Column(modifier = Modifier.fillMaxSize()) {
                    preview(Modifier.fillMaxWidth().weight(1f))
                    tools(Modifier.fillMaxWidth().weight(1f))
                }
            } else {
                // 宽屏：预览占据剩余空间，工具固定宽度靠右成检查器面板。
                Row(modifier = Modifier.fillMaxSize()) {
                    preview(Modifier.weight(1f).fillMaxHeight())
                    AppVerticalDivider(thickness = 0.5.dp)
                    tools(Modifier.width(toolsWidth).fillMaxHeight())
                }
            }
        }
    }

    cropSessionId?.let { sessionId ->
        MeetupCardCropDialog(
            sessionId = sessionId,
            savingPhoto = state.savingPhoto,
            onConfirm = { model.confirmPhoto(sessionId, photoTarget) },
            onDismiss = {
                model.discardPhotoSession(sessionId)
                cropSessionId = null
            },
            orientations = photoTarget.editableOrientations(),
        )
    }
}

/** 预览区：分段控件只切换预览方向，不旋转设备。 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun EditorPreview(
    state: MeetupCardUiState,
    onOrientation: (MeetupOrientation) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AppSegmentedRow {
            MeetupOrientation.entries.forEachIndexed { index, entry ->
                AppSegment(
                    selected = state.orientation == entry,
                    onClick = { onOrientation(entry) },
                ) {
                    AppText(
                        text = when (entry) {
                            MeetupOrientation.Portrait -> strings.meetupCardPortrait
                            MeetupOrientation.Landscape -> strings.meetupCardLandscape
                        },
                    )
                }
            }
        }
        BoxWithConstraints(
            modifier = Modifier
                .padding(top = 12.dp)
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            // 预览是真实展示的等比缩略图：按整页尺寸渲染再整体缩放，
            // 字体、间距与模板比例和展示页完全一致，不随预览盒子失真。
            val pageSize = meetupCardPageSize(state.orientation)
            val cardWidth = pageSize.width
            val cardHeight = pageSize.height
            if (cardWidth > 0.dp && cardHeight > 0.dp && maxWidth > 0.dp && maxHeight > 0.dp) {
                val scale = minOf(maxWidth / cardWidth, maxHeight / cardHeight, 1f)
                Box(
                    modifier = Modifier
                        .size(cardWidth * scale, cardHeight * scale)
                        // 与展示页整卡共享同一元素，进出编辑页连续变换。
                        .sharedBoundsBy(
                            key = meetupCardSharedKey(state.ownerUserId),
                            useSuffixKey = false,
                            resizeMode = MeetupCardResizeMode,
                        )
                        .clip(AppShapes.m),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .requiredSize(cardWidth, cardHeight)
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                            },
                    ) {
                        MeetupCardCanvas(
                            state = state,
                            orientation = state.orientation,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
        }
    }
}
