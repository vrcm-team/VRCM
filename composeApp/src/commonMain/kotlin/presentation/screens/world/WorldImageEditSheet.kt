package io.github.vrcmteam.vrcm.presentation.screens.world

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.ImageLoader
import io.github.vinceglb.filekit.name
import io.github.vinceglb.filekit.dialogs.compose.rememberFilePickerLauncher
import io.github.vrcmteam.vrcm.presentation.designsystem.AppActivityIndicator
import io.github.vrcmteam.vrcm.presentation.designsystem.AppButton
import io.github.vrcmteam.vrcm.presentation.designsystem.AppButtonStyle
import io.github.vrcmteam.vrcm.presentation.designsystem.AppIcon
import io.github.vrcmteam.vrcm.presentation.designsystem.AppShapes
import io.github.vrcmteam.vrcm.presentation.designsystem.AppSheet
import io.github.vrcmteam.vrcm.presentation.designsystem.AppSheetValue
import io.github.vrcmteam.vrcm.presentation.designsystem.AppText
import io.github.vrcmteam.vrcm.presentation.designsystem.AppTheme
import io.github.vrcmteam.vrcm.presentation.designsystem.LocalContentColor
import io.github.vrcmteam.vrcm.presentation.designsystem.rememberAppSheetState
import io.github.vrcmteam.vrcm.presentation.screens.gallery.galleryImagePickerType
import io.github.vrcmteam.vrcm.presentation.screens.gallery.editor.PreparedImage
import io.github.vrcmteam.vrcm.presentation.screens.gallery.editor.PrintImageFailure
import io.github.vrcmteam.vrcm.presentation.screens.gallery.editor.PrintImageProcessor
import io.github.vrcmteam.vrcm.presentation.screens.gallery.editor.SelectedImage
import io.github.vrcmteam.vrcm.presentation.screens.gallery.editor.localizedMessage
import io.github.vrcmteam.vrcm.presentation.screens.gallery.editor.readBoundedBytes
import io.github.vrcmteam.vrcm.presentation.screens.world.data.WorldProfileVo
import io.github.vrcmteam.vrcm.presentation.settings.locale.strings
import io.github.vrcmteam.vrcm.presentation.supports.AppIcons
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@Composable
internal fun WorldImageEditSheet(
    world: WorldProfileVo,
    imageProcessor: PrintImageProcessor,
    onDismiss: () -> Unit,
    onEditImage: (SelectedImage, PreparedImage) -> Unit,
) {
    var errorText by remember(world.worldId) { mutableStateOf<String?>(null) }
    var isPreparing by remember(world.worldId) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val locale = strings
    val latestIsPreparing = rememberUpdatedState(isPreparing)
    val sheetState = rememberAppSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { targetValue ->
            targetValue != AppSheetValue.Hidden || !latestIsPreparing.value
        },
    )
    val picker = rememberFilePickerLauncher(
        type = galleryImagePickerType(WorldImageLimits.ALLOWED_EXTENSIONS),
    ) { file ->
        if (file != null && !isPreparing) {
            scope.launch {
                isPreparing = true
                errorText = null
                try {
                    val bytes = file.readBoundedBytes(WorldImageLimits.MAX_FILE_BYTES)
                    when (val validation = validateWorldImage(file.name, bytes)) {
                        WorldImageValidation.FileTooLarge -> {
                            errorText = locale.worldImageEditFileTooLarge
                        }
                        WorldImageValidation.UnsupportedFormat -> {
                            errorText = locale.worldImageEditUnsupportedFormat
                        }
                        is WorldImageValidation.Valid -> {
                            val source = SelectedImage(
                                fileName = validation.image.fileName,
                                bytes = validation.image.bytes,
                            )
                            val prepared = imageProcessor.prepare(source).getOrElse { failure ->
                                if (failure is CancellationException) throw failure
                                errorText = (failure as? PrintImageFailure)
                                    ?.localizedMessage(locale)
                                    ?: locale.worldImageEditReadFailed
                                return@launch
                            }
                            onEditImage(source, prepared)
                        }
                    }
                } catch (error: CancellationException) {
                    throw error
                } catch (_: PrintImageFailure.FileTooLarge) {
                    errorText = locale.worldImageEditFileTooLarge
                } catch (_: Exception) {
                    errorText = locale.worldImageEditReadFailed
                } finally {
                    isPreparing = false
                }
            }
        }
    }

    AppSheet(
        onDismissRequest = { if (!isPreparing) onDismiss() },
        sheetState = sheetState,
        sheetGesturesEnabled = !isPreparing,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 720.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AppText(
                text = locale.worldImageEditTitle,
                style = AppTheme.type.title2,
                color = AppTheme.colors.tint,
            )
            WorldImagePreview(
                label = locale.worldImageEditPreview,
                imageUrl = world.worldImageUrl,
            )
            WorldImagePreview(
                label = locale.worldImageEditThumbnail,
                imageUrl = world.thumbnailImageUrl ?: world.worldImageUrl,
            )
            AppText(
                text = locale.worldImageEditHint,
                style = AppTheme.type.caption1,
                color = AppTheme.colors.secondaryLabel,
            )
            AppButton(
                onClick = picker::launch,
                modifier = Modifier.fillMaxWidth(),
                enabled = !isPreparing,
                style = AppButtonStyle.Gray,
            ) {
                if (isPreparing) {
                    AppActivityIndicator(
                        modifier = Modifier.size(18.dp),
                        color = LocalContentColor.current,
                    )
                } else {
                    AppIcon(
                        imageVector = AppIcons.Edit,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                }
                Spacer(Modifier.size(8.dp))
                AppText(locale.worldImageEditChoose)
            }
            errorText?.let { error ->
                AppText(
                    text = error,
                    style = AppTheme.type.caption1,
                    color = AppTheme.colors.destructive,
                )
            }
            AppButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.End),
                enabled = !isPreparing,
                style = AppButtonStyle.Plain,
            ) {
                AppText(locale.cancel)
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun WorldImagePreview(
    label: String,
    imageUrl: String?,
) {
    val imageLoader: ImageLoader = koinInject()
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        AppText(text = label, style = AppTheme.type.subheadlineEmphasized)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(AppShapes.s),
            contentAlignment = Alignment.Center,
        ) {
            AsyncImage(
                model = imageUrl,
                contentDescription = label,
                imageLoader = imageLoader,
                modifier = Modifier.matchParentSize(),
                contentScale = ContentScale.Crop,
            )
        }
    }
}
