package io.github.vrcmteam.vrcm.storage.meetup

import kotlinx.coroutines.test.runTest
import okio.ForwardingFileSystem
import okio.IOException
import okio.Path
import okio.Path.Companion.toPath
import okio.Sink
import okio.fakefilesystem.FakeFileSystem
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MeetupCardAssetStoreTest {
    @Test
    fun deletingSinglePhotoKeepsOtherAccountAssets() = runTest {
        val fileSystem = FakeFileSystem()
        val root = "/private/meetup".toPath()
        val store = MeetupCardAssetStore(fileSystem, root)
        val removed = store.writePhoto("usr_a", "removed".encodeToByteArray(), "jpg")
        val kept = store.writePhoto("usr_a", "kept".encodeToByteArray(), "png")
        val decoration = store.writeDecoration(
            "inv_keep",
            DecorationAssetType.Base,
            "decoration".encodeToByteArray(),
        )

        store.deletePhoto(removed)

        assertFailsWith<IOException> { store.read(removed) }
        assertContentEquals("kept".encodeToByteArray(), store.read(kept))
        assertContentEquals("decoration".encodeToByteArray(), store.read(decoration))
        fileSystem.checkNoOpenFiles()
    }

    @Test
    fun corruptContentAddressedPhotoIsReplacedByKnownGoodBytes() = runTest {
        val fileSystem = FakeFileSystem()
        val root = "/private/meetup".toPath()
        val store = MeetupCardAssetStore(fileSystem, root)
        val bytes = "correct".encodeToByteArray()
        val original = store.writePhoto("usr_a", bytes, "jpg")
        fileSystem.write(store.model(original).toPath()) { writeUtf8("corrupt") }

        val rewritten = store.writePhoto("usr_a", bytes, "jpg")

        assertEquals(original, rewritten)
        assertContentEquals(bytes, store.read(rewritten))
        fileSystem.checkNoOpenFiles()
    }

    @Test
    fun atomicMoveFailureAfterCompetitorPublishedSameContentIsIdempotentSuccess() = runTest {
        val fileSystem = FakeFileSystem()
        val root = "/private/meetup".toPath()
        val racingFileSystem = object : ForwardingFileSystem(fileSystem) {
            override fun atomicMove(source: Path, target: Path): Nothing {
                fileSystem.copy(source, target)
                throw IOException("target already exists")
            }
        }
        val store = MeetupCardAssetStore(racingFileSystem, root)
        val bytes = "same content".encodeToByteArray()

        val ref = store.writePhoto("usr_a", bytes, "jpg")

        assertContentEquals(bytes, store.read(ref))
        assertNoTemporaryFiles(fileSystem, root)
        fileSystem.checkNoOpenFiles()
    }

    @Test
    fun temporaryNameCollisionDoesNotDeleteFileOwnedByCompetitor() = runTest {
        val fileSystem = FakeFileSystem()
        val root = "/private/meetup".toPath()
        var competitorPath: Path? = null
        val racingFileSystem = object : ForwardingFileSystem(fileSystem) {
            override fun sink(file: Path, mustCreate: Boolean): Sink {
                if (mustCreate && file.name.endsWith(".tmp") && competitorPath == null) {
                    fileSystem.write(file, mustCreate = true) { writeUtf8("competitor") }
                    competitorPath = file
                }
                return super.sink(file, mustCreate)
            }
        }
        val store = MeetupCardAssetStore(racingFileSystem, root)
        val bytes = "photo".encodeToByteArray()

        val ref = store.writePhoto("usr_a", bytes, "jpg")

        val collision = checkNotNull(competitorPath)
        assertEquals("competitor", fileSystem.read(collision) { readUtf8() })
        assertContentEquals(bytes, store.read(ref))
        fileSystem.checkNoOpenFiles()
    }

    @Test
    fun temporaryCleanupFailureIsSuppressedByPrimaryWriteFailure() = runTest {
        val fileSystem = FakeFileSystem()
        val root = "/private/meetup".toPath()
        val failingFileSystem = object : ForwardingFileSystem(fileSystem) {
            override fun atomicMove(source: Path, target: Path): Nothing {
                throw IOException("move failed")
            }

            override fun delete(path: Path, mustExist: Boolean) {
                if (path.name.endsWith(".tmp")) throw IOException("cleanup failed")
                super.delete(path, mustExist)
            }
        }

        val failure = assertFailsWith<IOException> {
            MeetupCardAssetStore(failingFileSystem, root)
                .writePhoto("usr_a", "photo".encodeToByteArray(), "jpg")
        }

        assertEquals("move failed", failure.message)
        val suppressedFailures = generateSequence<Throwable>(failure) { it.cause }
            .flatMap { it.suppressedExceptions.asSequence() }
            .toList()
        assertTrue(
            suppressedFailures.any { it.message == "cleanup failed" },
            "Suppressed failures: ${suppressedFailures.map { it.message }}",
        )
        fileSystem.checkNoOpenFiles()
    }

    @Test
    fun failedWriteLeavesPreviousPhotoReadable() = runTest {
        val fileSystem = FakeFileSystem()
        val root = "/private/meetup".toPath()
        val store = MeetupCardAssetStore(fileSystem, root)
        val first = store.writePhoto("usr_a", "first".encodeToByteArray(), "jpg")
        val failingFileSystem = object : ForwardingFileSystem(fileSystem) {
            override fun sink(file: Path, mustCreate: Boolean): Sink {
                if (file.name.endsWith(".tmp")) throw IOException("disk full")
                return super.sink(file, mustCreate)
            }
        }

        assertFailsWith<IOException> {
            MeetupCardAssetStore(failingFileSystem, root)
                .writePhoto("usr_a", "second".encodeToByteArray(), "jpg")
        }

        assertContentEquals("first".encodeToByteArray(), store.read(first))
        assertNoTemporaryFiles(fileSystem, root)
        fileSystem.checkNoOpenFiles()
    }

    @Test
    fun deletingAccountKeepsSharedDecorationCache() = runTest {
        val fileSystem = FakeFileSystem()
        val root = "/private/meetup".toPath()
        val store = MeetupCardAssetStore(fileSystem, root)
        store.writePhoto("usr_a", byteArrayOf(1), "png")
        val decoration = store.writeDecoration(
            "inv_template",
            DecorationAssetType.Base,
            byteArrayOf(2),
        )

        store.deleteAccount("usr_a")

        assertFalse(fileSystem.exists(root / "accounts" / "usr_a"))
        assertContentEquals(byteArrayOf(2), store.read(decoration))
        fileSystem.checkNoOpenFiles()
    }

    @Test
    fun clearingDecorationCacheKeepsAccountPhotos() = runTest {
        val fileSystem = FakeFileSystem()
        val root = "/private/meetup".toPath()
        val store = MeetupCardAssetStore(fileSystem, root)
        val photo = store.writePhoto("usr_a", byteArrayOf(1), "png")
        val decoration = store.writeDecoration(
            "inv_template",
            DecorationAssetType.Base,
            byteArrayOf(2),
        )

        store.clearDecorationCache()

        assertContentEquals(byteArrayOf(1), store.read(photo))
        assertFailsWith<IOException> { store.read(decoration) }
        fileSystem.checkNoOpenFiles()
    }

    @Test
    fun failedDecorationReplacementLeavesPreviousVersionReadable() = runTest {
        val fileSystem = FakeFileSystem()
        val root = "/private/meetup".toPath()
        val store = MeetupCardAssetStore(fileSystem, root)
        val previous = store.writeDecoration(
            "inv_template",
            DecorationAssetType.MainAnimation,
            "old".encodeToByteArray(),
        )
        val failingFileSystem = object : ForwardingFileSystem(fileSystem) {
            override fun atomicMove(source: Path, target: Path): Nothing {
                if (target.toString().contains("/decorations/")) {
                    throw IOException("atomic move failed")
                }
                throw AssertionError("Unexpected atomic move target: $target")
            }
        }

        assertFailsWith<IOException> {
            MeetupCardAssetStore(failingFileSystem, root).writeDecoration(
                "inv_template",
                DecorationAssetType.MainAnimation,
                "new".encodeToByteArray(),
            )
        }

        assertContentEquals("old".encodeToByteArray(), store.read(previous))
        assertNoTemporaryFiles(fileSystem, root)
        fileSystem.checkNoOpenFiles()
    }

    @Test
    fun unsafeIdentifiersExtensionsAndReferencesCannotEscapeRoot() = runTest {
        val fileSystem = FakeFileSystem()
        val root = "/private/meetup".toPath()
        val sentinel = "/private/sentinel".toPath()
        fileSystem.createDirectories(sentinel.parent!!)
        fileSystem.write(sentinel) { writeUtf8("outside") }
        val store = MeetupCardAssetStore(fileSystem, root)

        for (ownerId in listOf("", ".", "..", "usr/a", "usr\\a", "usr a")) {
            assertFailsWith<IllegalArgumentException> {
                store.writePhoto(ownerId, byteArrayOf(1), "jpg")
            }
            // 非法 ID 不可能有已写入数据；deleteAccount 静默跳过，不中断账号移除流程。
            store.deleteAccount(ownerId)
        }
        assertTrue(fileSystem.exists(sentinel))
        for (templateId in listOf("", ".", "..", "inv/a", "inv\\a", "inv a")) {
            assertFailsWith<IllegalArgumentException> {
                store.writeDecoration(templateId, DecorationAssetType.Base, byteArrayOf(1))
            }
            assertFailsWith<IllegalArgumentException> {
                store.pruneDecorations(setOf("valid", templateId))
            }
        }
        for (extension in listOf("", "gif", "tar.gz", "../jpg", "\\jpg")) {
            assertFailsWith<IllegalArgumentException> {
                store.writePhoto("usr_a", byteArrayOf(1), extension)
            }
        }
        val normalized = store.writePhoto("usr_a", byteArrayOf(2), ".JPG")
        assertTrue(normalized.relativePath.endsWith(".jpg"))

        val maliciousRefs = listOf(
            MeetupAssetRef("../sentinel", "0".repeat(64)),
            MeetupAssetRef("/private/sentinel", "0".repeat(64)),
            MeetupAssetRef("accounts\\..\\sentinel", "0".repeat(64)),
            MeetupAssetRef("accounts/usr_a/photos/not-a-hash.jpg", "0".repeat(64)),
        )
        for (ref in maliciousRefs) {
            assertFailsWith<IllegalArgumentException> { store.model(ref) }
            assertFailsWith<IllegalArgumentException> { store.read(ref) }
        }

        assertTrue(fileSystem.exists(sentinel))
        assertTrue(fileSystem.read(sentinel) { readUtf8() } == "outside")
        fileSystem.checkNoOpenFiles()
    }

    @Test
    fun pruneDecorationsDeletesOnlyUnretainedTemplates() = runTest {
        val fileSystem = FakeFileSystem()
        val root = "/private/meetup".toPath()
        val store = MeetupCardAssetStore(fileSystem, root)
        val photo = store.writePhoto("usr_a", "photo".encodeToByteArray(), "webp")
        val retained = store.writeDecoration(
            "keep",
            DecorationAssetType.Base,
            "keep".encodeToByteArray(),
        )
        val removed = store.writeDecoration(
            "remove",
            DecorationAssetType.Base,
            "remove".encodeToByteArray(),
        )

        store.pruneDecorations(setOf("keep"))

        assertContentEquals("keep".encodeToByteArray(), store.read(retained))
        assertFailsWith<IOException> { store.read(removed) }
        assertContentEquals("photo".encodeToByteArray(), store.read(photo))
        fileSystem.checkNoOpenFiles()
    }

    private fun assertNoTemporaryFiles(fileSystem: FakeFileSystem, root: Path) {
        if (!fileSystem.exists(root)) return
        assertFalse(
            fileSystem.listRecursively(root).any { it.name.endsWith(".tmp") },
            "Temporary files must be cleaned up",
        )
    }
}
