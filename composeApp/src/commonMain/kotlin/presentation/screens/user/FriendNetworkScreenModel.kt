package io.github.vrcmteam.vrcm.presentation.screens.user

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import io.github.vrcmteam.vrcm.core.algorithms.ForceLayoutResult
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

data class FriendNetworkUiState(
    val selfId: String? = null,
    val nodes: List<MutualFriendData> = emptyList(),
    val edges: Map<String, List<String>> = emptyMap(),
    val nodeColors: Map<String, Color> = emptyMap(),
    val layout: ForceLayoutResult? = null,
    val updatedAt: Long? = null,
    val isFromCache: Boolean = false,
    val isLoading: Boolean = false,
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
        private val COLORS_PALETTE = listOf(
            Color(0xFF5470C6),
            Color(0xFF91CC75),
            Color(0xFFFAC858),
            Color(0xFFEE6666),
            Color(0xFF73C0DE),
            Color(0xFF3BA272),
            Color(0xFFFC8452),
            Color(0xFF9A60B4),
            Color(0xFFEA7CCC),
        )

        /**
         * 社区颜色分配
         * 按连接密度分组，而非简单连通分量
         */
        fun assignCommunityColors(
            nodes: List<MutualFriendData>,
            edges: Map<String, List<String>>,
            selfId: String? = null
        ): Map<String, Color> {
            if (nodes.isEmpty()) return emptyMap()
            val adjacency = edges
                .filterKeys { it != selfId }
                .mapValues { (_, neighbors) -> neighbors.filter { it != selfId }.toSet() }
                .filterValues { it.isNotEmpty() }

            // Louvain 社区检测
            val communityMap = louvainDetect(adjacency)

            // 按社区大小降序排列，大的社区先分配颜色
            val grouped = communityMap.entries.groupBy { it.value }
            val sorted = grouped.entries.sortedByDescending { it.value.size }
            val colorMap = mutableMapOf<String, Color>()
            sorted.forEachIndexed { index, (_, members) ->
                val color = COLORS_PALETTE[index % COLORS_PALETTE.size]
                members.forEach { colorMap[it.key] = color }
            }
            // 孤立节点（没有边的）单独分配颜色
            for (node in nodes) {
                if (node.id != selfId && node.id !in colorMap) {
                    colorMap[node.id] = COLORS_PALETTE[colorMap.size % COLORS_PALETTE.size]
                }
            }
            return colorMap
        }

        /**
         * 力导向布局算法
         * @param nodeSizePx 节点大小（像素），由 UI 层根据 density 计算
         */
        fun computeNodePositions(
            nodes: List<MutualFriendData>,
            edges: Map<String, List<String>>,
            nodeSizePx: Float,
        ): ForceLayoutResult {
            if (nodes.isEmpty()) return ForceLayoutResult(emptyMap(), 0f, 0f)
            return computeForceLayout(
                nodeIds = nodes.map { it.id },
                edges = edges,
                desiredSpacing = nodeSizePx * 1.3f,
            )
        }
    }

    var uiState by mutableStateOf(FriendNetworkUiState())
        private set

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
            runCatching { authService.currentUser() }
                .onSuccess { currentUser ->
                    val cache = cacheDao.load(currentUser.id) ?: return@onSuccess
                    val selfId = cache.userId
                    val (filteredNodes, filteredEdges) = filterSelf(cache.nodes, cache.edges, selfId)
                    val nodeColors = assignCommunityColors(cache.nodes, cache.edges, selfId)
                    val layout = computeLayout(filteredNodes, filteredEdges, nodeSizePx)
                    uiState = uiState.copy(
                        selfId = selfId,
                        nodes = filteredNodes,
                        edges = filteredEdges,
                        nodeColors = nodeColors,
                        layout = layout,
                        updatedAt = cache.updatedAt,
                        isFromCache = true,
                    )
                }
                .onFailure {
                    logger.error(it.message.orEmpty())
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
                val cache = FriendNetworkCache(
                    userId = currentUser.id,
                    updatedAt = now().toEpochMilliseconds(),
                    nodes = nodes,
                    edges = finalEdges
                )
                cacheDao.save(cache)
                val selfId = currentUser.id
                val (filteredNodes, filteredEdges) = filterSelf(nodes, finalEdges, selfId)
                val nodeColors = assignCommunityColors(nodes, finalEdges, selfId)
                val layout = computeLayout(filteredNodes, filteredEdges, nodeSizePx)
                uiState = FriendNetworkUiState(
                    selfId = selfId,
                    nodes = filteredNodes,
                    edges = filteredEdges,
                    nodeColors = nodeColors,
                    layout = layout,
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
    ): ForceLayoutResult = withContext(Dispatchers.Default) {
        computeNodePositions(nodes, edges, nodeSizePx)
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
