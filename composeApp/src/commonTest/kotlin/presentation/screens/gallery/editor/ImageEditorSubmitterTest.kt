package io.github.vrcmteam.vrcm.presentation.screens.gallery.editor

import io.github.vrcmteam.vrcm.network.api.avatars.data.AvatarData
import io.github.vrcmteam.vrcm.network.api.avatars.data.AvatarUpdateData
import io.github.vrcmteam.vrcm.presentation.screens.avatar.AvatarCoverFile
import io.github.vrcmteam.vrcm.presentation.screens.avatar.AvatarCoverUpdateFailure
import io.github.vrcmteam.vrcm.presentation.screens.avatar.AvatarEditor
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
        val submitter = NetworkImageEditorSubmitter(printUploader, avatarEditor)
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
        val submitter = NetworkImageEditorSubmitter(UnusedPrintUploader, avatarEditor)

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
        val submitter = NetworkImageEditorSubmitter(UnusedPrintUploader, avatarEditor)

        val result = submitter.submit(
            ImageEditorTarget.AvatarCover("avtr_test"),
            byteArrayOf(1),
            "avatar-cover.png",
        )

        assertIs<AvatarCoverUpdateFailure.Assignment>(result.exceptionOrNull())
        Unit
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
