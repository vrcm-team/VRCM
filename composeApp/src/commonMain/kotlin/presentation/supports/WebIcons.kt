package io.github.vrcmteam.vrcm.presentation.supports

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp
import io.ktor.http.URLBuilder

object WebIcons {
    fun selectIcon(link: String): ImageVector? =
        when (URLBuilder(link).host) {
            "twitter.com" -> TwitterIcon
            "steamcommunity.com" -> SteamCommunityIcon
            "discordapp.com" -> DiscordAppIcon
            "bilibili.com", "space.bilibili.com" -> BilibiliIcon
            "www.youtube.com" -> YoutubeIcon
            "jq.qq.com", "qm.qq.com" -> QQIcon
            "github.com" -> GithubIcon
            else -> null
        }

    val TwitterIcon: ImageVector by lazy {
        ImageVector.Builder(
            name = "TwitterWebIcon",
            defaultWidth = 50.dp,
            defaultHeight = 50.dp,
            viewportWidth = 50f,
            viewportHeight = 50f
        ).apply {
            path(
                fill = SolidColor(Color.Black),
                fillAlpha = 1.0f,
                stroke = null,
                strokeAlpha = 1.0f,
                strokeLineWidth = 1.0f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Miter,
                strokeLineMiter = 1.0f,
                pathFillType = PathFillType.NonZero
            ) {
                moveTo(6.9199219f, 6f)
                lineTo(21.136719f, 26.726562f)
                lineTo(6.2285156f, 44f)
                lineTo(9.40625f, 44f)
                lineTo(22.544922f, 28.777344f)
                lineTo(32.98633f, 44f)
                lineTo(43f, 44f)
                lineTo(28.123047f, 22.3125f)
                lineTo(42.203125f, 6f)
                lineTo(39.027344f, 6f)
                lineTo(26.716797f, 20.261719f)
                lineTo(16.933594f, 6f)
                lineTo(6.919922f, 6f)
                close()
            }
        }.build()
    }

