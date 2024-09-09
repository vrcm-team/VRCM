package io.github.vrcmteam.vrcm.network.api.favorite

import io.github.vrcmteam.vrcm.core.extensions.fetchDataList
import io.github.vrcmteam.vrcm.network.api.attributes.FAVORITE_API_PREFIX
import io.github.vrcmteam.vrcm.network.api.attributes.FavoriteType
import io.github.vrcmteam.vrcm.network.api.favorite.data.FavoriteData
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class FavoriteApi(private val client: HttpClient) {

    fun fetchFavorite(
        favoriteType: FavoriteType,
        offset: Int = 0,
        n: Int = 50,
    ): Flow<List<FavoriteData>> = flow {
            fetchDataList(offset, n) { currentOffset, _ ->
                client.get(FAVORITE_API_PREFIX) {
                    parameters {
                        parameter("type", favoriteType.value)
                        parameter("offset", currentOffset.toString())
                        parameter("n", n.toString())
                    }
                }.body()
            }
        }

}