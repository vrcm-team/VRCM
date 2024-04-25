package io.github.vrcmteam.vrcm.presentation.theme

import androidx.compose.ui.graphics.Color
import io.github.vrcmteam.vrcm.network.api.attributes.TrustRank
import io.github.vrcmteam.vrcm.network.api.attributes.UserStatus

object GameColor {
    object Status {
        val Online = Color(0xFF51E57E)
        val JoinMe = Color(0xFF42CAFF)
        val Busy = Color(0xFFFF6161)
        val AskMe = Color(0xFFFFD24C)
        val Offline = Color.Gray
        fun fromValue(status: UserStatus?): Color {
            return when (status) {
                UserStatus.Active -> Online
                UserStatus.JoinMe -> JoinMe
                UserStatus.AskMe -> AskMe
                UserStatus.Busy -> Busy
                UserStatus.Offline -> Offline
                else -> Offline
            }
        }
    }

    object Rank {
        val Visitor = Color(0xFFCCCCCC)
        val New = Color(0xFF1778FF)
        val User = Color(0xFF2BCF5C)
        val Known = Color(0xFFFF7B42)
        val Trusted = Color(0xFF8143E6)
        fun fromValue(rank: TrustRank?): Color {
            return when (rank) {
                TrustRank.TrustedUser -> Trusted
                TrustRank.KnownUser -> Known
                TrustRank.User -> User
                TrustRank.NewUser -> New
                TrustRank.Visitor -> Visitor
                else -> Visitor
            }
        }
    }

    val Supporter = Color(0xFFFFFC40)

}
