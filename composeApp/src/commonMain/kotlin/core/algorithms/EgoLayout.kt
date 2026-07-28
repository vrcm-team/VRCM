package io.github.vrcmteam.vrcm.core.algorithms

import androidx.compose.ui.geometry.Offset
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

private const val EGO_MARGIN_FACTOR = 1f

/**
 * 自我中心径向布局：自己固定在圆心，好友与自己的共同好友越多离圆心越近。
 * 这个视图里不存在社区概念，只有"我与每个人的距离"一个维度。
 *
 * - 按共同好友数降序排名，沿向日葵螺旋铺开（半径 = c×√排名、黄金角步进）：
 *   密度处处均匀，不会因度数分布偏斜出现层与层之间的大片空档
 * - 参考环的半径恰好包住"共同好友数 ≥ 刻度"的人群
 * - 碰撞消除时圆心固定不动
 *
 * @param nodeIds 好友 ID 列表（不含自己）
 * @param edges 邻接表；每个好友的边数即其与用户的共同好友数
 * @param desiredSpacing 节点间期望间距（像素）
 * @param selfId 自己的 ID，会以圆心位置包含在返回的 positions 里
 */
fun computeEgoLayout(
    nodeIds: List<String>,
    edges: Map<String, List<String>>,
    desiredSpacing: Float,
    selfId: String,
): ForceLayoutResult {
    val n = nodeIds.size
    if (n == 0) {
        val margin = desiredSpacing * EGO_MARGIN_FACTOR
        return ForceLayoutResult(
            positions = mapOf(selfId to Offset(margin, margin)),
            layoutWidthPx = margin * 2f,
            layoutHeightPx = margin * 2f,
        )
    }

    val degrees = nodeIds.associateWith { id -> edges[id].orEmpty().count { it != selfId } }
    val maxDegree = (degrees.values.maxOrNull() ?: 0).coerceAtLeast(1)
    // 向日葵螺旋：半径 = c×√(排名 + 偏移)，偏移给圆心的自己留出空间
    val c = desiredSpacing
    val rMin = desiredSpacing * 1.6f
    val rankOffset = (rMin / c) * (rMin / c)

    val goldenAngle = 2.399963f
    val ordered = nodeIds.sortedWith(
        compareByDescending<String> { degrees.getValue(it) }.thenBy { it }
    )
    // index 0 = 自己，固定在原点
    val x = FloatArray(n + 1)
    val y = FloatArray(n + 1)
    val indexOf = HashMap<String, Int>(n * 2)
    ordered.forEachIndexed { i, id ->
        val angle = i * goldenAngle
        val radius = c * sqrt(i + rankOffset)
        indexOf[id] = i + 1
        x[i + 1] = radius * cos(angle)
        y[i + 1] = radius * sin(angle)
    }

    resolveCollisions(x, y, desiredSpacing * 0.95f, pinned = 0)

    // 距离参考环：半径恰好包住"共同好友数 ≥ 刻度"的人群
    val ringDegrees = listOf(maxDegree, maxDegree / 2, maxDegree / 5)
        .filter { it > 0 }
        .distinct()
    val guideRings = ringDegrees.map { degree ->
        val enclosed = degrees.values.count { it >= degree }
        EgoGuideRing(c * sqrt(enclosed + rankOffset), degree)
    }

    var minX = 0f
    var maxX = 0f
    var minY = 0f
    var maxY = 0f
    for (i in 0..n) {
        if (x[i] < minX) minX = x[i]
        if (x[i] > maxX) maxX = x[i]
        if (y[i] < minY) minY = y[i]
        if (y[i] > maxY) maxY = y[i]
    }
    val margin = desiredSpacing * EGO_MARGIN_FACTOR
    val positions = buildMap {
        put(selfId, Offset(-minX + margin, -minY + margin))
        for (id in nodeIds) {
            val i = indexOf.getValue(id)
            put(id, Offset(x[i] - minX + margin, y[i] - minY + margin))
        }
    }
    return ForceLayoutResult(
        positions = positions,
        layoutWidthPx = (maxX - minX) + margin * 2f,
        layoutHeightPx = (maxY - minY) + margin * 2f,
        guideRings = guideRings,
    )
}
