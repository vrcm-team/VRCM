package io.github.vrcmteam.vrcm.core.algorithms

import androidx.compose.ui.geometry.Offset
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

data class ForceLayoutResult(
    val positions: Map<String, Offset>,
    val layoutWidthPx: Float,
    val layoutHeightPx: Float,
    // 自我中心视图的距离参考环（以 self 位置为圆心）
    val guideRings: List<EgoGuideRing> = emptyList(),
)

data class EgoGuideRing(
    val radiusPx: Float,
    val mutualCount: Int,
)

private const val FORCE_ITERATIONS = 300
private const val MIN_ITERATIONS = 30
private const val GRAVITY = 0.06f
private const val THETA_SQ = 0.64f
private const val EXACT_REPULSION_LIMIT = 128
private const val MAX_COLLISION_ITERATIONS = 64
private const val COLLISION_ROUNDING_MARGIN = 1.00001f
private const val MAX_DEPTH = 24
private const val INTRA_COMMUNITY_ATTRACTION = 1.3f
private const val CROSS_COMMUNITY_ATTRACTION = 0.25f
// 骑墙者显著倾向圈的跨圈边：引力加成，把人拉到两圈走廊
private const val LEAN_ATTRACTION = 0.5f
// 核心向心：按圈内度数加权拉向社区质心，核心居中、边缘在外
private const val CENTRIPETAL = 0.12f

/**
 * 力导向布局算法（简化版 ForceAtlas2）
 *
 * - 斥力：节点数超过 [EXACT_REPULSION_LIMIT] 时用 Barnes-Hut 四叉树近似，复杂度 O(n log n)
 * - 孤立节点（无边）不参与模拟，直接环形排列在主图外围
 * - 模拟结束后做碰撞消除，保证任意两节点中心距不小于 desiredSpacing * 0.95
 * - 画布尺寸按最终内容包围盒加边距得出
 *
 * @param nodeIds 节点 ID 列表
 * @param edges 邻接表，key 为节点 ID，value 为邻居节点 ID 列表
 * @param desiredSpacing 节点间期望间距（像素）
 * @param communities 节点所属社区（可选）：同社区的边引力增强、跨社区减弱，让圈子在空间上分离
 * @param leans 骑墙者的显著倾向圈（节点 ID → 至多 Top2 社区）：指向这些圈的跨圈边引力加成
 * @return 布局结果，包含每个节点的位置和画布尺寸
 */
