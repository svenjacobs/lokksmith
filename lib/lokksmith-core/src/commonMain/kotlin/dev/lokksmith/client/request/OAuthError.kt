/*
 * Copyright 2025 Sven Jacobs
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
package dev.lokksmith.client.request

public sealed interface OAuthError {

    public val code: String

    public data class Unknown(override val code: String) : OAuthError

    // https://www.rfc-editor.org/rfc/rfc6749#section-4.1.2.1

    public data object InvalidRequest : OAuthError {
        override val code: String = "invalid_request"
    }

    public data object UnauthorizedClient : OAuthError {
        override val code: String = "unauthorized_client"
    }

    public data object AccessDenied : OAuthError {
        override val code: String = "access_denied"
    }

    public data object UnsupportedResponseType : OAuthError {
        override val code: String = "unsupported_response_type"
    }

    public data object InvalidScope : OAuthError {
        override val code: String = "invalid_scope"
    }

    public data object ServerError : OAuthError {
        override val code: String = "server_error"
    }

    public data object TemporarilyUnavailable : OAuthError {
        override val code: String = "temporarily_unavailable"
    }

    // https://www.rfc-editor.org/rfc/rfc6749#section-5.2

    public data object InvalidClient : OAuthError {
        override val code: String = "invalid_client"
    }

    public data object InvalidGrant : OAuthError {
        override val code: String = "invalid_grant"
    }

    public data object UnsupportedGrantType : OAuthError {
        override val code: String = "unsupported_grant_type"
    }

    // https://openid.net/specs/openid-connect-core-1_0.html#AuthError

    public data object InteractionRequired : OAuthError {
        override val code: String = "interaction_required"
    }

    public data object LoginRequired : OAuthError {
        override val code: String = "login_required"
    }

    public data object AccountSelectionRequired : OAuthError {
        override val code: String = "account_selection_required"
    }

    public data object ConsentRequired : OAuthError {
        override val code: String = "consent_required"
    }

    public data object InvalidRequestUri : OAuthError {
        override val code: String = "invalid_request_uri"
    }

    public data object InvalidRequestObject : OAuthError {
        override val code: String = "invalid_request_object"
    }

    public data object RequestNotSupported : OAuthError {
        override val code: String = "request_not_supported"
    }

    public data object RequestUriNotSupported : OAuthError {
        override val code: String = "request_uri_not_supported"
    }

    public data object RegistrationNotSupported : OAuthError {
        override val code: String = "registration_not_supported"
    }
}

internal fun String.toOAuthError(): OAuthError =
    listOf(
            OAuthError.InvalidRequest,
            OAuthError.UnauthorizedClient,
            OAuthError.AccessDenied,
            OAuthError.UnsupportedResponseType,
            OAuthError.InvalidScope,
            OAuthError.ServerError,
            OAuthError.TemporarilyUnavailable,
            OAuthError.InvalidClient,
            OAuthError.InvalidGrant,
            OAuthError.UnsupportedGrantType,
            OAuthError.InteractionRequired,
            OAuthError.LoginRequired,
            OAuthError.AccountSelectionRequired,
            OAuthError.ConsentRequired,
            OAuthError.InvalidRequestUri,
            OAuthError.InvalidRequestObject,
            OAuthError.RequestNotSupported,
            OAuthError.RequestUriNotSupported,
            OAuthError.RegistrationNotSupported,
        )
        .firstOrNull { it.code == this } ?: OAuthError.Unknown(this)
