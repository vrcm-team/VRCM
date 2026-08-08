package io.github.vrcmteam.vrcm.service.meetup

import com.russhwolf.settings.MapSettings
import io.github.vrcmteam.vrcm.network.api.inventory.data.InventoryTemplateAsset
import io.github.vrcmteam.vrcm.network.api.inventory.data.InventoryTemplateData
import io.github.vrcmteam.vrcm.network.api.inventory.data.InventoryTemplateMetadata
import io.github.vrcmteam.vrcm.network.supports.VRCApiException
import io.github.vrcmteam.vrcm.presentation.screens.meetup.animation.AnimatedWebpDecoder
import io.github.vrcmteam.vrcm.presentation.screens.meetup.animation.DecodedAnimation
import io.github.vrcmteam.vrcm.presentation.screens.meetup.animation.OwnedAnimationFrame
import io.github.vrcmteam.vrcm.storage.DaoKeys
import io.github.vrcmteam.vrcm.storage.meetup.DecorationAssetType
import io.github.vrcmteam.vrcm.storage.meetup.DecorationTemplateCache
import io.github.vrcmteam.vrcm.storage.meetup.DecorationTemplateCacheDao
import io.github.vrcmteam.vrcm.storage.meetup.MeetupCardAssetStore
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.defaultRequest
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import okio.IOException
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DecorationResolverTest {
    @Test
    fun duplicateTemplateIdsAreFetchedOnce() = resolverTest {
        source.handler = { templateId ->
            template(templateId, asset("mainAnimation", "https://cdn/$templateId-main.webp"))
        }
        loader.handler = { url, _ -> url.encodeToByteArray() }

        val result = resolver().refresh(listOf(" inv_dup ", "", "inv_dup", "   "))

        assertEquals(listOf("inv_dup"), source.requests)
        assertEquals(setOf("inv_dup"), result.keys)
        assertEquals(DecorationRenderMode.Animated, result.getValue("inv_dup").mode)
    }

    @Test
    fun unsafeTemplateIdIsUnavailableWithoutSendingRequests() = resolverTest {
        val result = resolver().refresh(listOf("inv_good/../inv_other"))

        assertEquals(DecorationRenderMode.Unavailable, result.getValue("inv_good/../inv_other").mode)
        assertTrue(source.requests.isEmpty())
        assertTrue(loader.requests.isEmpty())
    }

    @Test
    fun failedMainAnimationFallsBackToBaseWithoutAffectingOtherTemplates() = resolverTest {
        source.handler = { templateId ->
            when (templateId) {
                "inv_frame" -> template(
                    templateId,
                    asset("mainAnimation", "https://cdn/frame-main.webp"),
                    asset("base", "https://cdn/frame-base.webp"),
                )
                "inv_effect" -> template(
                    templateId,
                    asset("mainAnimation", "https://cdn/effect-main.webp"),
                )
                else -> error("Unexpected template: $templateId")
            }
        }
        loader.handler = { url, _ ->
            when (url) {
                "https://cdn/frame-main.webp" -> "invalid-animation".encodeToByteArray()
                "https://cdn/frame-base.webp" -> "static-base".encodeToByteArray()
                "https://cdn/effect-main.webp" -> "animated-effect".encodeToByteArray()
                else -> error("Unexpected URL: $url")
            }
        }
        decoder.handler = { bytes ->
            if (bytes.decodeToString() == "invalid-animation") throw IOException("invalid WebP")
            FakeDecodedAnimation(decoder::recordClose)
        }

        val result = resolver().refresh(listOf("inv_frame", "inv_effect"))

        assertEquals(DecorationRenderMode.Static, result.getValue("inv_frame").mode)
        assertEquals(DecorationRenderMode.Animated, result.getValue("inv_effect").mode)
        assertTrue(loader.requests.all { it.maxBytes == DECORATION_MAX_BYTES })
        assertEquals(1, decoder.closeCount)
    }

    @Test
    fun unavailableTemplateDoesNotDiscardSuccessfulTemplate() = resolverTest {
        source.handler = { templateId ->
            when (templateId) {
                "inv_bad" -> template(templateId)
                "inv_good" -> template(
                    templateId,
                    asset("mainAnimation", "https://cdn/good.webp"),
                )
                else -> error("Unexpected template: $templateId")
            }
        }
        loader.handler = { _, _ -> "animated".encodeToByteArray() }

        val result = resolver().refresh(listOf("inv_bad", "inv_good"))

        assertEquals(DecorationRenderMode.Unavailable, result.getValue("inv_bad").mode)
        assertEquals(DecorationRenderMode.Animated, result.getValue("inv_good").mode)
    }

    @Test
    fun introAnimationIsIgnored() = resolverTest {
        source.handler = { templateId ->
            template(templateId, asset("introAnimation", "https://cdn/intro.webp"))
        }

        val result = resolver().refresh(listOf("inv_intro"))

        assertEquals(DecorationRenderMode.Unavailable, result.getValue("inv_intro").mode)
        assertTrue(loader.requests.isEmpty())
    }

    @Test
    fun cancellationFromSourceLoaderAndDecoderIsRethrown() = resolverTest {
        source.handler = { throw CancellationException("source cancelled") }
        assertFailsWith<CancellationException> { resolver().refresh(listOf("inv_cancel")) }

        source.handler = { templateId ->
            template(templateId, asset("mainAnimation", "https://cdn/cancel.webp"))
        }
        loader.handler = { _, _ -> throw CancellationException("loader cancelled") }
        assertFailsWith<CancellationException> { resolver().refresh(listOf("inv_cancel")) }

        loader.handler = { _, _ -> "animated".encodeToByteArray() }
        decoder.handler = { throw CancellationException("decoder cancelled") }
        assertFailsWith<CancellationException> { resolver().refresh(listOf("inv_cancel")) }
    }

    @Test
    fun fatalErrorsFromSourceLoaderAndDecoderAreRethrown() = resolverTest {
        source.handler = { throw AssertionError("source failed") }
        assertFailsWith<AssertionError> { resolver().refresh(listOf("inv_fatal")) }

        source.handler = { templateId ->
            template(templateId, asset("mainAnimation", "https://cdn/fatal.webp"))
        }
        loader.handler = { _, _ -> throw AssertionError("loader failed") }
        assertFailsWith<AssertionError> { resolver().refresh(listOf("inv_fatal")) }

        loader.handler = { _, _ -> "animated".encodeToByteArray() }
        decoder.handler = { throw AssertionError("decoder failed") }
        assertFailsWith<AssertionError> { resolver().refresh(listOf("inv_fatal")) }
    }

    @Test
    fun remoteFailureUsesValidatedCachedAnimationOffline() = resolverTest {
        val bytes = "cached-animation".encodeToByteArray()
        val ref = assetStore.writeDecoration(
            "inv_cached",
            DecorationAssetType.MainAnimation,
            bytes,
        )
        cacheDao.save(
            DecorationTemplateCache(
                templateId = "inv_cached",
                mainAnimationUrl = "https://cdn/cached.webp",
                mainAnimationAsset = ref,
                gradientStart = "#112233",
                gradientEnd = "#445566",
            ),
        )
        source.handler = { throw IOException("offline") }

        val result = resolver().refresh(listOf("inv_cached")).getValue("inv_cached")

        assertEquals(DecorationRenderMode.Animated, result.mode)
        assertEquals(ref, result.asset)
        assertEquals("#112233", result.gradientStart)
        assertEquals("#445566", result.gradientEnd)
        assertTrue(loader.requests.isEmpty())
        assertContentEquals(bytes, decoder.decoded.single())
        assertEquals(1, decoder.closeCount)
    }

    @Test
    fun mismatchedRemoteTemplateIdDoesNotPolluteValidatedCache() = resolverTest {
        val cachedBytes = "cached-animation".encodeToByteArray()
        val cachedRef = assetStore.writeDecoration(
            "inv_cached",
            DecorationAssetType.MainAnimation,
            cachedBytes,
        )
        val cached = DecorationTemplateCache(
            templateId = "inv_cached",
            mainAnimationUrl = "https://cdn/cached.webp",
            mainAnimationAsset = cachedRef,
            gradientStart = "#112233",
            gradientEnd = "#445566",
        )
        cacheDao.save(cached)
        source.handler = {
            template(
                "inv_other",
                asset("mainAnimation", "https://cdn/untrusted.webp"),
                gradientStart = "#AABBCC",
                gradientEnd = "#DDEEFF",
            )
        }
        loader.handler = { _, _ -> error("Validated cache must not be downloaded") }

        val result = resolver().refresh(listOf("inv_cached")).getValue("inv_cached")

        assertEquals(DecorationRenderMode.Animated, result.mode)
        assertEquals(cachedRef, result.asset)
        assertEquals("#112233", result.gradientStart)
        assertEquals("#445566", result.gradientEnd)
        assertEquals(cached, cacheDao.load("inv_cached"))
        assertTrue(loader.requests.isEmpty())
        assertContentEquals(cachedBytes, decoder.decoded.single())
    }

    @Test
    fun missingCachedAssetIsDownloadedAgainAndCacheReferenceIsUpdated() = resolverTest {
        val staleRef = assetStore.writeDecoration(
            "inv_missing",
            DecorationAssetType.MainAnimation,
            "stale-animation".encodeToByteArray(),
        )
        cacheDao.save(
            DecorationTemplateCache(
                templateId = "inv_missing",
                mainAnimationUrl = "https://cdn/animation.webp",
                mainAnimationAsset = staleRef,
            ),
        )
        fileSystem.delete(assetStore.model(staleRef).toPath())
        source.handler = { templateId ->
            template(templateId, asset("mainAnimation", "https://cdn/animation.webp"))
        }
        val freshBytes = "fresh-animation".encodeToByteArray()
        loader.handler = { _, _ -> freshBytes }

        val result = resolver().refresh(listOf("inv_missing")).getValue("inv_missing")

        assertEquals(DecorationRenderMode.Animated, result.mode)
        assertTrue(result.asset != null && result.asset != staleRef)
        assertEquals(listOf("https://cdn/animation.webp"), loader.requests.map(LoadRequest::url))
        assertEquals(result.asset, cacheDao.load("inv_missing")?.mainAnimationAsset)
        assertContentEquals(freshBytes, assetStore.read(requireNotNull(result.asset)))
    }

    @Test
    fun changedUrlsNeverReuseOldCachedAssets() = resolverTest {
        val oldMain = assetStore.writeDecoration(
            "inv_changed",
            DecorationAssetType.MainAnimation,
            "old-main".encodeToByteArray(),
        )
        val oldBase = assetStore.writeDecoration(
            "inv_changed",
            DecorationAssetType.Base,
            "old-base".encodeToByteArray(),
        )
        cacheDao.save(
            DecorationTemplateCache(
                templateId = "inv_changed",
                mainAnimationUrl = "https://cdn/old-main.webp",
                baseUrl = "https://cdn/old-base.webp",
                mainAnimationAsset = oldMain,
                baseAsset = oldBase,
            ),
        )
        source.handler = { templateId ->
            template(
                templateId,
                asset("mainAnimation", "https://cdn/new-main.webp"),
                asset("base", "https://cdn/new-base.webp"),
            )
        }
        loader.handler = { _, _ -> throw IOException("network failed") }

        val result = resolver().refresh(listOf("inv_changed")).getValue("inv_changed")

        assertEquals(DecorationRenderMode.Unavailable, result.mode)
        assertNull(result.asset)
        assertEquals(
            listOf("https://cdn/new-main.webp", "https://cdn/new-base.webp"),
            loader.requests.map(LoadRequest::url),
        )
        assertTrue(decoder.decoded.isEmpty())
        val updatedCache = cacheDao.load("inv_changed")
        assertEquals("https://cdn/new-main.webp", updatedCache?.mainAnimationUrl)
        assertEquals("https://cdn/new-base.webp", updatedCache?.baseUrl)
        assertNull(updatedCache?.mainAnimationAsset)
        assertNull(updatedCache?.baseAsset)
    }

    @Test
    fun failedMainKeepsReadableMatchingCachedBase() = resolverTest {
        val baseBytes = "cached-base".encodeToByteArray()
        val baseRef = assetStore.writeDecoration(
            "inv_base",
            DecorationAssetType.Base,
            baseBytes,
        )
        cacheDao.save(
            DecorationTemplateCache(
                templateId = "inv_base",
                mainAnimationUrl = "https://cdn/main.webp",
                baseUrl = "https://cdn/base.webp",
                baseAsset = baseRef,
            ),
        )
        source.handler = { templateId ->
            template(
                templateId,
                asset("mainAnimation", "https://cdn/main.webp"),
                asset("base", "https://cdn/base.webp"),
            )
        }
        loader.handler = { url, _ ->
            if (url.endsWith("main.webp")) throw IOException("main failed")
            error("Cached base must not be downloaded")
        }

        val result = resolver().refresh(listOf("inv_base")).getValue("inv_base")

        assertEquals(DecorationRenderMode.Static, result.mode)
        assertEquals(baseRef, result.asset)
        assertEquals(baseRef, cacheDao.load("inv_base")?.baseAsset)
        assertEquals(listOf("https://cdn/main.webp"), loader.requests.map(LoadRequest::url))
    }

    @Test
    fun cacheDaoIgnoresUnknownCorruptAndMismatchedEntriesWithoutDeletingThem() {
        val settings = MapSettings()
        val dao = DecorationTemplateCacheDao(settings)
        val prefix = DaoKeys.MeetupDecoration.KEY_PREFIX
        settings.putString(
            "$prefix.inv_future",
            """{"templateId":"inv_future","future":{"enabled":true}}""",
        )
        settings.putString("$prefix.inv_corrupt", "not-json")
        settings.putString(
            "$prefix.inv_mismatch",
            """{"templateId":"inv_other","mainAnimationUrl":"https://cdn/other.webp"}""",
        )

        assertEquals("inv_future", dao.load("inv_future")?.templateId)
        assertNull(dao.load("inv_corrupt"))
        assertNull(dao.load("inv_mismatch"))
        assertEquals("not-json", settings.getStringOrNull("$prefix.inv_corrupt"))
        assertTrue(settings.getStringOrNull("$prefix.inv_mismatch")?.contains("inv_other") == true)
    }

    @Test
    fun cacheDaoUsesSafeExactPrefixAndClearAllDoesNotTouchSimilarKeys() {
        val settings = MapSettings()
        val dao = DecorationTemplateCacheDao(settings)
        val prefix = DaoKeys.MeetupDecoration.KEY_PREFIX
        dao.save(DecorationTemplateCache(templateId = "inv_saved"))
        settings.putString("${prefix}s.inv_other", "keep")
        settings.putString("vrcm.meetup.decoration.other.inv_other", "keep")

        assertFailsWith<IllegalArgumentException> {
            dao.save(DecorationTemplateCache(templateId = "../inv_escape"))
        }
        assertFailsWith<IllegalArgumentException> { dao.load("../inv_escape") }
        assertNotNull(settings.getStringOrNull("$prefix.inv_saved"))

        dao.clearAll()

        assertNull(settings.getStringOrNull("$prefix.inv_saved"))
        assertEquals("keep", settings.getStringOrNull("${prefix}s.inv_other"))
        assertEquals("keep", settings.getStringOrNull("vrcm.meetup.decoration.other.inv_other"))
    }

    @Test
    fun httpLoaderRejectsDeclaredAndStreamedBodiesOverLimit() = runTest {
        val declaredClient = httpClient(
            bytes = byteArrayOf(1),
            headers = headersOf(HttpHeaders.ContentLength, "4"),
        )
        val streamedClient = httpClient(bytes = byteArrayOf(1, 2, 3, 4))

        try {
            assertFailsWith<IOException> {
                HttpMeetupRemoteBytesLoader(declaredClient).load("asset.webp", maxBytes = 3)
            }
            assertFailsWith<IOException> {
                HttpMeetupRemoteBytesLoader(streamedClient).load("asset.webp", maxBytes = 3)
            }
        } finally {
            declaredClient.close()
            streamedClient.close()
        }
    }

    @Test
    fun httpLoaderUsesCheckSuccessForNonSuccessResponse() = runTest {
        val client = httpClient(
            bytes = """{"error":{"message":"Unauthorized"}}""".encodeToByteArray(),
            status = HttpStatusCode.Unauthorized,
            headers = headersOf(HttpHeaders.ContentType, "application/json"),
        )

        try {
            val error = assertFailsWith<VRCApiException> {
                HttpMeetupRemoteBytesLoader(client).load("asset.webp", maxBytes = 1024)
            }
            assertEquals(401, error.code)
        } finally {
            client.close()
        }
    }

    @Test
    fun httpLoaderChecksDeclaredLengthBeforeReadingErrorBody() = runTest {
        val client = httpClient(
            bytes = byteArrayOf(1),
            status = HttpStatusCode.Unauthorized,
            headers = headersOf(HttpHeaders.ContentLength, "2048"),
        )

        try {
            assertFailsWith<IOException> {
                HttpMeetupRemoteBytesLoader(client).load("asset.webp", maxBytes = 1024)
            }
        } finally {
            client.close()
        }
    }

    @Test
    fun httpLoaderBoundsUnknownLengthErrorBody() = runTest {
        val client = httpClient(
            bytes = ByteArray(2048),
            status = HttpStatusCode.Unauthorized,
        )

        try {
            assertFailsWith<IOException> {
                HttpMeetupRemoteBytesLoader(client).load("asset.webp", maxBytes = 1024)
            }
        } finally {
            client.close()
        }
    }

    @Test
    fun httpLoaderReadsRelativeAndAbsoluteUrlsWithinLimit() = runTest {
        val paths = mutableListOf<String>()
        val client = HttpClient(MockEngine) {
            engine {
                addHandler { request ->
                    paths += request.url.encodedPath
                    respond(ByteReadChannel("ok".encodeToByteArray()))
                }
            }
            defaultRequest { url("https://api.vrchat.cloud/api/1/") }
        }

        try {
            val loader = HttpMeetupRemoteBytesLoader(client)
            assertContentEquals("ok".encodeToByteArray(), loader.load("asset.webp", 1024))
            assertContentEquals(
                "ok".encodeToByteArray(),
                loader.load("https://cdn.example.com/decor.webp", 1024),
            )
            assertEquals(listOf("/api/1/asset.webp", "/decor.webp"), paths)
        } finally {
            client.close()
        }
    }

    private fun resolverTest(block: suspend ResolverFixture.() -> Unit) = runTest {
        val fixture = ResolverFixture()
        try {
            fixture.block()
        } finally {
            fixture.fileSystem.checkNoOpenFiles()
        }
    }

    private fun httpClient(
        bytes: ByteArray,
        status: HttpStatusCode = HttpStatusCode.OK,
        headers: Headers = Headers.Empty,
    ) = HttpClient(MockEngine) {
        engine {
            addHandler { respond(ByteReadChannel(bytes), status, headers) }
        }
        defaultRequest { url("https://api.vrchat.cloud/api/1/") }
    }

    private fun template(
        id: String,
        vararg assets: InventoryTemplateAsset,
        gradientStart: String? = null,
        gradientEnd: String? = null,
    ) = InventoryTemplateData(
        id = id,
        metadata = InventoryTemplateMetadata(
            assets = assets.toList(),
            gradientStart = gradientStart,
            gradientEnd = gradientEnd,
        ),
    )

    private fun asset(type: String, url: String) = InventoryTemplateAsset(type = type, url = url)

    private companion object {
        const val DECORATION_MAX_BYTES = 20L * 1024L * 1024L
    }
}

