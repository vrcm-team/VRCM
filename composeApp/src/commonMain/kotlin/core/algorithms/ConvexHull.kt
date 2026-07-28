package io.github.vrcmteam.vrcm.core.algorithms

import androidx.compose.ui.geometry.Offset

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
