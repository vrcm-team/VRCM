package io.github.vrcmteam.vrcm.presentation.screens.gallery.editor

import io.github.vrcmteam.vrcm.core.shared.AccountSessionToken
import io.github.vrcmteam.vrcm.network.api.avatars.data.AvatarData
import io.github.vrcmteam.vrcm.network.api.avatars.data.AvatarUpdateData
import io.github.vrcmteam.vrcm.network.api.files.data.FileData
import io.github.vrcmteam.vrcm.network.api.files.data.FileTagType
import io.github.vrcmteam.vrcm.presentation.screens.avatar.AvatarCoverFile
import io.github.vrcmteam.vrcm.presentation.screens.avatar.AvatarCoverUpdateFailure
import io.github.vrcmteam.vrcm.presentation.screens.avatar.AvatarEditor
import io.github.vrcmteam.vrcm.presentation.screens.avatar.AvatarGalleryPendingRefresh
import io.github.vrcmteam.vrcm.presentation.screens.avatar.AvatarGalleryTarget
import io.github.vrcmteam.vrcm.presentation.screens.avatar.AvatarGalleryUpdate
import io.github.vrcmteam.vrcm.presentation.screens.avatar.AvatarGalleryUploader
import io.github.vrcmteam.vrcm.presentation.screens.avatar.AvatarMetadataUpdateResponse
import io.github.vrcmteam.vrcm.presentation.screens.avatar.AvatarStylesResponse
import io.github.vrcmteam.vrcm.presentation.screens.avatar.AvatarPublicationResponse
import io.github.vrcmteam.vrcm.presentation.screens.gallery.GalleryDataSource
import io.github.vrcmteam.vrcm.presentation.screens.world.SessionBoundValue
import io.github.vrcmteam.vrcm.presentation.screens.world.WorldImageEditor
import io.github.vrcmteam.vrcm.presentation.screens.world.WorldImageFile
import io.github.vrcmteam.vrcm.presentation.screens.world.WorldImageUpdate
import io.github.vrcmteam.vrcm.service.PrintUploader
import io.github.vrcmteam.vrcm.network.api.prints.data.PrintData
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

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

    @Test
    fun avatarGallerySubmissionReportsUploadAndRefreshStages() = runBlocking {
        val uploader = RecordingAvatarGalleryUploader()
        val submitter = NetworkImageEditorSubmitter(
            printUploader = UnusedPrintUploader,
            avatarEditor = UnusedAvatarEditor,
            galleryDataSource = UnusedGalleryDataSource,
            worldImageEditor = UnusedWorldImageEditor,
            avatarGalleryUploader = uploader,
        )
        val target = ImageEditorTarget.AvatarGallery(
            AvatarGalleryTarget(
                avatarId = "avtr_owner",
                ownerUserId = "usr_owner",
                sessionToken = AccountSessionToken("usr_owner", 1),
            )
        )
        val progress = mutableListOf<ImageEditorSubmissionProgress>()

        val result = submitter.submit(target, byteArrayOf(1), "gallery.png", progress::add)

        assertIs<ImageEditorSubmission.AvatarGallery>(result.getOrThrow())
        assertEquals("avtr_owner", uploader.lastTarget?.avatarId)
        assertEquals(
            listOf(
                ImageEditorSubmissionProgress.Upload(4, 8),
                ImageEditorSubmissionProgress.Refreshing,
            ),
            progress,
        )
    }

    @Test
    fun avatarGalleryRefreshFailureRetryDoesNotUploadAgain() = runBlocking {
        val uploader = RecordingAvatarGalleryUploader(refreshFailure = true)
        val submitter = NetworkImageEditorSubmitter(
            UnusedPrintUploader,
            UnusedAvatarEditor,
            UnusedGalleryDataSource,
            UnusedWorldImageEditor,
            uploader,
        )
        val targetValue = AvatarGalleryTarget(
            "avtr_owner",
            "usr_owner",
            AccountSessionToken("usr_owner", 1),
        )
        val target = ImageEditorTarget.AvatarGallery(targetValue)
        val first = submitter.submit(target, byteArrayOf(1), "gallery.png", {})
        val failure = first.exceptionOrNull()
        assertIs<io.github.vrcmteam.vrcm.presentation.screens.avatar.AvatarGalleryUploadFailure.Refresh>(failure)

        val stillPending = submitter.retry(target, byteArrayOf(1), "gallery.png", failure, {})
        val retryFailure = stillPending.exceptionOrNull()
        assertIs<io.github.vrcmteam.vrcm.presentation.screens.avatar.AvatarGalleryUploadFailure.Refresh>(
            retryFailure,
        )
        assertEquals(1, uploader.uploadCount)

        uploader.refreshFailure = false
        val retried = submitter.retry(target, byteArrayOf(1), "gallery.png", retryFailure, {})

        assertTrue(retried.isSuccess)
        assertEquals(1, uploader.uploadCount)
        assertEquals(3, uploader.refreshCount)
    }
}

