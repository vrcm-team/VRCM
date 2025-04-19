package io.github.vrcmteam.vrcm.presentation.screens.gallery

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import io.github.vrcmteam.vrcm.presentation.settings.locale.strings
import io.github.vrcmteam.vrcm.presentation.supports.AppIcons
import kotlinx.coroutines.launch

object GalleryScreen : Screen {

    private val tabPagers = listOf(
        GalleryTabPager.Companion.Icon,
        GalleryTabPager.Companion.Emoji,
        GalleryTabPager.Companion.Sticker,
        GalleryTabPager.Companion.Gallery
    )

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val galleryScreenModel: GalleryScreenModel = koinScreenModel()
        val pagerState = rememberPagerState { tabPagers.size }
        val coroutineScope = rememberCoroutineScope()
        val navigator = LocalNavigator.currentOrThrow

        LaunchedEffect(Unit) {
            galleryScreenModel.init()
        }

        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = { Text(strings.galleryScreenTitle) },
                    navigationIcon = {
                        IconButton(onClick = { navigator.pop() }) {
                            Icon(
                                painter = rememberVectorPainter(AppIcons.ArrowBackIosNew),
                                contentDescription = "back"
                            )
                        }
                    },
                )
            },
            contentColor = MaterialTheme.colorScheme.primary
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                // 标签页
                TabRow(
                    selectedTabIndex = pagerState.currentPage,
                    indicator = {
                        TabRowDefaults.PrimaryIndicator(
                            modifier = Modifier
                                .tabIndicatorOffset(it[pagerState.currentPage])
                                .padding(horizontal = 24.dp),  // 添加上部圆角
                            shape = RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)
                        )
                    }
                ) {
                    tabPagers.forEachIndexed { index, pager ->
                        Tab(
                            selected = pagerState.currentPage == index,
                            onClick = {
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage(
                                        index,
                                        animationSpec = spring(
                                            dampingRatio = Spring.DampingRatioLowBouncy,
                                            stiffness = Spring.StiffnessLow
                                        )
                                    )
                                }
                            },
                            text = { Text(pager.title) }
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
                        tabPager.Content()
                    }
                }
            }
        }
    }
} 
