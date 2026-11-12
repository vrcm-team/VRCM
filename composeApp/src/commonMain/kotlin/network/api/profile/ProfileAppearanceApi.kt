package io.github.vrcmteam.vrcm.network.api.profile

import io.github.vrcmteam.vrcm.network.api.profile.data.ProfileAppearanceData
import io.github.vrcmteam.vrcm.network.api.profile.data.UpdateProfileData
import io.github.vrcmteam.vrcm.network.extensions.checkSuccess
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType

/** Reads and updates a user's public profile configuration. */
class ProfileAppearanceApi(private val client: HttpClient) {
    suspend fun get(userId: String): ProfileAppearanceData {
        require(ID_PATTERN.matches(userId)) { "Invalid user ID" }

        return client.get("profile/$userId") {
            parameter("asSelf", true)
            parameter("withGroupsAndWorlds", true)
        }.checkSuccess()
    }

    /**
     * Loads the public profile fields that are no longer returned by `/users/{userId}`.
     * `asSelf` is intentionally omitted: this call is also used for other users.
     */
    suspend fun getPublicProfile(userId: String): ProfileAppearanceData {
        require(ID_PATTERN.matches(userId)) { "Invalid user ID" }

        return client.get("profile/$userId") {
            parameter("withGroupsAndWorlds", true)
        }.checkSuccess()
    }

    /** Rejects incomplete or unapplied updates even when the server returns HTTP 200. */
    suspend fun update(userId: String, data: UpdateProfileData): ProfileAppearanceData {
        require(ID_PATTERN.matches(userId)) { "Invalid user ID" }
        val profile = client.put("profile/$userId") {
            contentType(ContentType.Application.Json)
            setBody(data)
        }.checkSuccess<ProfileAppearanceData>()
        check(profile.id == userId) { "Profile update returned a different user" }
        if (data.bio != null) {
            check(profile.bio == data.bio) { "Profile update did not apply the requested bio" }
        }
        if (data.bioLinks != null) {
            checkNotNull(profile.bioLinks) { "Profile update response omitted social links" }
        }
        if (data.languages != null) {
            check(profile.languages?.toSet() == data.languages.toSet()) {
                "Profile update did not apply the requested languages"
            }
        }
        return profile
    }

    private companion object {
        val ID_PATTERN = Regex("[A-Za-z0-9_-]+")
    }
}
