package io.github.vrcmteam.vrcm.core.algorithms

import kotlin.math.sqrt
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.TimeSource

class ForceLayoutTest {

    private val spacing = 200f

    private fun minPairDistance(result: ForceLayoutResult): Float {
        val points = result.positions.values.toList()
        var minDist = Float.MAX_VALUE
        for (i in points.indices) {
            for (j in i + 1 until points.size) {
                val dx = points[i].x - points[j].x
                val dy = points[i].y - points[j].y
                val dist = sqrt(dx * dx + dy * dy)
                if (dist < minDist) minDist = dist
            }
        }
        return minDist
    }

    private fun randomGraph(nodeCount: Int, edgeCount: Int): Pair<List<String>, Map<String, List<String>>> {
        val ids = (0 until nodeCount).map { "usr_$it" }
        val rng = Random(7)
        val edges = mutableMapOf<String, MutableList<String>>()
        repeat(edgeCount) {
            val a = ids[rng.nextInt(nodeCount)]
            val b = ids[rng.nextInt(nodeCount)]
            if (a == b) return@repeat
            edges.getOrPut(a) { mutableListOf() }.add(b)
            edges.getOrPut(b) { mutableListOf() }.add(a)
        }
        return ids to edges
    }

    @Test
    fun emptyGraphReturnsEmptyResult() {
        val result = computeForceLayout(emptyList(), emptyMap(), spacing)
        assertTrue(result.positions.isEmpty())
        assertEquals(0f, result.layoutWidthPx)
    }

    @Test
    fun everyNodeGetsAPositionInsideTheCanvas() {
        val (ids, edges) = randomGraph(nodeCount = 60, edgeCount = 120)
        val result = computeForceLayout(ids, edges, spacing)

        assertEquals(ids.toSet(), result.positions.keys)
        result.positions.values.forEach { pos ->
            assertTrue(pos.x in 0f..result.layoutWidthPx, "x=${pos.x} 超出画布 ${result.layoutWidthPx}")
            assertTrue(pos.y in 0f..result.layoutHeightPx, "y=${pos.y} 超出画布 ${result.layoutHeightPx}")
        }
    }

    @Test
    fun nodesDoNotOverlapAfterCollisionResolution() {
        val (ids, edges) = randomGraph(nodeCount = 150, edgeCount = 400)
        val result = computeForceLayout(ids, edges, spacing)

        // 碰撞消除保证中心距不小于 spacing * 0.95（留一点收敛容差）
        val minDist = minPairDistance(result)
        assertTrue(minDist >= spacing * 0.9f, "最小节点间距 $minDist 小于期望 ${spacing * 0.9f}")
    }

    @Test
    fun isolatedNodesArePlacedWithoutOverlap() {
        // 一半节点完全没有边
        val ids = (0 until 80).map { "usr_$it" }
        val edges = mapOf(
            "usr_0" to listOf("usr_1"),
            "usr_1" to listOf("usr_0"),
        )
        val result = computeForceLayout(ids, edges, spacing)

        assertEquals(ids.toSet(), result.positions.keys)
        val minDist = minPairDistance(result)
        assertTrue(minDist >= spacing * 0.9f, "孤立节点间距 $minDist 过小")
    }

    @Test
    fun layoutIsDeterministic() {
        val (ids, edges) = randomGraph(nodeCount = 50, edgeCount = 100)
        val first = computeForceLayout(ids, edges, spacing)
        val second = computeForceLayout(ids, edges, spacing)
        assertEquals(first.positions, second.positions)
    }

    @Test
    fun edgesReferencingUnknownNodesAreIgnored() {
        val ids = listOf("usr_a", "usr_b")
        val edges = mapOf(
            "usr_a" to listOf("usr_b", "usr_missing"),
            "usr_stranger" to listOf("usr_a"),
        )
        val result = computeForceLayout(ids, edges, spacing)
        assertEquals(ids.toSet(), result.positions.keys)
    }

    @Test
    fun communityAwareLayoutPullsCommunitiesApart() {
        // 两个 10 人全连接小团 + 2 条跨团边
        val ids = (0 until 20).map { "usr_$it" }
        val edges = mutableMapOf<String, MutableList<String>>()
        fun link(a: Int, b: Int) {
            edges.getOrPut("usr_$a") { mutableListOf() }.add("usr_$b")
            edges.getOrPut("usr_$b") { mutableListOf() }.add("usr_$a")
        }
        for (group in 0..1) {
            for (i in 0 until 10) {
                for (j in i + 1 until 10) link(group * 10 + i, group * 10 + j)
            }
        }
        link(0, 10)
        link(5, 15)
        val communities = ids.associateWith { if (it.removePrefix("usr_").toInt() < 10) 0 else 1 }

        val result = computeForceLayout(ids, edges, spacing, communities)

        fun dist(a: String, b: String): Float {
            val p = result.positions.getValue(a)
            val q = result.positions.getValue(b)
            val dx = p.x - q.x
            val dy = p.y - q.y
            return sqrt(dx * dx + dy * dy)
        }
        var intraSum = 0f
        var intraCount = 0
        var crossSum = 0f
        var crossCount = 0
        for (i in ids.indices) {
            for (j in i + 1 until ids.size) {
                val d = dist(ids[i], ids[j])
                if (communities.getValue(ids[i]) == communities.getValue(ids[j])) {
                    intraSum += d; intraCount++
                } else {
                    crossSum += d; crossCount++
                }
            }
        }
        val avgIntra = intraSum / intraCount
        val avgCross = crossSum / crossCount
        assertTrue(
            avgCross > avgIntra * 1.5f,
            "跨团平均距离 $avgCross 应显著大于团内平均距离 $avgIntra"
        )
    }

    @Test
    fun largeGraphLaysOutQuickly() {
        val (ids, edges) = randomGraph(nodeCount = 1500, edgeCount = 6000)
        val mark = TimeSource.Monotonic.markNow()
        val result = computeForceLayout(ids, edges, spacing)
        val elapsed = mark.elapsedNow()

        assertEquals(ids.size, result.positions.size)
        assertTrue(
            elapsed.inWholeMilliseconds < 10_000,
            "1500 个节点布局耗时 ${elapsed.inWholeMilliseconds}ms，超过 10s"
        )
    }
}
