package io.github.vrcmteam.vrcm.storage.meetup

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okio.ByteString.Companion.toByteString
import okio.FileSystem
import okio.IOException
import okio.Path
import okio.Sink
import okio.buffer
import okio.use
import kotlin.random.Random

/** Profile Decoration 素材在本地仓库中的逻辑类型。 */
enum class DecorationAssetType(val fileName: String) {
    MainAnimation("main-animation.webp"),
    Base("base.webp"),
}

private class ContentHashMismatchException : IOException(
    "Meetup asset content hash verification failed",
)

/** 在应用私有目录中原子管理会面身份卡照片与装饰素材。 */
class MeetupCardAssetStore(
    private val fileSystem: FileSystem,
    private val root: Path,
) {
    suspend fun writePhoto(
        ownerId: String,
        bytes: ByteArray,
        extension: String,
    ): MeetupAssetRef = withContext(meetupCardAssetIoDispatcher) {
        requireValidId(ownerId, "ownerId")
        val normalizedExtension = normalizeExtension(extension)
        val sha256 = bytes.sha256()
        val relativePath = "accounts/$ownerId/photos/$sha256.$normalizedExtension"
        writeContentAddressed(relativePath, sha256, bytes)
        MeetupAssetRef(relativePath, sha256)
    }

    suspend fun writeDecoration(
        templateId: String,
        type: DecorationAssetType,
        bytes: ByteArray,
    ): MeetupAssetRef = withContext(meetupCardAssetIoDispatcher) {
        requireValidId(templateId, "templateId")
        val sha256 = bytes.sha256()
        val relativePath = "decorations/$templateId/$sha256/${type.fileName}"
        writeContentAddressed(relativePath, sha256, bytes)
        MeetupAssetRef(relativePath, sha256)
    }

    suspend fun read(ref: MeetupAssetRef): ByteArray = withContext(meetupCardAssetIoDispatcher) {
        val path = resolve(ref)
        val bytes = fileSystem.read(path) { readByteArray() }
        if (bytes.sha256() != ref.sha256) {
            throw IOException("Meetup asset content hash does not match its reference")
        }
        bytes
    }

    fun model(ref: MeetupAssetRef): String = resolve(ref).toString()

    /** 仅检查引用文件存在且非空，不做 hash 校验；供离线恢复快速过滤悬挂引用。 */
    fun exists(ref: MeetupAssetRef): Boolean {
        val path = try {
            resolve(ref)
        } catch (_: IllegalArgumentException) {
            return false
        }
        return try {
            (fileSystem.metadataOrNull(path)?.size ?: 0L) > 0L
        } catch (_: IOException) {
            false
        }
    }

    suspend fun deletePhoto(ref: MeetupAssetRef) = withContext(meetupCardAssetIoDispatcher) {
        require(ref.relativePath.startsWith("accounts/")) { "Asset reference is not a photo" }
        fileSystem.delete(resolve(ref), mustExist = false)
    }

    suspend fun deleteAccount(ownerId: String) = withContext(meetupCardAssetIoDispatcher) {
        // 非法 ID 不可能有已写入的数据；静默返回避免中断账号移除流程。
        if (!ID_PATTERN.matches(ownerId)) return@withContext
        fileSystem.deleteRecursively(root / "accounts" / ownerId, mustExist = false)
    }

    suspend fun clearDecorationCache() = withContext(meetupCardAssetIoDispatcher) {
        fileSystem.deleteRecursively(root / "decorations", mustExist = false)
    }

    suspend fun pruneDecorations(retainedTemplateIds: Set<String>) = withContext(meetupCardAssetIoDispatcher) {
        retainedTemplateIds.forEach { requireValidId(it, "templateId") }
        val decorations = root / "decorations"
        if (fileSystem.metadataOrNull(decorations)?.isDirectory != true) return@withContext
        fileSystem.list(decorations).forEach { templateDirectory ->
            if (templateDirectory.name !in retainedTemplateIds) {
                fileSystem.deleteRecursively(templateDirectory, mustExist = false)
            }
        }
    }

    private suspend fun writeContentAddressed(
        relativePath: String,
        sha256: String,
        bytes: ByteArray,
    ) {
        val final = root / relativePath
        if (fileSystem.exists(final)) {
            try {
                verifyHash(final, sha256)
                return
            } catch (_: ContentHashMismatchException) {
                // Known-good bytes below can safely replace corrupt content at the same hash path.
            }
        }

        val parent = requireNotNull(final.parent)
        fileSystem.createDirectories(parent)
        val (temporary, temporarySink) = openUniqueTemporarySink(parent, final.name)
        var primaryFailure: Throwable? = null
        try {
            temporarySink.buffer().use { it.write(bytes) }
            currentCoroutineContext().ensureActive()
            verifyHash(temporary, sha256)
            currentCoroutineContext().ensureActive()
            try {
                fileSystem.atomicMove(temporary, final)
            } catch (moveFailure: IOException) {
                try {
                    verifyHash(final, sha256)
                } catch (verificationFailure: Throwable) {
                    if (verificationFailure !== moveFailure) {
                        moveFailure.addSuppressed(verificationFailure)
                    }
                    throw moveFailure
                }
            }
        } catch (failure: Throwable) {
            primaryFailure = failure
            throw failure
        } finally {
            try {
                fileSystem.delete(temporary, mustExist = false)
            } catch (cleanupFailure: Throwable) {
                val failure = primaryFailure
                if (failure == null) {
                    throw cleanupFailure
                }
                if (cleanupFailure !== failure) {
                    failure.addSuppressed(cleanupFailure)
                }
            }
        }
    }

    private fun verifyHash(path: Path, expectedSha256: String) {
        val actualSha256 = fileSystem.read(path) { readByteArray() }.sha256()
        if (actualSha256 != expectedSha256) {
            throw ContentHashMismatchException()
        }
    }

    private fun openUniqueTemporarySink(parent: Path, finalName: String): Pair<Path, Sink> {
        var lastCollisionFailure: IOException? = null
        repeat(MAX_TEMP_NAME_ATTEMPTS) {
            val randomSuffix = Random.nextBytes(TEMP_RANDOM_BYTES).toByteString().hex()
            val candidate = parent / ".$finalName.$randomSuffix.tmp"
            try {
                return candidate to fileSystem.sink(candidate, mustCreate = true)
            } catch (createFailure: IOException) {
                val candidateExists = try {
                    fileSystem.exists(candidate)
                } catch (existenceFailure: Throwable) {
                    if (existenceFailure !== createFailure) {
                        createFailure.addSuppressed(existenceFailure)
                    }
                    throw createFailure
                }
                if (!candidateExists) throw createFailure
                lastCollisionFailure = createFailure
            }
        }
        throw checkNotNull(lastCollisionFailure)
    }

    private fun resolve(ref: MeetupAssetRef): Path {
        require(SHA256_PATTERN.matches(ref.sha256)) { "Invalid meetup asset SHA-256" }
        require('\\' !in ref.relativePath) { "Meetup asset path must use forward slashes" }
        val segments = ref.relativePath.split('/')
        require(segments.none { it.isEmpty() || it == "." || it == ".." }) {
            "Invalid meetup asset path"
        }

        when (segments.firstOrNull()) {
            "accounts" -> validatePhotoRef(segments, ref.sha256)
            "decorations" -> validateDecorationRef(segments, ref.sha256)
            else -> throw IllegalArgumentException("Unknown meetup asset path")
        }
        return segments.fold(root) { path, segment -> path / segment }
    }

    private fun validatePhotoRef(segments: List<String>, sha256: String) {
        require(segments.size == PHOTO_PATH_SEGMENTS && segments[2] == "photos") {
            "Invalid meetup photo path"
        }
        requireValidId(segments[1], "ownerId")
        val fileName = segments[3]
        val extension = fileName.substringAfterLast('.', missingDelimiterValue = "")
        require(extension in ALLOWED_PHOTO_EXTENSIONS && fileName == "$sha256.$extension") {
            "Meetup photo path does not match its reference"
        }
    }

    private fun validateDecorationRef(segments: List<String>, sha256: String) {
        require(segments.size == DECORATION_PATH_SEGMENTS) { "Invalid decoration asset path" }
        requireValidId(segments[1], "templateId")
        require(segments[2] == sha256) { "Decoration path does not match its reference" }
        require(DecorationAssetType.entries.any { it.fileName == segments[3] }) {
            "Unknown decoration asset type"
        }
    }

    private companion object {
        const val PHOTO_PATH_SEGMENTS = 4
        const val DECORATION_PATH_SEGMENTS = 4
        const val TEMP_RANDOM_BYTES = 16
        const val MAX_TEMP_NAME_ATTEMPTS = 8
        val ID_PATTERN = Regex("[A-Za-z0-9_-]+")
        val SHA256_PATTERN = Regex("[a-f0-9]{64}")
        val ALLOWED_PHOTO_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp")

        fun requireValidId(value: String, name: String) {
            require(ID_PATTERN.matches(value)) { "$name contains unsupported characters" }
        }

        fun normalizeExtension(extension: String): String {
            val normalized = extension.trim().removePrefix(".").lowercase()
            require(normalized in ALLOWED_PHOTO_EXTENSIONS) {
                "Unsupported meetup photo extension"
            }
            return normalized
        }

        fun ByteArray.sha256(): String = toByteString().sha256().hex()
    }
}
