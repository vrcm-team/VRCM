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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.times
import androidx.compose.ui.zIndex
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import io.github.vrcmteam.vrcm.network.api.worlds.WorldsApi
import io.github.vrcmteam.vrcm.presentation.compoments.*
import io.github.vrcmteam.vrcm.presentation.extensions.currentNavigator
import io.github.vrcmteam.vrcm.presentation.extensions.enableIf
import io.github.vrcmteam.vrcm.presentation.extensions.getInsetPadding
import io.github.vrcmteam.vrcm.presentation.screens.user.UserProfileScreen
import io.github.vrcmteam.vrcm.presentation.screens.user.data.UserProfileVo
import io.github.vrcmteam.vrcm.presentation.screens.world.components.InstanceCard
import io.github.vrcmteam.vrcm.presentation.screens.world.data.InstanceVo
import io.github.vrcmteam.vrcm.presentation.screens.world.data.WorldProfileVo
import io.github.vrcmteam.vrcm.presentation.supports.AppIcons
import io.github.vrcmteam.vrcm.service.AuthService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
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
                        // 获取实例ID列表
                        val instanceIds = worldData.instances?.map { it.firstOrNull() ?: "" }
                            ?.filter { it.isNotBlank() } ?: emptyList()
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
                        // 转换为InstanceVo列表,刷新覆盖原始数据
                        val instanceVoList = currentWorldProfileVo.instances.associate { it.instanceId to it } +
                                instancesList.associate { it.id to InstanceVo(it) }

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
    
    // 速度因子（正值表示向下拖动，负值表示向上拖动）
    val velocityFactor = (velocity / 800f).coerceIn(-1f, 1f)
    
    // 距离因子计算 - 使用归一化距离以统一评分标准
    val totalRange = expandedHeight - collapsedHeight
    val distToCollapsed = abs(currentHeightValue - collapsedHeight) / totalRange
    val distToHalf = abs(currentHeightValue - halfExpandedHeight) / totalRange
    val distToExpanded = abs(currentHeightValue - expandedHeight) / totalRange
    
    // 增加状态偏好因子 - 使评分受当前接近哪个阈值边界的影响
    val statePreferenceFactor = when {
        currentHeightValue < (collapsedHeight + halfExpandedHeight) / 2 -> -0.2f // 偏好收起状态
        currentHeightValue > (halfExpandedHeight + expandedHeight) / 2 -> 0.2f  // 偏好展开状态
        else -> 0f  // 中间区域不偏好
    }

    // 位置和速度的综合评分（越低越倾向于该状态）
    val collapsedScore =
        distToCollapsed - velocityFactor * 0.8f - (if (statePreferenceFactor < 0) abs(statePreferenceFactor) else 0f)
    val halfScore = distToHalf - abs(velocityFactor) * 0.2f
    val expandedScore =
        distToExpanded + velocityFactor * 0.8f - (if (statePreferenceFactor > 0) statePreferenceFactor else 0f)

    // 如果速度很大，直接基于方向决定
    if (abs(velocity) > 1500f) {
        return when {
            velocity < 0 -> when (currentState) {
                SheetState.COLLAPSED -> SheetState.HALF_EXPANDED
                else -> SheetState.EXPANDED
            }
            else -> when (currentState) {
                SheetState.EXPANDED -> SheetState.HALF_EXPANDED
                else -> SheetState.COLLAPSED
            }
        }
    }
    
    // 根据综合评分选择最佳状态
    return when {
        collapsedScore <= halfScore && collapsedScore <= expandedScore -> SheetState.COLLAPSED
        halfScore <= collapsedScore && halfScore <= expandedScore -> SheetState.HALF_EXPANDED
        else -> SheetState.EXPANDED
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
        startY = (sizes.imageHigh.value * 2f * 0.5f),
        endY = (sizes.imageHigh.value * 2f),
    )


    val itemSize = DpSize(86.dp, 70.dp)
    val navigator = LocalNavigator.currentOrThrow

    // 主内容区域
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(gradientBrush)
            .padding(horizontal = 8.dp)
            .systemBarsPadding(),
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
                        Text(text = worldProfileVo.worldName,)
                    },
                ){
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
                        Text(text = worldProfileVo.authorName ?: "未知作者",)
                    }
                ){
                    Text(
                        text = worldProfileVo.authorName ?: "未知作者",
                        color =  MaterialTheme.colorScheme.onTertiary,
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

                // 可用实例数
                if (worldProfileVo.instances.isNotEmpty()) {
                    InfoItemBlock(
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
                .height(topBarHeight + sysTopPadding)
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
            .padding(horizontal = 16.dp)
            .padding(bottom = getInsetPadding(WindowInsets::getBottom)),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {

        var visible by remember { mutableStateOf(true) }
        var currentDialog by LocationDialogContent.current

        // 标签区域
        AnimatedVisibility(
            visible = 1 - bottomSheetState.blurProgress > 0f,
        ) {
            Column(modifier = Modifier.alpha(1 - bottomSheetState.blurProgress)) {
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

        // 堆叠卡片 - 改为随着滑动过程展开
        AnimatedVisibility(
            visible = visible && bottomSheetState.collapsedAlpha > 0f,
            modifier = Modifier
                .fillMaxWidth()
        ) {
            Box(modifier =  Modifier.alpha(bottomSheetState.collapsedAlpha)){
                //            if (visible) {
                StackedCards(
                    instances = worldProfileVo.instances,
                    maxVisibleCards = 3,
                    // 传递展开程度，用于调整卡片样式
                    expandProgress = bottomSheetState.blurProgress,
                    onCardClick = {
                        visible = false
                        currentDialog = InstancesDialog(
                            instances = worldProfileVo.instances,
                            sharedSuffixKey = sharedSuffixKey,
                            onClose = {
                                visible = true
                                currentDialog = null
                            },
                        )
                    }
                )
//            }
            }

        }

        Spacer(modifier = Modifier.weight(1f))
        val buttonAlpha = (1 - bottomSheetState.blurProgress * 2).coerceIn(0f, 1f)
        AnimatedVisibility(
            visible = buttonAlpha > 0f,
        ) {
            // 操作按钮 - 始终显示在底部
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp)
                    .alpha(buttonAlpha)
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
    color: Color = MaterialTheme.colorScheme.tertiary ,
    size: DpSize,
    icon: ImageVector,
    label: String,
    description: String,
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


@Composable
fun AnimatedVisibilityScope.StackedCards(
    instances: List<InstanceVo>,
    maxVisibleCards: Int = 3,
    expandProgress: Float = 0f,  // 默认值为0，表示未展开
    onCardClick: () -> Unit,
) {

    val visibleInstances = instances
    val size = visibleInstances.size
    // 如果没有实例，直接返回
    if (size == 0) return
    val isFullyExpanded = expandProgress >= 1f

    val doCardClick = if (expandProgress == 0f) onCardClick else null
    // 创建滚动状态
    val scrollState = rememberScrollState()
    if (isFullyExpanded){
        // 在完全展开状态下使用Column布局垂直排列所有卡片
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 依次显示其他卡片
            instances.forEachIndexed { index, instance ->
                InstanceCard(
                    instance = instance,
                    size = instances.size - index,
                    index = index,
                    verticalOffset = 0.dp, // 在Column中不需要手动设置偏移
                    scaleEffect = 1f,
                    alphaEffect = 1f,
                    onClick = onCardClick
                )
            }
        }
    }else{
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .enableIf(expandProgress >= 1f) {
                    fillMaxHeight()
                }
        ) {
            val visibleInstances = instances

            // 显示背景卡片（最多显示maxVisibleCards-1张背景卡片）
            visibleInstances.drop(1).reversed().forEachIndexed { index, instance ->
                // 修改计算逻辑，使卡片从下方展开
                // 当expandProgress为0时，卡片堆叠在一起
                // 当expandProgress接近1时，卡片向下展开，每张卡片之间有固定间距
                val cardIndex = size - index - 2  // 调整索引，使其从0开始
                val stackedOffset = 8f  // 堆叠状态下的偏移
                val expandedOffset = (cardIndex + 1) * 130f  // 展开状态下的间距

                // 新的计算方式：从堆叠状态过渡到展开状态
                val cardOffset = stackedOffset + (expandedOffset - stackedOffset) * expandProgress

                InstanceCard(
                    instance = instance,
                    size = size - 1,
                    index = index,
                    // 使用计算出的偏移量
                    verticalOffset = cardOffset.dp,
                    // 随着展开减少透明度效果，但增加缩放效果
                    scaleEffect = 1f - ((size - index - 1) * 0.05f * (1f - expandProgress)),
                    alphaEffect = 1f - ((size - index - 1) * 0.6f * (1f - expandProgress)),
                    onClick = doCardClick
                )
            }

            // 显示顶部卡片（始终在最上方）
            InstanceCard(
                instance = visibleInstances.first(),
                size = size,
                index = size,
                verticalOffset = 0.dp,  // 顶部卡片始终保持在顶部位置
                scaleEffect = 1f, // 顶层卡片始终保持原始大小
                alphaEffect = 1f, // 顶层卡片始终完全不透明
                onClick = doCardClick
            )

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
