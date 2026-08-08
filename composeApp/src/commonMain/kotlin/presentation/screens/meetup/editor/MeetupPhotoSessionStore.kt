package io.github.vrcmteam.vrcm.presentation.screens.meetup.editor

import androidx.compose.ui.graphics.ImageBitmap
import io.github.vrcmteam.vrcm.presentation.screens.gallery.editor.ImageSize
import io.github.vrcmteam.vrcm.presentation.screens.gallery.editor.releasePlatformImageBitmap
import io.github.vrcmteam.vrcm.service.meetup.MeetupPhotoCandidate
import io.github.vrcmteam.vrcm.storage.meetup.MeetupCrop
import io.github.vrcmteam.vrcm.storage.meetup.MeetupOrientation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 候选照片与仅供编辑预览使用的解码位图。 */
data class MeetupPreparedPhoto(
    val candidate: MeetupPhotoCandidate,
    val preview: ImageBitmap,
) {
    val originalSize: ImageSize get() = ImageSize(candidate.width, candidate.height)
}

/** 单张候选照片的裁剪编辑会话；两个方向的草稿互不影响。 */
class MeetupPhotoSession internal constructor(
    val id: String,
    val prepared: MeetupPreparedPhoto,
) {
    private val _portraitCrop = MutableStateFlow(prepared.candidate.portraitCrop)
    private val _landscapeCrop = MutableStateFlow(prepared.candidate.landscapeCrop)

    val portraitCrop: StateFlow<MeetupCrop> = _portraitCrop.asStateFlow()
    val landscapeCrop: StateFlow<MeetupCrop> = _landscapeCrop.asStateFlow()

    fun crop(orientation: MeetupOrientation): StateFlow<MeetupCrop> = when (orientation) {
        MeetupOrientation.Portrait -> portraitCrop
        MeetupOrientation.Landscape -> landscapeCrop
    }

    fun updateCrop(orientation: MeetupOrientation, crop: MeetupCrop) {
        when (orientation) {
            MeetupOrientation.Portrait -> _portraitCrop.value = crop
            MeetupOrientation.Landscape -> _landscapeCrop.value = crop
        }
    }

    /** 携带最新双向裁剪的候选；提交失败时会话仍然存活，可反复读取。 */
    fun currentCandidate(): MeetupPhotoCandidate = prepared.candidate.copy(
        portraitCrop = _portraitCrop.value,
        landscapeCrop = _landscapeCrop.value,
    )
}

/**
 * 以内存会话在编辑器与裁剪页之间传递候选照片，路由只携带 session ID。
 * [complete] 一次性取走结果；[discard] 与 [complete] 都会释放预览位图。
 */
class MeetupPhotoSessionStore(
    private val releasePreview: (ImageBitmap) -> Unit = ::releasePlatformImageBitmap,
) {
    private val sessions = mutableMapOf<String, MeetupPhotoSession>()
    private var nextSessionId = 0L

    fun create(prepared: MeetupPreparedPhoto): MeetupPhotoSession {
        val session = MeetupPhotoSession(
            id = "meetup-photo-${nextSessionId++}",
            prepared = prepared,
        )
        sessions[session.id] = session
        return session
    }

    fun get(id: String): MeetupPhotoSession? = sessions[id]

    fun complete(id: String): MeetupPhotoCandidate? {
        val session = sessions.remove(id) ?: return null
        val result = session.currentCandidate()
        releasePreview(session.prepared.preview)
        return result
    }

    fun discard(id: String) {
        val session = sessions.remove(id) ?: return
        releasePreview(session.prepared.preview)
    }
}
