package io.github.vrcmteam.vrcm.core.algorithms

import androidx.compose.ui.geometry.Offset
import kotlin.math.sqrt

/**
 * Andrew 单调链凸包
 *
 * @param points 任意点集
 * @return 凸包顶点（逆时针）；点数少于 3 或共线时退化为端点序列
 */
fun convexHull(points: List<Offset>): List<Offset> {
    val pts = points.distinct().sortedWith(compareBy({ it.x }, { it.y }))
    if (pts.size < 3) return pts

    fun cross(o: Offset, a: Offset, b: Offset): Float =
        (a.x - o.x) * (b.y - o.y) - (a.y - o.y) * (b.x - o.x)

    val lower = mutableListOf<Offset>()
    for (p in pts) {
        while (lower.size >= 2 && cross(lower[lower.size - 2], lower[lower.size - 1], p) <= 0f) {
            lower.removeAt(lower.size - 1)
        }
        lower.add(p)
    }
    val upper = mutableListOf<Offset>()
    for (p in pts.asReversed()) {
        while (upper.size >= 2 && cross(upper[upper.size - 2], upper[upper.size - 1], p) <= 0f) {
            upper.removeAt(upper.size - 1)
        }
        upper.add(p)
    }
    lower.removeAt(lower.size - 1)
    upper.removeAt(upper.size - 1)
    return lower + upper
}

/**
 * 排除会把社区轮廓拉进圈间走廊的离群成员，只保留空间核心点。
 */
fun filterCorePoints(points: List<Offset>, factor: Float = 1.8f, minRadius: Float = 0f): List<Offset> {
    if (points.size < 4) return points
    var centerX = 0f
    var centerY = 0f
    points.forEach {
        centerX += it.x
        centerY += it.y
    }
    centerX /= points.size
    centerY /= points.size
    val distances = points.map { point ->
        val dx = point.x - centerX
        val dy = point.y - centerY
        sqrt(dx * dx + dy * dy)
    }
    val median = distances.sorted()[distances.size / 2]
    val threshold = maxOf(median * factor, minRadius)
    val core = points.filterIndexed { index, _ -> distances[index] <= threshold }
    return if (core.size >= 3) core else points
}
