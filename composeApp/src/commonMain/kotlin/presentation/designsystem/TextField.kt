package io.github.vrcmteam.vrcm.presentation.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp

/**
 * 输入框（HIG Text fields）：标签在框的上方而不是浮在边框里；框是圆角填充块、无描边，
 * 聚焦时描一圈强调色，出错时描红并把说明文字变红。[modifier] 作用在输入节点本身（焦点、测试标签、语义都落在这里）。
 */
@Composable
fun AppTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    textStyle: TextStyle = AppTheme.type.body,
    label: (@Composable () -> Unit)? = null,
    placeholder: (@Composable () -> Unit)? = null,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    supportingText: (@Composable () -> Unit)? = null,
    isError: Boolean = false,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    singleLine: Boolean = false,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    minLines: Int = 1,
    interactionSource: MutableInteractionSource? = null,
    shape: Shape = AppShapes.s,
    containerColor: Color = AppTheme.colors.secondaryFill,
) {
    val source = interactionSource ?: remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    val c = AppTheme.colors
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        enabled = enabled,
        readOnly = readOnly,
        textStyle = textStyle.merge(color = c.label),
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        singleLine = singleLine,
        maxLines = maxLines,
        minLines = minLines,
        visualTransformation = visualTransformation,
        interactionSource = source,
        cursorBrush = SolidColor(if (isError) c.destructive else c.tint),
        decorationBox = { innerTextField ->
            TextFieldDecoration(
                isEmpty = value.isEmpty(),
                innerTextField = innerTextField,
                enabled = enabled,
                focused = focused,
                isError = isError,
                textStyle = textStyle,
                label = label,
                placeholder = placeholder,
                leadingIcon = leadingIcon,
                trailingIcon = trailingIcon,
                supportingText = supportingText,
                shape = shape,
                containerColor = containerColor,
            )
        },
    )
}

/** 需要自己控制选区 / 光标时用的 [TextFieldValue] 版本。 */
@Composable
fun AppTextField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    textStyle: TextStyle = AppTheme.type.body,
    label: (@Composable () -> Unit)? = null,
    placeholder: (@Composable () -> Unit)? = null,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    supportingText: (@Composable () -> Unit)? = null,
    isError: Boolean = false,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    singleLine: Boolean = false,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    minLines: Int = 1,
    interactionSource: MutableInteractionSource? = null,
    shape: Shape = AppShapes.s,
    containerColor: Color = AppTheme.colors.secondaryFill,
) {
    val source = interactionSource ?: remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    val c = AppTheme.colors
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        enabled = enabled,
        readOnly = readOnly,
        textStyle = textStyle.merge(color = c.label),
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        singleLine = singleLine,
        maxLines = maxLines,
        minLines = minLines,
        visualTransformation = visualTransformation,
        interactionSource = source,
        cursorBrush = SolidColor(if (isError) c.destructive else c.tint),
        decorationBox = { innerTextField ->
            TextFieldDecoration(
                isEmpty = value.text.isEmpty(),
                innerTextField = innerTextField,
                enabled = enabled,
                focused = focused,
                isError = isError,
                textStyle = textStyle,
                label = label,
                placeholder = placeholder,
                leadingIcon = leadingIcon,
                trailingIcon = trailingIcon,
                supportingText = supportingText,
                shape = shape,
                containerColor = containerColor,
            )
        },
    )
}

@Composable
private fun TextFieldDecoration(
    isEmpty: Boolean,
    innerTextField: @Composable () -> Unit,
    enabled: Boolean,
    focused: Boolean,
    isError: Boolean,
    textStyle: TextStyle,
    label: (@Composable () -> Unit)?,
    placeholder: (@Composable () -> Unit)?,
    leadingIcon: (@Composable () -> Unit)?,
    trailingIcon: (@Composable () -> Unit)?,
    supportingText: (@Composable () -> Unit)?,
    shape: Shape,
    containerColor: Color,
) {
    val c = AppTheme.colors
    val outline = when {
        isError -> c.destructive
        focused -> c.tint
        else -> Color.Transparent
    }
    Column(
        Modifier.enabledAlpha(enabled),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (label != null) {
            Box(Modifier.padding(horizontal = 4.dp)) {
                ProvideContentColor(if (isError) c.destructive else c.secondaryLabel, AppTheme.type.footnote, label)
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(containerColor)
                .border(1.dp, outline, shape)
                .defaultMinSize(minHeight = 44.dp)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (leadingIcon != null) ProvideContentColor(c.secondaryLabel, content = leadingIcon)
            Box(Modifier.weight(1f)) {
                if (isEmpty && placeholder != null) {
                    ProvideContentColor(c.tertiaryLabel, textStyle, placeholder)
                }
                innerTextField()
            }
            if (trailingIcon != null) ProvideContentColor(c.secondaryLabel, content = trailingIcon)
        }
        if (supportingText != null) {
            Box(Modifier.padding(horizontal = 4.dp)) {
                ProvideContentColor(if (isError) c.destructive else c.secondaryLabel, AppTheme.type.caption1, supportingText)
            }
        }
    }
}
