package io.github.vrcmteam.vrcm.presentation.screens.gallery.editor

import io.github.vrcmteam.vrcm.network.api.avatars.data.AvatarData
import io.github.vrcmteam.vrcm.network.api.avatars.data.AvatarUpdateData
import io.github.vrcmteam.vrcm.network.api.files.data.FileData
import io.github.vrcmteam.vrcm.network.api.files.data.FileTagType
import io.github.vrcmteam.vrcm.presentation.screens.avatar.AvatarCoverFile
import io.github.vrcmteam.vrcm.presentation.screens.avatar.AvatarCoverUpdateFailure
import io.github.vrcmteam.vrcm.presentation.screens.avatar.AvatarEditor
import io.github.vrcmteam.vrcm.presentation.screens.gallery.GalleryDataSource
import io.github.vrcmteam.vrcm.presentation.screens.world.SessionBoundValue
import io.github.vrcmteam.vrcm.presentation.screens.world.WorldImageEditor
import io.github.vrcmteam.vrcm.presentation.screens.world.WorldImageFile
import io.github.vrcmteam.vrcm.presentation.screens.world.WorldImageUpdate
import io.github.vrcmteam.vrcm.core.shared.AccountSessionToken
import io.github.vrcmteam.vrcm.service.PrintUploader
import io.github.vrcmteam.vrcm.network.api.prints.data.PrintData
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ImageEditorSubmitterTest {
    @Test
    fun printSubmissionKeepsUsingThePrintUploader() = runBlocking {
        val printUploader = RecordingPrintUploader()
        val avatarEditor = FakeSubmissionAvatarEditor(
            uploadResult = Result.failure(IllegalStateException("unused")),
            assignmentResult = Result.failure(IllegalStateException("unused")),
        )
        val submitter = NetworkImageEditorSubmitter(
            printUploader,
            avatarEditor,
            UnusedGalleryDataSource,
            UnusedWorldImageEditor,
        )
        val png = byteArrayOf(1, 2, 3)

        val result = submitter.submit(
            target = ImageEditorTarget.Print,
            imageBytes = png,
            fileName = "print.png",
        ).getOrThrow()

        assertEquals(ImageEditorSubmission.Print, result)
        assertContentEquals(png, printUploader.imageBytes)
        assertEquals("print.png", printUploader.fileName)
        assertEquals(null, avatarEditor.uploadedCover)
    }

    @Test
    fun avatarCoverSubmissionUploadsPngBeforeAssigningItToTheAvatar() = runBlocking {
        val updated = AvatarData(
            id = "avtr_test",
            name = "Test",
            imageUrl = "https://example.test/cover.png",
        )
        val avatarEditor = FakeSubmissionAvatarEditor(
            uploadResult = Result.success(updated.imageUrl),
            assignmentResult = Result.success(updated),
        )
        val submitter = NetworkImageEditorSubmitter(
            printUploader = UnusedPrintUploader,
            avatarEditor = avatarEditor,
            galleryDataSource = UnusedGalleryDataSource,
            worldImageEditor = UnusedWorldImageEditor,
        )
        val png = byteArrayOf(1, 2, 3)

        val result = submitter.submit(
            target = ImageEditorTarget.AvatarCover(updated.id),
            imageBytes = png,
            fileName = "avatar-cover.png",
        ).getOrThrow()

        assertEquals(ImageEditorSubmission.AvatarCover(updated), result)
        assertContentEquals(png, avatarEditor.uploadedCover?.bytes)
        assertEquals("avatar-cover.png", avatarEditor.uploadedCover?.fileName)
        assertEquals("image/png", avatarEditor.uploadedCover?.mimeType)
        assertEquals(listOf(updated.id to updated.imageUrl), avatarEditor.assignments)
    }

    @Test
    fun avatarCoverUploadFailureDoesNotAttemptAssignment() = runBlocking {
        val failure = IllegalStateException("upload")
        val avatarEditor = FakeSubmissionAvatarEditor(
            uploadResult = Result.failure(failure),
            assignmentResult = Result.success(AvatarData("avtr_test", "Test")),
        )
        val submitter = NetworkImageEditorSubmitter(
            UnusedPrintUploader,
            avatarEditor,
            UnusedGalleryDataSource,
            UnusedWorldImageEditor,
        )

        val result = submitter.submit(
            ImageEditorTarget.AvatarCover("avtr_test"),
            byteArrayOf(1),
            "avatar-cover.png",
        )

        assertIs<AvatarCoverUpdateFailure.Upload>(result.exceptionOrNull())
        assertEquals(emptyList(), avatarEditor.assignments)
    }

    @Test
    fun avatarCoverAssignmentFailureRemainsDistinct() = runBlocking {
        val failure = IllegalStateException("assignment")
        val avatarEditor = FakeSubmissionAvatarEditor(
            uploadResult = Result.success("https://example.test/cover.png"),
            assignmentResult = Result.failure(failure),
        )
        val submitter = NetworkImageEditorSubmitter(
            UnusedPrintUploader,
            avatarEditor,
            UnusedGalleryDataSource,
            UnusedWorldImageEditor,
        )

        val result = submitter.submit(
            ImageEditorTarget.AvatarCover("avtr_test"),
            byteArrayOf(1),
            "avatar-cover.png",
        )

        assertIs<AvatarCoverUpdateFailure.Assignment>(result.exceptionOrNull())
        Unit
    }

    @Test
    fun gallerySubmissionUploadsCroppedPngWithTheRequestedTag() = runBlocking {
        val galleryDataSource = RecordingGalleryDataSource()
        val submitter = NetworkImageEditorSubmitter(
            printUploader = UnusedPrintUploader,
            avatarEditor = UnusedAvatarEditor,
            galleryDataSource = galleryDataSource,
            worldImageEditor = UnusedWorldImageEditor,
        )
        val png = byteArrayOf(1, 2, 3)

        val result = submitter.submit(
            target = ImageEditorTarget.Gallery(FileTagType.Sticker),
            imageBytes = png,
            fileName = "sticker-123.png",
        ).getOrThrow()

        assertEquals(ImageEditorSubmission.Gallery(FileTagType.Sticker), result)
        assertContentEquals(png, galleryDataSource.imageBytes)
        assertEquals("sticker-123.png", galleryDataSource.fileName)
        assertEquals("image/png", galleryDataSource.mimeType)
        assertEquals(FileTagType.Sticker, galleryDataSource.tagType)
    }
}

