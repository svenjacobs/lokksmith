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

import dev.lokksmith.client.Client
import dev.lokksmith.client.request.flow.authorizationCode.AuthorizationCodeFlow
import dev.lokksmith.client.request.flow.endSession.EndSessionFlow
import dev.lokksmith.client.request.parameter.Prompt
import dev.lokksmith.client.request.parameter.Scope
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/**
 * Provider metadata, used when a client is configured statically instead of through discovery.
 *
 * @see LokksmithClientConfiguration.metadata
 */
public class LokksmithMetadata(
    public val issuer: String,
    public val authorizationEndpoint: String,
    public val tokenEndpoint: String,
    public val jwksUri: String?,
    public val endSessionEndpoint: String?,
    public val userInfoEndpoint: String?,
) {
    /**
     * Creates metadata with only the endpoints required to run the Authorization Code Flow.
     *
     * Objective-C interop does not carry Kotlin default arguments, so this overload exists to keep
     * the common case short from Swift.
     */
    public constructor(
        issuer: String,
        authorizationEndpoint: String,
        tokenEndpoint: String,
    ) : this(
        issuer = issuer,
        authorizationEndpoint = authorizationEndpoint,
        tokenEndpoint = tokenEndpoint,
        jwksUri = null,
        endSessionEndpoint = null,
        userInfoEndpoint = null,
    )

    internal fun toCore(): Client.Metadata =
        Client.Metadata(
            issuer = issuer,
            authorizationEndpoint = authorizationEndpoint,
            tokenEndpoint = tokenEndpoint,
            jwksUri = jwksUri,
            endSessionEndpoint = endSessionEndpoint,
            userInfoEndpoint = userInfoEndpoint,
        )
}

/**
 * How a client obtains its provider configuration.
 *
 * Use [discovery] to resolve the configuration from the provider's OpenID configuration document,
 * or [metadata] to supply the endpoints directly — for example when migrating an existing session
 * without a network round trip.
 */
public class LokksmithClientConfiguration
private constructor(
    internal val clientId: String,
    internal val discoveryUrl: String?,
    internal val metadata: LokksmithMetadata?,
) {
    public companion object {

        /** Resolves the provider configuration from [discoveryUrl]. */
        public fun discovery(clientId: String, discoveryUrl: String): LokksmithClientConfiguration =
            LokksmithClientConfiguration(
                clientId = clientId,
                discoveryUrl = discoveryUrl,
                metadata = null,
            )

        /** Configures the client from [metadata], performing no discovery request. */
        public fun metadata(
            clientId: String,
            metadata: LokksmithMetadata,
        ): LokksmithClientConfiguration =
            LokksmithClientConfiguration(
                clientId = clientId,
                discoveryUrl = null,
                metadata = metadata,
            )
    }
}

/** An OAuth 2.0 token with an optional expiration, as seconds since the Unix epoch. */
public class LokksmithToken(public val token: String, public val expiresAt: Long?)

/** The claims of a validated ID token, plus its original encoded form. */
public class LokksmithIdToken(
    public val raw: String,
    public val issuer: String,
    public val subject: String,
    public val audiences: List<String>,
    public val expiration: Long,
    public val issuedAt: Long,
    public val authTime: Long?,
    public val notBefore: Long?,
    public val nonce: String?,
    public val authenticationContextClassReference: String?,
    public val authenticationMethodsReferences: List<String>,
    public val authorizedParty: String?,
)

/** The current token set of a client. */
public class LokksmithTokens(
    public val accessToken: LokksmithToken,
    public val refreshToken: LokksmithToken?,
    public val idToken: LokksmithIdToken,
)

internal fun Client.Tokens.toSwift(): LokksmithTokens =
    LokksmithTokens(
        accessToken = LokksmithToken(token = accessToken.token, expiresAt = accessToken.expiresAt),
        refreshToken =
            refreshToken?.let { LokksmithToken(token = it.token, expiresAt = it.expiresAt) },
        idToken =
            LokksmithIdToken(
                raw = idToken.raw,
                issuer = idToken.issuer,
                subject = idToken.subject,
                audiences = idToken.audiences,
                expiration = idToken.expiration,
                issuedAt = idToken.issuedAt,
                authTime = idToken.authTime,
                notBefore = idToken.notBefore,
                nonce = idToken.nonce,
                authenticationContextClassReference = idToken.authenticationContextClassReference,
                authenticationMethodsReferences = idToken.authenticationMethodsReferences,
                authorizedParty = idToken.authorizedParty,
            ),
    )

