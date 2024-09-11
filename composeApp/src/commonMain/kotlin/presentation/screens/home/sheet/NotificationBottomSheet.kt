package io.github.vrcmteam.vrcm.presentation.screens.home.sheet

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.vrcmteam.vrcm.network.api.notification.data.NotificationDataV2
import io.github.vrcmteam.vrcm.network.api.notification.data.ResponseData
import io.github.vrcmteam.vrcm.presentation.compoments.ABottomSheet
import io.github.vrcmteam.vrcm.presentation.compoments.AImage
import io.github.vrcmteam.vrcm.presentation.extensions.getInsetPadding

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationBottomSheet(
    bottomSheetIsVisible: Boolean,
    notificationList: List< NotificationDataV2>,
    sheetState: SheetState = rememberModalBottomSheetState(),
    onDismissRequest: () -> Unit,
    onResponseNotification: (String, ResponseData) -> Unit,
) {
    ABottomSheet(
        isVisible = bottomSheetIsVisible,
        sheetState = sheetState,
        onDismissRequest = onDismissRequest
    ) {
        if (notificationList.isEmpty()) {
            Text(
                modifier = Modifier.fillMaxWidth().align(Alignment.CenterHorizontally),
                text = "No Notification",
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.primaryContainer,
                style = MaterialTheme.typography.displaySmall
            )

        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = PaddingValues(
                    start = 6.dp,
                    top = 6.dp,
                    end = 6.dp,
                    bottom = getInsetPadding(6, WindowInsets::getBottom)
                )
            ) {
                items(notificationList, key = { it.id }) {
                    NotificationItem(it, onResponseNotification)
                }
            }
        }
    }
}


@Composable
private inline fun NotificationItem(it: NotificationDataV2, noinline onResponse: (String, ResponseData) -> Unit) {
    var isExpand by remember { mutableStateOf(false) }
    Card(
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.height(70.dp).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                AImage(
                    modifier = Modifier
                        .size(112.dp, 70.dp)
                        .background(MaterialTheme.colorScheme.surface, MaterialTheme.shapes.medium)
                        .clip(MaterialTheme.shapes.medium),
                    imageData = it.imageUrl
                )
                Column {
                    Text(
                        modifier = Modifier.fillMaxWidth(),
                        text = it.message,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 2,
                    )
                    Text(
                        text = it.createdAt,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = it.type.name,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Row {
                Spacer(modifier = Modifier.weight(1f))
                it.responses.forEach { response ->
                    TextButton(
                        onClick = { onResponse(it.id, response) }
                    ) {
                        Text(response.type.capitalizeFirst())
                    }
                }
                IconButton(
                    onClick = { isExpand = !isExpand }
                ) {
                    Icon(
                        imageVector = if (isExpand) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                        contentDescription = "ExpandIconButton"
                    )
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        color = MaterialTheme.colorScheme.onPrimary,
                        shape = MaterialTheme.shapes.medium
                    )
                    .animateContentSize()
            ) {
                if (isExpand) {
                    Text(
                        modifier = Modifier.padding(6.dp),
                        text = it.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

        }
    }
}