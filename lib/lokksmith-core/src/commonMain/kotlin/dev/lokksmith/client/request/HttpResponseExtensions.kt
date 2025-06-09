package dev.lokksmith.client.request

import io.ktor.client.call.body
import io.ktor.client.statement.HttpResponse
import kotlinx.coroutines.CancellationException

internal suspend inline fun <reified T> HttpResponse.bodyOrThrow() = try {
    body<T>()
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    throw ResponseException(
        cause = e,
        reason = ResponseException.Reason.HttpError,
    )
}