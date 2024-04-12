package io.github.vrcmteam.vrcm.presentation.screens.home.sheet

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import io.github.vrcmteam.vrcm.presentation.compoments.ABottomSheet
import io.github.vrcmteam.vrcm.presentation.compoments.AImage
import io.github.vrcmteam.vrcm.presentation.screens.home.data.FriendLocation

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FriendLocationBottomSheet(
    bottomSheetIsVisible: Boolean,
    sheetState: SheetState,
    currentLocation: State<FriendLocation?>,
    onDismissRequest: () -> Unit,
) {
    ABottomSheet(
        isVisible = bottomSheetIsVisible,
        sheetState = sheetState,
        onDismissRequest = onDismissRequest
    ) {
        if (currentLocation.value == null) return@ABottomSheet
        val currentInstants by currentLocation.value!!.instants
        Column(
            modifier = Modifier.padding(start = 6.dp, end = 6.dp, bottom = 6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ){
            Box(
                modifier = Modifier.clip(MaterialTheme.shapes.medium)
            ){
                AImage(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    imageData = currentLocation.value?.instants?.value?.worldImageUrl,
                    contentDescription = "WorldImage"
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .background(
                            MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f),
                        )
                        .padding(3.dp)
                ) {
                    Text(
                        modifier = Modifier.align(Alignment.Center),
                        text = currentInstants.worldName,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    contentColor = MaterialTheme.colorScheme.primary
                ),
            ){
                Column(
                    modifier = Modifier.padding(6.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ){
                    Text(
                        text = "Author:${currentInstants.worldAuthorName}",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        text = "AuthorTag:",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        text = currentInstants.worldAuthorTag.joinToString(",\t"),
                        style = MaterialTheme.typography.labelSmall,
                    )
                    Text(
                        text = "Description:",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        text = currentInstants.worldDescription,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
//            FlowRow (
//                modifier = Modifier.fillMaxWidth(),
//                horizontalArrangement = Arrangement.spacedBy(6.dp),
//                verticalArrangement = Arrangement.spacedBy(6.dp),
//            ) {
//                repeat(9){
//                    Card(
//                        modifier = Modifier.size(48.dp),
//                        colors = CardDefaults.cardColors(
//                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
//                            contentColor = MaterialTheme.colorScheme.primary
//                        ),
//                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
//                    ) {
//                        Column(
//                            modifier = Modifier.fillMaxSize(),
//                            verticalArrangement = Arrangement.Center,
//                            horizontalAlignment = Alignment.CenterHorizontally,
//                        ) {
//                            Icon(
//                                modifier = Modifier
//                                    .size(20.dp),
//                                imageVector = Icons.Rounded.Person,
//                                contentDescription = "PersonCount"
//                            )
//                            Text(
//                                text = currentInstants.userCount,
//                                style = MaterialTheme.typography.labelMedium,
//                            )
//                        }
//                    }
//                }
//            }
//            TextButton(
//                modifier = Modifier.align(Alignment.CenterHorizontally),
//                onClick = {
//                    scope.launch { sheetState.hide() }.invokeOnCompletion {
//                        if (sheetState.isVisible) return@invokeOnCompletion
//                        bottomSheetIsVisible = false
//                    }
//                }
//            ) {
//                Text(text = "Look JsonData")
//            }
        }

    }
}