    val SteamCommunityIcon: ImageVector by lazy {
        ImageVector.Builder(
            name = "SteamCommunityIcon",
            defaultWidth = 50.dp,
            defaultHeight = 50.dp,
            viewportWidth = 128f,
            viewportHeight = 128f
        ).apply {
            path(
                fill = SolidColor(Color.Black),
                fillAlpha = 1.0f,
                stroke = null,
                strokeAlpha = 1.0f,
                strokeLineWidth = 1.0f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Miter,
                strokeLineMiter = 1.0f,
                pathFillType = PathFillType.NonZero
            ) {
                moveTo(97f, 21f)
                curveTo(81.5886f, 21f, 69.0192f, 33.5578f, 69f, 48.9648f)
                lineTo(51.539062f, 71.25f)
                arcTo(
                    3.0003f, 3.0003f, 0f, isMoreThanHalf = false, isPositiveArc = false, 54.171875f,
                    76.08789f
                )
                lineTo(55.066406f, 76.00586f)
                curveTo(63.2989f, 76.0427f, 70f, 82.7591f, 70f, 91f)
                arcTo(
                    3.0003f, 3.0003f, 0f, isMoreThanHalf = false, isPositiveArc = false,
                    70.01953f, 91.365234f
                )
                arcTo(
                    3.0003f, 3.0003f, 0f, isMoreThanHalf = false, isPositiveArc = false,
                    69.9043f, 92.03906f
                )
                curveTo(69.4895f, 99.7552f, 63.0375f, 106f, 55f, 106f)
                curveTo(50.2929f, 106f, 46.1579f, 103.9177f, 43.4141f, 100.5918f)
                arcTo(
                    3.0003f,
                    3.0003f,
                    0f,
                    isMoreThanHalf = true,
                    isPositiveArc = false,
                    38.785156f,
                    104.4082f
                )
                curveTo(42.6413f, 109.0823f, 48.5071f, 112f, 55f, 112f)
                curveTo(65.6441f, 112f, 74.367f, 104.0121f, 75.6699f, 93.8359f)
                curveTo(82.2069f, 88.7159f, 90.2705f, 82.3576f, 97.0449f, 76.9961f)
                curveTo(112.4471f, 76.9711f, 125f, 64.4078f, 125f, 49f)
                curveTo(125f, 33.5768f, 112.4232f, 21f, 97f, 21f)
                close()
                moveTo(97f, 27f)
                curveTo(109.1768f, 27f, 119f, 36.8232f, 119f, 49f)
                curveTo(119f, 61.1768f, 109.1768f, 71f, 97f, 71f)
                lineTo(96f, 71f)
                arcTo(
                    3.0003f, 3.0003f, 0f, isMoreThanHalf = false, isPositiveArc = false,
                    94.13867f, 71.64844f
                )
                curveTo(88.3113f, 76.2618f, 81.5585f, 81.5752f, 75.4434f, 86.377f)
                curveTo(73.6595f, 78.5621f, 67.4931f, 72.3871f, 59.6875f, 70.5781f)
                lineTo(74.36133f, 51.84961f)
                arcTo(3.0003f, 3.0003f, 0f, isMoreThanHalf = false, isPositiveArc = false, 75f, 50f)
                lineTo(75f, 49f)
                curveTo(75f, 36.8232f, 84.8232f, 27f, 97f, 27f)
                close()
                moveTo(97f, 32f)
                curveTo(87.6467f, 32f, 80f, 39.6467f, 80f, 49f)
                curveTo(80f, 58.3533f, 87.6467f, 66f, 97f, 66f)
                curveTo(106.3533f, 66f, 114f, 58.3533f, 114f, 49f)
                curveTo(114f, 39.6467f, 106.3533f, 32f, 97f, 32f)
                close()
                moveTo(97f, 38f)
                curveTo(103.1107f, 38f, 108f, 42.8893f, 108f, 49f)
                curveTo(108f, 55.1107f, 103.1107f, 60f, 97f, 60f)
                curveTo(90.8893f, 60f, 86f, 55.1107f, 86f, 49f)
                curveTo(86f, 42.8893f, 90.8893f, 38f, 97f, 38f)
                close()
                moveTo(11.210938f, 62.708984f)
                curveTo(7.7044f, 62.6794f, 4.3237f, 64.7013f, 2.8477f, 68.1074f)
                curveTo(0.8871f, 72.6318f, 2.9663f, 77.9851f, 7.5078f, 79.9531f)
                lineTo(51.39258f, 99.24609f)
                arcTo(
                    3.0003f, 3.0003f, 0f, isMoreThanHalf = false, isPositiveArc = false, 51.546875f,
                    99.30859f
                )
                curveTo(52.5521f, 99.6856f, 53.7f, 100f, 55f, 100f)
                curveTo(58.4219f, 100f, 61.797f, 97.9478f, 63.2559f, 94.4766f)
                curveTo(65.2038f, 89.9585f, 63.1315f, 84.6214f, 58.6016f, 82.6523f)
                lineTo(14.701172f, 63.451172f)
                arcTo(
                    3.0003f,
                    3.0003f,
                    0f,
                    isMoreThanHalf = false,
                    isPositiveArc = false,
                    14.693359f,
                    63.447266f
                )
                curveTo(13.5622f, 62.9571f, 12.3798f, 62.7188f, 11.2109f, 62.709f)
                close()
                moveTo(11.15625f, 68.71875f)
                curveTo(11.5466f, 68.7185f, 11.9377f, 68.7933f, 12.3066f, 68.9531f)
                lineTo(56.197266f, 88.148438f)
                arcTo(
                    3.0003f, 3.0003f, 0f, isMoreThanHalf = false, isPositiveArc = false,
                    56.20703f, 88.15234f
                )
                curveTo(57.6655f, 88.7843f, 58.3875f, 90.6318f, 57.748f, 92.1074f)
                arcTo(
                    3.0003f, 3.0003f, 0f, isMoreThanHalf = false, isPositiveArc = false,
                    57.73047f, 92.146484f
                )
                curveTo(57.1833f, 93.4596f, 56.1696f, 94f, 55f, 94f)
                curveTo(54.7048f, 94f, 54.2564f, 93.9124f, 53.6758f, 93.6973f)
                lineTo(9.908203f, 74.453125f)
                arcTo(
                    3.0003f, 3.0003f, 0f, isMoreThanHalf = false, isPositiveArc = false,
                    9.892578f, 74.447266f
                )
                curveTo(8.4341f, 73.8153f, 7.7141f, 71.9678f, 8.3535f, 70.4922f)
                curveTo(8.6695f, 69.763f, 9.2885f, 69.2182f, 10.0137f, 68.9355f)
                curveTo(10.3763f, 68.7942f, 10.7659f, 68.719f, 11.1563f, 68.7188f)
                close()
            }
        }.build()
    }

