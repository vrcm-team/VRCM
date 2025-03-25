package io.github.vrcmteam.vrcm.network.api.instances.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 创建实例请求数据类
 * 用于封装创建世界实例的请求参数
 */
@Serializable
data class CreateInstanceRequest(
    /**
     * 世界ID
     */
    @SerialName("worldId")
    val worldId: String,

    /**
     * 实例类型
     * 可能的值：private, friends, hidden, public, group
     */
    @SerialName("type")
    val type: String,

    /**
     * 区域类型，如：us, eu, jp
     */
    @SerialName("region")
    val region: String,

    /**
     * 是否启用排队功能（可选）
     */
    @SerialName("queueEnabled")
    val queueEnabled: Boolean? = null,

    /**
     * 是否允许请求邀请（可选，仅适用于private类型）
     */
    @SerialName("canRequestInvite")
    val canRequestInvite: Boolean? = null,

    /**
     * 所有者ID
     * - 当实例类型为"group"时，使用群组ID
     * - 当实例类型为"public"时，为null
     * - 其他情况下，使用用户ID
     */
    @SerialName("ownerId")
    val ownerId: String? = null,

    /**
     * 群组访问权限类型（仅适用于group类型）
     * 可能的值：public, plus, members
     */
    @SerialName("groupAccessType")
    val groupAccessType: String? = null,

    /**
     * 允许加入的角色ID列表（仅适用于group类型且groupAccessType为members）
     */
    @SerialName("roleIds")
    val roleIds: List<String>? = null
)

