package io.github.vrcmteam.vrcm.di.modules

import io.github.vrcmteam.vrcm.network.api.auth.AuthApi
import io.github.vrcmteam.vrcm.network.api.files.FileApi
import io.github.vrcmteam.vrcm.network.api.friends.FriendsApi
import io.github.vrcmteam.vrcm.network.api.instances.InstancesApi
import io.github.vrcmteam.vrcm.network.api.users.UsersApi
import io.github.vrcmteam.vrcm.network.supports.ApiClientDefaultBuilder
import io.ktor.client.*
import io.ktor.client.plugins.cookies.*
import io.ktor.client.plugins.logging.*
import org.koin.core.definition.Definition
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

internal val networkModule = module {
    singleOf(::AuthApi)
    singleOf(::FileApi)
    singleOf(::FriendsApi)
    singleOf(::InstancesApi)
    singleOf(::UsersApi)
    single<HttpClient> { apiClientDefinition(it) }
}



private val apiClientDefinition: Definition<HttpClient> = {
    HttpClient {
        ApiClientDefaultBuilder()
        install(Logging) {
            logger = Logger.DEFAULT
            level = LogLevel.ALL
        }
        install(HttpCookies) {
            this.storage = get()
        }
    }
}