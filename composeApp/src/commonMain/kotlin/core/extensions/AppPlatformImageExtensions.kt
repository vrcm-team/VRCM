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
 * 保存应用自己生成的图片字节到系统相册（Desktop 落到用户图片目录）。
 * 与 [saveImageToGallery] 的区别是内容不来自网络，不需要再下载一次。
 * @param bytes 已编码的图片字节（PNG/JPEG）
 * @param fileName 文件名，扩展名决定写入的 MIME 类型
 * @return 保存是否成功
 */
expect suspend fun AppPlatform.saveImageBytesToGallery(bytes: ByteArray, fileName: String): Boolean

/**
 * 通过系统分享面板分享已编码的图片，不写入系统相册。
 */
expect suspend fun AppPlatform.shareImageBytes(bytes: ByteArray, fileName: String): Boolean

/**
 * 下载并通过系统分享面板分享图片，不写入系统相册。
 */
expect suspend fun AppPlatform.shareImage(imageUrl: String, fileName: String): Boolean

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
