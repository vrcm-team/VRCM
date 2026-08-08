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
import io.ktor.client.plugins.timeout
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
    /** Animated 模式下运行期解码失败时可用的静态 base 兜底。 */
    val staticFallback: MeetupAssetRef? = null,
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

        return client.prepareGet(url) {
            // 共享 client 的 15 秒整体超时对 20/50 MiB 素材下载不可行；按弱网大文件放宽。
            timeout {
                requestTimeoutMillis = ASSET_REQUEST_TIMEOUT_MILLIS
                socketTimeoutMillis = ASSET_SOCKET_TIMEOUT_MILLIS
            }
        }.execute { response ->
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
    /** Restores persisted decoration references without network access or byte-level reads. */
    fun restoreCached(templateIds: List<String>): Map<String, ResolvedDecoration> = buildMap {
        templateIds.asSequence()
            .map(String::trim)
            .filter(String::isNotEmpty)
            .distinct()
            .forEach { templateId ->
                if (!ID_PATTERN.matches(templateId)) {
                    // 与 refresh 一致：非法 ID 给出明确降级状态，而不是让槽位悄然消失。
                    put(templateId, unavailable(templateId))
                    return@forEach
                }
                val cached = cacheDao.load(templateId) ?: return@forEach
                // 元数据可能引用已被普通缓存清理/prune 删除的文件，恢复前过滤悬挂引用。
                val mainAsset = cached.mainAnimationAsset?.takeIf(assetStore::exists)
                val baseAsset = cached.baseAsset?.takeIf(assetStore::exists)
                val resolved = when {
                    mainAsset != null -> cached.resolved(
                        DecorationRenderMode.Animated,
                        mainAsset,
                        staticFallback = baseAsset,
                    )
                    baseAsset != null -> cached.resolved(DecorationRenderMode.Static, baseAsset)
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
            return workingCache.resolved(
                DecorationRenderMode.Animated,
                mainAsset,
                staticFallback = workingCache.baseAsset,
            )
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
            // 缺失/为空的响应 ID 同样不可信：截断或网关错误页不得覆盖有效缓存。
            require(template.id == templateId) {
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
        staticFallback: MeetupAssetRef? = null,
    ) = ResolvedDecoration(
        templateId = templateId,
        mode = mode,
        asset = asset,
        gradientStart = gradientStart,
        gradientEnd = gradientEnd,
        staticFallback = staticFallback,
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

private const val ASSET_REQUEST_TIMEOUT_MILLIS = 120_000L
private const val ASSET_SOCKET_TIMEOUT_MILLIS = 30_000L
