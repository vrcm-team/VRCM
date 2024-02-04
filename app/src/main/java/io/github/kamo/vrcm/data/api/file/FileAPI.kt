package io.github.kamo.vrcm.data.api.file

import io.github.kamo.vrcm.data.api.APIClient

internal const val FILE_API_SUFFIX = "file"
class FileAPI(private val client: APIClient) {


   suspend fun getFileLocal(fileUrl:String) =
        client.get(fileUrl).headers["Location"]

}