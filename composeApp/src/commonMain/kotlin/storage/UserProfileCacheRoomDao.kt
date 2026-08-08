package io.github.vrcmteam.vrcm.storage

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert

@Dao
internal interface UserProfileCacheRoomDao {
    @Query(
        "SELECT payload FROM user_profile_caches " +
            "WHERE ownerUserId = :ownerUserId AND userId = :userId",
    )
    suspend fun payload(ownerUserId: String, userId: String): String?

    @Upsert
    suspend fun upsert(entity: UserProfileCacheEntity)

    @Query("DELETE FROM user_profile_caches WHERE ownerUserId = :ownerUserId")
    suspend fun deleteOwner(ownerUserId: String)

    @Query("DELETE FROM user_profile_caches")
    suspend fun deleteAll()

    /** 每个账号只保留最近访问的 [limit] 份，避免逛过的每个人都永久驻留。 */
    @Query(
        "DELETE FROM user_profile_caches WHERE ownerUserId = :ownerUserId AND userId NOT IN (" +
            "SELECT userId FROM user_profile_caches WHERE ownerUserId = :ownerUserId " +
            "ORDER BY updatedAtMillis DESC LIMIT :limit)",
    )
    suspend fun trim(ownerUserId: String, limit: Int)

    @Transaction
    suspend fun save(entity: UserProfileCacheEntity, limit: Int) {
        upsert(entity)
        trim(entity.ownerUserId, limit)
    }
}