fun computeForceLayout(
    nodeIds: List<String>,
    edges: Map<String, List<String>>,
    desiredSpacing: Float,
    communities: Map<String, Int> = emptyMap(),
    leans: Map<String, Set<Int>> = emptyMap(),
): ForceLayoutResult {
    val n = nodeIds.size
    if (n == 0) return ForceLayoutResult(emptyMap(), 0f, 0f)

    val idx = nodeIds.mapIndexed { i, id -> id to i }.toMap()

    // 去重的无向边列表（成对存放索引）及每条边的引力权重
    val pairList = ArrayList<Int>()
    val weightList = ArrayList<Float>()
    val seenPairs = HashSet<Long>()
    val degree = IntArray(n)
    nodeIds.forEachIndexed { i, id ->
        for (other in edges[id].orEmpty()) {
            val j = idx[other] ?: continue
            if (i == j) continue
            val a = min(i, j)
            val b = max(i, j)
            if (seenPairs.add((a.toLong() shl 32) or b.toLong())) {
                pairList.add(a)
                pairList.add(b)
                degree[a]++
                degree[b]++
                weightList.add(
                    if (communities.isEmpty()) {
                        1f
                    } else {
                        val idA = nodeIds[a]
                        val idB = nodeIds[b]
                        val ca = communities[idA]
                        val cb = communities[idB]
                        when {
                            ca != null && ca == cb -> INTRA_COMMUNITY_ATTRACTION
                            // 任一端把对端社区列为显著倾向 → 加成
                            (cb != null && leans[idA]?.contains(cb) == true) ||
                                (ca != null && leans[idB]?.contains(ca) == true) -> LEAN_ATTRACTION
                            else -> CROSS_COMMUNITY_ATTRACTION
                        }
                    }
                )
            }
        }
    }

    val connected = (0 until n).filter { degree[it] > 0 }
    val isolated = (0 until n).filter { degree[it] == 0 }

    val x = FloatArray(n)
    val y = FloatArray(n)
    val rng = Random(42)

    val m = connected.size
    if (m > 0) {
        // 压缩到连通子集上模拟
        val compact = IntArray(n) { -1 }
        connected.forEachIndexed { ci, oi -> compact[oi] = ci }
        val sx = FloatArray(m)
        val sy = FloatArray(m)
        val spread = desiredSpacing * sqrt(m.toFloat()) * 0.5f
        for (i in 0 until m) {
            val angle = rng.nextFloat() * 2f * PI.toFloat()
            val r = sqrt(rng.nextFloat()) * spread
            sx[i] = r * cos(angle)
            sy[i] = r * sin(angle)
        }
        val edgePairs = IntArray(pairList.size)
        for (e in pairList.indices step 2) {
            edgePairs[e] = compact[pairList[e]]
            edgePairs[e + 1] = compact[pairList[e + 1]]
        }
        val edgeWeights = weightList.toFloatArray()

        // 核心向心的准备：社区稠密索引 + 按圈内度数归一化的权重
        val commDense = mutableMapOf<Int, Int>()
        val simComm = IntArray(m) { -1 }
        connected.forEachIndexed { ci, oi ->
            val comm = communities[nodeIds[oi]]
            if (comm != null && comm >= 0) {
                simComm[ci] = commDense.getOrPut(comm) { commDense.size }
            }
        }
        val centripetalWeight = FloatArray(m)
        if (commDense.isNotEmpty()) {
            val internalDegree = IntArray(m)
            var e2 = 0
            while (e2 < edgePairs.size) {
                val i = edgePairs[e2]
                val j = edgePairs[e2 + 1]
                e2 += 2
                if (simComm[i] >= 0 && simComm[i] == simComm[j]) {
                    internalDegree[i]++
                    internalDegree[j]++
                }
            }
            val maxDegreePerComm = IntArray(commDense.size)
            for (i in 0 until m) {
                val c = simComm[i]
                if (c >= 0 && internalDegree[i] > maxDegreePerComm[c]) maxDegreePerComm[c] = internalDegree[i]
            }
            for (i in 0 until m) {
                val c = simComm[i]
                if (c >= 0 && maxDegreePerComm[c] > 0) {
                    centripetalWeight[i] = internalDegree[i].toFloat() / maxDegreePerComm[c]
                }
            }
        }
        runForceSimulation(sx, sy, edgePairs, edgeWeights, desiredSpacing, simComm, centripetalWeight, commDense.size)
        connected.forEachIndexed { ci, oi ->
            x[oi] = sx[ci]
            y[oi] = sy[ci]
        }
    }

    if (isolated.isNotEmpty()) {
        placeIsolatedOnRings(x, y, connected, isolated, desiredSpacing)
    }

    resolveCollisions(x, y, desiredSpacing * 0.95f)

    // 按内容包围盒平移并留出边距
    var minX = x[0]
    var maxX = x[0]
    var minY = y[0]
    var maxY = y[0]
    for (i in 1 until n) {
        if (x[i] < minX) minX = x[i]
        if (x[i] > maxX) maxX = x[i]
        if (y[i] < minY) minY = y[i]
        if (y[i] > maxY) maxY = y[i]
    }
    val margin = desiredSpacing
    val positions = nodeIds.mapIndexed { i, id ->
        id to Offset(x[i] - minX + margin, y[i] - minY + margin)
    }.toMap()
    return ForceLayoutResult(
        positions = positions,
        layoutWidthPx = (maxX - minX) + margin * 2f,
        layoutHeightPx = (maxY - minY) + margin * 2f,
    )
}

