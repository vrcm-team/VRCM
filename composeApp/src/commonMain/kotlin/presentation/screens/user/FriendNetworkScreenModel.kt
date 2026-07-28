package io.github.vrcmteam.vrcm.presentation.screens.user

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import io.github.vrcmteam.vrcm.core.algorithms.ForceLayoutResult
import io.github.vrcmteam.vrcm.core.algorithms.computeEgoLayout
import io.github.vrcmteam.vrcm.core.algorithms.computeForceLayout
import io.github.vrcmteam.vrcm.core.algorithms.louvainDetect
import io.github.vrcmteam.vrcm.core.shared.SharedFlowCentre
import io.github.vrcmteam.vrcm.network.api.auth.data.CurrentUserData
import io.github.vrcmteam.vrcm.network.api.friends.date.FriendData
import io.github.vrcmteam.vrcm.network.api.users.UsersApi
import io.github.vrcmteam.vrcm.network.api.users.data.MutualFriendData
import io.github.vrcmteam.vrcm.presentation.compoments.ToastText
import io.github.vrcmteam.vrcm.service.AuthService
import io.github.vrcmteam.vrcm.service.FriendService
import io.github.vrcmteam.vrcm.storage.FriendNetworkCacheDao
import io.github.vrcmteam.vrcm.storage.data.FriendNetworkCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.core.logger.Logger
import kotlin.time.Clock.System.now
import kotlin.time.ExperimentalTime

data class FriendNetworkProgress(
    val current: Int,
    val total: Int,
)

enum class FriendNetworkViewMode {
    // 社区视图：圈子分离 + 气泡，头像大小 = 圈内度数
    Community,

    // 自我中心视图：自己居中，共同好友越多离得越近，头像大小 = 共同好友数
    Ego,
}

data class CommunitySummary(
    val id: Int,
    val name: String,
    val count: Int,
    val color: Color,
)

data class StraddlerLean(
    val communityId: Int,
    val edgeCount: Int,
)

/**
 * 骑墙者信息：某外圈边数 ≥ max(2, 1.5×圈内边数) 即为显著倾向。
 * [leans] 封顶 Top2，用于布局时的跨圈引力加成（把骑墙者拉到圈间走廊）；
 * 其余显著倾向的边数合计进 [remainderEdges]
 */
data class StraddlerInfo(
    val ownEdges: Int,
    val leans: List<StraddlerLean>,
    val remainderEdges: Int,
)

data class FriendNetworkUiState(
    val selfId: String? = null,
    val nodes: List<MutualFriendData> = emptyList(),
    val edges: Map<String, List<String>> = emptyMap(),
    val nodeColors: Map<String, Color> = emptyMap(),
    // 节点所属社区；小于 MIN_COMMUNITY_SIZE 人的社区与孤立节点归并为 OTHER_COMMUNITY_ID
    val communities: Map<String, Int> = emptyMap(),
    // 真实社区图例（按人数降序，以圈内度数最高成员命名）
    val communityLegend: List<CommunitySummary> = emptyList(),
    val viewMode: FriendNetworkViewMode = FriendNetworkViewMode.Community,
    // 自己的节点数据，自我中心视图的圆心
    val selfNode: MutualFriendData? = null,
    val layout: ForceLayoutResult? = null,
    val egoLayout: ForceLayoutResult? = null,
    val updatedAt: Long? = null,
    val isFromCache: Boolean = false,
    val isLoading: Boolean = false,
    // 正在读取缓存并计算布局（区别于 isLoading：不走网络、无进度）
    val isPreparing: Boolean = false,
    val progress: FriendNetworkProgress? = null,
)

