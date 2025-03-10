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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.times
import androidx.compose.ui.zIndex
import cafe.adriel.voyager.core.screen.Screen
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import io.github.vrcmteam.vrcm.network.api.worlds.WorldsApi
import io.github.vrcmteam.vrcm.presentation.compoments.*
import io.github.vrcmteam.vrcm.presentation.extensions.currentNavigator
import io.github.vrcmteam.vrcm.presentation.extensions.enableIf
import io.github.vrcmteam.vrcm.presentation.extensions.getInsetPadding
import io.github.vrcmteam.vrcm.presentation.screens.world.data.InstanceVo
import io.github.vrcmteam.vrcm.presentation.screens.world.data.WorldProfileVo
import io.github.vrcmteam.vrcm.presentation.supports.AppIcons
import io.github.vrcmteam.vrcm.service.AuthService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import presentation.compoments.TopMenuBar
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
    private val sharedSuffixKey: String,
) : Screen {

    @Composable
    override fun Content() {
        val worldsApi: WorldsApi = koinInject()
        val authService: AuthService = koinInject()

        // 创建一个可变状态的WorldProfileVo，初始值为传入的worldProfileVO
        var currentWorldProfileVo by remember { mutableStateOf(worldProfileVO) }

        // 用于标记是否正在加载
        var isLoading by remember { mutableStateOf(false) }

        val currentNavigator = currentNavigator

        // 启动协程获取最新世界数据
        val scope = rememberCoroutineScope()

        // 定义刷新世界数据的函数
        val refreshWorldData = {
            if (!isLoading && currentWorldProfileVo.worldId.isNotBlank()) {
                isLoading = true
                scope.launch(Dispatchers.IO) {
                    authService.reTryAuthCatching {
                        worldsApi.getWorldById(currentWorldProfileVo.worldId)
                    }.onSuccess { worldData ->
                        println("worldData" + worldData)
                        // 获取实例ID列表
                        val instanceIds = worldData.instances?.map { it.firstOrNull() ?: "" }
                            ?.filter { it.isNotBlank() } ?: emptyList()
                        println("instanceIds" + instanceIds)
                        // 如果有实例ID，则获取实例信息
                        val instancesList = if (instanceIds.isNotEmpty()) {
                            authService.reTryAuthCatching {
                                instanceIds.map {
                                    worldsApi.getWorldInstanceById(currentWorldProfileVo.worldId, it)
                                }
                            }.getOrDefault(emptyList())
                        } else {
                            emptyList()
                        }
                        println("instancesList" + instancesList)
                        // 转换为InstanceVo列表,刷新覆盖原始数据
                        val instanceVoList = currentWorldProfileVo.instances.associate { it.instanceId to it } +
                                instancesList.associate{ it.instanceId to InstanceVo(it) }

                        // 确定当前实例ID


                        // 创建新的WorldProfileVo
                        currentWorldProfileVo = WorldProfileVo(
                            world = worldData,
                            instancesList = instanceVoList.values.toList(),
                        )
                    }.onFailure {
                        // 获取失败时保持使用原有数据
                        println("获取世界数据失败: ${it.message}")
                    }
                    isLoading = false
                }
            }
        }

        // 组件首次加载时自动刷新数据
        LaunchedEffect(worldProfileVO.worldId) {
            refreshWorldData()
        }
        CompositionLocalProvider(
            LocalSharedSuffixKey provides sharedSuffixKey,
        ) {
            WorldProfileContent(
                worldProfileVo = currentWorldProfileVo,
                onReturn = { currentNavigator.pop() },
                onMenu = { /* 打开菜单 */ },
                isRefreshing = isLoading,
                onRefresh = { refreshWorldData() },
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
                    collapsedHeight = maxHeight / 2.25f,  // 底部基本信息高度
                    halfExpandedHeight = (maxHeight / 1.5f) - 8.dp,  // 半展开高度
                    expandedHeight = maxHeight,  // 完全展开高度
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
                worldId = worldProfileVo.worldId,
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
    // 根据速度快速决定下一个状态
    if (abs(velocity) > 800f) {
        return when {
            velocity < 0 -> { // 快速向上
                when (currentState) {
                    SheetState.COLLAPSED -> SheetState.HALF_EXPANDED
                    SheetState.HALF_EXPANDED -> SheetState.EXPANDED
                    SheetState.EXPANDED -> SheetState.EXPANDED
                }
            }

            else -> { // 快速向下
                when (currentState) {
                    SheetState.COLLAPSED -> SheetState.COLLAPSED
                    SheetState.HALF_EXPANDED -> SheetState.COLLAPSED
                    SheetState.EXPANDED -> SheetState.HALF_EXPANDED
                }
            }
        }
    } else {
        // 根据当前高度决定最接近的状态
        return when {
            currentHeightValue < (sizes.collapsedHeight.value + sizes.halfExpandedHeight.value) / 2 ->
                SheetState.COLLAPSED

            currentHeightValue < (sizes.halfExpandedHeight.value + sizes.expandedHeight.value) / 2 ->
                SheetState.HALF_EXPANDED

            else ->
                SheetState.EXPANDED
        }
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
@OptIn(ExperimentalLayoutApi::class)
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
            MaterialTheme.colorScheme.primaryContainer // 结束
        ),
        startY = (sizes.imageHigh.value * 2f * 0.8f),
        endY = (sizes.imageHigh.value * 2f),
    )
    val cardBrush = Brush.verticalGradient(
        colors = listOf(
            MaterialTheme.colorScheme.primary,
            MaterialTheme.colorScheme.tertiary
        ),
    )

    val itemSize = DpSize(86.dp, 70.dp)

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
                Text(text = worldProfileVo.worldName, color = Color.White, style = MaterialTheme.typography.titleLarge)
                HorizontalDivider(thickness = 2.dp)
                Text(
                    text = worldProfileVo.authorName ?: "未知作者",
                    color = Color.White,
                    style = MaterialTheme.typography.labelMedium
                )
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
                    cardBrush = cardBrush,
                    size = itemSize,
                    icon = AppIcons.Person,
                    label = "${worldProfileVo.capacity ?: 0}",
                    description = "容量"
                )

                // 推荐容量
                InfoItemBlock(
                    cardBrush = cardBrush,
                    size = itemSize,
                    icon = AppIcons.Group,
                    label = "${worldProfileVo.recommendedCapacity ?: 0}",
                    description = "推荐容量"
                )

                // 访问次数
                InfoItemBlock(
                    cardBrush = cardBrush,
                    size = itemSize,
                    icon = AppIcons.Visibility,
                    label = "${worldProfileVo.visits ?: 0}",
                    description = "访问"
                )

                // 收藏数
                InfoItemBlock(
                    cardBrush = cardBrush,
                    size = itemSize,
                    icon = AppIcons.Favorite,
                    label = "${worldProfileVo.favorites ?: 0}",
                    description = "收藏"
                )

                // 热度
                InfoItemBlock(
                    cardBrush = cardBrush,
                    size = itemSize,
                    icon = AppIcons.Whatshot,
                    label = "${worldProfileVo.heat ?: 0}",
                    description = "热度"
                )

                // 热门程度
                InfoItemBlock(
                    cardBrush = cardBrush,
                    size = itemSize,
                    icon = AppIcons.Trending,
                    label = "${worldProfileVo.popularity ?: 0}",
                    description = "热门度"
                )

                // 版本
                InfoItemBlock(
                    cardBrush = cardBrush,
                    size = itemSize,
                    icon = AppIcons.Update,
                    label = "v${worldProfileVo.version ?: 1}",
                    description = "版本"
                )

                // 更新时间
                InfoItemBlock(
                    cardBrush = cardBrush,
                    size = itemSize,
                    icon = AppIcons.DateRange,
                    label = worldProfileVo.updatedAt?.substringBefore("T") ?: "未知",
                    description = "更新日期"
                )

                // 可用实例数
                if (worldProfileVo.instances.isNotEmpty()) {
                    InfoItemBlock(
                        cardBrush = cardBrush,
                        size = itemSize,
                        icon = AppIcons.Dashboard,
                        label = "${worldProfileVo.instances.size}",
                        description = "实例数"
                    )
                }
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
                .height(topBarHeight)
                .alpha(blurProgress)
                .padding(top = sysTopPadding)
        ) {
            Row(
                modifier = Modifier
                    .align(Alignment.Center)
                    .clickable(onClick = onCollapse),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = worldName,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )

                // 刷新状态指示
                if (isRefreshing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                }
            }

            // 刷新按钮
            if (blurProgress > 0.5f && !isRefreshing) {
                IconButton(
                    onClick = onRefresh,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 16.dp)
                ) {
                    Icon(
                        imageVector = AppIcons.Explore,
                        contentDescription = "刷新",
                        tint = MaterialTheme.colorScheme.primary
                    )
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
    onDragDelta: (Float) -> Unit,
    onDragStopped: (Float) -> Unit,
) {
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
                Spacer(modifier = Modifier.height(bottomSheetState.blurProgress * (sizes.topBarHeight + sizes.sysTopPadding - 24.dp)))

                // 主要信息内容
                RenderBottomSheetContent(
                    worldProfileVo = worldProfileVo,
                    bottomSheetState = bottomSheetState,
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
) {

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
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
            text = worldProfileVo.worldDescription,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(bottom = 4.dp)
        )

        var visible by remember { mutableStateOf(true) }
        var currentDialog by LocationDialogContent.current

        // 标签区域
        AnimatedVisibility(
            visible = bottomSheetState.collapsedAlpha > 0f,
        ) {
            Column(modifier = Modifier.alpha(bottomSheetState.collapsedAlpha)) {
                if (worldProfileVo.tags?.isNotEmpty() == true) {
                    Text(
                        text = "所有标签",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
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
        val sharedSuffixKey = LocalSharedSuffixKey.current
        // 堆叠卡片
        StackedCards(
            instances = worldProfileVo.instances,
            visible = visible,
            maxVisibleCards = 3,
            onCardClick = {
                visible = !visible
                currentDialog = InstancesDialog(
                    instances = worldProfileVo.instances,
                    sharedSuffixKey = sharedSuffixKey,
                    onClose = {
                        visible = !visible
                        currentDialog = null
                    },
                )
            }
        )

        // 详细信息区域
        AnimatedVisibility(
            visible = bottomSheetState.blurProgress > 0.1f
        ) {
            Column(modifier = Modifier.alpha(bottomSheetState.blurProgress)) {
                Text(
                    text = "详细信息",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 8.dp)
                )

                // 详细属性信息
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(vertical = 8.dp)
                ) {
                    InfoRow("访问次数", "${worldProfileVo.visits ?: 0}")
                    InfoRow("推荐容量", "${worldProfileVo.recommendedCapacity ?: 0}")
                    InfoRow("最大容量", "${worldProfileVo.capacity ?: 0}")
//                    InfoRow("活跃状态", if (currentInstance?.isActive == true) "活跃" else "不活跃")
//                    InfoRow("是否已满", if (currentInstance?.isFull == true) "已满" else "未满")
//                    InfoRow(
//                        "队列状态",
//                        if (currentInstance?.queueEnabled == true) "已启用 (${currentInstance.queueSize ?: 0}人等待)" else "未启用"
//                    )
//                    InfoRow(
//                        "可加入性",
//                        if (currentInstance?.hasCapacity == true) "可加入" else "不可加入"
//                    )
                    if (worldProfileVo.instances.size > 1) {
                        InfoRow("可用实例数", "${worldProfileVo.instances.size}")
                    }
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // 操作按钮 - 始终显示在底部
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp)
                .padding(16.dp)
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
 * 信息行
 */
@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

/**
 * 信息块
 */
@Composable
private fun InfoItemBlock(
    cardBrush: Brush,
    size: DpSize,
    icon: ImageVector,
    label: String,
    description: String,
) {
    Column(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(16.dp))
            .background(cardBrush)
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
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onPrimary,
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(top = 4.dp)
        )
        Text(
            text = description,
            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f),
            style = MaterialTheme.typography.labelSmall
        )
    }
}

@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalSharedTransitionApi::class)
@Composable
fun AnimatedVisibilityScope.InstanceCard(
    instance: InstanceVo? = null,
    size: Int,
    index: Int,
    unfold: Boolean = false,
    shape: Shape = RoundedCornerShape(32.dp),
    onClick: (() -> Unit)? = null,
) {
    val animatedScale by animateFloatAsState(
        targetValue = if (unfold) 1f else 1f - ((size - index) * 0.05f),
        label = "scale"
    )

    val animatedOffset by animateDpAsState(
        targetValue = if (unfold) 0.dp else ((size - index) * 8).dp,
        label = "offset"
    )

    val modifier = Modifier.fillMaxWidth().height(120.dp)
        .sharedBoundsBy(
            key = "StackCards${(size - index)}Container",
            sharedTransitionScope = LocalSharedTransitionDialogScope.current,
            animatedVisibilityScope = this,
            clipInOverlayDuringTransition = with(LocalSharedTransitionDialogScope.current) {
                OverlayClip(DialogShapeForSharedElement)
            }
        )
        .enableIf(!unfold) {
            offset(y = animatedOffset)
                .graphicsLayer(
                    alpha = 1f - ((size - index) * 0.2f),
                    clip = false,
                    shape = shape,
                    scaleX = animatedScale,
                )
        }

    val colors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary
    )

    if (onClick != null) {
        Card(
            modifier = modifier,
            colors = colors,
            onClick = onClick,
            shape = shape,
        ) {
            if (instance != null) {
                InstanceCardContent(instance)
            }
        }
    } else {
        Card(
            modifier = modifier,
            colors = colors,
            shape = shape,
        ) {
            if (instance != null) {
                InstanceCardContent(instance)
            }
        }
    }
}

@Composable
private fun ColumnScope.InstanceCardContent(instance: InstanceVo) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        // 左侧实例信息
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // 实例名称
            Text(
                text = instance.instanceName.takeIf { it.isNotBlank() } ?: "实例",
                style = MaterialTheme.typography.titleMedium
            )

            // 区域信息
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = AppIcons.Shield,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = instance.regionName ?: "未知区域",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        // 右侧用户统计
        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // 总用户数
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "${instance.currentUsers ?: 0} 用户",
                    style = MaterialTheme.typography.titleSmall
                )
                Icon(
                    imageVector = AppIcons.Person,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
            }

            // PC用户
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "${instance.pcUsers ?: 0} PC",
                    style = MaterialTheme.typography.bodySmall
                )
                Icon(
                    imageVector = AppIcons.Computer,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp)
                )
            }

            // Quest用户
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "${instance.androidUsers ?: 0} Quest",
                    style = MaterialTheme.typography.bodySmall
                )
                Icon(
                    imageVector = AppIcons.Smartphone,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }

    // 底部状态信息
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 活跃状态标签
        if (instance.isActive == true) {
            StatusChip(
                text = "活跃",
                icon = AppIcons.Check,
                color = MaterialTheme.colorScheme.tertiary
            )
        }

        // 队列状态标签
        if (instance.queueEnabled == true) {
            StatusChip(
                text = "队列: ${instance.queueSize ?: 0}",
                icon = AppIcons.Queue,
                color = MaterialTheme.colorScheme.secondary
            )
        }

        // 满员状态标签
        if (instance.isFull == true) {
            StatusChip(
                text = "已满",
                icon = AppIcons.Block,
                color = MaterialTheme.colorScheme.error
            )
        } else if (instance.hasCapacity == true) {
            StatusChip(
                text = "可加入",
                icon = AppIcons.Login,
                color = MaterialTheme.colorScheme.tertiary
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        // 所有者信息
        instance.ownerName?.let { name ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = AppIcons.Person,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
                )
                Text(
                    text = name,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@Composable
private fun StatusChip(
    text: String,
    icon: ImageVector,
    color: Color,
) {
    Surface(
        color = color.copy(alpha = 0.2f),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(12.dp),
                tint = color
            )
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                color = color
            )
        }
    }
}

