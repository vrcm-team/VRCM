package io.github.vrcmteam.vrcm.storage.meetup

import kotlinx.serialization.Serializable

const val MEETUP_CARD_SCHEMA_VERSION = 1

/** 会面身份卡的内置版式。 */
@Serializable
enum class MeetupCardTemplate {
    InfoBar,
    Spotlight,
    SideTag,
}

/** 身份卡照片的来源类型。 */
@Serializable
enum class MeetupPhotoSource {
    ProfileBackground,
    LocalAlbum,
    VrchatGallery,
}

/** 身份卡导出与裁剪的画面方向。 */
@Serializable
enum class MeetupOrientation {
    Portrait,
    Landscape,
}

/** 单个画面方向下的照片裁剪参数。 */
@Serializable
data class MeetupCrop(
    val centerOffsetX: Float = 0f,
    val centerOffsetY: Float = 0f,
    val zoom: Float = 1f,
)

/** 托管在应用私有目录中的资源引用。 */
@Serializable
data class MeetupAssetRef(
    val relativePath: String,
    val sha256: String,
)

/** 身份卡照片及其可恢复来源信息。 */
@Serializable
data class MeetupPhoto(
    val source: MeetupPhotoSource = MeetupPhotoSource.ProfileBackground,
    val sourceId: String? = null,
    val sourceUrl: String? = null,
    val localAsset: MeetupAssetRef? = null,
    val width: Int = 0,
    val height: Int = 0,
)

/** 生成身份卡时使用的用户资料快照。 */
@Serializable
data class MeetupProfileSnapshot(
    val displayName: String,
    val avatarUrl: String = "",
    val pronouns: String = "",
    val languages: List<String> = emptyList(),
    val status: String = "",
    val statusDescription: String = "",
)

/** 生成身份卡时使用的外观资源快照。 */
@Serializable
data class MeetupAppearanceSnapshot(
    val iconFrameTemplateId: String = "",
    val profileEffectTemplateId: String = "",
    val nameplateEffectTemplateId: String = "",
)

/** 可按账号持久化并通过 [schemaVersion] 迁移的身份卡配置。 */
@Serializable
data class MeetupCardConfig(
    val schemaVersion: Int = MEETUP_CARD_SCHEMA_VERSION,
    val ownerUserId: String,
    val revision: Long = 0,
    val template: MeetupCardTemplate = MeetupCardTemplate.InfoBar,
    val accentArgb: Long = 0xFF3F8CFF,
    val scrimAlpha: Float = 0.36f,
    val showAvatar: Boolean = false,
    val showPronouns: Boolean = false,
    val showLanguages: Boolean = false,
    val showStatus: Boolean = false,
    val showStatusDescription: Boolean = false,
    val showShortText: Boolean = false,
    val showQrCode: Boolean = false,
    val showIconFrame: Boolean = true,
    val showProfileEffect: Boolean = true,
    val showNameplateEffect: Boolean = true,
    val shortText: String = "",
    val photo: MeetupPhoto = MeetupPhoto(),
    /** 横屏独立照片；为 null 时横屏沿用 [photo]。 */
    val landscapePhoto: MeetupPhoto? = null,
    val profileBackgroundFallback: MeetupPhoto? = null,
    val portraitCrop: MeetupCrop = MeetupCrop(),
    val landscapeCrop: MeetupCrop = MeetupCrop(),
    val profile: MeetupProfileSnapshot,
    val appearance: MeetupAppearanceSnapshot = MeetupAppearanceSnapshot(),
)

/** 创建仅包含账号标识和空资料快照的默认配置。 */
fun defaultMeetupCardConfig(ownerUserId: String): MeetupCardConfig =
    MeetupCardConfig(
        ownerUserId = ownerUserId,
        profile = MeetupProfileSnapshot(displayName = ""),
    )