private fun runForceSimulation(
    x: FloatArray,
    y: FloatArray,
    edgePairs: IntArray,
    edgeWeights: FloatArray,
    desiredSpacing: Float,
    simComm: IntArray = IntArray(0),
    centripetalWeight: FloatArray = FloatArray(0),
    commCount: Int = 0,
) {
    val n = x.size
    val k = desiredSpacing * 1.5f
    val k2 = k * k
    val fx = FloatArray(n)
    val fy = FloatArray(n)
    val tree = if (n > EXACT_REPULSION_LIMIT) BarnesHutTree(n) else null
    val convergenceThreshold = max(0.5f, k * 0.005f)
    val commSumX = FloatArray(commCount)
    val commSumY = FloatArray(commCount)
    val commSize = IntArray(commCount)

    for (iter in 0 until FORCE_ITERATIONS) {
        fx.fill(0f)
        fy.fill(0f)

        // 斥力
        if (tree != null) {
            tree.build(x, y, n)
            for (i in 0 until n) {
                tree.accumulateRepulsion(x[i], y[i], k2)
                fx[i] += tree.outFx
                fy[i] += tree.outFy
            }
        } else {
            for (i in 0 until n) {
                for (j in i + 1 until n) {
                    val dx = x[i] - x[j]
                    val dy = y[i] - y[j]
                    val dist = sqrt(dx * dx + dy * dy).coerceAtLeast(1f)
                    val force = k2 / dist
                    val fxij = (dx / dist) * force
                    val fyij = (dy / dist) * force
                    fx[i] += fxij; fy[i] += fyij
                    fx[j] -= fxij; fy[j] -= fyij
                }
            }
        }

        // 引力：连接的节点之间（按社区权重缩放）
        var e = 0
        while (e < edgePairs.size) {
            val i = edgePairs[e]
            val j = edgePairs[e + 1]
            e += 2
            val dx = x[j] - x[i]
            val dy = y[j] - y[i]
            val dist = sqrt(dx * dx + dy * dy).coerceAtLeast(1f)
            val force = (dist * dist) / k * edgeWeights[(e - 2) shr 1]
            val fxij = (dx / dist) * force
            val fyij = (dy / dist) * force
            fx[i] += fxij; fy[i] += fyij
            fx[j] -= fxij; fy[j] -= fyij
        }

        // 重力：向中心（原点）轻微吸引，只为拉住游离的连通分量
        for (i in 0 until n) {
            fx[i] -= x[i] * GRAVITY
            fy[i] -= y[i] * GRAVITY
        }

        // 核心向心：按圈内度数加权拉向本社区质心，核心居中、骑墙者留在边缘
        if (commCount > 0) {
            commSumX.fill(0f)
            commSumY.fill(0f)
            commSize.fill(0)
            for (i in 0 until n) {
                val c = simComm[i]
                if (c >= 0) {
                    commSumX[c] += x[i]
                    commSumY[c] += y[i]
                    commSize[c]++
                }
            }
            for (i in 0 until n) {
                val c = simComm[i]
                if (c >= 0 && commSize[c] > 0 && centripetalWeight[i] > 0f) {
                    val pull = CENTRIPETAL * centripetalWeight[i]
                    fx[i] += (commSumX[c] / commSize[c] - x[i]) * pull
                    fy[i] += (commSumY[c] / commSize[c] - y[i]) * pull
                }
            }
        }

        // 更新位置（带速度限制和降温）
        val maxDisplacement = k * (1f - iter.toFloat() / FORCE_ITERATIONS)
        var maxMove = 0f
        for (i in 0 until n) {
            val fMag = sqrt(fx[i] * fx[i] + fy[i] * fy[i]).coerceAtLeast(0.01f)
            val disp = min(fMag, maxDisplacement)
            val moveX = (fx[i] / fMag) * disp
            val moveY = (fy[i] / fMag) * disp
            x[i] += moveX
            y[i] += moveY
            val moveMag = sqrt(moveX * moveX + moveY * moveY)
            if (moveMag > maxMove) maxMove = moveMag
        }

        // 提前收敛检查：跳过前几轮不稳定振荡
        if (iter >= MIN_ITERATIONS && maxMove < convergenceThreshold) break
    }
}

/**
 * 孤立节点按同心环排在主图外围
 */
private fun placeIsolatedOnRings(
    x: FloatArray,
    y: FloatArray,
    connected: List<Int>,
    isolated: List<Int>,
    desiredSpacing: Float,
) {
    var maxRadius = 0f
    for (i in connected) {
        val r = sqrt(x[i] * x[i] + y[i] * y[i])
        if (r > maxRadius) maxRadius = r
    }
    var placed = 0
    var radius: Float
    if (connected.isEmpty()) {
        val first = isolated[0]
        x[first] = 0f
        y[first] = 0f
        placed = 1
        radius = desiredSpacing
    } else {
        radius = maxRadius + desiredSpacing * 1.5f
    }
    while (placed < isolated.size) {
        val capacity = max(6, floor(2f * PI.toFloat() * radius / desiredSpacing).toInt())
        val count = min(capacity, isolated.size - placed)
        val step = 2f * PI.toFloat() / count
        val phase = radius / desiredSpacing * 0.5f
        for (j in 0 until count) {
            val angle = phase + j * step
            val i = isolated[placed + j]
            x[i] = radius * cos(angle)
            y[i] = radius * sin(angle)
        }
        placed += count
        radius += desiredSpacing
    }
}

