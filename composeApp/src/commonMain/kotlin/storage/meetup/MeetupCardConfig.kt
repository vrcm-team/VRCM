package io.github.vrcmteam.vrcm.storage.meetup

import kotlinx.serialization.Serializable

/**
 * 配置结构版本。不兼容改动时 +1；[MeetupCardConfigDao] 会把旧版本配置视为不存在，
 * 由用户重新配置，而不是让缺失字段静默回落默认值。
 *
 * 2：二维码类型由单选 `qrLinkType` 改为多选 `qrLinkTypes`，并新增横竖屏独立版式、
 *    横屏独立照片与 `configured` 首配标记。
 */
const val MEETUP_CARD_SCHEMA_VERSION = 2

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

/**
 * 二维码链接类型：通用 VRChat 主页任何设备可扫；
 * VRCM 直达 scheme 在装有 VRCM 的设备上必定跳应用内用户页。
 */
@Serializable
enum class MeetupQrLinkType {
    VrchatWeb,
    VrcmDeepLink,
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
    /**
     * 用户是否真正完成过首次配置。仅在用户提交编辑或换图时置为 true；
     * [defaultMeetupCardConfig] 建档与后台刷新都不算，否则"打开过编辑页"
     * 就会被当成配置完成，下次长按直接进展示页。
     */
    val configured: Boolean = false,
    /** 竖屏版式；[landscapeTemplate] 为空时横屏也用它。 */
    val template: MeetupCardTemplate = MeetupCardTemplate.InfoBar,
    /** 横屏独立版式；为 null 表示跟随竖屏版式。 */
    val landscapeTemplate: MeetupCardTemplate? = null,
    val accentArgb: Long = 0xFF3F8CFF,
    val scrimAlpha: Float = 0.36f,
    val showAvatar: Boolean = false,
    val showPronouns: Boolean = false,
    val showLanguages: Boolean = false,
    val showStatus: Boolean = false,
    val showStatusDescription: Boolean = false,
    val showShortText: Boolean = false,
    val showQrCode: Boolean = false,
    /** 同时展示的二维码链接类型，可多选；为空时按通用主页处理。 */
    val qrLinkTypes: List<MeetupQrLinkType> = listOf(MeetupQrLinkType.VrchatWeb),
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

/** 指定方向实际使用的版式。 */
fun MeetupCardConfig.templateFor(orientation: MeetupOrientation): MeetupCardTemplate =
    when (orientation) {
        MeetupOrientation.Portrait -> template
        MeetupOrientation.Landscape -> landscapeTemplate ?: template
    }

/** 去重且非空的二维码类型列表；配置为空时回退通用主页。 */
fun MeetupCardConfig.resolvedQrLinkTypes(): List<MeetupQrLinkType> =
    qrLinkTypes.distinct().ifEmpty { listOf(MeetupQrLinkType.VrchatWeb) }
