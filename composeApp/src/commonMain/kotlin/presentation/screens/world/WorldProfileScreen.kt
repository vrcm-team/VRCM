package io.github.vrcmteam.vrcm.presentation.screens.world

import androidx.compose.animation.*
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.times
import androidx.compose.ui.zIndex
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import io.github.vrcmteam.vrcm.presentation.compoments.*
import io.github.vrcmteam.vrcm.presentation.extensions.currentNavigator
import io.github.vrcmteam.vrcm.presentation.extensions.enableIf
import io.github.vrcmteam.vrcm.presentation.extensions.getInsetPadding
import io.github.vrcmteam.vrcm.presentation.extensions.simpleClickable
import io.github.vrcmteam.vrcm.presentation.screens.user.UserProfileScreen
import io.github.vrcmteam.vrcm.presentation.screens.user.data.UserProfileVo
import io.github.vrcmteam.vrcm.presentation.screens.world.components.InstanceCard
import io.github.vrcmteam.vrcm.presentation.screens.world.data.InstanceVo
import io.github.vrcmteam.vrcm.presentation.screens.world.data.WorldProfileVo
import io.github.vrcmteam.vrcm.presentation.supports.AppIcons
import presentation.compoments.TopMenuBar
import presentation.screens.world.InstancesDialog
import kotlin.math.abs

/**
 *
 * kotlin类作用描述
 *
 * @author 次音(CiYin) QQ:2964221430
 * @github <a href="https://github.com/Ci-Yin">Ci-Yin</a>
 * @since 2024/3/23 19:44
 * @version: 1.0
 */
class WorldProfileScreen(
    private val worldProfileVO: WorldProfileVo,
    private val location: String? = null,
    private val sharedSuffixKey: String,
) : Screen {

    @Composable
    override fun Content() {
        // 创建ViewModel
        val screenModel: WorldProfileScreenModel = koinScreenModel()

        // 收集ViewModel状态
        var currentWorldProfileVo by screenModel.worldProfileState
        val isLoading by screenModel.isLoading.collectAsState()
        val currentNavigator = currentNavigator
        // 组件首次加载时自动刷新数据
        LaunchedEffect(worldProfileVO.worldId) {
            currentWorldProfileVo = worldProfileVO
            screenModel.refreshWorldData()
        }

        CompositionLocalProvider(
            LocalSharedSuffixKey provides sharedSuffixKey,
        ) {
            WorldProfileContent(
                worldProfileVo = currentWorldProfileVo ?: worldProfileVO,
                onReturn = { currentNavigator.pop() },
                onMenu = { /* 打开菜单 */ },
                isRefreshing = isLoading,
                onRefresh = { screenModel.refreshWorldData() },
            )
        }
    }

    // 主要内容组件
    @Composable
    fun WorldProfileContent(
        worldProfileVo: WorldProfileVo,
        onReturn: () -> Unit = {},
        onMenu: () -> Unit = {},
        isRefreshing: Boolean = false,
        onRefresh: () -> Unit = {},
    ) {
        // 模糊效果状态
        val hazeState = remember { HazeState() }

        BoxWithConstraints(
            modifier = Modifier.fillMaxSize()
        ) {
            // ========== 尺寸计算 ==========
            val sysTopPadding = getInsetPadding(WindowInsets::getTop)
            val sizes = remember(maxHeight) {
                WorldDetailSizes(
                    imageHigh = maxHeight / 5,
                    collapsedHeight = sysTopPadding + maxHeight / 2.25f,  // 底部基本信息高度
                    halfExpandedHeight = (maxHeight / 1.5f) - 8.dp,  // 半展开高度
                    expandedHeight =  maxHeight - sysTopPadding,  // 完全展开高度
                    topBarHeight = 64.dp,
                    sysTopPadding = sysTopPadding
                )
            }

            // ========== BottomSheet状态管理 ==========
            var sheetState by remember { mutableStateOf(SheetState.HALF_EXPANDED) }
            var dragOffset by remember { mutableStateOf(0f) }

            // 计算目标高度和当前高度
            val bottomSheetState = calculateBottomSheetState(
                sheetState = sheetState,
                dragOffset = dragOffset,
                sizes = sizes
            )

            // ========== 渲染背景图像 ==========
            RenderBackgroundImage(
                worldId = location ?: worldProfileVo.worldId,
                imageUrl = worldProfileVo.worldImageUrl ?: "",
                hazeState = hazeState,
                imageHeight = sizes.imageHigh * 2
            )

            // ========== 应用模糊效果 ==========
            ApplyBlurEffect(
                hazeState = hazeState,
                blurRadius = bottomSheetState.blurRadius.dp,
                overlayAlpha = bottomSheetState.overlayAlpha
            )

            // ========== 主内容区域 ==========
            RenderMainContent(
                worldProfileVo = worldProfileVo,
                sizes = sizes,
                collapsedAlphaVariant = 1 - bottomSheetState.collapsedAlpha,
            )

            // ========== 顶部菜单栏 ==========
            RenderTopBar(
                worldName = worldProfileVo.worldName,
                blurProgress = bottomSheetState.blurProgress,
                topBarHeight = sizes.topBarHeight,
                sysTopPadding = sizes.sysTopPadding,
                onReturn = onReturn,
                onMenu = onMenu,
                onCollapse = { sheetState = SheetState.COLLAPSED },
                isRefreshing = isRefreshing,
                onRefresh = onRefresh
            )

            // ========== BottomSheet ==========
            RenderBottomSheet(
                worldProfileVo = worldProfileVo,
                bottomSheetState = bottomSheetState,
                sizes = sizes,
                onExpanded = { sheetState = SheetState.EXPANDED },
                onDragDelta = { delta -> dragOffset += -delta },
                onDragStopped = { velocity ->
                    // 决定最终状态并重置拖动偏移
                    sheetState = determineSheetState(
                        currentHeightValue = bottomSheetState.targetHeight.value + dragOffset,
                        velocity = velocity,
                        currentState = sheetState,
                        sizes = sizes
                    )
                    dragOffset = 0f
                },
            )
        }
    }


}

