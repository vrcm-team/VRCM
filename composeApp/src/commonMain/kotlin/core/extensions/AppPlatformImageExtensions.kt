package io.github.vrcmteam.vrcm.core.extensions

import io.github.vrcmteam.vrcm.AppPlatform

/**
 * 保存图片到系统相册
 * @param imageUrl 图片URL
 * @param fileName 文件名
 * @return 保存是否成功
 */
expect suspend fun AppPlatform.saveImageToGallery(imageUrl: String, fileName: String): Boolean

/**
 * 从系统相册选择图片
 * @return 选择的图片文件路径，如果用户取消选择则返回null
 */
expect suspend fun AppPlatform.selectImageFromGallery(): String?

/**
 * 读取文件字节
 * @param filePath 文件路径
 * @return 文件字节数组
 */
expect suspend fun AppPlatform.readFileBytes(filePath: String): ByteArray

/**
 * 获取图片尺寸
 * @param filePath 图片文件路径
 * @return 图片尺寸，如果无法获取则返回null
 */
expect suspend fun AppPlatform.getImageDimensions(filePath: String): Pair<Int, Int>?
