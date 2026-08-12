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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import io.github.vrcmteam.vrcm.presentation.navigation.AppRoute
import io.github.vrcmteam.vrcm.core.extensions.saveImageBytesToGallery
import io.github.vrcmteam.vrcm.core.shared.SharedFlowCentre
import io.github.vrcmteam.vrcm.getAppPlatform
import org.koin.compose.viewmodel.koinViewModel
import io.github.vrcmteam.vrcm.presentation.navigation.LocalNavigator
import io.github.vrcmteam.vrcm.presentation.navigation.currentOrThrow
import io.github.vrcmteam.vrcm.core.algorithms.ForceLayoutResult
import io.github.vrcmteam.vrcm.core.algorithms.convexHull
import io.github.vrcmteam.vrcm.network.api.users.data.MutualFriendData
import io.github.vrcmteam.vrcm.presentation.compoments.ABottomSheet
import io.github.vrcmteam.vrcm.presentation.compoments.ToastText
import io.github.vrcmteam.vrcm.presentation.compoments.UserStateIcon
import io.github.vrcmteam.vrcm.presentation.screens.gallery.editor.PlatformImageCodec
import io.github.vrcmteam.vrcm.presentation.screens.user.data.UserProfileVo
import io.github.vrcmteam.vrcm.presentation.settings.locale.strings
import io.github.vrcmteam.vrcm.presentation.supports.AppIcons
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import org.koin.compose.koinInject
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlin.time.ExperimentalTime

@Serializable
object FriendNetworkScreen : AppRoute {

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val model: FriendNetworkScreenModel = koinViewModel()
        val state = model.uiState
        val selectedIdState = remember { mutableStateOf<String?>(null) }
        val highlightIdState = remember { mutableStateOf<String?>(null) }
        // 图例选中的社区，与个人长按高亮互斥
        val selectedCommunityState = remember { mutableStateOf<Int?>(null) }
        var showSheet by remember { mutableStateOf(false) }
        var savingScreenshot by remember { mutableStateOf(false) }
        val sheetState = rememberModalBottomSheetState()
        val scope = rememberCoroutineScope()
        val platform = getAppPlatform()
        val imageCodec = koinInject<PlatformImageCodec>()
        val graphLayer = rememberGraphicsLayer()
        val saveSuccessMessage = strings.imageSaveSuccess
        val saveFailedMessage = strings.imageSaveFailed
        val saveErrorTemplate = strings.imageSaveError