/**
 * Values that the `prompt` authorization request parameter may take.
 *
 * Mirrors `dev.lokksmith.client.request.parameter.Prompt`, which is not part of the exported
 * Objective-C surface.
 */
@OptIn(ExperimentalObjCName::class)
public enum class LokksmithPrompt {
    @ObjCName("none") None,
    @ObjCName("login") Login,
    @ObjCName("consent") Consent,
    @ObjCName("selectAccount") SelectAccount,
    @ObjCName("create") Create;

    internal fun toCore(): Prompt =
        when (this) {
            None -> Prompt.None
            Login -> Prompt.Login
            Consent -> Prompt.Consent
            SelectAccount -> Prompt.SelectAccount
            Create -> Prompt.Create
        }
}

/**
 * An authorization request.
 *
 * `openid` is always requested and does not need to be listed in [scopes].
 *
 * Everything beyond the redirect URI is optional and set as a property, because Objective-C interop
 * does not carry Kotlin default arguments.
 *
 * @param redirectUri The URI the provider redirects to. Its scheme must be registered by the app.
 */
public class LokksmithAuthorizationRequest(public val redirectUri: String) {

    /** Additional scopes to request. `openid` is always requested. */
    public var scopes: List<String> = emptyList()

    /** The `prompt` parameter. [LokksmithPrompt.Login] forces re-authentication. */
    public var prompts: List<LokksmithPrompt> = emptyList()

    /** The `ui_locales` parameter, in order of preference. */
    public var uiLocales: List<String> = emptyList()

    /** The `login_hint` parameter. */
    public var loginHint: String? = null

    /** The `max_age` parameter, in seconds. */
    public var maxAge: Int? = null

    /** Extra query parameters, added verbatim to the authorization request. */
    public var additionalParameters: Map<String, String> = emptyMap()

    /**
     * Forwarded to `ASWebAuthenticationSession.prefersEphemeralWebBrowserSession`. When `true`, the
     * flow does not share cookies with Safari.
     */
    public var prefersEphemeralWebBrowserSession: Boolean = false

    /** Forwarded to `ASWebAuthenticationSession.additionalHeaderFields`. */
    public var additionalHeaderFields: Map<String, String> = emptyMap()

    internal fun toCore(): AuthorizationCodeFlow.Request =
        AuthorizationCodeFlow.Request(
            redirectUri = redirectUri,
            scope = scopes.filter { it.isNotBlank() }.map { Scope.Custom(it) }.toSet(),
            prompt = prompts.map { it.toCore() }.toSet(),
            maxAge = maxAge,
            uiLocales = uiLocales,
            loginHint = loginHint,
            additionalParameters = additionalParameters,
        )
}

/**
 * An RP-initiated logout request.
 *
 * Requires the provider to advertise an `end_session_endpoint`.
 *
 * @param redirectUri The `post_logout_redirect_uri` parameter.
 */
public class LokksmithEndSessionRequest(public val redirectUri: String) {

    /** The `logout_hint` parameter. */
    public var logoutHint: String? = null

    /** The `ui_locales` parameter, in order of preference. */
    public var uiLocales: List<String> = emptyList()

    /** Extra query parameters, added verbatim to the request. */
    public var additionalParameters: Map<String, String> = emptyMap()

    /** Forwarded to `ASWebAuthenticationSession.prefersEphemeralWebBrowserSession`. */
    public var prefersEphemeralWebBrowserSession: Boolean = false

    internal fun toCore(): EndSessionFlow.Request =
        EndSessionFlow.Request(
            redirectUri = redirectUri,
            logoutHint = logoutHint,
            uiLocales = uiLocales,
            additionalParameters = additionalParameters,
        )
}
