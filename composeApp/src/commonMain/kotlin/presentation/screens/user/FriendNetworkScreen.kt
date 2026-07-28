package io.github.vrcmteam.vrcm.presentation.screens.user

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import io.github.vrcmteam.vrcm.core.algorithms.ForceLayoutResult
import io.github.vrcmteam.vrcm.core.algorithms.convexHull
import io.github.vrcmteam.vrcm.network.api.users.data.MutualFriendData
import io.github.vrcmteam.vrcm.presentation.compoments.ABottomSheet
import io.github.vrcmteam.vrcm.presentation.compoments.UserStateIcon
import io.github.vrcmteam.vrcm.presentation.screens.user.data.UserProfileVo
import io.github.vrcmteam.vrcm.presentation.settings.locale.strings
import io.github.vrcmteam.vrcm.presentation.supports.AppIcons
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlin.time.ExperimentalTime

object FriendNetworkScreen : Screen {

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val model: FriendNetworkScreenModel = koinScreenModel()
        val state = model.uiState
        val selectedIdState = remember { mutableStateOf<String?>(null) }
        val highlightIdState = remember { mutableStateOf<String?>(null) }
        // 图例选中的社区，与个人长按高亮互斥
        val selectedCommunityState = remember { mutableStateOf<Int?>(null) }
        var showSheet by remember { mutableStateOf(false) }
        val sheetState = rememberModalBottomSheetState()

        val density = LocalDensity.current
        val nodeSizePx = with(density) { (43.dp + 34.dp).toPx() }
        LaunchedEffect(Unit) {
            model.loadCache(nodeSizePx)
        }

        val nodeMap = remember(state.nodes) { state.nodes.associateBy { it.id } }
        val selectedNode = selectedIdState.value?.let { nodeMap[it] }
        val mutualIds = selectedIdState.value?.let { state.edges[it].orEmpty() }.orEmpty()
        // 高亮集合（社区整团或个人+邻居），draw 阶段读取，避免高亮变化触发全图重组
        val highlightIdsState = remember(model) {
            derivedStateOf {
                val communityId = selectedCommunityState.value
                if (communityId != null) {
                    model.uiState.communities.filterValues { it == communityId }.keys
                } else {
                    highlightIdState.value?.let { id ->
                        setOf(id) + model.uiState.edges[id].orEmpty()
                    }.orEmpty()
                }
            }
        }

        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            text = strings.friendNetworkTitle,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { navigator.pop() }) {
                            Icon(
                                painter = rememberVectorPainter(AppIcons.ArrowBackIosNew),
                                tint = MaterialTheme.colorScheme.primary,
                                contentDescription = "back"
                            )
                        }
                    },
                    actions = {
                        IconButton(
                            enabled = !state.isLoading,
                            onClick = { model.refresh(nodeSizePx) }
                        ) {
                            Icon(
                                painter = rememberVectorPainter(AppIcons.Update),
                                tint = MaterialTheme.colorScheme.primary,
                                contentDescription = "refresh"
                            )
                        }
                    }
                )
            },
            contentColor = MaterialTheme.colorScheme.primary
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                FriendNetworkHeader(
                    updatedAt = state.updatedAt,
                    isFromCache = state.isFromCache,
                    progress = state.progress,
                    isLoading = state.isLoading
                )
                if (state.communityLegend.isNotEmpty()) {
                    CommunityLegendRow(
                        legend = state.communityLegend,
                        selectedCommunity = selectedCommunityState.value,
                        onCommunityClick = { communityId ->
                            selectedCommunityState.value =
                                if (selectedCommunityState.value == communityId) null else communityId
                            highlightIdState.value = null
                        }
                    )
                }
                Box(modifier = Modifier.fillMaxSize()) {
                    val layout = state.layout
                    if (state.nodes.isEmpty() && !state.isLoading && !state.isPreparing) {
                        Text(
                            modifier = Modifier.align(Alignment.Center),
                            text = strings.friendNetworkEmpty,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.outline
                        )
                    } else if (layout != null && state.nodes.isNotEmpty()) {
                        FriendNetworkGraph(
                            nodes = state.nodes,
                            edges = state.edges,
                            nodeColors = state.nodeColors,
                            communities = state.communities,
                            layout = layout,
                            highlightIdsState = highlightIdsState,
                            selectedIdState = selectedIdState,
                            highlightIdState = highlightIdState,
                            selectedCommunityState = selectedCommunityState,
                            onNodeTap = { nodeId ->
                                val highlighted = highlightIdsState.value
                                if (highlighted.isNotEmpty() && nodeId !in highlighted) {
                                    // 高亮模式下点击未高亮的节点：退出高亮，不打开详情
                                    highlightIdState.value = null
                                    selectedCommunityState.value = null
                                } else {
                                    selectedIdState.value = nodeId
                                    showSheet = true
                                }
                            },
                            onNodeLongPress = {
                                highlightIdState.value = it
                                selectedCommunityState.value = null
                            },
                            onBackgroundTap = {
                                highlightIdState.value = null
                                selectedCommunityState.value = null
                            },
                        )
                    }
                    if ((state.isLoading || state.isPreparing) && state.nodes.isEmpty()) {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    }
                }
            }
        }

        ABottomSheet(
            isVisible = showSheet && selectedNode != null,
            sheetState = sheetState,
            onDismissRequest = {
                showSheet = false
                selectedIdState.value = null
            }
        ) {
            val node = selectedNode ?: return@ABottomSheet
            val mutualUsers = mutualIds.mapNotNull { nodeMap[it] }
                .sortedBy { it.displayName.lowercase() }
            FriendNetworkSheet(
                node = node,
                mutualUsers = mutualUsers,
                onUserClick = {
                    navigator.push(UserProfileScreen(UserProfileVo(it)))
                    showSheet = false
                    selectedIdState.value = null
                }
            )
        }
    }
}

