package io.github.vrcmteam.vrcm.storage

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json

/**
 * 所有 JSON 缓存共用的一张表。
 *
 * 这些缓存原先分散在多个 Settings 域里：iOS 的 NSUserDefaults 对每个域有 ~4 MB
 * 硬限制，好友列表和用户资料很容易撞上（实测单条可达 1.5 MB），超出后会退化为
 * direct mode 并可能丢数据。
 *
 * 之所以按 JSON 存而不是把 DTO 拆成正式列：这里只需要按 key 存取、按时间淘汰，
 * 没有按字段查询的需求；而每个缓存对应的 API DTO 有 30 个上下的字段且会随接口
 * 演进，拆列意味着每次接口调整都要写 schema 迁移，JSON + ignoreUnknownKeys 则能
 * 直接吸收。
 */
@Entity(
    tableName = "cached_blobs",
    primaryKeys = ["scope", "cacheKey"],
    indices = [Index(value = ["scope", "groupKey", "updatedAtMillis"])],
)
internal data class CachedBlobEntity(
    val scope: String,
    val cacheKey: String,
    /**
     * 淘汰与整组删除的分组维度，通常是所属账号；没有账号维度的缓存（世界、群组）
     * 统一用空串。单列相等匹配，避免用 LIKE 前缀——VRChat 的 `usr_` 里的下划线
     * 正好是 LIKE 的单字符通配符。
     */
    val groupKey: String,
    val payload: String,
    val updatedAtMillis: Long,
)

@Dao
internal interface CachedBlobDao {
    @Query("SELECT payload FROM cached_blobs WHERE scope = :scope AND cacheKey = :cacheKey")
    suspend fun payload(scope: String, cacheKey: String): String?

    @Upsert
    suspend fun upsert(entity: CachedBlobEntity)

    @Query("DELETE FROM cached_blobs WHERE scope = :scope AND cacheKey = :cacheKey")
    suspend fun delete(scope: String, cacheKey: String)

    @Query("DELETE FROM cached_blobs WHERE scope = :scope AND groupKey = :groupKey")
    suspend fun deleteGroup(scope: String, groupKey: String)

    @Query("DELETE FROM cached_blobs WHERE scope = :scope")
    suspend fun deleteScope(scope: String)

    @Query("DELETE FROM cached_blobs")
    suspend fun deleteAll()

    /** 每个分组只保留最近写入的 [limit] 条，避免逛过的内容永久驻留。 */
    @Query(
        "DELETE FROM cached_blobs WHERE scope = :scope AND groupKey = :groupKey " +
            "AND cacheKey NOT IN (" +
            "SELECT cacheKey FROM cached_blobs WHERE scope = :scope AND groupKey = :groupKey " +
            "ORDER BY updatedAtMillis DESC LIMIT :limit)",
    )
    suspend fun trim(scope: String, groupKey: String, limit: Int)

    @Transaction
    suspend fun save(entity: CachedBlobEntity, limit: Int) {
        upsert(entity)
        trim(entity.scope, entity.groupKey, limit)
    }
}

/** 某一类缓存在 [CachedBlobDao] 上的读写视图；[prune] 用于写入前剪掉不渲染的重字段。 */
internal class JsonBlobCache<T>(
    private val dao: CachedBlobDao,
    private val scope: String,
    private val serializer: KSerializer<T>,
    private val nowMillis: () -> Long,
    private val retained: Int,
    private val prune: (T) -> T = { it },
) {
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }

    suspend fun load(cacheKey: String): T? = dao.payload(scope, cacheKey)
        ?.let { raw -> runCatching { json.decodeFromString(serializer, raw) }.getOrNull() }

    suspend fun save(cacheKey: String, value: T, groupKey: String = "") {
        dao.save(
            entity = CachedBlobEntity(
                scope = scope,
                cacheKey = cacheKey,
                groupKey = groupKey,
                payload = json.encodeToString(serializer, prune(value)),
                updatedAtMillis = nowMillis(),
            ),
            limit = retained,
        )
    }

    suspend fun delete(cacheKey: String) = dao.delete(scope, cacheKey)

    suspend fun deleteGroup(groupKey: String) = dao.deleteGroup(scope, groupKey)

    suspend fun clear() = dao.deleteScope(scope)
}

internal object CacheScopes {
    const val USER_PROFILE = "user_profile"
    const val FRIEND_LIST = "friend_list"
    const val FRIEND_NETWORK = "friend_network"
    const val WORLD_PROFILE = "world_profile"
    const val GROUP_PROFILE = "group_profile"
}
