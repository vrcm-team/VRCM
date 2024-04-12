package network.api.notification.data

import kotlinx.serialization.Serializable

@Serializable
data class Response(
    val `data`: String,
    val icon: String,
    val text: String,
    val textKey: String,
    val type: String
)