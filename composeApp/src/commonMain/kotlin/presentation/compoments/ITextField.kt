package io.github.vrcmteam.vrcm.presentation.compoments

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusState
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import io.github.vrcmteam.vrcm.presentation.designsystem.AppIcon
import io.github.vrcmteam.vrcm.presentation.designsystem.AppShapes
import io.github.vrcmteam.vrcm.presentation.designsystem.AppText
import io.github.vrcmteam.vrcm.presentation.designsystem.AppTheme
import io.github.vrcmteam.vrcm.presentation.supports.AppIcons
import io.github.vrcmteam.vrcm.presentation.supports.PasswordMissEndVisualTransformation

@Composable
fun ITextField(
    modifier: Modifier = Modifier,
    painter: Painter,
    hintText: String,
    textValue: String,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    onValueChange: (String) -> Unit,
    supportingText: @Composable (() -> Unit)? = null
) =  ITextField(
    modifier = modifier,
    leadingIcon = {
        AppIcon(
            painter = painter,
            contentDescription = "leadingIcon",
            tint = AppTheme.colors.secondaryLabel
        )
    },
    hintText = hintText,
    textValue = textValue,
    keyboardOptions = keyboardOptions,
    keyboardActions = keyboardActions,
    onValueChange = onValueChange,
    supportingText = supportingText
)


@Composable
fun ITextField(
    modifier: Modifier = Modifier,
    leadingIcon: @Composable (() -> Unit)? = null,
    hintText: String,
    textValue: String,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    onValueChange: (String) -> Unit,
    supportingText: @Composable (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(modifier)
            .background(
                color = AppTheme.colors.fill,
                shape = AppShapes.m
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leadingIcon != null) {
            Box(
                modifier = Modifier
                    .padding(start = 12.dp, end = 6.dp, top = 12.dp, bottom = 12.dp)
            ) {
                leadingIcon()
            }
        }

        BasicTextField(
            modifier = Modifier
                .weight(1f)
                .padding(end = 6.dp, top = 12.dp, bottom = 12.dp),
            value = textValue,
            decorationBox = { innerTextField ->
                Box {
                    if (textValue.isEmpty()) {
                        AppText(
                            text = hintText,
                            style = AppTheme.type.body,
                            color = AppTheme.colors.tertiaryLabel
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
            textStyle = AppTheme.type.body.copy(color = AppTheme.colors.label),
            cursorBrush = SolidColor(AppTheme.colors.tint)
        )
        // 显示辅助文本
        supportingText?.let {
            Box(
                modifier = Modifier
                    .padding( end = 6.dp)
            ) {
                it()
            }
        }
        if (textValue.isNotEmpty()) {
            AppIcon(
                modifier = Modifier
                    .padding(end = 12.dp)
                    .clip(CircleShape)
                    .clickable { onValueChange("") },
                imageVector = AppIcons.Clear,
                contentDescription = "ClearIcon",
                tint = AppTheme.colors.tertiaryLabel
            )
        }

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
            .background(
                color = AppTheme.colors.fill,
                shape = AppShapes.m
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(
            modifier = Modifier
                .padding(start = 12.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
            imageVector = imageVector,
            contentDescription = "",
            tint = AppTheme.colors.secondaryLabel
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
                        AppText(
                            text = hintText,
                            style = AppTheme.type.body,
                            color = AppTheme.colors.tertiaryLabel
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
            textStyle = AppTheme.type.body.copy(color = AppTheme.colors.label),
            cursorBrush = SolidColor(AppTheme.colors.tint)
        )
        if (textValue.isNotEmpty() && keyboardOptions.keyboardType == KeyboardType.Password) {
            AppIcon(
                modifier = Modifier
                    .padding(end = 12.dp, top = 12.dp, bottom = 12.dp)
                    .clip(CircleShape)
                    .clickable { isShowPassword = !isShowPassword },
                imageVector =  if(isShowPassword) AppIcons.VisibilityOff
                else AppIcons.Visibility,
                contentDescription = "ShowPasswordIcon",
                tint = AppTheme.colors.tertiaryLabel
            )
        }
        if (textValue.isNotEmpty()) {
            AppIcon(
                modifier = Modifier
                    .padding(end = 12.dp, top = 12.dp, bottom = 12.dp)
                    .clip(CircleShape)
                    .clickable { onValueChange("") },
                imageVector = AppIcons.Clear,
                contentDescription = "ClearIcon",
                tint = AppTheme.colors.tertiaryLabel
            )
        }

    }
}
