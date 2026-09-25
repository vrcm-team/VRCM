package io.github.vrcmteam.vrcm.network.api.users

import io.github.vrcmteam.vrcm.network.api.users.data.UpdateUserInfoData
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class UsersApiPrivacyTest {
    @Test
    fun privacyUpdateSendsOnlyRequestedFlagAndReadsServerValue() = runBlocking {
        lateinit var capturedRequest: HttpRequestData
        val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            explicitNulls = false
        }
        val client = HttpClient(MockEngine) {
            engine {
                addHandler { request ->
                    capturedRequest = request
                    respond(
                        content = currentUserUpdateJson(isEnabled = false),
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }
            }
            install(ContentNegotiation) { json(json) }
        }

        val updatedUser = UsersApi(client).updateUserInfo(
            userId = "usr_self",
            updateUserInfoData = UpdateUserInfoData(isBoopingEnabled = false),
        )

        assertEquals(HttpMethod.Put, capturedRequest.method)
        assertEquals("/users/usr_self", capturedRequest.url.encodedPath)
        assertEquals(
            buildJsonObject { put("isBoopingEnabled", false) },
            json.parseToJsonElement(capturedRequest.bodyText()),
        )
        assertEquals("usr_self", updatedUser.id)
        assertFalse(updatedUser.isBoopingEnabled ?: true)
        client.close()
    }

    private fun HttpRequestData.bodyText(): String =
        (body as OutgoingContent.ByteArrayContent).bytes().decodeToString()

    private fun currentUserUpdateJson(isEnabled: Boolean): String = """
        {
          "ageVerificationStatus":"verified","ageVerified":true,
          "acceptedPrivacyVersion":0,"acceptedTOSVersion":0,
          "accountDeletionDate":null,"accountDeletionLog":null,
          "allowAvatarCopying":true,"bio":null,"bioLinks":[],
          "currentAvatar":"","currentAvatarAssetUrl":null,"currentAvatarImageUrl":"",
          "currentAvatarTags":[],"currentAvatarThumbnailImageUrl":"","date_joined":"",
          "developerType":"none","displayName":"Self","emailVerified":true,
          "fallbackAvatar":"","friendGroupNames":[],"friendKey":"","friends":[],
          "googleId":"","hasBirthday":true,"hasEmail":true,
          "hasLoggedInFromClient":true,"hasPendingEmail":false,
          "hideContentFilterSettings":false,"homeLocation":"","id":"usr_self",
          "isFriend":false,"isBoopingEnabled":$isEnabled,
          "last_activity":"","last_login":"","last_platform":"standalonewindows",
          "obfuscatedEmail":"","obfuscatedPendingEmail":"","oculusId":"",
          "pastDisplayNames":[],"picoId":"","profilePicOverride":"","state":"online",
          "status":"active","statusDescription":"","statusFirstTime":false,
          "statusHistory":[],"steamDetails":{},"steamId":"","tags":[],
          "twoFactorAuthEnabled":false,"twoFactorAuthEnabledDate":null,
          "unsubscribe":false,"updated_at":"","userIcon":"","userLanguage":null,
          "userLanguageCode":null,"username":"self","viveId":"","pronouns":null
        }
    """.trimIndent()
}
