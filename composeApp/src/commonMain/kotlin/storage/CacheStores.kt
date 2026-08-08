package io.github.vrcmteam.vrcm.storage

import io.github.vrcmteam.vrcm.storage.data.FriendListCache
import io.github.vrcmteam.vrcm.storage.data.FriendNetworkCache
import io.github.vrcmteam.vrcm.storage.data.GroupProfileCache
import io.github.vrcmteam.vrcm.storage.data.UserProfileCache
import io.github.vrcmteam.vrcm.storage.data.WorldProfileCache

/** 资料页缓存：按“当前账号 × 被查看用户”存取。 */
interface UserProfileCacheStore {
    suspend fun load(ownerUserId: String, userId: String): UserProfileCache?

    suspend fun save(ownerUserId: String, userId: String, cache: UserProfileCache)

    suspend fun clearOwner(ownerUserId: String)

    suspend fun clearAll()
}

/** 好友列表缓存：按账号存取。 */
interface FriendListCacheStore {
    suspend fun load(userId: String): FriendListCache?

    suspend fun save(userId: String, cache: FriendListCache)

    suspend fun clear(userId: String)

    suspend fun clearAll()
}

/** 好友关系网缓存：按账号存取。 */
interface FriendNetworkCacheStore {
    suspend fun load(userId: String): FriendNetworkCache?

    suspend fun save(cache: FriendNetworkCache)

    suspend fun clear(userId: String)
}

/** 世界资料缓存：按世界 ID 存取，全账号共享。 */
interface WorldProfileCacheStore {
    suspend fun load(worldId: String): WorldProfileCache?

    suspend fun save(cache: WorldProfileCache)

    suspend fun clearAll()
}

/** 群组资料缓存：按群组 ID 存取，全账号共享。 */
interface GroupProfileCacheStore {
    suspend fun load(groupId: String): GroupProfileCache?

    suspend fun save(cache: GroupProfileCache)

    suspend fun clearAll()
}

internal class RoomUserProfileCacheStore(
    dao: CachedBlobDao,
    nowMillis: () -> Long,
    retained: Int = 30,
) : UserProfileCacheStore {
    private val cache = JsonBlobCache(
        dao = dao,
        scope = CacheScopes.USER_PROFILE,
        serializer = UserProfileCache.serializer(),
        nowMillis = nowMillis,
        retained = retained,
        prune = { profile ->
            profile.copy(
                createdWorlds = profile.createdWorlds.map { it.prunedForListCache() },
                favoritedWorlds = profile.favoritedWorlds.map { group ->
                    group.copy(worlds = group.worlds.map { it.prunedForCache() })
                },
            )
        },
    )

    override suspend fun load(ownerUserId: String, userId: String): UserProfileCache? =
        cache.load(key(ownerUserId, userId))

    override suspend fun save(ownerUserId: String, userId: String, cache: UserProfileCache) =
        this.cache.save(key(ownerUserId, userId), cache, groupKey = ownerUserId)

    override suspend fun clearOwner(ownerUserId: String) = cache.deleteGroup(ownerUserId)

    override suspend fun clearAll() = cache.clear()

    private fun key(ownerUserId: String, userId: String) = "$ownerUserId|$userId"
}

internal class RoomFriendListCacheStore(
    dao: CachedBlobDao,
    nowMillis: () -> Long,
    retained: Int = 8,
) : FriendListCacheStore {
    private val cache = JsonBlobCache(
        dao = dao,
        scope = CacheScopes.FRIEND_LIST,
        serializer = FriendListCache.serializer(),
        nowMillis = nowMillis,
        retained = retained,
    )

    override suspend fun load(userId: String): FriendListCache? = cache.load(userId)

    override suspend fun save(userId: String, cache: FriendListCache) = this.cache.save(userId, cache)

    override suspend fun clear(userId: String) = cache.delete(userId)

    override suspend fun clearAll() = cache.clear()
}

internal class RoomFriendNetworkCacheStore(
    dao: CachedBlobDao,
    nowMillis: () -> Long,
    retained: Int = 8,
) : FriendNetworkCacheStore {
    private val cache = JsonBlobCache(
        dao = dao,
        scope = CacheScopes.FRIEND_NETWORK,
        serializer = FriendNetworkCache.serializer(),
        nowMillis = nowMillis,
        retained = retained,
    )

    override suspend fun load(userId: String): FriendNetworkCache? = cache.load(userId)

    override suspend fun save(cache: FriendNetworkCache) = this.cache.save(cache.userId, cache)

    override suspend fun clear(userId: String) = cache.delete(userId)
}

internal class RoomWorldProfileCacheStore(
    dao: CachedBlobDao,
    nowMillis: () -> Long,
    retained: Int = 60,
) : WorldProfileCacheStore {
    private val cache = JsonBlobCache(
        dao = dao,
        scope = CacheScopes.WORLD_PROFILE,
        serializer = WorldProfileCache.serializer(),
        nowMillis = nowMillis,
        retained = retained,
        prune = { it.copy(world = it.world.prunedForProfileCache()) },
    )

    override suspend fun load(worldId: String): WorldProfileCache? = cache.load(worldId)

    override suspend fun save(cache: WorldProfileCache) = this.cache.save(cache.world.id, cache)

    override suspend fun clearAll() = cache.clear()
}

internal class RoomGroupProfileCacheStore(
    dao: CachedBlobDao,
    nowMillis: () -> Long,
    retained: Int = 60,
) : GroupProfileCacheStore {
    private val cache = JsonBlobCache(
        dao = dao,
        scope = CacheScopes.GROUP_PROFILE,
        serializer = GroupProfileCache.serializer(),
        nowMillis = nowMillis,
        retained = retained,
    )

    override suspend fun load(groupId: String): GroupProfileCache? = cache.load(groupId)

    override suspend fun save(cache: GroupProfileCache) = this.cache.save(cache.group.id, cache)

    override suspend fun clearAll() = cache.clear()
}
