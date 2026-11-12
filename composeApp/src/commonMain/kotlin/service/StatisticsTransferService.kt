package io.github.vrcmteam.vrcm.service

import io.github.vrcmteam.vrcm.core.shared.AccountSessionToken
import io.github.vrcmteam.vrcm.core.shared.SharedFlowCentre
import io.github.vrcmteam.vrcm.storage.FriendActivityWriteToken
import io.github.vrcmteam.vrcm.storage.RoomFriendActivityStore
import io.github.vrcmteam.vrcm.storage.StatisticsArchive
import io.github.vrcmteam.vrcm.storage.StatisticsSnapshot
import io.github.vrcmteam.vrcm.storage.StatisticsTransferException
import io.github.vrcmteam.vrcm.storage.StatisticsTransferFailure
import io.github.vrcmteam.vrcm.storage.statisticsSQLiteDriver
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.cacheDir
import io.github.vinceglb.filekit.copyTo
import io.github.vinceglb.filekit.path
import io.github.vinceglb.filekit.write
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import okio.buffer
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

internal data class PreparedStatisticsImport(
    val session: AccountSessionToken,
    val writeToken: FriendActivityWriteToken,
    val snapshot: StatisticsSnapshot,
)

/** 文件 I/O 在 IO dispatcher 执行；解析成功并经用户确认后才在单一事务中合并。 */
internal class StatisticsTransferService(private val store: RoomFriendActivityStore) {
    private val archive = StatisticsArchive(statisticsSQLiteDriver())
    private val fileSystem = FileSystem.SYSTEM

    suspend fun prepareExport(session: AccountSessionToken): StatisticsSnapshot = withContext(Dispatchers.IO) {
        requireCurrent(session)
        val snapshot = store.statisticsSnapshot(session.userId)
        if (snapshot.isEmpty) throw StatisticsTransferException(StatisticsTransferFailure.Empty)
        requireCurrent(session)
        snapshot
    }

    suspend fun export(session: AccountSessionToken, snapshot: StatisticsSnapshot, destination: PlatformFile) = withContext(Dispatchers.IO) {
        requireCurrent(session)
        require(snapshot.ownerUserId == session.userId)
        withTemporaryDatabase { path ->
            archive.write(path.toString(), snapshot)
            requireCurrent(session)
            destination.write(PlatformFile(path.toString()))
        }
    }

    suspend fun prepareImport(session: AccountSessionToken, source: PlatformFile): PreparedStatisticsImport =
        withContext(Dispatchers.IO) {
            requireCurrent(session)
            val writeToken = store.activateAccount(session.userId)
            val snapshot = withTemporaryDatabase { path ->
                source.copyTo(PlatformFile(path.toString()))
                val validHeader = fileSystem.source(path).buffer().use { input ->
                    input.request(16L) && input.readUtf8(16L) == "SQLite format 3\u0000"
                }
                if (!validHeader) throw StatisticsTransferException(StatisticsTransferFailure.InvalidFile)
                archive.read(path.toString(), session.userId)
            }
            requireCurrent(session)
            PreparedStatisticsImport(session, writeToken, snapshot)
        }

    suspend fun import(prepared: PreparedStatisticsImport) = withContext(Dispatchers.IO) {
        requireCurrent(prepared.session)
        if (!store.mergeStatistics(prepared.writeToken, prepared.snapshot)) {
            throw StatisticsTransferException(StatisticsTransferFailure.SessionChanged)
        }
    }

    private fun requireCurrent(session: AccountSessionToken) {
        if (!SharedFlowCentre.isCurrentSession(session)) {
            throw StatisticsTransferException(StatisticsTransferFailure.SessionChanged)
        }
    }

    @OptIn(ExperimentalUuidApi::class)
    private suspend fun <T> withTemporaryDatabase(block: suspend (Path) -> T): T {
        val directory = FileKit.cacheDir.path.toPath() / "statistics-transfer"
        fileSystem.createDirectories(directory)
        val path = directory / "${Uuid.random()}.sqlite3"
        return try {
            block(path)
        } finally {
            // 连接已关闭，DELETE journal 模式确保单个文件就能完整迁移。
            listOf(path, "${path}-journal".toPath(), "${path}-wal".toPath(), "${path}-shm".toPath())
                .forEach { fileSystem.delete(it, mustExist = false) }
        }
    }
}