/**
 * 碰撞消除：把中心距小于 minDist 的节点对沿连线推开
 * @param pinned 该下标的节点位置固定不动（自我中心视图的中心），重叠时只推开对方
 */
internal fun resolveCollisions(x: FloatArray, y: FloatArray, minDist: Float, pinned: Int = -1) {
    val n = x.size
    if (n < 2 || minDist <= 0f) return
    val targetDist = minDist * COLLISION_ROUNDING_MARGIN
    val minDistSq = targetDist * targetDist
    val invCell = 1f / targetDist
    repeat(MAX_COLLISION_ITERATIONS) {
        var moved = false
        val grid = HashMap<Long, MutableList<Int>>(n * 2)
        for (i in 0 until n) {
            val key = cellKey(floor(x[i] * invCell).toInt(), floor(y[i] * invCell).toInt())
            grid.getOrPut(key) { mutableListOf() }.add(i)
        }
        for (i in 0 until n) {
            val cx = floor(x[i] * invCell).toInt()
            val cy = floor(y[i] * invCell).toInt()
            for (gx in cx - 1..cx + 1) {
                for (gy in cy - 1..cy + 1) {
                    val cellNodes = grid[cellKey(gx, gy)] ?: continue
                    for (j in cellNodes) {
                        if (j <= i) continue
                        val dx = x[j] - x[i]
                        val dy = y[j] - y[i]
                        val distSq = dx * dx + dy * dy
                        if (distSq >= minDistSq) continue
                        val dist = sqrt(distSq)
                        val ux: Float
                        val uy: Float
                        if (dist < 1e-3f) {
                            // 完全重合时按索引给一个确定性方向
                            val angle = (i * 0.618034f + j * 0.381966f) * 2f * PI.toFloat()
                            ux = cos(angle)
                            uy = sin(angle)
                        } else {
                            ux = dx / dist
                            uy = dy / dist
                        }
                        val overlap = targetDist - min(dist, targetDist)
                        when {
                            i == pinned -> {
                                x[j] += ux * overlap
                                y[j] += uy * overlap
                            }
                            j == pinned -> {
                                x[i] -= ux * overlap
                                y[i] -= uy * overlap
                            }
                            else -> {
                                x[i] -= ux * overlap / 2f
                                y[i] -= uy * overlap / 2f
                                x[j] += ux * overlap / 2f
                                y[j] += uy * overlap / 2f
                            }
                        }
                        moved = true
                    }
                }
            }
        }
        if (!moved) return
    }
    if (hasCollisions(x, y, targetDist)) {
        arrangeCollisionFallback(x, y, targetDist, pinned)
    }
}

private fun cellKey(gx: Int, gy: Int): Long = (gx.toLong() shl 32) or (gy.toLong() and 0xffffffffL)

private fun hasCollisions(x: FloatArray, y: FloatArray, minDist: Float): Boolean {
    val minDistSq = minDist * minDist
    val invCell = 1f / minDist
    val grid = HashMap<Long, MutableList<Int>>(x.size * 2)
    for (i in x.indices) {
        val cx = floor(x[i] * invCell).toInt()
        val cy = floor(y[i] * invCell).toInt()
        for (gx in cx - 1..cx + 1) {
            for (gy in cy - 1..cy + 1) {
                for (j in grid[cellKey(gx, gy)].orEmpty()) {
                    val dx = x[j] - x[i]
                    val dy = y[j] - y[i]
                    if (dx * dx + dy * dy < minDistSq) return true
                }
            }
        }
        grid.getOrPut(cellKey(cx, cy)) { mutableListOf() }.add(i)
    }
    return false
}

/**
 * 极端退化输入无法在迭代上限内收敛时，保留无冲突节点原位，只把仍冲突的
 * 节点移动到其原位附近最近的规则格点。pinned 会最先占位，因此始终保持原位。
 */
