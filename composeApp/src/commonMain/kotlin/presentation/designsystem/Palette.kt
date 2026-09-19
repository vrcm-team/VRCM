package io.github.vrcmteam.vrcm.presentation.designsystem

import kotlin.math.pow

/**
 * 语义色的原始 ARGB 值（纯 Kotlin，无 Compose 依赖），是 [AppColors] 的唯一来源。
 * 名称对齐 UIKit 语义色；一色一义，页面里不要直接写十六进制。
 */
object Palette {
    // ---- 浅色 ----
    const val LightLabel: Long = 0xFF1C1C1E
    const val LightSecondaryLabel: Long = 0xFF5F5F66      // 白底 5.9:1
    const val LightTertiaryLabel: Long = 0xFF8A8A8E       // 占位文字 / 非文字元素
    const val LightSystemBackground: Long = 0xFFFFFFFF
    const val LightSecondarySystemBackground: Long = 0xFFF2F2F7
    const val LightGroupedBackground: Long = 0xFFF2F2F7
    const val LightSecondaryGroupedBackground: Long = 0xFFFFFFFF
    const val LightTertiaryGroupedBackground: Long = 0xFFF2F2F7
    const val LightSeparator: Long = 0xFFD8D8DC
    const val LightFill: Long = 0x24787880                // rgba(120,120,128,0.14)
    const val LightSecondaryFill: Long = 0x14787880       // rgba(120,120,128,0.08)
    const val LightWarningSoft: Long = 0xFFFFF4D6
    const val LightOnWarningSoft: Long = 0xFF8A4B00
    const val LightDestructiveSoft: Long = 0xFFFBE5E3
    const val LightOnDestructiveSoft: Long = 0xFFA32820
    const val LightDestructive: Long = 0xFFC8322B
    const val LightSuccess: Long = 0xFF1F7A33
    const val LightGlassFill: Long = 0x94FFFFFF           // 玻璃填充 58%
    const val LightGlassStroke: Long = 0x12000000
    const val LightGlassHighlight: Long = 0xF2FFFFFF

    // ---- 深色 ----
    const val DarkLabel: Long = 0xFFF2F2F7
    const val DarkSecondaryLabel: Long = 0xFFA1A1A6       // #1C1C1E 底 7.2:1
    const val DarkTertiaryLabel: Long = 0xFF6E6E73
    const val DarkSystemBackground: Long = 0xFF000000
    const val DarkSecondarySystemBackground: Long = 0xFF1C1C1E
    const val DarkGroupedBackground: Long = 0xFF000000
    const val DarkSecondaryGroupedBackground: Long = 0xFF1C1C1E
    const val DarkTertiaryGroupedBackground: Long = 0xFF2C2C2E
    const val DarkElevatedTertiaryBackground: Long = 0xFF3A3A3C
    const val DarkSeparator: Long = 0xFF3A3A3C
    const val DarkFill: Long = 0x52787880                 // rgba(120,120,128,0.32)
    const val DarkSecondaryFill: Long = 0x2E787880        // rgba(120,120,128,0.18)
    const val DarkWarningSoft: Long = 0xFF3A2A12
    const val DarkOnWarningSoft: Long = 0xFFE0A455
    const val DarkDestructiveSoft: Long = 0xFF4A1A17
    const val DarkOnDestructiveSoft: Long = 0xFFFF8A80
    const val DarkDestructive: Long = 0xFFFF6B63
    const val DarkSuccess: Long = 0xFF4CD964
    const val DarkGlassFill: Long = 0xB81E1E20            // 72%：叠在纯白内容上 label 仍 ≥ 4.5:1
    const val DarkGlassStroke: Long = 0x14FFFFFF
    const val DarkGlassHighlight: Long = 0x38FFFFFF

    // ---- 列表行图标色块（iOS 系统色；深浅模式同一组，对齐系统设置）----
    const val RowIconBlue: Long = 0xFF3478F6
    const val RowIconIndigo: Long = 0xFF5856D6
    const val RowIconPurple: Long = 0xFFAF52DE
    const val RowIconPink: Long = 0xFFFF2D55
    const val RowIconRed: Long = 0xFFFF3B30
    const val RowIconOrange: Long = 0xFFFF9500
    const val RowIconGreen: Long = 0xFF2E9E4F
    const val RowIconTeal: Long = 0xFF30B0C7
    const val RowIconGray: Long = 0xFF8E8E93