// 定义 SheetState 枚举，与 MainScreen 中保持一致
enum class SheetState { COLLAPSED, HALF_EXPANDED, EXPANDED }


// ======================================
// 数据类型
// ======================================

/**
 * 世界详情界面的尺寸计算
 */
private data class WorldDetailSizes(
    val imageHigh: Dp,
    val collapsedHeight: Dp,
    val halfExpandedHeight: Dp,
    val expandedHeight: Dp,
    val topBarHeight: Dp,
    val sysTopPadding: Dp,
)

/**
 * BottomSheet的状态信息
 */
private data class BottomSheetUIState(
    val targetHeight: Dp,
    val currentHeight: Dp,
    val animatedHeight: Dp,
    val blurProgress: Float,
    val blurRadius: Float,
    val blurAlpha: Float,
    val overlayAlpha: Float,
    val collapsedProgress: Float,
    val collapsedAlpha: Float,
)

// ======================================
// 状态计算函数
// ======================================

/**
 * 计算BottomSheet状态
 */
@Composable
private fun calculateBottomSheetState(
    sheetState: SheetState,
    dragOffset: Float,
    sizes: WorldDetailSizes,
): BottomSheetUIState {
    // 计算目标高度
    val targetHeight = when (sheetState) {
        SheetState.COLLAPSED -> sizes.collapsedHeight
        SheetState.HALF_EXPANDED -> sizes.halfExpandedHeight
        SheetState.EXPANDED -> sizes.expandedHeight
    }

    // 计算当前显示高度（原目标高度 + 拖动偏移）
    val currentHeight = (targetHeight.value + dragOffset / 2).coerceIn(
        sizes.collapsedHeight.value,
        sizes.expandedHeight.value
    ).dp

    // 使用动画平滑过渡
    val animatedHeight by animateDpAsState(
        targetValue = currentHeight,
        label = "BottomSheet Height Animation"
    )

    // 计算模糊相关状态
    val blurProgress = if (currentHeight.value > sizes.halfExpandedHeight.value) {
        (currentHeight.value - sizes.halfExpandedHeight.value) /
                (sizes.expandedHeight.value - sizes.halfExpandedHeight.value)
    } else {
        0f
    }.coerceIn(0f, 1f)

    val blurRadius by animateFloatAsState(
        targetValue = (blurProgress * 25f).coerceIn(0f, 25f),
        animationSpec = tween(100),
        label = "Blur Animation"
    )

    val blurAlpha by animateFloatAsState(
        targetValue = blurProgress,
        label = "Blur Animation"
    )

    val overlayAlpha by animateFloatAsState(
        targetValue = (blurProgress * 0.6f).coerceIn(0f, 0.6f),
        label = "Overlay Animation"
    )

    // 计算折叠状态进度
    val collapsedProgress = (currentHeight.value - sizes.collapsedHeight.value) /
            (sizes.halfExpandedHeight.value - sizes.collapsedHeight.value)
    val collapsedAlpha by animateFloatAsState(
        targetValue = collapsedProgress.coerceIn(0f, 1f),
    )

    return BottomSheetUIState(
        targetHeight = targetHeight,
        currentHeight = currentHeight,
        animatedHeight = animatedHeight,
        blurProgress = blurProgress,
        blurRadius = blurRadius,
        blurAlpha = blurAlpha,
        overlayAlpha = overlayAlpha,
        collapsedProgress = collapsedProgress,
        collapsedAlpha = collapsedAlpha
    )
}

