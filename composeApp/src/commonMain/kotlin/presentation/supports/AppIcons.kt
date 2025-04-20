package io.github.vrcmteam.vrcm.presentation.supports

import androidx.compose.material.icons.materialIcon
import androidx.compose.material.icons.materialPath
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

object AppIcons {

    val Windows: ImageVector by lazy {
        ImageVector.Builder(
                name = "Windows",
                defaultWidth = 16.dp,
                defaultHeight = 16.dp,
                viewportWidth = 16f,
                viewportHeight = 16f
            ).apply {
                path(
                    fill = SolidColor(Color(0xFF000000)),
                    fillAlpha = 1.0f,
                    stroke = null,
                    strokeAlpha = 1.0f,
                    strokeLineWidth = 1.0f,
                    strokeLineCap = StrokeCap.Butt,
                    strokeLineJoin = StrokeJoin.Miter,
                    strokeLineMiter = 1.0f,
                    pathFillType = PathFillType.NonZero
                ) {
                    moveTo(6.555f, 1.375f)
                    lineTo(0f, 2.237f)
                    verticalLineToRelative(5.45f)
                    horizontalLineToRelative(6.555f)
                    close()
                    moveTo(0f, 13.795f)
                    lineToRelative(6.555f, 0.933f)
                    verticalLineTo(8.313f)
                    horizontalLineTo(0f)
                    close()
                    moveToRelative(7.278f, -5.4f)
                    lineToRelative(0.026f, 6.378f)
                    lineTo(16f, 16f)
                    verticalLineTo(8.395f)
                    close()
                    moveTo(16f, 0f)
                    lineTo(7.33f, 1.244f)
                    verticalLineToRelative(6.414f)
                    horizontalLineTo(16f)
                    close()
                }
            }.build()
    }
    val Android: ImageVector by lazy {
        ImageVector.Builder(
            name = "Android",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 960f,
            viewportHeight = 960f
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
                moveTo(40f, 720f)
                quadToRelative(9f, -107f, 65.5f, -197f)
                reflectiveQuadTo(256f, 380f)
                lineToRelative(-74f, -128f)
                quadToRelative(-6f, -9f, -3f, -19f)
                reflectiveQuadToRelative(13f, -15f)
                quadToRelative(8f, -5f, 18f, -2f)
                reflectiveQuadToRelative(16f, 12f)
                lineToRelative(74f, 128f)
                quadToRelative(86f, -36f, 180f, -36f)
                reflectiveQuadToRelative(180f, 36f)
                lineToRelative(74f, -128f)
                quadToRelative(6f, -9f, 16f, -12f)
                reflectiveQuadToRelative(18f, 2f)
                quadToRelative(10f, 5f, 13f, 15f)
                reflectiveQuadToRelative(-3f, 19f)
                lineToRelative(-74f, 128f)
                quadToRelative(94f, 53f, 150.5f, 143f)
                reflectiveQuadTo(920f, 720f)
                close()
                moveToRelative(240f, -110f)
                quadToRelative(21f, 0f, 35.5f, -14.5f)
                reflectiveQuadTo(330f, 560f)
                reflectiveQuadToRelative(-14.5f, -35.5f)
                reflectiveQuadTo(280f, 510f)
                reflectiveQuadToRelative(-35.5f, 14.5f)
                reflectiveQuadTo(230f, 560f)
                reflectiveQuadToRelative(14.5f, 35.5f)
                reflectiveQuadTo(280f, 610f)
                moveToRelative(400f, 0f)
                quadToRelative(21f, 0f, 35.5f, -14.5f)
                reflectiveQuadTo(730f, 560f)
                reflectiveQuadToRelative(-14.5f, -35.5f)
                reflectiveQuadTo(680f, 510f)
                reflectiveQuadToRelative(-35.5f, 14.5f)
                reflectiveQuadTo(630f, 560f)
                reflectiveQuadToRelative(14.5f, 35.5f)
                reflectiveQuadTo(680f, 610f)
            }
        }.build()
    }

    val Apple: ImageVector by lazy {
        ImageVector.Builder(
            name = "Apple",
            defaultWidth = 16.dp,
            defaultHeight = 16.dp,
            viewportWidth = 16f,
            viewportHeight = 16f
        ).apply {
            path(
                fill = SolidColor(Color(0xFF000000)),
                fillAlpha = 1.0f,
                stroke = null,
                strokeAlpha = 1.0f,
                strokeLineWidth = 1.0f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Miter,
                strokeLineMiter = 1.0f,
                pathFillType = PathFillType.NonZero
            ) {
                moveTo(11.182f, 0.008f)
                curveTo(11.148f, -0.03f, 9.923f, 0.023f, 8.857f, 1.18f)
                curveToRelative(-1.066f, 1.156f, -0.902f, 2.482f, -0.878f, 2.516f)
                reflectiveCurveToRelative(1.52f, 0.087f, 2.475f, -1.258f)
                reflectiveCurveToRelative(0.762f, -2.391f, 0.728f, -2.43f)
                moveToRelative(3.314f, 11.733f)
                curveToRelative(-0.048f, -0.096f, -2.325f, -1.234f, -2.113f, -3.422f)
                reflectiveCurveToRelative(1.675f, -2.789f, 1.698f, -2.854f)
                reflectiveCurveToRelative(-0.597f, -0.79f, -1.254f, -1.157f)
                arcToRelative(3.7f, 3.7f, 0f, isMoreThanHalf = false, isPositiveArc = false, -1.563f, -0.434f)
                curveToRelative(-0.108f, -0.003f, -0.483f, -0.095f, -1.254f, 0.116f)
                curveToRelative(-0.508f, 0.139f, -1.653f, 0.589f, -1.968f, 0.607f)
                curveToRelative(-0.316f, 0.018f, -1.256f, -0.522f, -2.267f, -0.665f)
                curveToRelative(-0.647f, -0.125f, -1.333f, 0.131f, -1.824f, 0.328f)
                curveToRelative(-0.49f, 0.196f, -1.422f, 0.754f, -2.074f, 2.237f)
                curveToRelative(-0.652f, 1.482f, -0.311f, 3.83f, -0.067f, 4.56f)
                reflectiveCurveToRelative(0.625f, 1.924f, 1.273f, 2.796f)
                curveToRelative(0.576f, 0.984f, 1.34f, 1.667f, 1.659f, 1.899f)
                reflectiveCurveToRelative(1.219f, 0.386f, 1.843f, 0.067f)
                curveToRelative(0.502f, -0.308f, 1.408f, -0.485f, 1.766f, -0.472f)
                curveToRelative(0.357f, 0.013f, 1.061f, 0.154f, 1.782f, 0.539f)
                curveToRelative(0.571f, 0.197f, 1.111f, 0.115f, 1.652f, -0.105f)
                curveToRelative(0.541f, -0.221f, 1.324f, -1.059f, 2.238f, -2.758f)
                quadToRelative(0.52f, -1.185f, 0.473f, -1.282f)
            }
            path(
                fill = SolidColor(Color(0xFF000000)),
                fillAlpha = 1.0f,
                stroke = null,
                strokeAlpha = 1.0f,
                strokeLineWidth = 1.0f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Miter,
                strokeLineMiter = 1.0f,
                pathFillType = PathFillType.NonZero
            ) {
                moveTo(11.182f, 0.008f)
                curveTo(11.148f, -0.03f, 9.923f, 0.023f, 8.857f, 1.18f)
                curveToRelative(-1.066f, 1.156f, -0.902f, 2.482f, -0.878f, 2.516f)
                reflectiveCurveToRelative(1.52f, 0.087f, 2.475f, -1.258f)
                reflectiveCurveToRelative(0.762f, -2.391f, 0.728f, -2.43f)
                moveToRelative(3.314f, 11.733f)
                curveToRelative(-0.048f, -0.096f, -2.325f, -1.234f, -2.113f, -3.422f)
                reflectiveCurveToRelative(1.675f, -2.789f, 1.698f, -2.854f)
                reflectiveCurveToRelative(-0.597f, -0.79f, -1.254f, -1.157f)
                arcToRelative(3.7f, 3.7f, 0f, isMoreThanHalf = false, isPositiveArc = false, -1.563f, -0.434f)
                curveToRelative(-0.108f, -0.003f, -0.483f, -0.095f, -1.254f, 0.116f)
                curveToRelative(-0.508f, 0.139f, -1.653f, 0.589f, -1.968f, 0.607f)
                curveToRelative(-0.316f, 0.018f, -1.256f, -0.522f, -2.267f, -0.665f)
                curveToRelative(-0.647f, -0.125f, -1.333f, 0.131f, -1.824f, 0.328f)
                curveToRelative(-0.49f, 0.196f, -1.422f, 0.754f, -2.074f, 2.237f)
                curveToRelative(-0.652f, 1.482f, -0.311f, 3.83f, -0.067f, 4.56f)
                reflectiveCurveToRelative(0.625f, 1.924f, 1.273f, 2.796f)
                curveToRelative(0.576f, 0.984f, 1.34f, 1.667f, 1.659f, 1.899f)
                reflectiveCurveToRelative(1.219f, 0.386f, 1.843f, 0.067f)
                curveToRelative(0.502f, -0.308f, 1.408f, -0.485f, 1.766f, -0.472f)
                curveToRelative(0.357f, 0.013f, 1.061f, 0.154f, 1.782f, 0.539f)
                curveToRelative(0.571f, 0.197f, 1.111f, 0.115f, 1.652f, -0.105f)
                curveToRelative(0.541f, -0.221f, 1.324f, -1.059f, 2.238f, -2.758f)
                quadToRelative(0.52f, -1.185f, 0.473f, -1.282f)
            }
        }.build()
    }
    val FlaskConical: ImageVector by lazy {
        ImageVector.Builder(
            name = "FlaskConical",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = null,
                fillAlpha = 1.0f,
                stroke = SolidColor(Color(0xFF000000)),
                strokeAlpha = 1.0f,
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 1.0f,
                pathFillType = PathFillType.NonZero
            ) {
                moveTo(10f, 2f)
                verticalLineToRelative(7.527f)
                arcToRelative(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, -0.211f, 0.896f)
                lineTo(4.72f, 20.55f)
                arcToRelative(1f, 1f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0.9f, 1.45f)
                horizontalLineToRelative(12.76f)
                arcToRelative(1f, 1f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0.9f, -1.45f)
                lineToRelative(-5.069f, -10.127f)
                arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 14f, 9.527f)
                verticalLineTo(2f)
            }
            path(
                fill = null,
                fillAlpha = 1.0f,
                stroke = SolidColor(Color(0xFF000000)),
                strokeAlpha = 1.0f,
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 1.0f,
                pathFillType = PathFillType.NonZero
            ) {
                moveTo(8.5f, 2f)
                horizontalLineToRelative(7f)
            }
            path(
                fill = null,
                fillAlpha = 1.0f,
                stroke = SolidColor(Color(0xFF000000)),
                strokeAlpha = 1.0f,
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 1.0f,
                pathFillType = PathFillType.NonZero
            ) {
                moveTo(7f, 16f)
                horizontalLineToRelative(10f)
            }
        }.build()

    }

    val SaveAlt: ImageVector by lazy {
        materialIcon(name = "Filled.SaveAlt") {
            materialPath {
                moveTo(19.0f, 12.0f)
                verticalLineToRelative(7.0f)
                horizontalLineTo(5.0f)
                verticalLineToRelative(-7.0f)
                horizontalLineTo(3.0f)
                verticalLineToRelative(7.0f)
                curveToRelative(0.0f, 1.1f, 0.9f, 2.0f, 2.0f, 2.0f)
                horizontalLineToRelative(14.0f)
                curveToRelative(1.1f, 0.0f, 2.0f, -0.9f, 2.0f, -2.0f)
                verticalLineToRelative(-7.0f)
                horizontalLineToRelative(-2.0f)
                close()
                moveTo(13.0f, 12.67f)
                lineToRelative(2.59f, -2.58f)
                lineTo(17.0f, 11.5f)
                lineToRelative(-5.0f, 5.0f)
                lineToRelative(-5.0f, -5.0f)
                lineToRelative(1.41f, -1.41f)
                lineTo(11.0f, 12.67f)
                verticalLineTo(3.0f)
                horizontalLineToRelative(2.0f)
                verticalLineToRelative(9.67f)
                close()
            }
        }
    }

    val Groups: ImageVector by lazy {
        ImageVector.Builder(
            name = "Groups",
            defaultWidth = 64.dp,
            defaultHeight = 64.dp,
            viewportWidth = 64f,
            viewportHeight = 64f
        ).apply {
            path(fill = SolidColor(Color(0xFF000000))) {
                moveTo(41.61f, 24.28f)
                lineTo(47.92f, 37.57f)
                lineTo(49.218f, 40.324f)
                curveTo(50.111f, 40.112f, 51.042f, 40f, 52f, 40f)
                curveTo(58.627f, 40f, 64f, 45.373f, 64f, 52f)
                curveTo(64f, 58.627f, 58.627f, 64f, 52f, 64f)
                curveTo(45.373f, 64f, 40f, 58.627f, 40f, 52f)
                curveTo(40f, 48.118f, 41.843f, 44.667f, 44.702f, 42.474f)
                lineTo(43.4f, 39.72f)
                lineTo(37.09f, 26.43f)
                curveTo(38.73f, 25.97f, 40.25f, 25.23f, 41.61f, 24.28f)
                close()
                moveTo(12f, 40f)
                curveTo(17.77f, 40f, 22.589f, 44.073f, 23.739f, 49.5f)
                lineTo(37.21f, 49.5f)
                curveTo(37.07f, 50.31f, 37f, 51.15f, 37f, 52f)
                curveTo(37f, 52.85f, 37.07f, 53.69f, 37.21f, 54.5f)
                lineTo(23.739f, 54.501f)
                curveTo(22.589f, 59.928f, 17.77f, 64f, 12f, 64f)
                curveTo(5.373f, 64f, 0f, 58.627f, 0f, 52f)
                curveTo(0f, 45.373f, 5.373f, 40f, 12f, 40f)
                close()
                moveTo(33f, 0f)
                lineTo(33.305f, 0.004f)
                curveTo(39.791f, 0.166f, 45f, 5.475f, 45f, 12f)
                curveTo(45f, 18.627f, 39.627f, 24f, 33f, 24f)
                curveTo(31.876f, 24f, 30.788f, 23.845f, 29.756f, 23.556f)
                lineTo(21.08f, 40.07f)
                curveTo(19.76f, 39.06f, 18.27f, 38.27f, 16.66f, 37.75f)
                lineTo(25.33f, 21.24f)
                curveTo(22.686f, 19.031f, 21f, 15.712f, 21f, 12f)
                curveTo(21f, 5.373f, 26.373f, 0f, 33f, 0f)
                close()
            }
        }.build()
    }
    val Link: ImageVector by lazy {
        materialIcon(name = "Outlined.Link") {
            materialPath {
                moveTo(17.0f, 7.0f)
                horizontalLineToRelative(-4.0f)
                verticalLineToRelative(2.0f)
                horizontalLineToRelative(4.0f)
                curveToRelative(1.65f, 0.0f, 3.0f, 1.35f, 3.0f, 3.0f)
                reflectiveCurveToRelative(-1.35f, 3.0f, -3.0f, 3.0f)
                horizontalLineToRelative(-4.0f)
                verticalLineToRelative(2.0f)
                horizontalLineToRelative(4.0f)
                curveToRelative(2.76f, 0.0f, 5.0f, -2.24f, 5.0f, -5.0f)
                reflectiveCurveToRelative(-2.24f, -5.0f, -5.0f, -5.0f)
                close()
                moveTo(11.0f, 15.0f)
                lineTo(7.0f, 15.0f)
                curveToRelative(-1.65f, 0.0f, -3.0f, -1.35f, -3.0f, -3.0f)
                reflectiveCurveToRelative(1.35f, -3.0f, 3.0f, -3.0f)
                horizontalLineToRelative(4.0f)
                lineTo(11.0f, 7.0f)
                lineTo(7.0f, 7.0f)
                curveToRelative(-2.76f, 0.0f, -5.0f, 2.24f, -5.0f, 5.0f)
                reflectiveCurveToRelative(2.24f, 5.0f, 5.0f, 5.0f)
                horizontalLineToRelative(4.0f)
                verticalLineToRelative(-2.0f)
                close()
                moveTo(8.0f, 11.0f)
                horizontalLineToRelative(8.0f)
                verticalLineToRelative(2.0f)
                lineTo(8.0f, 13.0f)
                close()
            }
        }
    }

    val Computer: ImageVector by lazy {
        materialIcon(name = "Filled.Computer") {
            materialPath {
                moveTo(20.0f, 18.0f)
                curveToRelative(1.1f, 0.0f, 2.0f, -0.9f, 2.0f, -2.0f)
                lineTo(22.0f, 6.0f)
                curveToRelative(0.0f, -1.1f, -0.9f, -2.0f, -2.0f, -2.0f)
                lineTo(4.0f, 4.0f)
                curveToRelative(-1.1f, 0.0f, -2.0f, 0.9f, -2.0f, 2.0f)
                verticalLineToRelative(10.0f)
                curveToRelative(0.0f, 1.1f, 0.9f, 2.0f, 2.0f, 2.0f)
                lineTo(0.0f, 18.0f)
                verticalLineToRelative(2.0f)
                horizontalLineToRelative(24.0f)
                verticalLineToRelative(-2.0f)
                horizontalLineToRelative(-4.0f)
                close()
                moveTo(4.0f, 6.0f)
                horizontalLineToRelative(16.0f)
                verticalLineToRelative(10.0f)
                lineTo(4.0f, 16.0f)
                lineTo(4.0f, 6.0f)
                close()
            }
        }
    }

    val Smartphone: ImageVector by lazy {
        materialIcon(name = "Filled.Smartphone") {
            materialPath {
                moveTo(17.0f, 1.01f)
                lineTo(7.0f, 1.0f)
                curveToRelative(-1.1f, 0.0f, -2.0f, 0.9f, -2.0f, 2.0f)
                verticalLineToRelative(18.0f)
                curveToRelative(0.0f, 1.1f, 0.9f, 2.0f, 2.0f, 2.0f)
                horizontalLineToRelative(10.0f)
                curveToRelative(1.1f, 0.0f, 2.0f, -0.9f, 2.0f, -2.0f)
                verticalLineTo(3.0f)
                curveToRelative(0.0f, -1.1f, -0.9f, -1.99f, -2.0f, -1.99f)
                close()
                moveTo(17.0f, 19.0f)
                horizontalLineTo(7.0f)
                verticalLineTo(5.0f)
                horizontalLineToRelative(10.0f)
                verticalLineTo(19.0f)
                close()
            }
        }
    }

    val Favorite: ImageVector by lazy {
        materialIcon(name = "Filled.Favorite") {
            materialPath {
                moveTo(12.0f, 21.35f)
                lineTo(10.55f, 20.03f)
                curveTo(4.0f, 14.08f, 2.0f, 11.0f, 2.0f, 8.5f)
                curveTo(2.0f, 5.42f, 4.42f, 3.0f, 7.5f, 3.0f)
                curveToRelative(1.74f, 0.0f, 3.41f, 0.81f, 4.5f, 2.09f)
                curveTo(13.09f, 3.81f, 14.76f, 3.0f, 16.5f, 3.0f)
                curveTo(19.58f, 3.0f, 22.0f, 5.42f, 22.0f, 8.5f)
                curveToRelative(0.0f, 3.78f, -3.4f, 6.86f, -8.55f, 11.54f)
                lineTo(12.0f, 21.35f)
                close()
            }
        }
    }

    val QuestionMark: ImageVector by lazy {
        materialIcon(name = "Rounded.QuestionMark") {
            materialPath {
                moveTo(7.92f, 7.54f)
                curveTo(7.12f, 7.2f, 6.78f, 6.21f, 7.26f, 5.49f)
                curveTo(8.23f, 4.05f, 9.85f, 3.0f, 11.99f, 3.0f)
                curveToRelative(2.35f, 0.0f, 3.96f, 1.07f, 4.78f, 2.41f)
                curveToRelative(0.7f, 1.15f, 1.11f, 3.3f, 0.03f, 4.9f)
                curveToRelative(-1.2f, 1.77f, -2.35f, 2.31f, -2.97f, 3.45f)
                curveToRelative(-0.15f, 0.27f, -0.24f, 0.49f, -0.3f, 0.94f)
                curveToRelative(-0.09f, 0.73f, -0.69f, 1.3f, -1.43f, 1.3f)
                curveToRelative(-0.87f, 0.0f, -1.58f, -0.75f, -1.48f, -1.62f)
                curveToRelative(0.06f, -0.51f, 0.18f, -1.04f, 0.46f, -1.54f)
                curveToRelative(0.77f, -1.39f, 2.25f, -2.21f, 3.11f, -3.44f)
                curveToRelative(0.91f, -1.29f, 0.4f, -3.7f, -2.18f, -3.7f)
                curveToRelative(-1.17f, 0.0f, -1.93f, 0.61f, -2.4f, 1.34f)
                curveTo(9.26f, 7.61f, 8.53f, 7.79f, 7.92f, 7.54f)
                close()
                moveTo(14.0f, 20.0f)
                curveToRelative(0.0f, 1.1f, -0.9f, 2.0f, -2.0f, 2.0f)
                reflectiveCurveToRelative(-2.0f, -0.9f, -2.0f, -2.0f)
                curveToRelative(0.0f, -1.1f, 0.9f, -2.0f, 2.0f, -2.0f)
                reflectiveCurveTo(14.0f, 18.9f, 14.0f, 20.0f)
                close()
            }
        }
    }

    val Person: ImageVector by lazy {
        materialIcon(name = "Filled.Person") {
            materialPath {
                moveTo(12.0f, 12.0f)
                curveToRelative(2.21f, 0.0f, 4.0f, -1.79f, 4.0f, -4.0f)
                reflectiveCurveToRelative(-1.79f, -4.0f, -4.0f, -4.0f)
                reflectiveCurveToRelative(-4.0f, 1.79f, -4.0f, 4.0f)
                reflectiveCurveToRelative(1.79f, 4.0f, 4.0f, 4.0f)
                close()
                moveTo(12.0f, 14.0f)
                curveToRelative(-2.67f, 0.0f, -8.0f, 1.34f, -8.0f, 4.0f)
                verticalLineToRelative(2.0f)
                horizontalLineToRelative(16.0f)
                verticalLineToRelative(-2.0f)
                curveToRelative(0.0f, -2.66f, -5.33f, -4.0f, -8.0f, -4.0f)
                close()
            }
        }
    }

    val ExpandMore: ImageVector by lazy {
        materialIcon(name = "Rounded.ExpandMore") {
            materialPath {
                moveTo(15.88f, 9.29f)
                lineTo(12.0f, 13.17f)
                lineTo(8.12f, 9.29f)
                curveToRelative(-0.39f, -0.39f, -1.02f, -0.39f, -1.41f, 0.0f)
                curveToRelative(-0.39f, 0.39f, -0.39f, 1.02f, 0.0f, 1.41f)
                lineToRelative(4.59f, 4.59f)
                curveToRelative(0.39f, 0.39f, 1.02f, 0.39f, 1.41f, 0.0f)
                lineToRelative(4.59f, -4.59f)
                curveToRelative(0.39f, -0.39f, 0.39f, -1.02f, 0.0f, -1.41f)
                curveToRelative(-0.39f, -0.38f, -1.03f, -0.39f, -1.42f, 0.0f)
                close()
            }
        }
    }

    val ExpandLess: ImageVector by lazy {
        materialIcon(name = "Rounded.ExpandLess") {
            materialPath {
                moveTo(11.29f, 8.71f)
                lineTo(6.7f, 13.3f)
                curveToRelative(-0.39f, 0.39f, -0.39f, 1.02f, 0.0f, 1.41f)
                curveToRelative(0.39f, 0.39f, 1.02f, 0.39f, 1.41f, 0.0f)
                lineTo(12.0f, 10.83f)
                lineToRelative(3.88f, 3.88f)
                curveToRelative(0.39f, 0.39f, 1.02f, 0.39f, 1.41f, 0.0f)
                curveToRelative(0.39f, -0.39f, 0.39f, -1.02f, 0.0f, -1.41f)
                lineTo(12.7f, 8.71f)
                curveToRelative(-0.38f, -0.39f, -1.02f, -0.39f, -1.41f, 0.0f)
                close()
            }
        }
    }

    val Notifications: ImageVector by lazy {
        materialIcon(name = "Rounded.Notifications") {
            materialPath {
                moveTo(12.0f, 22.0f)
                curveToRelative(1.1f, 0.0f, 2.0f, -0.9f, 2.0f, -2.0f)
                horizontalLineToRelative(-4.0f)
                curveToRelative(0.0f, 1.1f, 0.89f, 2.0f, 2.0f, 2.0f)
                close()
                moveTo(18.0f, 16.0f)
                verticalLineToRelative(-5.0f)
                curveToRelative(0.0f, -3.07f, -1.64f, -5.64f, -4.5f, -6.32f)
                lineTo(13.5f, 4.0f)
                curveToRelative(0.0f, -0.83f, -0.67f, -1.5f, -1.5f, -1.5f)
                reflectiveCurveToRelative(-1.5f, 0.67f, -1.5f, 1.5f)
                verticalLineToRelative(0.68f)
                curveTo(7.63f, 5.36f, 6.0f, 7.92f, 6.0f, 11.0f)
                verticalLineToRelative(5.0f)
                lineToRelative(-1.29f, 1.29f)
                curveToRelative(-0.63f, 0.63f, -0.19f, 1.71f, 0.7f, 1.71f)
                horizontalLineToRelative(13.17f)
                curveToRelative(0.89f, 0.0f, 1.34f, -1.08f, 0.71f, -1.71f)
                lineTo(18.0f, 16.0f)
                close()
            }
        }
    }

    val Settings: ImageVector by lazy {
        materialIcon(name = "Rounded.Settings") {
            materialPath {
                moveTo(19.5f, 12.0f)
                curveToRelative(0.0f, -0.23f, -0.01f, -0.45f, -0.03f, -0.68f)
                lineToRelative(1.86f, -1.41f)
                curveToRelative(0.4f, -0.3f, 0.51f, -0.86f, 0.26f, -1.3f)
                lineToRelative(-1.87f, -3.23f)
                curveToRelative(-0.25f, -0.44f, -0.79f, -0.62f, -1.25f, -0.42f)
                lineToRelative(-2.15f, 0.91f)
                curveToRelative(-0.37f, -0.26f, -0.76f, -0.49f, -1.17f, -0.68f)
                lineToRelative(-0.29f, -2.31f)
                curveTo(14.8f, 2.38f, 14.37f, 2.0f, 13.87f, 2.0f)
                horizontalLineToRelative(-3.73f)
                curveTo(9.63f, 2.0f, 9.2f, 2.38f, 9.14f, 2.88f)
                lineTo(8.85f, 5.19f)
                curveToRelative(-0.41f, 0.19f, -0.8f, 0.42f, -1.17f, 0.68f)
                lineTo(5.53f, 4.96f)
                curveToRelative(-0.46f, -0.2f, -1.0f, -0.02f, -1.25f, 0.42f)
                lineTo(2.41f, 8.62f)
                curveToRelative(-0.25f, 0.44f, -0.14f, 0.99f, 0.26f, 1.3f)
                lineToRelative(1.86f, 1.41f)
                curveTo(4.51f, 11.55f, 4.5f, 11.77f, 4.5f, 12.0f)
                reflectiveCurveToRelative(0.01f, 0.45f, 0.03f, 0.68f)
                lineToRelative(-1.86f, 1.41f)
                curveToRelative(-0.4f, 0.3f, -0.51f, 0.86f, -0.26f, 1.3f)
                lineToRelative(1.87f, 3.23f)
                curveToRelative(0.25f, 0.44f, 0.79f, 0.62f, 1.25f, 0.42f)
                lineToRelative(2.15f, -0.91f)
                curveToRelative(0.37f, 0.26f, 0.76f, 0.49f, 1.17f, 0.68f)
                lineToRelative(0.29f, 2.31f)
                curveTo(9.2f, 21.62f, 9.63f, 22.0f, 10.13f, 22.0f)
                horizontalLineToRelative(3.73f)
                curveToRelative(0.5f, 0.0f, 0.93f, -0.38f, 0.99f, -0.88f)
                lineToRelative(0.29f, -2.31f)
                curveToRelative(0.41f, -0.19f, 0.8f, -0.42f, 1.17f, -0.68f)
                lineToRelative(2.15f, 0.91f)
                curveToRelative(0.46f, 0.2f, 1.0f, 0.02f, 1.25f, -0.42f)
                lineToRelative(1.87f, -3.23f)
                curveToRelative(0.25f, -0.44f, 0.14f, -0.99f, -0.26f, -1.3f)
                lineToRelative(-1.86f, -1.41f)
                curveTo(19.49f, 12.45f, 19.5f, 12.23f, 19.5f, 12.0f)
                close()
                moveTo(12.04f, 15.5f)
                curveToRelative(-1.93f, 0.0f, -3.5f, -1.57f, -3.5f, -3.5f)
                reflectiveCurveToRelative(1.57f, -3.5f, 3.5f, -3.5f)
                reflectiveCurveToRelative(3.5f, 1.57f, 3.5f, 3.5f)
                reflectiveCurveTo(13.97f, 15.5f, 12.04f, 15.5f)
                close()
            }
        }
    }

    val Lock: ImageVector by lazy {
        materialIcon(name = "Outlined.Lock") {
            materialPath {
                moveTo(18.0f, 8.0f)
                horizontalLineToRelative(-1.0f)
                lineTo(17.0f, 6.0f)
                curveToRelative(0.0f, -2.76f, -2.24f, -5.0f, -5.0f, -5.0f)
                reflectiveCurveTo(7.0f, 3.24f, 7.0f, 6.0f)
                verticalLineToRelative(2.0f)
                lineTo(6.0f, 8.0f)
                curveToRelative(-1.1f, 0.0f, -2.0f, 0.9f, -2.0f, 2.0f)
                verticalLineToRelative(10.0f)
                curveToRelative(0.0f, 1.1f, 0.9f, 2.0f, 2.0f, 2.0f)
                horizontalLineToRelative(12.0f)
                curveToRelative(1.1f, 0.0f, 2.0f, -0.9f, 2.0f, -2.0f)
                lineTo(20.0f, 10.0f)
                curveToRelative(0.0f, -1.1f, -0.9f, -2.0f, -2.0f, -2.0f)
                close()
                moveTo(9.0f, 6.0f)
                curveToRelative(0.0f, -1.66f, 1.34f, -3.0f, 3.0f, -3.0f)
                reflectiveCurveToRelative(3.0f, 1.34f, 3.0f, 3.0f)
                verticalLineToRelative(2.0f)
                lineTo(9.0f, 8.0f)
                lineTo(9.0f, 6.0f)
                close()
                moveTo(18.0f, 20.0f)
                lineTo(6.0f, 20.0f)
                lineTo(6.0f, 10.0f)
                horizontalLineToRelative(12.0f)
                verticalLineToRelative(10.0f)
                close()
                moveTo(12.0f, 17.0f)
                curveToRelative(1.1f, 0.0f, 2.0f, -0.9f, 2.0f, -2.0f)
                reflectiveCurveToRelative(-0.9f, -2.0f, -2.0f, -2.0f)
                reflectiveCurveToRelative(-2.0f, 0.9f, -2.0f, 2.0f)
                reflectiveCurveToRelative(0.9f, 2.0f, 2.0f, 2.0f)
                close()
            }
        }
    }

    val AccountCircle: ImageVector by lazy {
        materialIcon(name = "Outlined.AccountCircle") {
            materialPath {
                moveTo(12.0f, 2.0f)
                curveTo(6.48f, 2.0f, 2.0f, 6.48f, 2.0f, 12.0f)
                reflectiveCurveToRelative(4.48f, 10.0f, 10.0f, 10.0f)
                reflectiveCurveToRelative(10.0f, -4.48f, 10.0f, -10.0f)
                reflectiveCurveTo(17.52f, 2.0f, 12.0f, 2.0f)
                close()
                moveTo(7.35f, 18.5f)
                curveTo(8.66f, 17.56f, 10.26f, 17.0f, 12.0f, 17.0f)
                reflectiveCurveToRelative(3.34f, 0.56f, 4.65f, 1.5f)
                curveTo(15.34f, 19.44f, 13.74f, 20.0f, 12.0f, 20.0f)
                reflectiveCurveTo(8.66f, 19.44f, 7.35f, 18.5f)
                close()
                moveTo(18.14f, 17.12f)
                lineTo(18.14f, 17.12f)
                curveTo(16.45f, 15.8f, 14.32f, 15.0f, 12.0f, 15.0f)
                reflectiveCurveToRelative(-4.45f, 0.8f, -6.14f, 2.12f)
                lineToRelative(0.0f, 0.0f)
                curveTo(4.7f, 15.73f, 4.0f, 13.95f, 4.0f, 12.0f)
                curveToRelative(0.0f, -4.42f, 3.58f, -8.0f, 8.0f, -8.0f)
                reflectiveCurveToRelative(8.0f, 3.58f, 8.0f, 8.0f)
                curveTo(20.0f, 13.95f, 19.3f, 15.73f, 18.14f, 17.12f)
                close()
            }
            materialPath {
                moveTo(12.0f, 6.0f)
                curveToRelative(-1.93f, 0.0f, -3.5f, 1.57f, -3.5f, 3.5f)
                reflectiveCurveTo(10.07f, 13.0f, 12.0f, 13.0f)
                reflectiveCurveToRelative(3.5f, -1.57f, 3.5f, -3.5f)
                reflectiveCurveTo(13.93f, 6.0f, 12.0f, 6.0f)
                close()
                moveTo(12.0f, 11.0f)
                curveToRelative(-0.83f, 0.0f, -1.5f, -0.67f, -1.5f, -1.5f)
                reflectiveCurveTo(11.17f, 8.0f, 12.0f, 8.0f)
                reflectiveCurveToRelative(1.5f, 0.67f, 1.5f, 1.5f)
                reflectiveCurveTo(12.83f, 11.0f, 12.0f, 11.0f)
                close()
            }
        }
    }

    val KeyboardArrowLeft: ImageVector by lazy {
        materialIcon(
            name = "AutoMirrored.Outlined.KeyboardArrowLeft",
            autoMirror = true
        ) {
            materialPath {
                moveTo(15.41f, 16.59f)
                lineTo(10.83f, 12.0f)
                lineToRelative(4.58f, -4.59f)
                lineTo(14.0f, 6.0f)
                lineToRelative(-6.0f, 6.0f)
                lineToRelative(6.0f, 6.0f)
                lineToRelative(1.41f, -1.41f)
                close()
            }
        }
    }

    val Shield: ImageVector by lazy {
        materialIcon(name = "Rounded.Shield") {
            materialPath {
                moveTo(11.3f, 2.26f)
                lineToRelative(-6.0f, 2.25f)
                curveTo(4.52f, 4.81f, 4.0f, 5.55f, 4.0f, 6.39f)
                verticalLineToRelative(4.7f)
                curveToRelative(0.0f, 4.83f, 3.13f, 9.37f, 7.43f, 10.75f)
                curveToRelative(0.37f, 0.12f, 0.77f, 0.12f, 1.14f, 0.0f)
                curveToRelative(4.3f, -1.38f, 7.43f, -5.91f, 7.43f, -10.75f)
                verticalLineToRelative(-4.7f)
                curveToRelative(0.0f, -0.83f, -0.52f, -1.58f, -1.3f, -1.87f)
                lineToRelative(-6.0f, -2.25f)
                curveTo(12.25f, 2.09f, 11.75f, 2.09f, 11.3f, 2.26f)
                close()
            }
        }
    }

    val Search: ImageVector by lazy {
        materialIcon(name = "Rounded.Search") {
            materialPath {
                moveTo(15.5f, 14.0f)
                horizontalLineToRelative(-0.79f)
                lineToRelative(-0.28f, -0.27f)
                curveToRelative(1.2f, -1.4f, 1.82f, -3.31f, 1.48f, -5.34f)
                curveToRelative(-0.47f, -2.78f, -2.79f, -5.0f, -5.59f, -5.34f)
                curveToRelative(-4.23f, -0.52f, -7.79f, 3.04f, -7.27f, 7.27f)
                curveToRelative(0.34f, 2.8f, 2.56f, 5.12f, 5.34f, 5.59f)
                curveToRelative(2.03f, 0.34f, 3.94f, -0.28f, 5.34f, -1.48f)
                lineToRelative(0.27f, 0.28f)
                verticalLineToRelative(0.79f)
                lineToRelative(4.25f, 4.25f)
                curveToRelative(0.41f, 0.41f, 1.08f, 0.41f, 1.49f, 0.0f)
                curveToRelative(0.41f, -0.41f, 0.41f, -1.08f, 0.0f, -1.49f)
                lineTo(15.5f, 14.0f)
                close()
                moveTo(9.5f, 14.0f)
                curveTo(7.01f, 14.0f, 5.0f, 11.99f, 5.0f, 9.5f)
                reflectiveCurveTo(7.01f, 5.0f, 9.5f, 5.0f)
                reflectiveCurveTo(14.0f, 7.01f, 14.0f, 9.5f)
                reflectiveCurveTo(11.99f, 14.0f, 9.5f, 14.0f)
                close()
            }
        }
    }

    val Update: ImageVector by lazy {
        materialIcon(name = "Filled.Update") {
            materialPath {
                moveTo(21.0f, 10.12f)
                horizontalLineToRelative(-6.78f)
                lineToRelative(2.74f, -2.82f)
                curveToRelative(-2.73f, -2.7f, -7.15f, -2.8f, -9.88f, -0.1f)
                curveToRelative(-2.73f, 2.71f, -2.73f, 7.08f, 0.0f, 9.79f)
                reflectiveCurveToRelative(7.15f, 2.71f, 9.88f, 0.0f)
                curveTo(18.32f, 15.65f, 19.0f, 14.08f, 19.0f, 12.1f)
                horizontalLineToRelative(2.0f)
                curveToRelative(0.0f, 1.98f, -0.88f, 4.55f, -2.64f, 6.29f)
                curveToRelative(-3.51f, 3.48f, -9.21f, 3.48f, -12.72f, 0.0f)
                curveToRelative(-3.5f, -3.47f, -3.53f, -9.11f, -0.02f, -12.58f)
                reflectiveCurveToRelative(9.14f, -3.47f, 12.65f, 0.0f)
                lineTo(21.0f, 3.0f)
                verticalLineTo(10.12f)
                close()
                moveTo(12.5f, 8.0f)
                verticalLineToRelative(4.25f)
                lineToRelative(3.5f, 2.08f)
                lineToRelative(-0.72f, 1.21f)
                lineTo(11.0f, 13.0f)
                verticalLineTo(8.0f)
                horizontalLineTo(12.5f)
                close()
            }
        }
    }

    val Menu: ImageVector by lazy {
        materialIcon(name = "Rounded.Menu") {
            materialPath {
                moveTo(4.0f, 18.0f)
                horizontalLineToRelative(16.0f)
                curveToRelative(0.55f, 0.0f, 1.0f, -0.45f, 1.0f, -1.0f)
                reflectiveCurveToRelative(-0.45f, -1.0f, -1.0f, -1.0f)
                lineTo(4.0f, 16.0f)
                curveToRelative(-0.55f, 0.0f, -1.0f, 0.45f, -1.0f, 1.0f)
                reflectiveCurveToRelative(0.45f, 1.0f, 1.0f, 1.0f)
                close()
                moveTo(4.0f, 13.0f)
                horizontalLineToRelative(16.0f)
                curveToRelative(0.55f, 0.0f, 1.0f, -0.45f, 1.0f, -1.0f)
                reflectiveCurveToRelative(-0.45f, -1.0f, -1.0f, -1.0f)
                lineTo(4.0f, 11.0f)
                curveToRelative(-0.55f, 0.0f, -1.0f, 0.45f, -1.0f, 1.0f)
                reflectiveCurveToRelative(0.45f, 1.0f, 1.0f, 1.0f)
                close()
                moveTo(3.0f, 7.0f)
                curveToRelative(0.0f, 0.55f, 0.45f, 1.0f, 1.0f, 1.0f)
                horizontalLineToRelative(16.0f)
                curveToRelative(0.55f, 0.0f, 1.0f, -0.45f, 1.0f, -1.0f)
                reflectiveCurveToRelative(-0.45f, -1.0f, -1.0f, -1.0f)
                lineTo(4.0f, 6.0f)
                curveToRelative(-0.55f, 0.0f, -1.0f, 0.45f, -1.0f, 1.0f)
                close()
            }
        }
    }

    val Group: ImageVector by lazy {
        materialIcon(name = "Rounded.Group") {
            materialPath {
                moveTo(16.0f, 11.0f)
                curveToRelative(1.66f, 0.0f, 2.99f, -1.34f, 2.99f, -3.0f)
                reflectiveCurveTo(17.66f, 5.0f, 16.0f, 5.0f)
                reflectiveCurveToRelative(-3.0f, 1.34f, -3.0f, 3.0f)
                reflectiveCurveToRelative(1.34f, 3.0f, 3.0f, 3.0f)
                close()
                moveTo(8.0f, 11.0f)
                curveToRelative(1.66f, 0.0f, 2.99f, -1.34f, 2.99f, -3.0f)
                reflectiveCurveTo(9.66f, 5.0f, 8.0f, 5.0f)
                reflectiveCurveTo(5.0f, 6.34f, 5.0f, 8.0f)
                reflectiveCurveToRelative(1.34f, 3.0f, 3.0f, 3.0f)
                close()
                moveTo(8.0f, 13.0f)
                curveToRelative(-2.33f, 0.0f, -7.0f, 1.17f, -7.0f, 3.5f)
                lineTo(1.0f, 18.0f)
                curveToRelative(0.0f, 0.55f, 0.45f, 1.0f, 1.0f, 1.0f)
                horizontalLineToRelative(12.0f)
                curveToRelative(0.55f, 0.0f, 1.0f, -0.45f, 1.0f, -1.0f)
                verticalLineToRelative(-1.5f)
                curveToRelative(0.0f, -2.33f, -4.67f, -3.5f, -7.0f, -3.5f)
                close()
                moveTo(16.0f, 13.0f)
                curveToRelative(-0.29f, 0.0f, -0.62f, 0.02f, -0.97f, 0.05f)
                curveToRelative(0.02f, 0.01f, 0.03f, 0.03f, 0.04f, 0.04f)
                curveToRelative(1.14f, 0.83f, 1.93f, 1.94f, 1.93f, 3.41f)
                lineTo(17.0f, 18.0f)
                curveToRelative(0.0f, 0.35f, -0.07f, 0.69f, -0.18f, 1.0f)
                lineTo(22.0f, 19.0f)
                curveToRelative(0.55f, 0.0f, 1.0f, -0.45f, 1.0f, -1.0f)
                verticalLineToRelative(-1.5f)
                curveToRelative(0.0f, -2.33f, -4.67f, -3.5f, -7.0f, -3.5f)
                close()
            }
        }
    }

    val Explore: ImageVector by lazy {
        materialIcon(name = "Rounded.Explore") {
            materialPath {
                moveTo(12.0f, 10.9f)
                curveToRelative(-0.61f, 0.0f, -1.1f, 0.49f, -1.1f, 1.1f)
                reflectiveCurveToRelative(0.49f, 1.1f, 1.1f, 1.1f)
                curveToRelative(0.61f, 0.0f, 1.1f, -0.49f, 1.1f, -1.1f)
                reflectiveCurveToRelative(-0.49f, -1.1f, -1.1f, -1.1f)
                close()
                moveTo(12.0f, 2.0f)
                curveTo(6.48f, 2.0f, 2.0f, 6.48f, 2.0f, 12.0f)
                reflectiveCurveToRelative(4.48f, 10.0f, 10.0f, 10.0f)
                reflectiveCurveToRelative(10.0f, -4.48f, 10.0f, -10.0f)
                reflectiveCurveTo(17.52f, 2.0f, 12.0f, 2.0f)
                close()
                moveTo(14.19f, 14.19f)
                lineTo(6.0f, 18.0f)
                lineToRelative(3.81f, -8.19f)
                lineTo(18.0f, 6.0f)
                lineToRelative(-3.81f, 8.19f)
                close()
            }
        }
    }

    val PersonSearch: ImageVector by lazy {
        materialIcon(name = "Rounded.PersonSearch") {
            materialPath {
                moveTo(10.0f, 8.0f)
                moveToRelative(-4.0f, 0.0f)
                arcToRelative(4.0f, 4.0f, 0.0f, true, true, 8.0f, 0.0f)
                arcToRelative(4.0f, 4.0f, 0.0f, true, true, -8.0f, 0.0f)
            }
            materialPath {
                moveTo(10.35f, 14.01f)
                curveTo(7.62f, 13.91f, 2.0f, 15.27f, 2.0f, 18.0f)
                verticalLineToRelative(1.0f)
                curveToRelative(0.0f, 0.55f, 0.45f, 1.0f, 1.0f, 1.0f)
                horizontalLineToRelative(8.54f)
                curveTo(9.07f, 17.24f, 10.31f, 14.11f, 10.35f, 14.01f)
                close()
            }
            materialPath {
                moveTo(19.43f, 18.02f)
                curveToRelative(0.47f, -0.8f, 0.7f, -1.77f, 0.48f, -2.82f)
                curveToRelative(-0.34f, -1.64f, -1.72f, -2.95f, -3.38f, -3.16f)
                curveToRelative(-2.63f, -0.34f, -4.85f, 1.87f, -4.5f, 4.5f)
                curveToRelative(0.22f, 1.66f, 1.52f, 3.04f, 3.16f, 3.38f)
                curveToRelative(1.05f, 0.22f, 2.02f, -0.01f, 2.82f, -0.48f)
                lineToRelative(1.86f, 1.86f)
                curveToRelative(0.39f, 0.39f, 1.02f, 0.39f, 1.41f, 0.0f)
                lineToRelative(0.0f, 0.0f)
                curveToRelative(0.39f, -0.39f, 0.39f, -1.02f, 0.0f, -1.41f)
                lineTo(19.43f, 18.02f)
                close()
                moveTo(16.0f, 18.0f)
                curveToRelative(-1.1f, 0.0f, -2.0f, -0.9f, -2.0f, -2.0f)
                curveToRelative(0.0f, -1.1f, 0.9f, -2.0f, 2.0f, -2.0f)
                reflectiveCurveToRelative(2.0f, 0.9f, 2.0f, 2.0f)
                curveTo(18.0f, 17.1f, 17.1f, 18.0f, 16.0f, 18.0f)
                close()
            }
        }
    }

    val Clear: ImageVector by lazy {
        materialIcon(name = "Filled.Clear") {
            materialPath {
                moveTo(19.0f, 6.41f)
                lineTo(17.59f, 5.0f)
                lineTo(12.0f, 10.59f)
                lineTo(6.41f, 5.0f)
                lineTo(5.0f, 6.41f)
                lineTo(10.59f, 12.0f)
                lineTo(5.0f, 17.59f)
                lineTo(6.41f, 19.0f)
                lineTo(12.0f, 13.41f)
                lineTo(17.59f, 19.0f)
                lineTo(19.0f, 17.59f)
                lineTo(13.41f, 12.0f)
                close()
            }
        }
    }

    val VisibilityOff: ImageVector by lazy {
        materialIcon(name = "Filled.VisibilityOff") {
            materialPath {
                moveTo(12.0f, 7.0f)
                curveToRelative(2.76f, 0.0f, 5.0f, 2.24f, 5.0f, 5.0f)
                curveToRelative(0.0f, 0.65f, -0.13f, 1.26f, -0.36f, 1.83f)
                lineToRelative(2.92f, 2.92f)
                curveToRelative(1.51f, -1.26f, 2.7f, -2.89f, 3.43f, -4.75f)
                curveToRelative(-1.73f, -4.39f, -6.0f, -7.5f, -11.0f, -7.5f)
                curveToRelative(-1.4f, 0.0f, -2.74f, 0.25f, -3.98f, 0.7f)
                lineToRelative(2.16f, 2.16f)
                curveTo(10.74f, 7.13f, 11.35f, 7.0f, 12.0f, 7.0f)
                close()
                moveTo(2.0f, 4.27f)
                lineToRelative(2.28f, 2.28f)
                lineToRelative(0.46f, 0.46f)
                curveTo(3.08f, 8.3f, 1.78f, 10.02f, 1.0f, 12.0f)
                curveToRelative(1.73f, 4.39f, 6.0f, 7.5f, 11.0f, 7.5f)
                curveToRelative(1.55f, 0.0f, 3.03f, -0.3f, 4.38f, -0.84f)
                lineToRelative(0.42f, 0.42f)
                lineTo(19.73f, 22.0f)
                lineTo(21.0f, 20.73f)
                lineTo(3.27f, 3.0f)
                lineTo(2.0f, 4.27f)
                close()
                moveTo(7.53f, 9.8f)
                lineToRelative(1.55f, 1.55f)
                curveToRelative(-0.05f, 0.21f, -0.08f, 0.43f, -0.08f, 0.65f)
                curveToRelative(0.0f, 1.66f, 1.34f, 3.0f, 3.0f, 3.0f)
                curveToRelative(0.22f, 0.0f, 0.44f, -0.03f, 0.65f, -0.08f)
                lineToRelative(1.55f, 1.55f)
                curveToRelative(-0.67f, 0.33f, -1.41f, 0.53f, -2.2f, 0.53f)
                curveToRelative(-2.76f, 0.0f, -5.0f, -2.24f, -5.0f, -5.0f)
                curveToRelative(0.0f, -0.79f, 0.2f, -1.53f, 0.53f, -2.2f)
                close()
                moveTo(11.84f, 9.02f)
                lineToRelative(3.15f, 3.15f)
                lineToRelative(0.02f, -0.16f)
                curveToRelative(0.0f, -1.66f, -1.34f, -3.0f, -3.0f, -3.0f)
                lineToRelative(-0.17f, 0.01f)
                close()
            }
        }
    }

    val Visibility: ImageVector by lazy {
        materialIcon(name = "Filled.Visibility") {
            materialPath {
                moveTo(12.0f, 4.5f)
                curveTo(7.0f, 4.5f, 2.73f, 7.61f, 1.0f, 12.0f)
                curveToRelative(1.73f, 4.39f, 6.0f, 7.5f, 11.0f, 7.5f)
                reflectiveCurveToRelative(9.27f, -3.11f, 11.0f, -7.5f)
                curveToRelative(-1.73f, -4.39f, -6.0f, -7.5f, -11.0f, -7.5f)
                close()
                moveTo(12.0f, 17.0f)
                curveToRelative(-2.76f, 0.0f, -5.0f, -2.24f, -5.0f, -5.0f)
                reflectiveCurveToRelative(2.24f, -5.0f, 5.0f, -5.0f)
                reflectiveCurveToRelative(5.0f, 2.24f, 5.0f, 5.0f)
                reflectiveCurveToRelative(-2.24f, 5.0f, -5.0f, 5.0f)
                close()
                moveTo(12.0f, 9.0f)
                curveToRelative(-1.66f, 0.0f, -3.0f, 1.34f, -3.0f, 3.0f)
                reflectiveCurveToRelative(1.34f, 3.0f, 3.0f, 3.0f)
                reflectiveCurveToRelative(3.0f, -1.34f, 3.0f, -3.0f)
                reflectiveCurveToRelative(-1.34f, -3.0f, -3.0f, -3.0f)
                close()
            }
        }
    }

    val ArrowBackIosNew: ImageVector by lazy {
        materialIcon(name = "Rounded.ArrowBackIosNew") {
            materialPath {
                moveTo(16.88f, 2.88f)
                lineTo(16.88f, 2.88f)
                curveToRelative(-0.49f, -0.49f, -1.28f, -0.49f, -1.77f, 0.0f)
                lineToRelative(-8.41f, 8.41f)
                curveToRelative(-0.39f, 0.39f, -0.39f, 1.02f, 0.0f, 1.41f)
                lineToRelative(8.41f, 8.41f)
                curveToRelative(0.49f, 0.49f, 1.28f, 0.49f, 1.77f, 0.0f)
                lineToRelative(0.0f, 0.0f)
                curveToRelative(0.49f, -0.49f, 0.49f, -1.28f, 0.0f, -1.77f)
                lineTo(9.54f, 12.0f)
                lineToRelative(7.35f, -7.35f)
                curveTo(17.37f, 4.16f, 17.37f, 3.37f, 16.88f, 2.88f)
                close()
            }
        }
    }

    val Block: ImageVector by lazy {
        materialIcon(name = "Rounded.Block") {
            materialPath {
                moveTo(12.0f, 2.0f)
                curveTo(6.48f, 2.0f, 2.0f, 6.48f, 2.0f, 12.0f)
                reflectiveCurveToRelative(4.48f, 10.0f, 10.0f, 10.0f)
                reflectiveCurveToRelative(10.0f, -4.48f, 10.0f, -10.0f)
                reflectiveCurveTo(17.52f, 2.0f, 12.0f, 2.0f)
                close()
                moveTo(4.0f, 12.0f)
                curveToRelative(0.0f, -4.42f, 3.58f, -8.0f, 8.0f, -8.0f)
                curveToRelative(1.85f, 0.0f, 3.55f, 0.63f, 4.9f, 1.69f)
                lineTo(5.69f, 16.9f)
                curveTo(4.63f, 15.55f, 4.0f, 13.85f, 4.0f, 12.0f)
                close()
                moveTo(12.0f, 20.0f)
                curveToRelative(-1.85f, 0.0f, -3.55f, -0.63f, -4.9f, -1.69f)
                lineTo(18.31f, 7.1f)
                curveTo(19.37f, 8.45f, 20.0f, 10.15f, 20.0f, 12.0f)
                curveToRelative(0.0f, 4.42f, -3.58f, 8.0f, -8.0f, 8.0f)
                close()
            }
        }
    }

    val Login: ImageVector by lazy {
        materialIcon(name = "Rounded.Login") {
            materialPath {
                moveTo(11.0f, 7.0f)
                lineTo(9.6f, 8.4f)
                curveToRelative(-0.39f, 0.39f, -0.39f, 1.02f, 0.0f, 1.41f)
                curveToRelative(0.39f, 0.39f, 1.02f, 0.39f, 1.41f, 0.0f)
                lineTo(12.0f, 8.83f)
                verticalLineToRelative(8.17f)
                curveToRelative(0.0f, 0.55f, 0.45f, 1.0f, 1.0f, 1.0f)
                reflectiveCurveToRelative(1.0f, -0.45f, 1.0f, -1.0f)
                verticalLineTo(8.83f)
                lineToRelative(0.99f, 0.99f)
                curveToRelative(0.39f, 0.39f, 1.02f, 0.39f, 1.41f, 0.0f)
                curveToRelative(0.39f, -0.39f, 0.39f, -1.02f, 0.0f, -1.41f)
                lineTo(14.0f, 6.0f)
                curveToRelative(-0.39f, -0.39f, -1.02f, -0.39f, -1.41f, 0.0f)
                curveTo(12.2f, 6.39f, 11.0f, 7.0f, 11.0f, 7.0f)
                close()
                moveTo(3.0f, 5.0f)
                verticalLineToRelative(14.0f)
                curveToRelative(0.0f, 1.1f, 0.9f, 2.0f, 2.0f, 2.0f)
                horizontalLineToRelative(14.0f)
                curveToRelative(1.1f, 0.0f, 2.0f, -0.9f, 2.0f, -2.0f)
                verticalLineTo(5.0f)
                curveToRelative(0.0f, -1.1f, -0.9f, -2.0f, -2.0f, -2.0f)
                horizontalLineTo(5.0f)
                curveTo(3.9f, 3.0f, 3.0f, 3.9f, 3.0f, 5.0f)
                close()
                moveTo(19.0f, 19.0f)
                horizontalLineTo(5.0f)
                verticalLineTo(5.0f)
                horizontalLineToRelative(14.0f)
                verticalLineTo(19.0f)
                close()
            }
        }
    }

    val Queue: ImageVector by lazy {
        materialIcon(name = "Rounded.QueuePlayNext") {
            materialPath {
                moveTo(21.0f, 3.0f)
                horizontalLineTo(3.0f)
                curveToRelative(-1.1f, 0.0f, -2.0f, 0.9f, -2.0f, 2.0f)
                verticalLineToRelative(12.0f)
                curveToRelative(0.0f, 1.1f, 0.9f, 2.0f, 2.0f, 2.0f)
                horizontalLineToRelative(5.0f)
                verticalLineToRelative(1.0f)
                curveToRelative(0.0f, 0.55f, 0.45f, 1.0f, 1.0f, 1.0f)
                horizontalLineToRelative(6.0f)
                curveToRelative(0.55f, 0.0f, 1.0f, -0.45f, 1.0f, -1.0f)
                verticalLineToRelative(-1.0f)
                horizontalLineToRelative(5.0f)
                curveToRelative(1.1f, 0.0f, 2.0f, -0.9f, 2.0f, -2.0f)
                verticalLineTo(5.0f)
                curveTo(23.0f, 3.9f, 22.1f, 3.0f, 21.0f, 3.0f)
                close()
                moveTo(21.0f, 17.0f)
                horizontalLineTo(3.0f)
                verticalLineTo(5.0f)
                horizontalLineToRelative(18.0f)
                verticalLineTo(17.0f)
                close()
                moveTo(16.0f, 11.0f)
                verticalLineToRelative(-1.0f)
                curveToRelative(0.0f, -0.55f, -0.45f, -1.0f, -1.0f, -1.0f)
                horizontalLineToRelative(-2.0f)
                verticalLineToRelative(2.0f)
                horizontalLineToRelative(-2.0f)
                verticalLineToRelative(2.0f)
                horizontalLineToRelative(2.0f)
                verticalLineToRelative(2.0f)
                horizontalLineToRelative(2.0f)
                curveToRelative(0.55f, 0.0f, 1.0f, -0.45f, 1.0f, -1.0f)
                verticalLineToRelative(-1.0f)
                horizontalLineToRelative(2.0f)
                verticalLineToRelative(1.0f)
                curveToRelative(0.0f, 0.55f, 0.45f, 1.0f, 1.0f, 1.0f)
                reflectiveCurveToRelative(1.0f, -0.45f, 1.0f, -1.0f)
                verticalLineToRelative(-2.0f)
                curveToRelative(0.0f, -0.55f, -0.45f, -1.0f, -1.0f, -1.0f)
                horizontalLineToRelative(-2.0f)
                verticalLineToRelative(-1.0f)
                horizontalLineToRelative(2.0f)
                curveToRelative(0.55f, 0.0f, 1.0f, -0.45f, 1.0f, -1.0f)
                reflectiveCurveToRelative(-0.45f, -1.0f, -1.0f, -1.0f)
                horizontalLineToRelative(-2.0f)
                close()
                moveTo(10.0f, 7.0f)
                horizontalLineTo(8.0f)
                verticalLineToRelative(2.0f)
                horizontalLineToRelative(6.0f)
                curveToRelative(0.55f, 0.0f, 1.0f, -0.45f, 1.0f, -1.0f)
                reflectiveCurveToRelative(0.45f, -1.0f, 1.0f, -1.0f)
                horizontalLineToRelative(2.0f)
                verticalLineToRelative(2.0f)
                horizontalLineToRelative(2.0f)
                curveToRelative(0.55f, 0.0f, 1.0f, -0.45f, 1.0f, -1.0f)
                verticalLineTo(8.0f)
                curveTo(11.0f, 7.45f, 10.55f, 7.0f, 10.0f, 7.0f)
                close()
            }
        }
    }

    val Check: ImageVector by lazy {
        materialIcon(name = "Rounded.Check") {
            materialPath {
                moveTo(9.0f, 16.17f)
                lineTo(5.53f, 12.7f)
                curveToRelative(-0.39f, -0.39f, -1.02f, -0.39f, -1.41f, 0.0f)
                curveToRelative(-0.39f, 0.39f, -0.39f, 1.02f, 0.0f, 1.41f)
                lineToRelative(4.18f, 4.18f)
                curveToRelative(0.39f, 0.39f, 1.02f, 0.39f, 1.41f, 0.0f)
                lineTo(19.88f, 8.12f)
                curveToRelative(0.39f, -0.39f, 0.39f, -1.02f, 0.0f, -1.41f)
                curveToRelative(-0.39f, -0.39f, -1.02f, -0.39f, -1.41f, 0.0f)
                lineTo(9.0f, 16.17f)
                close()
            }
        }
    }

    val CheckCircle: ImageVector by lazy {
        materialIcon(name = "Rounded.CheckCircle") {
            materialPath {
                moveTo(12.0f, 2.0f)
                curveTo(6.48f, 2.0f, 2.0f, 6.48f, 2.0f, 12.0f)
                reflectiveCurveToRelative(4.48f, 10.0f, 10.0f, 10.0f)
                reflectiveCurveToRelative(10.0f, -4.48f, 10.0f, -10.0f)
                reflectiveCurveTo(17.52f, 2.0f, 12.0f, 2.0f)
                close()
                moveTo(9.29f, 16.29f)
                lineTo(5.7f, 12.7f)
                curveToRelative(-0.39f, -0.39f, -0.39f, -1.02f, 0.0f, -1.41f)
                curveToRelative(0.39f, -0.39f, 1.02f, -0.39f, 1.41f, 0.0f)
                lineTo(10.0f, 14.17f)
                lineToRelative(6.88f, -6.88f)
                curveToRelative(0.39f, -0.39f, 1.02f, -0.39f, 1.41f, 0.0f)
                curveToRelative(0.39f, 0.39f, 0.39f, 1.02f, 0.0f, 1.41f)
                lineToRelative(-7.59f, 7.59f)
                curveToRelative(-0.38f, 0.39f, -1.02f, 0.39f, -1.41f, 0.0f)
                close()
            }
        }
    }

    val Hot: ImageVector by lazy {
        ImageVector.Builder(
            name = "Mode_heat",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 960f,
            viewportHeight = 960f
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
                moveTo(240f, 560f)
                quadToRelative(0f, 52f, 21f, 98.5f)
                reflectiveQuadToRelative(60f, 81.5f)
                quadToRelative(-1f, -5f, -1f, -9f)
                verticalLineToRelative(-9f)
                quadToRelative(0f, -32f, 12f, -60f)
                reflectiveQuadToRelative(35f, -51f)
                lineToRelative(113f, -111f)
                lineToRelative(113f, 111f)
                quadToRelative(23f, 23f, 35f, 51f)
                reflectiveQuadToRelative(12f, 60f)
                verticalLineToRelative(9f)
                quadToRelative(0f, 4f, -1f, 9f)
                quadToRelative(39f, -35f, 60f, -81.5f)
                reflectiveQuadToRelative(21f, -98.5f)
                quadToRelative(0f, -50f, -18.5f, -94.5f)
                reflectiveQuadTo(648f, 386f)
                quadToRelative(-20f, 13f, -42f, 19.5f)
                reflectiveQuadToRelative(-45f, 6.5f)
                quadToRelative(-62f, 0f, -107.5f, -41f)
                reflectiveQuadTo(401f, 270f)
                quadToRelative(-39f, 33f, -69f, 68.5f)
                reflectiveQuadToRelative(-50.5f, 72f)
                reflectiveQuadToRelative(-31f, 74.5f)
                reflectiveQuadToRelative(-10.5f, 75f)
                moveToRelative(240f, 52f)
                lineToRelative(-57f, 56f)
                quadToRelative(-11f, 11f, -17f, 25f)
                reflectiveQuadToRelative(-6f, 29f)
                quadToRelative(0f, 32f, 23.5f, 55f)
                reflectiveQuadToRelative(56.5f, 23f)
                reflectiveQuadToRelative(56.5f, -23f)
                reflectiveQuadToRelative(23.5f, -55f)
                quadToRelative(0f, -16f, -6f, -29.5f)
                reflectiveQuadTo(537f, 668f)
                close()
                moveToRelative(0f, -492f)
                verticalLineToRelative(132f)
                quadToRelative(0f, 34f, 23.5f, 57f)
                reflectiveQuadToRelative(57.5f, 23f)
                quadToRelative(18f, 0f, 33.5f, -7.5f)
                reflectiveQuadTo(622f, 302f)
                lineToRelative(18f, -22f)
                quadToRelative(74f, 42f, 117f, 117f)
                reflectiveQuadToRelative(43f, 163f)
                quadToRelative(0f, 134f, -93f, 227f)
                reflectiveQuadTo(480f, 880f)
                reflectiveQuadToRelative(-227f, -93f)
                reflectiveQuadToRelative(-93f, -227f)
                quadToRelative(0f, -129f, 86.5f, -245f)
                reflectiveQuadTo(480f, 120f)
            }
        }.build()
    }

    val Trending: ImageVector by lazy {
        ImageVector.Builder(
            name = "Trending_up",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 960f,
            viewportHeight = 960f
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
                moveTo(136f, 720f)
                lineToRelative(-56f, -56f)
                lineToRelative(296f, -298f)
                lineToRelative(160f, 160f)
                lineToRelative(208f, -206f)
                horizontalLineTo(640f)
                verticalLineToRelative(-80f)
                horizontalLineToRelative(240f)
                verticalLineToRelative(240f)
                horizontalLineToRelative(-80f)
                verticalLineToRelative(-104f)
                lineTo(536f, 640f)
                lineTo(376f, 480f)
                close()
            }
        }.build()
    }

    val DateRange: ImageVector by lazy {
        materialIcon(name = "Rounded.DateRange") {
            materialPath {
                moveTo(19.0f, 4.0f)
                horizontalLineToRelative(-1.0f)
                lineTo(18.0f, 3.0f)
                curveToRelative(0.0f, -0.55f, -0.45f, -1.0f, -1.0f, -1.0f)
                reflectiveCurveToRelative(-1.0f, 0.45f, -1.0f, 1.0f)
                verticalLineToRelative(1.0f)
                lineTo(8.0f, 4.0f)
                lineTo(8.0f, 3.0f)
                curveToRelative(0.0f, -0.55f, -0.45f, -1.0f, -1.0f, -1.0f)
                reflectiveCurveToRelative(-1.0f, 0.45f, -1.0f, 1.0f)
                verticalLineToRelative(1.0f)
                lineTo(5.0f, 4.0f)
                curveToRelative(-1.11f, 0.0f, -1.99f, 0.9f, -1.99f, 2.0f)
                lineTo(3.0f, 20.0f)
                curveToRelative(0.0f, 1.1f, 0.89f, 2.0f, 2.0f, 2.0f)
                horizontalLineToRelative(14.0f)
                curveToRelative(1.1f, 0.0f, 2.0f, -0.9f, 2.0f, -2.0f)
                lineTo(21.0f, 6.0f)
                curveToRelative(0.0f, -1.1f, -0.9f, -2.0f, -2.0f, -2.0f)
                close()
                moveTo(19.0f, 20.0f)
                lineTo(5.0f, 20.0f)
                lineTo(5.0f, 10.0f)
                horizontalLineToRelative(14.0f)
                verticalLineToRelative(10.0f)
                close()
                moveTo(19.0f, 8.0f)
                lineTo(5.0f, 8.0f)
                lineTo(5.0f, 6.0f)
                horizontalLineToRelative(14.0f)
                verticalLineToRelative(2.0f)
                close()
                moveTo(9.0f, 14.0f)
                horizontalLineToRelative(2.0f)
                verticalLineToRelative(2.0f)
                lineTo(9.0f, 16.0f)
                verticalLineToRelative(-2.0f)
                close()
                moveTo(13.0f, 14.0f)
                horizontalLineToRelative(2.0f)
                verticalLineToRelative(2.0f)
                horizontalLineToRelative(-2.0f)
                verticalLineToRelative(-2.0f)
                close()
                moveTo(9.0f, 11.0f)
                horizontalLineToRelative(2.0f)
                verticalLineToRelative(2.0f)
                lineTo(9.0f, 13.0f)
                verticalLineToRelative(-2.0f)
                close()
                moveTo(13.0f, 11.0f)
                horizontalLineToRelative(2.0f)
                verticalLineToRelative(2.0f)
                horizontalLineToRelative(-2.0f)
                verticalLineToRelative(-2.0f)
                close()
            }
        }
    }

    val Dashboard: ImageVector by lazy {
        materialIcon(name = "Rounded.Dashboard") {
            materialPath {
                moveTo(4.0f, 13.0f)
                horizontalLineToRelative(6.0f)
                curveToRelative(0.55f, 0.0f, 1.0f, -0.45f, 1.0f, -1.0f)
                verticalLineTo(4.0f)
                curveToRelative(0.0f, -0.55f, -0.45f, -1.0f, -1.0f, -1.0f)
                horizontalLineTo(4.0f)
                curveTo(3.45f, 3.0f, 3.0f, 3.45f, 3.0f, 4.0f)
                verticalLineToRelative(8.0f)
                curveTo(3.0f, 12.55f, 3.45f, 13.0f, 4.0f, 13.0f)
                close()
                moveTo(4.0f, 21.0f)
                horizontalLineToRelative(6.0f)
                curveToRelative(0.55f, 0.0f, 1.0f, -0.45f, 1.0f, -1.0f)
                verticalLineToRelative(-4.0f)
                curveToRelative(0.0f, -0.55f, -0.45f, -1.0f, -1.0f, -1.0f)
                horizontalLineTo(4.0f)
                curveToRelative(-0.55f, 0.0f, -1.0f, 0.45f, -1.0f, 1.0f)
                verticalLineToRelative(4.0f)
                curveTo(3.0f, 20.55f, 3.45f, 21.0f, 4.0f, 21.0f)
                close()
                moveTo(14.0f, 21.0f)
                horizontalLineToRelative(6.0f)
                curveToRelative(0.55f, 0.0f, 1.0f, -0.45f, 1.0f, -1.0f)
                verticalLineToRelative(-8.0f)
                curveToRelative(0.0f, -0.55f, -0.45f, -1.0f, -1.0f, -1.0f)
                horizontalLineTo(14.0f)
                curveToRelative(-0.55f, 0.0f, -1.0f, 0.45f, -1.0f, 1.0f)
                verticalLineToRelative(8.0f)
                curveTo(13.0f, 20.55f, 13.45f, 21.0f, 14.0f, 21.0f)
                close()
                moveTo(14.0f, 13.0f)
                horizontalLineToRelative(6.0f)
                curveToRelative(0.55f, 0.0f, 1.0f, -0.45f, 1.0f, -1.0f)
                verticalLineTo(4.0f)
                curveToRelative(0.0f, -0.55f, -0.45f, -1.0f, -1.0f, -1.0f)
                horizontalLineTo(14.0f)
                curveToRelative(-0.55f, 0.0f, -1.0f, 0.45f, -1.0f, 1.0f)
                verticalLineToRelative(8.0f)
                curveTo(13.0f, 12.55f, 13.45f, 13.0f, 14.0f, 13.0f)
                close()
            }
        }
    }

    val Close: ImageVector by lazy {
        materialIcon(name = "Filled.Close") {
            materialPath {
                moveTo(19.0f, 6.41f)
                lineTo(17.59f, 5.0f)
                lineTo(12.0f, 10.59f)
                lineTo(6.41f, 5.0f)
                lineTo(5.0f, 6.41f)
                lineTo(10.59f, 12.0f)
                lineTo(5.0f, 17.59f)
                lineTo(6.41f, 19.0f)
                lineTo(12.0f, 13.41f)
                lineTo(17.59f, 19.0f)
                lineTo(19.0f, 17.59f)
                lineTo(13.41f, 12.0f)
                close()
            }
        }
    }
}

