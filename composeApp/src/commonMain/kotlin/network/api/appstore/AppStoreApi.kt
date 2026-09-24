package io.github.vrcmteam.vrcm.network.api.appstore

import io.github.vrcmteam.vrcm.network.api.appstore.data.AppStoreLookupData
import io.github.vrcmteam.vrcm.network.extensions.checkSuccess
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.serialization.json.Json

private const val APP_STORE_LOOKUP_URL = "https://itunes.apple.com/lookup"

class AppStoreApi(
    private val client: HttpClient,
    private val json: Json,
) {

    /**
     * 按 bundle ID 查询 App Store 上架信息；[country] 为空时查询默认（美国）商店。
     *
     * 该接口的 Content-Type 是 text/javascript，ContentNegotiation 不会处理，按文本解码。
     */
    suspend fun lookup(bundleId: String, country: String?): Result<AppStoreLookupData> =
        runCatching {
            client.get(APP_STORE_LOOKUP_URL) {
                parameter("bundleId", bundleId)
                parameter("country", country)
            }.checkSuccess { json.decodeFromString<AppStoreLookupData>(bodyAsText()) }
        }
}
