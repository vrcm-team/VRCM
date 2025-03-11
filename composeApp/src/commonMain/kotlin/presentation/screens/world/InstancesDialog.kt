package presentation.screens.world

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import io.github.vrcmteam.vrcm.presentation.compoments.LocalSharedSuffixKey
import io.github.vrcmteam.vrcm.presentation.compoments.SharedDialog
import io.github.vrcmteam.vrcm.presentation.screens.world.components.InstanceCard
import io.github.vrcmteam.vrcm.presentation.screens.world.data.InstanceVo
import io.github.vrcmteam.vrcm.presentation.supports.AppIcons

class InstancesDialog(
    private val instances: List<InstanceVo> = emptyList(),
    private val onClose: () -> Unit = {},
    private val sharedSuffixKey: String,
) : SharedDialog {
    @Composable
    override fun Content(animatedVisibilityScope: AnimatedVisibilityScope) {
        val lazyListState = rememberLazyListState()
        val layoutInfo by remember { derivedStateOf { lazyListState.layoutInfo}}
        CompositionLocalProvider(
            LocalSharedSuffixKey provides sharedSuffixKey
        ) {
            Column(
                modifier = Modifier.fillMaxSize().padding(8.dp).systemBarsPadding(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                FilledIconButton(
                    onClick = onClose
                ){
                    Icon(
                        imageVector = AppIcons.Close,
                        contentDescription = "Close"
                    )
                }
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(vertical = 16.dp),
                    state = lazyListState,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // 实例列表
                    itemsIndexed(instances) { index, instance ->
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
                                .graphicsLayer {
                                    scaleX = scale
                                    scaleY = scale
                                }
                        ) {
                            with(animatedVisibilityScope) {
                                InstanceCard(
                                    instance = instance,
                                    size = instances.size - 1,
                                    index = index,
                                    unfold = true
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    override fun close() = onClose()
}