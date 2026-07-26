package io.github.vrcmteam.vrcm.presentation.screens.gallery

import io.github.vrcmteam.vrcm.network.api.files.data.FileData
import io.github.vrcmteam.vrcm.network.api.files.data.FileTagType
import io.github.vrcmteam.vrcm.network.api.prints.data.PrintData
import io.github.vrcmteam.vrcm.testing.MainDispatcherTest
import kotlinx.coroutines.Dispatchers
import org.koin.core.logger.EmptyLogger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GalleryScreenModelTest : MainDispatcherTest() {
    @Test
    fun gallerySelectionCannotDeleteAPrint() {
        val dataSource = FakeGalleryDataSource()
        val model = createModel(dataSource)

        model.toggleSelection(FileTagType.Gallery, "file_gallery")
        model.deleteSelectedPrints("deleting", "deleted", "failed")

        assertEquals(emptyList(), dataSource.deletedPrintIds)
        assertFalse(model.hasSelection(FileTagType.Print))
        assertTrue(model.hasSelection(FileTagType.Gallery))
    }

    @Test
    fun selectingAnotherTagAtomicallyReplacesThePreviousSelection() {
        val model = createModel()

        model.toggleSelection(FileTagType.Gallery, "file_gallery")
        model.toggleSelection(FileTagType.Print, "print_current")

        assertFalse(model.isSelected(FileTagType.Gallery, "file_gallery"))
        assertFalse(model.hasSelection(FileTagType.Gallery))
        assertTrue(model.isSelected(FileTagType.Print, "print_current"))
        assertEquals(setOf("print_current"), model.selectedIds(FileTagType.Print))
    }

    @Test
    fun staleFileIdIsNotSentToTheDeleteApi() {
        val dataSource = FakeGalleryDataSource()
        val model = createModel(dataSource)

        model.toggleSelection(FileTagType.Gallery, "file_stale")
        model.deleteSelectedFiles(FileTagType.Gallery, "deleting", "deleted", "failed")

        assertEquals(emptyList(), dataSource.deletedFileIds)
        assertFalse(model.hasSelection(FileTagType.Gallery))
    }

    @Test
    fun currentFileSelectionDeletesAndRefreshesItsOwnTag() {
        val dataSource = FakeGalleryDataSource().apply {
            filesByTag[FileTagType.Sticker] = listOf(file("file_sticker"))
        }
        val model = createModel(dataSource)
        model.refreshFiles(FileTagType.Sticker)
        dataSource.fileRequests.clear()

        model.toggleSelection(FileTagType.Sticker, "file_sticker")
        model.deleteSelectedFiles(FileTagType.Sticker, "deleting", "deleted", "failed")

        assertEquals(listOf("file_sticker"), dataSource.deletedFileIds)
        assertEquals(listOf(FileRequest(FileTagType.Sticker, 100, 0)), dataSource.fileRequests)
        assertFalse(model.hasSelection(FileTagType.Sticker))
    }

    @Test
    fun refreshRequestsEnoughFilesForTheProductLimit() {
        val dataSource = FakeGalleryDataSource()
        val model = createModel(dataSource)

        model.refreshFiles(FileTagType.Gallery)

        assertEquals(listOf(FileRequest(FileTagType.Gallery, 100, 0)), dataSource.fileRequests)
    }

    @Test
    fun initClearsSelectionRetainedByTheSingleton() {
        val model = createModel()
        model.toggleSelection(FileTagType.Gallery, "file_gallery")

        model.init()

        assertFalse(model.hasSelection(FileTagType.Gallery))
    }

    @Test
    fun successfulByteUploadRefreshesTheUploadedTag() {
        val dataSource = FakeGalleryDataSource().apply {
            uploadResult = Result.success(file("file_webp"))
        }
        val model = createModel(dataSource)

        model.uploadImageBytes(
            fileBytes = byteArrayOf(1),
            fileName = "image.webp",
            tagType = FileTagType.Emoji,
            uploadingMessage = "uploading",
            successMessage = "uploaded",
            failedMessagePrefix = "failed",
        )

        assertEquals(listOf(UploadRequest("image.webp", "image/webp", FileTagType.Emoji)), dataSource.uploadRequests)
        assertEquals(listOf(FileRequest(FileTagType.Emoji, 100, 0)), dataSource.fileRequests)
    }

    @Test
    fun unsupportedByteUploadDoesNotCallTheApi() {
        val dataSource = FakeGalleryDataSource()
        val model = createModel(dataSource)

        model.uploadImageBytes(
            fileBytes = byteArrayOf(1),
            fileName = "image.bmp",
            tagType = FileTagType.Gallery,
            uploadingMessage = "uploading",
            successMessage = "uploaded",
            failedMessagePrefix = "failed",
        )

        assertEquals(emptyList(), dataSource.uploadRequests)
        assertEquals(emptyList(), dataSource.fileRequests)
    }

    private fun createModel(
        dataSource: FakeGalleryDataSource = FakeGalleryDataSource(),
    ) = GalleryScreenModel(
        dataSource = dataSource,
        logger = EmptyLogger(),
        workerDispatcher = Dispatchers.Unconfined,
    )
}

private data class FileRequest(val tagType: FileTagType, val n: Int, val offset: Int)

private data class UploadRequest(
    val fileName: String,
    val mimeType: String,
    val tagType: FileTagType,
)

private class FakeGalleryDataSource : GalleryDataSource {
    val filesByTag = mutableMapOf<FileTagType, List<FileData>>()
    var prints: List<PrintData> = emptyList()
    var uploadResult: Result<FileData> = Result.failure(IllegalStateException("upload result not configured"))
    val fileRequests = mutableListOf<FileRequest>()
    val uploadRequests = mutableListOf<UploadRequest>()
    val deletedFileIds = mutableListOf<String>()
    val deletedPrintIds = mutableListOf<String>()

    override suspend fun isCurrentUserSupporter(): Boolean = false

    override suspend fun getFiles(tagType: FileTagType, n: Int, offset: Int): List<FileData> {
        fileRequests += FileRequest(tagType, n, offset)
        return filesByTag[tagType].orEmpty()
    }

    override suspend fun getPrints(n: Int, offset: Int): List<PrintData> = prints

    override suspend fun uploadImage(
        fileBytes: ByteArray,
        fileName: String,
        mimeType: String,
        tagType: FileTagType,
    ): Result<FileData> {
        uploadRequests += UploadRequest(fileName, mimeType, tagType)
        return uploadResult
    }

    override suspend fun deleteFile(id: String) {
        deletedFileIds += id
    }

    override suspend fun deletePrint(id: String) {
        deletedPrintIds += id
    }
}

private fun file(id: String) = FileData(
    id = id,
    name = "$id.png",
    ownerId = "usr_test",
    mimeType = "image/png",
    extension = ".png",
    animationStyle = null,
    tags = emptyList(),
    versions = emptyList(),
)
