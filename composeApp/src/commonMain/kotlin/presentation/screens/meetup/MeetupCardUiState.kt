package io.github.vrcmteam.vrcm.presentation.screens.meetup

import io.github.vrcmteam.vrcm.service.meetup.DecorationSlot
import io.github.vrcmteam.vrcm.service.meetup.MeetupRefreshResult
import io.github.vrcmteam.vrcm.service.meetup.ResolvedDecoration
import io.github.vrcmteam.vrcm.storage.meetup.MeetupCardConfig
import io.github.vrcmteam.vrcm.storage.meetup.MeetupCrop
import io.github.vrcmteam.vrcm.storage.meetup.MeetupOrientation

/** 身份卡展示页与编辑页共享的唯一 UI state。 */
data class MeetupCardUiState(
    val ownerUserId: String,
    val displayName: String,
    val config: MeetupCardConfig,
    val photoModel: String?,
    val decorations: Map<DecorationSlot, ResolvedDecoration>,
    val orientation: MeetupOrientation,
    /** 手势进行中的裁剪草稿；结束前只影响预览，不落盘。 */
    val cropDraft: MeetupCrop? = null,
    val blockingLoading: Boolean = false,
    val refreshing: Boolean = false,
    val savingPhoto: Boolean = false,
    val editorError: MeetupEditorError? = null,
    val refreshResult: MeetupRefreshResult = MeetupRefreshResult.NotStarted,
) {
    /** 预览与展示实际生效的裁剪：草稿优先于已保存值。 */
    val activeCrop: MeetupCrop
        get() = cropDraft ?: when (orientation) {
            MeetupOrientation.Portrait -> config.portraitCrop
            MeetupOrientation.Landscape -> config.landscapeCrop
        }
}

/** 编辑操作的可本地化失败状态。 */
sealed interface MeetupEditorError {
    data object ShortTextTooLong : MeetupEditorError
    data class PhotoFailed(val reason: Throwable) : MeetupEditorError
    data class SaveFailed(val reason: Throwable) : MeetupEditorError
}
