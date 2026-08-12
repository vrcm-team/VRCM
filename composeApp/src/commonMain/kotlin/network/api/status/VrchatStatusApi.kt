package io.github.vrcmteam.vrcm.network.api.status

import io.github.vrcmteam.vrcm.network.api.status.data.VrchatStatusData
import io.github.vrcmteam.vrcm.network.extensions.checkSuccess
import io.ktor.client.HttpClient
import io.ktor.client.request.get

class VrchatStatusApi(private val client: HttpClient) {
    suspend fun fetchStatus(): Result<VrchatStatusData> = runCatching {
        client.get(STATUS_URL).checkSuccess()
    }

    private companion object {
        const val STATUS_URL = "https://status.vrchat.com/api/v2/status.json"
    }
}
