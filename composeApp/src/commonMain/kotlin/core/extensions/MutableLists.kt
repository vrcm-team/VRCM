package io.github.vrcmteam.vrcm.core.extensions

/**
 * 删除与给定条件匹配的第一个元素.
 * SnapshotStateList没有重写removeAll方法通过条件删除的方法，导致无法更新页面
 */
fun <T> MutableList<T>.removeFirst(predicate: (T) -> Boolean) {
    val index = indexOfFirst(predicate)
    if (index != -1) {
        removeAt(index)
    }
}

fun <T> MutableList<T>.removeIf(predicate: (T) -> Boolean) {
    for (i in lastIndex downTo 0) {
        if (predicate(get(i))) {
            removeAt(i)
        }
    }
}