class InstancesDialog(
    private val instances: List<InstanceVo> = emptyList(),
    private val onClose: () -> Unit = {},
    private val sharedSuffixKey: String,
) : SharedDialog {
    @Composable
    override fun Content(animatedVisibilityScope: AnimatedVisibilityScope) {
        CompositionLocalProvider(
            LocalSharedSuffixKey provides sharedSuffixKey
        ) {
            Box(
                modifier = Modifier.fillMaxSize().padding(8.dp).systemBarsPadding()
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // 标题作为第一个项目
                    item {
                        Text(
                            text = "可用实例列表 (${instances.size})",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }

                    // 实例列表
                    itemsIndexed(instances) { index, instance ->
                        with(animatedVisibilityScope) {
                            InstanceCard(
                                instance = instance,
                                size = instances.size - 1,
                                index = index,
                                unfold = true,
                                onClick = { onClose() }
                            )
                        }
                    }
                }
            }
        }
    }

    override fun close() = onClose()
}

@Composable
fun ColumnScope.StackedCards(
    instances: List<InstanceVo>,
    visible: Boolean,
    maxVisibleCards: Int = 3,
    onCardClick: () -> Unit
) {
    AnimatedVisibility(
        visible = visible
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            val visibleInstances = instances.take(maxVisibleCards)
            val size = visibleInstances.size
            
            // 显示背景卡片（最多显示maxVisibleCards-1张背景卡片）
            if (size > 1) {
                visibleInstances.drop(1).forEachIndexed { index, instance ->
                    InstanceCard(
                        instance = instance,
                        size = size - 1,
                        index = index,
                    )
                }
            }
            
            // 显示顶部卡片
            InstanceCard(
                instance = visibleInstances.firstOrNull(),
                size = size,
                index = size,
                onClick = onCardClick
            )
            
            // 显示剩余卡片数量的指示器
            if (instances.size > maxVisibleCards) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 8.dp, end = 16.dp)
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