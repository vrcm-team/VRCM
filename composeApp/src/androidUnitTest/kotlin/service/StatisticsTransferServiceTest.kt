package io.github.vrcmteam.vrcm.service

import android.content.ContentProvider
import android.content.ContentValues
import android.content.pm.ProviderInfo
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import androidx.room.Room
import io.github.vrcmteam.vrcm.core.shared.SharedFlowCentre
import io.github.vrcmteam.vrcm.service.data.AccountDto
import io.github.vrcmteam.vrcm.storage.FriendActivitySummaryEntity
import io.github.vrcmteam.vrcm.storage.RoomFriendActivityStore
import io.github.vrcmteam.vrcm.storage.StatisticsArchive
import io.github.vrcmteam.vrcm.storage.StatisticsSnapshot
import io.github.vrcmteam.vrcm.storage.VrcmDatabase
import io.github.vrcmteam.vrcm.storage.statisticsSQLiteDriver
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.manualFileKitCoreInitialization
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowContentResolver
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24, 35])
class StatisticsTransferServiceTest {
    @Test
    fun importsDocumentWithKnownSizeWithoutStoragePermission() = withTransfer { service, store, document ->
        assertImport(service, store, document)
    }

    @Test
    fun importsDocumentWithUnknownSize() = withTransfer { service, store, document ->
        document.reportedSize = -1L
        assertImport(service, store, document)
    }

    @Test
    fun importsDocumentWithNullSize() = withTransfer { service, store, document ->
        document.reportedSize = null
        assertImport(service, store, document)
    }

    @Test
    fun importsDocumentWithoutSizeColumn() = withTransfer { service, store, document ->
        document.includeSize = false
        assertImport(service, store, document)
    }

    @Test
    fun exportsToDocumentAndImportsItBack() = withTransfer { service, store, document ->
        val session = checkNotNull(SharedFlowCentre.currentSession.value).token
        service.export(session, snapshot(), PlatformFile(document.uri))
        assertImport(service, store, document)
    }

    private suspend fun assertImport(
        service: StatisticsTransferService,
        store: RoomFriendActivityStore,
        document: StatisticsDocumentProvider,
    ) {
        val session = checkNotNull(SharedFlowCentre.currentSession.value).token
        val prepared = service.prepareImport(session, PlatformFile(document.uri))
        assertEquals(snapshot(), prepared.snapshot)
        assertTrue(store.statisticsSnapshot(OWNER).isEmpty)
        repeat(2) { service.import(prepared) }
        assertEquals(snapshot(), store.statisticsSnapshot(OWNER))
        val temporaryDirectory = File(RuntimeEnvironment.getApplication().cacheDir, "statistics-transfer")
        assertTrue(temporaryDirectory.listFiles().orEmpty().isEmpty())
    }

    private fun withTransfer(
        block: suspend (StatisticsTransferService, RoomFriendActivityStore, StatisticsDocumentProvider) -> Unit,
    ) = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        FileKit.manualFileKitCoreInitialization(context)
        val database = Room.inMemoryDatabaseBuilder<VrcmDatabase>(context)
            .setDriver(statisticsSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            .build()
        val file = File.createTempFile("statistics-document-", ".sqlite3", context.cacheDir)
        try {
            StatisticsArchive(statisticsSQLiteDriver()).write(file.absolutePath, snapshot())
            val document = StatisticsDocumentProvider(file)
            document.attachInfo(context, ProviderInfo().apply { authority = document.uri.authority })
            ShadowContentResolver.registerProviderInternal(document.uri.authority, document)
            SharedFlowCentre.emitAuthenticated(AccountDto(userId = OWNER))
            val store = RoomFriendActivityStore(database.friendActivityDao())
            block(StatisticsTransferService(store), store, document)
        } finally {
            SharedFlowCentre.emitLogout()
            database.close()
            file.delete()
        }
    }

    private fun snapshot() = StatisticsSnapshot(
        ownerUserId = OWNER,
        summaries = listOf(FriendActivitySummaryEntity(
            ownerUserId = OWNER,
            friendUserId = "usr_friend",
            displayName = "Friend",
            profileImageUrl = "",
            meetingCount = 3,
            togetherDurationMillis = 60_000L,
        )),
    )

    private class StatisticsDocumentProvider(private val file: File) : ContentProvider() {
        val uri: Uri = Uri.parse("content://vrcm.statistics.test/document/backup")
        var reportedSize: Long? = file.length()
        var includeSize = true

        override fun onCreate() = true
        override fun getType(uri: Uri) = "application/octet-stream"
        override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor =
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.parseMode(mode))

        override fun query(
            uri: Uri,
            projection: Array<out String>?,
            selection: String?,
            selectionArgs: Array<out String>?,
            sortOrder: String?,
        ): Cursor = if (includeSize) {
            MatrixCursor(arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)).apply {
                addRow(arrayOf<Any?>(file.name, reportedSize))
            }
        } else {
            MatrixCursor(arrayOf(OpenableColumns.DISPLAY_NAME)).apply { addRow(arrayOf(file.name)) }
        }

        override fun insert(uri: Uri, values: ContentValues?): Uri? = error("Unsupported")
        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = error("Unsupported")
        override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int =
            error("Unsupported")
    }

    private companion object {
        const val OWNER = "usr_owner"
    }
}
