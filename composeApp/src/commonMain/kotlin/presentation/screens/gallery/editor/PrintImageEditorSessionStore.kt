package io.github.vrcmteam.vrcm.presentation.screens.gallery.editor

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

data class PrintImageEditorSession(
    val source: SelectedImage,
    val prepared: PreparedImage,
)

class PrintImageEditorSessionStore {
    private val sessions = mutableMapOf<String, PrintImageEditorSession>()
    private val completionChannel = Channel<Unit>(capacity = Channel.BUFFERED)
    private var nextSessionId = 0L

    val uploadCompletions: Flow<Unit> = completionChannel.receiveAsFlow()

    fun create(source: SelectedImage, prepared: PreparedImage): String {
        val id = "print-editor-${nextSessionId++}"
        sessions[id] = PrintImageEditorSession(source, prepared)
        return id
    }

    fun get(id: String): PrintImageEditorSession? = sessions[id]

    fun discard(id: String) {
        sessions.remove(id)
    }

    fun complete(id: String) {
        if (sessions.remove(id) != null) {
            completionChannel.trySend(Unit)
        }
    }
}
