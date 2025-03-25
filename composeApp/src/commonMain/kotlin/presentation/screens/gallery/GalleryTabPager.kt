package io.github.vrcmteam.vrcm.presentation.screens.gallery

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.koin.koinScreenModel
import com.skydoves.landscapist.ImageOptions
import com.skydoves.landscapist.coil3.CoilImage
import io.github.vrcmteam.vrcm.network.api.files.FileApi
import io.github.vrcmteam.vrcm.network.api.files.data.FileData
import io.github.vrcmteam.vrcm.network.api.files.data.FileTagType
import io.github.vrcmteam.vrcm.presentation.compoments.EmptyContent
import io.github.vrcmteam.vrcm.presentation.compoments.RefreshBox
import io.github.vrcmteam.vrcm.presentation.supports.Pager
import org.koin.compose.koinInject

sealed class GalleryTabPager(private val tagType: FileTagType) : Pager {
    
    override val index: Int
        get() = tagType.ordinal
    
    override val title: String
        @Composable
        get() = tagType.toString().replaceFirstChar { it.uppercase() }
    
    override val icon: Painter?
        @Composable
        get() = null
    
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val galleryScreenModel: GalleryScreenModel = koinScreenModel()
        
        LaunchedEffect(Unit) {
            // 首次加载或刷新文件
            galleryScreenModel.refreshFiles(tagType)
        }
        
        RefreshBox(
            isRefreshing = galleryScreenModel.isRefreshingByTag(tagType),
            doRefresh = { galleryScreenModel.refreshFiles(tagType) }
        ) {
            val files = galleryScreenModel.getFilesByTag(tagType)
            
            if (files.isEmpty() && !galleryScreenModel.isRefreshingByTag(tagType)) {
                EmptyContent(
                    message = "暂无${title}文件",
                )
            } else {
                GalleryGrid(files, tagType)
            }
        }
    }
    
    @Composable
    private fun GalleryGrid(files: List<FileData>, tagType: FileTagType) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 120.dp),
            contentPadding = PaddingValues(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(files) { file ->
                GalleryItem(file, tagType)
            }
        }
    }
    
    @Composable
    private fun GalleryItem(file: FileData, tagType: FileTagType) {

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                // 获取最新版本的图片URL
                val latestVersion = file.versions.maxByOrNull { it.version }
                val imageUrl = if (latestVersion != null) {
                    FileApi.convertFileUrl(file.id, 256)
                } else {
                    ""
                }
                CoilImage(
                    imageModel = { imageUrl },
                    imageOptions = ImageOptions(
                        contentScale = ContentScale.Crop,
                        alignment = Alignment.Center
                    ),
                    imageLoader = { koinInject() },
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(MaterialTheme.shapes.medium),
                    loading = {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp
                            )
                        }
                    },
                    failure = {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = "加载失败",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                )
                
                // 显示文件名称，位于底部
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(4.dp),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    Text(
                        text = file.name,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(4.dp)
                    )
                }
            }
        }
    }

    companion object{
       data object Icon  : GalleryTabPager(FileTagType.Icon)
        data object Emoji  : GalleryTabPager(FileTagType.Emoji)
        data object Sticker  : GalleryTabPager(FileTagType.Sticker)
        data object Gallery  : GalleryTabPager(FileTagType.Gallery)
    }
}
