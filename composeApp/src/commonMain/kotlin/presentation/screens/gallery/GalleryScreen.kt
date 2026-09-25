package io.github.vrcmteam.vrcm.presentation.screens.gallery

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.vrcmteam.vrcm.presentation.designsystem.*
import io.github.vrcmteam.vrcm.presentation.navigation.AppRoute
import io.github.vrcmteam.vrcm.presentation.compoments.animateScrollToTab
import org.koin.compose.viewmodel.koinViewModel
import io.github.vrcmteam.vrcm.presentation.navigation.LocalNavigator
import io.github.vrcmteam.vrcm.presentation.navigation.currentOrThrow
import io.github.vrcmteam.vrcm.presentation.screens.gallery.editor.PrintImageEditorSessionStore
import io.github.vrcmteam.vrcm.presentation.settings.locale.strings
import io.github.vrcmteam.vrcm.presentation.supports.AppIcons
import kotlinx.serialization.Serializable
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@Serializable
object GalleryScreen : AppRoute {

    private val tabPagers = listOf(
        GalleryTabPager.Companion.Gallery,
        GalleryTabPager.Companion.Print,
        GalleryTabPager.Companion.Icon,
        GalleryTabPager.Companion.Emoji,
        GalleryTabPager.Companion.Sticker,
    )

        @Composable
    override fun Content() {
        val galleryScreenModel: GalleryScreenModel = koinViewModel()
        val pagerState = rememberPagerState { tabPagers.size }
        val coroutineScope = rememberCoroutineScope()
        val navigator = LocalNavigator.currentOrThrow
        val editorSessionStore: PrintImageEditorSessionStore = koinInject()

        LaunchedEffect(Unit) {
            galleryScreenModel.init()
        }

        LaunchedEffect(editorSessionStore) {
            editorSessionStore.galleryUploadCompletions.collect(galleryScreenModel::refreshFiles)
        }

        AppScaffold(
            topBar = {
                AppNavBar(
                    title = {
                        AppText(
                            text = strings.galleryScreenTitle,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    navigationIcon = {
                        AppIconButton(onClick = { navigator.pop() }) {
                            AppIcon(
                                painter = rememberVectorPainter(AppIcons.ArrowBackIosNew),
                                contentDescription = "back"
                            )
                        }
                    },
                )
            },
        ) { paddingValues ->
            // 顶部留白做外边距（标签条固定在导航栏下方）；底部安全区由各标签页的网格承担，图片能滚到系统导航条下面
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = paddingValues.calculateTopPadding())
            ) {
                // 标签页
                AppScrollableTabRow(
                    edgePadding = 16.dp
                ) {
                    tabPagers.forEachIndexed { index, pager ->
                        AppTab(
                            selected = pagerState.currentPage == index,
                            onClick = {
                                coroutineScope.launch {
                                    pagerState.animateScrollToTab(index)
                                }
                            },
                            text = {
                                val tagType = pager.fileTagType
                                val count = galleryScreenModel.getFileCount(tagType)
                                val maxCount = galleryScreenModel.getMaxCount(tagType)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    AppText(
                                        text = pager.title,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    AppText(
                                        text = "$count/$maxCount",
                                        style = AppTheme.type.caption2Emphasized,
                                        fontSize = 10.sp,
                                        color = AppTheme.colors.label.copy(alpha = 0.6f),
                                        maxLines = 1
                                    )
                                }
                            }
                        )
                    }
                }

                // 标签页内容
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.weight(1f)
                ) { page ->
                    val tabPager = tabPagers[page]
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        tabPager.Content(galleryScreenModel)
                    }
                }
            }
        }
    }
} 
