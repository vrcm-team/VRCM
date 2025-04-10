package io.github.vrcmteam.vrcm.presentation.compoments

/**
 * 搜索标签类型枚举
 */
enum class SearchTabType(val index: Int) {
    USER(0),    // 用户
    WORLD(1),   // 世界
    MODEL(2),   // 模型
    GROUP(3);   // 群组
    
    companion object {
        fun fromIndex(index: Int): SearchTabType {
            return entries.firstOrNull { it.index == index } ?: USER
        }
    }
} 