    /** WCAG 相对亮度（sRGB）。 */
    fun relativeLuminance(argb: Long): Double {
        fun ch(v: Long): Double {
            val c = (v and 0xFF) / 255.0
            return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * ch(argb shr 16) + 0.7152 * ch(argb shr 8) + 0.0722 * ch(argb)
    }

    /** WCAG 对比度；两色都视为不透明。 */
    fun contrast(fg: Long, bg: Long): Double {
        val l1 = relativeLuminance(fg)
        val l2 = relativeLuminance(bg)
        val hi = maxOf(l1, l2)
        val lo = minOf(l1, l2)
        return (hi + 0.05) / (lo + 0.05)
    }
}

/** 一套强调色的四件套：着色面 / 面上的字、浅底面 / 浅底面上的字。 */
data class AccentColors(val tint: Long, val onTint: Long, val tintSoft: Long, val onTintSoft: Long)

/**
 * 一个内置主题色：[id] 与设置里存的主题名一致；浅 / 深色各一套。
 * [secondaryLight] / [secondaryDark] 是配套的第二强调色，只用于标签、角标这类需要与主强调色区分的小面积元素。
 */
data class AccentSpec(
    val id: String,
    val light: AccentColors,
    val dark: AccentColors,
    val secondaryLight: AccentColors,
    val secondaryDark: AccentColors,
)

/**
 * 内置主题色（设置 → 主题颜色）。
 * 浅色 tint 要同时满足"白字压在上面 ≥ 4.5"和"作为纯文字按钮压在白底上 ≥ 4.5"，所以都是偏深的饱和色；
 * 深色 tint 反过来要亮，onTint 用同色系深色而不是纯黑。
 */
object Accents {
    private val pinkLight = AccentColors(tint = 0xFFC2185B, onTint = 0xFFFFFFFF, tintSoft = 0xFFFCE4EC, onTintSoft = 0xFF8E0F3F)
    private val pinkDark = AccentColors(tint = 0xFFF48FB1, onTint = 0xFF4A0A25, tintSoft = 0xFF3E1424, onTintSoft = 0xFFF8A5C2)
    private val tealLight = AccentColors(tint = 0xFF0D7680, onTint = 0xFFFFFFFF, tintSoft = 0xFFE3F3F4, onTintSoft = 0xFF0A5F67)
    private val tealDark = AccentColors(tint = 0xFF4FC3CC, onTint = 0xFF00363B, tintSoft = 0xFF0B3F44, onTintSoft = 0xFF7FD6DD)
    private val blueLight = AccentColors(tint = 0xFF1E63C9, onTint = 0xFFFFFFFF, tintSoft = 0xFFE4EEFB, onTintSoft = 0xFF174F9E)
    private val blueDark = AccentColors(tint = 0xFF7AB4FF, onTint = 0xFF0B2B5E, tintSoft = 0xFF10284A, onTintSoft = 0xFF9CC6FF)
    private val purpleLight = AccentColors(tint = 0xFF6F3DC7, onTint = 0xFFFFFFFF, tintSoft = 0xFFEEE7FA, onTintSoft = 0xFF552E98)
    private val purpleDark = AccentColors(tint = 0xFFB79CF2, onTint = 0xFF2C1462, tintSoft = 0xFF261A45, onTintSoft = 0xFFC9B5F7)
    private val orangeLight = AccentColors(tint = 0xFFB3520A, onTint = 0xFFFFFFFF, tintSoft = 0xFFFDEBDC, onTintSoft = 0xFF8A3E05)
    private val orangeDark = AccentColors(tint = 0xFFFFA35C, onTint = 0xFF4A2200, tintSoft = 0xFF3E2410, onTintSoft = 0xFFFFB985)
    private val greenLight = AccentColors(tint = 0xFF2E7D32, onTint = 0xFFFFFFFF, tintSoft = 0xFFE6F3E7, onTintSoft = 0xFF1F5D24)
    private val greenDark = AccentColors(tint = 0xFF7CD98A, onTint = 0xFF0F3A16, tintSoft = 0xFF123A1A, onTintSoft = 0xFF9BE3A6)

    // id 就是设置里存的主题名：Blue / Pink / Green 沿用旧值，旧的 "Default" 已不存在，读到时回落到 [default]
    val blue = AccentSpec("Blue", blueLight, blueDark, secondaryLight = purpleLight, secondaryDark = purpleDark)
    val purple = AccentSpec("Purple", purpleLight, purpleDark, secondaryLight = pinkLight, secondaryDark = pinkDark)
    val pink = AccentSpec("Pink", pinkLight, pinkDark, secondaryLight = orangeLight, secondaryDark = orangeDark)
    val orange = AccentSpec("Orange", orangeLight, orangeDark, secondaryLight = blueLight, secondaryDark = blueDark)
    val green = AccentSpec("Green", greenLight, greenDark, secondaryLight = tealLight, secondaryDark = tealDark)
    val teal = AccentSpec("Teal", tealLight, tealDark, secondaryLight = purpleLight, secondaryDark = purpleDark)

    val all: List<AccentSpec> = listOf(blue, purple, pink, orange, green, teal)
    val default: AccentSpec = blue
}
