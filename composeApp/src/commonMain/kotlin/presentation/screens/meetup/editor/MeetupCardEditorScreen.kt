package io.github.vrcmteam.vrcm.presentation.screens.meetup.editor

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
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.vinceglb.filekit.dialogs.compose.rememberFilePickerLauncher
import io.github.vinceglb.filekit.name
import io.github.vrcmteam.vrcm.core.shared.SharedFlowCentre
import io.github.vrcmteam.vrcm.presentation.compoments.ToastText
import io.github.vrcmteam.vrcm.presentation.navigation.LocalNavigator
import io.github.vrcmteam.vrcm.presentation.navigation.currentOrThrow
import io.github.vrcmteam.vrcm.presentation.screens.gallery.GalleryPickerScreen
import io.github.vrcmteam.vrcm.presentation.screens.gallery.editor.PrintImageLimits
import io.github.vrcmteam.vrcm.presentation.screens.gallery.editor.readBoundedBytes
import io.github.vrcmteam.vrcm.presentation.screens.gallery.galleryImagePickerType
import io.github.vrcmteam.vrcm.presentation.screens.meetup.MeetupCardCanvas
import io.github.vrcmteam.vrcm.presentation.screens.meetup.MeetupCardScreenModel
import io.github.vrcmteam.vrcm.presentation.screens.meetup.MeetupCardUiState
import io.github.vrcmteam.vrcm.presentation.screens.meetup.MeetupEditorError
import io.github.vrcmteam.vrcm.presentation.settings.locale.strings
import io.github.vrcmteam.vrcm.presentation.supports.AppIcons
import io.github.vrcmteam.vrcm.service.meetup.MeetupPhotoTarget
import io.github.vrcmteam.vrcm.storage.meetup.MeetupOrientation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import kotlin.math.max
import kotlin.math.min

/**
 * 身份卡编辑页：实时预览 + 四个工具页；无保存按钮，离散操作立即提交，
 * 页面返回前刷新一次草稿。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeetupCardEditorContent(
    model: MeetupCardScreenModel,
    onBack: () -> Unit,
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
            onShowShortText = model::setShowShortText,
            onShortText = model::setShortText,
            onShowQrCode = model::setShowQrCode,
            onQrLinkType = model::setQrLinkType,
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

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = locale.meetupCardTitle,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            model.flushDrafts()
                            onBack()
                        },
                    ) {
                        Icon(
                            painter = rememberVectorPainter(AppIcons.ArrowBackIosNew),
                            tint = MaterialTheme.colorScheme.primary,
                            contentDescription = "back",
                        )
                    }
                },
            )
        },
    ) { paddingValues ->
        BoxWithConstraints(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            val compact = maxWidth < 840.dp
            val toolsWidth = if (maxWidth >= 1080.dp) 420.dp else 380.dp
            if (compact) {
                Column(modifier = Modifier.fillMaxSize()) {
                    EditorPreview(
                        state = state,
                        onOrientation = model::setOrientation,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(top = 12.dp),
                        thickness = 0.5.dp,
                    )
                    MeetupEditorTools(
                        state = state,
                        actions = actions,
                        photoTarget = photoTarget,
                        onPhotoTarget = { photoTarget = it },
                        modifier = Modifier.fillMaxWidth().weight(1f),
                    )
                }
            } else {
                // 宽屏：预览占据剩余空间，工具固定宽度靠右成检查器面板。
                Row(modifier = Modifier.fillMaxSize()) {
                    EditorPreview(
                        state = state,
                        onOrientation = model::setOrientation,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    )
                    VerticalDivider(thickness = 0.5.dp)
                    MeetupEditorTools(
                        state = state,
                        actions = actions,
                        photoTarget = photoTarget,
                        onPhotoTarget = { photoTarget = it },
                        modifier = Modifier
                            .width(toolsWidth)
                            .fillMaxHeight(),
                    )
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
        SingleChoiceSegmentedButtonRow {
            MeetupOrientation.entries.forEachIndexed { index, entry ->
                SegmentedButton(
                    selected = state.orientation == entry,
                    onClick = { onOrientation(entry) },
                    shape = SegmentedButtonDefaults.itemShape(
                        index = index,
                        count = MeetupOrientation.entries.size,
                    ),
                ) {
                    Text(
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
            // 预览是真实展示的等比缩略图：按窗口全尺寸渲染再整体缩放，
            // 字体、间距与模板比例和展示页完全一致，不随预览盒子失真。
            val container = LocalWindowInfo.current.containerSize
            val density = LocalDensity.current
            val shortDp = with(density) { min(container.width, container.height).toDp() }
            val longDp = with(density) { max(container.width, container.height).toDp() }
            val portrait = state.orientation == MeetupOrientation.Portrait
            val cardWidth = if (portrait) shortDp else longDp
            val cardHeight = if (portrait) longDp else shortDp
            if (cardWidth > 0.dp && cardHeight > 0.dp && maxWidth > 0.dp && maxHeight > 0.dp) {
                val scale = minOf(maxWidth / cardWidth, maxHeight / cardHeight, 1f)
                Box(
                    modifier = Modifier
                        .size(cardWidth * scale, cardHeight * scale)
                        .clip(MaterialTheme.shapes.medium),
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
