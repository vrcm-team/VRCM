package io.github.vrcmteam.vrcm.service

import io.github.vrcmteam.vrcm.core.shared.SharedFlowCentre
import io.github.vrcmteam.vrcm.network.api.files.FileApi
import io.github.vrcmteam.vrcm.network.api.files.data.PlatformFileSize
import io.github.vrcmteam.vrcm.network.api.files.data.PlatformType
import io.github.vrcmteam.vrcm.network.api.worlds.data.UnityPackage
import io.github.vrcmteam.vrcm.network.api.worlds.data.WorldData
import io.github.vrcmteam.vrcm.presentation.compoments.ToastText
import io.ktor.http.Url
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/** 平台大小查询结果，以及结果是否可以作为完整缓存保存。 */
data class WorldPlatformFileSizesResult(
    val platformFileSizes: List<PlatformFileSize>,
    val isComplete: Boolean,
)

/**
 * 用于处理世界平台相关操作的服务
 */
class WorldPlatformService(
    private val fileApi: FileApi,
    private val authService: AuthService,
) {

    /**
     * 获取世界的所有平台文件大小
     * @param worldData 世界数据
     * @return 成功解析的平台大小，以及所有已发起查询是否都成功
     */
    suspend fun getWorldPlatformFileSizes(worldData: WorldData): WorldPlatformFileSizesResult = coroutineScope {
        // 按平台对UnityPackages进行分组，并按创建日期排序（最新的在前）
        val platformPackages = worldData.unityPackages.platformPackages

        // 处理每个支持的平台
        val deferredResults = supportedPlatforms.mapNotNull { (platform, displayName) ->
            // 获取此平台的最新包
            val latestPackage = platformPackages[platform]?.firstOrNull()

            // 如果此平台有包且有assetUrl，则获取其文件大小
            latestPackage?.assetUrl?.let { assetUrl ->
                async { getPlatformFileSize(platform, displayName, assetUrl) }
            }
        }

        val results = deferredResults.awaitAll()
        WorldPlatformFileSizesResult(
            platformFileSizes = results.mapNotNull { it.getOrNull() },
            isComplete = results.all { it.isSuccess },
        )
    }

    /**
     * 从UnityPackage获取特定平台的文件大小
     * @param platform 平台类型
     * @param assetUrl UnityPackage中的资源URL
     * @return 平台文件大小信息
     */
    private suspend fun getPlatformFileSize(
        platform: PlatformType,
        displayName: String,
        assetUrl: String,
    ): Result<PlatformFileSize> = try {
        val reference = parseFileReference(assetUrl)
            ?: throw IllegalArgumentException("Invalid resource URL: $assetUrl")

        val fileInfo = authService.reTryAuth { fileApi.getFileInfo(reference.fileId) }.getOrThrow()
        val packageVersion = fileInfo.versions.firstOrNull { it.version == reference.version }
            ?: error("Version ${reference.version} of file ${reference.fileId} was not found")
        val sizeInBytes = packageVersion.file?.sizeInBytes
            ?: error("Version ${reference.version} of file ${reference.fileId} has no file data")

        Result.success(
            PlatformFileSize(
                platform = platform,
                sizeInBytes = sizeInBytes,
                displayName = displayName,
            )
        )
    } catch (error: CancellationException) {
        throw error
    } catch (error: Throwable) {
        SharedFlowCentre.toastText.emit(
            ToastText.Error(error.message ?: "Failed to get platform file size")
        )
        Result.failure(error)
    }

    private fun parseFileReference(assetUrl: String): FileReference? {
        val segments = runCatching { Url(assetUrl).segments }.getOrNull() ?: return null
        return segments.windowed(FILE_REFERENCE_SEGMENT_COUNT).firstNotNullOfOrNull {
            (resource, fileId, versionText) ->
            val version = versionText.toIntOrNull()?.takeIf { it > 0 }
            if (resource == "file" && fileIdRegex.matches(fileId) && version != null) {
                FileReference(fileId, version)
            } else {
                null
            }
        }
    }

    private companion object {
        private const val FILE_REFERENCE_SEGMENT_COUNT = 3
        private val fileIdRegex = Regex("file_[\\w-]+")
        private val supportedPlatforms = listOf(
            PlatformType.Windows to "PC",
            PlatformType.Android to "Android",
            PlatformType.Ios to "iOS",
        )
    }
}

private data class FileReference(
    val fileId: String,
    val version: Int,
)

val List<UnityPackage>.platformPackages: Map<PlatformType, List<UnityPackage>>
    get() = this.groupBy { it.platform }
        .mapValues { (_, packages) ->
            packages.sortedByDescending { it.createdAt }
        }
