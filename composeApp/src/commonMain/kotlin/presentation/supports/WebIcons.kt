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
        when (URLBuilder(link).host.removePrefix("www.")) {
            //Video&Streaming
            "bilibili.com", "space.bilibili.com", "b23.tv" -> BilibiliIcon
            "youtube.com", "youtu.be" -> YoutubeIcon
            "twitch.tv", "clips.twitch.tv" -> TwitchIcon
            "tiktok.com", "v.douyin.com" -> TiktokIcon

            //Gaming
            "steamcommunity.com" -> SteamCommunityIcon

            //Social Media
            "facebook.com" -> FacebookIcon
            "instagram.com" -> InstagramIcon
            "weibo.com" -> SinaweiboIcon
            "twitter.com", "x.com" -> TwitterIcon

            //Messaging
            "discordapp.com" -> DiscordAppIcon
            "jq.qq.com", "qm.qq.com" -> QQIcon
            "user.qzone.qq.com" -> QzoneIcon
            "t.me", "telegram.me" -> TelegramIcon
            "line.me" -> LineIcon
            "snapchat.com" -> SnapchatIcon

            //Music
            "open.spotify.com" -> SpotifyIcon
            "soundcloud.com" -> SoundcloudIcon
            "music.youtube.com" -> YoutubemusicIcon
            "music.163.com", "y.music.163.com" -> NeteasecloudmusicIcon

            //Other
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
            name = "Youtube",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
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
                moveTo(23.498f, 6.186f)
                arcToRelative(3.016f, 3.016f, 0f, isMoreThanHalf = false, isPositiveArc = false, -2.122f, -2.136f)
                curveTo(19.505f, 3.545f, 12f, 3.545f, 12f, 3.545f)
                reflectiveCurveToRelative(-7.505f, 0f, -9.377f, 0.505f)
                arcTo(3.017f, 3.017f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0.502f, 6.186f)
                curveTo(0f, 8.07f, 0f, 12f, 0f, 12f)
                reflectiveCurveToRelative(0f, 3.93f, 0.502f, 5.814f)
                arcToRelative(3.016f, 3.016f, 0f, isMoreThanHalf = false, isPositiveArc = false, 2.122f, 2.136f)
                curveToRelative(1.871f, 0.505f, 9.376f, 0.505f, 9.376f, 0.505f)
                reflectiveCurveToRelative(7.505f, 0f, 9.377f, -0.505f)
                arcToRelative(3.015f, 3.015f, 0f, isMoreThanHalf = false, isPositiveArc = false, 2.122f, -2.136f)
                curveTo(24f, 15.93f, 24f, 12f, 24f, 12f)
                reflectiveCurveToRelative(0f, -3.93f, -0.502f, -5.814f)
                close()
                moveTo(9.545f, 15.568f)
                verticalLineTo(8.432f)
                lineTo(15.818f, 12f)
                lineToRelative(-6.273f, 3.568f)
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

    val GithubIcon: ImageVector by lazy {
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

    val SpotifyIcon: ImageVector by lazy {
        ImageVector.Builder(
            name = "SpotifyIcon",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
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
                moveTo(12f, 0f)
                curveTo(5.4f, 0f, 0f, 5.4f, 0f, 12f)
                reflectiveCurveToRelative(5.4f, 12f, 12f, 12f)
                reflectiveCurveToRelative(12f, -5.4f, 12f, -12f)
                reflectiveCurveTo(18.66f, 0f, 12f, 0f)
                close()
                moveToRelative(5.521f, 17.34f)
                curveToRelative(-0.24f, 0.359f, -0.66f, 0.48f, -1.021f, 0.24f)
                curveToRelative(-2.82f, -1.74f, -6.36f, -2.101f, -10.561f, -1.141f)
                curveToRelative(-0.418f, 0.122f, -0.779f, -0.179f, -0.899f, -0.539f)
                curveToRelative(-0.12f, -0.421f, 0.18f, -0.78f, 0.54f, -0.9f)
                curveToRelative(4.56f, -1.021f, 8.52f, -0.6f, 11.64f, 1.32f)
                curveToRelative(0.42f, 0.18f, 0.479f, 0.659f, 0.301f, 1.02f)
                close()
                moveToRelative(1.44f, -3.3f)
                curveToRelative(-0.301f, 0.42f, -0.841f, 0.6f, -1.262f, 0.3f)
                curveToRelative(-3.239f, -1.98f, -8.159f, -2.58f, -11.939f, -1.38f)
                curveToRelative(-0.479f, 0.12f, -1.02f, -0.12f, -1.14f, -0.6f)
                curveToRelative(-0.12f, -0.48f, 0.12f, -1.021f, 0.6f, -1.141f)
                curveTo(9.6f, 9.9f, 15f, 10.561f, 18.72f, 12.84f)
                curveToRelative(0.361f, 0.181f, 0.54f, 0.78f, 0.241f, 1.2f)
                close()
                moveToRelative(0.12f, -3.36f)
                curveTo(15.24f, 8.4f, 8.82f, 8.16f, 5.16f, 9.301f)
                curveToRelative(-0.6f, 0.179f, -1.2f, -0.181f, -1.38f, -0.721f)
                curveToRelative(-0.18f, -0.601f, 0.18f, -1.2f, 0.72f, -1.381f)
                curveToRelative(4.26f, -1.26f, 11.28f, -1.02f, 15.721f, 1.621f)
                curveToRelative(0.539f, 0.3f, 0.719f, 1.02f, 0.419f, 1.56f)
                curveToRelative(-0.299f, 0.421f, -1.02f, 0.599f, -1.559f, 0.3f)
                close()
            }
        }.build()
    }

    val QzoneIcon: ImageVector by lazy {

        ImageVector.Builder(
            name = "QzoneIcon",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
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
                moveTo(23.9868f, 9.2012f)
                curveToRelative(-0.032f, -0.099f, -0.127f, -0.223f, -0.334f, -0.258f)
                curveToRelative(-0.207f, -0.036f, -7.352f, -1.4063f, -7.352f, -1.4063f)
                reflectiveCurveToRelative(-0.105f, -0.022f, -0.198f, -0.07f)
                curveToRelative(-0.092f, -0.047f, -0.127f, -0.167f, -0.127f, -0.167f)
                reflectiveCurveTo(12.4472f, 0.954f, 12.3491f, 0.7679f)
                curveToRelative(-0.099f, -0.187f, -0.245f, -0.238f, -0.349f, -0.238f)
                curveToRelative(-0.104f, 0f, -0.251f, 0.051f, -0.349f, 0.238f)
                curveTo(11.5531f, 0.954f, 8.0245f, 7.3f, 8.0245f, 7.3f)
                reflectiveCurveToRelative(-0.035f, 0.12f, -0.128f, 0.167f)
                curveToRelative(-0.092f, 0.047f, -0.197f, 0.07f, -0.197f, 0.07f)
                reflectiveCurveTo(0.5546f, 8.9071f, 0.3466f, 8.9421f)
                curveToRelative(-0.208f, 0.036f, -0.302f, 0.16f, -0.333f, 0.258f)
                arcToRelative(
                    0.477f,
                    0.477f,
                    0f,
                    isMoreThanHalf = false,
                    isPositiveArc = false,
                    0.125f,
                    0.4491f
                )
                lineTo(5.5013f, 15.14f)
                reflectiveCurveToRelative(0.072f, 0.08f, 0.119f, 0.172f)
                curveToRelative(0.016f, 0.104f, 0.005f, 0.21f, 0.005f, 0.21f)
                reflectiveCurveToRelative(-1.1891f, 7.243f, -1.2201f, 7.451f)
                curveToRelative(-0.031f, 0.208f, 0.075f, 0.369f, 0.159f, 0.4301f)
                curveToRelative(0.083f, 0.062f, 0.233f, 0.106f, 0.421f, 0.013f)
                curveToRelative(0.189f, -0.093f, 6.813f, -3.2614f, 6.813f, -3.2614f)
                reflectiveCurveToRelative(0.098f, -0.044f, 0.201f, -0.061f)
                curveToRelative(0.103f, -0.017f, 0.201f, 0.061f, 0.201f, 0.061f)
                reflectiveCurveToRelative(6.624f, 3.1684f, 6.813f, 3.2614f)
                curveToRelative(0.188f, 0.094f, 0.338f, 0.049f, 0.421f, -0.013f)
                arcToRelative(
                    0.463f,
                    0.463f,
                    0f,
                    isMoreThanHalf = false,
                    isPositiveArc = false,
                    0.159f,
                    -0.43f
                )
                curveToRelative(-0.021f, -0.14f, -0.93f, -5.6778f, -0.93f, -5.6778f)
                curveToRelative(0.876f, -0.5401f, 1.4251f, -1.0392f, 1.8492f, -1.7473f)
                curveToRelative(-2.5944f, 0.9692f, -6.0069f, 1.7173f, -9.4163f, 1.8663f)
                curveToRelative(-0.9152f, 0.041f, -2.4104f, 0.097f, -3.4735f, -0.015f)
                curveToRelative(-0.6781f, -0.071f, -1.1702f, -0.144f, -1.2432f, -0.438f)
                curveToRelative(-0.053f, -0.2151f, 0.054f, -0.4601f, 0.5451f, -0.8312f)
                arcToRelative(
                    2640.8625f,
                    2640.8625f,
                    0f,
                    isMoreThanHalf = false,
                    isPositiveArc = true,
                    2.8614f,
                    -2.1553f
                )
                curveToRelative(1.2852f, -0.9681f, 3.5595f, -2.4703f, 3.5595f, -2.7314f)
                curveToRelative(0f, -0.285f, -2.1443f, -0.781f, -4.0376f, -0.781f)
                curveToRelative(-1.9452f, 0f, -2.2753f, 0.132f, -2.8114f, 0.168f)
                curveToRelative(-0.488f, 0.034f, -0.769f, 0.005f, -0.804f, -0.138f)
                curveToRelative(-0.06f, -0.2481f, 0.183f, -0.3891f, 0.588f, -0.5682f)
                curveToRelative(0.7091f, -0.314f, 1.8603f, -0.594f, 1.9843f, -0.626f)
                curveToRelative(0.194f, -0.052f, 3.0824f, -0.8051f, 5.6188f, -0.5351f)
                curveToRelative(1.3181f, 0.14f, 3.2444f, 0.668f, 3.2444f, 1.2762f)
                curveToRelative(0f, 0.342f, -1.7212f, 1.4942f, -3.2254f, 2.5973f)
                curveToRelative(-1.1492f, 0.8431f, -2.2173f, 1.5612f, -2.2173f, 1.6883f)
                curveToRelative(0f, 0.342f, 3.5334f, 1.2411f, 6.6899f, 1.01f)
                lineToRelative(0.003f, -0.022f)
                curveToRelative(0.048f, -0.092f, 0.119f, -0.172f, 0.119f, -0.172f)
                lineToRelative(5.3627f, -5.4907f)
                arcToRelative(
                    0.477f,
                    0.477f,
                    0f,
                    isMoreThanHalf = false,
                    isPositiveArc = false,
                    0.127f,
                    -0.449f
                )
                close()
            }
        }.build()
    }

    val TwitchIcon: ImageVector by lazy {
        ImageVector.Builder(
            name = "TwitchIcon",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
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
                moveTo(11.571f, 4.714f)
                horizontalLineToRelative(1.715f)
                verticalLineToRelative(5.143f)
                horizontalLineTo(11.57f)
                close()
                moveToRelative(4.715f, 0f)
                horizontalLineTo(18f)
                verticalLineToRelative(5.143f)
                horizontalLineToRelative(-1.714f)
                close()
                moveTo(6f, 0f)
                lineTo(1.714f, 4.286f)
                verticalLineToRelative(15.428f)
                horizontalLineToRelative(5.143f)
                verticalLineTo(24f)
                lineToRelative(4.286f, -4.286f)
                horizontalLineToRelative(3.428f)
                lineTo(22.286f, 12f)
                verticalLineTo(0f)
                close()
                moveToRelative(14.571f, 11.143f)
                lineToRelative(-3.428f, 3.428f)
                horizontalLineToRelative(-3.429f)
                lineToRelative(-3f, 3f)
                verticalLineToRelative(-3f)
                horizontalLineTo(6.857f)
                verticalLineTo(1.714f)
                horizontalLineToRelative(13.714f)
                close()
            }
        }.build()
    }

    val FacebookIcon: ImageVector by lazy {
        ImageVector.Builder(
            name = "FacebookIcon",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
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
                moveTo(9.101f, 23.691f)
                verticalLineToRelative(-7.98f)
                horizontalLineTo(6.627f)
                verticalLineToRelative(-3.667f)
                horizontalLineToRelative(2.474f)
                verticalLineToRelative(-1.58f)
                curveToRelative(0f, -4.085f, 1.848f, -5.978f, 5.858f, -5.978f)
                curveToRelative(0.401f, 0f, 0.955f, 0.042f, 1.468f, 0.103f)
                arcToRelative(
                    8.68f,
                    8.68f,
                    0f,
                    isMoreThanHalf = false,
                    isPositiveArc = true,
                    1.141f,
                    0.195f
                )
                verticalLineToRelative(3.325f)
                arcToRelative(
                    8.623f,
                    8.623f,
                    0f,
                    isMoreThanHalf = false,
                    isPositiveArc = false,
                    -0.653f,
                    -0.036f
                )
                arcToRelative(
                    26.805f,
                    26.805f,
                    0f,
                    isMoreThanHalf = false,
                    isPositiveArc = false,
                    -0.733f,
                    -0.009f
                )
                curveToRelative(-0.707f, 0f, -1.259f, 0.096f, -1.675f, 0.309f)
                arcToRelative(
                    1.686f,
                    1.686f,
                    0f,
                    isMoreThanHalf = false,
                    isPositiveArc = false,
                    -0.679f,
                    0.622f
                )
                curveToRelative(-0.258f, 0.42f, -0.374f, 0.995f, -0.374f, 1.752f)
                verticalLineToRelative(1.297f)
                horizontalLineToRelative(3.919f)
                lineToRelative(-0.386f, 2.103f)
                lineToRelative(-0.287f, 1.564f)
                horizontalLineToRelative(-3.246f)
                verticalLineToRelative(8.245f)
                curveTo(19.396f, 23.238f, 24f, 18.179f, 24f, 12.044f)
                curveToRelative(0f, -6.627f, -5.373f, -12f, -12f, -12f)
                reflectiveCurveToRelative(-12f, 5.373f, -12f, 12f)
                curveToRelative(0f, 5.628f, 3.874f, 10.35f, 9.101f, 11.647f)
                close()
            }
        }.build()
    }

    val TiktokIcon: ImageVector by lazy {
        ImageVector.Builder(
            name = "TiktokIcon",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
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
                moveTo(12.525f, 0.02f)
                curveToRelative(1.31f, -0.02f, 2.61f, -0.01f, 3.91f, -0.02f)
                curveToRelative(0.08f, 1.53f, 0.63f, 3.09f, 1.75f, 4.17f)
                curveToRelative(1.12f, 1.11f, 2.7f, 1.62f, 4.24f, 1.79f)
                verticalLineToRelative(4.03f)
                curveToRelative(-1.44f, -0.05f, -2.89f, -0.35f, -4.2f, -0.97f)
                curveToRelative(-0.57f, -0.26f, -1.1f, -0.59f, -1.62f, -0.93f)
                curveToRelative(-0.01f, 2.92f, 0.01f, 5.84f, -0.02f, 8.75f)
                curveToRelative(-0.08f, 1.4f, -0.54f, 2.79f, -1.35f, 3.94f)
                curveToRelative(-1.31f, 1.92f, -3.58f, 3.17f, -5.91f, 3.21f)
                curveToRelative(-1.43f, 0.08f, -2.86f, -0.31f, -4.08f, -1.03f)
                curveToRelative(-2.02f, -1.19f, -3.44f, -3.37f, -3.65f, -5.71f)
                curveToRelative(-0.02f, -0.5f, -0.03f, -1f, -0.01f, -1.49f)
                curveToRelative(0.18f, -1.9f, 1.12f, -3.72f, 2.58f, -4.96f)
                curveToRelative(1.66f, -1.44f, 3.98f, -2.13f, 6.15f, -1.72f)
                curveToRelative(0.02f, 1.48f, -0.04f, 2.96f, -0.04f, 4.44f)
                curveToRelative(-0.99f, -0.32f, -2.15f, -0.23f, -3.02f, 0.37f)
                curveToRelative(-0.63f, 0.41f, -1.11f, 1.04f, -1.36f, 1.75f)
                curveToRelative(-0.21f, 0.51f, -0.15f, 1.07f, -0.14f, 1.61f)
                curveToRelative(0.24f, 1.64f, 1.82f, 3.02f, 3.5f, 2.87f)
                curveToRelative(1.12f, -0.01f, 2.19f, -0.66f, 2.77f, -1.61f)
                curveToRelative(0.19f, -0.33f, 0.4f, -0.67f, 0.41f, -1.06f)
                curveToRelative(0.1f, -1.79f, 0.06f, -3.57f, 0.07f, -5.36f)
                curveToRelative(0.01f, -4.03f, -0.01f, -8.05f, 0.02f, -12.07f)
                close()
            }
        }.build()
    }

    val TelegramIcon: ImageVector by lazy {
        ImageVector.Builder(
            name = "TelegramIcon",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
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
                moveTo(11.944f, 0f)
                arcTo(12f, 12f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0f, 12f)
                arcToRelative(12f, 12f, 0f, isMoreThanHalf = false, isPositiveArc = false, 12f, 12f)
                arcToRelative(
                    12f,
                    12f,
                    0f,
                    isMoreThanHalf = false,
                    isPositiveArc = false,
                    12f,
                    -12f
                )
                arcTo(12f, 12f, 0f, isMoreThanHalf = false, isPositiveArc = false, 12f, 0f)
                arcToRelative(
                    12f,
                    12f,
                    0f,
                    isMoreThanHalf = false,
                    isPositiveArc = false,
                    -0.056f,
                    0f
                )
                close()
                moveToRelative(4.962f, 7.224f)
                curveToRelative(0.1f, -0.002f, 0.321f, 0.023f, 0.465f, 0.14f)
                arcToRelative(
                    0.506f,
                    0.506f,
                    0f,
                    isMoreThanHalf = false,
                    isPositiveArc = true,
                    0.171f,
                    0.325f
                )
                curveToRelative(0.016f, 0.093f, 0.036f, 0.306f, 0.02f, 0.472f)
                curveToRelative(-0.18f, 1.898f, -0.962f, 6.502f, -1.36f, 8.627f)
                curveToRelative(-0.168f, 0.9f, -0.499f, 1.201f, -0.82f, 1.23f)
                curveToRelative(-0.696f, 0.065f, -1.225f, -0.46f, -1.9f, -0.902f)
                curveToRelative(-1.056f, -0.693f, -1.653f, -1.124f, -2.678f, -1.8f)
                curveToRelative(-1.185f, -0.78f, -0.417f, -1.21f, 0.258f, -1.91f)
                curveToRelative(0.177f, -0.184f, 3.247f, -2.977f, 3.307f, -3.23f)
                curveToRelative(0.007f, -0.032f, 0.014f, -0.15f, -0.056f, -0.212f)
                reflectiveCurveToRelative(-0.174f, -0.041f, -0.249f, -0.024f)
                curveToRelative(-0.106f, 0.024f, -1.793f, 1.14f, -5.061f, 3.345f)
                curveToRelative(-0.48f, 0.33f, -0.913f, 0.49f, -1.302f, 0.48f)
                curveToRelative(-0.428f, -0.008f, -1.252f, -0.241f, -1.865f, -0.44f)
                curveToRelative(-0.752f, -0.245f, -1.349f, -0.374f, -1.297f, -0.789f)
                curveToRelative(0.027f, -0.216f, 0.325f, -0.437f, 0.893f, -0.663f)
                curveToRelative(3.498f, -1.524f, 5.83f, -2.529f, 6.998f, -3.014f)
                curveToRelative(3.332f, -1.386f, 4.025f, -1.627f, 4.476f, -1.635f)
                close()
            }
        }.build()
    }

    val LineIcon: ImageVector by lazy {
        ImageVector.Builder(
            name = "LineIcon",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
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
                moveTo(19.365f, 9.863f)
                curveToRelative(0.349f, 0f, 0.63f, 0.285f, 0.63f, 0.631f)
                curveToRelative(0f, 0.345f, -0.281f, 0.63f, -0.63f, 0.63f)
                horizontalLineTo(17.61f)
                verticalLineToRelative(1.125f)
                horizontalLineToRelative(1.755f)
                curveToRelative(0.349f, 0f, 0.63f, 0.283f, 0.63f, 0.63f)
                curveToRelative(0f, 0.344f, -0.281f, 0.629f, -0.63f, 0.629f)
                horizontalLineToRelative(-2.386f)
                curveToRelative(-0.345f, 0f, -0.627f, -0.285f, -0.627f, -0.629f)
                verticalLineTo(8.108f)
                curveToRelative(0f, -0.345f, 0.282f, -0.63f, 0.63f, -0.63f)
                horizontalLineToRelative(2.386f)
                curveToRelative(0.346f, 0f, 0.627f, 0.285f, 0.627f, 0.63f)
                curveToRelative(0f, 0.349f, -0.281f, 0.63f, -0.63f, 0.63f)
                horizontalLineTo(17.61f)
                verticalLineToRelative(1.125f)
                horizontalLineToRelative(1.755f)
                close()
                moveToRelative(-3.855f, 3.016f)
                curveToRelative(0f, 0.27f, -0.174f, 0.51f, -0.432f, 0.596f)
                curveToRelative(-0.064f, 0.021f, -0.133f, 0.031f, -0.199f, 0.031f)
                curveToRelative(-0.211f, 0f, -0.391f, -0.09f, -0.51f, -0.25f)
                lineToRelative(-2.443f, -3.317f)
                verticalLineToRelative(2.94f)
                curveToRelative(0f, 0.344f, -0.279f, 0.629f, -0.631f, 0.629f)
                curveToRelative(-0.346f, 0f, -0.626f, -0.285f, -0.626f, -0.629f)
                verticalLineTo(8.108f)
                curveToRelative(0f, -0.27f, 0.173f, -0.51f, 0.43f, -0.595f)
                curveToRelative(0.06f, -0.023f, 0.136f, -0.033f, 0.194f, -0.033f)
                curveToRelative(0.195f, 0f, 0.375f, 0.104f, 0.495f, 0.254f)
                lineToRelative(2.462f, 3.33f)
                verticalLineTo(8.108f)
                curveToRelative(0f, -0.345f, 0.282f, -0.63f, 0.63f, -0.63f)
                curveToRelative(0.345f, 0f, 0.63f, 0.285f, 0.63f, 0.63f)
                verticalLineToRelative(4.771f)
                close()
                moveToRelative(-5.741f, 0f)
                curveToRelative(0f, 0.344f, -0.282f, 0.629f, -0.631f, 0.629f)
                curveToRelative(-0.345f, 0f, -0.627f, -0.285f, -0.627f, -0.629f)
                verticalLineTo(8.108f)
                curveToRelative(0f, -0.345f, 0.282f, -0.63f, 0.63f, -0.63f)
                curveToRelative(0.346f, 0f, 0.628f, 0.285f, 0.628f, 0.63f)
                verticalLineToRelative(4.771f)
                close()
                moveToRelative(-2.466f, 0.629f)
                horizontalLineTo(4.917f)
                curveToRelative(-0.345f, 0f, -0.63f, -0.285f, -0.63f, -0.629f)
                verticalLineTo(8.108f)
                curveToRelative(0f, -0.345f, 0.285f, -0.63f, 0.63f, -0.63f)
                curveToRelative(0.348f, 0f, 0.63f, 0.285f, 0.63f, 0.63f)
                verticalLineToRelative(4.141f)
                horizontalLineToRelative(1.756f)
                curveToRelative(0.348f, 0f, 0.629f, 0.283f, 0.629f, 0.63f)
                curveToRelative(0f, 0.344f, -0.282f, 0.629f, -0.629f, 0.629f)
                moveTo(24f, 10.314f)
                curveTo(24f, 4.943f, 18.615f, 0.572f, 12f, 0.572f)
                reflectiveCurveTo(0f, 4.943f, 0f, 10.314f)
                curveToRelative(0f, 4.811f, 4.27f, 8.842f, 10.035f, 9.608f)
                curveToRelative(0.391f, 0.082f, 0.923f, 0.258f, 1.058f, 0.59f)
                curveToRelative(0.12f, 0.301f, 0.079f, 0.766f, 0.038f, 1.08f)
                lineToRelative(-0.164f, 1.02f)
                curveToRelative(-0.045f, 0.301f, -0.24f, 1.186f, 1.049f, 0.645f)
                curveToRelative(1.291f, -0.539f, 6.916f, -4.078f, 9.436f, -6.975f)
                curveTo(23.176f, 14.393f, 24f, 12.458f, 24f, 10.314f)
            }
        }.build()
    }

    val SnapchatIcon: ImageVector by lazy {
        ImageVector.Builder(
            name = "SnapchatIcon",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
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
                moveTo(12.206f, 0.793f)
                curveToRelative(0.99f, 0f, 4.347f, 0.276f, 5.93f, 3.821f)
                curveToRelative(0.529f, 1.193f, 0.403f, 3.219f, 0.299f, 4.847f)
                lineToRelative(-0.003f, 0.06f)
                curveToRelative(-0.012f, 0.18f, -0.022f, 0.345f, -0.03f, 0.51f)
                curveToRelative(0.075f, 0.045f, 0.203f, 0.09f, 0.401f, 0.09f)
                curveToRelative(0.3f, -0.016f, 0.659f, -0.12f, 1.033f, -0.301f)
                curveToRelative(0.165f, -0.088f, 0.344f, -0.104f, 0.464f, -0.104f)
                curveToRelative(0.182f, 0f, 0.359f, 0.029f, 0.509f, 0.09f)
                curveToRelative(0.45f, 0.149f, 0.734f, 0.479f, 0.734f, 0.838f)
                curveToRelative(0.015f, 0.449f, -0.39f, 0.839f, -1.213f, 1.168f)
                curveToRelative(-0.089f, 0.029f, -0.209f, 0.075f, -0.344f, 0.119f)
                curveToRelative(-0.45f, 0.135f, -1.139f, 0.36f, -1.333f, 0.81f)
                curveToRelative(-0.09f, 0.224f, -0.061f, 0.524f, 0.12f, 0.868f)
                lineToRelative(0.015f, 0.015f)
                curveToRelative(0.06f, 0.136f, 1.526f, 3.475f, 4.791f, 4.014f)
                curveToRelative(0.255f, 0.044f, 0.435f, 0.27f, 0.42f, 0.509f)
                curveToRelative(0f, 0.075f, -0.015f, 0.149f, -0.045f, 0.225f)
                curveToRelative(-0.24f, 0.569f, -1.273f, 0.988f, -3.146f, 1.271f)
                curveToRelative(-0.059f, 0.091f, -0.12f, 0.375f, -0.164f, 0.57f)
                curveToRelative(-0.029f, 0.179f, -0.074f, 0.36f, -0.134f, 0.553f)
                curveToRelative(-0.076f, 0.271f, -0.27f, 0.405f, -0.555f, 0.405f)
                horizontalLineToRelative(-0.03f)
                curveToRelative(-0.135f, 0f, -0.313f, -0.031f, -0.538f, -0.074f)
                curveToRelative(-0.36f, -0.075f, -0.765f, -0.135f, -1.273f, -0.135f)
                curveToRelative(-0.3f, 0f, -0.599f, 0.015f, -0.913f, 0.074f)
                curveToRelative(-0.6f, 0.104f, -1.123f, 0.464f, -1.723f, 0.884f)
                curveToRelative(-0.853f, 0.599f, -1.826f, 1.288f, -3.294f, 1.288f)
                curveToRelative(-0.06f, 0f, -0.119f, -0.015f, -0.18f, -0.015f)
                horizontalLineToRelative(-0.149f)
                curveToRelative(-1.468f, 0f, -2.427f, -0.675f, -3.279f, -1.288f)
                curveToRelative(-0.599f, -0.42f, -1.107f, -0.779f, -1.707f, -0.884f)
                curveToRelative(-0.314f, -0.045f, -0.629f, -0.074f, -0.928f, -0.074f)
                curveToRelative(-0.54f, 0f, -0.958f, 0.089f, -1.272f, 0.149f)
                curveToRelative(-0.211f, 0.043f, -0.391f, 0.074f, -0.54f, 0.074f)
                curveToRelative(-0.374f, 0f, -0.523f, -0.224f, -0.583f, -0.42f)
                curveToRelative(-0.061f, -0.192f, -0.09f, -0.389f, -0.135f, -0.567f)
                curveToRelative(-0.046f, -0.181f, -0.105f, -0.494f, -0.166f, -0.57f)
                curveToRelative(-1.918f, -0.222f, -2.95f, -0.642f, -3.189f, -1.226f)
                curveToRelative(-0.031f, -0.063f, -0.052f, -0.15f, -0.055f, -0.225f)
                curveToRelative(-0.015f, -0.243f, 0.165f, -0.465f, 0.42f, -0.509f)
                curveToRelative(3.264f, -0.54f, 4.73f, -3.879f, 4.791f, -4.02f)
                lineToRelative(0.016f, -0.029f)
                curveToRelative(0.18f, -0.345f, 0.224f, -0.645f, 0.119f, -0.869f)
                curveToRelative(-0.195f, -0.434f, -0.884f, -0.658f, -1.332f, -0.809f)
                curveToRelative(-0.121f, -0.029f, -0.24f, -0.074f, -0.346f, -0.119f)
                curveToRelative(-1.107f, -0.435f, -1.257f, -0.93f, -1.197f, -1.273f)
                curveToRelative(0.09f, -0.479f, 0.674f, -0.793f, 1.168f, -0.793f)
                curveToRelative(0.146f, 0f, 0.27f, 0.029f, 0.383f, 0.074f)
                curveToRelative(0.42f, 0.194f, 0.789f, 0.3f, 1.104f, 0.3f)
                curveToRelative(0.234f, 0f, 0.384f, -0.06f, 0.465f, -0.105f)
                lineToRelative(-0.046f, -0.569f)
                curveToRelative(-0.098f, -1.626f, -0.225f, -3.651f, 0.307f, -4.837f)
                curveTo(7.392f, 1.077f, 10.739f, 0.807f, 11.727f, 0.807f)
                lineToRelative(0.419f, -0.015f)
                horizontalLineToRelative(0.06f)
                close()
            }
        }.build()
    }

    val SinaweiboIcon: ImageVector by lazy {
        ImageVector.Builder(
            name = "SinaweiboIcon",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
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
                moveTo(10.098f, 20.323f)
                curveToRelative(-3.977f, 0.391f, -7.414f, -1.406f, -7.672f, -4.02f)
                curveToRelative(-0.259f, -2.609f, 2.759f, -5.047f, 6.74f, -5.441f)
                curveToRelative(3.979f, -0.394f, 7.413f, 1.404f, 7.671f, 4.018f)
                curveToRelative(0.259f, 2.6f, -2.759f, 5.049f, -6.737f, 5.439f)
                lineToRelative(-0.002f, 0.004f)
                close()
                moveTo(9.05f, 17.219f)
                curveToRelative(-0.384f, 0.616f, -1.208f, 0.884f, -1.829f, 0.602f)
                curveToRelative(-0.612f, -0.279f, -0.793f, -0.991f, -0.406f, -1.593f)
                curveToRelative(0.379f, -0.595f, 1.176f, -0.861f, 1.793f, -0.601f)
                curveToRelative(0.622f, 0.263f, 0.82f, 0.972f, 0.442f, 1.592f)
                close()
                moveToRelative(1.27f, -1.627f)
                curveToRelative(-0.141f, 0.237f, -0.449f, 0.353f, -0.689f, 0.253f)
                curveToRelative(-0.236f, -0.09f, -0.313f, -0.361f, -0.177f, -0.586f)
                curveToRelative(0.138f, -0.227f, 0.436f, -0.346f, 0.672f, -0.24f)
                curveToRelative(0.239f, 0.09f, 0.315f, 0.36f, 0.18f, 0.601f)
                lineToRelative(0.014f, -0.028f)
                close()
                moveToRelative(0.176f, -2.719f)
                curveToRelative(-1.893f, -0.493f, -4.033f, 0.45f, -4.857f, 2.118f)
                curveToRelative(-0.836f, 1.704f, -0.026f, 3.591f, 1.886f, 4.21f)
                curveToRelative(1.983f, 0.64f, 4.318f, -0.341f, 5.132f, -2.179f)
                curveToRelative(0.8f, -1.793f, -0.201f, -3.642f, -2.161f, -4.149f)
                close()
                moveToRelative(7.563f, -1.224f)
                curveToRelative(-0.346f, -0.105f, -0.57f, -0.18f, -0.405f, -0.615f)
                curveToRelative(0.375f, -0.977f, 0.42f, -1.804f, 0f, -2.404f)
                curveToRelative(-0.781f, -1.112f, -2.915f, -1.053f, -5.364f, -0.03f)
                curveToRelative(0f, 0f, -0.766f, 0.331f, -0.571f, -0.271f)
                curveToRelative(0.376f, -1.217f, 0.315f, -2.224f, -0.27f, -2.809f)
                curveToRelative(-1.338f, -1.337f, -4.869f, 0.045f, -7.888f, 3.08f)
                curveTo(1.309f, 10.87f, 0f, 13.273f, 0f, 15.348f)
                curveToRelative(0f, 3.981f, 5.099f, 6.395f, 10.086f, 6.395f)
                curveToRelative(6.536f, 0f, 10.888f, -3.801f, 10.888f, -6.82f)
                curveToRelative(0f, -1.822f, -1.547f, -2.854f, -2.915f, -3.284f)
                verticalLineToRelative(0.01f)
                close()
                moveToRelative(1.908f, -5.092f)
                curveToRelative(-0.766f, -0.856f, -1.908f, -1.187f, -2.96f, -0.962f)
                curveToRelative(-0.436f, 0.09f, -0.706f, 0.511f, -0.616f, 0.932f)
                curveToRelative(0.09f, 0.42f, 0.511f, 0.691f, 0.932f, 0.602f)
                curveToRelative(0.511f, -0.105f, 1.067f, 0.044f, 1.442f, 0.465f)
                curveToRelative(0.376f, 0.421f, 0.466f, 0.977f, 0.316f, 1.473f)
                curveToRelative(-0.136f, 0.406f, 0.089f, 0.856f, 0.51f, 0.992f)
                curveToRelative(0.405f, 0.119f, 0.857f, -0.105f, 0.992f, -0.512f)
                curveToRelative(0.33f, -1.021f, 0.12f, -2.178f, -0.646f, -3.035f)
                lineToRelative(0.03f, 0.045f)
                close()
                moveToRelative(2.418f, -2.195f)
                curveToRelative(-1.576f, -1.757f, -3.905f, -2.419f, -6.054f, -1.968f)
                curveToRelative(-0.496f, 0.104f, -0.812f, 0.587f, -0.706f, 1.081f)
                curveToRelative(0.104f, 0.496f, 0.586f, 0.813f, 1.082f, 0.707f)
                curveToRelative(1.532f, -0.331f, 3.185f, 0.15f, 4.296f, 1.383f)
                curveToRelative(1.112f, 1.246f, 1.429f, 2.943f, 0.947f, 4.416f)
                curveToRelative(-0.165f, 0.48f, 0.106f, 1.007f, 0.586f, 1.157f)
                curveToRelative(0.479f, 0.165f, 0.991f, -0.104f, 1.157f, -0.586f)
                curveToRelative(0.675f, -2.088f, 0.241f, -4.478f, -1.338f, -6.235f)
                lineToRelative(0.03f, 0.045f)
                close()
            }
        }.build()
    }

    val InstagramIcon: ImageVector by lazy {
        ImageVector.Builder(
            name = "InstagramIcon",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
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
                moveTo(7.0301f, 0.084f)
                curveToRelative(-1.2768f, 0.0602f, -2.1487f, 0.264f, -2.911f, 0.5634f)
                curveToRelative(-0.7888f, 0.3075f, -1.4575f, 0.72f, -2.1228f, 1.3877f)
                curveToRelative(-0.6652f, 0.6677f, -1.075f, 1.3368f, -1.3802f, 2.127f)
                curveToRelative(-0.2954f, 0.7638f, -0.4956f, 1.6365f, -0.552f, 2.914f)
                curveToRelative(-0.0564f, 1.2775f, -0.0689f, 1.6882f, -0.0626f, 4.947f)
                curveToRelative(0.0062f, 3.2586f, 0.0206f, 3.6671f, 0.0825f, 4.9473f)
                curveToRelative(0.061f, 1.2765f, 0.264f, 2.1482f, 0.5635f, 2.9107f)
                curveToRelative(0.308f, 0.7889f, 0.72f, 1.4573f, 1.388f, 2.1228f)
                curveToRelative(0.6679f, 0.6655f, 1.3365f, 1.0743f, 2.1285f, 1.38f)
                curveToRelative(0.7632f, 0.295f, 1.6361f, 0.4961f, 2.9134f, 0.552f)
                curveToRelative(1.2773f, 0.056f, 1.6884f, 0.069f, 4.9462f, 0.0627f)
                curveToRelative(3.2578f, -0.0062f, 3.668f, -0.0207f, 4.9478f, -0.0814f)
                curveToRelative(1.28f, -0.0607f, 2.147f, -0.2652f, 2.9098f, -0.5633f)
                curveToRelative(0.7889f, -0.3086f, 1.4578f, -0.72f, 2.1228f, -1.3881f)
                curveToRelative(0.665f, -0.6682f, 1.0745f, -1.3378f, 1.3795f, -2.1284f)
                curveToRelative(0.2957f, -0.7632f, 0.4966f, -1.636f, 0.552f, -2.9124f)
                curveToRelative(0.056f, -1.2809f, 0.0692f, -1.6898f, 0.063f, -4.948f)
                curveToRelative(-0.0063f, -3.2583f, -0.021f, -3.6668f, -0.0817f, -4.9465f)
                curveToRelative(-0.0607f, -1.2797f, -0.264f, -2.1487f, -0.5633f, -2.9117f)
                curveToRelative(-0.3084f, -0.7889f, -0.72f, -1.4568f, -1.3876f, -2.1228f)
                curveTo(21.2982f, 1.33f, 20.628f, 0.9208f, 19.8378f, 0.6165f)
                curveTo(19.074f, 0.321f, 18.2017f, 0.1197f, 16.9244f, 0.0645f)
                curveTo(15.6471f, 0.0093f, 15.236f, -0.005f, 11.977f, 0.0014f)
                curveTo(8.718f, 0.0076f, 8.31f, 0.0215f, 7.0301f, 0.0839f)
                moveToRelative(0.1402f, 21.6932f)
                curveToRelative(-1.17f, -0.0509f, -1.8053f, -0.2453f, -2.2287f, -0.408f)
                curveToRelative(-0.5606f, -0.216f, -0.96f, -0.4771f, -1.3819f, -0.895f)
                curveToRelative(-0.422f, -0.4178f, -0.6811f, -0.8186f, -0.9f, -1.378f)
                curveToRelative(-0.1644f, -0.4234f, -0.3624f, -1.058f, -0.4171f, -2.228f)
                curveToRelative(-0.0595f, -1.2645f, -0.072f, -1.6442f, -0.079f, -4.848f)
                curveToRelative(-0.007f, -3.2037f, 0.0053f, -3.583f, 0.0607f, -4.848f)
                curveToRelative(0.05f, -1.169f, 0.2456f, -1.805f, 0.408f, -2.2282f)
                curveToRelative(0.216f, -0.5613f, 0.4762f, -0.96f, 0.895f, -1.3816f)
                curveToRelative(0.4188f, -0.4217f, 0.8184f, -0.6814f, 1.3783f, -0.9003f)
                curveToRelative(0.423f, -0.1651f, 1.0575f, -0.3614f, 2.227f, -0.4171f)
                curveToRelative(1.2655f, -0.06f, 1.6447f, -0.072f, 4.848f, -0.079f)
                curveToRelative(3.2033f, -0.007f, 3.5835f, 0.005f, 4.8495f, 0.0608f)
                curveToRelative(1.169f, 0.0508f, 1.8053f, 0.2445f, 2.228f, 0.408f)
                curveToRelative(0.5608f, 0.216f, 0.96f, 0.4754f, 1.3816f, 0.895f)
                curveToRelative(0.4217f, 0.4194f, 0.6816f, 0.8176f, 0.9005f, 1.3787f)
                curveToRelative(0.1653f, 0.4217f, 0.3617f, 1.056f, 0.4169f, 2.2263f)
                curveToRelative(0.0602f, 1.2655f, 0.0739f, 1.645f, 0.0796f, 4.848f)
                curveToRelative(0.0058f, 3.203f, -0.0055f, 3.5834f, -0.061f, 4.848f)
                curveToRelative(-0.051f, 1.17f, -0.245f, 1.8055f, -0.408f, 2.2294f)
                curveToRelative(-0.216f, 0.5604f, -0.4763f, 0.96f, -0.8954f, 1.3814f)
                curveToRelative(-0.419f, 0.4215f, -0.8181f, 0.6811f, -1.3783f, 0.9f)
                curveToRelative(-0.4224f, 0.1649f, -1.0577f, 0.3617f, -2.2262f, 0.4174f)
                curveToRelative(-1.2656f, 0.0595f, -1.6448f, 0.072f, -4.8493f, 0.079f)
                curveToRelative(-3.2045f, 0.007f, -3.5825f, -0.006f, -4.848f, -0.0608f)
                moveTo(16.953f, 5.5864f)
                arcTo(
                    1.44f,
                    1.44f,
                    0f,
                    isMoreThanHalf = true,
                    isPositiveArc = false,
                    18.39f,
                    4.144f
                )
                arcToRelative(
                    1.44f,
                    1.44f,
                    0f,
                    isMoreThanHalf = false,
                    isPositiveArc = false,
                    -1.437f,
                    1.4424f
                )
                moveTo(5.8385f, 12.012f)
                curveToRelative(0.0067f, 3.4032f, 2.7706f, 6.1557f, 6.173f, 6.1493f)
                curveToRelative(3.4026f, -0.0065f, 6.157f, -2.7701f, 6.1506f, -6.1733f)
                curveToRelative(-0.0065f, -3.4032f, -2.771f, -6.1565f, -6.174f, -6.1498f)
                curveToRelative(-3.403f, 0.0067f, -6.156f, 2.771f, -6.1496f, 6.1738f)
                moveTo(8f, 12.0077f)
                arcToRelative(
                    4f,
                    4f,
                    0f,
                    isMoreThanHalf = true,
                    isPositiveArc = true,
                    4.008f,
                    3.9921f
                )
                arcTo(
                    3.9996f,
                    3.9996f,
                    0f,
                    isMoreThanHalf = false,
                    isPositiveArc = true,
                    8f,
                    12.0077f
                )
            }
        }.build()
    }

    val NeteasecloudmusicIcon: ImageVector by lazy {
        ImageVector.Builder(
            name = "NeteasecloudmusicIcon",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
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
                moveTo(13.046f, 9.388f)
                arcToRelative(
                    3.919f,
                    3.919f,
                    0f,
                    isMoreThanHalf = false,
                    isPositiveArc = false,
                    -0.66f,
                    0.19f
                )
                curveToRelative(-0.809f, 0.312f, -1.447f, 0.991f, -1.666f, 1.775f)
                arcToRelative(
                    2.269f,
                    2.269f,
                    0f,
                    isMoreThanHalf = false,
                    isPositiveArc = false,
                    -0.074f,
                    0.81f
                )
                curveToRelative(0.048f, 0.546f, 0.333f, 1.05f, 0.764f, 1.35f)
                arcToRelative(
                    1.483f,
                    1.483f,
                    0f,
                    isMoreThanHalf = false,
                    isPositiveArc = false,
                    2.01f,
                    -0.286f
                )
                curveToRelative(0.406f, -0.531f, 0.355f, -1.183f, 0.24f, -1.636f)
                curveToRelative(-0.098f, -0.387f, -0.22f, -0.816f, -0.345f, -1.249f)
                arcToRelative(
                    64.76f,
                    64.76f,
                    0f,
                    isMoreThanHalf = false,
                    isPositiveArc = true,
                    -0.269f,
                    -0.954f
                )
                close()
                moveToRelative(-0.82f, 10.07f)
                curveToRelative(-3.984f, 0f, -7.224f, -3.24f, -7.224f, -7.223f)
                curveToRelative(0f, -0.98f, 0.226f, -3.02f, 1.884f, -4.822f)
                arcTo(
                    7.188f,
                    7.188f,
                    0f,
                    isMoreThanHalf = false,
                    isPositiveArc = true,
                    9.502f,
                    5.6f
                )
                arcToRelative(
                    0.792f,
                    0.792f,
                    0f,
                    isMoreThanHalf = true,
                    isPositiveArc = true,
                    0.587f,
                    1.472f
                )
                arcToRelative(
                    5.619f,
                    5.619f,
                    0f,
                    isMoreThanHalf = false,
                    isPositiveArc = false,
                    -2.795f,
                    2.462f
                )
                arcToRelative(
                    5.538f,
                    5.538f,
                    0f,
                    isMoreThanHalf = false,
                    isPositiveArc = false,
                    -0.707f,
                    2.7f
                )
                arcToRelative(
                    5.645f,
                    5.645f,
                    0f,
                    isMoreThanHalf = false,
                    isPositiveArc = false,
                    5.638f,
                    5.638f
                )
                curveToRelative(1.844f, 0f, 3.627f, -0.953f, 4.542f, -2.428f)
                curveToRelative(1.042f, -1.68f, 0.772f, -3.931f, -0.627f, -5.238f)
                arcToRelative(
                    3.299f,
                    3.299f,
                    0f,
                    isMoreThanHalf = false,
                    isPositiveArc = false,
                    -1.437f,
                    -0.777f
                )
                curveToRelative(0.172f, 0.589f, 0.334f, 1.18f, 0.494f, 1.772f)
                curveToRelative(0.284f, 1.12f, 0.1f, 2.181f, -0.519f, 2.989f)
                curveToRelative(-0.39f, 0.51f, -0.956f, 0.888f, -1.592f, 1.064f)
                arcToRelative(
                    3.038f,
                    3.038f,
                    0f,
                    isMoreThanHalf = false,
                    isPositiveArc = true,
                    -2.58f,
                    -0.44f
                )
                arcToRelative(
                    3.45f,
                    3.45f,
                    0f,
                    isMoreThanHalf = false,
                    isPositiveArc = true,
                    -1.44f,
                    -2.514f
                )
                curveToRelative(-0.04f, -0.467f, 0.002f, -0.93f, 0.128f, -1.376f)
                curveToRelative(0.35f, -1.256f, 1.356f, -2.339f, 2.622f, -2.826f)
                arcToRelative(
                    5.5f,
                    5.5f,
                    0f,
                    isMoreThanHalf = false,
                    isPositiveArc = true,
                    0.823f,
                    -0.246f
                )
                lineToRelative(-0.134f, -0.505f)
                curveToRelative(-0.37f, -1.371f, 0.25f, -2.579f, 1.547f, -3.007f)
                curveToRelative(0.329f, -0.109f, 0.68f, -0.145f, 1.025f, -0.105f)
                curveToRelative(0.792f, 0.09f, 1.476f, 0.592f, 1.709f, 1.023f)
                curveToRelative(0.258f, 0.507f, -0.096f, 1.153f, -0.706f, 1.153f)
                arcToRelative(
                    0.788f,
                    0.788f,
                    0f,
                    isMoreThanHalf = false,
                    isPositiveArc = true,
                    -0.54f,
                    -0.213f
                )
                curveToRelative(-0.088f, -0.08f, -0.163f, -0.174f, -0.259f, -0.247f)
                arcToRelative(
                    0.825f,
                    0.825f,
                    0f,
                    isMoreThanHalf = false,
                    isPositiveArc = false,
                    -0.632f,
                    -0.166f
                )
                arcToRelative(
                    0.807f,
                    0.807f,
                    0f,
                    isMoreThanHalf = false,
                    isPositiveArc = false,
                    -0.634f,
                    0.551f
                )
                curveToRelative(-0.056f, 0.191f, -0.031f, 0.406f, 0.02f, 0.595f)
                curveToRelative(0.07f, 0.256f, 0.159f, 0.597f, 0.217f, 0.82f)
                curveToRelative(1.11f, 0.098f, 2.162f, 0.54f, 2.97f, 1.296f)
                curveToRelative(1.974f, 1.844f, 2.35f, 4.886f, 0.892f, 7.233f)
                curveToRelative(-1.197f, 1.93f, -3.509f, 3.177f, -5.889f, 3.177f)
                close()
                moveTo(0f, 12f)
                curveToRelative(0f, 6.627f, 5.373f, 12f, 12f, 12f)
                reflectiveCurveToRelative(12f, -5.373f, 12f, -12f)
                reflectiveCurveTo(18.627f, 0f, 12f, 0f)
                reflectiveCurveTo(0f, 5.373f, 0f, 12f)
                close()
            }
        }.build()
    }

    val YoutubemusicIcon: ImageVector by lazy {
        ImageVector.Builder(
            name = "YoutubemusicIcon",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
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
                moveTo(12f, 0f)
                curveTo(5.376f, 0f, 0f, 5.376f, 0f, 12f)
                reflectiveCurveToRelative(5.376f, 12f, 12f, 12f)
                reflectiveCurveToRelative(12f, -5.376f, 12f, -12f)
                reflectiveCurveTo(18.624f, 0f, 12f, 0f)
                close()
                moveToRelative(0f, 19.104f)
                curveToRelative(-3.924f, 0f, -7.104f, -3.18f, -7.104f, -7.104f)
                reflectiveCurveTo(8.076f, 4.896f, 12f, 4.896f)
                reflectiveCurveToRelative(7.104f, 3.18f, 7.104f, 7.104f)
                reflectiveCurveToRelative(-3.18f, 7.104f, -7.104f, 7.104f)
                close()
                moveToRelative(0f, -13.332f)
                curveToRelative(-3.432f, 0f, -6.228f, 2.796f, -6.228f, 6.228f)
                reflectiveCurveTo(8.568f, 18.228f, 12f, 18.228f)
                reflectiveCurveToRelative(6.228f, -2.796f, 6.228f, -6.228f)
                reflectiveCurveTo(15.432f, 5.772f, 12f, 5.772f)
                close()
                moveTo(9.684f, 15.54f)
                verticalLineTo(8.46f)
                lineTo(15.816f, 12f)
                lineToRelative(-6.132f, 3.54f)
                close()
            }
        }.build()
    }

    val SoundcloudIcon: ImageVector by lazy {
        ImageVector.Builder(
            name = "SoundcloudIcon",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
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
                moveTo(1.175f, 12.225f)
                curveToRelative(-0.051f, 0f, -0.094f, 0.046f, -0.101f, 0.1f)
                lineToRelative(-0.233f, 2.154f)
                lineToRelative(0.233f, 2.105f)
                curveToRelative(0.007f, 0.058f, 0.05f, 0.098f, 0.101f, 0.098f)
                curveToRelative(0.05f, 0f, 0.09f, -0.04f, 0.099f, -0.098f)
                lineToRelative(0.255f, -2.105f)
                lineToRelative(-0.27f, -2.154f)
                curveToRelative(0f, -0.057f, -0.045f, -0.1f, -0.09f, -0.1f)
                moveToRelative(-0.899f, 0.828f)
                curveToRelative(-0.06f, 0f, -0.091f, 0.037f, -0.104f, 0.094f)
                lineTo(0f, 14.479f)
                lineToRelative(0.165f, 1.308f)
                curveToRelative(0f, 0.055f, 0.045f, 0.094f, 0.09f, 0.094f)
                reflectiveCurveToRelative(0.089f, -0.045f, 0.104f, -0.104f)
                lineToRelative(0.21f, -1.319f)
                lineToRelative(-0.21f, -1.334f)
                curveToRelative(0f, -0.061f, -0.044f, -0.09f, -0.09f, -0.09f)
                moveToRelative(1.83f, -1.229f)
                curveToRelative(-0.061f, 0f, -0.12f, 0.045f, -0.12f, 0.104f)
                lineToRelative(-0.21f, 2.563f)
                lineToRelative(0.225f, 2.458f)
                curveToRelative(0f, 0.06f, 0.045f, 0.12f, 0.119f, 0.12f)
                curveToRelative(0.061f, 0f, 0.105f, -0.061f, 0.121f, -0.12f)
                lineToRelative(0.254f, -2.474f)
                lineToRelative(-0.254f, -2.548f)
                curveToRelative(-0.016f, -0.06f, -0.061f, -0.12f, -0.121f, -0.12f)
                moveToRelative(0.945f, -0.089f)
                curveToRelative(-0.075f, 0f, -0.135f, 0.06f, -0.15f, 0.135f)
                lineToRelative(-0.193f, 2.64f)
                lineToRelative(0.21f, 2.544f)
                curveToRelative(0.016f, 0.077f, 0.075f, 0.138f, 0.149f, 0.138f)
                curveToRelative(0.075f, 0f, 0.135f, -0.061f, 0.15f, -0.15f)
                lineToRelative(0.24f, -2.532f)
                lineToRelative(-0.24f, -2.623f)
                curveToRelative(0f, -0.075f, -0.06f, -0.135f, -0.135f, -0.135f)
                lineToRelative(-0.031f, -0.017f)
                close()
                moveToRelative(1.155f, 0.36f)
                curveToRelative(-0.005f, -0.09f, -0.075f, -0.149f, -0.159f, -0.149f)
                curveToRelative(-0.09f, 0f, -0.158f, 0.06f, -0.164f, 0.149f)
                lineToRelative(-0.217f, 2.43f)
                lineToRelative(0.2f, 2.563f)
                curveToRelative(0f, 0.09f, 0.075f, 0.157f, 0.159f, 0.157f)
                curveToRelative(0.074f, 0f, 0.148f, -0.068f, 0.148f, -0.158f)
                lineToRelative(0.227f, -2.563f)
                lineToRelative(-0.227f, -2.444f)
                lineToRelative(0.033f, 0.015f)
                close()
                moveToRelative(0.809f, -1.709f)
                curveToRelative(-0.101f, 0f, -0.18f, 0.09f, -0.18f, 0.181f)
                lineToRelative(-0.21f, 3.957f)
                lineToRelative(0.187f, 2.563f)
                curveToRelative(0f, 0.09f, 0.08f, 0.164f, 0.18f, 0.164f)
                curveToRelative(0.094f, 0f, 0.174f, -0.09f, 0.18f, -0.18f)
                lineToRelative(0.209f, -2.563f)
                lineToRelative(-0.209f, -3.972f)
                curveToRelative(-0.008f, -0.104f, -0.088f, -0.18f, -0.18f, -0.18f)
                moveToRelative(0.959f, -0.914f)
                curveToRelative(-0.105f, 0f, -0.195f, 0.09f, -0.203f, 0.194f)
                lineToRelative(-0.18f, 4.872f)
                lineToRelative(0.165f, 2.548f)
                curveToRelative(0f, 0.12f, 0.09f, 0.209f, 0.195f, 0.209f)
                curveToRelative(0.104f, 0f, 0.194f, -0.089f, 0.21f, -0.209f)
                lineToRelative(0.193f, -2.548f)
                lineToRelative(-0.192f, -4.856f)
                curveToRelative(-0.016f, -0.12f, -0.105f, -0.21f, -0.21f, -0.21f)
                moveToRelative(0.989f, -0.449f)
                curveToRelative(-0.121f, 0f, -0.211f, 0.089f, -0.225f, 0.209f)
                lineToRelative(-0.165f, 5.275f)
                lineToRelative(0.165f, 2.52f)
                curveToRelative(0.014f, 0.119f, 0.104f, 0.225f, 0.225f, 0.225f)
                curveToRelative(0.119f, 0f, 0.225f, -0.105f, 0.225f, -0.225f)
                lineToRelative(0.195f, -2.52f)
                lineToRelative(-0.196f, -5.275f)
                curveToRelative(0f, -0.12f, -0.105f, -0.225f, -0.225f, -0.225f)
                moveToRelative(1.245f, 0.045f)
                curveToRelative(0f, -0.135f, -0.105f, -0.24f, -0.24f, -0.24f)
                curveToRelative(-0.119f, 0f, -0.24f, 0.105f, -0.24f, 0.24f)
                lineToRelative(-0.149f, 5.441f)
                lineToRelative(0.149f, 2.503f)
                curveToRelative(0.016f, 0.135f, 0.121f, 0.24f, 0.256f, 0.24f)
                reflectiveCurveToRelative(0.24f, -0.105f, 0.24f, -0.24f)
                lineToRelative(0.164f, -2.503f)
                lineToRelative(-0.164f, -5.456f)
                lineToRelative(-0.016f, 0.015f)
                close()
                moveToRelative(0.749f, -0.134f)
                curveToRelative(-0.135f, 0f, -0.255f, 0.119f, -0.255f, 0.254f)
                lineToRelative(-0.15f, 5.322f)
                lineToRelative(0.15f, 2.473f)
                curveToRelative(0f, 0.15f, 0.12f, 0.255f, 0.255f, 0.255f)
                reflectiveCurveToRelative(0.255f, -0.12f, 0.255f, -0.27f)
                lineToRelative(0.15f, -2.474f)
                lineToRelative(-0.165f, -5.307f)
                curveToRelative(0f, -0.148f, -0.12f, -0.27f, -0.271f, -0.27f)
                moveToRelative(1.005f, 0.166f)
                curveToRelative(-0.164f, 0f, -0.284f, 0.135f, -0.284f, 0.285f)
                lineToRelative(-0.103f, 5.143f)
                lineToRelative(0.135f, 2.474f)
                curveToRelative(0f, 0.149f, 0.119f, 0.277f, 0.284f, 0.277f)
                curveToRelative(0.149f, 0f, 0.271f, -0.12f, 0.284f, -0.285f)
                lineToRelative(0.121f, -2.443f)
                lineToRelative(-0.135f, -5.112f)
                curveToRelative(-0.012f, -0.164f, -0.135f, -0.285f, -0.285f, -0.285f)
                moveToRelative(1.184f, -0.945f)
                curveToRelative(-0.045f, -0.029f, -0.105f, -0.044f, -0.165f, -0.044f)
                reflectiveCurveToRelative(-0.119f, 0.015f, -0.165f, 0.044f)
                curveToRelative(-0.09f, 0.054f, -0.149f, 0.15f, -0.149f, 0.255f)
                verticalLineToRelative(0.061f)
                lineToRelative(-0.104f, 6.048f)
                lineToRelative(0.115f, 2.449f)
                verticalLineToRelative(0.008f)
                curveToRelative(0.008f, 0.06f, 0.03f, 0.135f, 0.074f, 0.18f)
                curveToRelative(0.058f, 0.061f, 0.142f, 0.104f, 0.234f, 0.104f)
                curveToRelative(0.08f, 0f, 0.158f, -0.044f, 0.209f, -0.09f)
                curveToRelative(0.058f, -0.06f, 0.091f, -0.135f, 0.091f, -0.225f)
                lineToRelative(0.015f, -0.24f)
                lineToRelative(0.117f, -2.203f)
                lineToRelative(-0.135f, -6.086f)
                curveToRelative(0f, -0.104f, -0.061f, -0.193f, -0.135f, -0.239f)
                lineToRelative(-0.002f, -0.022f)
                close()
                moveToRelative(1.006f, -0.547f)
                curveToRelative(-0.045f, -0.045f, -0.09f, -0.061f, -0.15f, -0.061f)
                curveToRelative(-0.074f, 0f, -0.149f, 0.016f, -0.209f, 0.061f)
                curveToRelative(-0.075f, 0.061f, -0.119f, 0.15f, -0.119f, 0.24f)
                verticalLineToRelative(0.029f)
                lineToRelative(-0.137f, 6.609f)
                lineToRelative(0.076f, 1.215f)
                lineToRelative(0.061f, 1.185f)
                curveToRelative(0f, 0.164f, 0.148f, 0.314f, 0.328f, 0.314f)
                curveToRelative(0.181f, 0f, 0.33f, -0.15f, 0.33f, -0.329f)
                lineToRelative(0.15f, -2.414f)
                lineToRelative(-0.15f, -6.637f)
                curveToRelative(0f, -0.12f, -0.074f, -0.221f, -0.165f, -0.277f)
                moveToRelative(8.934f, 3.777f)
                curveToRelative(-0.405f, 0f, -0.795f, 0.086f, -1.139f, 0.232f)
                curveToRelative(-0.24f, -2.654f, -2.46f, -4.736f, -5.188f, -4.736f)
                curveToRelative(-0.659f, 0f, -1.305f, 0.135f, -1.889f, 0.359f)
                curveToRelative(-0.225f, 0.09f, -0.27f, 0.18f, -0.285f, 0.359f)
                verticalLineToRelative(9.368f)
                curveToRelative(0.016f, 0.18f, 0.15f, 0.33f, 0.33f, 0.345f)
                horizontalLineToRelative(8.185f)
                curveTo(22.681f, 17.218f, 24f, 15.914f, 24f, 14.28f)
                reflectiveCurveToRelative(-1.319f, -2.952f, -2.938f, -2.952f)
            }
        }.build()
    }

}