private class FakeSubmissionAvatarEditor(
    private val uploadResult: Result<String>,
    private val assignmentResult: Result<AvatarData>,
) : AvatarEditor {
    var uploadedCover: AvatarCoverFile? = null
    val assignments = mutableListOf<Pair<String, String>>()

    override suspend fun updateMetadata(
        avatarId: String,
        update: AvatarUpdateData,
    ): Result<AvatarData> = error("Metadata update is not used")

    override suspend fun uploadCover(cover: AvatarCoverFile): Result<String> {
        uploadedCover = cover
        return uploadResult
    }

    override suspend fun assignCover(avatarId: String, imageUrl: String): Result<AvatarData> {
        assignments += avatarId to imageUrl
        return assignmentResult
    }
}

private data object UnusedPrintUploader : PrintUploader {
    override suspend fun upload(imageBytes: ByteArray, fileName: String): Result<PrintData> =
        error("Print upload is not used")
}

private data object UnusedAvatarEditor : AvatarEditor {
    override suspend fun updateMetadata(
        avatarId: String,
        update: AvatarUpdateData,
    ): Result<AvatarData> = error("Avatar editing is not used")

    override suspend fun uploadCover(cover: AvatarCoverFile): Result<String> =
        error("Avatar editing is not used")

    override suspend fun assignCover(avatarId: String, imageUrl: String): Result<AvatarData> =
        error("Avatar editing is not used")
}

private data object UnusedWorldImageEditor : WorldImageEditor {
    override suspend fun uploadImage(
        sessionToken: AccountSessionToken,
        image: WorldImageFile,
    ): Result<SessionBoundValue<String>> = error("World image editing is not used")

    override suspend fun assignImage(
        sessionToken: AccountSessionToken,
        worldId: String,
        imageUrl: String,
    ): Result<SessionBoundValue<Unit>> = error("World image editing is not used")

    override suspend fun refreshWorld(
        sessionToken: AccountSessionToken,
        worldId: String,
    ): Result<WorldImageUpdate> = error("World image editing is not used")
}

private data object UnusedGalleryDataSource : GalleryDataSource {
    override suspend fun isCurrentUserSupporter(): Boolean = error("Gallery access is not used")
    override suspend fun getFiles(tagType: FileTagType, n: Int, offset: Int): List<FileData> =
        error("Gallery access is not used")
    override suspend fun getPrints(n: Int, offset: Int): List<PrintData> =
        error("Gallery access is not used")
    override suspend fun uploadImage(
        fileBytes: ByteArray,
        fileName: String,
        mimeType: String,
        tagType: FileTagType,
    ): Result<FileData> = error("Gallery access is not used")
    override suspend fun deleteFile(id: String) = error("Gallery access is not used")
    override suspend fun deletePrint(id: String) = error("Gallery access is not used")
}

private class RecordingGalleryDataSource : GalleryDataSource {
    var imageBytes: ByteArray? = null
    var fileName: String? = null
    var mimeType: String? = null
    var tagType: FileTagType? = null

    override suspend fun uploadImage(
        fileBytes: ByteArray,
        fileName: String,
        mimeType: String,
        tagType: FileTagType,
    ): Result<FileData> {
        this.imageBytes = fileBytes
        this.fileName = fileName
        this.mimeType = mimeType
        this.tagType = tagType
        return Result.success(testFile(tagType))
    }

    override suspend fun isCurrentUserSupporter(): Boolean = error("Not used")
    override suspend fun getFiles(tagType: FileTagType, n: Int, offset: Int): List<FileData> =
        error("Not used")
    override suspend fun getPrints(n: Int, offset: Int): List<PrintData> = error("Not used")
    override suspend fun deleteFile(id: String) = error("Not used")
    override suspend fun deletePrint(id: String) = error("Not used")
}

private fun testFile(tagType: FileTagType) = FileData(
    id = "file_test",
    name = "${tagType.value}.png",
    ownerId = "usr_test",
    mimeType = "image/png",
    extension = ".png",
    animationStyle = null,
    tags = listOf(tagType.value),
    versions = emptyList(),
)

private class RecordingPrintUploader : PrintUploader {
    var imageBytes: ByteArray? = null
    var fileName: String? = null

    override suspend fun upload(
        imageBytes: ByteArray,
        fileName: String,
    ): Result<PrintData> {
        this.imageBytes = imageBytes
        this.fileName = fileName
        return Result.success(PrintData("print_test"))
    }
}