    val DiscordAppIcon: ImageVector by lazy {
        ImageVector.Builder(
            name = "DiscordAppIcon",
            defaultWidth = 50.dp,
            defaultHeight = 50.dp,
            viewportWidth = 50f,
            viewportHeight = 50f
        ).apply {
            path(
                fill = SolidColor(Color.Black),
                fillAlpha = 1.0f,
                stroke = null,
                strokeAlpha = 1.0f,
                strokeLineWidth = 1.0f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Miter,
                strokeLineMiter = 1.0f,
                pathFillType = PathFillType.NonZero
            ) {
                moveTo(41.625f, 10.769531f)
                curveTo(37.6445f, 7.5664f, 31.3477f, 7.0234f, 31.0781f, 7.0039f)
                curveTo(30.6602f, 6.9688f, 30.2617f, 7.2031f, 30.0898f, 7.5898f)
                curveTo(30.0742f, 7.6133f, 29.9375f, 7.9297f, 29.7852f, 8.4219f)
                curveTo(32.418f, 8.8672f, 35.6523f, 9.7617f, 38.5781f, 11.5781f)
                curveTo(39.0469f, 11.8672f, 39.1914f, 12.4844f, 38.9023f, 12.9531f)
                curveTo(38.7109f, 13.2617f, 38.3867f, 13.4297f, 38.0508f, 13.4297f)
                curveTo(37.8711f, 13.4297f, 37.6875f, 13.3789f, 37.5234f, 13.2773f)
                curveTo(32.4922f, 10.1563f, 26.2109f, 10f, 25f, 10f)
                curveTo(23.7891f, 10f, 17.5039f, 10.1563f, 12.4766f, 13.2773f)
                curveTo(12.0078f, 13.5703f, 11.3906f, 13.4258f, 11.1016f, 12.957f)
                curveTo(10.8086f, 12.4844f, 10.9531f, 11.8711f, 11.4219f, 11.5781f)
                curveTo(14.3477f, 9.7656f, 17.582f, 8.8672f, 20.2148f, 8.4258f)
                curveTo(20.0625f, 7.9297f, 19.9258f, 7.6172f, 19.9141f, 7.5898f)
                curveTo(19.7383f, 7.2031f, 19.3438f, 6.9609f, 18.9219f, 7.0039f)
                curveTo(18.6523f, 7.0234f, 12.3555f, 7.5664f, 8.3203f, 10.8125f)
                curveTo(6.2148f, 12.7617f, 2f, 24.1523f, 2f, 34f)
                curveTo(2f, 34.1758f, 2.0469f, 34.3438f, 2.1328f, 34.4961f)
                curveTo(5.0391f, 39.6055f, 12.9727f, 40.9414f, 14.7813f, 41f)
                curveTo(14.7891f, 41f, 14.8008f, 41f, 14.8125f, 41f)
                curveTo(15.1328f, 41f, 15.4336f, 40.8477f, 15.6211f, 40.5898f)
                lineTo(17.449219f, 38.07422f)
                curveTo(12.5156f, 36.8008f, 9.9961f, 34.6367f, 9.8516f, 34.5078f)
                curveTo(9.4375f, 34.1445f, 9.3984f, 33.5117f, 9.7656f, 33.0977f)
                curveTo(10.1289f, 32.6836f, 10.7617f, 32.6445f, 11.1758f, 33.0078f)
                curveTo(11.2344f, 33.0625f, 15.875f, 37f, 25f, 37f)
                curveTo(34.1406f, 37f, 38.7813f, 33.0469f, 38.8281f, 33.0078f)
                curveTo(39.2422f, 32.6484f, 39.8711f, 32.6836f, 40.2383f, 33.1016f)
                curveTo(40.6016f, 33.5156f, 40.5625f, 34.1445f, 40.1484f, 34.5078f)
                curveTo(40.0039f, 34.6367f, 37.4844f, 36.8008f, 32.5508f, 38.0742f)
                lineTo(34.378906f, 40.589844f)
                curveTo(34.5664f, 40.8477f, 34.8672f, 41f, 35.1875f, 41f)
                curveTo(35.1992f, 41f, 35.2109f, 41f, 35.2188f, 41f)
                curveTo(37.0273f, 40.9414f, 44.9609f, 39.6055f, 47.8672f, 34.4961f)
                curveTo(47.9531f, 34.3438f, 48f, 34.1758f, 48f, 34f)
                curveTo(48f, 24.1523f, 43.7852f, 12.7617f, 41.625f, 10.7695f)
                close()
                moveTo(18.5f, 30f)
                curveTo(16.5664f, 30f, 15f, 28.2109f, 15f, 26f)
                curveTo(15f, 23.7891f, 16.5664f, 22f, 18.5f, 22f)
                curveTo(20.4336f, 22f, 22f, 23.7891f, 22f, 26f)
                curveTo(22f, 28.2109f, 20.4336f, 30f, 18.5f, 30f)
                close()
                moveTo(31.5f, 30f)
                curveTo(29.5664f, 30f, 28f, 28.2109f, 28f, 26f)
                curveTo(28f, 23.7891f, 29.5664f, 22f, 31.5f, 22f)
                curveTo(33.4336f, 22f, 35f, 23.7891f, 35f, 26f)
                curveTo(35f, 28.2109f, 33.4336f, 30f, 31.5f, 30f)
                close()
            }
        }.build()
    }

