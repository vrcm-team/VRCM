package io.github.vrcmteam.vrcm.core.algorithms

/**
 * Louvain 社区检测算法
 * 通过迭代优化模块度来发现密集连接的子群
 *
 * @param adjacency 邻接表，key 为节点 ID，value 为邻居节点 ID 集合
 * @return 每个节点对应的社区 ID 映射
 */
fun louvainDetect(adjacency: Map<String, Set<String>>): Map<String, Int> {
    val nodeIds = adjacency.keys.toList()
    val n = nodeIds.size
    if (n == 0) return emptyMap()
    val m = adjacency.values.sumOf { it.size } / 2
    if (m == 0) return nodeIds.mapIndexed { i, id -> id to i }.toMap()

    val idx = nodeIds.mapIndexed { i, id -> id to i }.toMap()
    val degree = IntArray(n) { adjacency[nodeIds[it]]?.size ?: 0 }
    val comm = IntArray(n) { it } // 每个节点初始为独立社区
    val commTot = IntArray(n) { degree[it] } // 每个社区的度数总和

    // 计算节点到指定社区的边数
    fun edgesToComm(nodeIdx: Int, targetComm: Int): Int {
        var count = 0
        for (neighbor in adjacency[nodeIds[nodeIdx]].orEmpty()) {
            val j = idx[neighbor] ?: continue
            if (comm[j] == targetComm) count++
        }
        return count
    }

    // 迭代优化模块度
    var improved = true
    while (improved) {
        improved = false
        for (i in 0 until n) {
            val currentComm = comm[i]
            val ki = degree[i]
            if (ki == 0) continue

            // 收集邻居社区
            val neighborComms = mutableSetOf<Int>()
            for (neighbor in adjacency[nodeIds[i]].orEmpty()) {
                val j = idx[neighbor] ?: continue
                neighborComms.add(comm[j])
            }

            // 从当前社区移除
            commTot[currentComm] -= ki

            var bestComm = currentComm
            var bestGain = 0.0

            for (targetComm in neighborComms) {
                val kiIn = edgesToComm(i, targetComm)
                // 模块度增益公式
                val gain = kiIn.toDouble() / m -
                    (ki.toLong() * commTot[targetComm]).toDouble() / (2.0 * m * m)
                if (gain > bestGain) {
                    bestGain = gain
                    bestComm = targetComm
                }
            }

            // 移动到最优社区
            comm[i] = bestComm
            commTot[bestComm] += ki

            if (bestComm != currentComm) improved = true
        }
    }

    // 重新编号为连续整数
    val renumber = mutableMapOf<Int, Int>()
    var nextId = 0
    val result = mutableMapOf<String, Int>()
    for (i in 0 until n) {
        val c = comm[i]
        if (c !in renumber) renumber[c] = nextId++
        result[nodeIds[i]] = renumber[c]!!
    }
    return result
}
