package io.github.vrcmteam.vrcm.di.modules

import io.github.vrcmteam.vrcm.core.shared.SharedFlowCentre
import io.github.vrcmteam.vrcm.network.api.auth.AuthApi
import io.github.vrcmteam.vrcm.network.api.files.FileApi
import io.github.vrcmteam.vrcm.network.api.friends.FriendsApi
import io.github.vrcmteam.vrcm.network.api.github.GitHubApi
import io.github.vrcmteam.vrcm.network.api.groups.GroupsApi
import io.github.vrcmteam.vrcm.network.api.instances.InstancesApi
import io.github.vrcmteam.vrcm.network.api.invite.InviteApi
import io.github.vrcmteam.vrcm.network.api.notification.NotificationApi
import io.github.vrcmteam.vrcm.network.api.users.UsersApi
import io.github.vrcmteam.vrcm.network.api.worlds.WorldsApi
import io.github.vrcmteam.vrcm.network.supports.ApiClientDefaultBuilder
import io.github.vrcmteam.vrcm.network.websocket.WebSocketApi
import io.github.vrcmteam.vrcm.presentation.compoments.ToastText
import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.cookies.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import org.koin.core.definition.Definition
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

internal val networkModule = module(true) {
    singleOf(::AuthApi)
    singleOf(::FileApi)
    singleOf(::FriendsApi)
    singleOf(::InstancesApi)
    singleOf(::UsersApi)
    singleOf(::NotificationApi)
    singleOf(::InviteApi)
    singleOf(::WorldsApi)
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
//        install(Logging) {
//            logger = Logger.SIMPLE
//            level = LogLevel.ALL
//        }
        install(HttpCookies) {
            this.storage = get()
        }
        install(HttpCallValidator){
            handleResponseExceptionWithRequest{ cause, request ->
                SharedFlowCentre.toastText.emit(ToastText.Error(cause.message?: "Unknown error occurred in ${request.url}"))
            }
        }
    }
}