    val BilibiliIcon: ImageVector by lazy {
        ImageVector.Builder(
            name = "BilibiliIcon",
            defaultWidth = 50.dp,
            defaultHeight = 50.dp,
            viewportWidth = 48f,
            viewportHeight = 48f
        ).apply {
            path(
                fill = SolidColor(Color(0xFF1E88E5)),
                fillAlpha = 1.0f,
                stroke = null,
                strokeAlpha = 1.0f,
                strokeLineWidth = 1.0f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Miter,
                strokeLineMiter = 1.0f,
                pathFillType = PathFillType.NonZero
            ) {
                moveTo(36.5f, 12f)
                horizontalLineToRelative(-7.086f)
                lineToRelative(3.793f, -3.793f)
                curveToRelative(0.391f, -0.391f, 0.391f, -1.023f, 0f, -1.414f)
                reflectiveCurveToRelative(-1.023f, -0.391f, -1.414f, 0f)
                lineTo(26.586f, 12f)
                horizontalLineToRelative(-5.172f)
                lineToRelative(-5.207f, -5.207f)
                curveToRelative(-0.391f, -0.391f, -1.023f, -0.391f, -1.414f, 0f)
                reflectiveCurveToRelative(-0.391f, 1.023f, 0f, 1.414f)
                lineTo(18.586f, 12f)
                horizontalLineTo(12.5f)
                curveTo(9.467f, 12f, 7f, 14.467f, 7f, 17.5f)
                verticalLineToRelative(15f)
                curveToRelative(0f, 3.033f, 2.467f, 5.5f, 5.5f, 5.5f)
                horizontalLineToRelative(2f)
                curveToRelative(0f, 0.829f, 0.671f, 1.5f, 1.5f, 1.5f)
                reflectiveCurveToRelative(1.5f, -0.671f, 1.5f, -1.5f)
                horizontalLineToRelative(14f)
                curveToRelative(0f, 0.829f, 0.671f, 1.5f, 1.5f, 1.5f)
                reflectiveCurveToRelative(1.5f, -0.671f, 1.5f, -1.5f)
                horizontalLineToRelative(2f)
                curveToRelative(3.033f, 0f, 5.5f, -2.467f, 5.5f, -5.5f)
                verticalLineToRelative(-15f)
                curveTo(42f, 14.467f, 39.533f, 12f, 36.5f, 12f)
                close()
                moveTo(39f, 32.5f)
                curveToRelative(0f, 1.378f, -1.122f, 2.5f, -2.5f, 2.5f)
                horizontalLineToRelative(-24f)
                curveToRelative(-1.378f, 0f, -2.5f, -1.122f, -2.5f, -2.5f)
                verticalLineToRelative(-15f)
                curveToRelative(0f, -1.378f, 1.122f, -2.5f, 2.5f, -2.5f)
                horizontalLineToRelative(24f)
                curveToRelative(1.378f, 0f, 2.5f, 1.122f, 2.5f, 2.5f)
                verticalLineTo(32.5f)
                close()
            }
            path(
                fill = SolidColor(Color(0xFF1E88E5)),
                fillAlpha = 1.0f,
                stroke = null,
                strokeAlpha = 1.0f,
                strokeLineWidth = 1.0f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Miter,
                strokeLineMiter = 1.0f,
                pathFillType = PathFillType.NonZero
            ) {
                moveTo(30.625f, 18.463f)
                horizontalLineTo(33.375f)
                verticalLineTo(25.538f)
                horizontalLineTo(30.625f)
                verticalLineTo(18.463f)
                close()
            }
            path(
                fill = SolidColor(Color(0xFF1E88E5)),
                fillAlpha = 1.0f,
                stroke = null,
                strokeAlpha = 1.0f,
                strokeLineWidth = 1.0f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Miter,
                strokeLineMiter = 1.0f,
                pathFillType = PathFillType.NonZero
            ) {
                moveTo(14.463f, 20.625f)
                horizontalLineTo(21.538f)
                verticalLineTo(23.375f)
                horizontalLineTo(14.463f)
                verticalLineTo(20.625f)
                close()
            }
            path(
                fill = SolidColor(Color(0xFF1E88E5)),
                fillAlpha = 1.0f,
                stroke = null,
                strokeAlpha = 1.0f,
                strokeLineWidth = 1.0f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Miter,
                strokeLineMiter = 1.0f,
                pathFillType = PathFillType.NonZero
            ) {
                moveTo(28.033f, 27.526f)
                curveToRelative(-0.189f, 0.593f, -0.644f, 0.896f, -1.326f, 0.896f)
                curveToRelative(-0.076f, -0.013f, -0.139f, -0.013f, -0.24f, -0.025f)
                curveToRelative(-0.013f, 0f, -0.05f, -0.013f, -0.063f, 0f)
                curveToRelative(-0.341f, -0.05f, -0.745f, -0.177f, -1.061f, -0.467f)
                curveToRelative(-0.366f, -0.265f, -0.808f, -0.745f, -0.947f, -1.477f)
                curveToRelative(0f, 0f, -0.29f, 1.174f, -0.896f, 1.49f)
                curveToRelative(-0.076f, 0.05f, -0.164f, 0.114f, -0.253f, 0.164f)
                lineToRelative(-0.038f, 0.025f)
                curveToRelative(-0.303f, 0.164f, -0.682f, 0.265f, -1.086f, 0.278f)
                curveToRelative(-0.568f, -0.051f, -0.947f, -0.328f, -1.136f, -0.821f)
                lineToRelative(-0.063f, -0.164f)
                lineToRelative(-1.427f, 0.656f)
                lineToRelative(0.05f, 0.139f)
                curveToRelative(0.467f, 1.124f, 1.465f, 1.768f, 2.74f, 1.768f)
                curveToRelative(0.922f, 0f, 1.667f, -0.303f, 2.209f, -0.909f)
                curveToRelative(0.556f, 0.606f, 1.288f, 0.909f, 2.209f, 0.909f)
                curveToRelative(1.856f, 0f, 2.55f, -1.288f, 2.765f, -1.843f)
                lineToRelative(0.051f, -0.126f)
                lineToRelative(-1.427f, -0.657f)
                lineTo(28.033f, 27.526f)
                close()
            }
        }.build()
    }