        val density = LocalDensity.current
        // 最大头像尺寸（基础 40dp + 度数加成 44dp），布局间距按此计算
        val nodeSizePx = with(density) { (40.dp + 44.dp).toPx() }
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
                            enabled = state.nodes.isNotEmpty() &&
                                !state.isLoading &&
                                !state.isPreparing &&
                                !savingScreenshot,
                            onClick = {
                                if (savingScreenshot) return@IconButton
                                savingScreenshot = true
                                scope.launch {
                                    runCatching {
                                        val bytes = imageCodec.encodePng(graphLayer.toImageBitmap())
                                        platform.saveImageBytesToGallery(
                                            bytes = bytes,
                                            fileName = friendNetworkScreenshotFileName(),
                                        )
                                    }.onSuccess { saved ->
                                        SharedFlowCentre.toastText.emit(
                                            if (saved) ToastText.Success(saveSuccessMessage)
                                            else ToastText.Error(saveFailedMessage),
                                        )
                                    }.onFailure { error ->
                                        SharedFlowCentre.toastText.emit(
                                            ToastText.Error(
                                                saveErrorTemplate.replace("%s", error.message.orEmpty()),
                                            ),
                                        )
                                    }
                                    savingScreenshot = false
                                }
                            },
                        ) {
                            if (savingScreenshot) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(
                                    painter = rememberVectorPainter(AppIcons.SaveAlt),
                                    tint = MaterialTheme.colorScheme.primary,
                                    contentDescription = strings.friendNetworkSaveScreenshot,
                                )
                            }
                        }
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
                // 自我中心视图：共同好友数 Top10 排行（替代社区图例的位置）
                val mutualTopFriends = remember(state.nodes, state.edges) {
                    state.nodes
                        .map { node -> node to state.edges[node.id].orEmpty().size }
                        .sortedWith(
                            compareByDescending<Pair<MutualFriendData, Int>> { it.second }
                                .thenBy { it.first.displayName }
                        )
                        .take(10)
                }
                if (state.nodes.isNotEmpty()) {
                    FriendNetworkControlRow(
                        viewMode = state.viewMode,
                        // 自我中心视图里没有社区概念，不展示社区图例
                        legend = if (state.viewMode == FriendNetworkViewMode.Community) state.communityLegend
                        else emptyList(),
                        selectedCommunity = selectedCommunityState.value,
                        egoTopFriends = if (state.viewMode == FriendNetworkViewMode.Ego) mutualTopFriends
                        else emptyList(),
                        highlightedId = highlightIdState.value,
                        onViewModeChange = { mode ->
                            model.setViewMode(mode)
                            selectedCommunityState.value = null
                            highlightIdState.value = null
                        },
                        onCommunityClick = { communityId ->
                            selectedCommunityState.value =
                                if (selectedCommunityState.value == communityId) null else communityId
                            highlightIdState.value = null
                        },
                        onTopFriendClick = { friendId ->
                            highlightIdState.value =
                                if (highlightIdState.value == friendId) null else friendId
                            selectedCommunityState.value = null
                        }
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        // Capture only the visible graph viewport. The toolbar, status text and
                        // controls stay out of the saved screenshot.
                        .drawWithContent {
                            graphLayer.record { this@drawWithContent.drawContent() }
                            drawLayer(graphLayer)
                        },
                ) {
                    val isEgoView = state.viewMode == FriendNetworkViewMode.Ego
                    val layout = if (isEgoView) state.egoLayout else state.layout
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
                            isEgoView = isEgoView,
                            selfNode = state.selfNode,
                            selfId = state.selfId,
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

@OptIn(ExperimentalTime::class)
private fun friendNetworkScreenshotFileName(): String =
    "VRCM_FriendNetwork_${kotlin.time.Clock.System.now().toEpochMilliseconds()}.png"

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
private fun FriendNetworkControlRow(
    viewMode: FriendNetworkViewMode,
    legend: List<CommunitySummary>,
    selectedCommunity: Int?,
    egoTopFriends: List<Pair<MutualFriendData, Int>>,
    highlightedId: String?,
    onViewModeChange: (FriendNetworkViewMode) -> Unit,
    onCommunityClick: (Int) -> Unit,
    onTopFriendClick: (String) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 视图切换：社区 / 以我为中心
        Row(
            modifier = Modifier
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                .padding(2.dp)
        ) {
            ViewModeChip(
                text = strings.friendNetworkViewCommunity,
                isSelected = viewMode == FriendNetworkViewMode.Community,
                onClick = { onViewModeChange(FriendNetworkViewMode.Community) }
            )
            ViewModeChip(
                text = strings.friendNetworkViewEgo,
                isSelected = viewMode == FriendNetworkViewMode.Ego,
                onClick = { onViewModeChange(FriendNetworkViewMode.Ego) }
            )
        }
        if (legend.isNotEmpty() || egoTopFriends.isNotEmpty()) {
            Spacer(modifier = Modifier.width(8.dp))
            Row(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState()),
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
                // 共同好友数 Top10：点击进入该好友的高亮状态
                egoTopFriends.forEachIndexed { index, (friend, count) ->
                    val isSelected = highlightedId == friend.id
                    val primary = MaterialTheme.colorScheme.primary
                    Row(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(
                                if (isSelected) primary.copy(alpha = 0.14f)
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            )
                            .border(
                                width = 1.dp,
                                color = if (isSelected) primary else Color.Transparent,
                                shape = CircleShape
                            )
                            .clickable { onTopFriendClick(friend.id) }
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${index + 1}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (isSelected) primary else MaterialTheme.colorScheme.outline
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "${friend.displayName} · $count",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (isSelected) MaterialTheme.colorScheme.onSurface
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ViewModeChip(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Text(
        text = text,
        modifier = Modifier
            .clip(CircleShape)
            .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 3.dp),
        style = MaterialTheme.typography.labelMedium,
        color = if (isSelected) MaterialTheme.colorScheme.onPrimary
        else MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1
    )
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
    isEgoView: Boolean,
    selfNode: MutualFriendData?,
    selfId: String?,
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
        val baseNodeSize = 40.dp
        val maxExtraSize = 44.dp
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
        // rememberSaveable：切后台进程重建后还原视口；key 与视口尺寸解耦，
        // 折叠/分屏等尺寸变化不再把状态清零（切换视图模式仍复位）
        var scale by rememberSaveable(nodes.size, isEgoView) { mutableStateOf(initialScale) }
        var offsetX by rememberSaveable(nodes.size, isEgoView) { mutableStateOf(0f) }
        var offsetY by rememberSaveable(nodes.size, isEgoView) { mutableStateOf(0f) }
        var hasUserInteracted by rememberSaveable(nodes.size, isEgoView) { mutableStateOf(false) }
        // 视口尺寸变化（折叠态切换/分屏/重建）：平移做中心补偿，画面中心点保持不变
        var lastViewWidth by rememberSaveable(nodes.size, isEgoView) { mutableStateOf(viewWidthPx) }
        var lastViewHeight by rememberSaveable(nodes.size, isEgoView) { mutableStateOf(viewHeightPx) }
        if (lastViewWidth != viewWidthPx || lastViewHeight != viewHeightPx) {
            offsetX += (viewWidthPx - lastViewWidth) / 2f
            offsetY += (viewHeightPx - lastViewHeight) / 2f
            lastViewWidth = viewWidthPx
            lastViewHeight = viewHeightPx
        }
        val edgeList = remember(edges) { buildEdgeList(edges) }
        // 头像大小比例：社区视图=圈内度数（圈子核心大），自我视图=共同好友数
        // 线性映射，让核心与边缘的大小差距一眼可辨
        val nodeSizeRatio = remember(nodes, edges, communities, isEgoView) {
            if (isEgoView) {
                val degree = nodes.associate { it.id to edges[it.id].orEmpty().size }
                val maxDegree = (degree.values.maxOrNull() ?: 0).coerceAtLeast(1)
                degree.mapValues { (_, d) -> d.toFloat() / maxDegree }
            } else {
                // 圈内度数按「本社区最大值」归一化：每个圈子的核心都拿到本圈最大尺寸；
                // 再按社区规模(sqrt)设上限：小圈核心醒目但不越级到全图最大
                val internalDegree = nodes.associate { node ->
                    val community = communities[node.id]
                    node.id to if (community != null && community >= 0) {
                        edges[node.id].orEmpty().count { communities[it] == community }
                    } else 0
                }
                val communitySize = mutableMapOf<Int, Int>()
                val maxInternalByCommunity = mutableMapOf<Int, Int>()
                nodes.forEach { node ->
                    val community = communities[node.id] ?: return@forEach
                    if (community >= 0) {
                        communitySize[community] = (communitySize[community] ?: 0) + 1
                        val degree = internalDegree[node.id] ?: 0
                        if (degree > (maxInternalByCommunity[community] ?: 0)) {
                            maxInternalByCommunity[community] = degree
                        }
                    }
                }
                val maxCommunitySize = (communitySize.values.maxOrNull() ?: 1).coerceAtLeast(1)
                nodes.associate { node ->
                    val community = communities[node.id]
                    val ratio = if (community != null && community >= 0) {
                        val localMax = (maxInternalByCommunity[community] ?: 0).coerceAtLeast(1)
                        val localRatio = (internalDegree[node.id] ?: 0).toFloat() / localMax
                        val sizeCap = 0.6f + 0.4f * sqrt(
                            (communitySize[community] ?: 1).toFloat() / maxCommunitySize
                        )
                        localRatio * sizeCap
                    } else 0f
                    node.id to ratio
                }
            }
        }
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
        val hullPaths = remember(layout, communities, isEgoView) {
            if (isEgoView) return@remember emptyList()
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
        val textMeasurer = rememberTextMeasurer()
        val ringLabelStyle = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.outline)
        val primaryColor = MaterialTheme.colorScheme.primary

        // 自我视图 Top10 辐射线数据：(好友 ID, 共同好友数比例)
        val egoTopSpokes = remember(nodes, edges, isEgoView) {
            if (!isEgoView) return@remember emptyList()
            val ranked = nodes
                .map { node -> node to edges[node.id].orEmpty().size }
                .sortedWith(
                    compareByDescending<Pair<MutualFriendData, Int>> { it.second }
                        .thenBy { it.first.displayName }
                )
                .take(10)
            val maxDegree = (ranked.firstOrNull()?.second ?: 0).coerceAtLeast(1)
            ranked.map { (node, degree) -> node.id to degree.toFloat() / maxDegree }
        }

        val currentOnNodeTap by rememberUpdatedState(onNodeTap)
        val currentOnNodeLongPress by rememberUpdatedState(onNodeLongPress)
        val currentOnBackgroundTap by rememberUpdatedState(onBackgroundTap)

        // 头像半径（布局坐标系），命中测试用
        val nodeRadius = remember(nodes, nodeSizeRatio) {
            nodes.associate { node ->
                val sizeRatio = nodeSizeRatio[node.id] ?: 0f
                node.id to (baseNodeSizePx + maxExtraSizePx * sizeRatio) / 2f
            }
        }
        val minTouchRadiusPx = with(density) { 24.dp.toPx() }
        // 统一在父层做命中测试：节点自身不挂手势，避免消费事件吞掉缩放
        val hitTest by rememberUpdatedState<(Offset) -> String?> { viewPos ->
            val currentScale = if (hasUserInteracted) scale else initialScale
            val currentOffset = if (hasUserInteracted) Offset(offsetX, offsetY) else centeredOffset
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
                .pointerInput(nodes.size, initialScale, viewWidthPx, viewHeightPx, layout) {
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
                                offsetX = centeredOffset.x
                                offsetY = centeredOffset.y
                                hasUserInteracted = false
                            }
                        }
                    )
                }
                .pointerInput(nodes.size, initialScale, viewWidthPx, viewHeightPx, layout) {
                    detectTransformGestures { centroid, pan, zoom, _ ->
                        val currentScale = if (hasUserInteracted) scale else initialScale
                        val currentOffset = if (hasUserInteracted) Offset(offsetX, offsetY) else centeredOffset
                        if (!hasUserInteracted && (pan != Offset.Zero || zoom != 1f)) {
                            hasUserInteracted = true
                        }
                        val newScale = (currentScale * zoom).coerceIn(minScale, maxScale)
                        val layoutPoint = (centroid - currentOffset) / currentScale
                        val nextOffset = centroid - (layoutPoint * newScale) + pan
                        offsetX = nextOffset.x
                        offsetY = nextOffset.y
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
                        val renderOffset = if (hasUserInteracted) Offset(offsetX, offsetY) else centeredOffset
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

                    // 0. 自我视图：圈层色带 + Top10 辐射线 + 距离参考环
                    if (isEgoView && selfId != null) {
                        val center = positions[selfId]
                        if (center != null) {
                            // ① 亲密圈层色带：参考环之间交替填充极淡主题色
                            val sortedRings = layout.guideRings.sortedBy { it.radiusPx }
                            val boundaries = buildList {
                                add(0f)
                                sortedRings.forEach { add(it.radiusPx) }
                            }
                            val bandColor = primaryColor.copy(alpha = 0.045f)
                            for (band in 0 until boundaries.size - 1 step 2) {
                                val outer = boundaries[band + 1]
                                val inner = boundaries[band]
                                if (inner <= 0f) {
                                    drawCircle(color = bandColor, radius = outer, center = center)
                                } else {
                                    val annulus = Path().apply {
                                        fillType = PathFillType.EvenOdd
                                        addOval(Rect(center.x - outer, center.y - outer, center.x + outer, center.y + outer))
                                        addOval(Rect(center.x - inner, center.y - inner, center.x + inner, center.y + inner))
                                    }
                                    drawPath(annulus, bandColor)
                                }
                            }

                            // ③ Top10 辐射线：粗细 ∝ 共同好友数，与排行 chips 联动加亮
                            egoTopSpokes.forEach { (friendId, ratio) ->
                                val target = positions[friendId] ?: return@forEach
                                val focused = currentHighlightId == friendId
                                drawLine(
                                    color = primaryColor.copy(alpha = if (focused) 0.5f else 0.14f),
                                    start = center,
                                    end = target,
                                    strokeWidth = (2f + 6f * ratio) * (if (focused) 1.4f else 1f),
                                    cap = StrokeCap.Round
                                )
                            }

                            // 距离参考环与刻度
                            sortedRings.forEach { ring ->
                                drawCircle(
                                    color = crossEdgeColor.copy(alpha = 0.25f),
                                    radius = ring.radiusPx,
                                    center = center,
                                    style = Stroke(
                                        width = 2f,
                                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 14f))
                                    )
                                )
                                val label = textMeasurer.measure("≥ ${ring.mutualCount}", ringLabelStyle)
                                drawText(
                                    textLayoutResult = label,
                                    topLeft = Offset(
                                        center.x - label.size.width / 2f,
                                        center.y - ring.radiusPx - label.size.height - 4f
                                    )
                                )
                            }
                        }
                    }

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

                    // 2. 普通边：圈内用社区色，跨圈用中性灰细线（自我视图不画，避免遮住距离读数）
                    if (!isEgoView) {
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
                        val sizeRatio = nodeSizeRatio[node.id] ?: 0f
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
                                // ② 自我中心视图：圆环按共同好友数走主题色渐变（近浓远淡），无社区色
                                communityColor = if (isEgoView) {
                                    primaryColor.copy(alpha = 0.25f + 0.75f * sizeRatio)
                                } else {
                                    nodeColors[node.id]
                                },
                            )
                        }
                    }
                }

                // 自我视图：自己固定在圆心
                if (isEgoView && selfNode != null && selfId != null) {
                    val selfPos = positions[selfId]
                    if (selfPos != null) {
                        val selfSizePx = baseNodeSizePx + maxExtraSizePx
                        val selfOffset = IntOffset(
                            x = (selfPos.x - labelWidthPx / 2).roundToInt(),
                            y = (selfPos.y - selfSizePx / 2).roundToInt()
                        )
                        Box(
                            modifier = Modifier
                                .offset { selfOffset }
                                .width(labelWidth),
                            contentAlignment = Alignment.Center
                        ) {
                            FriendNetworkNode(
                                node = selfNode,
                                size = with(density) { selfSizePx.toDp() },
                                selectedIdState = selectedIdState,
                                communityColor = MaterialTheme.colorScheme.primary,
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
