package io.github.vrcmteam.vrcm.presentation.screens.home.data

import io.github.vrcmteam.vrcm.network.api.notification.data.NotificationData

data class NotificationItemData(
    val id: String,
    val imageUrl: String,
    val title: String,
    val message: String,
    val createdAt: String,
    val senderUserId: String,
    val type: String,
    val actions: List<ActionData>
) {
    data class ActionData(
        val data: String,
        val type: String
    )

    constructor(n: NotificationData) : this(
        id = n.id,
        imageUrl = n.imageUrl,
        title = n.title,
        message = n.message,
        createdAt = n.createdAt,
        senderUserId = n.senderUserId.orEmpty(),
        type = n.type,
        actions = n.responses.map { responses ->
            ActionData(
                data = responses.responseData,
                type = responses.type
            )
        }
    )

}
