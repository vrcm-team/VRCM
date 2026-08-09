package io.github.vrcmteam.vrcm.presentation.screens.gallery

/** Gallery 单选结果；只携带标识与 URL，不携带图片字节。 */
data class GallerySelection(
    val fileId: String,
    val fileName: String,
    val extension: String,
    val imageUrl: String,
)

/**
 * 在发起方与 Gallery 选择页之间以内存会话传递单选结果，
 * 路由只携带 session ID。[consume] 一次性取走结果并移除会话。
 */
class GallerySelectionSessionStore {
    private sealed interface Entry {
        data object Pending : Entry
        data class Completed(val selection: GallerySelection) : Entry
    }

    private val sessions = mutableMapOf<String, Entry>()
    private var nextSessionId = 0L

    fun create(): String {
        val id = "gallery-selection-${nextSessionId++}"
        sessions[id] = Entry.Pending
        return id
    }

    /** 只接受未完成会话的第一个结果；未知或已完成的会话返回 false。 */
    fun complete(id: String, selection: GallerySelection): Boolean {
        if (sessions[id] != Entry.Pending) return false
        sessions[id] = Entry.Completed(selection)
        return true
    }

    fun cancel(id: String) {
        sessions.remove(id)
    }

    /** 会话仍在等待选择结果时为 true；用于区分"未完成"与"已取消/未知"。 */
    fun isPending(id: String): Boolean = sessions[id] == Entry.Pending

    /** 只取走已完成的结果；等待中的会话保持原样，允许稍后再次消费。 */
    fun consume(id: String): GallerySelection? {
        val entry = sessions[id] ?: return null
        if (entry == Entry.Pending) return null
        sessions.remove(id)
        return (entry as Entry.Completed).selection
    }
}