/**
 * 根据拖动结束时的状态确定最终Sheet状态
 */
private fun determineSheetState(
    currentHeightValue: Float,
    velocity: Float,
    currentState: SheetState,
    sizes: WorldDetailSizes,
): SheetState {
    // 计算各状态高度
    val collapsedHeight = sizes.collapsedHeight.value
    val halfExpandedHeight = sizes.halfExpandedHeight.value
    val expandedHeight = sizes.expandedHeight.value

    // 计算当前高度距离各状态的距离
    val distToCollapsed = abs(currentHeightValue - collapsedHeight)
    val distToHalfExpanded = abs(currentHeightValue - halfExpandedHeight)
    val distToExpanded = abs(currentHeightValue - expandedHeight)

    // 计算相对位置 - 当前高度在整个范围内的位置比例(0~1)
    val positionRatio = (currentHeightValue - collapsedHeight) / (expandedHeight - collapsedHeight)

    // 速度处理 - 正规化速度值 (正值表示向下拖动/收起，负值表示向上拖动/展开)
    val normalizedVelocity = (velocity / 800f).coerceIn(-3f, 3f)

    // 防止状态跳跃：根据当前状态和速度限制可达状态
    val allowedStates = when (currentState) {
        SheetState.COLLAPSED -> {
            // 从折叠状态只能到达半展开
            if (normalizedVelocity < -1.5f) listOf(SheetState.HALF_EXPANDED)
            else listOf(SheetState.COLLAPSED, SheetState.HALF_EXPANDED)
        }

        SheetState.HALF_EXPANDED -> {
            // 从半展开可到达任何状态，但需要根据位置和速度判断
            listOf(SheetState.COLLAPSED, SheetState.HALF_EXPANDED, SheetState.EXPANDED)
        }

        SheetState.EXPANDED -> {
            // 从展开状态只能到达半展开
            if (normalizedVelocity > 1.5f) listOf(SheetState.HALF_EXPANDED)
            else listOf(SheetState.HALF_EXPANDED, SheetState.EXPANDED)
        }
    }

    // 强磁吸效果：如果非常接近某个状态且没有明显反向速度，直接返回该状态
    when {
        // 非常接近折叠状态 (距离小于总范围的10%)
        distToCollapsed < (expandedHeight - collapsedHeight) * 0.1f &&
                normalizedVelocity > -1f &&
                SheetState.COLLAPSED in allowedStates ->
            return SheetState.COLLAPSED

        // 非常接近半展开状态 (距离小于总范围的10%)
        distToHalfExpanded < (expandedHeight - collapsedHeight) * 0.1f &&
                abs(normalizedVelocity) < 1f &&
                SheetState.HALF_EXPANDED in allowedStates ->
            return SheetState.HALF_EXPANDED

        // 非常接近展开状态 (距离小于总范围的10%)
        distToExpanded < (expandedHeight - collapsedHeight) * 0.1f &&
                normalizedVelocity < 1f &&
                SheetState.EXPANDED in allowedStates ->
            return SheetState.EXPANDED
    }

    // 处理中等速度滑动 - 主要根据方向和位置决定
    if (abs(normalizedVelocity) > 1f) {
        return when {
            normalizedVelocity < 0 -> { // 向上滑动
                // 在底部区域向上滑，到达半展开
                if (positionRatio < 0.4f && SheetState.HALF_EXPANDED in allowedStates)
                    SheetState.HALF_EXPANDED
                // 在上部区域向上滑，且允许展开，则展开
                else if (positionRatio > 0.6f && SheetState.EXPANDED in allowedStates)
                    SheetState.EXPANDED
                // 默认保持在半展开
                else SheetState.HALF_EXPANDED
            }

            else -> { // 向下滑动
                // 在上部区域向下滑，到达半展开
                if (positionRatio > 0.6f && SheetState.HALF_EXPANDED in allowedStates)
                    SheetState.HALF_EXPANDED
                // 在底部区域向下滑，且允许折叠，则折叠
                else if (positionRatio < 0.4f && SheetState.COLLAPSED in allowedStates)
                    SheetState.COLLAPSED
                // 默认保持在半展开
                else SheetState.HALF_EXPANDED
            }
        }
    }

    // 对于低速或停止的情况，纯粹根据位置决定
    return when {
        // 位于下1/3区域，倾向于折叠
        positionRatio < 0.33f && SheetState.COLLAPSED in allowedStates ->
            SheetState.COLLAPSED
        // 位于上1/3区域，倾向于展开
        positionRatio > 0.67f && SheetState.EXPANDED in allowedStates ->
            SheetState.EXPANDED
        // 中间区域或其他情况，倾向于半展开
        else -> SheetState.HALF_EXPANDED
    }
}

