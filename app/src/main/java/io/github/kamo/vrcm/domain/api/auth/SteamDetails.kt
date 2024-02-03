package io.github.kamo.vrcm.domain.api.auth

data class SteamDetails(
    val avatar: String,
    val avatarfull: String,
    val avatarhash: String,
    val avatarmedium: String,
    val communityvisibilitystate: Int,
    val loccountrycode: String,
    val locstatecode: String,
    val personaname: String,
    val personastate: Int,
    val personastateflags: Int,
    val primaryclanid: String,
    val profilestate: Int,
    val profileurl: String,
    val steamid: String,
    val timecreated: Int
)