package io.github.vrcmteam.vrcm.presentation.compoments

import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import io.github.vrcmteam.vrcm.presentation.settings.locale.strings
import io.github.vrcmteam.vrcm.presentation.supports.AppIcons

@Composable
fun SearchTextField(
    modifier: Modifier = Modifier,
    value: String,
    placeholder: String = strings.fiendListPagerSearch,
    onValueChange: (String) -> Unit
) {
    val focusManager = LocalFocusManager.current
    ITextField(
        modifier = modifier,
        painter = rememberVectorPainter(AppIcons.Search),
        hintText = placeholder,
        textValue = value,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
        onValueChange = onValueChange
    )
}