private fun arrangeCollisionFallback(
    x: FloatArray,
    y: FloatArray,
    minDist: Float,
    pinned: Int,
) {
    val step = minDist * 1.001f
    val minDistSq = minDist * minDist
    val invCell = 1f / minDist
    val originalX = x.copyOf()
    val originalY = y.copyOf()
    val grid = HashMap<Long, MutableList<Int>>(x.size * 2)
    val order = if (pinned in x.indices) {
        listOf(pinned) + x.indices.filter { it != pinned }
    } else {
        x.indices.toList()
    }

    fun isFree(px: Float, py: Float): Boolean {
        val cx = floor(px * invCell).toInt()
        val cy = floor(py * invCell).toInt()
        for (gx in cx - 1..cx + 1) {
            for (gy in cy - 1..cy + 1) {
                for (j in grid[cellKey(gx, gy)].orEmpty()) {
                    val dx = x[j] - px
                    val dy = y[j] - py
                    if (dx * dx + dy * dy < minDistSq) return false
                }
            }
        }
        return true
    }

    fun occupy(i: Int, px: Float, py: Float) {
        x[i] = px
        y[i] = py
        val cx = floor(px * invCell).toInt()
        val cy = floor(py * invCell).toInt()
        grid.getOrPut(cellKey(cx, cy)) { mutableListOf() }.add(i)
    }

    for (i in order) {
        val originX = originalX[i]
        val originY = originalY[i]
        if (isFree(originX, originY)) {
            occupy(i, originX, originY)
            continue
        }

        var placed = false
        fun tryCandidate(gx: Int, gy: Int) {
            if (placed) return
            val candidateX = originX + gx * step
            val candidateY = originY + gy * step
            if (isFree(candidateX, candidateY)) {
                occupy(i, candidateX, candidateY)
                placed = true
            }
        }

        var radius = 1
        while (!placed && radius <= x.size + 1) {
            for (gx in -radius..radius) {
                tryCandidate(gx, -radius)
                tryCandidate(gx, radius)
            }
            for (gy in -radius + 1 until radius) {
                tryCandidate(-radius, gy)
                tryCandidate(radius, gy)
            }
            radius++
        }
        if (!placed) {
            arrangeGlobalCollisionGrid(x, y, minDist, pinned)
            return
        }
    }
}

private fun arrangeGlobalCollisionGrid(x: FloatArray, y: FloatArray, minDist: Float, pinned: Int) {
    val step = minDist * 1.001f
    if (pinned in x.indices) {
        val centerX = x[pinned]
        val centerY = y[pinned]
        val movable = x.indices.filter { it != pinned }
        var next = 0
        var radius = 1
        fun place(gx: Int, gy: Int) {
            if (next >= movable.size) return
            val i = movable[next++]
            x[i] = centerX + gx * step
            y[i] = centerY + gy * step
        }
        while (next < movable.size) {
            for (gx in -radius..radius) {
                place(gx, -radius)
                place(gx, radius)
            }
            for (gy in -radius + 1 until radius) {
                place(-radius, gy)
                place(radius, gy)
            }
            radius++
        }
        return
    }
    val centerX = x.average().toFloat()
    val centerY = y.average().toFloat()
    val columns = kotlin.math.ceil(sqrt(x.size.toFloat())).toInt()
    val rows = (x.size + columns - 1) / columns
    val startX = centerX - (columns - 1) * step / 2f
    val startY = centerY - (rows - 1) * step / 2f
    for (i in x.indices) {
        x[i] = startX + (i % columns) * step
        y[i] = startY + (i / columns) * step
    }
}

/**
 * Barnes-Hut 四叉树（数组实现，可跨迭代复用）
 * 远处的节点簇按质心近似计算斥力
 */
internal class BarnesHutTree(bodyCount: Int) {
    private var cap = max(64, bodyCount * 4)
    private var childBase = IntArray(cap)
    private var mass = FloatArray(cap)
    private var comX = FloatArray(cap)
    private var comY = FloatArray(cap)
    private var nodeCount = 0
    private var rootMinX = 0f
    private var rootMinY = 0f
    private var rootSize = 1f
    private val stackNode = IntArray(4 * MAX_DEPTH + 8)
    private val stackSize = FloatArray(4 * MAX_DEPTH + 8)
    private val stackMinX = FloatArray(4 * MAX_DEPTH + 8)
    private val stackMinY = FloatArray(4 * MAX_DEPTH + 8)

    var outFx = 0f
        private set
    var outFy = 0f
        private set

