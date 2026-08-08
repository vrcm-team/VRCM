package io.github.vrcmteam.vrcm.storage.meetup

import kotlinx.serialization.Serializable

/** Persisted decoration metadata and locally verified asset references. */
@Serializable
data class DecorationTemplateCache(
    val templateId: String,
    val mainAnimationUrl: String = "",
    val baseUrl: String = "",
    val mainAnimationAsset: MeetupAssetRef? = null,
    val baseAsset: MeetupAssetRef? = null,
    val gradientStart: String = "",
    val gradientEnd: String = "",
    /**
     * 上一次下载后解码失败的动画 URL。该平台的解码器对这份素材无能为力，
     * 记下来避免每次刷新都重下最多 20 MiB 再失败一次；URL 变了就重新尝试。
     */
    val failedMainAnimationUrl: String = "",
)
