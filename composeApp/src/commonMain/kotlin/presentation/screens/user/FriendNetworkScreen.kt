package io.github.vrcmteam.vrcm.presentation.screens.user

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.input.pointer.pointerInput
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
import io.github.vrcmteam.vrcm.network.api.users.data.MutualFriendData
import io.github.vrcmteam.vrcm.presentation.compoments.ABottomSheet
import io.github.vrcmteam.vrcm.presentation.compoments.UserStateIcon
import io.github.vrcmteam.vrcm.presentation.screens.user.data.UserProfileVo
import io.github.vrcmteam.vrcm.presentation.settings.locale.strings
import io.github.vrcmteam.vrcm.presentation.supports.AppIcons
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.ExperimentalTime
import kotlin.math.roundToInt

object FriendNetworkScreen : Screen {

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val model: FriendNetworkScreenModel = koinScreenModel()
        val state = model.uiState
        var selectedId by remember { mutableStateOf<String?>(null) }
        var highlightId by remember { mutableStateOf<String?>(null) }
        var showSheet by remember { mutableStateOf(false) }
        val sheetState = rememberModalBottomSheetState()

        val density = LocalDensity.current
        val nodeSizePx = with(density) { (43.dp + 34.dp).toPx() }
        LaunchedEffect(Unit) {
            model.loadCache(nodeSizePx)
        }

