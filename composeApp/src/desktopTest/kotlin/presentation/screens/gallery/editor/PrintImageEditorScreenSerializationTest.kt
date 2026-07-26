package io.github.vrcmteam.vrcm.presentation.screens.gallery.editor

import java.io.ByteArrayOutputStream
import java.io.ObjectOutputStream
import kotlin.test.Test
import kotlin.test.assertTrue

class PrintImageEditorScreenSerializationTest {
    @Test
    fun voyagerScreenSerializesWithoutSourceBytesOrBitmap() {
        val screen = PrintImageEditorScreen(
            sessionId = "print-editor-1",
        )

        val serialized = ByteArrayOutputStream().use { bytes ->
            ObjectOutputStream(bytes).use { it.writeObject(screen) }
            bytes.toByteArray()
        }

        assertTrue(serialized.size < 1_024)
    }
}
