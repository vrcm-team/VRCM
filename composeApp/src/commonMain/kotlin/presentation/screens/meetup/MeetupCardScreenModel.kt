package io.github.vrcmteam.vrcm.presentation.screens.meetup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.vrcmteam.vrcm.presentation.screens.meetup.editor.MeetupPhotoSessionStore
import io.github.vrcmteam.vrcm.service.meetup.MeetupCardRepository
import io.github.vrcmteam.vrcm.service.meetup.MeetupCardState
import io.github.vrcmteam.vrcm.service.meetup.MeetupPhotoTarget
import io.github.vrcmteam.vrcm.storage.meetup.MeetupCardConfig
import io.github.vrcmteam.vrcm.storage.meetup.MeetupCardTemplate
import io.github.vrcmteam.vrcm.storage.meetup.MeetupCrop
import io.github.vrcmteam.vrcm.storage.meetup.MeetupOrientation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 每个 code point 计 1，surrogate pair 不重复计数。 */
internal fun countCodePoints(value: String): Int = value.count { !it.isLowSurrogate() }

internal const val MEETUP_SHORT_TEXT_MAX_CODE_POINTS = 80

/**
 * 身份卡的唯一 ViewModel：对外只暴露一份 [state]；
 * 离散编辑立即持久化，裁剪草稿在手势结束时提交。
 */
class MeetupCardScreenModel(
    private val ownerUserId: String,
    private val repository: MeetupCardRepository,
    private val photoSessions: MeetupPhotoSessionStore,
) : ViewModel() {

    private data class LocalEditorState(
        val orientation: MeetupOrientation = MeetupOrientation.Portrait,
        val cropDraft: MeetupCrop? = null,
        val savingPhoto: Boolean = false,
        val editorError: MeetupEditorError? = null,
    )

    private val local = MutableStateFlow(LocalEditorState())
    private val consumedPhotoSessions = mutableSetOf<String>()

    val state: StateFlow<MeetupCardUiState> = combine(
        repository.observe(ownerUserId),
        local,
    ) { repo, local -> repo.toUiState(local) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = repository.observe(ownerUserId).value.toUiState(local.value),
        )

    init {
        viewModelScope.launch {
            // 首次进入建档（已有配置时是幂等读取），随后离线内容已可展示，仅后台刷新。
            runUpdate { repository.ensureDefault(ownerUserId) }
            repository.refresh(ownerUserId)
        }
    }

    fun setTemplate(value: MeetupCardTemplate) = persist { it.copy(template = value) }

    fun setAccentArgb(value: Long) = persist { it.copy(accentArgb = value) }

    fun setScrimAlpha(value: Float) = persist { it.copy(scrimAlpha = value.coerceIn(0f, 1f)) }

    fun setShowAvatar(value: Boolean) = persist { it.copy(showAvatar = value) }

    fun setShowPronouns(value: Boolean) = persist { it.copy(showPronouns = value) }

    fun setShowLanguages(value: Boolean) = persist { it.copy(showLanguages = value) }

    fun setShowStatus(value: Boolean) = persist { it.copy(showStatus = value) }

    fun setShowStatusDescription(value: Boolean) = persist { it.copy(showStatusDescription = value) }

    fun setShowShortText(value: Boolean) = persist { it.copy(showShortText = value) }

    fun setShowQrCode(value: Boolean) = persist { it.copy(showQrCode = value) }

    fun setShowIconFrame(value: Boolean) = persist { it.copy(showIconFrame = value) }

    fun setShowProfileEffect(value: Boolean) = persist { it.copy(showProfileEffect = value) }

    fun setShowNameplateEffect(value: Boolean) = persist { it.copy(showNameplateEffect = value) }

    fun setShortText(value: String) {
        if (countCodePoints(value) > MEETUP_SHORT_TEXT_MAX_CODE_POINTS) {
            local.update { it.copy(editorError = MeetupEditorError.ShortTextTooLong) }
            return
        }
        persist { it.copy(shortText = value) }
    }

    fun setOrientation(value: MeetupOrientation) {
        local.update { current ->
            if (current.orientation == value) {
                current
            } else {
                // 方向切换丢弃未提交草稿，避免把 A 方向的手势值写进 B 方向。
                current.copy(orientation = value, cropDraft = null)
            }
        }
    }

    fun updateCropDraft(value: MeetupCrop) {
        local.update { it.copy(cropDraft = value) }
    }

    fun commitCrop() {
        val draft = local.value.cropDraft ?: return
        val orientation = local.value.orientation
        local.update { it.copy(cropDraft = null) }
        persist { config ->
            when (orientation) {
                MeetupOrientation.Portrait -> config.copy(portraitCrop = draft)
                MeetupOrientation.Landscape -> config.copy(landscapeCrop = draft)
            }
        }
    }

    /** 页面离开前提交仍在草稿中的裁剪。 */
    fun flushDrafts() = commitCrop()

    fun confirmPhoto(sessionId: String, target: MeetupPhotoTarget = MeetupPhotoTarget.Both) {
        if (local.value.savingPhoto || sessionId in consumedPhotoSessions) return
        val session = photoSessions.get(sessionId) ?: return
        consumedPhotoSessions += sessionId
        local.update { it.copy(savingPhoto = true) }
        viewModelScope.launch {
            try {
                val result = repository.replacePhoto(ownerUserId, session.currentCandidate(), target)
                result.fold(
                    onSuccess = {
                        photoSessions.complete(sessionId)
                        local.update { it.copy(savingPhoto = false, editorError = null) }
                    },
                    onFailure = { reason ->
                        // 会话保留，允许用户在失败后直接重试确认。
                        consumedPhotoSessions -= sessionId
                        local.update {
                            it.copy(
                                savingPhoto = false,
                                editorError = MeetupEditorError.PhotoFailed(reason),
                            )
                        }
                    },
                )
            } catch (cancelled: CancellationException) {
                consumedPhotoSessions -= sessionId
                throw cancelled
            }
        }
    }

    fun discardPhotoSession(sessionId: String) {
        photoSessions.discard(sessionId)
        consumedPhotoSessions -= sessionId
    }

    fun refresh() {
        repository.refresh(ownerUserId)
    }

    fun clearError() {
        local.update { it.copy(editorError = null) }
    }

    private fun persist(transform: (MeetupCardConfig) -> MeetupCardConfig) {
        viewModelScope.launch {
            runUpdate { repository.update(ownerUserId, transform) }
        }
    }

    private suspend fun runUpdate(block: suspend () -> Unit) {
        try {
            block()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            local.update { it.copy(editorError = MeetupEditorError.SaveFailed(error)) }
        }
    }

    private fun MeetupCardState.toUiState(local: LocalEditorState) = MeetupCardUiState(
        ownerUserId = ownerUserId,
        displayName = config.profile.displayName.ifBlank { ownerUserId },
        config = config,
        photoModel = photoModel,
        landscapePhotoModel = landscapePhotoModel,
        decorations = decorations,
        orientation = local.orientation,
        cropDraft = local.cropDraft,
        blockingLoading = false,
        refreshing = refreshing,
        savingPhoto = local.savingPhoto,
        editorError = local.editorError,
        refreshResult = lastRefresh,
    )
}
