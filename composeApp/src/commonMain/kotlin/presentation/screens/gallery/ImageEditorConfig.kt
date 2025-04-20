package io.github.vrcmteam.vrcm.presentation.screens.gallery

import io.github.vrcmteam.vrcm.network.api.files.data.FileTagType
import io.github.vrcmteam.vrcm.network.api.files.data.FileTagType.*

/**
 * 图片编辑器配置类
 * 用于为不同的文件标签类型提供不同的编辑和验证规则
 */
class ImageEditorConfig private constructor(
    val aspectRatio: Float,
    val minWidth: Int,
    val minHeight: Int,
    val maxWidth: Int,
    val maxHeight: Int,
    val maxSizeMB: Float,
    val description: String
) {
    companion object {
        /**
         * 根据文件标签类型获取对应的编辑器配置
         */
        fun forFileTagType(fileTagType: FileTagType): ImageEditorConfig {
            return when (fileTagType) {
                Icon -> {
                    // Icon: 必须小于10MB、大于64x64像素且小于2048x2048像素
                    ImageEditorConfig(
                        aspectRatio = 1f, // 1:1 比例
                        minWidth = 64,
                        minHeight = 64,
                        maxWidth = 2048,
                        maxHeight = 2048,
                        maxSizeMB = 10f,
                        description = "图标必须小于10MB、大于64x64像素且小于2048x2048像素"
                    )
                }
                Emoji -> {
                    // Emoji: 应为Sticker 1024x1024的一比一图像
                    ImageEditorConfig(
                        aspectRatio = 1f, // 1:1 比例
                        minWidth = 64,
                        minHeight = 64,
                        maxWidth = 1024,
                        maxHeight = 1024,
                        maxSizeMB = 10f,
                        description = "表情应为1024x1024的1:1图像"
                    )
                }
                Sticker -> {
                    // Sticker: 使用与Emoji相同的规则
                    ImageEditorConfig(
                        aspectRatio = 1f, // 1:1 比例
                        minWidth = 64,
                        minHeight = 64,
                        maxWidth = 1024,
                        maxHeight = 1024,
                        maxSizeMB = 10f,
                        description = "贴图应为1024x1024的1:1图像"
                    )
                }
                Gallery -> {
                    // Gallery: 必须小于10MB、大于64x64像素且小于2048x2048像素，比例为16:9
                    ImageEditorConfig(
                        aspectRatio = 16f / 9f, // 16:9 比例
                        minWidth = 64,
                        minHeight = 64,
                        maxWidth = 2048,
                        maxHeight = 2048,
                        maxSizeMB = 10f,
                        description = "图片必须小于10MB、大于64x64像素且小于2048x2048像素，比例为16:9"
                    )
                }

                Print -> TODO()
            }
        }
    }
}