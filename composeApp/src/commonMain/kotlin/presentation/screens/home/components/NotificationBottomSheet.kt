package io.github.vrcmteam.vrcm.presentation.screens.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Text
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import io.github.vrcmteam.vrcm.network.api.notification.NotificationApi
import io.github.vrcmteam.vrcm.network.api.notification.data.NotificationData
import io.github.vrcmteam.vrcm.presentation.compoments.ABottomSheet
import io.github.vrcmteam.vrcm.presentation.compoments.AImage
import io.github.vrcmteam.vrcm.presentation.extensions.getInsetPadding
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationBottomSheet(
    bottomSheetIsVisible: Boolean,
    sheetState: SheetState = rememberModalBottomSheetState(),
    onDismissRequest: () -> Unit,
) {
    val notificationApi = koinInject<NotificationApi>()

    var notificationList by remember { mutableStateOf(listOf<NotificationData>()) }
    LaunchedEffect(bottomSheetIsVisible) {
        if (bottomSheetIsVisible) {
            notificationList = notificationApi.fetchNotifications()
        }
    }
    ABottomSheet(
        isVisible = bottomSheetIsVisible,
        sheetState = sheetState,
        onDismissRequest = onDismissRequest
    ){
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(6.dp),
            contentPadding = PaddingValues(
                start = 6.dp,
                top = 6.dp,
                end = 6.dp,
                bottom = getInsetPadding(6, WindowInsets::getBottom)
            )
        ) {
            items(notificationList){
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
                                    .clip(MaterialTheme.shapes.medium),
                                imageData = it.imageUrl
                            )
                            Column {
                                Text(
                                    modifier = Modifier.fillMaxWidth(),
                                    text =  it.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    maxLines = 2,
                                )
                                Text(
                                    text =  it.createdAt,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text =  it.type,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        Box(
                            modifier = Modifier.fillMaxWidth().background(
                                color = MaterialTheme.colorScheme.onPrimary,
                                shape = MaterialTheme.shapes.medium
                            ).padding(6.dp)
                        ){
                            Text(
                                text = it.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                    }
                }

            }
        }
    }
}