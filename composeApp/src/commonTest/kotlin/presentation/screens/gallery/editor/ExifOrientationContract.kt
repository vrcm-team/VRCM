package io.github.vrcmteam.vrcm.presentation.screens.gallery.editor

import kotlin.math.abs
import kotlin.test.assertTrue

internal val EXIF_ORIENTATION_FIXTURE_SIZE = ImageSize(width = 160, height = 96)

internal enum class ExifFixtureColor(val red: Int, val green: Int, val blue: Int) {
    RED(255, 0, 0),
    GREEN(0, 255, 0),
    BLUE(0, 0, 255),
    YELLOW(255, 255, 0),
}

internal enum class ExifFixtureCorner(
    private val xNumerator: Int,
    private val yNumerator: Int,
) {
    TOP_LEFT(1, 1),
    TOP_RIGHT(3, 1),
    BOTTOM_LEFT(1, 3),
    BOTTOM_RIGHT(3, 3),
    ;

    fun samplePoint(size: ImageSize): Pair<Int, Int> =
        size.width * xNumerator / 4 to size.height * yNumerator / 4
}

internal data class ExifExpectedCorner(
    val corner: ExifFixtureCorner,
    val color: ExifFixtureColor,
)

internal data class ExifOrientationContract(
    val orientation: Int,
    private val colors: List<ExifFixtureColor>,
) {
    init {
        require(orientation in 1..8)
        require(colors.size == ExifFixtureCorner.entries.size)
    }

    val expectedCorners: List<ExifExpectedCorner> =
        ExifFixtureCorner.entries.zip(colors, ::ExifExpectedCorner)

    fun orientedSize(rawSize: ImageSize): ImageSize = if (orientation in 5..8) {
        ImageSize(rawSize.height, rawSize.width)
    } else {
        rawSize
    }
}

internal val EXIF_ORIENTATION_CONTRACTS = listOf(
    ExifOrientationContract(1, colors("RGBY")),
    ExifOrientationContract(2, colors("GRYB")),
    ExifOrientationContract(3, colors("YBGR")),
    ExifOrientationContract(4, colors("BYRG")),
    ExifOrientationContract(5, colors("RBGY")),
    ExifOrientationContract(6, colors("BRYG")),
    ExifOrientationContract(7, colors("YGBR")),
    ExifOrientationContract(8, colors("GYRB")),
)

internal fun exifFixtureColorAt(x: Int, y: Int, size: ImageSize): ExifFixtureColor = when {
    x < size.width / 2 && y < size.height / 2 -> ExifFixtureColor.RED
    x >= size.width / 2 && y < size.height / 2 -> ExifFixtureColor.GREEN
    x < size.width / 2 -> ExifFixtureColor.BLUE
    else -> ExifFixtureColor.YELLOW
}

internal fun assertExifColorNear(
    expected: ExifFixtureColor,
    actualRed: Int,
    actualGreen: Int,
    actualBlue: Int,
    tolerance: Int = 45,
    context: String,
) {
    assertTrue(
        abs(expected.red - actualRed) <= tolerance &&
                abs(expected.green - actualGreen) <= tolerance &&
                abs(expected.blue - actualBlue) <= tolerance,
        "$context: expected=${expected.name} " +
                "(${expected.red}/${expected.green}/${expected.blue}), " +
                "actual=$actualRed/$actualGreen/$actualBlue, tolerance=$tolerance",
    )
}

internal fun ByteArray.withExifOrientation(orientation: Int): ByteArray {
    require(orientation in 1..8)
    require(size >= 2 && this[0] == 0xFF.toByte() && this[1] == 0xD8.toByte()) {
        "EXIF orientation can only be inserted into JPEG data"
    }
    val segment = byteArrayOf(
        0xFF.toByte(), 0xE1.toByte(), 0x00, 0x22,
        0x45, 0x78, 0x69, 0x66, 0x00, 0x00,
        0x49, 0x49, 0x2A, 0x00,
        0x08, 0x00, 0x00, 0x00,
        0x01, 0x00,
        0x12, 0x01, 0x03, 0x00, 0x01, 0x00, 0x00, 0x00,
        orientation.toByte(), 0x00, 0x00, 0x00,
        0x00, 0x00, 0x00, 0x00,
    )
    return copyOfRange(0, 2) + segment + copyOfRange(2, size)
}

private fun colors(order: String): List<ExifFixtureColor> = order.map { symbol ->
    when (symbol) {
        'R' -> ExifFixtureColor.RED
        'G' -> ExifFixtureColor.GREEN
        'B' -> ExifFixtureColor.BLUE
        'Y' -> ExifFixtureColor.YELLOW
        else -> error("Unknown EXIF fixture color: $symbol")
    }
}
