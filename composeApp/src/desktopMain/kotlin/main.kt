package io.github.vrcmteam.vrcm

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import io.github.vrcmteam.vrcm.core.shared.AppConst.APP_NAME
import io.github.vrcmteam.vrcm.di.commonModules
import io.github.vrcmteam.vrcm.di.modules.platformModule
import org.jetbrains.compose.resources.painterResource
import org.koin.core.context.startKoin
import vrcm.composeapp.generated.resources.Res
import vrcm.composeapp.generated.resources.logo

fun main() = run {
    startKoin {
        printLogger()
        modules(commonModules + platformModule)
    }
    application {
        val windowState = rememberWindowState(
            width = 400.dp,
            height = 800.dp,
            position = WindowPosition.Aligned(Alignment.Center)
        )
        Window(
            state = windowState,
            onCloseRequest = ::exitApplication,
            title = APP_NAME,
            icon = painterResource(Res.drawable.logo)
        ) {
//            AppDesktopPreview()
            App()
        }
    }
}

@Composable
fun AppDesktopPreview() {
    ScaleOnScrollList()
}

@Composable
fun ScaleOnScrollList() {
    val scrollState = rememberLazyListState()
    val layoutInfo by remember { derivedStateOf { scrollState.layoutInfo}}

    LazyColumn(
        state = scrollState,
        modifier = Modifier.fillMaxSize()
    ) {
        items(100) { index ->
            val visibleItemInfo by remember { derivedStateOf {
                layoutInfo.visibleItemsInfo.find { it.index == index }
            } }

            // 计算缩放比例
            val scale by animateFloatAsState(
                targetValue = visibleItemInfo?.let {
                    val itemBottom = it.offset + it.size
                    val viewportBottom = layoutInfo.viewportEndOffset
                    val distanceFromBottom = viewportBottom - itemBottom

                    when {
                        // 元素完全在视口下方
                        distanceFromBottom < -it.size -> 0.7f
                        // 元素开始进入视口
                        distanceFromBottom < 0 -> 0.7f + 0.3f * (1 - distanceFromBottom / -it.size.toFloat())
                        // 元素完全可见
                        else -> 1f
                    }
                } ?: 0.7f,  // 不可见元素保持最小缩放
                animationSpec = tween(300)
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    }
                    .background(Color.LightGray)
                    .padding(8.dp)
            ) {
                Text("Item $index", fontSize = 18.sp)
            }
        }
    }
}


