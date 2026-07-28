package io.github.vrcmteam.vrcm.core.algorithms

import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EgoLayoutTest {

    private val spacing = 200f
    private val selfId = "usr_self"

    private fun symmetricEdges(vararg pairs: Pair<String, String>): Map<String, List<String>> {
        val map = mutableMapOf<String, MutableList<String>>()
        pairs.forEach { (a, b) ->
            map.getOrPut(a) { mutableListOf() }.add(b)
            map.getOrPut(b) { mutableListOf() }.add(a)
        }
        return map
    }

    private fun distToSelf(result: ForceLayoutResult, id: String): Float {
        val self = result.positions.getValue(selfId)
        val p = result.positions.getValue(id)
        val dx = p.x - self.x
        val dy = p.y - self.y
        return sqrt(dx * dx + dy * dy)
    }

    @Test
    fun emptyGraphContainsOnlySelf() {
        val result = computeEgoLayout(emptyList(), emptyMap(), spacing, selfId)
        assertEquals(setOf(selfId), result.positions.keys)
    }

    @Test
    fun moreMutualFriendsMeansCloserToSelf() {
        // hub 与 5 人相连（共同好友 5），mid 2 条，leaf 1 条，loner 0 条
        val ids = listOf("hub", "mid", "leaf", "loner", "a", "b", "c", "d")
        val edges = symmetricEdges(
            "hub" to "a", "hub" to "b", "hub" to "c", "hub" to "d", "hub" to "mid",
            "mid" to "leaf",
        )
        val result = computeEgoLayout(ids, edges, spacing, selfId)

        assertEquals(ids.toSet() + selfId, result.positions.keys)
        val hub = distToSelf(result, "hub")
        val mid = distToSelf(result, "mid")
        val loner = distToSelf(result, "loner")
        assertTrue(hub < mid, "hub($hub) 应比 mid($mid) 更近")
        assertTrue(mid < loner, "mid($mid) 应比 loner($loner) 更近")
    }

    @Test
    fun layoutIsDeterministicAndInsideCanvas() {
        val ids = (0 until 40).map { "usr_$it" }
        val pairs = mutableListOf<Pair<String, String>>()
        for (i in 0 until 40) {
            for (j in i + 1 until 40) {
                if ((i + j) % 5 == 0) pairs.add("usr_$i" to "usr_$j")
            }
        }
        val edges = symmetricEdges(*pairs.toTypedArray())
        val first = computeEgoLayout(ids, edges, spacing, selfId)
        val second = computeEgoLayout(ids, edges, spacing, selfId)

        assertEquals(first.positions, second.positions)
        first.positions.values.forEach { pos ->
            assertTrue(pos.x in 0f..first.layoutWidthPx && pos.y in 0f..first.layoutHeightPx)
        }
    }

    @Test
    fun guideRingsDescendFromMaxMutualCount() {
        val ids = listOf("a", "b", "c", "d", "e")
        val edges = symmetricEdges("a" to "b", "a" to "c", "a" to "d", "a" to "e", "b" to "c")
        val result = computeEgoLayout(ids, edges, spacing, selfId)

        assertTrue(result.guideRings.isNotEmpty())
        // 共同好友数越大的环半径越小
        val sorted = result.guideRings.sortedByDescending { it.mutualCount }
        for (i in 1 until sorted.size) {
            assertTrue(sorted[i - 1].radiusPx < sorted[i].radiusPx)
        }
        assertEquals(4, sorted.first().mutualCount)
    }

    @Test
    fun nodesDoNotOverlapAroundCenter() {
        val ids = (0 until 60).map { "usr_$it" }
        // 全员与前 5 人相连，制造大量同半径节点
        val pairs = mutableListOf<Pair<String, String>>()
        for (i in 5 until 60) {
            for (j in 0 until 5) pairs.add("usr_$i" to "usr_$j")
        }
        val edges = symmetricEdges(*pairs.toTypedArray())
        val result = computeEgoLayout(ids, edges, spacing, selfId)

        val points = result.positions.values.toList()
        var minDist = Float.MAX_VALUE
        for (i in points.indices) {
            for (j in i + 1 until points.size) {
                val dx = points[i].x - points[j].x
                val dy = points[i].y - points[j].y
                minDist = minOf(minDist, sqrt(dx * dx + dy * dy))
            }
        }
        assertTrue(minDist >= spacing * 0.9f, "最小间距 $minDist 过小")
    }
}
