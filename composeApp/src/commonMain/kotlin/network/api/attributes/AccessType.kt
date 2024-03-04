package io.github.vrcmteam.vrcm.network.api.attributes

enum class AccessType(val value: String, val displayName: String) {
    Public("public", "Public"),

    GroupPublic("public", "Group Public"),
    GroupPlus("plus", "Group+"),
    GroupMembers("members", "Group"),
    Group("group", "Group"),

    FriendPlus("hidden", "Friends+"),
    Friend("friends", "Friends"),

    InvitePlus("canRequestInvite", "Invite"),
    Invite("!canRequestInvite", "Invite"),
    Private("private", "Private"),

}