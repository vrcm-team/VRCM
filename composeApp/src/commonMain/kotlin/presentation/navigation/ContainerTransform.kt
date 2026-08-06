package io.github.vrcmteam.vrcm.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.currentCompositeKeyHashCode
import androidx.compose.runtime.saveable.rememberSaveable
import io.github.vrcmteam.vrcm.presentation.compoments.LocalSharedTransitionScreenScope

internal fun containerTransformToken(seed: Any?, positionKey: Any?): String =
    "ct-$seed-$positionKey"

/**
 * 为当前组合位置生成可跨重组恢复的共享过渡 token。
 *
 * 同一业务对象出现在多个列表位置时会得到不同 token，点击方应将该 token 随导航传给目标页面，
 * 从而只让被点击的元素参与共享过渡。不在共享过渡宿主内时返回 null。
 */
@Composable
fun rememberContainerTransformToken(seed: Any?): String? {
    if (LocalSharedTransitionScreenScope.current == null) return null
    val positionKey = currentCompositeKeyHashCode
    return rememberSaveable(seed) { containerTransformToken(seed, positionKey) }
}
