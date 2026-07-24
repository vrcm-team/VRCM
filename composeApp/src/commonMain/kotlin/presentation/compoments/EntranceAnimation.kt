package io.github.vrcmteam.vrcm.presentation.compoments

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.runtime.*

private const val EntranceDurationMillis = 350
private const val EntranceStaggerMillis = 60
private const val EntranceStaggerCap = 5

/**
 * 入场淡入动画规格，按 index 错开延迟（上限 [EntranceStaggerCap] 项）
 */
fun entranceFadeSpec(index: Int = 0): FiniteAnimationSpec<Float> = tween(
    durationMillis = EntranceDurationMillis,
    delayMillis = minOf(index, EntranceStaggerCap) * EntranceStaggerMillis,
    easing = FastOutSlowInEasing
)

/**
 * animateItem 的出现动画只对初次组合后新插入的项生效，
 * 因此先以空列表组合，下一帧再填入数据，让所有项走插入动画；
 * 后续列表更新直接透传，已有项不会重放入场动画。
 */
@Composable
fun <T> rememberStaggeredReveal(items: List<T>): List<T> {
    val shown = remember { mutableStateListOf<T>() }
    LaunchedEffect(items) { shown.addAll(items)  }
    return shown
}