        val nodeMap = remember(state.nodes) { state.nodes.associateBy { it.id } }
        val selectedNode = selectedId?.let { nodeMap[it] }
        val mutualIds = selectedId?.let { state.edges[it].orEmpty() }.orEmpty()
        val highlightIds = highlightId?.let { id ->
            setOf(id) + state.edges[id].orEmpty()
        }.orEmpty()

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
                Box(modifier = Modifier.fillMaxSize()) {
                    if (state.nodes.isEmpty() && !state.isLoading) {
                        Text(
                            modifier = Modifier.align(Alignment.Center),
                            text = strings.friendNetworkEmpty,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.outline
                        )
                    } else if (state.layout != null) {
                        val layout = state.layout
                        FriendNetworkGraph(
                            nodes = state.nodes,
                            edges = state.edges,
                            nodeColors = state.nodeColors,
                            layout = layout,
                            highlightIds = highlightIds,
                            selectedId = selectedId,
                            highlightId = highlightId,
                            onHighlightChange = { highlightId = it },
                            onNodeClick = { nodeId ->
                                selectedId = nodeId
                                showSheet = true
                            }
                        )
                    }
                    if (state.isLoading && state.nodes.isEmpty()) {
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
                selectedId = null
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
                    selectedId = null
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
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        updatedAt?.let {
            Text(
                text = strings.friendNetworkLastUpdated.replace("%s", formatTimestamp(it)),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.outline
            )
        }
        if (isFromCache) {
            Text(
                text = strings.friendNetworkCacheHint,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.outline
            )
        }
        if (isLoading) {
            val progressText = progress?.let { "${it.current}/${it.total}" }.orEmpty()
            Text(
                text = strings.friendNetworkBuilding.replace("%s", progressText),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
}

@Composable
private fun FriendNetworkGraph(
    nodes: List<MutualFriendData>,
    edges: Map<String, List<String>>,
    nodeColors: Map<String, Color>,
    layout: ForceLayoutResult,
    highlightIds: Set<String>,
    selectedId: String?,
    highlightId: String?,
    onHighlightChange: (String?) -> Unit,
    onNodeClick: (String) -> Unit,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
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
        val minScale = 0.35f
        val maxScale = 3.5f
        val initialScale = maxOf(
            viewWidthPx / layoutWidthPx,
            viewHeightPx / layoutHeightPx
        ).coerceIn(minScale, 1.25f)
        var scale by remember(nodes.size, viewWidthPx, viewHeightPx) { mutableStateOf(initialScale) }
        var offset by remember(nodes.size, viewWidthPx, viewHeightPx) { mutableStateOf(Offset.Zero) }
        var hasUserInteracted by remember(nodes.size) { mutableStateOf(false) }
        val edgeList = remember(edges) { buildEdgeList(edges) }
        // 计算每个节点的度数（连接数）
        val nodeDegree = remember(edges) { nodes.associate { it.id to edges[it.id].orEmpty().size } }
        val maxDegree = remember(nodeDegree) { nodeDegree.values.maxOrNull() ?: 1 }
        val positions = layout.positions
        val viewCenter = Offset(viewWidthPx / 2f, viewHeightPx / 2f)
        val layoutCenter = Offset(layoutWidthPx / 2f, layoutHeightPx / 2f)
        val centeredOffset = viewCenter - (layoutCenter * initialScale)
        val renderScale = if (hasUserInteracted) scale else initialScale
        val renderOffset = if (hasUserInteracted) offset else centeredOffset
        val defaultColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
        val highlightColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(nodes.size, initialScale) {
                    detectTapGestures(
                        onDoubleTap = {
                            scale = initialScale
                            offset = centeredOffset
                            hasUserInteracted = false
                        }
                    )
                }
                .pointerInput(nodes.size) {
                    detectTransformGestures { centroid, pan, zoom, _ ->
                        val currentScale = if (hasUserInteracted) scale else initialScale
                        val currentOffset = if (hasUserInteracted) offset else centeredOffset
                        if (!hasUserInteracted && (pan != Offset.Zero || zoom != 1f)) {
                            hasUserInteracted = true
                            scale = currentScale
                            offset = currentOffset
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
                    .graphicsLayer {
                        translationX = renderOffset.x
                        translationY = renderOffset.y
                        scaleX = renderScale
                        scaleY = renderScale
                    }
                    .width(with(density) { layoutWidthPx.toDp() })
                    .height(with(density) { layoutHeightPx.toDp() })
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    edgeList.forEach { (from, to) ->
                        val fromPos = positions[from] ?: return@forEach
                        val toPos = positions[to] ?: return@forEach
                        val isHighlighted = highlightId != null &&
                            ((from == highlightId && highlightIds.contains(to)) ||
                                (to == highlightId && highlightIds.contains(from)))
                        // 使用社区颜色作为边的颜色
                        val communityColor = nodeColors[from]?.copy(alpha = 0.4f) ?: defaultColor
                        // 计算弧线的控制点：在中点垂直方向偏移
                        val midX = (fromPos.x + toPos.x) / 2f
                        val midY = (fromPos.y + toPos.y) / 2f
                        val dx = toPos.x - fromPos.x
                        val dy = toPos.y - fromPos.y
                        val len = kotlin.math.sqrt(dx * dx + dy * dy)
                        // 垂直方向偏移量，弧度约为线长的 15%
                        val curvature = len * 0.15f
                        val nx = if (len > 0f) -dy / len else 0f
                        val ny = if (len > 0f) dx / len else 0f
                        val ctrlX = midX + nx * curvature
                        val ctrlY = midY + ny * curvature
                        val path = Path().apply {
                            moveTo(fromPos.x, fromPos.y)
                            quadraticBezierTo(ctrlX, ctrlY, toPos.x, toPos.y)
                        }
                        drawPath(
                            path = path,
                            color = if (isHighlighted) highlightColor else communityColor,
                            style = Stroke(width = if (isHighlighted) 4f else 2.5f)
                        )
                    }
                }

                nodes.forEach { node ->
                    val pos = positions[node.id] ?: return@forEach
                    // 根据连接数计算头像大小
                    val degree = nodeDegree[node.id] ?: 0
                    val sizeRatio = if (maxDegree > 0) degree.toFloat() / maxDegree else 0f
                    val nodeSizeDp = with(density) { (baseNodeSizePx + maxExtraSizePx * sizeRatio).toDp() }
                    val nodeSizePxLocal = baseNodeSizePx + maxExtraSizePx * sizeRatio
                    val offset = IntOffset(
                        x = (pos.x - labelWidthPx / 2).roundToInt(),
                        y = (pos.y - nodeSizePxLocal / 2).roundToInt()
                    )
                    val isHighlighted = highlightIds.isEmpty() || highlightIds.contains(node.id)
                    val alpha = if (isHighlighted) 1f else 0.35f
                    Box(
                        modifier = Modifier
                            .offset { offset }
                            .width(labelWidth)
                            .alpha(alpha)
                            .pointerInput(node.id, highlightId) {
                                detectTapGestures(
                                    onTap = { onNodeClick(node.id) },
                                    onLongPress = { onHighlightChange(node.id) },
                                    onPress = {
                                        tryAwaitRelease()
                                        if (highlightId == node.id) {
                                            onHighlightChange(null)
                                        }
                                    }
                                )
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        FriendNetworkNode(
                            node = node,
                            size = nodeSizeDp,
                            isSelected = node.id == selectedId,
                            communityColor = nodeColors[node.id],
                        )
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
    isSelected: Boolean,
    communityColor: Color? = null,
) {
    val borderColor = when {
        isSelected -> MaterialTheme.colorScheme.primary
        communityColor != null -> communityColor
        else -> Color.Transparent
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(size)
                .border(width = if (communityColor != null || isSelected) 3.dp else 2.dp, color = borderColor, shape = CircleShape)
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
