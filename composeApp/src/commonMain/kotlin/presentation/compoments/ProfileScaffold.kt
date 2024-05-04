package io.github.vrcmteam.vrcm.presentation.compoments

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBackIosNew
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonColors
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.PlatformContext
import coil3.request.ImageRequest
import coil3.request.crossfade
import io.github.vrcmteam.vrcm.presentation.extensions.enableIf
import io.github.vrcmteam.vrcm.presentation.extensions.getInsetPadding
import io.github.vrcmteam.vrcm.presentation.supports.thresholdNestedScrollConnection
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import kotlin.math.roundToInt

/**
 * BottomCard和ProfileUserImage接触点的圆角大小
 */
private const val ContactPointShape = 36

/**
 * 详情页面脚手架
 * @param profileImageUrl 详情页背景图
 * @param iconUrl 详情页头像
 * @param onReturn 返回按钮点击事件
 * @param onMenu 菜单按钮点击事件
 * @param content 详情页内容
 */
@Composable
fun ProfileScaffold(
    profileImageUrl: String?,
    iconUrl: String?,
    onReturn: () -> Unit,
    onMenu:  () -> Unit,
    content: @Composable ColumnScope.(Float) -> Unit
) {
    BoxWithConstraints {
        val scrollState = rememberScrollState()

        val imageHeight = remember { maxHeight / 2.5f }
        // 位移的距离
        val offsetDp = with(LocalDensity.current) { scrollState.value.toDp() }
        // 剩下的距离
        val remainingDistance = imageHeight - offsetDp

        // image上滑比例
        val ratio = ((remainingDistance / imageHeight).coerceIn(0f, 1f)).let {
            FastOutSlowInEasing.transform(it)
        }
        // 模糊程度随着上滑的距离增加
        val fl = scrollState.value / imageHeight.value
        val blurDp = with(LocalDensity.current) { (fl * 20).toDp() }
        // topBar高度
        val topBarHeight = 64.dp
        // 系统栏高度
        val sysTopPadding = with(LocalDensity.current) {
            WindowInsets.systemBars.getTop(this).toDp()
        }
        // icon显示大小与透明度比例 单独一个比例是为了更加自然不会突然出现(因为icon只有当image隐藏时才开始显示)
        val topIconRatio =
            (remainingDistance / (topBarHeight + sysTopPadding)).coerceIn(0f, 1f).let {
                FastOutSlowInEasing.transform(1f - it)
            }
        val lastIconPadding = imageHeight - (topBarHeight * ratio)
        val scope = rememberCoroutineScope()
        val isHidden = topBarHeight + sysTopPadding < remainingDistance
        // 嵌套滑动,当父组件没有滑到maxValue时，父组件将消费滚动偏移量
        val nestedScrollConnection =
            thresholdNestedScrollConnection({ scrollState.value < scrollState.maxValue }) {
                scope.launch { scrollState.scrollTo((scrollState.value + -it).roundToInt()) }
            }
        Surface(
            Modifier
                .verticalScroll(scrollState)
                .height(imageHeight + maxHeight)
                .fillMaxWidth(),
            contentColor = MaterialTheme.colorScheme.primary
        ) {
            // 用户Image
            ProfileImage(
                imageHeight,
                offsetDp,
                ratio,
                blurDp,
                profileImageUrl
            )
            // 底部信息卡片
            BottomCard(
                imageHeight,
                ratio,
                topBarHeight,
                sysTopPadding,
                nestedScrollConnection,
                content
            )
            // 顶部导航栏
            TopMenuBar(
                topBarHeight,
                sysTopPadding,
                offsetDp,
                ratio,
                onReturn = onReturn,
                onMenu = onMenu
            )
            // 用户icon
            ProfileIcon(
                isHidden,
                lastIconPadding,
                offsetDp,
                imageHeight,
                topIconRatio,
                iconUrl,
            ) {
                scope.launch {
                    scrollState.animateScrollTo(0, spring(stiffness = Spring.StiffnessLow))
                }
            }
        }
    }

}

@Composable
private fun ProfileImage(
    imageHeight: Dp,
    offsetDp: Dp,
    ratio: Float,
    blurDp: Dp,
    imageUrl: String?
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
    ) {
        AImage(
            modifier = Modifier
                .height(imageHeight)
                .fillMaxWidth()
                .padding(top = offsetDp)
                .clip(
                    RoundedCornerShape(
                        bottomStart = (ContactPointShape * ratio).dp,
                        bottomEnd = (ContactPointShape * ratio).dp
                    )
                )
                .blur(blurDp),
            imageData = imageUrl,
        )
    }
}