private class FakeSubmissionAvatarEditor(
    private val uploadResult: Result<String>,
    private val assignmentResult: Result<AvatarData>,
) : AvatarEditor {
    var uploadedCover: AvatarCoverFile? = null
    val assignments = mutableListOf<Pair<String, String>>()

    override suspend fun loadStyles(
        sessionToken: AccountSessionToken,
    ): AvatarStylesResponse? = error("Metadata update is not used")

    override suspend fun updateMetadata(
        sessionToken: AccountSessionToken,
        avatarId: String,
        update: AvatarUpdateData,
    ): AvatarMetadataUpdateResponse? = error("Metadata update is not used")

    override suspend fun updatePublication(
        sessionToken: AccountSessionToken,
        avatarId: String,
        releaseStatus: String,
    ): AvatarPublicationResponse? = error("Publication update is not used")

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
    override suspend fun loadStyles(
        sessionToken: AccountSessionToken,
    ): AvatarStylesResponse? = error("Avatar editing is not used")

    override suspend fun updateMetadata(
        sessionToken: AccountSessionToken,
        avatarId: String,
        update: AvatarUpdateData,
    ): AvatarMetadataUpdateResponse? = error("Avatar editing is not used")

    override suspend fun updatePublication(
        sessionToken: AccountSessionToken,
        avatarId: String,
        releaseStatus: String,
    ): AvatarPublicationResponse? = error("Avatar editing is not used")

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

private class RecordingAvatarGalleryUploader(
    var refreshFailure: Boolean = false,
) : AvatarGalleryUploader {
    var uploadCount = 0
    var refreshCount = 0
    var lastTarget: AvatarGalleryTarget? = null

    override suspend fun uploadAndRefresh(
        target: AvatarGalleryTarget,
        imageBytes: ByteArray,
        fileName: String,
        mimeType: String,
        onUploadProgress: suspend (Long, Long?) -> Unit,
        onRefreshing: () -> Unit,
    ): Result<AvatarGalleryUpdate> {
        uploadCount++
        lastTarget = target
        onUploadProgress(4, 8)
        val pending = AvatarGalleryPendingRefresh(
            target,
            testFile(FileTagType.Gallery),
            target.sessionToken,
        )
        refreshCount++
        onRefreshing()
        return if (refreshFailure) {
            Result.failure(
                io.github.vrcmteam.vrcm.presentation.screens.avatar.AvatarGalleryUploadFailure.Refresh(
                    pending,
                    IllegalStateException("refresh"),
                )
            )
        } else {
            Result.success(AvatarGalleryUpdate(target.avatarId, emptyList(), target.sessionToken))
        }
    }

    override suspend fun refresh(
        pending: AvatarGalleryPendingRefresh,
        onRefreshing: () -> Unit,
    ): Result<AvatarGalleryUpdate> {
        refreshCount++
        onRefreshing()
        return if (refreshFailure) {
            Result.failure(
                io.github.vrcmteam.vrcm.presentation.screens.avatar.AvatarGalleryUploadFailure.Refresh(
                    pending,
                    IllegalStateException("still refreshing"),
                )
            )
        } else {
            Result.success(
                AvatarGalleryUpdate(pending.target.avatarId, emptyList(), pending.sessionToken)
            )
        }
    }
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
