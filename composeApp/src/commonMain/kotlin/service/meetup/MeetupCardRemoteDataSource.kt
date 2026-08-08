package io.github.vrcmteam.vrcm.service.meetup

import io.github.vrcmteam.vrcm.network.api.profile.ProfileAppearanceApi
import io.github.vrcmteam.vrcm.network.api.users.UsersApi
import io.github.vrcmteam.vrcm.presentation.screens.gallery.editor.PrintImageLimits

/** A compact remote profile used to refresh a meetup card snapshot. */
data class MeetupRemoteProfile(
    val id: String,
    val displayName: String,
    val avatarUrl: String,
    val pronouns: String,
    val languages: List<String>,
    val status: String,
    val statusDescription: String,
    val profileBackgroundUrl: String,
)

/** Equipped profile decoration IDs returned by the profile appearance endpoint. */
data class MeetupRemoteAppearance(
    val id: String,
    val iconFrame: String? = null,
    val profileEffect: String? = null,
    val nameplateEffect: String? = null,
)

/** Narrow remote boundary required by the meetup card repository. */
interface MeetupCardRemoteDataSource {
    suspend fun getProfile(ownerId: String): MeetupRemoteProfile

    suspend fun getAppearance(ownerId: String): MeetupRemoteAppearance

    suspend fun loadImage(url: String): ByteArray
}

/** Production adapter over the existing authenticated VRChat APIs and bounded byte loader. */
class DefaultMeetupCardRemoteDataSource(
    private val usersApi: UsersApi,
    private val profileAppearanceApi: ProfileAppearanceApi,
    private val remoteBytesLoader: MeetupRemoteBytesLoader,
) : MeetupCardRemoteDataSource {
    override suspend fun getProfile(ownerId: String): MeetupRemoteProfile =
        usersApi.fetchUser(ownerId).let { user ->
            MeetupRemoteProfile(
                id = user.id,
                displayName = user.displayName,
                avatarUrl = user.iconUrl,
                pronouns = user.pronouns.orEmpty(),
                languages = user.speakLanguages,
                status = user.status.value,
                statusDescription = user.statusDescription,
                profileBackgroundUrl = user.profileImageUrl,
            )
        }

    override suspend fun getAppearance(ownerId: String): MeetupRemoteAppearance =
        profileAppearanceApi.get(ownerId).let { appearance ->
            MeetupRemoteAppearance(
                id = appearance.id,
                iconFrame = appearance.iconFrame,
                profileEffect = appearance.profileEffect,
                nameplateEffect = appearance.nameplateEffect,
            )
        }

    override suspend fun loadImage(url: String): ByteArray =
        remoteBytesLoader.load(url, PrintImageLimits.MAX_FILE_BYTES)
}