private class ResolverFixture {
    val fileSystem = FakeFileSystem()
    val assetStore = MeetupCardAssetStore(fileSystem, "/meetup-assets".toPath())
    val cacheDao = DecorationTemplateCacheDao(MapSettings())
    val source = FakeDecorationTemplateSource()
    val loader = FakeMeetupRemoteBytesLoader()
    val decoder = FakeAnimatedWebpDecoder()

    fun resolver() = DecorationResolver(source, loader, assetStore, decoder, cacheDao)
}

private class FakeDecorationTemplateSource : DecorationTemplateSource {
    val requests = mutableListOf<String>()
    var handler: suspend (String) -> InventoryTemplateData = { templateId ->
        error("No template configured for $templateId")
    }

    override suspend fun getTemplate(templateId: String): InventoryTemplateData {
        requests += templateId
        return handler(templateId)
    }
}

private data class LoadRequest(val url: String, val maxBytes: Long)

private class FakeMeetupRemoteBytesLoader : MeetupRemoteBytesLoader {
    val requests = mutableListOf<LoadRequest>()
    var handler: suspend (String, Long) -> ByteArray = { url, _ ->
        error("No bytes configured for $url")
    }

    override suspend fun load(url: String, maxBytes: Long): ByteArray {
        requests += LoadRequest(url, maxBytes)
        return handler(url, maxBytes)
    }
}

private class FakeAnimatedWebpDecoder : AnimatedWebpDecoder {
    val decoded = mutableListOf<ByteArray>()
    var closeCount = 0
        private set
    var handler: (ByteArray) -> DecodedAnimation = {
        FakeDecodedAnimation(::recordClose)
    }

    override fun decode(bytes: ByteArray): DecodedAnimation {
        decoded += bytes.copyOf()
        return handler(bytes)
    }

    fun recordClose() {
        closeCount++
    }
}

private class FakeDecodedAnimation(
    private val onClose: () -> Unit,
) : DecodedAnimation {
    private var closed = false

    override val frameCount: Int = 2

    override fun durationMillis(index: Int): Int = 16

    override fun start(
        onFrame: (OwnedAnimationFrame) -> Unit,
        onError: (Throwable) -> Unit,
    ) = Unit

    override fun pause() = Unit

    override fun resume() = Unit

    override fun close() {
        if (!closed) {
            closed = true
            onClose()
        }
    }
}
