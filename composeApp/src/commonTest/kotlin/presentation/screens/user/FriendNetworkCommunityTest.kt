package io.github.vrcmteam.vrcm.presentation.screens.user

import io.github.vrcmteam.vrcm.network.api.users.data.MutualFriendData
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FriendNetworkCommunityTest {

    private fun node(id: String, name: String = id) = MutualFriendData(id = id, displayName = name)

    private fun symmetricEdges(vararg pairs: Pair<String, String>): Map<String, List<String>> {
        val map = mutableMapOf<String, MutableList<String>>()
        pairs.forEach { (a, b) ->
            map.getOrPut(a) { mutableListOf() }.add(b)
            map.getOrPut(b) { mutableListOf() }.add(a)
        }
        return map
    }

    // 4 人全连接团 + 3 人三角 + 2 人对 + 1 个孤立节点
    private val nodes = (1..4).map { node("a$it") } +
        (1..3).map { node("b$it") } +
        (1..2).map { node("c$it") } +
        node("lonely")
    private val edges = symmetricEdges(
        "a1" to "a2", "a1" to "a3", "a1" to "a4", "a2" to "a3", "a2" to "a4", "a3" to "a4",
        "b1" to "b2", "b2" to "b3", "b1" to "b3",
        "c1" to "c2",
    )

    @Test
    fun smallCommunitiesAndIsolatedNodesMergeIntoOther() {
        val communities = FriendNetworkScreenModel.assignCommunities(nodes, edges)

        // 4 人团编号 0（人最多），3 人三角编号 1
        assertEquals(setOf(0), (1..4).map { communities["a$it"] }.toSet())
        assertEquals(setOf(1), (1..3).map { communities["b$it"] }.toSet())
        // 2 人对与孤立节点归并为「其他」
        assertEquals(FriendNetworkScreenModel.OTHER_COMMUNITY_ID, communities["c1"])
        assertEquals(FriendNetworkScreenModel.OTHER_COMMUNITY_ID, communities["c2"])
        assertEquals(FriendNetworkScreenModel.OTHER_COMMUNITY_ID, communities["lonely"])
    }

    @Test
    fun selfIsExcludedFromCommunities() {
        val selfEdges = edges + ("self" to listOf("a1", "b1"))
        val communities = FriendNetworkScreenModel.assignCommunities(
            nodes + node("self"), selfEdges, selfId = "self"
        )
        assertTrue("self" !in communities)
    }

    @Test
    fun legendSortsBySizeAndNamesByTopDegreeMember() {
        val communities = FriendNetworkScreenModel.assignCommunities(nodes, edges)
        val legend = FriendNetworkScreenModel.buildCommunityLegend(nodes, edges, communities)

        // 只含真实社区，按人数降序
        assertEquals(listOf(4, 3), legend.map { it.count })
        assertEquals(listOf(0, 1), legend.map { it.id })
        // 4 人团内所有人度数相同，取任一成员名；三角同理——名字必须来自本社区
        assertTrue(legend[0].name.startsWith("a"))
        assertTrue(legend[1].name.startsWith("b"))
    }

    @Test
    fun otherCommunityUsesGrayAndPaletteCycles() {
        val other = FriendNetworkScreenModel.colorOfCommunity(FriendNetworkScreenModel.OTHER_COMMUNITY_ID)
        assertEquals(FriendNetworkScreenModel.colorOfCommunity(0), FriendNetworkScreenModel.colorOfCommunity(9))
        assertTrue(other != FriendNetworkScreenModel.colorOfCommunity(0))
    }

    @Test
    fun nodeColorsFollowCommunityAssignment() {
        val communities = FriendNetworkScreenModel.assignCommunities(nodes, edges)
        val colors = FriendNetworkScreenModel.communityNodeColors(communities)
        assertEquals(FriendNetworkScreenModel.colorOfCommunity(0), colors["a1"])
        assertEquals(
            FriendNetworkScreenModel.colorOfCommunity(FriendNetworkScreenModel.OTHER_COMMUNITY_ID),
            colors["lonely"]
        )
    }
}
