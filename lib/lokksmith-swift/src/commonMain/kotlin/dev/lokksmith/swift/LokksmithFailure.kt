/*
 * Copyright 2026 Sven Jacobs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package dev.lokksmith.swift

import dev.lokksmith.LokksmithException
import dev.lokksmith.client.discovery.MetadataDiscoveryException
import dev.lokksmith.client.request.OAuthResponseException
import dev.lokksmith.client.request.RequestException
import dev.lokksmith.client.request.ResponseException
import dev.lokksmith.client.request.token.TokenTemporalValidationException
import dev.lokksmith.client.request.token.TokenValidationException
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/**
 * The kind of a [LokksmithFailure].
 *
 * The distinction that matters most to a client application is [OAuthRejection] versus [Transport]:
 * only the former means the provider rejected the grant, and only then should a session be
 * considered dead. A [Transport] failure is transient and the session should be kept.
 */
@OptIn(ExperimentalObjCName::class)
public enum class LokksmithFailureKind {
    /** The provider returned a parseable OAuth 2.0 error, such as `invalid_grant`. */
    @ObjCName("oAuthRejection") OAuthRejection,

    /** The request did not complete: no connection, timeout, TLS failure. */
    @ObjCName("transport") Transport,

    /** The provider responded, but not with something usable. */
    @ObjCName("response") Response,

    /** Discovery of the provider configuration failed. */
    @ObjCName("discovery") Discovery,

    /** A token failed validation, for example a signature, issuer or nonce mismatch. */
    @ObjCName("tokenValidation") TokenValidation,

    /**
     * A token failed validation because of timestamps only. Commonly a skewed device clock rather
     * than a genuine problem with the token.
     */
    @ObjCName("tokenTemporalValidation") TokenTemporalValidation,

    /** Anything else. */
    @ObjCName("generic") Generic,
}

/**
 * The single error type thrown across the Objective-C boundary.
 *
 * Swift receives this as an `NSError` whose `userInfo["KotlinException"]` holds this instance:
 * ```swift
 * do {
 *     let tokens = try await client.refresh()
 * } catch let error as NSError {
 *     let failure = error.userInfo["KotlinException"] as? LokksmithFailure
 *     if failure?.kind == .oAuthRejection { /* session is dead, sign the user out */ }
 * }
 * ```
 *
 * @param kind What went wrong, in terms a client application can act on.
 * @param message A human-readable description, for logging rather than display.
 * @param code The OAuth error code, when [kind] is [LokksmithFailureKind.OAuthRejection].
 */
public class LokksmithFailure
internal constructor(
    public val kind: LokksmithFailureKind,
    override val message: String?,
    public val code: String?,
) : Exception(message)

internal fun Throwable.toFailure(): LokksmithFailure =
    when (this) {
        is LokksmithFailure -> this
        is OAuthResponseException ->
            LokksmithFailure(
                kind = LokksmithFailureKind.OAuthRejection,
                message = errorDescription ?: message,
                code = error.code,
            )
        is RequestException ->
            LokksmithFailure(
                kind = LokksmithFailureKind.Transport,
                message = message,
                code = null,
            )
        is MetadataDiscoveryException ->
            LokksmithFailure(
                kind = LokksmithFailureKind.Discovery,
                message = message,
                code = null,
            )
        is TokenTemporalValidationException ->
            LokksmithFailure(
                kind = LokksmithFailureKind.TokenTemporalValidation,
                message = message,
                code = null,
            )
        is TokenValidationException ->
            LokksmithFailure(
                kind = LokksmithFailureKind.TokenValidation,
                message = message,
                code = null,
            )
        // Checked after the more specific subtypes above, which it is a supertype of.
        is ResponseException ->
            LokksmithFailure(
                kind = LokksmithFailureKind.Response,
                message = message,
                code = null,
            )
        is LokksmithException ->
            LokksmithFailure(
                kind = LokksmithFailureKind.Generic,
                message = message,
                code = null,
            )
        else ->
            LokksmithFailure(
                kind = LokksmithFailureKind.Generic,
                message = message,
                code = null,
            )
    }

/**
 * Runs [block], translating any Lokksmith or platform exception into a [LokksmithFailure] so that
 * Swift callers see one predictable error type.
 *
 * `CancellationException` is deliberately not caught: structured concurrency must keep working when
 * a Swift `Task` is cancelled.
 */
internal suspend inline fun <T> mapFailures(crossinline block: suspend () -> T): T =
    try {
        block()
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (e: Throwable) {
        throw e.toFailure()
    }
