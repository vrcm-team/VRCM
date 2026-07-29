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
    fun legendNamePrefersInternalDegreeOverTotalDegree() {
        // 5 人团（缺 a1-a5 边）：a1 圈内度 3 但总度 5（外连 b1、c1），
        // a2/a3/a4 圈内度 4——代言人必须是圈内度最高者，而非社交面最广的 a1
        val members = (1..5).map { node("a$it") } + (1..3).map { node("b$it") } +
            node("c1") + node("c2")
        val graph = symmetricEdges(
            "a1" to "a2", "a1" to "a3", "a1" to "a4",
            "a2" to "a3", "a2" to "a4", "a2" to "a5",
            "a3" to "a4", "a3" to "a5", "a4" to "a5",
            "b1" to "b2", "b2" to "b3", "b1" to "b3",
            "c1" to "c2",
            "a1" to "b1", "a1" to "c1",
        )
        val communities = FriendNetworkScreenModel.assignCommunities(members, graph)
        val legend = FriendNetworkScreenModel.buildCommunityLegend(members, graph, communities)

        val communityA = legend.first { it.count == 5 }
        assertTrue(communityA.name != "a1", "总度数最高的 a1 不应成为代言人")
        assertTrue(communityA.name in setOf("a2", "a3", "a4"))
    }

    @Test
    fun otherCommunityUsesGrayAndPaletteCycles() {
        val other = FriendNetworkScreenModel.colorOfCommunity(FriendNetworkScreenModel.OTHER_COMMUNITY_ID)
        assertEquals(FriendNetworkScreenModel.colorOfCommunity(0), FriendNetworkScreenModel.colorOfCommunity(9))
        assertTrue(other != FriendNetworkScreenModel.colorOfCommunity(0))
    }

    @Test
    fun straddlerDetectionUsesRatioThreshold() {
        val members = listOf(node("a1"), node("a2"), node("x"), node("b1"), node("b2"), node("b3"), node("y"))
        val communities = mapOf(
            "a1" to 0, "a2" to 0, "x" to 0,
            "b1" to 1, "b2" to 1, "b3" to 1, "y" to 1,
        )
        val graph = symmetricEdges(
            // x：圈内 2 边，连圈 1 有 3 边 → 3 ≥ max(2, 1.5×2)=3 显著
            "x" to "a1", "x" to "a2",
            "x" to "b1", "x" to "b2", "x" to "b3",
            // y：圈内 3 边，连圈 0 有 2 边 → 2 < 4.5 不显著
            "y" to "b1", "y" to "b2", "y" to "b3",
            "y" to "a1", "y" to "a2",
        )
        val straddlers = FriendNetworkScreenModel.detectStraddlers(members, graph, communities)

        assertEquals(setOf(1), straddlers.getValue("x"))
        assertTrue("y" !in straddlers)
    }

    @Test
    fun straddlerLeansCapAtTopTwo() {
        val ids = listOf("s", "a1", "a2") +
            (1..4).map { "b$it" } + (1..4).map { "c$it" } + (1..3).map { "d$it" }
        val members = ids.map { node(it) }
        val communities = buildMap {
            put("s", 0); put("a1", 0); put("a2", 0)
            (1..4).forEach { put("b$it", 1) }
            (1..4).forEach { put("c$it", 2) }
            (1..3).forEach { put("d$it", 3) }
        }
        // s：圈内 2 边；B=4、C=4、D=3 全部过阈值(max(2, 3)=3)
        val pairs = mutableListOf("s" to "a1", "s" to "a2")
        (1..4).forEach { pairs.add("s" to "b$it") }
        (1..4).forEach { pairs.add("s" to "c$it") }
        (1..3).forEach { pairs.add("s" to "d$it") }
        val graph = symmetricEdges(*pairs.toTypedArray())
        val straddlers = FriendNetworkScreenModel.detectStraddlers(members, graph, communities)

        // Top2 取 B、C（同边数按社区编号升序），D 不进入布局引力集合
        assertEquals(setOf(1, 2), straddlers.getValue("s"))
    }

    @Test
    fun otherCommunityMembersAreNeverStraddlers() {
        val members = listOf(node("o1"), node("b1"), node("b2"), node("b3"))
        val communities = mapOf(
            "o1" to FriendNetworkScreenModel.OTHER_COMMUNITY_ID,
            "b1" to 0, "b2" to 0, "b3" to 0,
        )
        val graph = symmetricEdges("o1" to "b1", "o1" to "b2", "o1" to "b3")
        assertTrue(FriendNetworkScreenModel.detectStraddlers(members, graph, communities).isEmpty())
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
