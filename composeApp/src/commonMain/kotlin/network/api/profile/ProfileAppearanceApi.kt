package io.github.vrcmteam.vrcm.network.api.profile

import io.github.vrcmteam.vrcm.network.api.profile.data.ProfileAppearanceData
import io.github.vrcmteam.vrcm.network.extensions.checkSuccess
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter

/** Retrieves a user's profile appearance configuration from VRChat. */
class ProfileAppearanceApi(private val client: HttpClient) {
    suspend fun get(userId: String): ProfileAppearanceData {
        require(ID_PATTERN.matches(userId)) { "Invalid user ID" }

        return client.get("profile/$userId") {
            parameter("asSelf", true)
            parameter("withGroupsAndWorlds", true)
        }.checkSuccess()
    }

    private companion object {
        val ID_PATTERN = Regex("[A-Za-z0-9_-]+")
    }
}
