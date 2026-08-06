package io.github.vrcmteam.vrcm.presentation.screens.gallery.editor

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertTrue

class PrintImageEditorScreenSerializationTest {
    @Test
    fun navigationKeySerializesWithoutSourceBytesOrBitmap() {
        val screen = PrintImageEditorScreen(
            sessionId = "print-editor-1",
        )

        val serialized = Json.encodeToString(screen)

        assertTrue(serialized.length < 1_024)
    }
}
