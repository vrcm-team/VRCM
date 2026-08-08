package io.github.vrcmteam.vrcm.storage

import androidx.room.Entity
import androidx.room.Index

/**
 * 用户资料缓存。整份缓存以 JSON 存放在 [payload]：
 * 这里只需要"能按账号与用户存取、能按时间淘汰"，没有按字段查询的需求，
 * 拆成正式列反而要为每个 API DTO 维护一套表结构。
 *
 * 之所以放 Room 而不是 Settings：单条可达 1.5 MB（作者的创建世界/模型列表），
 * 而 iOS 的 NSUserDefaults 对整个 domain 有 ~4 MB 硬限制，超出会退化并可能丢数据。
 */
@Entity(
    tableName = "user_profile_caches",
    primaryKeys = ["ownerUserId", "userId"],
    indices = [Index(value = ["ownerUserId", "updatedAtMillis"])],
)
internal data class UserProfileCacheEntity(
    val ownerUserId: String,
    val userId: String,
    val payload: String,
    val updatedAtMillis: Long,
)
