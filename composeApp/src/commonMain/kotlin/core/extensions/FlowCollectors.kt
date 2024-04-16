package io.github.vrcmteam.vrcm.core.extensions

import kotlinx.coroutines.flow.FlowCollector

suspend fun<T> FlowCollector<List<T>>.fetchDataList(
    offset: Int = 0,
    n: Int = 50,
    doFetch : suspend (currentOffset:Int, n:Int) -> List<T>
){
    var count = 0
    while (true) {
        val bodyList: List<T> = doFetch(offset + count * n, n)
        if (bodyList.isEmpty()) break
        this@fetchDataList.emit(bodyList)
        count++
    }
}
