package io.github.vrcmteam.vrcm.storage

import io.github.vrcmteam.vrcm.network.api.worlds.data.FavoritedWorld
import io.github.vrcmteam.vrcm.network.api.worlds.data.WorldData

/**
 * 世界 DTO 里最占体积的是 `unityPackages`（每个平台构建一条，含长 CDN 链接）、
 * `instances` 和 `udonProducts`，而资料页与世界详情页都不渲染它们——一个高产
 * 作者的创建世界列表因此能撑到 1.5 MB。缓存写入前剪掉，需要时由网络补齐。
 *
 * 模型的 `unityPackages` 不剪：`AvatarProfileVo.platformInfos` 会渲染它，
 * 且每条只有平台、Unity 版本、性能评级三个短字段，体积可忽略。
 */
/** 用于资料页的世界卡片：连房间列表一起剪，那里只显示名称与缩略图。 */
internal fun WorldData.prunedForListCache(): WorldData = copy(
    unityPackages = emptyList(),
    udonProducts = emptyList(),
    instances = null,
)

/**
 * 用于世界详情页缓存：保留 `instances`——详情页据此渲染房间列表，
 * 并用它判断缓存是否够新，剪掉会让这份缓存每次都失效。
 */
internal fun WorldData.prunedForProfileCache(): WorldData = copy(
    unityPackages = emptyList(),
    udonProducts = emptyList(),
)

internal fun FavoritedWorld.prunedForCache(): FavoritedWorld = copy(
    unityPackages = emptyList(),
    udonProducts = emptyList(),
)
