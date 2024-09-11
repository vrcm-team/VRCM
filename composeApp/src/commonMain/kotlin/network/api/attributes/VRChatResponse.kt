package io.github.vrcmteam.vrcm.network.api.attributes

import io.github.vrcmteam.vrcm.network.supports.VRCApiException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class VRChatResponse(
    val error: VRChatResult? = null,
    val success: VRChatResult? = null,
) {
    @Serializable
    data class VRChatResult(
        val message: String,
        @SerialName("status_code")
        val statusCode: Int,
    )

    val isError: Boolean = error != null

    val isSuccess: Boolean = success != null

    fun toResult(): Result<VRChatResult> {
        return when {
            success != null -> Result.success(success)
            error != null -> Result.failure(VRCApiException(error.message, error.statusCode, ""))
            else -> Result.failure(IllegalStateException("Both success and error are null"))
        }
    }

}