/**
 * 底部信息卡片
 */
@Composable
private fun BottomCard(
    imageHeight: Dp,
    ratio: Float,
    topBarHeight: Dp,
    sysTopPadding: Dp,
    nestedScrollConnection: NestedScrollConnection,
    content: @Composable ColumnScope.(Float) -> Unit
) {
    // image上滑反比例
    val inverseRatio = 1 - ratio
    val scrollState = rememberScrollState()
    if (inverseRatio == 0f) {
        LaunchedEffect(Unit) {
            scrollState.animateScrollTo(0)
        }
    }
    Card(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = imageHeight),
        shape = RoundedCornerShape(
            topStart = (ContactPointShape * ratio).dp,
            topEnd = (ContactPointShape * ratio).dp
        ),
        colors = CardDefaults.cardColors(contentColor = MaterialTheme.colorScheme.primary)
    ) {
        Column(
            modifier = Modifier
                .nestedScroll(nestedScrollConnection)
                .padding(
                    start = 12.dp,
                    end = 12.dp,
                    bottom = getInsetPadding(12, WindowInsets::getBottom)
                ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height((topBarHeight + sysTopPadding) * inverseRatio))
            content(ratio)
        }
    }
}


@Composable
private fun ProfileIcon(
    isHidden: Boolean,
    lastIconPadding: Dp,
    offsetDp: Dp,
    imageHeight: Dp,
    topIconRatio: Float,
    avatarThumbnailImageUrl: String?,
    onClickIcon: () -> Unit = {}
) {
    val iconSize = (60 * topIconRatio).dp
    Box {
        Box(
            modifier = Modifier
                .systemBarsPadding()
                .fillMaxWidth()
                .enableIf(isHidden) { offset(y = offsetDp - imageHeight) }
                .padding(top = lastIconPadding)
                .alpha(topIconRatio),
        ) {
            AImage(
                modifier = Modifier
                    .align(Alignment.Center)
                    .clip(CircleShape)
                    .size(iconSize)
                    .clickable(onClick = onClickIcon),
                imageData = ImageRequest.Builder(koinInject<PlatformContext>())
                    .data(avatarThumbnailImageUrl)
                    .crossfade(600)
                    .size(70, 70).build(),
                contentDescription = "UserIcon",
            )
        }
    }
}

@Composable
private fun TopMenuBar(
    topBarHeight: Dp,
    sysTopPadding: Dp,
    offsetDp: Dp,
    ratio: Float,
    color: Color = MaterialTheme.colorScheme.onPrimary,
    onReturn: () -> Unit,
    onMenu: () -> Unit
) {
    // image上滑反比例
    val inverseRatio = 1 - ratio
    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .height(topBarHeight + sysTopPadding)
                .offset(y = offsetDp)
                .background(
                    color.copy(alpha = inverseRatio), MaterialTheme.shapes.medium.copy(
                        topStart = CornerSize(0.dp),
                        topEnd = CornerSize(0.dp)
                    )
                )
                .padding(top = sysTopPadding),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val iconColor = lerp(
                MaterialTheme.colorScheme.onPrimary,
                MaterialTheme.colorScheme.primary,
                inverseRatio
            )
            val actionColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f * ratio)
            val iconButtonColors = IconButtonColors(
                containerColor = actionColor,
                contentColor = iconColor,
                disabledContainerColor = Color.Unspecified,
                disabledContentColor = Color.Unspecified,
            )
            IconButton(
                modifier = Modifier
                    .padding(horizontal = 10.dp),
                colors = iconButtonColors,
                onClick = onReturn
            ) {
                Icon(
                    imageVector = Icons.Rounded.ArrowBackIosNew,
                    tint = iconColor,
                    contentDescription = "ReturnIcon"
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            IconButton(
                modifier = Modifier
                    .padding(horizontal = 10.dp),
                colors = iconButtonColors,
                onClick = onMenu
            ) {
                Icon(
                    imageVector = Icons.Rounded.Menu,
                    tint = iconColor,
                    contentDescription = "MenuIcon"
                )
            }
        }
    }
}