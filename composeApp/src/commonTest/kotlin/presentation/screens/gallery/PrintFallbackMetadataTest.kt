package io.github.vrcmteam.vrcm.presentation.screens.gallery

import io.github.vrcmteam.vrcm.network.api.prints.data.PrintData
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PrintFallbackMetadataTest {
    @Test
    fun printWithoutLocationRetainsAuthorAndCaptureTime() {
        val print = PrintData(
            id = "prnt_uploaded",
            authorName = "Photographer",
            timestamp = "2026-08-18T12:00:00Z",
        )

        assertEquals(
            PrintFallbackMetadata(
                authorName = "Photographer",
                timestamp = "2026-08-18T12:00:00Z",
            ),
            print.fallbackMetadata(),
        )
    }

    @Test
    fun missingMetadataFieldsAreHandledIndependently() {
        val print = PrintData(
            id = "prnt_saved",
            authorName = "  ",
            createdAt = "2026-08-18T12:00:00Z",
        )

        assertEquals(
            PrintFallbackMetadata(
                authorName = null,
                timestamp = "2026-08-18T12:00:00Z",
            ),
            print.fallbackMetadata(),
        )
    }

    @Test
    fun authorIsRetainedWhenCaptureTimeIsMissing() {
        val print = PrintData(
            id = "prnt_legacy",
            authorName = "Photographer",
        )

        assertEquals(
            PrintFallbackMetadata(
                authorName = "Photographer",
                timestamp = null,
            ),
            print.fallbackMetadata(),
        )
    }

    @Test
    fun printWithLocationKeepsMetadataFromItsExistingCanvas() {
        val print = PrintData(
            id = "prnt_camera",
            worldName = "Test World",
            authorName = "Photographer",
            timestamp = "2026-08-18T12:00:00Z",
        )

        assertNull(print.fallbackMetadata())
    }
}