class FriendNetworkScreenModel(
    private val authService: AuthService,
    private val usersApi: UsersApi,
    private val friendService: FriendService,
    private val cacheDao: FriendNetworkCacheDao,
    private val logger: Logger,
) : ScreenModel {

    companion object {
        // 调色板：按社区 ID 分配颜色
        // 相邻色对经色觉可分性校验（protan/deutan/tritan ΔE ≥ 8.9，正常视觉 ΔE ≥ 16.2）
        private val COLORS_PALETTE = listOf(
            Color(0xFF4E79C9),
            Color(0xFFDC7A30),
            Color(0xFF237B4B),
            Color(0xFFBD982A),
            Color(0xFFB85CA6),
            Color(0xFF77862B),
            Color(0xFF3BA8C9),
            Color(0xFFE06A7C),
            Color(0xFF9A60B4),
        )

        // 归并后的「其他」伪社区：碎片社区与孤立节点
        const val OTHER_COMMUNITY_ID = -1
        private const val MIN_COMMUNITY_SIZE = 3
        private val OTHER_COMMUNITY_COLOR = Color(0xFF8A93A0)

        // 骑墙者判定：外圈边数 ≥ max(STRADDLER_MIN_EDGES, STRADDLER_RATIO × 圈内边数)
        private const val STRADDLER_RATIO = 1.5f
        private const val STRADDLER_MIN_EDGES = 2
        // 站位引力加成封顶：至多 Top2 倾向圈
        private const val MAX_LEAN_SEGMENTS = 2

        // 社区锚定：新圈成员与旧圈的交集 ≥ 30%（且 ≥2 人）才继承旧编号/颜色
        private const val ANCHOR_MIN_OVERLAP_RATIO = 0.3f
        private const val ANCHOR_MIN_OVERLAP_COUNT = 2

        fun colorOfCommunity(communityId: Int): Color =
            if (communityId == OTHER_COMMUNITY_ID) OTHER_COMMUNITY_COLOR
            else COLORS_PALETTE[communityId % COLORS_PALETTE.size]

        /**
         * 社区划分：Louvain 检测后，不足 [MIN_COMMUNITY_SIZE] 人的碎片社区
         * 和孤立节点归并为「其他」。
         *
         * 编号规则：
         * - 无历史划分（[previousAssignments] 为空）：按人数降序编号（0 起），
         *   大社区优先拿到调色板前面的颜色
         * - 有历史划分：按成员重叠做锚定匹配——交集 ≥ max(2, 30%×新圈人数) 的
         *   新圈继承旧编号（颜色跟人走，刷新不洗牌）；未匹配的新圈分配空闲编号，
         *   且跳过上一轮的所有编号（刚退役的编号冷却一轮，避免颜色被立刻误复用）
         */
        fun assignCommunities(
            nodes: List<MutualFriendData>,
            edges: Map<String, List<String>>,
            selfId: String? = null,
            previousAssignments: Map<String, Int> = emptyMap(),
        ): Map<String, Int> {
            if (nodes.isEmpty()) return emptyMap()
            val adjacency = edges
                .filterKeys { it != selfId }
                .mapValues { (_, neighbors) -> neighbors.filter { it != selfId }.toSet() }
                .filterValues { it.isNotEmpty() }

            val detected = louvainDetect(adjacency)
            val realCommunities = detected.entries
                .groupBy({ it.value }, { it.key })
                .values
                .filter { it.size >= MIN_COMMUNITY_SIZE }
                .sortedWith(compareByDescending<List<String>> { it.size }.thenBy { it.minOrNull() })

            val result = mutableMapOf<String, Int>()
            val previousReal = previousAssignments.filterValues { it >= 0 }
            if (previousReal.isEmpty()) {
                realCommunities.forEachIndexed { index, members ->
                    members.forEach { result[it] = index }
                }
            } else {
                // 候选匹配对：新圈 × 旧编号，按交集人数贪心配对
                data class Candidate(val newIndex: Int, val prevId: Int, val overlap: Int)

                val candidates = mutableListOf<Candidate>()
                realCommunities.forEachIndexed { index, members ->
                    val overlapByPrev = mutableMapOf<Int, Int>()
                    members.forEach { member ->
                        previousReal[member]?.let { prevId ->
                            overlapByPrev[prevId] = (overlapByPrev[prevId] ?: 0) + 1
                        }
                    }
                    val threshold = maxOf(
                        ANCHOR_MIN_OVERLAP_COUNT.toFloat(),
                        ANCHOR_MIN_OVERLAP_RATIO * members.size
                    )
                    overlapByPrev.forEach { (prevId, overlap) ->
                        if (overlap >= threshold) candidates.add(Candidate(index, prevId, overlap))
                    }
                }
                val inherited = mutableMapOf<Int, Int>()
                val usedPrevIds = mutableSetOf<Int>()
                candidates
                    .sortedWith(
                        compareByDescending<Candidate> { it.overlap }
                            .thenBy { it.prevId }
                            .thenBy { it.newIndex }
                    )
                    .forEach { candidate ->
                        if (candidate.newIndex !in inherited && candidate.prevId !in usedPrevIds) {
                            inherited[candidate.newIndex] = candidate.prevId
                            usedPrevIds.add(candidate.prevId)
                        }
                    }
                // 未匹配的新圈：分配空闲编号，上一轮所有编号（含刚退役的）冷却一轮
                val blockedIds = previousReal.values.toMutableSet().apply { addAll(inherited.values) }
                var nextId = 0
                realCommunities.forEachIndexed { index, members ->
                    val communityId = inherited[index] ?: run {
                        while (nextId in blockedIds) nextId++
                        blockedIds.add(nextId)
                        nextId
                    }
                    members.forEach { result[it] = communityId }
                }
            }
            for (node in nodes) {
                if (node.id != selfId && node.id !in result) {
                    result[node.id] = OTHER_COMMUNITY_ID
                }
            }
            return result
        }

        fun communityNodeColors(communities: Map<String, Int>): Map<String, Color> =
            communities.mapValues { (_, communityId) -> colorOfCommunity(communityId) }

        /**
         * 社区图例：只含真实社区，按人数降序排列（锚定后编号不再代表人数排名），
         * 以圈内度数最高成员命名。只数本社区内部的连线：总度数高的人往往
         * 社交面广、归属最模糊，用圈内度数才能选出真正代表这个圈子的人
         */
        fun buildCommunityLegend(
            nodes: List<MutualFriendData>,
            edges: Map<String, List<String>>,
            communities: Map<String, Int>,
        ): List<CommunitySummary> {
            val nodeById = nodes.associateBy { it.id }
            return communities.entries
                .filter { it.value != OTHER_COMMUNITY_ID }
                .groupBy({ it.value }, { it.key })
                .entries
                .sortedWith(
                    compareByDescending<Map.Entry<Int, List<String>>> { it.value.size }
                        .thenBy { it.key }
                )
                .map { (communityId, members) ->
                    val memberSet = members.toSet()
                    val topMember = members.maxByOrNull { member ->
                        edges[member].orEmpty().count { it in memberSet }
                    }
                    CommunitySummary(
                        id = communityId,
                        name = topMember?.let { nodeById[it]?.displayName }.orEmpty()
                            .ifBlank { "#${communityId + 1}" },
                        count = members.size,
                        color = colorOfCommunity(communityId),
                    )
                }
        }

        /**
         * 骑墙者判定（封顶规则）：
         * 对真实社区的成员，统计与每个外圈的连线数，
         * 达到 max([STRADDLER_MIN_EDGES], [STRADDLER_RATIO]×圈内边数) 的记为显著倾向；
         * 按边数降序取 Top[MAX_LEAN_SEGMENTS]，其余合计为灰色余量
         */
        fun detectStraddlers(
            nodes: List<MutualFriendData>,
            edges: Map<String, List<String>>,
            communities: Map<String, Int>,
        ): Map<String, StraddlerInfo> {
            val result = mutableMapOf<String, StraddlerInfo>()
            for (node in nodes) {
                val own = communities[node.id] ?: continue
                if (own == OTHER_COMMUNITY_ID) continue
                var ownEdges = 0
                val leanCounts = mutableMapOf<Int, Int>()
                for (neighbor in edges[node.id].orEmpty()) {
                    val community = communities[neighbor] ?: continue
                    when {
                        community == own -> ownEdges++
                        community != OTHER_COMMUNITY_ID ->
                            leanCounts[community] = (leanCounts[community] ?: 0) + 1
                    }
                }
                val threshold = maxOf(STRADDLER_MIN_EDGES.toFloat(), STRADDLER_RATIO * ownEdges)
                val qualifying = leanCounts.entries
                    .filter { it.value >= threshold }
                    .sortedWith(compareByDescending<Map.Entry<Int, Int>> { it.value }.thenBy { it.key })
                if (qualifying.isEmpty()) continue
                result[node.id] = StraddlerInfo(
                    ownEdges = ownEdges,
                    leans = qualifying.take(MAX_LEAN_SEGMENTS)
                        .map { StraddlerLean(it.key, it.value) },
                    remainderEdges = qualifying.drop(MAX_LEAN_SEGMENTS).sumOf { it.value },
                )
            }
            return result
        }

        /**
         * 力导向布局算法
         * @param nodeSizePx 节点大小（像素），由 UI 层根据 density 计算
         * @param communities 社区划分，用于圈内聚拢、圈间分离
         * @param straddlers 骑墙者信息：Top 倾向圈的跨圈边引力加成
         */
        fun computeNodePositions(
            nodes: List<MutualFriendData>,
            edges: Map<String, List<String>>,
            nodeSizePx: Float,
            communities: Map<String, Int> = emptyMap(),
            straddlers: Map<String, StraddlerInfo> = emptyMap(),
        ): ForceLayoutResult {
            if (nodes.isEmpty()) return ForceLayoutResult(emptyMap(), 0f, 0f)
            return computeForceLayout(
                nodeIds = nodes.map { it.id },
                edges = edges,
                desiredSpacing = nodeSizePx * 1.3f,
                communities = communities,
                leans = straddlers.mapValues { (_, info) ->
                    info.leans.map { it.communityId }.toSet()
                },
            )
        }
    }

    var uiState by mutableStateOf(FriendNetworkUiState())
        private set

    fun setViewMode(mode: FriendNetworkViewMode) {
        uiState = uiState.copy(viewMode = mode)
    }

    private fun filterSelf(
        nodes: List<MutualFriendData>,
        edges: Map<String, List<String>>,
        selfId: String,
    ): Pair<List<MutualFriendData>, Map<String, List<String>>> {
        val filteredNodes = nodes.filter { it.id != selfId }
        val filteredEdges = edges
            .filterKeys { it != selfId }
            .mapValues { (_, v) -> v.filter { it != selfId } }
            .filterValues { it.isNotEmpty() }
        return filteredNodes to filteredEdges
    }

    fun loadCache(nodeSizePx: Float) {
        screenModelScope.launch(Dispatchers.IO) {
            uiState = uiState.copy(isPreparing = true)
            try {
                val currentUser = authService.currentUser()
                val cache = cacheDao.load(currentUser.id)
                if (cache != null) {
                    val selfId = cache.userId
                    val (filteredNodes, filteredEdges) = filterSelf(cache.nodes, cache.edges, selfId)
                    // 优先复用缓存里保存的划分：读缓存不重跑 Louvain，颜色与上次完全一致
                    val communities = cache.communityAssignments.takeIf { it.isNotEmpty() }
                        ?: assignCommunities(cache.nodes, cache.edges, selfId)
                    val straddlers = detectStraddlers(filteredNodes, filteredEdges, communities)
                    val layout = computeLayout(filteredNodes, filteredEdges, nodeSizePx, communities, straddlers)
                    val egoLayout = computeEgo(filteredNodes, filteredEdges, nodeSizePx, selfId)
                    uiState = uiState.copy(
                        selfId = selfId,
                        nodes = filteredNodes,
                        edges = filteredEdges,
                        nodeColors = communityNodeColors(communities),
                        communities = communities,
                        communityLegend = buildCommunityLegend(filteredNodes, filteredEdges, communities),
                        selfNode = cache.nodes.firstOrNull { it.id == selfId },
                        layout = layout,
                        egoLayout = egoLayout,
                        updatedAt = cache.updatedAt,
                        isFromCache = true,
                    )
                }
            } catch (e: Exception) {
                logger.error(e.message.orEmpty())
            } finally {
                uiState = uiState.copy(isPreparing = false)
            }
        }
    }

    @OptIn(ExperimentalTime::class)
    fun refresh(nodeSizePx: Float) {
        if (uiState.isLoading) return
        screenModelScope.launch(Dispatchers.IO) {
            uiState = uiState.copy(isLoading = true, progress = FriendNetworkProgress(0, 0))
            try {
                val currentUser = authService.currentUser()
                val friendList = loadFriends()
                val friendIds = friendList.map { it.id }.toSet()
                val total = friendList.size
                uiState = uiState.copy(progress = FriendNetworkProgress(0, total))

                val edges = mutableMapOf<String, MutableSet<String>>()
                val selfNode = currentUser.toMutualFriendData(isFriend = false)
                edges[selfNode.id] = friendIds.toMutableSet()

                val nodes = mutableListOf<MutualFriendData>()
                nodes.add(selfNode)
                nodes.addAll(friendList.map { it.toMutualFriendData() })

                var processed = 0
                val chunkSize = 4
                friendList.chunked(chunkSize).forEach { chunk ->
                    coroutineScope {
                        val deferred = chunk.map { friend ->
                            async {
                                friend.id to fetchAllMutualFriends(friend.id)
                            }
                        }
                        deferred.forEach { task ->
                            val (friendId, mutuals) = task.await()
                            val mutualIds = mutuals.asSequence()
                                .map { it.id }
                                .filter { it in friendIds }
                                .toSet()
                            edges.getOrPut(friendId) { mutableSetOf() }.addAll(mutualIds)
                            mutualIds.forEach { mutualId ->
                                edges.getOrPut(mutualId) { mutableSetOf() }.add(friendId)
                            }
                            processed += 1
                            uiState = uiState.copy(progress = FriendNetworkProgress(processed, total))
                        }
                    }
                }

                val finalEdges = edges.mapValues { it.value.toList() }
                val selfId = currentUser.id
                val (filteredNodes, filteredEdges) = filterSelf(nodes, finalEdges, selfId)
                // 社区锚定：拿上一次的划分做匹配，颜色跟人走
                val previousAssignments = uiState.communities.takeIf { it.isNotEmpty() }
                    ?: cacheDao.load(selfId)?.communityAssignments.orEmpty()
                val communities = assignCommunities(nodes, finalEdges, selfId, previousAssignments)
                val cache = FriendNetworkCache(
                    userId = currentUser.id,
                    updatedAt = now().toEpochMilliseconds(),
                    nodes = nodes,
                    edges = finalEdges,
                    communityAssignments = communities,
                )
                cacheDao.save(cache)
                val straddlers = detectStraddlers(filteredNodes, filteredEdges, communities)
                val layout = computeLayout(filteredNodes, filteredEdges, nodeSizePx, communities, straddlers)
                val egoLayout = computeEgo(filteredNodes, filteredEdges, nodeSizePx, selfId)
                uiState = FriendNetworkUiState(
                    selfId = selfId,
                    nodes = filteredNodes,
                    edges = filteredEdges,
                    nodeColors = communityNodeColors(communities),
                    communities = communities,
                    communityLegend = buildCommunityLegend(filteredNodes, filteredEdges, communities),
                    viewMode = uiState.viewMode,
                    selfNode = selfNode,
                    layout = layout,
                    egoLayout = egoLayout,
                    updatedAt = cache.updatedAt,
                    isFromCache = false,
                    isLoading = false,
                    progress = null
                )
            } catch (e: Exception) {
                logger.error(e.message.orEmpty())
                SharedFlowCentre.toastText.emit(ToastText.Error(e.message.orEmpty()))
                uiState = uiState.copy(isLoading = false, progress = null)
            }
        }
    }

    private suspend fun loadFriends(): List<FriendData> {
        friendService.refreshFriendList()
        return friendService.friendMap.values.sortedBy { it.displayName.lowercase() }
    }

    private suspend fun fetchAllMutualFriends(userId: String): List<MutualFriendData> {
        val all = mutableListOf<MutualFriendData>()
        var offset = 0
        val limit = 100
        while (true) {
            val pageResult = authService.reTryAuthCatching {
                usersApi.getMutualFriends(userId, n = limit, offset = offset)
            }
            if (pageResult.isFailure) {
                val message = pageResult.exceptionOrNull()?.message.orEmpty()
                logger.error(message)
                SharedFlowCentre.toastText.emit(ToastText.Error(message))
                break
            }
            val page = pageResult.getOrDefault(emptyList())
            all.addAll(page)
            if (page.size < limit) break
            offset += limit
        }
        return all
    }

    private fun FriendData.toMutualFriendData() = MutualFriendData(
        id = id,
        displayName = displayName,
        bio = bio,
        bioLinks = bioLinks,
        tags = tags,
        currentAvatarImageUrl = currentAvatarImageUrl,
        currentAvatarThumbnailImageUrl = currentAvatarThumbnailImageUrl,
        currentAvatarTags = currentAvatarTags,
        imageUrl = imageUrl,
        profilePicOverride = profilePicOverride,
        userIcon = userIcon,
        isFriend = isFriend,
        lastLogin = lastLogin,
        lastPlatform = lastPlatform,
        developerType = developerType,
        pronouns = pronouns,
    )

    private suspend fun computeLayout(
        nodes: List<MutualFriendData>,
        edges: Map<String, List<String>>,
        nodeSizePx: Float,
        communities: Map<String, Int>,
        straddlers: Map<String, StraddlerInfo>,
    ): ForceLayoutResult = withContext(Dispatchers.Default) {
        computeNodePositions(nodes, edges, nodeSizePx, communities, straddlers)
    }

    private suspend fun computeEgo(
        nodes: List<MutualFriendData>,
        edges: Map<String, List<String>>,
        nodeSizePx: Float,
        selfId: String,
    ): ForceLayoutResult = withContext(Dispatchers.Default) {
        computeEgoLayout(
            nodeIds = nodes.map { it.id },
            edges = edges,
            desiredSpacing = nodeSizePx * 1.3f,
            selfId = selfId,
        )
    }

    private fun CurrentUserData.toMutualFriendData(isFriend: Boolean) = MutualFriendData(
        id = id,
        displayName = displayName,
        bio = bio,
        bioLinks = bioLinks,
        tags = tags,
        currentAvatarImageUrl = currentAvatarImageUrl,
        currentAvatarThumbnailImageUrl = currentAvatarThumbnailImageUrl,
        currentAvatarTags = currentAvatarTags,
        imageUrl = "",
        profilePicOverride = profilePicOverride,
        userIcon = userIcon,
        isFriend = isFriend,
        lastLogin = lastLogin,
        lastPlatform = lastPlatform,
        developerType = developerType,
        pronouns = pronouns,
    )
}
