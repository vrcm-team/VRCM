package io.github.vrcmteam.vrcm.network.api.files

import io.github.vrcmteam.vrcm.network.api.files.data.FileTagType
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.readRemaining
import kotlinx.coroutines.runBlocking
import kotlinx.io.readByteArray
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FileApiUploadTest {
    @Test
    fun emojiUploadSendsVrchatEmojiMetadata() = runBlocking {
        val capturedBody = captureUploadBody(FileTagType.Emoji)

        assertTrue(capturedBody.contains("filename=\"blob\""))
        assertTrue(capturedBody.contains("name=tag"))
        assertTrue(capturedBody.contains("emoji"))
        assertTrue(capturedBody.contains("name=animationStyle"))
        assertTrue(capturedBody.contains("stop"))
        assertTrue(capturedBody.contains("name=maskTag"))
        assertTrue(capturedBody.contains("square"))
    }

    @Test
    fun stickerUploadSendsVrchatSquareMaskMetadata() = runBlocking {
        val capturedBody = captureUploadBody(FileTagType.Sticker)

        assertTrue(capturedBody.contains("name=tag"))
        assertTrue(capturedBody.contains("sticker"))
        assertTrue(capturedBody.contains("name=maskTag"))
        assertTrue(capturedBody.contains("square"))
        assertFalse(capturedBody.contains("name=animationStyle"))
    }

    @Test
    fun galleryAndIconUploadsDoNotSendEmojiOrStickerMetadata() = runBlocking {
        listOf(FileTagType.Gallery, FileTagType.Icon).forEach { tagType ->
            val capturedBody = captureUploadBody(tagType)

            assertFalse(capturedBody.contains("name=maskTag"), tagType.value)
            assertFalse(capturedBody.contains("name=animationStyle"), tagType.value)
        }
    }

    @Test
    fun avatarImageUploadKeepsItsExistingMultipartFileName() = runBlocking {
        val capturedBody = captureUploadBody(
            tagType = FileTagType.AvatarImage,
            fileName = "avatar-cover.png",
        )

        assertTrue(capturedBody.contains("filename=\"avatar-cover.png\""))
        assertTrue(capturedBody.contains("avatarimage"))
    }

    @Test
    fun worldImageUploadUsesTheVrchatWorldImageTag() = runBlocking {
        val capturedBody = captureUploadBody(FileTagType.WorldImage)

        assertTrue(capturedBody.contains("filename=\"blob\""))
        assertTrue(capturedBody.contains("worldimage"))
        assertFalse(capturedBody.contains("name=maskTag"))
    }

    @Test
    fun animatedEmojiUploadUsesVrcxFilenameMetadata() = runBlocking {
        val capturedBody = captureUploadBody(
            tagType = FileTagType.Emoji,
            fileName = "wave_stopanimationStyle_8frames_24fps_pingpongloopStyle.png",
        )

        assertTrue(capturedBody.contains("emojianimated"))
        assertTrue(capturedBody.contains("name=frames"))
        assertTrue(capturedBody.contains("8"))
        assertTrue(capturedBody.contains("name=framesOverTime"))
        assertTrue(capturedBody.contains("24"))
        assertTrue(capturedBody.contains("name=loopStyle"))
        assertTrue(capturedBody.contains("pingpong"))
    }

    @Test
    fun staticEmojiFilenameCanSelectAnimationStyle() {
        val parameters = vrcxImageUploadParameters(
            FileTagType.Emoji,
            "wave_bounceanimationStyle.png",
        )

        assertEquals("emoji", parameters.tag)
        assertEquals("bounce", parameters.animationStyle)
        assertEquals(null, parameters.frames)
        assertEquals(null, parameters.framesOverTime)
    }

    private suspend fun captureUploadBody(
        tagType: FileTagType,
        fileName: String = "${tagType.value}.png",
    ): String {
        var capturedBody = ""
        val client = HttpClient(MockEngine) {
            engine {
                addHandler { request ->
                    val multipart = request.body as MultiPartFormDataContent
                    val channel = ByteChannel()
                    multipart.writeTo(channel)
                    capturedBody = channel.readRemaining().readByteArray().decodeToString()
                    respond(
                        content = """{
                            "id":"file_test",
                            "name":"blob",
                            "ownerId":"usr_test",
                            "mimeType":"image/png",
                            "extension":".png",
                            "animationStyle":null,
                            "tags":["${tagType.value}"],
                            "versions":[]
                        }""".trimIndent(),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }
            }
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }

        FileApi(client).uploadImageFile(
            fileBytes = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47),
            fileName = fileName,
            mimeType = "image/png",
            tagType = tagType,
        ).getOrThrow()

        client.close()
        return capturedBody
    }
}
