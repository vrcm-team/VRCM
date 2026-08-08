package io.github.vrcmteam.vrcm.storage

import io.github.vrcmteam.vrcm.storage.data.UserProfileCache
import kotlinx.serialization.json.Json

/** 用户资料缓存的读写边界；实现放在 Room，不占 Settings 的容量预算。 */
interface UserProfileCacheStore {
    suspend fun load(ownerUserId: String, userId: String): UserProfileCache?

    suspend fun save(ownerUserId: String, userId: String, cache: UserProfileCache)

    suspend fun clearOwner(ownerUserId: String)

    suspend fun clearAll()
}

internal class RoomUserProfileCacheStore(
    private val dao: UserProfileCacheRoomDao,
    private val nowMillis: () -> Long,
    private val retainedPerOwner: Int = DEFAULT_RETAINED_PER_OWNER,
) : UserProfileCacheStore {
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }

    override suspend fun load(ownerUserId: String, userId: String): UserProfileCache? =
        dao.payload(ownerUserId, userId)
            ?.let { raw -> runCatching { json.decodeFromString<UserProfileCache>(raw) }.getOrNull() }

    override suspend fun save(ownerUserId: String, userId: String, cache: UserProfileCache) {
        dao.save(
            entity = UserProfileCacheEntity(
                ownerUserId = ownerUserId,
                userId = userId,
                payload = json.encodeToString(UserProfileCache.serializer(), cache),
                updatedAtMillis = nowMillis(),
            ),
            limit = retainedPerOwner,
        )
    }

    override suspend fun clearOwner(ownerUserId: String) = dao.deleteOwner(ownerUserId)

    override suspend fun clearAll() = dao.deleteAll()

    private companion object {
        const val DEFAULT_RETAINED_PER_OWNER = 30
    }
}
