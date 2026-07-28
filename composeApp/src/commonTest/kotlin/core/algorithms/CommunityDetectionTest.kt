package io.github.vrcmteam.vrcm.core.algorithms

import kotlin.test.Test
import kotlin.test.assertEquals

class CommunityDetectionTest {

    @Test
    fun resultIsIndependentOfInsertionOrder() {
        // 两个三角形 + 一条桥边
        val pairs = listOf(
            "a" to "b", "b" to "c", "a" to "c",
            "d" to "e", "e" to "f", "d" to "f",
            "c" to "d",
        )
        fun build(order: List<Pair<String, String>>): Map<String, Set<String>> {
            val map = LinkedHashMap<String, MutableSet<String>>()
            order.forEach { (x, y) ->
                map.getOrPut(x) { linkedSetOf() }.add(y)
                map.getOrPut(y) { linkedSetOf() }.add(x)
            }
            return map
        }
        val forward = louvainDetect(build(pairs))
        val backward = louvainDetect(build(pairs.reversed().map { (a, b) -> b to a }))
        assertEquals(forward, backward)
    }
}
