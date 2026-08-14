package io.github.vrcmteam.vrcm.presentation.screens.gallery.editor

import io.github.vrcmteam.vrcm.network.api.avatars.data.AvatarData
import io.github.vrcmteam.vrcm.network.api.files.data.FileTagType
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update

data class PrintImageEditorSession(
    val source: SelectedImage,
    val prepared: PreparedImage,
    val target: ImageEditorTarget,
)

class PrintImageEditorSessionStore {
    private val sessions = mutableMapOf<String, PrintImageEditorSession>()
    private val completionChannel = Channel<Unit>(capacity = Channel.BUFFERED)
    private val galleryCompletionChannel = Channel<FileTagType>(capacity = Channel.BUFFERED)
    private val _avatarCoverUpdates = MutableStateFlow<Map<String, AvatarData>>(emptyMap())
    private var nextSessionId = 0L

    val uploadCompletions: Flow<Unit> = completionChannel.receiveAsFlow()
    val galleryUploadCompletions: Flow<FileTagType> = galleryCompletionChannel.receiveAsFlow()
    val avatarCoverUpdates: StateFlow<Map<String, AvatarData>> =
        _avatarCoverUpdates.asStateFlow()

    fun create(
        source: SelectedImage,
        prepared: PreparedImage,
        target: ImageEditorTarget = ImageEditorTarget.Print,
    ): String {
        val id = "image-editor-${nextSessionId++}"
        sessions[id] = PrintImageEditorSession(source, prepared, target)
        return id
    }

    fun get(id: String): PrintImageEditorSession? = sessions[id]

    fun discard(id: String) {
        sessions.remove(id)
    }

    fun complete(id: String) {
        complete(id, ImageEditorSubmission.Print)
    }

    fun complete(id: String, submission: ImageEditorSubmission) {
        val session = sessions.remove(id) ?: return
        when (submission) {
            ImageEditorSubmission.Print -> {
                check(session.target == ImageEditorTarget.Print)
                completionChannel.trySend(Unit)
            }
            is ImageEditorSubmission.AvatarCover -> {
                val target = session.target as? ImageEditorTarget.AvatarCover
                    ?: error("Avatar cover result completed a non-avatar editor session")
                check(target.avatarId == submission.avatar.id)
                _avatarCoverUpdates.update { it + (submission.avatar.id to submission.avatar) }
            }
            is ImageEditorSubmission.Gallery -> {
                val target = session.target as? ImageEditorTarget.Gallery
                    ?: error("Gallery result completed a non-gallery editor session")
                check(target.tagType == submission.tagType)
                galleryCompletionChannel.trySend(submission.tagType)
            }
        }
    }

    fun consumeAvatarCoverUpdate(avatarId: String): AvatarData? {
        val update = _avatarCoverUpdates.value[avatarId] ?: return null
        _avatarCoverUpdates.update { it - avatarId }
        return update
    }
}
