package io.github.vrcmteam.vrcm.service

import io.github.vrcmteam.vrcm.core.shared.SharedFlowCentre
import io.github.vrcmteam.vrcm.network.api.files.FileApi
import io.github.vrcmteam.vrcm.network.api.files.FileApi.Companion.findFileId
import io.github.vrcmteam.vrcm.network.api.files.data.PlatformFileSize
import io.github.vrcmteam.vrcm.network.api.files.data.PlatformType
import io.github.vrcmteam.vrcm.network.api.worlds.data.WorldData
import io.github.vrcmteam.vrcm.presentation.compoments.ToastText
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
        val platformPackages = worldData.unityPackages
            .groupBy { it.platform }
            .mapValues { (_, packages) ->
                packages.sortedByDescending { it.createdAt }
            }

        // 处理每个支持的平台
        val deferredResults = PlatformType.entries.mapNotNull { platform ->
            // 获取此平台的最新包
            val latestPackage = platformPackages[platform]?.firstOrNull()

            // 如果此平台有包且有assetUrl，则获取其文件大小
            latestPackage?.assetUrl?.let { assetUrl ->
                async { getPlatformFileSize(platform, assetUrl).getOrNull() }
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
    private suspend fun getPlatformFileSize(platform: PlatformType, assetUrl: String): Result<PlatformFileSize> =
        runCatching {
            val fileId = findFileId(assetUrl)
            if (fileId.isEmpty()) error(IllegalArgumentException("Invalid resource URL: $assetUrl"))

            val fileInfoResult = fileApi.getFileInfo(fileId)
            if (fileInfoResult.isFailure) error(fileInfoResult.exceptionOrNull() ?: Exception("failed to get file information"))

            val fileInfo = fileInfoResult.getOrThrow()
            // 获取最新版本
            val latestVersion = fileInfo.versions.maxByOrNull { it.version }
                ?: error("The version of file $fileId was not found")

            val sizeInBytes = latestVersion.file.sizeInBytes
            val displayName = platform.name

            PlatformFileSize(platform, sizeInBytes, displayName)
        }.onFailure {
            SharedFlowCentre.toastText.emit(ToastText.Error(it.message ?: "Failed to get platform file size"))
        }
}