// ======================================
// UI 渲染组件
// ======================================

/**
 * 渲染背景图像
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun RenderBackgroundImage(
    worldId: String,
    imageUrl: String,
    hazeState: HazeState,
    imageHeight: Dp,
) {
    AImage(
        modifier = Modifier
            .height(imageHeight)
            .hazeSource(hazeState)
            .sharedBoundsBy(worldId + "WorldImage"),
        imageData = imageUrl,
    )
}

/**
 * 应用模糊效果
 */
@Composable
private fun ApplyBlurEffect(
    hazeState: HazeState,
    blurRadius: Dp,
    overlayAlpha: Float,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .hazeEffect(
                hazeState,
                style = HazeStyle(
                    blurRadius = blurRadius,
                    tint = null,
                    backgroundColor = MaterialTheme.colorScheme.onSurface.copy(alpha = overlayAlpha)
                )
            )
    )
}

/**
 * 渲染主内容区域
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun RenderMainContent(
    worldProfileVo: WorldProfileVo,
    sizes: WorldDetailSizes,
    collapsedAlphaVariant: Float,
) {

    // 渐变和卡片样式
    val gradientBrush = Brush.verticalGradient(
        colors = listOf(
            Color.Transparent, // 起始颜色（完全透明）
            MaterialTheme.colorScheme.secondary // 结束
        ),
        startY = ((sizes.sysTopPadding + sizes.imageHigh )* 4 * 0.5f).value,
        endY = ((sizes.sysTopPadding + sizes.imageHigh) * 4f).value,
    )


    val itemSize = DpSize(86.dp, 70.dp)
    val navigator = LocalNavigator.currentOrThrow

    // 主内容区域
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(gradientBrush)
            .padding(horizontal = 8.dp),
    ) {
        Spacer(modifier = Modifier.height(sizes.imageHigh * 1.25f))

        // 顶部信息区
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().heightIn(max = sizes.imageHigh * 0.5f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.Start
            ) {
                ATooltipBox(
                    tooltip = {
                        Text(text = worldProfileVo.worldName)
                    },
                ) {
                    Text(
                        text = worldProfileVo.worldName,
                        color = MaterialTheme.colorScheme.onPrimary,
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                HorizontalDivider(thickness = 2.dp)
                ATooltipBox(
                    modifier = Modifier.clickable {
                        worldProfileVo.authorID?.let {
                            val userProfileScreen = UserProfileScreen(
                                userProfileVO = UserProfileVo(
                                    id = it
                                )
                            )
                            navigator.push(userProfileScreen)
                        }

                    },
                    tooltip = {
                        Text(text = worldProfileVo.authorName ?: "未知作者")
                    }
                ) {
                    Text(
                        text = worldProfileVo.authorName ?: "未知作者",
                        color = MaterialTheme.colorScheme.onTertiary,
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

            }
        }

        // 信息卡片区域
        AnimatedVisibility(
            visible = collapsedAlphaVariant > 0,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            FlowRow(
                modifier = Modifier.fillMaxWidth().alpha(collapsedAlphaVariant),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // 世界容量
                InfoItemBlock(
                    size = itemSize,
                    icon = AppIcons.Person,
                    label = "${worldProfileVo.capacity ?: 0}",
                    description = "容量"
                )

                // 推荐容量
                InfoItemBlock(
                    size = itemSize,
                    icon = AppIcons.Group,
                    label = "${worldProfileVo.recommendedCapacity ?: 0}",
                    description = "推荐容量"
                )

                // 访问次数
                InfoItemBlock(
                    size = itemSize,
                    icon = AppIcons.Visibility,
                    label = "${worldProfileVo.visits ?: 0}",
                    description = "访问"
                )

                // 收藏数
                InfoItemBlock(
                    size = itemSize,
                    icon = AppIcons.Favorite,
                    label = "${worldProfileVo.favorites ?: 0}",
                    description = "收藏"
                )

                // 热度
                InfoItemBlock(
                    size = itemSize,
                    icon = AppIcons.Whatshot,
                    label = "${worldProfileVo.heat ?: 0}",
                    description = "热度"
                )

                // 热门程度
                InfoItemBlock(
                    size = itemSize,
                    icon = AppIcons.Trending,
                    label = "${worldProfileVo.popularity ?: 0}",
                    description = "热门度"
                )

                // 版本
                InfoItemBlock(
                    size = itemSize,
                    icon = AppIcons.Update,
                    label = "v${worldProfileVo.version ?: 1}",
                    description = "版本"
                )

                // 更新时间
                InfoItemBlock(
                    size = itemSize,
                    icon = AppIcons.DateRange,
                    label = worldProfileVo.updatedAt?.substringBefore("T") ?: "未知",
                    description = "更新日期"
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))
    }
}


/**
 * 渲染顶部菜单栏
 */
