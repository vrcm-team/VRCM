package io.github.vrcmteam.vrcm.core.algorithms

import androidx.compose.ui.geometry.Offset
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

private const val EGO_MARGIN_FACTOR = 1f

/**
 * 自我中心径向布局：自己固定在圆心，好友与自己的共同好友越多离圆心越近。
 *
 * - 半径按 sqrt(共同好友数/最大值) 反向映射，缓和头部差距
 * - 角度按社区分扇区（编号升序，「其他」在最后），扇区大小与成员数成正比，
 *   同一个圈子的人在同一方向上，径向读数与社区归属两个信息互不干扰
 * - 碰撞消除时圆心固定不动
 *
 * @param nodeIds 好友 ID 列表（不含自己）
 * @param edges 邻接表；每个好友的边数即其与用户的共同好友数
 * @param communities 社区划分（角度扇区用）
 * @param desiredSpacing 节点间期望间距（像素）
 * @param selfId 自己的 ID，会以圆心位置包含在返回的 positions 里
 */
fun computeEgoLayout(
    nodeIds: List<String>,
    edges: Map<String, List<String>>,
    communities: Map<String, Int>,
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
    val rMin = desiredSpacing * 1.8f
    val rMax = rMin + desiredSpacing * sqrt(n.toFloat()) * 1.05f

    fun radiusOf(degree: Int): Float {
        val t = 1f - sqrt(degree.toFloat() / maxDegree)
        return rMin + t * (rMax - rMin)
    }

    // 角度扇区：社区编号升序（人数降序的语义），「其他」压轴
    val sectorOrder = nodeIds
        .groupBy { communities[it] ?: Int.MAX_VALUE }
        .entries
        .sortedWith(compareBy { if (it.key < 0) Int.MAX_VALUE else it.key })

    // index 0 = 自己，固定在原点
    val x = FloatArray(n + 1)
    val y = FloatArray(n + 1)
    val indexOf = HashMap<String, Int>(n * 2)
    var sectorStart = 0f
    var next = 1
    for ((_, members) in sectorOrder) {
        val span = 2f * PI.toFloat() * members.size / n
        members.sorted().forEachIndexed { i, id ->
            val angle = sectorStart + (i + 0.5f) / members.size * span
            val radius = radiusOf(degrees.getValue(id))
            indexOf[id] = next
            x[next] = radius * cos(angle)
            y[next] = radius * sin(angle)
            next++
        }
        sectorStart += span
    }

    resolveCollisions(x, y, desiredSpacing * 0.95f, pinned = 0)

    // 距离参考环：最大值、一半、约五分之一
    val ringDegrees = listOf(maxDegree, maxDegree / 2, maxDegree / 5)
        .filter { it > 0 }
        .distinct()
    val guideRings = ringDegrees.map { EgoGuideRing(radiusOf(it), it) }

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
