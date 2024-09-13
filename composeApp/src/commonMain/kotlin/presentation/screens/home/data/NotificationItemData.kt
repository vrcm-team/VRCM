package io.github.vrcmteam.vrcm.presentation.screens.home.data

data class NotificationItemData(
    val id: String,
    val imageUrl: String,
    val message: String,
    val createdAt: String,
    val type: String,
    val actions: List<ActionData>
) {
    data class ActionData(
        val data: String,
        val type: String
    )
}