@Composable
private fun RenderTopBar(
    worldName: String,
    blurProgress: Float,
    topBarHeight: Dp,
    sysTopPadding: Dp,
    onReturn: () -> Unit,
    onMenu: () -> Unit,
    onCollapse: () -> Unit,
    isRefreshing: Boolean = false,
    onRefresh: () -> Unit = {},
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .zIndex(20f) // 确保在所有内容之上
    ) {
        val topBarRatio = (1 - blurProgress).coerceIn(0f, 1f)

        // 添加TopMenuBar
        TopMenuBar(
            topBarHeight = topBarHeight,
            sysTopPadding = sysTopPadding,
            offsetDp = 0.dp,
            ratio = topBarRatio,
            color = MaterialTheme.colorScheme.surface,
            onReturn = onReturn,
            onMenu = onMenu
        )
        // 标题显示
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(topBarHeight + sysTopPadding)
                .alpha(blurProgress)
                .padding(top = sysTopPadding)
        ) {
            Row(
                modifier = Modifier
                    .align(Alignment.Center)
                    .simpleClickable(onClick = onCollapse),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    modifier = Modifier.widthIn(max = 200.dp),
                    text = worldName,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                // 刷新状态指示
                if (isRefreshing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                }
                // 刷新按钮
                if (blurProgress > 0.5f && !isRefreshing) {
                    IconButton(
                        onClick = onRefresh,
                        modifier = Modifier
                            .padding(end = 16.dp)
                    ) {
                        Icon(
                            imageVector = AppIcons.Update,
                            contentDescription = "refresh",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

/**
 * 渲染BottomSheet
 */
@Composable
private fun RenderBottomSheet(
    worldProfileVo: WorldProfileVo,
    bottomSheetState: BottomSheetUIState,
    sizes: WorldDetailSizes,
    onExpanded: () -> Unit,
    onDragDelta: (Float) -> Unit,
    onDragStopped: (Float) -> Unit,
) {
    var currentDialog by LocationDialogContent.current
    val sharedSuffixKey = LocalSharedSuffixKey.current
    val onShrinkCardClick: (InstanceVo) -> Unit = {
        if (currentDialog == null) {
            currentDialog = InstancesDialog(
                instance = it,
                sharedSuffixKey = sharedSuffixKey
            )
        }
    }
    // BottomSheet容器
    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(10f)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(bottomSheetState.animatedHeight)
                .align(Alignment.BottomCenter),
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            shadowElevation = 16.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .draggable(
                        state = rememberDraggableState(onDelta = onDragDelta),
                        orientation = Orientation.Vertical,
                        onDragStopped = { velocity -> onDragStopped(velocity) }
                    )
            ) {
                // 拖动指示条
                DragBar(dragOffset = bottomSheetState.currentHeight.value - bottomSheetState.targetHeight.value)

                // 全屏时添加额外空间
                Spacer(modifier = Modifier.height(bottomSheetState.blurProgress * (sizes.topBarHeight - 24.dp)))

                // 主要信息内容
                RenderBottomSheetContent(
                    worldProfileVo = worldProfileVo,
                    bottomSheetState = bottomSheetState,
                    onShrinkCardClick = onShrinkCardClick,
                    onExpanded = onExpanded
                )
            }
        }
    }
}

/**
 * 渲染BottomSheet内容
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RenderBottomSheetContent(
    worldProfileVo: WorldProfileVo,
    bottomSheetState: BottomSheetUIState,
    onShrinkCardClick: (InstanceVo) -> Unit,
    onExpanded: () -> Unit,
) {
    // 上滑渐变小
    val fl = 1 - bottomSheetState.blurProgress
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .padding(bottom = getInsetPadding(WindowInsets::getBottom))
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 标签区域
            AnimatedVisibility(
                visible = fl > 0f,
            ) {
                Column(
                    modifier = Modifier.alpha(fl),
                    verticalArrangement = Arrangement.spacedBy(4.dp * fl)
                ) {
                    // 描述标题
                    Text(
                        text = "世界描述",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(
                            alpha = 1f - (0.3f * abs(
                                (bottomSheetState.currentHeight.value - bottomSheetState.targetHeight.value).coerceIn(
                                    -30f,
                                    30f
                                )
                            ) / 30f)
                        )
                    )

                    // 描述内容
                    Text(
                        modifier = Modifier.heightIn(max = bottomSheetState.animatedHeight / 4).verticalScroll(rememberScrollState()),
                        text = worldProfileVo.worldDescription,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (worldProfileVo.tags?.isNotEmpty() == true) {
                        Text(
                            text = "所有标签",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.padding(bottom = 8.dp)
                        ) {
                            worldProfileVo.tags.forEach { tag ->
                                Badge(
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                                ) {
                                    Text(
                                        text = tag,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 堆叠卡片 - 改为随着滑动过程展开
            AnimatedVisibility(
                visible = bottomSheetState.collapsedAlpha > 0f,
                modifier = Modifier
                    .fillMaxWidth()
            ) {
                Box(modifier = Modifier.alpha(bottomSheetState.collapsedAlpha)) {
                    StackedCards(
                        instances = worldProfileVo.instances,
                        maxVisibleCards = 3,
                        // 传递展开程度，用于调整卡片样式
                        expandProgress = bottomSheetState.blurProgress,
                        onShrinkCardClick = { onShrinkCardClick(it) },
                        onExpandCardClick = { onExpanded() },
                    )
                }
            }
        }
        val buttonAlpha = (1 - bottomSheetState.blurProgress * 2).coerceIn(0f, 1f)
        AnimatedVisibility(
            modifier = Modifier
                .align(Alignment.BottomCenter),
            enter = slideInVertically { it },
            exit = slideOutVertically { it },
            visible = buttonAlpha > 0f,
        ) {
            // 操作按钮 - 始终显示在底部
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp)
                    .alpha(buttonAlpha)
                    .padding(vertical = 16.dp)
            ) {
                Button(
                    onClick = { /* 处理加入世界逻辑 */ },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("加入世界")
                }

                OutlinedButton(
                    onClick = { /* 处理收藏世界逻辑 */ },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("收藏世界")
                }
            }
        }
    }

}