    val YoutubeIcon: ImageVector by lazy {
        ImageVector.Builder(
            name = "YoutubeIcon",
            defaultWidth = 50.dp,
            defaultHeight = 50.dp,
            viewportWidth = 50f,
            viewportHeight = 50f
        ).apply {
            path(
                fill = SolidColor(Color.Black),
                fillAlpha = 1.0f,
                stroke = null,
                strokeAlpha = 1.0f,
                strokeLineWidth = 1.0f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Miter,
                strokeLineMiter = 1.0f,
                pathFillType = PathFillType.EvenOdd
            ) {
                moveTo(13f, 5f)
                lineTo(16f, 14f)
                lineTo(16f, 20f)
                lineTo(18f, 20f)
                lineTo(18f, 14f)
                lineTo(21f, 5f)
                lineTo(19f, 5f)
                lineTo(17f, 11f)
                lineTo(15f, 5f)
                close()
                moveTo(24f, 9f)
                curveTo(22.9336f, 9f, 22.4102f, 9.168f, 21.7578f, 9.7031f)
                curveTo(21.1328f, 10.2305f, 20.9609f, 10.6367f, 21f, 12f)
                lineTo(21f, 17f)
                curveTo(21f, 17.9961f, 21.1641f, 18.6523f, 21.7656f, 19.2344f)
                curveTo(22.3906f, 19.8164f, 22.9805f, 20f, 24f, 20f)
                curveTo(25.0664f, 20f, 25.6484f, 19.8164f, 26.25f, 19.2344f)
                curveTo(26.875f, 18.6758f, 27f, 17.9961f, 27f, 17f)
                lineTo(27f, 12f)
                curveTo(27f, 11.1172f, 26.8438f, 10.2813f, 26.2383f, 9.7227f)
                curveTo(25.6133f, 9.1484f, 24.9688f, 9f, 24f, 9f)
                close()
                moveTo(29f, 9f)
                lineTo(29f, 18f)
                curveTo(29f, 18.9727f, 29.9805f, 20f, 31f, 20f)
                curveTo(32.0195f, 20f, 32.5586f, 19.4883f, 33f, 19f)
                lineTo(33f, 20f)
                lineTo(35f, 20f)
                lineTo(35f, 9f)
                lineTo(33f, 9f)
                lineTo(33f, 17f)
                curveTo(32.9883f, 17.6836f, 32.1836f, 18f, 32f, 18f)
                curveTo(31.793f, 18f, 31f, 17.957f, 31f, 17f)
                lineTo(31f, 9f)
                close()
                moveTo(24f, 11f)
                curveTo(24.3008f, 11f, 25f, 10.9961f, 25f, 12f)
                lineTo(25f, 17f)
                curveTo(25f, 17.9688f, 24.3242f, 18f, 24f, 18f)
                curveTo(23.6992f, 18f, 23f, 17.9883f, 23f, 17f)
                lineTo(23f, 12f)
                curveTo(23f, 11.1836f, 23.4336f, 11f, 24f, 11f)
                close()
                moveTo(10f, 22f)
                curveTo(6.4063f, 22f, 4f, 24.3828f, 4f, 28f)
                lineTo(4f, 37.5f)
                curveTo(4f, 41.1172f, 6.4063f, 44f, 10f, 44f)
                lineTo(40f, 44f)
                curveTo(43.5938f, 44f, 46f, 41.6172f, 46f, 38f)
                lineTo(46f, 28f)
                curveTo(46f, 24.3828f, 43.5938f, 22f, 40f, 22f)
                close()
                moveTo(12f, 26f)
                lineTo(18f, 26f)
                lineTo(18f, 28f)
                lineTo(16f, 28f)
                lineTo(16f, 40f)
                lineTo(14f, 40f)
                lineTo(14f, 28f)
                lineTo(12f, 28f)
                close()
                moveTo(26f, 26f)
                lineTo(28f, 26f)
                lineTo(28f, 30f)
                curveTo(28.2305f, 29.6406f, 28.5742f, 29.3555f, 28.9023f, 29.1953f)
                curveTo(29.2227f, 29.0313f, 29.5469f, 28.9375f, 29.875f, 28.9375f)
                curveTo(30.5234f, 28.9375f, 31.0313f, 29.1719f, 31.3789f, 29.6094f)
                curveTo(31.7266f, 30.0508f, 32f, 30.6367f, 32f, 31.5f)
                lineTo(32f, 37.5f)
                curveTo(32f, 38.2422f, 31.75f, 38.7031f, 31.4219f, 39.0977f)
                curveTo(31.1016f, 39.4922f, 30.6211f, 39.9922f, 30f, 40f)
                curveTo(28.9492f, 40.0117f, 28.3867f, 39.4492f, 28f, 39f)
                lineTo(28f, 40f)
                lineTo(26f, 40f)
                close()
                moveTo(18f, 29f)
                lineTo(20f, 29f)
                lineTo(20f, 37f)
                curveTo(20f, 37.2305f, 20.2695f, 38.0078f, 21f, 38f)
                curveTo(21.8125f, 37.9922f, 21.8203f, 37.2344f, 22f, 37f)
                lineTo(22f, 29f)
                lineTo(24f, 29f)
                lineTo(24f, 40f)
                lineTo(22f, 40f)
                lineTo(22f, 39f)
                curveTo(21.6289f, 39.4375f, 21.4375f, 39.5742f, 21.0195f, 39.7813f)
                curveTo(20.6055f, 40.0156f, 20.1836f, 40f, 19.793f, 40f)
                curveTo(19.3086f, 40f, 18.7578f, 39.5625f, 18.5f, 39.2344f)
                curveTo(18.2695f, 38.9336f, 18f, 38.625f, 18f, 38f)
                close()
                moveTo(36.199219f, 29f)
                curveTo(37.1484f, 29f, 37.8164f, 29.2031f, 38.3203f, 29.7344f)
                curveTo(38.8359f, 30.2656f, 39f, 30.8867f, 39f, 31.8867f)
                lineTo(39f, 35f)
                lineTo(35f, 35f)
                lineTo(35f, 36.546875f)
                curveTo(35f, 37.1055f, 35.0742f, 37.4609f, 35.2188f, 37.6719f)
                curveTo(35.3555f, 37.9023f, 35.6328f, 38.0039f, 36f, 38f)
                curveTo(36.4063f, 37.9961f, 36.6641f, 37.9141f, 36.8008f, 37.7305f)
                curveTo(36.9414f, 37.5664f, 37f, 37.1016f, 37f, 36.5f)
                lineTo(37f, 36f)
                lineTo(39f, 36f)
                lineTo(39f, 36.59375f)
                curveTo(39f, 37.6836f, 38.9141f, 38.4961f, 38.375f, 39.0273f)
                curveTo(37.8672f, 39.5859f, 37.0742f, 39.8438f, 36.0352f, 39.8438f)
                curveTo(35.0859f, 39.8438f, 34.3438f, 39.5625f, 33.8125f, 38.9844f)
                curveTo(33.2813f, 38.4063f, 33.0039f, 37.6133f, 33.0039f, 36.5938f)
                lineTo(33.003906f, 31.886719f)
                curveTo(33.0039f, 30.9805f, 33.3203f, 30.3086f, 33.9023f, 29.7109f)
                curveTo(34.3711f, 29.2305f, 35.25f, 29f, 36.1992f, 29f)
                close()
                moveTo(29f, 30.5f)
                curveTo(28.4492f, 30.5f, 28.0078f, 30.9961f, 28f, 31.5f)
                lineTo(28f, 37.5f)
                curveTo(28.0078f, 37.7891f, 28.4492f, 38f, 29f, 38f)
                curveTo(29.5508f, 38f, 30f, 37.5742f, 30f, 37.0234f)
                lineTo(30f, 32f)
                curveTo(30f, 31f, 29.5508f, 30.5f, 29f, 30.5f)
                close()
                moveTo(36f, 31f)
                curveTo(35.4492f, 31f, 35.0078f, 31.4648f, 35f, 32f)
                lineTo(35f, 33f)
                lineTo(37f, 33f)
                lineTo(37f, 32f)
                curveTo(37f, 31.3867f, 36.5508f, 31f, 36f, 31f)
                close()
            }
        }.build()
    }
    val QQIcon: ImageVector by lazy {
        ImageVector.Builder(
            name = "QQIcon",
            defaultWidth = 50.dp,
            defaultHeight = 50.dp,
            viewportWidth = 30f,
            viewportHeight = 30f
        ).apply {
            path(
                fill = SolidColor(Color.Black),
                fillAlpha = 1.0f,
                stroke = null,
                strokeAlpha = 1.0f,
                strokeLineWidth = 1.0f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Miter,
                strokeLineMiter = 1.0f,
                pathFillType = PathFillType.NonZero
            ) {
                moveTo(15f, 3f)
                curveTo(10.74f, 3f, 7.1992f, 6.2398f, 7.1992f, 10.2598f)
                lineTo(7.1992188f, 12.359375f)
                curveTo(7.3092f, 12.3904f, 7.4275f, 12.4431f, 7.5605f, 12.5391f)
                curveTo(7.5605f, 12.5391f, 11.1005f, 14.4004f, 15.0605f, 14.4004f)
                curveTo(19.0205f, 14.4004f, 22.5605f, 12.5391f, 22.5605f, 12.5391f)
                curveTo(22.6455f, 12.4701f, 22.7238f, 12.4189f, 22.7988f, 12.3809f)
                curveTo(22.7968f, 11.5589f, 22.7402f, 10.8498f, 22.7402f, 10.2598f)
                curveTo(22.7402f, 6.2398f, 19.26f, 3f, 15f, 3f)
                close()
                moveTo(12.900391f, 6f)
                curveTo(13.7404f, 6f, 14.4004f, 6.9596f, 14.4004f, 8.0996f)
                curveTo(14.4004f, 9.2396f, 13.7404f, 10.1992f, 12.9004f, 10.1992f)
                curveTo(12.0604f, 10.1992f, 11.4004f, 9.2396f, 11.4004f, 8.0996f)
                curveTo(11.4004f, 6.9596f, 12.0604f, 6f, 12.9004f, 6f)
                close()
                moveTo(17.09961f, 6f)
                curveTo(17.9396f, 6f, 18.5996f, 6.9596f, 18.5996f, 8.0996f)
                curveTo(18.5996f, 9.2396f, 17.9396f, 10.1992f, 17.0996f, 10.1992f)
                curveTo(16.2596f, 10.1992f, 15.5996f, 9.2396f, 15.5996f, 8.0996f)
                curveTo(15.5996f, 6.9596f, 16.2596f, 6f, 17.0996f, 6f)
                close()
                moveTo(13.199219f, 7.1992188f)
                curveTo(12.8392f, 7.1992f, 12.5996f, 7.6196f, 12.5996f, 8.0996f)
                curveTo(12.5996f, 8.5796f, 12.8392f, 9f, 13.1992f, 9f)
                curveTo(13.5592f, 9f, 13.8008f, 8.5796f, 13.8008f, 8.0996f)
                curveTo(13.8008f, 7.6196f, 13.5592f, 7.1992f, 13.1992f, 7.1992f)
                close()
                moveTo(16.800781f, 7.1992188f)
                curveTo(16.4408f, 7.1992f, 16.1992f, 7.6196f, 16.1992f, 8.0996f)
                curveTo(16.1992f, 8.5796f, 16.4408f, 7.8008f, 16.8008f, 7.8008f)
                curveTo(17.1608f, 7.8008f, 17.4004f, 8.5796f, 17.4004f, 8.0996f)
                curveTo(17.4004f, 7.6196f, 17.1608f, 7.1992f, 16.8008f, 7.1992f)
                close()
                moveTo(15f, 11f)
                curveTo(17.2f, 11f, 19f, 11.35f, 19f, 11.75f)
                curveTo(19f, 12.15f, 17.2f, 13f, 15f, 13f)
                curveTo(12.8f, 13f, 11f, 12.15f, 11f, 11.75f)
                curveTo(11f, 11.35f, 12.8f, 11f, 15f, 11f)
                close()
                moveTo(6.466797f, 14.837891f)
                curveTo(5.5648f, 16.2479f, 4.8008f, 17.7925f, 4.8008f, 19.0195f)
                curveTo(4.8008f, 21.8995f, 5.6406f, 21.5996f, 5.6406f, 21.5996f)
                curveTo(6.0006f, 21.5996f, 6.6006f, 20.9998f, 7.1406f, 20.3398f)
                curveTo(7.5248f, 21.4191f, 8.0845f, 22.3873f, 8.7598f, 23.2344f)
                curveTo(7.8166f, 23.6214f, 7.1992f, 24.2266f, 7.1992f, 24.9004f)
                curveTo(7.1992f, 26.0404f, 8.9396f, 27f, 11.0996f, 27f)
                curveTo(12.2292f, 27f, 13.2362f, 26.7339f, 13.9473f, 26.3203f)
                curveTo(14.292f, 26.3686f, 14.6423f, 26.4004f, 15f, 26.4004f)
                curveTo(15.3577f, 26.4004f, 15.708f, 26.3686f, 16.0527f, 26.3203f)
                curveTo(16.7638f, 26.7339f, 17.7708f, 27f, 18.9004f, 27f)
                curveTo(21.0604f, 27f, 22.8008f, 26.0404f, 22.8008f, 24.9004f)
                curveTo(22.8008f, 24.2266f, 22.1834f, 23.6214f, 21.2402f, 23.2344f)
                curveTo(21.9155f, 22.3873f, 22.4752f, 21.4191f, 22.8594f, 20.3398f)
                curveTo(23.3994f, 20.9998f, 23.9994f, 21.5996f, 24.3594f, 21.5996f)
                curveTo(24.3594f, 21.5996f, 25.1992f, 21.8995f, 25.1992f, 19.0195f)
                curveTo(25.1992f, 17.8205f, 24.4688f, 16.2782f, 23.5938f, 14.9102f)
                curveTo(23.5497f, 14.9392f, 23.5129f, 14.969f, 23.4609f, 15f)
                curveTo(23.3409f, 15.06f, 19.4991f, 17.4004f, 15.1191f, 17.4004f)
                curveTo(14.0391f, 17.4004f, 13.0791f, 17.2191f, 12.1191f, 17.0391f)
                curveTo(11.9391f, 18.2991f, 11.9395f, 19.4991f, 11.9395f, 20.0391f)
                curveTo(11.9395f, 20.9991f, 11.1592f, 20.94f, 10.1992f, 21f)
                curveTo(9.2392f, 21f, 8.5209f, 21.1207f, 8.4609f, 20.2207f)
                curveTo(8.4609f, 20.1007f, 8.3998f, 18.1195f, 8.7598f, 16.0195f)
                curveTo(7.5598f, 15.4795f, 6.7807f, 15.06f, 6.7207f, 15f)
                curveTo(6.6217f, 14.944f, 6.5388f, 14.8909f, 6.4668f, 14.8379f)
                close()
            }
        }.build()
    }

