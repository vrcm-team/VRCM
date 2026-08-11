package io.github.vrcmteam.vrcm.service

import io.github.vrcmteam.vrcm.network.api.avatars.AvatarsApi
import io.github.vrcmteam.vrcm.network.api.avatars.data.AvatarData
import io.github.vrcmteam.vrcm.network.api.groups.GroupsApi
import io.github.vrcmteam.vrcm.network.api.groups.data.GroupData
import io.github.vrcmteam.vrcm.network.api.users.UsersApi
import io.github.vrcmteam.vrcm.network.api.users.data.UserData
import io.github.vrcmteam.vrcm.network.api.worlds.WorldsApi
import io.github.vrcmteam.vrcm.network.api.worlds.data.WorldData
import io.ktor.http.URLProtocol
import io.ktor.http.Url
import kotlinx.coroutines.CancellationException

internal enum class OfficialLinkType(
    internal val pathSegment: String,
    internal val idPrefix: String,
) {
    User("user", "usr_"),
    World("world", "wrld_"),
    Group("group", "grp_"),
    Avatar("avatar", "avtr_"),
}

internal data class OfficialLinkTarget(
    val type: OfficialLinkType,
    val id: String,
)

internal sealed interface OfficialLinkContent {
    data class User(val data: UserData) : OfficialLinkContent
    data class World(val data: WorldData) : OfficialLinkContent
    data class Group(val data: GroupData) : OfficialLinkContent
    data class Avatar(val data: AvatarData) : OfficialLinkContent
}

/** Parses a standalone ID for a VRChat content type that VRCM can open. */
internal fun parseOfficialId(value: String): OfficialLinkTarget? {
    val id = value.trim()
    val type = OfficialLinkType.entries.firstOrNull { id.startsWith(it.idPrefix) }
        ?: return null
    return type.targetFor(id)
}

/** Parses an exact public VRChat profile URL and rejects foreign hosts or mismatched IDs. */
internal fun parseOfficialLink(value: String): OfficialLinkTarget? {
    val url = runCatching { Url(value.trim()) }.getOrNull() ?: return null
    if (url.protocol != URLProtocol.HTTPS) return null
    if (url.host != "vrchat.com" && url.host != "www.vrchat.com") return null

    val segments = url.encodedPath.split('/').filter(String::isNotEmpty)
    if (segments.size != 3 || segments[0] != "home") return null
    val type = OfficialLinkType.entries.firstOrNull { it.pathSegment == segments[1] }
        ?: return null
    return type.targetFor(segments[2])
}

private fun OfficialLinkType.targetFor(id: String): OfficialLinkTarget? =
    id.takeIf { Regex("$idPrefix[A-Za-z0-9-]+").matches(it) }
        ?.let { OfficialLinkTarget(type = this, id = it) }

internal class OfficialLinkService(
    private val usersApi: UsersApi,
    private val worldsApi: WorldsApi,
    private val groupsApi: GroupsApi,
    private val avatarsApi: AvatarsApi,
    private val authService: AuthService,
) {
    suspend fun resolve(target: OfficialLinkTarget): Result<OfficialLinkContent> {
        val result = authService.reTryAuthCatching {
            when (target.type) {
                OfficialLinkType.User -> OfficialLinkContent.User(usersApi.fetchUser(target.id))
                OfficialLinkType.World -> OfficialLinkContent.World(worldsApi.getWorldById(target.id))
                OfficialLinkType.Group -> OfficialLinkContent.Group(
                    groupsApi.fetchGroup(target.id, includeRoles = true)
                )
                OfficialLinkType.Avatar -> OfficialLinkContent.Avatar(
                    avatarsApi.getAvatarById(target.id)
                )
            }
        }
        result.exceptionOrNull()?.let { if (it is CancellationException) throw it }
        return result
    }
}
