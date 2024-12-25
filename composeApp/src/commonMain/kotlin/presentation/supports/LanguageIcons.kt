package io.github.vrcmteam.vrcm.presentation.supports

import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.group
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

object LanguageIcons {
    fun getFlag(language: String): ImageVector? {
        return when (language) {
            "eng" -> English
            "kor" -> Korea
            "rus" -> Russia
            "spa" -> Spain
            "por" -> Portugal
            "zho" -> China
            "deu" -> Germany
            "jpn" -> Japan
            "fra" -> France
            "swe" -> Sweden
            "nld" -> Netherlands
            "pol" -> Poland
            "dan" -> Denmark
            "nor" -> Norway
            "ita" -> Italy
            "tha" -> Thailand
            "fin" -> Finland
            "hun" -> Hungary
            "ces" -> Czech
            "tur" -> Turkey
            "ara" -> Arabic
            "ron" -> Romania
            "vie" -> Vietnam
            "ase" -> USA
            "bfi" -> UK
            "dse" -> Netherlands
            "fsl" -> France
            "kvk" -> Korea
            else -> null
        }
    }

    val English: ImageVector by lazy {
        ImageVector.Builder(
            name = "English",
            defaultWidth = 22.dp,
            defaultHeight = 16.dp,
            viewportWidth = 22f,
            viewportHeight = 16f
        ).apply {
            group {
                path(
                    fill = SolidColor(Color(0xFFFFFFFF)),
                    fillAlpha = 1.0f,
                    stroke = null,
                    strokeAlpha = 1.0f,
                    strokeLineWidth = 1.0f,
                    strokeLineCap = StrokeCap.Butt,
                    strokeLineJoin = StrokeJoin.Miter,
                    strokeLineMiter = 1.0f,
                    pathFillType = PathFillType.NonZero
                ) {
                    moveTo(2f, 0f)
                    horizontalLineTo(20f)
                    arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 22f, 2f)
                    verticalLineTo(14f)
                    arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 20f, 16f)
                    horizontalLineTo(2f)
                    arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0f, 14f)
                    verticalLineTo(2f)
                    arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 2f, 0f)
                    close()
                }
                path(
                    fill = SolidColor(Color(0xFF1A47B8)),
                    fillAlpha = 1.0f,
                    stroke = null,
                    strokeAlpha = 1.0f,
                    strokeLineWidth = 1.0f,
                    strokeLineCap = StrokeCap.Butt,
                    strokeLineJoin = StrokeJoin.Miter,
                    strokeLineMiter = 1.0f,
                    pathFillType = PathFillType.EvenOdd
                ) {
                    moveTo(0f, 0f)
                    horizontalLineTo(9.42857f)
                    verticalLineTo(7.46667f)
                    horizontalLineTo(0f)
                    verticalLineTo(0f)
                    close()
                }
                path(
                    fill = SolidColor(Color(0xFFF93939)),
                    fillAlpha = 1.0f,
                    stroke = null,
                    strokeAlpha = 1.0f,
                    strokeLineWidth = 1.0f,
                    strokeLineCap = StrokeCap.Butt,
                    strokeLineJoin = StrokeJoin.Miter,
                    strokeLineMiter = 1.0f,
                    pathFillType = PathFillType.EvenOdd
                ) {
                    moveTo(9.42857f, 0f)
                    verticalLineTo(1.06667f)
                    horizontalLineTo(20.5333f)
                    lineTo(22f, 0f)
                    horizontalLineTo(9.42857f)
                    close()
                    moveTo(9.42857f, 2.13333f)
                    verticalLineTo(3.2f)
                    horizontalLineTo(17.6f)
                    lineTo(19.0667f, 2.13333f)
                    horizontalLineTo(9.42857f)
                    close()
                    moveTo(9.42857f, 4.26667f)
                    verticalLineTo(5.33333f)
                    horizontalLineTo(14.6667f)
                    lineTo(16.1333f, 4.26667f)
                    horizontalLineTo(9.42857f)
                    close()
                    moveTo(9.42857f, 6.4f)
                    verticalLineTo(7.46667f)
                    horizontalLineTo(11.7333f)
                    lineTo(13.2f, 6.4f)
                    horizontalLineTo(9.42857f)
                    close()
                    moveTo(0f, 8.53333f)
                    verticalLineTo(9.6f)
                    horizontalLineTo(8.8f)
                    lineTo(10.2667f, 8.53333f)
                    horizontalLineTo(0f)
                    close()
                    moveTo(0f, 10.6667f)
                    verticalLineTo(11.7333f)
                    horizontalLineTo(5.86667f)
                    lineTo(7.33333f, 10.6667f)
                    horizontalLineTo(0f)
                    close()
                    moveTo(0f, 12.8f)
                    verticalLineTo(13.8667f)
                    horizontalLineTo(2.93333f)
                    lineTo(4.4f, 12.8f)
                    horizontalLineTo(0f)
                    close()
                    moveTo(0f, 14.9333f)
                    verticalLineTo(16f)
                    lineTo(1.46667f, 14.9333f)
                    horizontalLineTo(0f)
                    close()
                }
                path(
                    fill = SolidColor(Color(0xFFFFFFFF)),
                    fillAlpha = 1.0f,
                    stroke = null,
                    strokeAlpha = 1.0f,
                    strokeLineWidth = 1.0f,
                    strokeLineCap = StrokeCap.Butt,
                    strokeLineJoin = StrokeJoin.Miter,
                    strokeLineMiter = 1.0f,
                    pathFillType = PathFillType.EvenOdd
                ) {
                    moveTo(1.04761f, 1.06665f)
                    verticalLineTo(2.13332f)
                    horizontalLineTo(2.09523f)
                    verticalLineTo(1.06665f)
                    horizontalLineTo(1.04761f)
                    close()
                    moveTo(3.14285f, 1.06665f)
                    verticalLineTo(2.13332f)
                    horizontalLineTo(4.19046f)
                    verticalLineTo(1.06665f)
                    horizontalLineTo(3.14285f)
                    close()
                    moveTo(5.23808f, 1.06665f)
                    verticalLineTo(2.13332f)
                    horizontalLineTo(6.2857f)
                    verticalLineTo(1.06665f)
                    horizontalLineTo(5.23808f)
                    close()
                    moveTo(7.33332f, 1.06665f)
                    verticalLineTo(2.13332f)
                    horizontalLineTo(8.38094f)
                    verticalLineTo(1.06665f)
                    horizontalLineTo(7.33332f)
                    close()
                    moveTo(6.2857f, 2.13332f)
                    verticalLineTo(3.19998f)
                    horizontalLineTo(7.33332f)
                    verticalLineTo(2.13332f)
                    horizontalLineTo(6.2857f)
                    close()
                    moveTo(4.19046f, 2.13332f)
                    verticalLineTo(3.19998f)
                    horizontalLineTo(5.23808f)
                    verticalLineTo(2.13332f)
                    horizontalLineTo(4.19046f)
                    close()
                    moveTo(2.09523f, 2.13332f)
                    verticalLineTo(3.19998f)
                    horizontalLineTo(3.14285f)
                    verticalLineTo(2.13332f)
                    horizontalLineTo(2.09523f)
                    close()
                    moveTo(1.04761f, 3.19998f)
                    verticalLineTo(4.26665f)
                    horizontalLineTo(2.09523f)
                    verticalLineTo(3.19998f)
                    horizontalLineTo(1.04761f)
                    close()
                    moveTo(3.14285f, 3.19998f)
                    verticalLineTo(4.26665f)
                    horizontalLineTo(4.19046f)
                    verticalLineTo(3.19998f)
                    horizontalLineTo(3.14285f)
                    close()
                    moveTo(5.23808f, 3.19998f)
                    verticalLineTo(4.26665f)
                    horizontalLineTo(6.2857f)
                    verticalLineTo(3.19998f)
                    horizontalLineTo(5.23808f)
                    close()
                    moveTo(7.33332f, 3.19998f)
                    verticalLineTo(4.26665f)
                    horizontalLineTo(8.38094f)
                    verticalLineTo(3.19998f)
                    horizontalLineTo(7.33332f)
                    close()
                    moveTo(1.04761f, 5.33332f)
                    verticalLineTo(6.39998f)
                    horizontalLineTo(2.09523f)
                    verticalLineTo(5.33332f)
                    horizontalLineTo(1.04761f)
                    close()
                    moveTo(3.14285f, 5.33332f)
                    verticalLineTo(6.39998f)
                    horizontalLineTo(4.19046f)
                    verticalLineTo(5.33332f)
                    horizontalLineTo(3.14285f)
                    close()
                    moveTo(5.23808f, 5.33332f)
                    verticalLineTo(6.39998f)
                    horizontalLineTo(6.2857f)
                    verticalLineTo(5.33332f)
                    horizontalLineTo(5.23808f)
                    close()
                    moveTo(7.33332f, 5.33332f)
                    verticalLineTo(6.39998f)
                    horizontalLineTo(8.38094f)
                    verticalLineTo(5.33332f)
                    horizontalLineTo(7.33332f)
                    close()
                    moveTo(6.2857f, 4.26665f)
                    verticalLineTo(5.33332f)
                    horizontalLineTo(7.33332f)
                    verticalLineTo(4.26665f)
                    horizontalLineTo(6.2857f)
                    close()
                    moveTo(4.19046f, 4.26665f)
                    verticalLineTo(5.33332f)
                    horizontalLineTo(5.23808f)
                    verticalLineTo(4.26665f)
                    horizontalLineTo(4.19046f)
                    close()
                    moveTo(2.09523f, 4.26665f)
                    verticalLineTo(5.33332f)
                    horizontalLineTo(3.14285f)
                    verticalLineTo(4.26665f)
                    horizontalLineTo(2.09523f)
                    close()
                }
                path(
                    fill = SolidColor(Color(0xFF1A47B8)),
                    fillAlpha = 1.0f,
                    stroke = null,
                    strokeAlpha = 1.0f,
                    strokeLineWidth = 1.0f,
                    strokeLineCap = StrokeCap.Butt,
                    strokeLineJoin = StrokeJoin.Miter,
                    strokeLineMiter = 1.0f,
                    pathFillType = PathFillType.EvenOdd
                ) {
                    moveTo(0f, 16f)
                    lineTo(22f, 0f)
                    verticalLineTo(16f)
                    horizontalLineTo(0f)
                    close()
                }
                path(
                    fill = SolidColor(Color(0xFFFFFFFF)),
                    fillAlpha = 1.0f,
                    stroke = null,
                    strokeAlpha = 1.0f,
                    strokeLineWidth = 1.0f,
                    strokeLineCap = StrokeCap.Butt,
                    strokeLineJoin = StrokeJoin.Miter,
                    strokeLineMiter = 1.0f,
                    pathFillType = PathFillType.NonZero
                ) {
                    moveTo(22f, 16f)
                    lineTo(19.6469f, 16f)
                    lineTo(9.4837f, 9.10277f)
                    lineTo(12.5133f, 6.89941f)
                    lineTo(22f, 13.3334f)
                    verticalLineTo(16f)
                    close()
                }
                path(
                    fill = SolidColor(Color(0xFFF93939)),
                    fillAlpha = 1.0f,
                    stroke = null,
                    strokeAlpha = 1.0f,
                    strokeLineWidth = 1.0f,
                    strokeLineCap = StrokeCap.Butt,
                    strokeLineJoin = StrokeJoin.Miter,
                    strokeLineMiter = 1.0f,
                    pathFillType = PathFillType.NonZero
                ) {
                    moveTo(22f, 16f)
                    verticalLineTo(14.4378f)
                    lineTo(11.7435f, 7.45923f)
                    lineTo(10.2662f, 8.53364f)
                    lineTo(21.2377f, 16f)
                    horizontalLineTo(22f)
                    close()
                }
                path(
                    fill = SolidColor(Color(0xFFFFFFFF)),
                    fillAlpha = 1.0f,
                    stroke = null,
                    strokeAlpha = 1.0f,
                    strokeLineWidth = 1.0f,
                    strokeLineCap = StrokeCap.Butt,
                    strokeLineJoin = StrokeJoin.Miter,
                    strokeLineMiter = 1.0f,
                    pathFillType = PathFillType.EvenOdd
                ) {
                    moveTo(22.0001f, 0f)
                    lineTo(22.0001f, 2.66667f)
                    curveTo(22.0001f, 2.6667f, 8.3913f, 11.5499f, 2.0953f, 16f)
                    horizontalLineTo(0.0000610352f)
                    lineTo(22.0001f, 0f)
                    close()
                }
                path(
                    fill = SolidColor(Color(0xFFF93939)),
                    fillAlpha = 1.0f,
                    stroke = null,
                    strokeAlpha = 1.0f,
                    strokeLineWidth = 1.0f,
                    strokeLineCap = StrokeCap.Butt,
                    strokeLineJoin = StrokeJoin.Miter,
                    strokeLineMiter = 1.0f,
                    pathFillType = PathFillType.NonZero
                ) {
                    moveTo(0.780579f, 16f)
                    lineTo(22f, 1.55895f)
                    lineTo(22f, 0f)
                    lineTo(0f, 16f)
                    horizontalLineTo(0.780579f)
                    close()
                }
                path(
                    fill = SolidColor(Color(0xFFFFFFFF)),
                    fillAlpha = 1.0f,
                    stroke = null,
                    strokeAlpha = 1.0f,
                    strokeLineWidth = 1.0f,
                    strokeLineCap = StrokeCap.Butt,
                    strokeLineJoin = StrokeJoin.Miter,
                    strokeLineMiter = 1.0f,
                    pathFillType = PathFillType.NonZero
                ) {
                    moveTo(22f, 4.9353f)
                    horizontalLineTo(15.214f)
                    lineTo(6.79047f, 11.0615f)
                    horizontalLineTo(8.00074f)
                    verticalLineTo(16f)
                    horizontalLineTo(14.0175f)
                    verticalLineTo(11.0615f)
                    horizontalLineTo(22f)
                    verticalLineTo(4.9353f)
                    close()
                }
                path(
                    fill = SolidColor(Color(0xFFF93939)),
                    fillAlpha = 1.0f,
                    stroke = null,
                    strokeAlpha = 1.0f,
                    strokeLineWidth = 1.0f,
                    strokeLineCap = StrokeCap.Butt,
                    strokeLineJoin = StrokeJoin.Miter,
                    strokeLineMiter = 1.0f,
                    pathFillType = PathFillType.NonZero
                ) {
                    moveTo(22f, 6.15381f)
                    horizontalLineTo(13.5385f)
                    lineTo(8.46155f, 9.84612f)
                    horizontalLineTo(9.26316f)
                    verticalLineTo(16f)
                    horizontalLineTo(12.7368f)
                    verticalLineTo(9.84612f)
                    horizontalLineTo(22f)
                    verticalLineTo(6.15381f)
                    close()
                }
            }
        }.build()
    }

    val Korea: ImageVector by lazy {
        ImageVector.Builder(
            name = "Korean",
            defaultWidth = 26.dp,
            defaultHeight = 17.dp,
            viewportWidth = 26f,
            viewportHeight = 17f
        ).apply {
            group {
                group {
                    path(
                        fill = SolidColor(Color(0xFFFFFFFF)),
                        fillAlpha = 1.0f,
                        stroke = null,
                        strokeAlpha = 1.0f,
                        strokeLineWidth = 1.0f,
                        strokeLineCap = StrokeCap.Butt,
                        strokeLineJoin = StrokeJoin.Miter,
                        strokeLineMiter = 1.0f,
                        pathFillType = PathFillType.NonZero
                    ) {
                        moveTo(25.5984f, 16.7467f)
                        horizontalLineTo(0.252268f)
                        curveTo(0.0023f, 16.7467f, -0.2004f, 16.544f, -0.2004f, 16.294f)
                        verticalLineTo(0.0000709891f)
                        curveTo(-0.2004f, -0.2499f, 0.0023f, -0.4526f, 0.2523f, -0.4526f)
                        horizontalLineTo(25.5985f)
                        curveTo(25.8484f, -0.4526f, 26.0511f, -0.2499f, 26.0511f, 0.0001f)
                        verticalLineTo(16.294f)
                        curveTo(26.051f, 16.544f, 25.8484f, 16.7467f, 25.5984f, 16.7467f)
                        close()
                    }
                    path(
                        fill = SolidColor(Color(0xFFFF4B55)),
                        fillAlpha = 1.0f,
                        stroke = null,
                        strokeAlpha = 1.0f,
                        strokeLineWidth = 1.0f,
                        strokeLineCap = StrokeCap.Butt,
                        strokeLineJoin = StrokeJoin.Miter,
                        strokeLineMiter = 1.0f,
                        pathFillType = PathFillType.NonZero
                    ) {
                        moveTo(15.4379f, 4.38022f)
                        curveTo(13.3582f, 2.9938f, 10.5491f, 3.5606f, 9.1636f, 5.6367f)
                        curveTo(8.4686f, 6.673f, 8.7511f, 8.081f, 9.7909f, 8.7743f)
                        curveTo(10.8299f, 9.4675f, 12.2335f, 9.186f, 12.9267f, 8.1461f)
                        curveTo(13.62f, 7.1063f, 15.0245f, 6.8282f, 16.0635f, 7.5178f)
                        curveTo(17.1042f, 8.2111f, 17.3841f, 9.6192f, 16.6899f, 10.6554f)
                        curveTo(18.0764f, 8.5758f, 17.5159f, 5.7667f, 15.4379f, 4.3802f)
                        close()
                    }
                    path(
                        fill = SolidColor(Color(0xFF41479B)),
                        fillAlpha = 1.0f,
                        stroke = null,
                        strokeAlpha = 1.0f,
                        strokeLineWidth = 1.0f,
                        strokeLineCap = StrokeCap.Butt,
                        strokeLineJoin = StrokeJoin.Miter,
                        strokeLineMiter = 1.0f,
                        pathFillType = PathFillType.NonZero
                    ) {
                        moveTo(9.16363f, 5.63673f)
                        curveTo(8.4686f, 6.673f, 8.7511f, 8.0811f, 9.7909f, 8.7743f)
                        curveTo(10.83f, 9.4676f, 12.2336f, 9.1861f, 12.9268f, 8.1462f)
                        curveTo(13.6201f, 7.1063f, 15.0246f, 6.8282f, 16.0635f, 7.5179f)
                        curveTo(17.1043f, 8.2111f, 17.3842f, 9.6192f, 16.6899f, 10.6555f)
                        curveTo(15.3053f, 12.7388f, 12.4971f, 13.2985f, 10.4174f, 11.912f)
                        curveTo(8.3395f, 10.5256f, 7.7772f, 7.7165f, 9.1636f, 5.6367f)
                        close()
                    }
                    path(
                        fill = SolidColor(Color(0xFF464655)),
                        fillAlpha = 1.0f,
                        stroke = null,
                        strokeAlpha = 1.0f,
                        strokeLineWidth = 1.0f,
                        strokeLineCap = StrokeCap.Butt,
                        strokeLineJoin = StrokeJoin.Miter,
                        strokeLineMiter = 1.0f,
                        pathFillType = PathFillType.NonZero
                    ) {
                        moveTo(17.7414f, 2.67077f)
                        lineTo(18.5636f, 3.90653f)
                        curveTo(18.6326f, 4.0104f, 18.6047f, 4.1505f, 18.5011f, 4.2199f)
                        lineTo(18.2926f, 4.35952f)
                        curveTo(18.1886f, 4.4291f, 18.0478f, 4.4012f, 17.9784f, 4.297f)
                        lineTo(17.1552f, 3.06152f)
                        curveTo(17.0859f, 2.9576f, 17.114f, 2.8171f, 17.2179f, 2.7478f)
                        lineTo(17.4274f, 2.60791f)
                        curveTo(17.5315f, 2.5384f, 17.6721f, 2.5666f, 17.7414f, 2.6708f)
                        close()
                    }
                    path(
                        fill = SolidColor(Color(0xFF464655)),
                        fillAlpha = 1.0f,
                        stroke = null,
                        strokeAlpha = 1.0f,
                        strokeLineWidth = 1.0f,
                        strokeLineCap = StrokeCap.Butt,
                        strokeLineJoin = StrokeJoin.Miter,
                        strokeLineMiter = 1.0f,
                        pathFillType = PathFillType.NonZero
                    ) {
                        moveTo(19.0102f, 4.57609f)
                        lineTo(19.835f, 5.81108f)
                        curveTo(19.9046f, 5.9154f, 19.8762f, 6.0565f, 19.7716f, 6.1256f)
                        lineTo(19.5622f, 6.26397f)
                        curveTo(19.4583f, 6.3327f, 19.3184f, 6.3044f, 19.2493f, 6.2009f)
                        lineTo(18.4249f, 4.96719f)
                        curveTo(18.3555f, 4.8633f, 18.3834f, 4.7228f, 18.4873f, 4.6533f)
                        lineTo(18.6962f, 4.51364f)
                        curveTo(18.8001f, 4.4442f, 18.9407f, 4.4721f, 19.0102f, 4.5761f)
                        close()
                    }
                    path(
                        fill = SolidColor(Color(0xFF464655)),
                        fillAlpha = 1.0f,
                        stroke = null,
                        strokeAlpha = 1.0f,
                        strokeLineWidth = 1.0f,
                        strokeLineCap = StrokeCap.Butt,
                        strokeLineJoin = StrokeJoin.Miter,
                        strokeLineMiter = 1.0f,
                        pathFillType = PathFillType.NonZero
                    ) {
                        moveTo(18.6218f, 2.08328f)
                        lineTo(20.7152f, 5.22488f)
                        curveTo(20.7845f, 5.3289f, 20.7564f, 5.4694f, 20.6524f, 5.5387f)
                        lineTo(20.4421f, 5.67884f)
                        curveTo(20.3381f, 5.7482f, 20.1976f, 5.7201f, 20.1282f, 5.6161f)
                        lineTo(18.0341f, 2.47469f)
                        curveTo(17.9648f, 2.3707f, 17.993f, 2.2301f, 18.097f, 2.1608f)
                        lineTo(18.308f, 2.02036f)
                        curveTo(18.4121f, 1.9512f, 18.5525f, 1.9793f, 18.6218f, 2.0833f)
                        close()
                    }
                    path(
                        fill = SolidColor(Color(0xFF464655)),
                        fillAlpha = 1.0f,
                        stroke = null,
                        strokeAlpha = 1.0f,
                        strokeLineWidth = 1.0f,
                        strokeLineCap = StrokeCap.Butt,
                        strokeLineJoin = StrokeJoin.Miter,
                        strokeLineMiter = 1.0f,
                        pathFillType = PathFillType.NonZero
                    ) {
                        moveTo(19.4998f, 1.49672f)
                        lineTo(20.3238f, 2.7349f)
                        curveTo(20.3932f, 2.8392f, 20.3647f, 2.98f, 20.2601f, 3.049f)
                        lineTo(20.0506f, 3.18748f)
                        curveTo(19.9467f, 3.2562f, 19.8067f, 3.2279f, 19.7376f, 3.1242f)
                        lineTo(18.9131f, 1.88767f)
                        curveTo(18.8437f, 1.7837f, 18.8718f, 1.6432f, 18.9758f, 1.5738f)
                        lineTo(19.186f, 1.43376f)
                        curveTo(19.2899f, 1.3644f, 19.4305f, 1.3925f, 19.4998f, 1.4967f)
                        close()
                    }
                    path(
                        fill = SolidColor(Color(0xFF464655)),
                        fillAlpha = 1.0f,
                        stroke = null,
                        strokeAlpha = 1.0f,
                        strokeLineWidth = 1.0f,
                        strokeLineCap = StrokeCap.Butt,
                        strokeLineJoin = StrokeJoin.Miter,
                        strokeLineMiter = 1.0f,
                        pathFillType = PathFillType.NonZero
                    ) {
                        moveTo(20.7692f, 3.40606f)
                        lineTo(21.5918f, 4.64167f)
                        curveTo(21.6611f, 4.7455f, 21.633f, 4.8858f, 21.5293f, 4.9552f)
                        lineTo(21.3203f, 5.09486f)
                        curveTo(21.2163f, 5.1644f, 21.0755f, 5.1363f, 21.0062f, 5.0322f)
                        lineTo(20.1835f, 3.79655f)
                        curveTo(20.1144f, 3.6927f, 20.1424f, 3.5524f, 20.2461f, 3.483f)
                        lineTo(20.455f, 3.34335f)
                        curveTo(20.559f, 3.2738f, 20.6998f, 3.3018f, 20.7692f, 3.4061f)
                        close()
                    }
                    path(
                        fill = SolidColor(Color(0xFF464655)),
                        fillAlpha = 1.0f,
                        stroke = null,
                        strokeAlpha = 1.0f,
                        strokeLineWidth = 1.0f,
                        strokeLineCap = StrokeCap.Butt,
                        strokeLineJoin = StrokeJoin.Miter,
                        strokeLineMiter = 1.0f,
                        pathFillType = PathFillType.NonZero
                    ) {
                        moveTo(6.60523f, 10.0946f)
                        lineTo(8.69837f, 13.2328f)
                        curveTo(8.7677f, 13.3367f, 8.7397f, 13.4772f, 8.6357f, 13.5466f)
                        lineTo(8.42611f, 13.6866f)
                        curveTo(8.3221f, 13.756f, 8.1816f, 13.7279f, 8.1122f, 13.6239f)
                        lineTo(6.01903f, 10.4857f)
                        curveTo(5.9497f, 10.3818f, 5.9777f, 10.2413f, 6.0817f, 10.1719f)
                        lineTo(6.29129f, 10.0319f)
                        curveTo(6.3953f, 9.9625f, 6.5359f, 9.9906f, 6.6052f, 10.0946f)
                        close()
                    }
                    path(
                        fill = SolidColor(Color(0xFF464655)),
                        fillAlpha = 1.0f,
                        stroke = null,
                        strokeAlpha = 1.0f,
                        strokeLineWidth = 1.0f,
                        strokeLineCap = StrokeCap.Butt,
                        strokeLineJoin = StrokeJoin.Miter,
                        strokeLineMiter = 1.0f,
                        pathFillType = PathFillType.NonZero
                    ) {
                        moveTo(5.72669f, 10.6816f)
                        lineTo(6.54848f, 11.9142f)
                        curveTo(6.6178f, 12.0181f, 6.5898f, 12.1583f, 6.4861f, 12.2278f)
                        lineTo(6.27761f, 12.3674f)
                        curveTo(6.1737f, 12.437f, 6.033f, 12.409f, 5.9635f, 12.305f)
                        lineTo(5.14075f, 11.0726f)
                        curveTo(5.0713f, 10.9687f, 5.0993f, 10.8282f, 5.2033f, 10.7588f)
                        lineTo(5.4128f, 10.6189f)
                        curveTo(5.5167f, 10.5495f, 5.6573f, 10.5776f, 5.7267f, 10.6816f)
                        close()
                    }
                    path(
                        fill = SolidColor(Color(0xFF464655)),
                        fillAlpha = 1.0f,
                        stroke = null,
                        strokeAlpha = 1.0f,
                        strokeLineWidth = 1.0f,
                        strokeLineCap = StrokeCap.Butt,
                        strokeLineJoin = StrokeJoin.Miter,
                        strokeLineMiter = 1.0f,
                        pathFillType = PathFillType.NonZero
                    ) {
                        moveTo(6.99652f, 12.5876f)
                        lineTo(7.81923f, 13.8234f)
                        curveTo(7.8884f, 13.9272f, 7.8604f, 14.0676f, 7.7566f, 14.1369f)
                        lineTo(7.54749f, 14.2767f)
                        curveTo(7.4435f, 14.3462f, 7.3028f, 14.3181f, 7.2334f, 14.2141f)
                        lineTo(6.41017f, 12.9785f)
                        curveTo(6.3409f, 12.8746f, 6.369f, 12.7342f, 6.4728f, 12.6648f)
                        lineTo(6.68242f, 12.5248f)
                        curveTo(6.7865f, 12.4553f, 6.9271f, 12.4835f, 6.9965f, 12.5876f)
                        close()
                    }
                    path(
                        fill = SolidColor(Color(0xFF464655)),
                        fillAlpha = 1.0f,
                        stroke = null,
                        strokeAlpha = 1.0f,
                        strokeLineWidth = 1.0f,
                        strokeLineCap = StrokeCap.Butt,
                        strokeLineJoin = StrokeJoin.Miter,
                        strokeLineMiter = 1.0f,
                        pathFillType = PathFillType.NonZero
                    ) {
                        moveTo(4.84783f, 11.2684f)
                        lineTo(6.93964f, 14.4068f)
                        curveTo(7.0089f, 14.5107f, 6.9809f, 14.6511f, 6.8771f, 14.7205f)
                        lineTo(6.6681f, 14.8602f)
                        curveTo(6.5641f, 14.9298f, 6.4235f, 14.9018f, 6.3541f, 14.7977f)
                        lineTo(4.26102f, 11.6597f)
                        curveTo(4.1917f, 11.5557f, 4.2198f, 11.4152f, 4.3238f, 11.3458f)
                        lineTo(4.53409f, 11.2057f)
                        curveTo(4.638f, 11.1363f, 4.7785f, 11.1644f, 4.8478f, 11.2684f)
                        close()
                    }
                    path(
                        fill = SolidColor(Color(0xFF464655)),
                        fillAlpha = 1.0f,
                        stroke = null,
                        strokeAlpha = 1.0f,
                        strokeLineWidth = 1.0f,
                        strokeLineCap = StrokeCap.Butt,
                        strokeLineJoin = StrokeJoin.Miter,
                        strokeLineMiter = 1.0f,
                        pathFillType = PathFillType.NonZero
                    ) {
                        moveTo(6.01951f, 5.8113f)
                        lineTo(8.11234f, 2.67051f)
                        curveTo(8.1817f, 2.5664f, 8.3223f, 2.5383f, 8.4263f, 2.6078f)
                        lineTo(8.63588f, 2.74768f)
                        curveTo(8.7398f, 2.8171f, 8.7678f, 2.9575f, 8.6985f, 3.0615f)
                        lineTo(6.60489f, 6.2005f)
                        curveTo(6.5357f, 6.3042f, 6.3956f, 6.3324f, 6.2917f, 6.2636f)
                        lineTo(6.08299f, 6.12544f)
                        curveTo(5.9785f, 6.0563f, 5.9501f, 5.9155f, 6.0195f, 5.8113f)
                        close()
                    }
                    path(
                        fill = SolidColor(Color(0xFF464655)),
                        fillAlpha = 1.0f,
                        stroke = null,
                        strokeAlpha = 1.0f,
                        strokeLineWidth = 1.0f,
                        strokeLineCap = StrokeCap.Butt,
                        strokeLineJoin = StrokeJoin.Miter,
                        strokeLineMiter = 1.0f,
                        pathFillType = PathFillType.NonZero
                    ) {
                        moveTo(5.14035f, 5.22508f)
                        lineTo(7.23354f, 2.08368f)
                        curveTo(7.3029f, 1.9796f, 7.4436f, 1.9515f, 7.5476f, 2.021f)
                        lineTo(7.75667f, 2.16079f)
                        curveTo(7.8605f, 2.2302f, 7.8884f, 2.3704f, 7.8192f, 2.4744f)
                        lineTo(5.7266f, 5.61593f)
                        curveTo(5.6572f, 5.72f, 5.5166f, 5.7482f, 5.4126f, 5.6787f)
                        lineTo(5.20301f, 5.53876f)
                        curveTo(5.0991f, 5.4694f, 5.0711f, 5.329f, 5.1403f, 5.2251f)
                        close()
                    }
                    path(
                        fill = SolidColor(Color(0xFF464655)),
                        fillAlpha = 1.0f,
                        stroke = null,
                        strokeAlpha = 1.0f,
                        strokeLineWidth = 1.0f,
                        strokeLineCap = StrokeCap.Butt,
                        strokeLineJoin = StrokeJoin.Miter,
                        strokeLineMiter = 1.0f,
                        pathFillType = PathFillType.NonZero
                    ) {
                        moveTo(4.26061f, 4.64165f)
                        lineTo(6.354f, 1.49712f)
                        curveTo(6.4234f, 1.393f, 6.5641f, 1.3648f, 6.6681f, 1.4344f)
                        lineTo(6.87719f, 1.57414f)
                        curveTo(6.981f, 1.6435f, 7.009f, 1.7837f, 6.9398f, 1.8876f)
                        lineTo(4.84773f, 5.0326f)
                        curveTo(4.7785f, 5.1367f, 4.6378f, 5.1649f, 4.5338f, 5.0956f)
                        lineTo(4.32347f, 4.95538f)
                        curveTo(4.2195f, 4.8861f, 4.1914f, 4.7456f, 4.2606f, 4.6417f)
                        close()
                    }
                    path(
                        fill = SolidColor(Color(0xFF464655)),
                        fillAlpha = 1.0f,
                        stroke = null,
                        strokeAlpha = 1.0f,
                        strokeLineWidth = 1.0f,
                        strokeLineCap = StrokeCap.Butt,
                        strokeLineJoin = StrokeJoin.Miter,
                        strokeLineMiter = 1.0f,
                        pathFillType = PathFillType.NonZero
                    ) {
                        moveTo(17.1556f, 13.2328f)
                        lineTo(17.9784f, 12.0005f)
                        curveTo(18.0478f, 11.8964f, 18.1885f, 11.8685f, 18.2925f, 11.9381f)
                        lineTo(18.5009f, 12.0777f)
                        curveTo(18.6047f, 12.1471f, 18.6326f, 12.2874f, 18.5633f, 12.3913f)
                        lineTo(17.7415f, 13.6239f)
                        curveTo(17.6722f, 13.7279f, 17.5316f, 13.756f, 17.4276f, 13.6866f)
                        lineTo(17.2181f, 13.5467f)
                        curveTo(17.1142f, 13.4773f, 17.0862f, 13.3368f, 17.1556f, 13.2328f)
                        close()
                    }
                    path(
                        fill = SolidColor(Color(0xFF464655)),
                        fillAlpha = 1.0f,
                        stroke = null,
                        strokeAlpha = 1.0f,
                        strokeLineWidth = 1.0f,
                        strokeLineCap = StrokeCap.Butt,
                        strokeLineJoin = StrokeJoin.Miter,
                        strokeLineMiter = 1.0f,
                        pathFillType = PathFillType.NonZero
                    ) {
                        moveTo(18.4247f, 11.3333f)
                        lineTo(19.2488f, 10.0948f)
                        curveTo(19.3181f, 9.9908f, 19.4587f, 9.9626f, 19.5627f, 10.0319f)
                        lineTo(19.7727f, 10.1719f)
                        curveTo(19.8767f, 10.2413f, 19.9048f, 10.3819f, 19.8354f, 10.4859f)
                        lineTo(19.0096f, 11.7224f)
                        curveTo(18.9403f, 11.8261f, 18.8003f, 11.8542f, 18.6964f, 11.7853f)
                        lineTo(18.4881f, 11.6473f)
                        curveTo(18.3837f, 11.5781f, 18.3553f, 11.4374f, 18.4247f, 11.3333f)
                        close()
                    }
                    path(
                        fill = SolidColor(Color(0xFF464655)),
                        fillAlpha = 1.0f,
                        stroke = null,
                        strokeAlpha = 1.0f,
                        strokeLineWidth = 1.0f,
                        strokeLineCap = StrokeCap.Butt,
                        strokeLineJoin = StrokeJoin.Miter,
                        strokeLineMiter = 1.0f,
                        pathFillType = PathFillType.NonZero
                    ) {
                        moveTo(18.0341f, 13.8229f)
                        lineTo(18.8574f, 12.5873f)
                        curveTo(18.9267f, 12.4833f, 19.0672f, 12.4551f, 19.1713f, 12.5245f)
                        lineTo(19.3817f, 12.6648f)
                        curveTo(19.4856f, 12.7342f, 19.5137f, 12.8745f, 19.4445f, 12.9786f)
                        lineTo(18.6217f, 14.2142f)
                        curveTo(18.5525f, 14.3183f, 18.412f, 14.3465f, 18.3079f, 14.2772f)
                        lineTo(18.097f, 14.1368f)
                        curveTo(17.993f, 14.0675f, 17.9648f, 13.9269f, 18.0341f, 13.8229f)
                        close()
                    }
                    path(
                        fill = SolidColor(Color(0xFF464655)),
                        fillAlpha = 1.0f,
                        stroke = null,
                        strokeAlpha = 1.0f,
                        strokeLineWidth = 1.0f,
                        strokeLineCap = StrokeCap.Butt,
                        strokeLineJoin = StrokeJoin.Miter,
                        strokeLineMiter = 1.0f,
                        pathFillType = PathFillType.NonZero
                    ) {
                        moveTo(19.3047f, 11.9138f)
                        lineTo(20.1284f, 10.6811f)
                        curveTo(20.1978f, 10.5773f, 20.3382f, 10.5493f, 20.4421f, 10.6185f)
                        lineTo(20.6525f, 10.7587f)
                        curveTo(20.7565f, 10.8281f, 20.7846f, 10.9687f, 20.7151f, 11.0727f)
                        lineTo(19.8914f, 12.3054f)
                        curveTo(19.822f, 12.4092f, 19.6816f, 12.4372f, 19.5777f, 12.3679f)
                        lineTo(19.3673f, 12.2278f)
                        curveTo(19.2632f, 12.1585f, 19.2352f, 12.0178f, 19.3047f, 11.9138f)
                        close()
                    }
                    path(
                        fill = SolidColor(Color(0xFF464655)),
                        fillAlpha = 1.0f,
                        stroke = null,
                        strokeAlpha = 1.0f,
                        strokeLineWidth = 1.0f,
                        strokeLineCap = StrokeCap.Butt,
                        strokeLineJoin = StrokeJoin.Miter,
                        strokeLineMiter = 1.0f,
                        pathFillType = PathFillType.NonZero
                    ) {
                        moveTo(18.9132f, 14.4065f)
                        lineTo(19.7374f, 13.1731f)
                        curveTo(19.8066f, 13.0696f, 19.9465f, 13.0414f, 20.0503f, 13.11f)
                        lineTo(20.2598f, 13.2485f)
                        curveTo(20.3644f, 13.3176f, 20.3929f, 13.4585f, 20.3234f, 13.5628f)
                        lineTo(19.4998f, 14.7979f)
                        curveTo(19.4304f, 14.9019f, 19.2899f, 14.9299f, 19.1859f, 14.8606f)
                        lineTo(18.9758f, 14.7206f)
                        curveTo(18.8717f, 14.6511f, 18.8437f, 14.5105f, 18.9132f, 14.4065f)
                        close()
                    }
                    path(
                        fill = SolidColor(Color(0xFF464655)),
                        fillAlpha = 1.0f,
                        stroke = null,
                        strokeAlpha = 1.0f,
                        strokeLineWidth = 1.0f,
                        strokeLineCap = StrokeCap.Butt,
                        strokeLineJoin = StrokeJoin.Miter,
                        strokeLineMiter = 1.0f,
                        pathFillType = PathFillType.NonZero
                    ) {
                        moveTo(20.1838f, 12.5037f)
                        lineTo(21.006f, 11.2688f)
                        curveTo(21.0754f, 11.1647f, 21.2161f, 11.1365f, 21.3201f, 11.2061f)
                        lineTo(21.5289f, 11.3457f)
                        curveTo(21.6327f, 11.4151f, 21.6607f, 11.5555f, 21.5914f, 11.6594f)
                        lineTo(20.7685f, 12.8927f)
                        curveTo(20.6993f, 12.9964f, 20.5591f, 13.0246f, 20.4552f, 12.9557f)
                        lineTo(20.2471f, 12.8177f)
                        curveTo(20.1428f, 12.7486f, 20.1145f, 12.6079f, 20.1838f, 12.5037f)
                        close()
                    }
                }
            }
        }.build()
    }

    val Russia: ImageVector by lazy {
        ImageVector.Builder(
            name = "Russia",
            defaultWidth = 22.dp,
            defaultHeight = 16.dp,
            viewportWidth = 22f,
            viewportHeight = 16f
        ).apply {
            group {
                group {
                    path(
                        fill = SolidColor(Color(0xFF1A47B8)),
                        fillAlpha = 1.0f,
                        stroke = null,
                        strokeAlpha = 1.0f,
                        strokeLineWidth = 1.0f,
                        strokeLineCap = StrokeCap.Butt,
                        strokeLineJoin = StrokeJoin.Miter,
                        strokeLineMiter = 1.0f,
                        pathFillType = PathFillType.NonZero
                    ) {
                        moveTo(2f, 0f)
                        horizontalLineTo(20f)
                        arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 22f, 2f)
                        verticalLineTo(14f)
                        arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 20f, 16f)
                        horizontalLineTo(2f)
                        arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0f, 14f)
                        verticalLineTo(2f)
                        arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 2f, 0f)
                        close()
                    }
                    path(
                        fill = SolidColor(Color(0xFFF93939)),
                        fillAlpha = 1.0f,
                        stroke = null,
                        strokeAlpha = 1.0f,
                        strokeLineWidth = 1.0f,
                        strokeLineCap = StrokeCap.Butt,
                        strokeLineJoin = StrokeJoin.Miter,
                        strokeLineMiter = 1.0f,
                        pathFillType = PathFillType.EvenOdd
                    ) {
                        moveTo(0f, 10.6667f)
                        horizontalLineTo(22f)
                        verticalLineTo(16f)
                        horizontalLineTo(0f)
                        verticalLineTo(10.6667f)
                        close()
                    }
                    path(
                        fill = SolidColor(Color(0xFFFFFFFF)),
                        fillAlpha = 1.0f,
                        stroke = null,
                        strokeAlpha = 1.0f,
                        strokeLineWidth = 1.0f,
                        strokeLineCap = StrokeCap.Butt,
                        strokeLineJoin = StrokeJoin.Miter,
                        strokeLineMiter = 1.0f,
                        pathFillType = PathFillType.EvenOdd
                    ) {
                        moveTo(0f, 0f)
                        horizontalLineTo(22f)
                        verticalLineTo(5.33333f)
                        horizontalLineTo(0f)
                        verticalLineTo(0f)
                        close()
                    }
                }
            }
        }.build()
    }

    val Spain: ImageVector by lazy {
        ImageVector.Builder(
            name = "Spain",
            defaultWidth = 22.dp,
            defaultHeight = 16.dp,
            viewportWidth = 22f,
            viewportHeight = 16f
        ).apply {
            group {
                group {
                    path(
                        fill = SolidColor(Color(0xFFF93939)),
                        fillAlpha = 1.0f,
                        stroke = null,
                        strokeAlpha = 1.0f,
                        strokeLineWidth = 1.0f,
                        strokeLineCap = StrokeCap.Butt,
                        strokeLineJoin = StrokeJoin.Miter,
                        strokeLineMiter = 1.0f,
                        pathFillType = PathFillType.NonZero
                    ) {
                        moveTo(2f, 0f)
                        horizontalLineTo(20f)
                        arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 22f, 2f)
                        verticalLineTo(14f)
                        arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 20f, 16f)
                        horizontalLineTo(2f)
                        arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0f, 14f)
                        verticalLineTo(2f)
                        arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 2f, 0f)
                        close()
                    }
                    path(
                        fill = SolidColor(Color(0xFFF93939)),
                        fillAlpha = 1.0f,
                        stroke = null,
                        strokeAlpha = 1.0f,
                        strokeLineWidth = 1.0f,
                        strokeLineCap = StrokeCap.Butt,
                        strokeLineJoin = StrokeJoin.Miter,
                        strokeLineMiter = 1.0f,
                        pathFillType = PathFillType.NonZero
                    ) {
                        moveTo(19.9048f, 0f)
                        horizontalLineTo(2.09524f)
                        curveTo(0.9381f, 0f, 0f, 0.9551f, 0f, 2.1333f)
                        verticalLineTo(13.8667f)
                        curveTo(0f, 15.0449f, 0.9381f, 16f, 2.0952f, 16f)
                        horizontalLineTo(19.9048f)
                        curveTo(21.0619f, 16f, 22f, 15.0449f, 22f, 13.8667f)
                        verticalLineTo(2.13333f)
                        curveTo(22f, 0.9551f, 21.0619f, 0f, 19.9048f, 0f)
                        close()
                    }
                    path(
                        fill = SolidColor(Color(0xFFFFDA2C)),
                        fillAlpha = 1.0f,
                        stroke = null,
                        strokeAlpha = 1.0f,
                        strokeLineWidth = 1.0f,
                        strokeLineCap = StrokeCap.Butt,
                        strokeLineJoin = StrokeJoin.Miter,
                        strokeLineMiter = 1.0f,
                        pathFillType = PathFillType.EvenOdd
                    ) {
                        moveTo(0f, 4.26666f)
                        horizontalLineTo(22f)
                        verticalLineTo(11.7333f)
                        horizontalLineTo(0f)
                        verticalLineTo(4.26666f)
                        close()
                    }
                    path(
                        fill = SolidColor(Color(0xFFD4AF2C)),
                        fillAlpha = 1.0f,
                        stroke = null,
                        strokeAlpha = 1.0f,
                        strokeLineWidth = 1.0f,
                        strokeLineCap = StrokeCap.Butt,
                        strokeLineJoin = StrokeJoin.Miter,
                        strokeLineMiter = 1.0f,
                        pathFillType = PathFillType.EvenOdd
                    ) {
                        moveTo(9.42857f, 6.6368f)
                        verticalLineTo(9.3248f)
                        curveTo(9.4286f, 10.0715f, 8.7246f, 10.6688f, 7.8571f, 10.6688f)
                        horizontalLineTo(5.7619f)
                        curveTo(4.8966f, 10.6667f, 4.1905f, 10.0661f, 4.1905f, 9.3227f)
                        verticalLineTo(6.63467f)
                        curveTo(4.1905f, 6.0245f, 4.6598f, 5.5147f, 5.3051f, 5.3493f)
                        curveTo(5.5f, 4.7947f, 6.0992f, 5.2917f, 6.8095f, 5.2917f)
                        curveTo(7.524f, 5.2917f, 8.119f, 4.7979f, 8.3139f, 5.3504f)
                        curveTo(8.9571f, 5.52f, 9.4286f, 6.0309f, 9.4286f, 6.6368f)
                        close()
                    }
                    path(
                        fill = SolidColor(Color(0xFFCBCBCB)),
                        fillAlpha = 1.0f,
                        stroke = null,
                        strokeAlpha = 1.0f,
                        strokeLineWidth = 1.0f,
                        strokeLineCap = StrokeCap.Butt,
                        strokeLineJoin = StrokeJoin.Miter,
                        strokeLineMiter = 1.0f,
                        pathFillType = PathFillType.EvenOdd
                    ) {
                        moveTo(9.42857f, 7.46666f)
                        horizontalLineTo(10.4762f)
                        verticalLineTo(10.6667f)
                        horizontalLineTo(9.42857f)
                        verticalLineTo(7.46666f)
                        close()
                        moveTo(3.14285f, 7.46666f)
                        horizontalLineTo(4.19047f)
                        verticalLineTo(10.6667f)
                        horizontalLineTo(3.14285f)
                        verticalLineTo(7.46666f)
                        close()
                    }
                    path(
                        fill = SolidColor(Color(0xFF1A47B8)),
                        fillAlpha = 1.0f,
                        stroke = null,
                        strokeAlpha = 1.0f,
                        strokeLineWidth = 1.0f,
                        strokeLineCap = StrokeCap.Butt,
                        strokeLineJoin = StrokeJoin.Miter,
                        strokeLineMiter = 1.0f,
                        pathFillType = PathFillType.EvenOdd
                    ) {
                        moveTo(9.42857f, 9.60001f)
                        horizontalLineTo(10.4762f)
                        verticalLineTo(10.6667f)
                        horizontalLineTo(9.42857f)
                        verticalLineTo(9.60001f)
                        close()
                        moveTo(3.14285f, 9.60001f)
                        horizontalLineTo(4.19047f)
                        verticalLineTo(10.6667f)
                        horizontalLineTo(3.14285f)
                        verticalLineTo(9.60001f)
                        close()
                    }
                    path(
                        fill = SolidColor(Color(0xFFD4AF2C)),
                        fillAlpha = 1.0f,
                        stroke = null,
                        strokeAlpha = 1.0f,
                        strokeLineWidth = 1.0f,
                        strokeLineCap = StrokeCap.Butt,
                        strokeLineJoin = StrokeJoin.Miter,
                        strokeLineMiter = 1.0f,
                        pathFillType = PathFillType.EvenOdd
                    ) {
                        moveTo(9.42857f, 6.39999f)
                        horizontalLineTo(10.4762f)
                        verticalLineTo(7.46666f)
                        horizontalLineTo(9.42857f)
                        verticalLineTo(6.39999f)
                        close()
                        moveTo(3.14285f, 6.39999f)
                        horizontalLineTo(4.19047f)
                        verticalLineTo(7.46666f)
                        horizontalLineTo(3.14285f)
                        verticalLineTo(6.39999f)
                        close()
                    }
                    path(
                        fill = SolidColor(Color(0xFFAF010D)),
                        fillAlpha = 1.0f,
                        stroke = null,
                        strokeAlpha = 1.0f,
                        strokeLineWidth = 1.0f,
                        strokeLineCap = StrokeCap.Butt,
                        strokeLineJoin = StrokeJoin.Miter,
                        strokeLineMiter = 1.0f,
                        pathFillType = PathFillType.EvenOdd
                    ) {
                        moveTo(5.2381f, 6.39999f)
                        horizontalLineTo(6.28572f)
                        verticalLineTo(7.99999f)
                        horizontalLineTo(5.2381f)
                        verticalLineTo(6.39999f)
                        close()
                        moveTo(7.33334f, 8.53333f)
                        horizontalLineTo(8.38096f)
                        verticalLineTo(10.1333f)
                        horizontalLineTo(7.33334f)
                        verticalLineTo(8.53333f)
                        close()
                    }
                    path(
                        fill = SolidColor(Color(0xFFAE6A3E)),
                        fillAlpha = 1.0f,
                        stroke = null,
                        strokeAlpha = 1.0f,
                        strokeLineWidth = 1.0f,
                        strokeLineCap = StrokeCap.Butt,
                        strokeLineJoin = StrokeJoin.Miter,
                        strokeLineMiter = 1.0f,
                        pathFillType = PathFillType.EvenOdd
                    ) {
                        moveTo(7.33333f, 6.39999f)
                        horizontalLineTo(8.38095f)
                        verticalLineTo(7.99999f)
                        horizontalLineTo(7.33333f)
                        verticalLineTo(6.39999f)
                        close()
                    }
                    path(
                        fill = SolidColor(Color(0xFFFFDA2C)),
                        fillAlpha = 1.0f,
                        stroke = null,
                        strokeAlpha = 1.0f,
                        strokeLineWidth = 1.0f,
                        strokeLineCap = StrokeCap.Butt,
                        strokeLineJoin = StrokeJoin.Miter,
                        strokeLineMiter = 1.0f,
                        pathFillType = PathFillType.EvenOdd
                    ) {
                        moveTo(5.2381f, 8.53334f)
                        horizontalLineTo(6.28572f)
                        verticalLineTo(10.1333f)
                        horizontalLineTo(5.2381f)
                        verticalLineTo(8.53334f)
                        close()
                    }
                    path(
                        fill = SolidColor(Color(0xFFAF010D)),
                        fillAlpha = 1.0f,
                        stroke = null,
                        strokeAlpha = 1.0f,
                        strokeLineWidth = 1.0f,
                        strokeLineCap = StrokeCap.Butt,
                        strokeLineJoin = StrokeJoin.Miter,
                        strokeLineMiter = 1.0f,
                        pathFillType = PathFillType.EvenOdd
                    ) {
                        moveTo(6.28572f, 6.39999f)
                        lineTo(5.2381f, 5.33333f)
                        horizontalLineTo(8.38096f)
                        lineTo(7.33334f, 6.39999f)
                        horizontalLineTo(6.28572f)
                        close()
                    }
                    path(
                        fill = SolidColor(Color(0xFFD4AF2C)),
                        fillAlpha = 1.0f,
                        stroke = null,
                        strokeAlpha = 1.0f,
                        strokeLineWidth = 1.0f,
                        strokeLineCap = StrokeCap.Butt,
                        strokeLineJoin = StrokeJoin.Miter,
                        strokeLineMiter = 1.0f,
                        pathFillType = PathFillType.EvenOdd
                    ) {
                        moveTo(6.28572f, 4.26666f)
                        horizontalLineTo(7.33334f)
                        verticalLineTo(5.33333f)
                        horizontalLineTo(6.28572f)
                        verticalLineTo(4.26666f)
                        close()
                    }
                }
            }
        }.build()
    }

    val Portugal: ImageVector by lazy {
        ImageVector.Builder(
            name = "Portugal",
            defaultWidth = 22.dp,
            defaultHeight = 16.dp,
            viewportWidth = 22f,
            viewportHeight = 16f
        ).apply {
            group {
                path(
                    fill = SolidColor(Color(0xFFF93939)),
                    fillAlpha = 1.0f,
                    stroke = null,
                    strokeAlpha = 1.0f,
                    strokeLineWidth = 1.0f,
                    strokeLineCap = StrokeCap.Butt,
                    strokeLineJoin = StrokeJoin.Miter,
                    strokeLineMiter = 1.0f,
                    pathFillType = PathFillType.NonZero
                ) {
                    moveTo(2f, 0f)
                    horizontalLineTo(20f)
                    arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 22f, 2f)
                    verticalLineTo(14f)
                    arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 20f, 16f)
                    horizontalLineTo(2f)
                    arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0f, 14f)
                    verticalLineTo(2f)
                    arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 2f, 0f)
                    close()
                }
                path(
                    fill = SolidColor(Color(0xFF249F58)),
                    fillAlpha = 1.0f,
                    stroke = null,
                    strokeAlpha = 1.0f,
                    strokeLineWidth = 1.0f,
                    strokeLineCap = StrokeCap.Butt,
                    strokeLineJoin = StrokeJoin.Miter,
                    strokeLineMiter = 1.0f,
                    pathFillType = PathFillType.EvenOdd
                ) {
                    moveTo(0f, 0f)
                    horizontalLineTo(7f)
                    verticalLineTo(16f)
                    horizontalLineTo(0f)
                    verticalLineTo(0f)
                    close()
                }
                path(
                    fill = SolidColor(Color(0xFFFFDA2C)),
                    fillAlpha = 1.0f,
                    stroke = null,
                    strokeAlpha = 1.0f,
                    strokeLineWidth = 1.0f,
                    strokeLineCap = StrokeCap.Butt,
                    strokeLineJoin = StrokeJoin.Miter,
                    strokeLineMiter = 1.0f,
                    pathFillType = PathFillType.NonZero
                ) {
                    moveTo(7.33333f, 10.6667f)
                    curveTo(9.0691f, 10.6667f, 10.4762f, 9.234f, 10.4762f, 7.4667f)
                    curveTo(10.4762f, 5.6993f, 9.0691f, 4.2667f, 7.3333f, 4.2667f)
                    curveTo(5.5976f, 4.2667f, 4.1905f, 5.6993f, 4.1905f, 7.4667f)
                    curveTo(4.1905f, 9.234f, 5.5976f, 10.6667f, 7.3333f, 10.6667f)
                    close()
                }
                path(
                    fill = SolidColor(Color(0xFFF93939)),
                    fillAlpha = 1.0f,
                    stroke = null,
                    strokeAlpha = 1.0f,
                    strokeLineWidth = 1.0f,
                    strokeLineCap = StrokeCap.Butt,
                    strokeLineJoin = StrokeJoin.Miter,
                    strokeLineMiter = 1.0f,
                    pathFillType = PathFillType.EvenOdd
                ) {
                    moveTo(9.42857f, 8.53333f)
                    verticalLineTo(5.33333f)
                    horizontalLineTo(5.23809f)
                    verticalLineTo(8.53333f)
                    curveTo(5.2381f, 9.1221f, 6.1757f, 9.6f, 7.3333f, 9.6f)
                    curveTo(8.4909f, 9.6f, 9.4286f, 9.1221f, 9.4286f, 8.5333f)
                    close()
                }
                path(
                    fill = SolidColor(Color(0xFFFFFFFF)),
                    fillAlpha = 1.0f,
                    stroke = null,
                    strokeAlpha = 1.0f,
                    strokeLineWidth = 1.0f,
                    strokeLineCap = StrokeCap.Butt,
                    strokeLineJoin = StrokeJoin.Miter,
                    strokeLineMiter = 1.0f,
                    pathFillType = PathFillType.EvenOdd
                ) {
                    moveTo(6.28571f, 6.39999f)
                    horizontalLineTo(8.38095f)
                    verticalLineTo(8.53333f)
                    horizontalLineTo(6.28571f)
                    verticalLineTo(6.39999f)
                    close()
                }
                path(
                    fill = SolidColor(Color(0xFF1A47B8)),
                    fillAlpha = 1.0f,
                    stroke = null,
                    strokeAlpha = 1.0f,
                    strokeLineWidth = 1.0f,
                    strokeLineCap = StrokeCap.Butt,
                    strokeLineJoin = StrokeJoin.Miter,
                    strokeLineMiter = 1.0f,
                    pathFillType = PathFillType.EvenOdd
                ) {
                    moveTo(6.28571f, 6.39999f)
                    horizontalLineTo(7.33333f)
                    verticalLineTo(7.46666f)
                    horizontalLineTo(6.28571f)
                    verticalLineTo(6.39999f)
                    close()
                    moveTo(7.33333f, 7.46666f)
                    horizontalLineTo(8.38095f)
                    verticalLineTo(8.53333f)
                    horizontalLineTo(7.33333f)
                    verticalLineTo(7.46666f)
                    close()
                }
            }
        }.build()
    }

    val China: ImageVector by lazy {
        ImageVector.Builder(
            name = "China",
            defaultWidth = 22.dp,
            defaultHeight = 16.dp,
            viewportWidth = 22f,
            viewportHeight = 16f
        ).apply {
            group {
                group {
                    path(
                        fill = SolidColor(Color(0xFFF93939)),
                        fillAlpha = 1.0f,
                        stroke = null,
                        strokeAlpha = 1.0f,
                        strokeLineWidth = 1.0f,
                        strokeLineCap = StrokeCap.Butt,
                        strokeLineJoin = StrokeJoin.Miter,
                        strokeLineMiter = 1.0f,
                        pathFillType = PathFillType.NonZero
                    ) {
                        moveTo(2f, 0f)
                        horizontalLineTo(20f)
                        arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 22f, 2f)
                        verticalLineTo(14f)
                        arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 20f, 16f)
                        horizontalLineTo(2f)
                        arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0f, 14f)
                        verticalLineTo(2f)
                        arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 2f, 0f)
                        close()
                    }
                    path(
                        fill = SolidColor(Color(0xFFF93939)),
                        fillAlpha = 1.0f,
                        stroke = null,
                        strokeAlpha = 1.0f,
                        strokeLineWidth = 1.0f,
                        strokeLineCap = StrokeCap.Butt,
                        strokeLineJoin = StrokeJoin.Miter,
                        strokeLineMiter = 1.0f,
                        pathFillType = PathFillType.NonZero
                    ) {
                        moveTo(19.9048f, 0f)
                        horizontalLineTo(2.09524f)
                        curveTo(0.9381f, 0f, 0f, 0.9551f, 0f, 2.1333f)
                        verticalLineTo(13.8667f)
                        curveTo(0f, 15.0449f, 0.9381f, 16f, 2.0952f, 16f)
                        horizontalLineTo(19.9048f)
                        curveTo(21.0619f, 16f, 22f, 15.0449f, 22f, 13.8667f)
                        verticalLineTo(2.13333f)
                        curveTo(22f, 0.9551f, 21.0619f, 0f, 19.9048f, 0f)
                        close()
                    }
                    path(
                        fill = SolidColor(Color(0xFFFFDA2C)),
                        fillAlpha = 1.0f,
                        stroke = null,
                        strokeAlpha = 1.0f,
                        strokeLineWidth = 1.0f,
                        strokeLineCap = StrokeCap.Butt,
                        strokeLineJoin = StrokeJoin.Miter,
                        strokeLineMiter = 1.0f,
                        pathFillType = PathFillType.EvenOdd
                    ) {
                        moveTo(5.75993f, 7.712f)
                        lineTo(4.21993f, 8.53653f)
                        lineTo(4.51326f, 6.7904f)
                        lineTo(3.26869f, 5.55306f)
                        lineTo(4.98993f, 5.30026f)
                        lineTo(5.75993f, 3.71093f)
                        lineTo(6.52888f, 5.30026f)
                        lineTo(8.25012f, 5.55306f)
                        lineTo(7.00345f, 6.7904f)
                        lineTo(7.29888f, 8.53546f)
                        lineTo(5.75993f, 7.712f)
                        close()
                        moveTo(9.42869f, 3.2f)
                        horizontalLineTo(10.4763f)
                        verticalLineTo(4.26666f)
                        horizontalLineTo(9.42869f)
                        verticalLineTo(3.2f)
                        close()
                        moveTo(10.4763f, 5.33333f)
                        horizontalLineTo(11.5239f)
                        verticalLineTo(6.4f)
                        horizontalLineTo(10.4763f)
                        verticalLineTo(5.33333f)
                        close()
                        moveTo(10.4763f, 7.46666f)
                        horizontalLineTo(11.5239f)
                        verticalLineTo(8.53333f)
                        horizontalLineTo(10.4763f)
                        verticalLineTo(7.46666f)
                        close()
                        moveTo(9.42869f, 9.6f)
                        horizontalLineTo(10.4763f)
                        verticalLineTo(10.6667f)
                        horizontalLineTo(9.42869f)
                        verticalLineTo(9.6f)
                        close()
                    }
                }
            }
        }.build()
    }

    val Germany: ImageVector by lazy {
        ImageVector.Builder(
            name = "Germany",
            defaultWidth = 22.dp,
            defaultHeight = 16.dp,
            viewportWidth = 22f,
            viewportHeight = 16f
        ).apply {
            group {
                group {
                    path(
                        fill = SolidColor(Color(0xFFF93939)),
                        fillAlpha = 1.0f,
                        stroke = null,
                        strokeAlpha = 1.0f,
                        strokeLineWidth = 1.0f,
                        strokeLineCap = StrokeCap.Butt,
                        strokeLineJoin = StrokeJoin.Miter,
                        strokeLineMiter = 1.0f,
                        pathFillType = PathFillType.NonZero
                    ) {
                        moveTo(2f, 0f)
                        horizontalLineTo(20f)
                        arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 22f, 2f)
                        verticalLineTo(14f)
                        arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 20f, 16f)
                        horizontalLineTo(2f)
                        arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0f, 14f)
                        verticalLineTo(2f)
                        arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 2f, 0f)
                        close()
                    }
                    path(
                        fill = SolidColor(Color(0xFFFFDA2C)),
                        fillAlpha = 1.0f,
                        stroke = null,
                        strokeAlpha = 1.0f,
                        strokeLineWidth = 1.0f,
                        strokeLineCap = StrokeCap.Butt,
                        strokeLineJoin = StrokeJoin.Miter,
                        strokeLineMiter = 1.0f,
                        pathFillType = PathFillType.EvenOdd
                    ) {
                        moveTo(0f, 11f)
                        horizontalLineTo(23f)
                        verticalLineTo(16f)
                        horizontalLineTo(0f)
                        verticalLineTo(11f)
                        close()
                    }
                    path(
                        fill = SolidColor(Color(0xFF151515)),
                        fillAlpha = 1.0f,
                        stroke = null,
                        strokeAlpha = 1.0f,
                        strokeLineWidth = 1.0f,
                        strokeLineCap = StrokeCap.Butt,
                        strokeLineJoin = StrokeJoin.Miter,
                        strokeLineMiter = 1.0f,
                        pathFillType = PathFillType.EvenOdd
                    ) {
                        moveTo(0f, 0f)
                        horizontalLineTo(23f)
                        verticalLineTo(5f)
                        horizontalLineTo(0f)
                        verticalLineTo(0f)
                        close()
                    }
                }
            }
        }.build()
    }

    val Japan: ImageVector by lazy {
        ImageVector.Builder(
            name = "Japan",
            defaultWidth = 22.dp,
            defaultHeight = 16.dp,
            viewportWidth = 22f,
            viewportHeight = 16f
        ).apply {
            group {
                path(
                    fill = SolidColor(Color(0xFFFFFFFF)),
                    fillAlpha = 1.0f,
                    stroke = null,
                    strokeAlpha = 1.0f,
                    strokeLineWidth = 1.0f,
                    strokeLineCap = StrokeCap.Butt,
                    strokeLineJoin = StrokeJoin.Miter,
                    strokeLineMiter = 1.0f,
                    pathFillType = PathFillType.NonZero
                ) {
                    moveTo(2f, 0f)
                    horizontalLineTo(20f)
                    arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 22f, 2f)
                    verticalLineTo(14f)
                    arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 20f, 16f)
                    horizontalLineTo(2f)
                    arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0f, 14f)
                    verticalLineTo(2f)
                    arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 2f, 0f)
                    close()
                }
                path(
                    fill = SolidColor(Color(0xFFF93939)),
                    fillAlpha = 1.0f,
                    stroke = null,
                    strokeAlpha = 1.0f,
                    strokeLineWidth = 1.0f,
                    strokeLineCap = StrokeCap.Butt,
                    strokeLineJoin = StrokeJoin.Miter,
                    strokeLineMiter = 1.0f,
                    pathFillType = PathFillType.NonZero
                ) {
                    moveTo(11f, 11.7333f)
                    curveTo(13.025f, 11.7333f, 14.6667f, 10.0619f, 14.6667f, 8f)
                    curveTo(14.6667f, 5.9381f, 13.025f, 4.2667f, 11f, 4.2667f)
                    curveTo(8.9749f, 4.2667f, 7.3333f, 5.9381f, 7.3333f, 8f)
                    curveTo(7.3333f, 10.0619f, 8.9749f, 11.7333f, 11f, 11.7333f)
                    close()
                }
            }
        }.build()
    }

    val France: ImageVector by lazy {
        ImageVector.Builder(
            name = "France",
            defaultWidth = 22.dp,
            defaultHeight = 16.dp,
            viewportWidth = 22f,
            viewportHeight = 16f
        ).apply {
            group {
                group {
                    path(
                        fill = SolidColor(Color(0xFFFFFFFF)),
                        fillAlpha = 1.0f,
                        stroke = null,
                        strokeAlpha = 1.0f,
                        strokeLineWidth = 1.0f,
                        strokeLineCap = StrokeCap.Butt,
                        strokeLineJoin = StrokeJoin.Miter,
                        strokeLineMiter = 1.0f,
                        pathFillType = PathFillType.NonZero
                    ) {
                        moveTo(2f, 0f)
                        horizontalLineTo(20f)
                        arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 22f, 2f)
                        verticalLineTo(14f)
                        arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 20f, 16f)
                        horizontalLineTo(2f)
                        arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0f, 14f)
                        verticalLineTo(2f)
                        arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 2f, 0f)
                        close()
                    }
                    path(
                        fill = SolidColor(Color(0xFF1A47B8)),
                        fillAlpha = 1.0f,
                        stroke = null,
                        strokeAlpha = 1.0f,
                        strokeLineWidth = 1.0f,
                        strokeLineCap = StrokeCap.Butt,
                        strokeLineJoin = StrokeJoin.Miter,
                        strokeLineMiter = 1.0f,
                        pathFillType = PathFillType.EvenOdd
                    ) {
                        moveTo(0f, 0f)
                        horizontalLineTo(7f)
                        verticalLineTo(16f)
                        horizontalLineTo(0f)
                        verticalLineTo(0f)
                        close()
                    }
                    path(
                        fill = SolidColor(Color(0xFFF93939)),
                        fillAlpha = 1.0f,
                        stroke = null,
                        strokeAlpha = 1.0f,
                        strokeLineWidth = 1.0f,
                        strokeLineCap = StrokeCap.Butt,
                        strokeLineJoin = StrokeJoin.Miter,
                        strokeLineMiter = 1.0f,
                        pathFillType = PathFillType.EvenOdd
                    ) {
                        moveTo(15f, 0f)
                        horizontalLineTo(22f)
                        verticalLineTo(16f)
                        horizontalLineTo(15f)
                        verticalLineTo(0f)
                        close()
                    }
                }
            }
        }.build()
    }

    val Sweden: ImageVector by lazy {
        ImageVector.Builder(
            name = "Sweden",
            defaultWidth = 22.dp,
            defaultHeight = 16.dp,
            viewportWidth = 22f,
            viewportHeight = 16f
        ).apply {
            group {
                group {
                    path(
                        fill = SolidColor(Color(0xFF3A99FF)),
                        fillAlpha = 1.0f,
                        stroke = null,
                        strokeAlpha = 1.0f,
                        strokeLineWidth = 1.0f,
                        strokeLineCap = StrokeCap.Butt,
                        strokeLineJoin = StrokeJoin.Miter,
                        strokeLineMiter = 1.0f,
                        pathFillType = PathFillType.NonZero
                    ) {
                        moveTo(2f, 0f)
                        horizontalLineTo(20f)
                        arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 22f, 2f)
                        verticalLineTo(14f)
                        arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 20f, 16f)
                        horizontalLineTo(2f)
                        arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0f, 14f)
                        verticalLineTo(2f)
                        arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 2f, 0f)
                        close()
                    }
                    path(
                        fill = SolidColor(Color(0xFFFFDA2C)),
                        fillAlpha = 1.0f,
                        stroke = null,
                        strokeAlpha = 1.0f,
                        strokeLineWidth = 1.0f,
                        strokeLineCap = StrokeCap.Butt,
                        strokeLineJoin = StrokeJoin.Miter,
                        strokeLineMiter = 1.0f,
                        pathFillType = PathFillType.EvenOdd
                    ) {
                        moveTo(6.28571f, 9.6f)
                        horizontalLineTo(0f)
                        verticalLineTo(6.4f)
                        horizontalLineTo(6.28571f)
                        verticalLineTo(0f)
                        horizontalLineTo(9.42857f)
                        verticalLineTo(6.4f)
                        horizontalLineTo(22f)
                        verticalLineTo(9.6f)
                        horizontalLineTo(9.42857f)
                        verticalLineTo(16f)
                        horizontalLineTo(6.28571f)
                        verticalLineTo(9.6f)
                        close()
                    }
                }
            }
        }.build()
    }

    val Netherlands: ImageVector by lazy {
        ImageVector.Builder(
            name = "Netherlands",
            defaultWidth = 22.dp,
            defaultHeight = 16.dp,
            viewportWidth = 22f,
            viewportHeight = 16f
        ).apply {
            group {
                group {
                    path(
                        fill = SolidColor(Color(0xFFFFFFFF)),
                        fillAlpha = 1.0f,
                        stroke = null,
                        strokeAlpha = 1.0f,
                        strokeLineWidth = 1.0f,
                        strokeLineCap = StrokeCap.Butt,
                        strokeLineJoin = StrokeJoin.Miter,
                        strokeLineMiter = 1.0f,
                        pathFillType = PathFillType.NonZero
                    ) {
                        moveTo(2f, 0f)
                        horizontalLineTo(20f)
                        arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 22f, 2f)
                        verticalLineTo(14f)
                        arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 20f, 16f)
                        horizontalLineTo(2f)
                        arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0f, 14f)
                        verticalLineTo(2f)
                        arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 2f, 0f)
                        close()
                    }
                    path(
                        fill = SolidColor(Color(0xFF1E448D)),
                        fillAlpha = 1.0f,
                        stroke = null,
                        strokeAlpha = 1.0f,
                        strokeLineWidth = 1.0f,
                        strokeLineCap = StrokeCap.Butt,
                        strokeLineJoin = StrokeJoin.Miter,
                        strokeLineMiter = 1.0f,
                        pathFillType = PathFillType.EvenOdd
                    ) {
                        moveTo(0f, 11f)
                        horizontalLineTo(22f)
                        verticalLineTo(16f)
                        horizontalLineTo(0f)
                        verticalLineTo(11f)
                        close()
                    }
                    path(
                        fill = SolidColor(Color(0xFFB01923)),
                        fillAlpha = 1.0f,
                        stroke = null,
                        strokeAlpha = 1.0f,
                        strokeLineWidth = 1.0f,
                        strokeLineCap = StrokeCap.Butt,
                        strokeLineJoin = StrokeJoin.Miter,
                        strokeLineMiter = 1.0f,
                        pathFillType = PathFillType.EvenOdd
                    ) {
                        moveTo(0f, 0f)
                        horizontalLineTo(22f)
                        verticalLineTo(5f)
                        horizontalLineTo(0f)
                        verticalLineTo(0f)
                        close()
                    }
                }
            }
        }.build()
    }

    val Poland: ImageVector by lazy {
        ImageVector.Builder(
            name = "Poland",
            defaultWidth = 22.dp,
            defaultHeight = 16.dp,
            viewportWidth = 22f,
            viewportHeight = 16f
        ).apply {
            group {
                path(
                    fill = SolidColor(Color(0xFFD80027)),
                    fillAlpha = 1.0f,
                    stroke = null,
                    strokeAlpha = 1.0f,
                    strokeLineWidth = 1.0f,
                    strokeLineCap = StrokeCap.Butt,
                    strokeLineJoin = StrokeJoin.Miter,
                    strokeLineMiter = 1.0f,
                    pathFillType = PathFillType.NonZero
                ) {
                    moveTo(2f, 0f)
                    horizontalLineTo(20f)
                    arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 22f, 2f)
                    verticalLineTo(14f)
                    arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 20f, 16f)
                    horizontalLineTo(2f)
                    arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0f, 14f)
                    verticalLineTo(2f)
                    arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 2f, 0f)
                    close()
                }
                path(
                    fill = SolidColor(Color(0xFFFFFFFF)),
                    fillAlpha = 1.0f,
                    stroke = null,
                    strokeAlpha = 1.0f,
                    strokeLineWidth = 1.0f,
                    strokeLineCap = StrokeCap.Butt,
                    strokeLineJoin = StrokeJoin.Miter,
                    strokeLineMiter = 1.0f,
                    pathFillType = PathFillType.EvenOdd
                ) {
                    moveTo(0f, 0f)
                    horizontalLineTo(22f)
                    verticalLineTo(8f)
                    horizontalLineTo(0f)
                    verticalLineTo(0f)
                    close()
                }
            }
        }.build()
    }

    val Denmark: ImageVector by lazy {
        ImageVector.Builder(
            name = "Denmark",
            defaultWidth = 22.dp,
            defaultHeight = 16.dp,
            viewportWidth = 22f,
            viewportHeight = 16f
        ).apply {
            group {
                group {
                    path(
                        fill = SolidColor(Color(0xFFF93939)),
                        fillAlpha = 1.0f,
                        stroke = null,
                        strokeAlpha = 1.0f,
                        strokeLineWidth = 1.0f,
                        strokeLineCap = StrokeCap.Butt,
                        strokeLineJoin = StrokeJoin.Miter,
                        strokeLineMiter = 1.0f,
                        pathFillType = PathFillType.NonZero
                    ) {
                        moveTo(2f, 0f)
                        horizontalLineTo(20f)
                        arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 22f, 2f)
                        verticalLineTo(14f)
                        arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 20f, 16f)
                        horizontalLineTo(2f)
                        arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0f, 14f)
                        verticalLineTo(2f)
                        arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 2f, 0f)
                        close()
                    }
                    path(
                        fill = SolidColor(Color(0xFFFFFFFF)),
                        fillAlpha = 1.0f,
                        stroke = null,
                        strokeAlpha = 1.0f,
                        strokeLineWidth = 1.0f,
                        strokeLineCap = StrokeCap.Butt,
                        strokeLineJoin = StrokeJoin.Miter,
                        strokeLineMiter = 1.0f,
                        pathFillType = PathFillType.EvenOdd
                    ) {
                        moveTo(6.28571f, 9.6f)
                        horizontalLineTo(0f)
                        verticalLineTo(6.4f)
                        horizontalLineTo(6.28571f)
                        verticalLineTo(0f)
                        horizontalLineTo(9.42857f)
                        verticalLineTo(6.4f)
                        horizontalLineTo(22f)
                        verticalLineTo(9.6f)
                        horizontalLineTo(9.42857f)
                        verticalLineTo(16f)
                        horizontalLineTo(6.28571f)
                        verticalLineTo(9.6f)
                        close()
                    }
                }
            }
        }.build()
    }

    val Norway: ImageVector by lazy {
        ImageVector.Builder(
            name = "Norway",
            defaultWidth = 22.dp,
            defaultHeight = 16.dp,
            viewportWidth = 22f,
            viewportHeight = 16f
        ).apply {
            group {
                group {
                    path(
                        fill = SolidColor(Color(0xFFAF010D)),
                        fillAlpha = 1.0f,
                        stroke = null,
                        strokeAlpha = 1.0f,
                        strokeLineWidth = 1.0f,
                        strokeLineCap = StrokeCap.Butt,
                        strokeLineJoin = StrokeJoin.Miter,
                        strokeLineMiter = 1.0f,
                        pathFillType = PathFillType.NonZero
                    ) {
                        moveTo(2f, 0f)
                        horizontalLineTo(20f)
                        arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 22f, 2f)
                        verticalLineTo(14f)
                        arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 20f, 16f)
                        horizontalLineTo(2f)
                        arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0f, 14f)
                        verticalLineTo(2f)
                        arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 2f, 0f)
                        close()
                    }
                    path(
                        fill = SolidColor(Color(0xFFFFFFFF)),
                        fillAlpha = 1.0f,
                        stroke = null,
                        strokeAlpha = 1.0f,
                        strokeLineWidth = 1.0f,
                        strokeLineCap = StrokeCap.Butt,
                        strokeLineJoin = StrokeJoin.Miter,
                        strokeLineMiter = 1.0f,
                        pathFillType = PathFillType.EvenOdd
                    ) {
                        moveTo(6.28571f, 9.6f)
                        horizontalLineTo(0f)
                        verticalLineTo(6.4f)
                        horizontalLineTo(6.28571f)
                        verticalLineTo(0f)
                        horizontalLineTo(9.42857f)
                        verticalLineTo(6.4f)
                        horizontalLineTo(22f)
                        verticalLineTo(9.6f)
                        horizontalLineTo(9.42857f)
                        verticalLineTo(16f)
                        horizontalLineTo(6.28571f)
                        verticalLineTo(9.6f)
                        close()
                    }
                    path(
                        fill = SolidColor(Color(0xFF1A47B8)),
                        fillAlpha = 1.0f,
                        stroke = null,
                        strokeAlpha = 1.0f,
                        strokeLineWidth = 1.0f,
                        strokeLineCap = StrokeCap.Butt,
                        strokeLineJoin = StrokeJoin.Miter,
                        strokeLineMiter = 1.0f,
                        pathFillType = PathFillType.EvenOdd
                    ) {
                        moveTo(7.33333f, 8.53333f)
                        horizontalLineTo(0f)
                        verticalLineTo(7.46667f)
                        horizontalLineTo(7.33333f)
                        verticalLineTo(0f)
                        horizontalLineTo(8.38095f)
                        verticalLineTo(7.46667f)
                        horizontalLineTo(22f)
                        verticalLineTo(8.53333f)
                        horizontalLineTo(8.38095f)
                        verticalLineTo(16f)
                        horizontalLineTo(7.33333f)
                        verticalLineTo(8.53333f)
                        close()
                    }
                }
            }
        }.build()
    }

    val Italy: ImageVector by lazy {
        ImageVector.Builder(
            name = "Italy",
            defaultWidth = 22.dp,
            defaultHeight = 16.dp,
            viewportWidth = 22f,
            viewportHeight = 16f
        ).apply {
            group {
                group {
                    path(
                        fill = SolidColor(Color(0xFFFFFFFF)),
                        fillAlpha = 1.0f,
                        stroke = null,
                        strokeAlpha = 1.0f,
                        strokeLineWidth = 1.0f,
                        strokeLineCap = StrokeCap.Butt,
                        strokeLineJoin = StrokeJoin.Miter,
                        strokeLineMiter = 1.0f,
                        pathFillType = PathFillType.NonZero
                    ) {
                        moveTo(2f, 0f)
                        horizontalLineTo(20f)
                        arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 22f, 2f)
                        verticalLineTo(14f)
                        arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 20f, 16f)
                        horizontalLineTo(2f)
                        arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0f, 14f)
                        verticalLineTo(2f)
                        arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 2f, 0f)
                        close()
                    }
                    path(
                        fill = SolidColor(Color(0xFF249F58)),
                        fillAlpha = 1.0f,
                        stroke = null,
                        strokeAlpha = 1.0f,
                        strokeLineWidth = 1.0f,
                        strokeLineCap = StrokeCap.Butt,
                        strokeLineJoin = StrokeJoin.Miter,
                        strokeLineMiter = 1.0f,
                        pathFillType = PathFillType.EvenOdd
                    ) {
                        moveTo(0f, 0f)
                        horizontalLineTo(7f)
                        verticalLineTo(16f)
                        horizontalLineTo(0f)
                        verticalLineTo(0f)
                        close()
                    }
                    path(
                        fill = SolidColor(Color(0xFFF93939)),
                        fillAlpha = 1.0f,
                        stroke = null,
                        strokeAlpha = 1.0f,
                        strokeLineWidth = 1.0f,
                        strokeLineCap = StrokeCap.Butt,
                        strokeLineJoin = StrokeJoin.Miter,
                        strokeLineMiter = 1.0f,
                        pathFillType = PathFillType.EvenOdd
                    ) {
                        moveTo(15f, 0f)
                        horizontalLineTo(22f)
                        verticalLineTo(16f)
                        horizontalLineTo(15f)
                        verticalLineTo(0f)
                        close()
                    }
                }
            }
        }.build()
    }

    val Thailand: ImageVector by lazy {
        ImageVector.Builder(
            name = "Thailand",
            defaultWidth = 22.dp,
            defaultHeight = 16.dp,
            viewportWidth = 22f,
            viewportHeight = 16f
        ).apply {
            group {
                group {
                    path(
                        fill = SolidColor(Color(0xFFFFFFFF)),
                        fillAlpha = 1.0f,
                        stroke = null,
                        strokeAlpha = 1.0f,
                        strokeLineWidth = 1.0f,
                        strokeLineCap = StrokeCap.Butt,
                        strokeLineJoin = StrokeJoin.Miter,
                        strokeLineMiter = 1.0f,
                        pathFillType = PathFillType.NonZero
                    ) {
                        moveTo(2f, 0f)
                        horizontalLineTo(20f)
                        arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 22f, 2f)
                        verticalLineTo(14f)
                        arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 20f, 16f)
                        horizontalLineTo(2f)
                        arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0f, 14f)
                        verticalLineTo(2f)
                        arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 2f, 0f)
                        close()
                    }
                    path(
                        fill = SolidColor(Color(0xFFF93939)),
                        fillAlpha = 1.0f,
                        stroke = null,
                        strokeAlpha = 1.0f,
                        strokeLineWidth = 1.0f,
                        strokeLineCap = StrokeCap.Butt,
                        strokeLineJoin = StrokeJoin.Miter,
                        strokeLineMiter = 1.0f,
                        pathFillType = PathFillType.EvenOdd
                    ) {
                        moveTo(0f, 0f)
                        horizontalLineTo(22f)
                        verticalLineTo(3.2f)
                        horizontalLineTo(0f)
                        verticalLineTo(0f)
                        close()
                        moveTo(0f, 12.8f)
                        horizontalLineTo(22f)
                        verticalLineTo(16f)
                        horizontalLineTo(0f)
                        verticalLineTo(12.8f)
                        close()
                    }
                    path(
                        fill = SolidColor(Color(0xFF232C80)),
                        fillAlpha = 1.0f,
                        stroke = null,
                        strokeAlpha = 1.0f,
                        strokeLineWidth = 1.0f,
                        strokeLineCap = StrokeCap.Butt,
                        strokeLineJoin = StrokeJoin.Miter,
                        strokeLineMiter = 1.0f,
                        pathFillType = PathFillType.EvenOdd
                    ) {
                        moveTo(0f, 5.33333f)
                        horizontalLineTo(22f)
                        verticalLineTo(10.6667f)
                        horizontalLineTo(0f)
                        verticalLineTo(5.33333f)
                        close()
                    }
                }
            }
        }.build()
    }

    val Finland: ImageVector by lazy {
        ImageVector.Builder(
            name = "Finland",
            defaultWidth = 22.dp,
            defaultHeight = 16.dp,
            viewportWidth = 22f,
            viewportHeight = 16f
        ).apply {
            group {
                group {
                    path(
                        fill = SolidColor(Color(0xFFFFFFFF)),
                        fillAlpha = 1.0f,
                        stroke = null,
                        strokeAlpha = 1.0f,
                        strokeLineWidth = 1.0f,
                        strokeLineCap = StrokeCap.Butt,
                        strokeLineJoin = StrokeJoin.Miter,
                        strokeLineMiter = 1.0f,
                        pathFillType = PathFillType.NonZero
                    ) {
                        moveTo(2f, 0f)
                        horizontalLineTo(20f)
                        arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 22f, 2f)
                        verticalLineTo(14f)
                        arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 20f, 16f)
                        horizontalLineTo(2f)
                        arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0f, 14f)
                        verticalLineTo(2f)
                        arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 2f, 0f)
                        close()
                    }
                    path(
                        fill = SolidColor(Color(0xFF1A47B8)),
                        fillAlpha = 1.0f,
                        stroke = null,
                        strokeAlpha = 1.0f,
                        strokeLineWidth = 1.0f,
                        strokeLineCap = StrokeCap.Butt,
                        strokeLineJoin = StrokeJoin.Miter,
                        strokeLineMiter = 1.0f,
                        pathFillType = PathFillType.EvenOdd
                    ) {
                        moveTo(6.28571f, 9.6f)
                        horizontalLineTo(0f)
                        verticalLineTo(6.4f)
                        horizontalLineTo(6.28571f)
                        verticalLineTo(0f)
                        horizontalLineTo(9.42857f)
                        verticalLineTo(6.4f)
                        horizontalLineTo(22f)
                        verticalLineTo(9.6f)
                        horizontalLineTo(9.42857f)
                        verticalLineTo(16f)
                        horizontalLineTo(6.28571f)
                        verticalLineTo(9.6f)
                        close()
                    }
                }
            }
        }.build()
    }

    val Hungary: ImageVector by lazy {
        ImageVector.Builder(
            name = "Hungary",
            defaultWidth = 22.dp,
            defaultHeight = 16.dp,
            viewportWidth = 22f,
            viewportHeight = 16f
        ).apply {
            group {
                group {
                    path(
                        fill = SolidColor(Color(0xFFFFFFFF)),
                        fillAlpha = 1.0f,
                        stroke = null,
                        strokeAlpha = 1.0f,
                        strokeLineWidth = 1.0f,
                        strokeLineCap = StrokeCap.Butt,
                        strokeLineJoin = StrokeJoin.Miter,
                        strokeLineMiter = 1.0f,
                        pathFillType = PathFillType.NonZero
                    ) {
                        moveTo(2f, 0f)
                        horizontalLineTo(20f)
                        arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 22f, 2f)
                        verticalLineTo(14f)
                        arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 20f, 16f)
                        horizontalLineTo(2f)
                        arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0f, 14f)
                        verticalLineTo(2f)
                        arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 2f, 0f)
                        close()
                    }
                    path(
                        fill = SolidColor(Color(0xFF249F58)),
                        fillAlpha = 1.0f,
                        stroke = null,
                        strokeAlpha = 1.0f,
                        strokeLineWidth = 1.0f,
                        strokeLineCap = StrokeCap.Butt,
                        strokeLineJoin = StrokeJoin.Miter,
                        strokeLineMiter = 1.0f,
                        pathFillType = PathFillType.EvenOdd
                    ) {
                        moveTo(0f, 11f)
                        horizontalLineTo(22f)
                        verticalLineTo(16f)
                        horizontalLineTo(0f)
                        verticalLineTo(11f)
                        close()
                    }
                    path(
                        fill = SolidColor(Color(0xFFF93939)),
                        fillAlpha = 1.0f,
                        stroke = null,
                        strokeAlpha = 1.0f,
                        strokeLineWidth = 1.0f,
                        strokeLineCap = StrokeCap.Butt,
                        strokeLineJoin = StrokeJoin.Miter,
                        strokeLineMiter = 1.0f,
                        pathFillType = PathFillType.EvenOdd
                    ) {
                        moveTo(0f, 0f)
                        horizontalLineTo(22f)
                        verticalLineTo(5f)
                        horizontalLineTo(0f)
                        verticalLineTo(0f)
                        close()
                    }
                }
            }
        }.build()
    }

    val Czech: ImageVector by lazy {
        ImageVector.Builder(
            name = "Czech",
            defaultWidth = 22.dp,
            defaultHeight = 16.dp,
            viewportWidth = 22f,
            viewportHeight = 16f
        ).apply {
            group {
                group {
                    path(
                        fill = SolidColor(Color(0xFFF93939)),
                        fillAlpha = 1.0f,
                        stroke = null,
                        strokeAlpha = 1.0f,
                        strokeLineWidth = 1.0f,
                        strokeLineCap = StrokeCap.Butt,
                        strokeLineJoin = StrokeJoin.Miter,
                        strokeLineMiter = 1.0f,
                        pathFillType = PathFillType.NonZero
                    ) {
                        moveTo(2f, 0f)
                        horizontalLineTo(20f)
                        arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 22f, 2f)
                        verticalLineTo(14f)
                        arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 20f, 16f)
                        horizontalLineTo(2f)
                        arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0f, 14f)
                        verticalLineTo(2f)
                        arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 2f, 0f)
                        close()
                    }
                    path(
                        fill = SolidColor(Color(0xFFFFFFFF)),
                        fillAlpha = 1.0f,
                        stroke = null,
                        strokeAlpha = 1.0f,
                        strokeLineWidth = 1.0f,
                        strokeLineCap = StrokeCap.Butt,
                        strokeLineJoin = StrokeJoin.Miter,
                        strokeLineMiter = 1.0f,
                        pathFillType = PathFillType.EvenOdd
                    ) {
                        moveTo(0f, 0f)
                        horizontalLineTo(22f)
                        verticalLineTo(8f)
                        horizontalLineTo(0f)
                        verticalLineTo(0f)
                        close()
                    }
                    path(
                        fill = SolidColor(Color(0xFF1A47B8)),
                        fillAlpha = 1.0f,
                        stroke = null,
                        strokeAlpha = 1.0f,
                        strokeLineWidth = 1.0f,
                        strokeLineCap = StrokeCap.Butt,
                        strokeLineJoin = StrokeJoin.Miter,
                        strokeLineMiter = 1.0f,
                        pathFillType = PathFillType.EvenOdd
                    ) {
                        moveTo(0f, 0f)
                        lineTo(10.4762f, 8f)
                        lineTo(0f, 16f)
                        verticalLineTo(0f)
                        close()
                    }
                }
            }
        }.build()
    }

    val Turkey: ImageVector by lazy {
        ImageVector.Builder(
            name = "Turkey",
            defaultWidth = 22.dp,
            defaultHeight = 16.dp,
            viewportWidth = 22f,
            viewportHeight = 16f
        ).apply {
            group {
                path(
                    fill = SolidColor(Color(0xFFF93939)),
                    fillAlpha = 1.0f,
                    stroke = null,
                    strokeAlpha = 1.0f,
                    strokeLineWidth = 1.0f,
                    strokeLineCap = StrokeCap.Butt,
                    strokeLineJoin = StrokeJoin.Miter,
                    strokeLineMiter = 1.0f,
                    pathFillType = PathFillType.NonZero
                ) {
                    moveTo(2f, 0f)
                    horizontalLineTo(20f)
                    arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 22f, 2f)
                    verticalLineTo(14f)
                    arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 20f, 16f)
                    horizontalLineTo(2f)
                    arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0f, 14f)
                    verticalLineTo(2f)
                    arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 2f, 0f)
                    close()
                }
                path(
                    fill = SolidColor(Color(0xFFFFFFFF)),
                    fillAlpha = 1.0f,
                    stroke = null,
                    strokeAlpha = 1.0f,
                    strokeLineWidth = 1.0f,
                    strokeLineCap = StrokeCap.Butt,
                    strokeLineJoin = StrokeJoin.Miter,
                    strokeLineMiter = 1.0f,
                    pathFillType = PathFillType.EvenOdd
                ) {
                    moveTo(14.6688f, 9.2288f)
                    lineTo(13.4514f, 9.87946f)
                    lineTo(13.684f, 8.50026f)
                    lineTo(12.6992f, 7.52213f)
                    lineTo(14.0611f, 7.31946f)
                    lineTo(14.6688f, 6.06506f)
                    lineTo(15.2764f, 7.32053f)
                    lineTo(16.6383f, 7.5232f)
                    lineTo(15.6535f, 8.4992f)
                    lineTo(15.8861f, 9.8784f)
                }
                path(
                    fill = SolidColor(Color(0xFFFFFFFF)),
                    fillAlpha = 1.0f,
                    stroke = null,
                    strokeAlpha = 1.0f,
                    strokeLineWidth = 1.0f,
                    strokeLineCap = StrokeCap.Butt,
                    strokeLineJoin = StrokeJoin.Miter,
                    strokeLineMiter = 1.0f,
                    pathFillType = PathFillType.EvenOdd
                ) {
                    moveTo(9.95238f, 11.7333f)
                    curveTo(11.9774f, 11.7333f, 13.619f, 10.0619f, 13.619f, 8f)
                    curveTo(13.619f, 5.9381f, 11.9774f, 4.2667f, 9.9524f, 4.2667f)
                    curveTo(7.9273f, 4.2667f, 6.2857f, 5.9381f, 6.2857f, 8f)
                    curveTo(6.2857f, 10.0619f, 7.9273f, 11.7333f, 9.9524f, 11.7333f)
                    close()
                    moveTo(11f, 10.6667f)
                    curveTo(12.4457f, 10.6667f, 13.619f, 9.472f, 13.619f, 8f)
                    curveTo(13.619f, 6.528f, 12.4457f, 5.3333f, 11f, 5.3333f)
                    curveTo(9.5543f, 5.3333f, 8.381f, 6.528f, 8.381f, 8f)
                    curveTo(8.381f, 9.472f, 9.5543f, 10.6667f, 11f, 10.6667f)
                    close()
                }
            }
        }.build()
    }

    val Arabic: ImageVector by lazy {
        ImageVector.Builder(
            name = "Arabic",
            defaultWidth = 22.dp,
            defaultHeight = 16.dp,
            viewportWidth = 22f,
            viewportHeight = 16f
        ).apply {
            group {
                group {
                    path(
                        fill = SolidColor(Color(0xFFFFFFFF)),
                        fillAlpha = 1.0f,
                        stroke = null,
                        strokeAlpha = 1.0f,
                        strokeLineWidth = 1.0f,
                        strokeLineCap = StrokeCap.Butt,
                        strokeLineJoin = StrokeJoin.Miter,
                        strokeLineMiter = 1.0f,
                        pathFillType = PathFillType.NonZero
                    ) {
                        moveTo(2f, 0f)
                        horizontalLineTo(20f)
                        arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 22f, 2f)
                        verticalLineTo(14f)
                        arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 20f, 16f)
                        horizontalLineTo(2f)
                        arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0f, 14f)
                        verticalLineTo(2f)
                        arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 2f, 0f)
                        close()
                    }
                    path(
                        fill = SolidColor(Color(0xFF249F58)),
                        fillAlpha = 1.0f,
                        stroke = null,
                        strokeAlpha = 1.0f,
                        strokeLineWidth = 1.0f,
                        strokeLineCap = StrokeCap.Butt,
                        strokeLineJoin = StrokeJoin.Miter,
                        strokeLineMiter = 1.0f,
                        pathFillType = PathFillType.EvenOdd
                    ) {
                        moveTo(0f, 11f)
                        horizontalLineTo(22f)
                        verticalLineTo(16f)
                        horizontalLineTo(0f)
                        verticalLineTo(11f)
                        close()
                    }
                    path(
                        fill = SolidColor(Color(0xFF151515)),
                        fillAlpha = 1.0f,
                        stroke = null,
                        strokeAlpha = 1.0f,
                        strokeLineWidth = 1.0f,
                        strokeLineCap = StrokeCap.Butt,
                        strokeLineJoin = StrokeJoin.Miter,
                        strokeLineMiter = 1.0f,
                        pathFillType = PathFillType.EvenOdd
                    ) {
                        moveTo(0f, 0f)
                        horizontalLineTo(22f)
                        verticalLineTo(5f)
                        horizontalLineTo(0f)
                        verticalLineTo(0f)
                        close()
                    }
                    path(
                        fill = SolidColor(Color(0xFFF93939)),
                        fillAlpha = 1.0f,
                        stroke = null,
                        strokeAlpha = 1.0f,
                        strokeLineWidth = 1.0f,
                        strokeLineCap = StrokeCap.Butt,
                        strokeLineJoin = StrokeJoin.Miter,
                        strokeLineMiter = 1.0f,
                        pathFillType = PathFillType.EvenOdd
                    ) {
                        moveTo(0f, 0f)
                        verticalLineTo(16.0352f)
                        lineTo(10.4762f, 8f)
                        lineTo(0f, 0f)
                        close()
                    }
                }
            }
        }.build()
    }

    val Romania: ImageVector by lazy {
        ImageVector.Builder(
            name = "Romania",
            defaultWidth = 22.dp,
            defaultHeight = 16.dp,
            viewportWidth = 22f,
            viewportHeight = 16f
        ).apply {
            group {
                group {
                    path(
                        fill = SolidColor(Color(0xFFFFDA2C)),
                        fillAlpha = 1.0f,
                        stroke = null,
                        strokeAlpha = 1.0f,
                        strokeLineWidth = 1.0f,
                        strokeLineCap = StrokeCap.Butt,
                        strokeLineJoin = StrokeJoin.Miter,
                        strokeLineMiter = 1.0f,
                        pathFillType = PathFillType.NonZero
                    ) {
                        moveTo(2f, 0f)
                        horizontalLineTo(20f)
                        arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 22f, 2f)
                        verticalLineTo(14f)
                        arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 20f, 16f)
                        horizontalLineTo(2f)
                        arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0f, 14f)
                        verticalLineTo(2f)
                        arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 2f, 0f)
                        close()
                    }
                    path(
                        fill = SolidColor(Color(0xFF1A47B8)),
                        fillAlpha = 1.0f,
                        stroke = null,
                        strokeAlpha = 1.0f,
                        strokeLineWidth = 1.0f,
                        strokeLineCap = StrokeCap.Butt,
                        strokeLineJoin = StrokeJoin.Miter,
                        strokeLineMiter = 1.0f,
                        pathFillType = PathFillType.EvenOdd
                    ) {
                        moveTo(0f, 0f)
                        horizontalLineTo(7f)
                        verticalLineTo(16f)
                        horizontalLineTo(0f)
                        verticalLineTo(0f)
                        close()
                    }
                    path(
                        fill = SolidColor(Color(0xFFF93939)),
                        fillAlpha = 1.0f,
                        stroke = null,
                        strokeAlpha = 1.0f,
                        strokeLineWidth = 1.0f,
                        strokeLineCap = StrokeCap.Butt,
                        strokeLineJoin = StrokeJoin.Miter,
                        strokeLineMiter = 1.0f,
                        pathFillType = PathFillType.EvenOdd
                    ) {
                        moveTo(15f, 0f)
                        horizontalLineTo(22f)
                        verticalLineTo(16f)
                        horizontalLineTo(15f)
                        verticalLineTo(0f)
                        close()
                    }
                }
            }
        }.build()
    }

    val Vietnam: ImageVector by lazy {
        ImageVector.Builder(
            name = "Vietnam",
            defaultWidth = 22.dp,
            defaultHeight = 16.dp,
            viewportWidth = 22f,
            viewportHeight = 16f
        ).apply {
            group {
                path(
                    fill = SolidColor(Color(0xFFF93939)),
                    fillAlpha = 1.0f,
                    stroke = null,
                    strokeAlpha = 1.0f,
                    strokeLineWidth = 1.0f,
                    strokeLineCap = StrokeCap.Butt,
                    strokeLineJoin = StrokeJoin.Miter,
                    strokeLineMiter = 1.0f,
                    pathFillType = PathFillType.NonZero
                ) {
                    moveTo(2f, 0f)
                    horizontalLineTo(20f)
                    arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 22f, 2f)
                    verticalLineTo(14f)
                    arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 20f, 16f)
                    horizontalLineTo(2f)
                    arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0f, 14f)
                    verticalLineTo(2f)
                    arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 2f, 0f)
                    close()
                }
                path(
                    fill = SolidColor(Color(0xFFFFDA2C)),
                    fillAlpha = 1.0f,
                    stroke = null,
                    strokeAlpha = 1.0f,
                    strokeLineWidth = 1.0f,
                    strokeLineCap = StrokeCap.Butt,
                    strokeLineJoin = StrokeJoin.Miter,
                    strokeLineMiter = 1.0f,
                    pathFillType = PathFillType.EvenOdd
                ) {
                    moveTo(11.0021f, 9.952f)
                    lineTo(8.81257f, 11.1253f)
                    lineTo(9.23162f, 8.6432f)
                    lineTo(7.46114f, 6.8864f)
                    lineTo(9.90838f, 6.52373f)
                    lineTo(11.0021f, 4.26666f)
                    lineTo(12.0969f, 6.52373f)
                    lineTo(14.543f, 6.8864f)
                    lineTo(12.7726f, 8.6432f)
                    lineTo(13.1916f, 11.1243f)
                }
            }
        }.build()
    }

    val USA: ImageVector by lazy {
        ImageVector.Builder(
            name = "USA",
            defaultWidth = 22.dp,
            defaultHeight = 16.dp,
            viewportWidth = 22f,
            viewportHeight = 16f
        ).apply {
            group {
                group {
                    path(
                        fill = SolidColor(Color(0xFFFFFFFF)),
                        fillAlpha = 1.0f,
                        stroke = null,
                        strokeAlpha = 1.0f,
                        strokeLineWidth = 1.0f,
                        strokeLineCap = StrokeCap.Butt,
                        strokeLineJoin = StrokeJoin.Miter,
                        strokeLineMiter = 1.0f,
                        pathFillType = PathFillType.NonZero
                    ) {
                        moveTo(2f, 0f)
                        horizontalLineTo(20f)
                        arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 22f, 2f)
                        verticalLineTo(14f)
                        arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 20f, 16f)
                        horizontalLineTo(2f)
                        arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0f, 14f)
                        verticalLineTo(2f)
                        arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 2f, 0f)
                        close()
                    }
                    path(
                        fill = SolidColor(Color(0xFF1A47B8)),
                        fillAlpha = 1.0f,
                        stroke = null,
                        strokeAlpha = 1.0f,
                        strokeLineWidth = 1.0f,
                        strokeLineCap = StrokeCap.Butt,
                        strokeLineJoin = StrokeJoin.Miter,
                        strokeLineMiter = 1.0f,
                        pathFillType = PathFillType.EvenOdd
                    ) {
                        moveTo(0f, 0f)
                        horizontalLineTo(9.42857f)
                        verticalLineTo(7.46667f)
                        horizontalLineTo(0f)
                        verticalLineTo(0f)
                        close()
                    }
                    path(
                        fill = SolidColor(Color(0xFFF93939)),
                        fillAlpha = 1.0f,
                        stroke = null,
                        strokeAlpha = 1.0f,
                        strokeLineWidth = 1.0f,
                        strokeLineCap = StrokeCap.Butt,
                        strokeLineJoin = StrokeJoin.Miter,
                        strokeLineMiter = 1.0f,
                        pathFillType = PathFillType.EvenOdd
                    ) {
                        moveTo(9.42857f, 0f)
                        verticalLineTo(1.06667f)
                        horizontalLineTo(22f)
                        verticalLineTo(0f)
                        horizontalLineTo(9.42857f)
                        close()
                        moveTo(9.42857f, 2.13333f)
                        verticalLineTo(3.2f)
                        horizontalLineTo(22f)
                        verticalLineTo(2.13333f)
                        horizontalLineTo(9.42857f)
                        close()
                        moveTo(9.42857f, 4.26667f)
                        verticalLineTo(5.33333f)
                        horizontalLineTo(22f)
                        verticalLineTo(4.26667f)
                        horizontalLineTo(9.42857f)
                        close()
                        moveTo(9.42857f, 6.4f)
                        verticalLineTo(7.46667f)
                        horizontalLineTo(22f)
                        verticalLineTo(6.4f)
                        horizontalLineTo(9.42857f)
                        close()
                        moveTo(0f, 8.53333f)
                        verticalLineTo(9.6f)
                        horizontalLineTo(22f)
                        verticalLineTo(8.53333f)
                        horizontalLineTo(0f)
                        close()
                        moveTo(0f, 10.6667f)
                        verticalLineTo(11.7333f)
                        horizontalLineTo(22f)
                        verticalLineTo(10.6667f)
                        horizontalLineTo(0f)
                        close()
                        moveTo(0f, 12.8f)
                        verticalLineTo(13.8667f)
                        horizontalLineTo(22f)
                        verticalLineTo(12.8f)
                        horizontalLineTo(0f)
                        close()
                        moveTo(0f, 14.9333f)
                        verticalLineTo(16f)
                        horizontalLineTo(22f)
                        verticalLineTo(14.9333f)
                        horizontalLineTo(0f)
                        close()
                    }
                    path(
                        fill = SolidColor(Color(0xFFFFFFFF)),
                        fillAlpha = 1.0f,
                        stroke = null,
                        strokeAlpha = 1.0f,
                        strokeLineWidth = 1.0f,
                        strokeLineCap = StrokeCap.Butt,
                        strokeLineJoin = StrokeJoin.Miter,
                        strokeLineMiter = 1.0f,
                        pathFillType = PathFillType.EvenOdd
                    ) {
                        moveTo(1.04762f, 1.06668f)
                        verticalLineTo(2.13335f)
                        horizontalLineTo(2.09524f)
                        verticalLineTo(1.06668f)
                        horizontalLineTo(1.04762f)
                        close()
                        moveTo(3.14286f, 1.06668f)
                        verticalLineTo(2.13335f)
                        horizontalLineTo(4.19048f)
                        verticalLineTo(1.06668f)
                        horizontalLineTo(3.14286f)
                        close()
                        moveTo(5.2381f, 1.06668f)
                        verticalLineTo(2.13335f)
                        horizontalLineTo(6.28571f)
                        verticalLineTo(1.06668f)
                        horizontalLineTo(5.2381f)
                        close()
                        moveTo(7.33333f, 1.06668f)
                        verticalLineTo(2.13335f)
                        horizontalLineTo(8.38095f)
                        verticalLineTo(1.06668f)
                        horizontalLineTo(7.33333f)
                        close()
                        moveTo(6.28571f, 2.13335f)
                        verticalLineTo(3.20001f)
                        horizontalLineTo(7.33333f)
                        verticalLineTo(2.13335f)
                        horizontalLineTo(6.28571f)
                        close()
                        moveTo(4.19048f, 2.13335f)
                        verticalLineTo(3.20001f)
                        horizontalLineTo(5.2381f)
                        verticalLineTo(2.13335f)
                        horizontalLineTo(4.19048f)
                        close()
                        moveTo(2.09524f, 2.13335f)
                        verticalLineTo(3.20001f)
                        horizontalLineTo(3.14286f)
                        verticalLineTo(2.13335f)
                        horizontalLineTo(2.09524f)
                        close()
                        moveTo(1.04762f, 3.20001f)
                        verticalLineTo(4.26668f)
                        horizontalLineTo(2.09524f)
                        verticalLineTo(3.20001f)
                        horizontalLineTo(1.04762f)
                        close()
                        moveTo(3.14286f, 3.20001f)
                        verticalLineTo(4.26668f)
                        horizontalLineTo(4.19048f)
                        verticalLineTo(3.20001f)
                        horizontalLineTo(3.14286f)
                        close()
                        moveTo(5.2381f, 3.20001f)
                        verticalLineTo(4.26668f)
                        horizontalLineTo(6.28571f)
                        verticalLineTo(3.20001f)
                        horizontalLineTo(5.2381f)
                        close()
                        moveTo(7.33333f, 3.20001f)
                        verticalLineTo(4.26668f)
                        horizontalLineTo(8.38095f)
                        verticalLineTo(3.20001f)
                        horizontalLineTo(7.33333f)
                        close()
                        moveTo(1.04762f, 5.33335f)
                        verticalLineTo(6.40001f)
                        horizontalLineTo(2.09524f)
                        verticalLineTo(5.33335f)
                        horizontalLineTo(1.04762f)
                        close()
                        moveTo(3.14286f, 5.33335f)
                        verticalLineTo(6.40001f)
                        horizontalLineTo(4.19048f)
                        verticalLineTo(5.33335f)
                        horizontalLineTo(3.14286f)
                        close()
                        moveTo(5.2381f, 5.33335f)
                        verticalLineTo(6.40001f)
                        horizontalLineTo(6.28571f)
                        verticalLineTo(5.33335f)
                        horizontalLineTo(5.2381f)
                        close()
                        moveTo(7.33333f, 5.33335f)
                        verticalLineTo(6.40001f)
                        horizontalLineTo(8.38095f)
                        verticalLineTo(5.33335f)
                        horizontalLineTo(7.33333f)
                        close()
                        moveTo(6.28571f, 4.26668f)
                        verticalLineTo(5.33335f)
                        horizontalLineTo(7.33333f)
                        verticalLineTo(4.26668f)
                        horizontalLineTo(6.28571f)
                        close()
                        moveTo(4.19048f, 4.26668f)
                        verticalLineTo(5.33335f)
                        horizontalLineTo(5.2381f)
                        verticalLineTo(4.26668f)
                        horizontalLineTo(4.19048f)
                        close()
                        moveTo(2.09524f, 4.26668f)
                        verticalLineTo(5.33335f)
                        horizontalLineTo(3.14286f)
                        verticalLineTo(4.26668f)
                        horizontalLineTo(2.09524f)
                        close()
                    }
                }
            }
        }.build()
    }

    val UK: ImageVector by lazy {
        ImageVector.Builder(
            name = "UK",
            defaultWidth = 22.dp,
            defaultHeight = 16.dp,
            viewportWidth = 22f,
            viewportHeight = 16f
        ).apply {
            group {
                group {
                    path(
                        fill = SolidColor(Color(0xFF1A47B8)),
                        fillAlpha = 1.0f,
                        stroke = null,
                        strokeAlpha = 1.0f,
                        strokeLineWidth = 1.0f,
                        strokeLineCap = StrokeCap.Butt,
                        strokeLineJoin = StrokeJoin.Miter,
                        strokeLineMiter = 1.0f,
                        pathFillType = PathFillType.NonZero
                    ) {
                        moveTo(2f, 0f)
                        horizontalLineTo(20f)
                        arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 22f, 2f)
                        verticalLineTo(14f)
                        arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 20f, 16f)
                        horizontalLineTo(2f)
                        arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0f, 14f)
                        verticalLineTo(2f)
                        arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 2f, 0f)
                        close()
                    }
                    path(
                        fill = SolidColor(Color(0xFFFFFFFF)),
                        fillAlpha = 1.0f,
                        stroke = null,
                        strokeAlpha = 1.0f,
                        strokeLineWidth = 1.0f,
                        strokeLineCap = StrokeCap.Butt,
                        strokeLineJoin = StrokeJoin.Miter,
                        strokeLineMiter = 1.0f,
                        pathFillType = PathFillType.EvenOdd
                    ) {
                        moveTo(2.34035f, 0f)
                        horizontalLineTo(0f)
                        verticalLineTo(2.66667f)
                        lineTo(19.6469f, 16f)
                        lineTo(22f, 16f)
                        verticalLineTo(13.3333f)
                        lineTo(2.34035f, 0f)
                        close()
                    }
                    path(
                        fill = SolidColor(Color(0xFFF93939)),
                        fillAlpha = 1.0f,
                        stroke = null,
                        strokeAlpha = 1.0f,
                        strokeLineWidth = 1.0f,
                        strokeLineCap = StrokeCap.Butt,
                        strokeLineJoin = StrokeJoin.Miter,
                        strokeLineMiter = 1.0f,
                        pathFillType = PathFillType.NonZero
                    ) {
                        moveTo(0.780579f, 0f)
                        lineTo(22f, 14.4378f)
                        verticalLineTo(16f)
                        horizontalLineTo(21.2377f)
                        lineTo(0f, 1.54726f)
                        verticalLineTo(0f)
                        horizontalLineTo(0.780579f)
                        close()
                    }
                    path(
                        fill = SolidColor(Color(0xFFFFFFFF)),
                        fillAlpha = 1.0f,
                        stroke = null,
                        strokeAlpha = 1.0f,
                        strokeLineWidth = 1.0f,
                        strokeLineCap = StrokeCap.Butt,
                        strokeLineJoin = StrokeJoin.Miter,
                        strokeLineMiter = 1.0f,
                        pathFillType = PathFillType.EvenOdd
                    ) {
                        moveTo(19.9048f, 0f)
                        horizontalLineTo(22.0001f)
                        verticalLineTo(2.66667f)
                        curveTo(22.0001f, 2.6667f, 8.3913f, 11.5499f, 2.0953f, 16f)
                        horizontalLineTo(0.0000762939f)
                        verticalLineTo(13.3333f)
                        lineTo(19.9048f, 0f)
                        close()
                    }
                    path(
                        fill = SolidColor(Color(0xFFF93939)),
                        fillAlpha = 1.0f,
                        stroke = null,
                        strokeAlpha = 1.0f,
                        strokeLineWidth = 1.0f,
                        strokeLineCap = StrokeCap.Butt,
                        strokeLineJoin = StrokeJoin.Miter,
                        strokeLineMiter = 1.0f,
                        pathFillType = PathFillType.NonZero
                    ) {
                        moveTo(22f, 0f)
                        horizontalLineTo(21.2895f)
                        lineTo(0f, 14.4502f)
                        verticalLineTo(16f)
                        horizontalLineTo(0.780579f)
                        lineTo(22f, 1.55895f)
                        verticalLineTo(0f)
                        close()
                    }
                    path(
                        fill = SolidColor(Color(0xFFFFFFFF)),
                        fillAlpha = 1.0f,
                        stroke = null,
                        strokeAlpha = 1.0f,
                        strokeLineWidth = 1.0f,
                        strokeLineCap = StrokeCap.Butt,
                        strokeLineJoin = StrokeJoin.Miter,
                        strokeLineMiter = 1.0f,
                        pathFillType = PathFillType.EvenOdd
                    ) {
                        moveTo(8.00075f, 0f)
                        horizontalLineTo(14.0176f)
                        verticalLineTo(4.93527f)
                        horizontalLineTo(22f)
                        verticalLineTo(11.0615f)
                        horizontalLineTo(14.0176f)
                        verticalLineTo(16f)
                        horizontalLineTo(8.00075f)
                        verticalLineTo(11.0615f)
                        horizontalLineTo(0f)
                        verticalLineTo(4.93527f)
                        horizontalLineTo(8.00075f)
                        verticalLineTo(0f)
                        close()
                    }
                    path(
                        fill = SolidColor(Color(0xFFF93939)),
                        fillAlpha = 1.0f,
                        stroke = null,
                        strokeAlpha = 1.0f,
                        strokeLineWidth = 1.0f,
                        strokeLineCap = StrokeCap.Butt,
                        strokeLineJoin = StrokeJoin.Miter,
                        strokeLineMiter = 1.0f,
                        pathFillType = PathFillType.EvenOdd
                    ) {
                        moveTo(9.26316f, 0f)
                        horizontalLineTo(12.7368f)
                        verticalLineTo(6.15385f)
                        horizontalLineTo(22f)
                        verticalLineTo(9.84615f)
                        horizontalLineTo(12.7368f)
                        verticalLineTo(16f)
                        horizontalLineTo(9.26316f)
                        verticalLineTo(9.84615f)
                        horizontalLineTo(0f)
                        verticalLineTo(6.15385f)
                        horizontalLineTo(9.26316f)
                        verticalLineTo(0f)
                        close()
                    }
                }
            }
        }.build()
    }

}