    val GithubIcon : ImageVector by lazy {
        ImageVector.Builder(
            name = "GithubIcon",
            defaultWidth = 50.dp,
            defaultHeight = 50.dp,
            viewportWidth = 30f,
            viewportHeight = 30f
        ).apply {
            path(
                fill = SolidColor(Color.Black),
                fillAlpha = 1.0f,
                stroke = null,
                strokeAlpha = 1.0f,
                strokeLineWidth = 1.0f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Miter,
                strokeLineMiter = 1.0f,
                pathFillType = PathFillType.NonZero
            ) {
                moveTo(15f, 3f)
                curveTo(8.373f, 3f, 3f, 8.373f, 3f, 15f)
                curveToRelative(0f, 5.623f, 3.872f, 10.328f, 9.092f, 11.63f)
                curveTo(12.036f, 26.468f, 12f, 26.28f, 12f, 26.047f)
                verticalLineToRelative(-2.051f)
                curveToRelative(-0.487f, 0f, -1.303f, 0f, -1.508f, 0f)
                curveToRelative(-0.821f, 0f, -1.551f, -0.353f, -1.905f, -1.009f)
                curveToRelative(-0.393f, -0.729f, -0.461f, -1.844f, -1.435f, -2.526f)
                curveToRelative(-0.289f, -0.227f, -0.069f, -0.486f, 0.264f, -0.451f)
                curveToRelative(0.615f, 0.174f, 1.125f, 0.596f, 1.605f, 1.222f)
                curveToRelative(0.478f, 0.627f, 0.703f, 0.769f, 1.596f, 0.769f)
                curveToRelative(0.433f, 0f, 1.081f, -0.025f, 1.691f, -0.121f)
                curveToRelative(0.328f, -0.833f, 0.895f, -1.6f, 1.588f, -1.962f)
                curveToRelative(-3.996f, -0.411f, -5.903f, -2.399f, -5.903f, -5.098f)
                curveToRelative(0f, -1.162f, 0.495f, -2.286f, 1.336f, -3.233f)
                curveTo(9.053f, 10.647f, 8.706f, 8.73f, 9.435f, 8f)
                curveToRelative(1.798f, 0f, 2.885f, 1.166f, 3.146f, 1.481f)
                curveTo(13.477f, 9.174f, 14.461f, 9f, 15.495f, 9f)
                curveToRelative(1.036f, 0f, 2.024f, 0.174f, 2.922f, 0.483f)
                curveTo(18.675f, 9.17f, 19.763f, 8f, 21.565f, 8f)
                curveToRelative(0.732f, 0.731f, 0.381f, 2.656f, 0.102f, 3.594f)
                curveToRelative(0.836f, 0.945f, 1.328f, 2.066f, 1.328f, 3.226f)
                curveToRelative(0f, 2.697f, -1.904f, 4.684f, -5.894f, 5.097f)
                curveTo(18.199f, 20.49f, 19f, 22.1f, 19f, 23.313f)
                verticalLineToRelative(2.734f)
                curveToRelative(0f, 0.104f, -0.023f, 0.179f, -0.035f, 0.268f)
                curveTo(23.641f, 24.676f, 27f, 20.236f, 27f, 15f)
                curveTo(27f, 8.373f, 21.627f, 3f, 15f, 3f)
                close()
            }
        }.build()
    }
}






