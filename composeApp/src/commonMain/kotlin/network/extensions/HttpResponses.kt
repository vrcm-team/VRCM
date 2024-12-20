package io.github.vrcmteam.vrcm.network.extensions

import io.github.vrcmteam.vrcm.network.supports.VRCApiException
import io.ktor.client.call.*
import io.ktor.client.statement.*

suspend inline fun <reified T> HttpResponse.checkSuccessResult(mapping: HttpResponse.() -> T): Result<T> =
    runCatching { checkSuccess(mapping) }

suspend inline fun <reified T> HttpResponse.checkSuccessResult(): Result<T> =
    checkSuccessResult{ body() }

suspend inline fun <reified T> HttpResponse.checkSuccess(mapping: HttpResponse.() -> T): T =
    if (status.value == 200) {
        this.run { mapping() }
    } else {
        throw VRCApiException(
            description = status.description,
            code = status.value,
            bodyText = bodyAsText()
        )
    }

suspend inline fun <reified T> HttpResponse.checkSuccess(): T = checkSuccess { body() }