/**
 * 拖动指示条
 */
@Composable
private fun DragBar(dragOffset: Float = 100f) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .width(40.dp)
                .height(4.dp)
                .clip(MaterialTheme.shapes.medium)
                .background(
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(
                        alpha = 0.3f + (0.7f * abs(dragOffset.coerceIn(-100f, 100f)) / 100f)
                    )
                )
        )
    }
}

/**
 * 信息块
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InfoItemBlock(
    color: Color = MaterialTheme.colorScheme.tertiary,
    size: DpSize,
    icon: ImageVector,
    label: String,
    description: String,
) {
    ATooltipBox(
        tooltip = {
            Text(
                text = description,
                style = MaterialTheme.typography.labelSmall
            )
        }
    ) {
        Column(
            modifier = Modifier
                .size(size)
                .clip(RoundedCornerShape(16.dp))
                .background(color)
                .padding(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                tint = MaterialTheme.colorScheme.onPrimary,
                contentDescription = description,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = label,
                color = MaterialTheme.colorScheme.onPrimary,
                style = MaterialTheme.typography.titleSmall,
            )

        }
    }

}


@Composable
fun AnimatedVisibilityScope.StackedCards(
    instances: List<InstanceVo>,
    maxVisibleCards: Int = 3,
    expandProgress: Float = 0f,  // 默认值为0，表示未展开
    onShrinkCardClick: (InstanceVo) -> Unit = {},
    onExpandCardClick: () -> Unit,
) {
    val size = instances.size
    // 如果没有实例，直接返回
    if (size == 0) return
    val isFullyExpanded = expandProgress >= 1f

    val doExpandCardClick = if (expandProgress == 0f) onExpandCardClick else null
    if (isFullyExpanded) {
        // 在完全展开状态下使用Column布局垂直排列所有卡片
        val lazyListState = rememberLazyListState()
        val layoutInfo by remember { derivedStateOf { lazyListState.layoutInfo } }
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth(),
            state = lazyListState,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            itemsIndexed(instances) { index, instance ->
                val visibleItemInfo by remember {
                    derivedStateOf {
                        layoutInfo.visibleItemsInfo.find { it.index == index }
                    }
                }
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
                    InstanceCard(
                        instance = instance,
                        size = instances.size - index,
                        index = index,
                        verticalOffset = 0.dp, // 在Column中不需要手动设置偏移
                        scaleEffect = 1f,
                        alphaEffect = 1f,
                        onClick = { onShrinkCardClick(instance) }
                    )
                }
            }
        }
    } else {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .enableIf(expandProgress >= 1f) {
                    fillMaxHeight()
                }
        ) {
            // 计算需要显示的卡片数量
            val visibleCardsCount = minOf(maxVisibleCards, size)

            // 只显示前visibleCardsCount张卡片，并且倒序渲染（最后一张卡片最先渲染，在最底层）
            // 获取要显示的卡片子列表
            val visibleCards = instances.take(visibleCardsCount)

            // 从下往上渲染卡片（索引从visibleCards.size-1到0）
            for (i in visibleCards.size - 1 downTo 1) {
                val instance = visibleCards[i]
                val cardIndex = i


                // 基础偏移和视觉效果
                val baseOffset = 10.dp * cardIndex
                val baseScale = 1f - (0.1f * cardIndex)
                val baseAlpha = 1f - (0.25f * cardIndex)

                // 随展开程度调整的偏移量（展开时增加间距）
                val expandedOffset = cardIndex * 130f
                val currentOffset = baseOffset + (expandedOffset.dp - baseOffset) * expandProgress

                // 随展开程度调整的透明度和缩放（展开时减少透明度和缩放效果）
                val currentScale = baseScale + ((1f - baseScale) * expandProgress)
                val currentAlpha = baseAlpha + ((1f - baseAlpha) * expandProgress)

                InstanceCard(
                    instance = instance,
                    size = size,
                    index = cardIndex,
                    verticalOffset = currentOffset,
                    scaleEffect = currentScale,
                    alphaEffect = currentAlpha
                )
            }

            // 显示顶部卡片（始终在最上方且完全不透明不缩小）
            if (visibleCards.isNotEmpty()) {
                InstanceCard(
                    instance = visibleCards.first(),
                    size = size,
                    index = 0,
                    verticalOffset = 0.dp,
                    scaleEffect = 1f,
                    alphaEffect = 1f,
                    onClick = doExpandCardClick
                )
            }

            // 显示剩余卡片数量的指示器
            if (instances.size > maxVisibleCards) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(bottom = 12.dp, end = 16.dp)
                        .alpha(1f - expandProgress) // 随着展开进度增加而变透明
                        .background(
                            color = MaterialTheme.colorScheme.tertiary,
                            shape = CircleShape
                        )
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "+${instances.size - maxVisibleCards}",
                        color = MaterialTheme.colorScheme.onTertiary,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }
    }
}
