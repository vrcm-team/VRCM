package io.github.vrcmteam.vrcm.service.meetup

import io.github.vrcmteam.vrcm.network.api.inventory.InventoryApi
import io.github.vrcmteam.vrcm.network.api.inventory.data.InventoryTemplateData
import io.github.vrcmteam.vrcm.network.supports.VRCApiException
import io.github.vrcmteam.vrcm.presentation.screens.meetup.animation.AnimatedWebpDecoder
import io.github.vrcmteam.vrcm.storage.meetup.DecorationAssetType
import io.github.vrcmteam.vrcm.storage.meetup.DecorationTemplateCache
import io.github.vrcmteam.vrcm.storage.meetup.DecorationTemplateCacheDao
import io.github.vrcmteam.vrcm.storage.meetup.MeetupAssetRef
import io.github.vrcmteam.vrcm.storage.meetup.MeetupCardAssetStore
import io.ktor.client.HttpClient
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.contentLength
import io.ktor.utils.io.readRemaining
import kotlinx.io.readByteArray
import kotlinx.coroutines.CancellationException
import okio.IOException

/** Rendering strategy selected for a resolved profile decoration. */
enum class DecorationRenderMode {
    Animated,
    Static,
    Unavailable,
}

/** A decoration asset and gradient ready for the meetup card renderer. */
data class ResolvedDecoration(
    val templateId: String,
    val mode: DecorationRenderMode,
    val asset: MeetupAssetRef?,
    val gradientStart: String,
    val gradientEnd: String,
)

/** Supplies inventory template metadata without coupling the resolver to a network implementation. */
interface DecorationTemplateSource {
    suspend fun getTemplate(templateId: String): InventoryTemplateData
}

/** Adapts the VRChat inventory API to the decoration template source boundary. */
class InventoryDecorationTemplateSource(
    private val inventoryApi: InventoryApi,
) : DecorationTemplateSource {
    override suspend fun getTemplate(templateId: String): InventoryTemplateData =
        inventoryApi.getTemplate(templateId)
}

/** Loads remote meetup assets while enforcing a caller-provided byte limit. */
fun interface MeetupRemoteBytesLoader {
    suspend fun load(url: String, maxBytes: Long): ByteArray
}

/** Streams remote meetup assets through an injected Ktor client with a strict size bound. */
class HttpMeetupRemoteBytesLoader(
    private val client: HttpClient,
) : MeetupRemoteBytesLoader {
    override suspend fun load(url: String, maxBytes: Long): ByteArray {
        require(maxBytes > 0 && maxBytes < Int.MAX_VALUE) {
            "maxBytes must be positive and smaller than Int.MAX_VALUE"
        }

        return client.prepareGet(url).execute { response ->
            if ((response.contentLength() ?: 0L) > maxBytes) {
                throw IOException("Remote meetup asset exceeds $maxBytes bytes")
            }

            val bytes = response.bodyAsChannel()
                .readRemaining(maxBytes + 1L)
                .readByteArray()
            if (bytes.size.toLong() > maxBytes) {
                throw IOException("Remote meetup asset exceeds $maxBytes bytes")
            }
            if (response.status.value != 200) {
                throw VRCApiException(
                    description = response.status.description,
                    code = response.status.value,
                    bodyText = bytes.decodeToString(),
                )
            }
            bytes
        }
    }
}

