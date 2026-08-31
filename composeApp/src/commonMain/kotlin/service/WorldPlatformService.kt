package io.github.vrcmteam.vrcm.service

import io.github.vrcmteam.vrcm.core.shared.SharedFlowCentre
import io.github.vrcmteam.vrcm.network.api.files.FileApi
import io.github.vrcmteam.vrcm.network.api.files.FileApi.Companion.findFileId
import io.github.vrcmteam.vrcm.network.api.files.FileApi.Companion.findFileVersion
import io.github.vrcmteam.vrcm.network.api.files.data.PlatformFileSize
import io.github.vrcmteam.vrcm.network.api.files.data.PlatformType
import io.github.vrcmteam.vrcm.network.api.worlds.data.UnityPackage
import io.github.vrcmteam.vrcm.network.api.worlds.data.WorldData
import io.github.vrcmteam.vrcm.presentation.compoments.ToastText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/**
 * 用于处理世界平台相关操作的服务
 */
class WorldPlatformService(private val fileApi: FileApi) {

    /**
     * 获取世界的所有平台文件大小
     * @param worldData 世界数据
     * @return 平台文件大小列表
     */
    suspend fun getWorldPlatformFileSizes(worldData: WorldData): List<PlatformFileSize> = coroutineScope {
        // 按平台对UnityPackages进行分组，并按创建日期排序（最新的在前）
        val platformPackages = worldData.unityPackages.platformPackages

        // 处理每个支持的平台
        val deferredResults = supportedPlatforms.mapNotNull { (platform, displayName) ->
            // 获取此平台的最新包
            val latestPackage = platformPackages[platform]?.firstOrNull()

            // 如果此平台有包且有assetUrl，则获取其文件大小
            latestPackage?.assetUrl?.let { assetUrl ->
                async { getPlatformFileSize(platform, displayName, assetUrl).getOrNull() }
            }
        }

        // 等待所有请求完成并过滤掉空值
        deferredResults.awaitAll().filterNotNull().toList()
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
        val fileId = findFileId(assetUrl)
        if (fileId.isEmpty()) throw IllegalArgumentException("Invalid resource URL: $assetUrl")
        val fileVersion = findFileVersion(assetUrl).toIntOrNull()
            ?: throw IllegalArgumentException("Invalid resource version: $assetUrl")

        val fileInfo = fileApi.getFileInfo(fileId).getOrThrow()
        val packageVersion = fileInfo.versions.firstOrNull { it.version == fileVersion }
            ?: error("Version $fileVersion of file $fileId was not found")

        Result.success(
            PlatformFileSize(
                platform = platform,
                sizeInBytes = packageVersion.file.sizeInBytes,
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

    private companion object {
        private val supportedPlatforms = listOf(
            PlatformType.Windows to "PC",
            PlatformType.Android to "Android",
            PlatformType.Ios to "iOS",
        )
    }
}

val List<UnityPackage>.platformPackages: Map<PlatformType, List<UnityPackage>>
    get() = this.groupBy { it.platform }
        .mapValues { (_, packages) ->
            packages.sortedByDescending { it.createdAt }
        }
