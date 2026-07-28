package io.github.vrcmteam.vrcm.core.algorithms

import androidx.compose.ui.geometry.Offset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ConvexHullTest {

    @Test
    fun innerPointsAreExcluded() {
        val square = listOf(
            Offset(0f, 0f), Offset(10f, 0f), Offset(10f, 10f), Offset(0f, 10f),
            Offset(5f, 5f), Offset(3f, 7f),
        )
        val hull = convexHull(square)
        assertEquals(4, hull.size)
        assertTrue(hull.containsAll(square.take(4)))
    }

    @Test
    fun collinearPointsDegenerateToEndpoints() {
        val line = listOf(Offset(0f, 0f), Offset(5f, 5f), Offset(10f, 10f))
        val hull = convexHull(line)
        assertEquals(2, hull.size)
        assertTrue(Offset(0f, 0f) in hull && Offset(10f, 10f) in hull)
    }

    @Test
    fun tinyInputsPassThrough() {
        assertEquals(0, convexHull(emptyList()).size)
        assertEquals(1, convexHull(listOf(Offset(1f, 2f))).size)
        assertEquals(2, convexHull(listOf(Offset(1f, 2f), Offset(3f, 4f))).size)
        // 重复点去重
        assertEquals(1, convexHull(listOf(Offset(1f, 2f), Offset(1f, 2f))).size)
    }
}