/** Resolves remote or cached decoration templates into independently usable render assets. */
class DecorationResolver(
    private val source: DecorationTemplateSource,
    private val remoteBytesLoader: MeetupRemoteBytesLoader,
    private val assetStore: MeetupCardAssetStore,
    private val animatedWebpDecoder: AnimatedWebpDecoder,
    private val cacheDao: DecorationTemplateCacheDao,
) {
    /** Restores persisted decoration references without network or file reads. */
    fun restoreCached(templateIds: List<String>): Map<String, ResolvedDecoration> = buildMap {
        templateIds.asSequence()
            .map(String::trim)
            .filter(ID_PATTERN::matches)
            .distinct()
            .forEach { templateId ->
                val cached = cacheDao.load(templateId) ?: return@forEach
                val resolved = when {
                    cached.mainAnimationAsset != null -> cached.resolved(
                        DecorationRenderMode.Animated,
                        cached.mainAnimationAsset,
                    )
                    cached.baseAsset != null -> cached.resolved(
                        DecorationRenderMode.Static,
                        cached.baseAsset,
                    )
                    else -> cached.resolved(DecorationRenderMode.Unavailable, null)
                }
                put(templateId, resolved)
            }
    }

    suspend fun refresh(templateIds: List<String>): Map<String, ResolvedDecoration> = buildMap {
        templateIds.asSequence()
            .map(String::trim)
            .filter(String::isNotEmpty)
            .distinct()
            .forEach { templateId ->
                put(
                    templateId,
                    if (ID_PATTERN.matches(templateId)) {
                        resolveIndependently(templateId)
                    } else {
                        unavailable(templateId)
                    },
                )
            }
    }

    private suspend fun resolveIndependently(templateId: String): ResolvedDecoration = try {
        resolve(templateId)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        unavailable(templateId)
    }

    private suspend fun resolve(templateId: String): ResolvedDecoration {
        val cached = cacheDao.load(templateId)
        val remote = fetchRemote(templateId)
        val workingCache = remote?.toCache(templateId, cached) ?: cached
            ?: return unavailable(templateId)

        val mainAsset = resolveMainAnimation(
            templateId = templateId,
            url = workingCache.mainAnimationUrl,
            cachedAsset = workingCache.mainAnimationAsset,
        )
        if (mainAsset != null) {
            saveIgnoringFailure(workingCache.copy(mainAnimationAsset = mainAsset))
            return workingCache.resolved(DecorationRenderMode.Animated, mainAsset)
        }

        val baseAsset = resolveBase(
            templateId = templateId,
            url = workingCache.baseUrl,
            cachedAsset = workingCache.baseAsset,
        )
        val updatedCache = workingCache.copy(
            mainAnimationAsset = null,
            baseAsset = baseAsset,
        )
        saveIgnoringFailure(updatedCache)
        return if (baseAsset != null) {
            updatedCache.resolved(DecorationRenderMode.Static, baseAsset)
        } else {
            updatedCache.resolved(DecorationRenderMode.Unavailable, null)
        }
    }

    private suspend fun fetchRemote(templateId: String): InventoryTemplateData? = try {
        source.getTemplate(templateId).also { template ->
            require(template.id.isEmpty() || template.id == templateId) {
                "Inventory template response ID does not match the request"
            }
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        null
    }

    private suspend fun resolveMainAnimation(
        templateId: String,
        url: String,
        cachedAsset: MeetupAssetRef?,
    ): MeetupAssetRef? {
        if (url.isBlank()) return null
        cachedAsset?.let { ref ->
            attempt {
                val bytes = assetStore.read(ref)
                require(bytes.isNotEmpty()) { "Cached animation is empty" }
                validateAnimation(bytes)
                ref
            }?.let { return it }
        }
        return attempt {
            val bytes = remoteBytesLoader.load(url, MAX_DECORATION_BYTES)
            require(bytes.isNotEmpty()) { "Downloaded animation is empty" }
            validateAnimation(bytes)
            assetStore.writeDecoration(templateId, DecorationAssetType.MainAnimation, bytes)
        }
    }

    private suspend fun resolveBase(
        templateId: String,
        url: String,
        cachedAsset: MeetupAssetRef?,
    ): MeetupAssetRef? {
        if (url.isBlank()) return null
        cachedAsset?.let { ref ->
            attempt {
                require(assetStore.read(ref).isNotEmpty()) { "Cached base is empty" }
                ref
            }?.let { return it }
        }
        return attempt {
            val bytes = remoteBytesLoader.load(url, MAX_DECORATION_BYTES)
            require(bytes.isNotEmpty()) { "Downloaded base is empty" }
            assetStore.writeDecoration(templateId, DecorationAssetType.Base, bytes)
        }
    }

    private fun validateAnimation(bytes: ByteArray) {
        animatedWebpDecoder.decode(bytes).close()
    }

    private suspend fun <T> attempt(block: suspend () -> T): T? = try {
        block()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        null
    }

    private fun saveIgnoringFailure(cache: DecorationTemplateCache) {
        try {
            cacheDao.save(cache)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            Unit
        }
    }

    private fun InventoryTemplateData.toCache(
        requestedTemplateId: String,
        cached: DecorationTemplateCache?,
    ): DecorationTemplateCache {
        val mainAnimationUrl = firstAssetUrl(MAIN_ANIMATION_TYPE)
        val baseUrl = firstAssetUrl(BASE_TYPE)
        return DecorationTemplateCache(
            templateId = requestedTemplateId,
            mainAnimationUrl = mainAnimationUrl,
            baseUrl = baseUrl,
            mainAnimationAsset = cached?.mainAnimationAsset
                ?.takeIf { cached.mainAnimationUrl == mainAnimationUrl },
            baseAsset = cached?.baseAsset?.takeIf { cached.baseUrl == baseUrl },
            gradientStart = metadata.gradientStart.orEmpty(),
            gradientEnd = metadata.gradientEnd.orEmpty(),
        )
    }

    private fun InventoryTemplateData.firstAssetUrl(type: String): String = metadata.assets
        .asSequence()
        .filter { it.type == type }
        .map { it.url.trim() }
        .firstOrNull(String::isNotEmpty)
        .orEmpty()

    private fun DecorationTemplateCache.resolved(
        mode: DecorationRenderMode,
        asset: MeetupAssetRef?,
    ) = ResolvedDecoration(
        templateId = templateId,
        mode = mode,
        asset = asset,
        gradientStart = gradientStart,
        gradientEnd = gradientEnd,
    )

    private fun unavailable(templateId: String) = ResolvedDecoration(
        templateId = templateId,
        mode = DecorationRenderMode.Unavailable,
        asset = null,
        gradientStart = "",
        gradientEnd = "",
    )

    private companion object {
        const val MAIN_ANIMATION_TYPE = "mainAnimation"
        const val BASE_TYPE = "base"
        const val MAX_DECORATION_BYTES = 20L * 1024L * 1024L
        val ID_PATTERN = Regex("[A-Za-z0-9_-]+")
    }
}
