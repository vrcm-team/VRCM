package io.github.vrcmteam.vrcm.di.modules

import io.github.vrcmteam.vrcm.network.api.auth.AuthApi
import io.github.vrcmteam.vrcm.network.api.files.FileApi
import io.github.vrcmteam.vrcm.network.api.friends.FriendsApi
import io.github.vrcmteam.vrcm.network.api.github.GitHubApi
import io.github.vrcmteam.vrcm.network.api.groups.GroupsApi
import io.github.vrcmteam.vrcm.network.api.instances.InstancesApi
import io.github.vrcmteam.vrcm.network.api.notification.NotificationApi
import io.github.vrcmteam.vrcm.network.api.users.UsersApi
import io.github.vrcmteam.vrcm.network.supports.ApiClientDefaultBuilder
import io.github.vrcmteam.vrcm.network.websocket.WebSocketApi
import io.ktor.client.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.cookies.*
import io.ktor.client.plugins.logging.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import org.koin.core.definition.Definition
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

@OptIn(ExperimentalSerializationApi::class)
internal val networkModule = module(true) {
    singleOf(::AuthApi)
    singleOf(::FileApi)
    singleOf(::FriendsApi)
    singleOf(::InstancesApi)
    singleOf(::UsersApi)
    singleOf(::NotificationApi)
    singleOf(::WebSocketApi)
    singleOf(::GitHubApi)
    singleOf(::GroupsApi)
    single <HttpClient> { apiClientDefinition(it) }
    single { Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
        prettyPrint = true
        isLenient = true
    } }
}



private val apiClientDefinition: Definition<HttpClient> = {
    HttpClient {
        ApiClientDefaultBuilder()
        // api的json序列化器
        install(ContentNegotiation) {
            json(get())
        }
        install(Logging) {
            logger = Logger.DEFAULT
            level = LogLevel.ALL
        }
        install(HttpCookies) {
            this.storage = get()
        }
    }
}