@Composable
private fun FriendNetworkHeader(
    updatedAt: Long?,
    isFromCache: Boolean,
    progress: FriendNetworkProgress?,
    isLoading: Boolean,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        // 更新时间与缓存提示合并为一行，压缩头部高度
        val infoLine = buildList {
            updatedAt?.let { add(strings.friendNetworkLastUpdated.replace("%s", formatTimestamp(it))) }
            if (isFromCache) add(strings.friendNetworkCacheHint)
        }.joinToString(" · ")
        if (infoLine.isNotEmpty()) {
            Text(
                text = infoLine,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (isLoading) {
            val progressText = progress?.let { "${it.current}/${it.total}" }.orEmpty()
            Text(
                text = strings.friendNetworkBuilding.replace("%s", progressText),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1
            )
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
}

@Composable
private fun CommunityLegendRow(
    legend: List<CommunitySummary>,
    selectedCommunity: Int?,
    onCommunityClick: (Int) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        legend.forEach { community ->
            val isSelected = selectedCommunity == community.id
            Row(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(
                        if (isSelected) community.color.copy(alpha = 0.16f)
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                    .border(
                        width = 1.dp,
                        color = if (isSelected) community.color else Color.Transparent,
                        shape = CircleShape
                    )
                    .clickable { onCommunityClick(community.id) }
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(community.color, CircleShape)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "${community.name} · ${community.count}",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isSelected) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
    }
}

private class EdgePathData(
    val from: String,
    val to: String,
    // 两端同社区时为社区 ID，跨社区为 null
    val communityId: Int?,
    val path: Path,
)

private class HullPathData(
    val communityId: Int,
    val color: Color,
    val path: Path,
)

@Composable
private fun FriendNetworkGraph(
    nodes: List<MutualFriendData>,
    edges: Map<String, List<String>>,
    nodeColors: Map<String, Color>,
    communities: Map<String, Int>,
    layout: ForceLayoutResult,
    highlightIdsState: State<Set<String>>,
    selectedIdState: State<String?>,
    highlightIdState: State<String?>,
    selectedCommunityState: State<Int?>,
    onNodeTap: (String) -> Unit,
    onNodeLongPress: (String) -> Unit,
    onBackgroundTap: () -> Unit,
) {
    // clipToBounds：画布经 graphicsLayer 平移/缩放后不得越界画到上方的图例和头部信息上
    BoxWithConstraints(modifier = Modifier.fillMaxSize().clipToBounds()) {
        val density = LocalDensity.current
        val baseNodeSize = 43.dp
        val maxExtraSize = 34.dp
        val baseNodeSizePx = with(density) { baseNodeSize.toPx() }
        val maxExtraSizePx = with(density) { maxExtraSize.toPx() }
        val labelWidth = 88.dp
        val labelWidthPx = with(density) { labelWidth.toPx() }
        val viewWidthPx = with(density) { maxWidth.toPx() }
        val viewHeightPx = with(density) { maxHeight.toPx() }
        val layoutWidthPx = layout.layoutWidthPx
        val layoutHeightPx = layout.layoutHeightPx
        // 大图允许缩小到刚好能看到全貌
        val fitScale = minOf(viewWidthPx / layoutWidthPx, viewHeightPx / layoutHeightPx)
        val minScale = minOf(0.35f, fitScale * 0.8f)
        val maxScale = 3.5f
        // 初始缩放不低于 0.6，保证节点一眼可辨；想看全貌可以继续缩小到 minScale
        val initialScale = maxOf(
            viewWidthPx / layoutWidthPx,
            viewHeightPx / layoutHeightPx
        ).coerceIn(0.6f, 1.25f)
        var scale by remember(nodes.size, viewWidthPx, viewHeightPx) { mutableStateOf(initialScale) }
        var offset by remember(nodes.size, viewWidthPx, viewHeightPx) { mutableStateOf(Offset.Zero) }
        var hasUserInteracted by remember(nodes.size) { mutableStateOf(false) }
        val edgeList = remember(edges) { buildEdgeList(edges) }
        // 计算每个节点的度数（连接数）
        val nodeDegree = remember(nodes, edges) { nodes.associate { it.id to edges[it.id].orEmpty().size } }
        val maxDegree = remember(nodeDegree) { nodeDegree.values.maxOrNull() ?: 1 }
        val positions = layout.positions
        // 预构建边的弧线 Path，避免每帧重建
        val edgePaths = remember(edgeList, layout, communities) {
            edgeList.mapNotNull { (from, to) ->
                val fromPos = positions[from] ?: return@mapNotNull null
                val toPos = positions[to] ?: return@mapNotNull null
                // 弧线控制点：在中点垂直方向偏移，弧度约为线长的 15%
                val midX = (fromPos.x + toPos.x) / 2f
                val midY = (fromPos.y + toPos.y) / 2f
                val dx = toPos.x - fromPos.x
                val dy = toPos.y - fromPos.y
                val len = sqrt(dx * dx + dy * dy)
                val curvature = len * 0.15f
                val nx = if (len > 0f) -dy / len else 0f
                val ny = if (len > 0f) dx / len else 0f
                val path = Path().apply {
                    moveTo(fromPos.x, fromPos.y)
                    quadraticBezierTo(midX + nx * curvature, midY + ny * curvature, toPos.x, toPos.y)
                }
                val fromCommunity = communities[from]
                val communityId = if (fromCommunity != null && fromCommunity == communities[to]) fromCommunity else null
                EdgePathData(from, to, communityId, path)
            }
        }
        // 真实社区的凸包气泡（扩张到头像外约 46dp）
        val hullPaddingPx = with(density) { 46.dp.toPx() }
        val hullStrokeWidthPx = with(density) { 24.dp.toPx() }
        val hullPaths = remember(layout, communities) {
            communities.entries
                .filter { it.value != FriendNetworkScreenModel.OTHER_COMMUNITY_ID }
                .groupBy({ it.value }, { it.key })
                .mapNotNull { (communityId, members) ->
                    val points = members.mapNotNull { positions[it] }
                    if (points.size < 3) return@mapNotNull null
                    val hull = convexHull(points)
                    if (hull.size < 2) return@mapNotNull null
                    var centerX = 0f
                    var centerY = 0f
                    points.forEach { centerX += it.x; centerY += it.y }
                    centerX /= points.size
                    centerY /= points.size
                    val path = Path()
                    hull.forEachIndexed { index, p ->
                        val dx = p.x - centerX
                        val dy = p.y - centerY
                        val len = sqrt(dx * dx + dy * dy)
                        val ex = if (len > 0f) p.x + dx / len * hullPaddingPx else p.x
                        val ey = if (len > 0f) p.y + dy / len * hullPaddingPx else p.y
                        if (index == 0) path.moveTo(ex, ey) else path.lineTo(ex, ey)
                    }
                    path.close()
                    HullPathData(communityId, FriendNetworkScreenModel.colorOfCommunity(communityId), path)
                }
        }
        val viewCenter = Offset(viewWidthPx / 2f, viewHeightPx / 2f)
        val layoutCenter = Offset(layoutWidthPx / 2f, layoutHeightPx / 2f)
        val centeredOffset = viewCenter - (layoutCenter * initialScale)
        val defaultColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
        val highlightColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
        val crossEdgeColor = MaterialTheme.colorScheme.outline

        val currentOnNodeTap by rememberUpdatedState(onNodeTap)
        val currentOnNodeLongPress by rememberUpdatedState(onNodeLongPress)
        val currentOnBackgroundTap by rememberUpdatedState(onBackgroundTap)

        // 头像半径（布局坐标系），命中测试用
        val nodeRadius = remember(nodes, nodeDegree, maxDegree) {
            nodes.associate { node ->
                val degree = nodeDegree[node.id] ?: 0
                val sizeRatio = if (maxDegree > 0) degree.toFloat() / maxDegree else 0f
                node.id to (baseNodeSizePx + maxExtraSizePx * sizeRatio) / 2f
            }
        }
        val minTouchRadiusPx = with(density) { 24.dp.toPx() }
        // 统一在父层做命中测试：节点自身不挂手势，避免消费事件吞掉缩放
        val hitTest by rememberUpdatedState<(Offset) -> String?> { viewPos ->
            val currentScale = if (hasUserInteracted) scale else initialScale
            val currentOffset = if (hasUserInteracted) offset else centeredOffset
            val layoutPoint = (viewPos - currentOffset) / currentScale
            var best: String? = null
            var bestDist = Float.MAX_VALUE
            nodes.forEach { node ->
                val pos = positions[node.id] ?: return@forEach
                // 缩得很小时保证屏幕上仍有约 24dp 的命中半径
                val radius = maxOf(nodeRadius[node.id] ?: 0f, minTouchRadiusPx / currentScale)
                val dx = layoutPoint.x - pos.x
                val dy = layoutPoint.y - pos.y
                val dist = sqrt(dx * dx + dy * dy)
                if (dist <= radius && dist < bestDist) {
                    bestDist = dist
                    best = node.id
                }
            }
            best
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(nodes.size, initialScale, viewWidthPx, viewHeightPx) {
                    detectTapGestures(
                        onTap = { pos ->
                            val nodeId = hitTest(pos)
                            if (nodeId != null) currentOnNodeTap(nodeId) else currentOnBackgroundTap()
                        },
                        onLongPress = { pos ->
                            hitTest(pos)?.let { currentOnNodeLongPress(it) }
                        },
                        onDoubleTap = { pos ->
                            // 双击节点视作点击打开详情，双击空白复位视图
                            val nodeId = hitTest(pos)
                            if (nodeId != null) {
                                currentOnNodeTap(nodeId)
                            } else {
                                scale = initialScale
                                offset = centeredOffset
                                hasUserInteracted = false
                            }
                        }
                    )
                }
                .pointerInput(nodes.size, initialScale, viewWidthPx, viewHeightPx) {
                    detectTransformGestures { centroid, pan, zoom, _ ->
                        val currentScale = if (hasUserInteracted) scale else initialScale
                        val currentOffset = if (hasUserInteracted) offset else centeredOffset
                        if (!hasUserInteracted && (pan != Offset.Zero || zoom != 1f)) {
                            hasUserInteracted = true
                        }
                        val newScale = (currentScale * zoom).coerceIn(minScale, maxScale)
                        val layoutPoint = (centroid - currentOffset) / currentScale
                        val nextOffset = centroid - (layoutPoint * newScale)
                        offset = nextOffset + pan
                        scale = newScale
                    }
                }
        ) {
            Box(
                modifier = Modifier
                    // 在 graphicsLayer 内读取状态：平移/缩放只更新图层属性，不触发重组
                    .graphicsLayer {
                        transformOrigin = TransformOrigin(0f, 0f)
                        val renderScale = if (hasUserInteracted) scale else initialScale
                        val renderOffset = if (hasUserInteracted) offset else centeredOffset
                        translationX = renderOffset.x
                        translationY = renderOffset.y
                        scaleX = renderScale
                        scaleY = renderScale
                    }
                    .width(with(density) { layoutWidthPx.toDp() })
                    .height(with(density) { layoutHeightPx.toDp() })
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    // draw 阶段读取高亮状态：高亮变化只触发重绘
                    val currentHighlightId = highlightIdState.value
                    val selectedCommunity = selectedCommunityState.value
                    val highlightActive = currentHighlightId != null || selectedCommunity != null
                    // 当前聚焦的社区（个人高亮取其所在社区），用于气泡压暗
                    val activeCommunity = selectedCommunity ?: currentHighlightId?.let { communities[it] }

                    // 1. 社区气泡垫底
                    hullPaths.forEach { hull ->
                        val focused = activeCommunity != null && hull.communityId == activeCommunity
                        val dimmed = activeCommunity != null && hull.communityId != activeCommunity
                        val fillAlpha = when {
                            dimmed -> 0.03f
                            focused -> 0.12f
                            else -> 0.08f
                        }
                        val strokeAlpha = when {
                            dimmed -> 0.04f
                            focused -> 0.14f
                            else -> 0.10f
                        }
                        drawPath(hull.path, hull.color.copy(alpha = fillAlpha))
                        drawPath(
                            path = hull.path,
                            color = hull.color.copy(alpha = strokeAlpha),
                            style = Stroke(width = hullStrokeWidthPx, join = StrokeJoin.Round)
                        )
                    }

                    // 2. 普通边：圈内用社区色，跨圈用中性灰细线
                    edgePaths.forEach { edge ->
                        val isPersonHighlight = currentHighlightId != null &&
                            (edge.from == currentHighlightId || edge.to == currentHighlightId)
                        val isCommunityHighlight = selectedCommunity != null && edge.communityId == selectedCommunity
                        if (isPersonHighlight || isCommunityHighlight) return@forEach
                        if (edge.communityId != null) {
                            val color = nodeColors[edge.from] ?: defaultColor
                            drawPath(
                                path = edge.path,
                                color = color.copy(alpha = if (highlightActive) 0.08f else 0.55f),
                                style = Stroke(width = 2.5f)
                            )
                        } else {
                            drawPath(
                                path = edge.path,
                                color = crossEdgeColor.copy(alpha = if (highlightActive) 0.05f else 0.18f),
                                style = Stroke(width = 1.5f)
                            )
                        }
                    }

                    // 3. 高亮边最后画，盖在普通边上面
                    if (currentHighlightId != null) {
                        edgePaths.forEach { edge ->
                            if (edge.from == currentHighlightId || edge.to == currentHighlightId) {
                                drawPath(
                                    path = edge.path,
                                    color = highlightColor,
                                    style = Stroke(width = 4f)
                                )
                            }
                        }
                    }
                    if (selectedCommunity != null) {
                        edgePaths.forEach { edge ->
                            if (edge.communityId == selectedCommunity) {
                                val color = nodeColors[edge.from] ?: defaultColor
                                drawPath(
                                    path = edge.path,
                                    color = color.copy(alpha = 0.9f),
                                    style = Stroke(width = 3.5f)
                                )
                            }
                        }
                    }
                }

                nodes.forEach { node ->
                    val pos = positions[node.id] ?: return@forEach
                    key(node.id) {
                        // 根据连接数计算头像大小
                        val degree = nodeDegree[node.id] ?: 0
                        val sizeRatio = if (maxDegree > 0) degree.toFloat() / maxDegree else 0f
                        val nodeSizeDp = with(density) { (baseNodeSizePx + maxExtraSizePx * sizeRatio).toDp() }
                        val nodeSizePxLocal = baseNodeSizePx + maxExtraSizePx * sizeRatio
                        val nodeOffset = IntOffset(
                            x = (pos.x - labelWidthPx / 2).roundToInt(),
                            y = (pos.y - nodeSizePxLocal / 2).roundToInt()
                        )
                        Box(
                            modifier = Modifier
                                .offset { nodeOffset }
                                .width(labelWidth)
                                .graphicsLayer {
                                    val highlightIds = highlightIdsState.value
                                    alpha = if (highlightIds.isEmpty() || node.id in highlightIds) 1f else 0.15f
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            FriendNetworkNode(
                                node = node,
                                size = nodeSizeDp,
                                selectedIdState = selectedIdState,
                                communityColor = nodeColors[node.id],
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FriendNetworkNode(
    node: MutualFriendData,
    size: androidx.compose.ui.unit.Dp,
    selectedIdState: State<String?>,
    communityColor: Color? = null,
) {
    val isSelected = selectedIdState.value == node.id
    val borderColor = when {
        isSelected -> MaterialTheme.colorScheme.primary
        communityColor != null -> communityColor
        else -> Color.Transparent
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(size)
                .border(
                    width = if (communityColor != null || isSelected) 3.dp else 2.dp,
                    color = borderColor,
                    shape = CircleShape
                )
                .background(MaterialTheme.colorScheme.surface, CircleShape)
        ) {
            UserStateIcon(
                modifier = Modifier.fillMaxSize(),
                iconUrl = node.iconUrl,
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        if (node.displayName.isNotBlank()) {
            Text(
                text = node.displayName,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun FriendNetworkSheet(
    node: MutualFriendData,
    mutualUsers: List<MutualFriendData>,
    onUserClick: (MutualFriendData) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onUserClick(node) }
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            UserStateIcon(
                modifier = Modifier.size(48.dp),
                iconUrl = node.iconUrl,
            )
            Spacer(modifier = Modifier.width(12.dp))
            val displayName = node.displayName.ifBlank { strings.users }
            Text(
                text = displayName,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        HorizontalDivider()
        Spacer(modifier = Modifier.height(8.dp))
        val visibleMutualUsers = mutualUsers.filter { it.id != HIDDEN_MUTUAL_USER_ID }
        val hiddenCount = mutualUsers.size - visibleMutualUsers.size
        val titleText = if (hiddenCount > 0) {
            strings.mutualFriendsCountWithHidden
                .replace("%total%", mutualUsers.size.toString())
                .replace("%hidden%", hiddenCount.toString())
        } else {
            strings.mutualFriendsCount.replace("%total%", mutualUsers.size.toString())
        }
        Text(
            text = titleText,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(8.dp))
        if (mutualUsers.isEmpty()) {
            Text(
                text = strings.mutualFriendsEmpty.replace("%s", node.displayName),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline
            )
        } else {
            LazyColumn(modifier = Modifier.fillMaxWidth().height(280.dp)) {
                itemsIndexed(
                    items = visibleMutualUsers,
                    key = { index, user -> "${user.id}#$index" }
                ) { _, user ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp)
                            .clickable { onUserClick(user) },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        UserStateIcon(
                            modifier = Modifier.size(32.dp),
                            iconUrl = user.iconUrl,
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = user.displayName,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
    }
}

private const val HIDDEN_MUTUAL_USER_ID = "usr_00000000-0000-0000-0000-000000000000"

private fun buildEdgeList(edges: Map<String, List<String>>): List<Pair<String, String>> {
    val seen = HashSet<String>()
    val list = mutableListOf<Pair<String, String>>()
    edges.forEach { (from, tos) ->
        tos.forEach { to ->
            if (from == to) return@forEach
            val key = if (from < to) "$from|$to" else "$to|$from"
            if (seen.add(key)) {
                list.add(from to to)
            }
        }
    }
    return list
}

@OptIn(ExperimentalTime::class)
private fun formatTimestamp(epochMillis: Long): String {
    val local = Instant.fromEpochMilliseconds(epochMillis).toLocalDateTime(TimeZone.currentSystemDefault())
    val hour = local.hour.toString().padStart(2, '0')
    val minute = local.minute.toString().padStart(2, '0')
    return "${local.date} $hour:$minute"
}
