package io.github.vrcmteam.vrcm.presentation.supports

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation

class PasswordMissEndVisualTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        return TransformedText(
            AnnotatedString(
                if (text.text.isNotEmpty()) '\u2022'.toString().repeat(text.text.length - 1) + text.text.last()
                else ""
            ), OffsetMapping.Identity
        )
    }
}