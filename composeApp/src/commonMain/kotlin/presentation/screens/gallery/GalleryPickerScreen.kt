package io.github.vrcmteam.vrcm.presentation.screens.gallery

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.ImageLoader
import coil3.compose.SubcomposeAsyncImage
import io.github.vrcmteam.vrcm.network.api.files.FileApi
import io.github.vrcmteam.vrcm.network.api.files.data.FileData
import io.github.vrcmteam.vrcm.network.api.files.data.FileTagType
import io.github.vrcmteam.vrcm.presentation.compoments.EmptyContent
import io.github.vrcmteam.vrcm.presentation.compoments.RefreshBox
import io.github.vrcmteam.vrcm.presentation.navigation.AppRoute
import io.github.vrcmteam.vrcm.presentation.navigation.LocalNavigator
import io.github.vrcmteam.vrcm.presentation.navigation.currentOrThrow
import io.github.vrcmteam.vrcm.presentation.settings.locale.strings
import io.github.vrcmteam.vrcm.presentation.supports.AppIcons
import kotlinx.serialization.Serializable
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

/**
 * VRChat Gallery 单选页：仅展示 Gallery 标签的图片，单击把结果写入
 * [GallerySelectionSessionStore] 后返回；不提供上传、删除或多选。
 */
@Serializable
data class GalleryPickerScreen(val sessionId: String) : AppRoute {

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val sessionStore: GallerySelectionSessionStore = koinInject()
        val galleryScreenModel: GalleryScreenModel = koinViewModel()
        var completed by remember(sessionId) { mutableStateOf(false) }

        LaunchedEffect(sessionId) {
            galleryScreenModel.refreshFiles(FileTagType.Gallery)
        }
        DisposableEffect(sessionId) {
            onDispose {
                // 任何未完成的离开路径（系统返回、手势、异常销毁）都取消会话。
                if (!completed) sessionStore.cancel(sessionId)
            }
        }

        val onPick: (FileData) -> Unit = onPick@{ file ->
            if (completed) return@onPick
            val accepted = sessionStore.complete(
                sessionId,
                GallerySelection(
                    fileId = file.id,
                    fileName = file.name,
                    extension = file.extension,
                    imageUrl = FileApi.convertFileUrl(file.id, 2048),
                ),
            )
            if (accepted) {
                completed = true
                navigator.pop()
            }
        }

        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            text = strings.meetupCardPickPhotoTitle,
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { navigator.pop() }) {
                            Icon(
                                painter = rememberVectorPainter(AppIcons.ArrowBackIosNew),
                                tint = MaterialTheme.colorScheme.primary,
                                contentDescription = "back",
                            )
                        }
                    },
                )
            },
            contentColor = MaterialTheme.colorScheme.primary,
        ) { paddingValues ->
            RefreshBox(
                modifier = Modifier.fillMaxSize().padding(paddingValues),
                isRefreshing = galleryScreenModel.isRefreshingByTag(FileTagType.Gallery),
                doRefresh = { galleryScreenModel.refreshFiles(FileTagType.Gallery) },
            ) {
                val files = galleryScreenModel.getFilesByTag(FileTagType.Gallery)
                if (files.isEmpty() && !galleryScreenModel.isRefreshingByTag(FileTagType.Gallery)) {
                    EmptyContent(
                        message = strings.galleryTabNoFiles.replace("%s", "Gallery"),
                    )
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 160.dp),
                        contentPadding = PaddingValues(8.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(items = files, key = FileData::id) { file ->
                            PickableGalleryImage(file = file, onPick = onPick)
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun PickableGalleryImage(
        file: FileData,
        onPick: (FileData) -> Unit,
    ) {
        SubcomposeAsyncImage(
            model = FileApi.convertFileUrl(file.id, 256),
            contentDescription = file.name,
            imageLoader = koinInject<ImageLoader>(),
            contentScale = ContentScale.Crop,
            alignment = Alignment.Center,
            modifier = Modifier
                .fillMaxSize()
                .aspectRatio(16f / 9f)
                .padding(2.dp)
                .clip(MaterialTheme.shapes.medium)
                .clickable { onPick(file) },
            loading = {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp,
                    )
                }
            },
            error = {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = strings.galleryTabLoadFailed,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
        )
    }
}