    fun build(x: FloatArray, y: FloatArray, n: Int) {
        var minX = x[0]
        var maxX = x[0]
        var minY = y[0]
        var maxY = y[0]
        for (i in 1 until n) {
            if (x[i] < minX) minX = x[i]
            if (x[i] > maxX) maxX = x[i]
            if (y[i] < minY) minY = y[i]
            if (y[i] > maxY) maxY = y[i]
        }
        rootMinX = minX
        rootMinY = minY
        rootSize = max(maxX - minX, maxY - minY).coerceAtLeast(1f) * 1.0001f
        nodeCount = 1
        childBase[0] = -1
        mass[0] = 0f
        for (i in 0 until n) insert(x[i], y[i])
    }

    private fun grow(needed: Int) {
        if (needed <= cap) return
        var newCap = cap
        while (newCap < needed) newCap *= 2
        childBase = childBase.copyOf(newCap)
        mass = mass.copyOf(newCap)
        comX = comX.copyOf(newCap)
        comY = comY.copyOf(newCap)
        cap = newCap
    }

    private fun insert(px: Float, py: Float) {
        var node = 0
        var minX = rootMinX
        var minY = rootMinY
        var size = rootSize
        var depth = 0
        while (true) {
            if (mass[node] == 0f) {
                mass[node] = 1f
                comX[node] = px
                comY[node] = py
                return
            }
            if (childBase[node] == -1) {
                val sameSpot = abs(comX[node] - px) + abs(comY[node] - py) < 1e-3f
                if (depth >= MAX_DEPTH || sameSpot) {
                    // 深度到顶或同点：聚合成伪节点
                    val total = mass[node] + 1f
                    comX[node] += (px - comX[node]) / total
                    comY[node] += (py - comY[node]) / total
                    mass[node] = total
                    return
                }
                // 分裂叶子：原有实体下沉到子节点
                grow(nodeCount + 4)
                val base = nodeCount
                nodeCount += 4
                for (c in base until base + 4) {
                    childBase[c] = -1
                    mass[c] = 0f
                }
                childBase[node] = base
                val half = size / 2f
                val q = quadrant(comX[node], comY[node], minX, minY, half)
                val child = base + q
                mass[child] = mass[node]
                comX[child] = comX[node]
                comY[child] = comY[node]
            }
            val total = mass[node] + 1f
            comX[node] += (px - comX[node]) / total
            comY[node] += (py - comY[node]) / total
            mass[node] = total
            val half = size / 2f
            val q = quadrant(px, py, minX, minY, half)
            node = childBase[node] + q
            if (q and 1 != 0) minX += half
            if (q and 2 != 0) minY += half
            size = half
            depth++
        }
    }

    private fun quadrant(px: Float, py: Float, minX: Float, minY: Float, half: Float): Int =
        (if (px >= minX + half) 1 else 0) or (if (py >= minY + half) 2 else 0)

    fun accumulateRepulsion(px: Float, py: Float, k2: Float) {
        var fxAcc = 0f
        var fyAcc = 0f
        var sp = 0
        stackNode[sp] = 0
        stackSize[sp] = rootSize
        stackMinX[sp] = rootMinX
        stackMinY[sp] = rootMinY
        sp++
        while (sp > 0) {
            sp--
            val node = stackNode[sp]
            val size = stackSize[sp]
            val minX = stackMinX[sp]
            val minY = stackMinY[sp]
            val m = mass[node]
            if (m == 0f) continue
            var sourceMass = m
            var sourceX = comX[node]
            var sourceY = comY[node]
            val containsQuery = px >= minX && px < minX + size && py >= minY && py < minY + size
            val base = childBase[node]
            if (base == -1 && containsQuery) {
                sourceMass -= 1f
                if (sourceMass <= 0f) continue
                sourceX = (sourceX * m - px) / sourceMass
                sourceY = (sourceY * m - py) / sourceMass
            }
            val dx = px - sourceX
            val dy = py - sourceY
            val distSq = dx * dx + dy * dy
            if (base == -1 || (!containsQuery && size * size < THETA_SQ * distSq)) {
                if (distSq < 1e-6f) continue
                val dist = sqrt(distSq)
                val force = sourceMass * k2 / dist
                fxAcc += (dx / dist) * force
                fyAcc += (dy / dist) * force
            } else {
                val half = size / 2f
                for (quadrant in 0 until 4) {
                    stackNode[sp] = base + quadrant
                    stackSize[sp] = half
                    stackMinX[sp] = minX + if (quadrant and 1 != 0) half else 0f
                    stackMinY[sp] = minY + if (quadrant and 2 != 0) half else 0f
                    sp++
                }
            }
        }
        outFx = fxAcc
        outFy = fyAcc
    }
}
