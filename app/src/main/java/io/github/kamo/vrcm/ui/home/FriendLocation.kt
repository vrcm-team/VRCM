package io.github.kamo.vrcm.ui.home

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import io.github.kamo.vrcm.data.api.auth.FriendInfo

data class FriendLocation(
    val location: String,
    val instants:MutableState<InstantsVO> = mutableStateOf(InstantsVO()),
    val friends: List<MutableState<FriendInfo>>,
) {
    constructor(friends: List<MutableState<FriendInfo>>) : this(
        friends[0].value.location,
        friends = friends
    )
}
data class InstantsVO(
    val worldName :String="" ,
    val worldImageUrl:String = "",
    val instantsType: String ="",
    val userCount:String ="",
)
enum class InstantsType{
    Public,
    FriendPlus,
    Friend,
    Prav
}