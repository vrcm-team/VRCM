package io.github.vrcmteam.vrcm.presentation.compoments

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusState
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import io.github.vrcmteam.vrcm.presentation.supports.AppIcons
import io.github.vrcmteam.vrcm.presentation.supports.PasswordMissEndVisualTransformation

@Composable
fun ITextField(
    modifier: Modifier = Modifier,
    imageVector: ImageVector,
    hintText: String,
    textValue: String,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    onValueChange: (String) -> Unit
) {
    Row(
        modifier = Modifier

            .fillMaxWidth()
            .then(modifier)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
                shape = MaterialTheme.shapes.large
            )
            .background(
                color = MaterialTheme.colorScheme.surface,
                shape = MaterialTheme.shapes.large
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            modifier = Modifier
                .padding(start = 12.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
            imageVector = imageVector,
            contentDescription = "",
            tint = MaterialTheme.colorScheme.primary
        )
        BasicTextField(
            modifier = Modifier
                .weight(1f)
                .padding(end = 12.dp, top = 12.dp, bottom = 12.dp),
            value = textValue,
            decorationBox = { innerTextField ->
                Box {
                    if (textValue.isEmpty()) {
                        Text(
                            text = hintText,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                    innerTextField()
                }
            },
            onValueChange = { onValueChange(it) },
            singleLine = true,
            visualTransformation =  VisualTransformation.None,
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.onSurface)
        )
        if (textValue.isEmpty()) return
        Icon(
            modifier = Modifier
                .padding(end = 12.dp, top = 12.dp, bottom = 12.dp)
                .clip(CircleShape)
                .clickable { onValueChange("") },
            imageVector = AppIcons.Clear,
            contentDescription = "ClearIcon",
            tint = MaterialTheme.colorScheme.outlineVariant
        )

    }
}


@Composable
fun IPasswordField(
    modifier: Modifier = Modifier,
    imageVector: ImageVector,
    hintText: String,
    textValue: String,
    isFocus: Boolean,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    onFocusChanged: (FocusState) -> Unit = {},
    onValueChange: (String) -> Unit
) {
    val visualPassword = if (isFocus) {
        PasswordMissEndVisualTransformation()
    } else {
        PasswordVisualTransformation()
    }
    var isShowPassword by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(modifier)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
                shape = MaterialTheme.shapes.large
            )
            .background(
                color = MaterialTheme.colorScheme.surface,
                shape = MaterialTheme.shapes.large
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            modifier = Modifier
                .padding(start = 12.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
            imageVector = imageVector,
            contentDescription = "",
            tint = MaterialTheme.colorScheme.primary
        )
        BasicTextField(
            modifier = Modifier
                .weight(1f)
                .onFocusChanged(onFocusChanged)
                .padding(end = 12.dp, top = 12.dp, bottom = 12.dp),
            value = textValue,
            decorationBox = { innerTextField ->
                Box {
                    if (textValue.isEmpty()) {
                        Text(
                            text = hintText,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                    innerTextField()

                }
            },
            onValueChange = { onValueChange(it.trim()) },
            singleLine = true,
            visualTransformation = if (isShowPassword) {
                VisualTransformation.None
            }else visualPassword,
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.onSurface)
        )
        if (textValue.isEmpty()) return
        if (keyboardOptions.keyboardType == KeyboardType.Password){
            Icon(
                modifier = Modifier
                    .padding(end = 12.dp, top = 12.dp, bottom = 12.dp)
                    .clip(CircleShape)
                    .clickable { isShowPassword = !isShowPassword },
                imageVector =  if(isShowPassword) AppIcons.VisibilityOff
                else AppIcons.Visibility,
                contentDescription = "ClearIcon",
                tint = MaterialTheme.colorScheme.outlineVariant
            )
        }
        Icon(
            modifier = Modifier
                .padding(end = 12.dp, top = 12.dp, bottom = 12.dp)
                .clip(CircleShape)
                .clickable { onValueChange("") },
            imageVector = AppIcons.Clear,
            contentDescription = "ClearIcon",
            tint = MaterialTheme.colorScheme.outlineVariant
        )

    }
}

