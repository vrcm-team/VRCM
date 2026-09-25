package io.github.vrcmteam.vrcm.network.api.avatars.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/** VRChat 返回的模型替身生成任务权威状态。 */
@Serializable
data class AvatarImpostorServiceStatus(
    @SerialName("created_at")
    val createdAt: String = "",
    val id: String = "",
    val progress: List<JsonObject> = emptyList(),
    val requesterUserId: String = "",
    val state: String = "",
    val subjectId: String = "",
    val subjectType: String = "",
    val type: String = "",
    @SerialName("updated_at")
    val updatedAt: String = "",
)

/** 当前账户的模型替身队列预计处理时长。 */
@Serializable
data class AvatarImpostorQueueStats(
    val estimatedServiceDurationSeconds: Int,
)
