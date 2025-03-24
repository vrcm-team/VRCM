package io.github.vrcmteam.vrcm.network.api.attributes

/**
 * 世界实例访问类型持有属性的抽象
 */
interface IAccessType {
    val instanceId: String
    val accessType: AccessType
        get() =
            when {
                instanceId.contains(AccessType.Group.value) ->
                    when (instanceId.substringAfter("groupAccessType(").substringBefore(")")) {
                    AccessType.GroupPublic.value -> AccessType.GroupPublic
                    AccessType.GroupPlus.value -> AccessType.GroupPlus
                    AccessType.GroupMembers.value -> AccessType.GroupMembers
                    else -> AccessType.Group
                }
                instanceId.contains(AccessType.FriendPlus.value) -> AccessType.FriendPlus
                instanceId.contains(AccessType.Friend.value) -> AccessType.Friend
                else -> AccessType.Public
            }
}