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
package dev.lokksmith.client.request.parameter

/**
 * @see <a href="https://openid.net/specs/openid-connect-core-1_0.html#AuthRequest">Authentication
 *   Request</a>
 */
public object Parameter {

    // Request & response
    public const val CLIENT_ID: String = "client_id"
    public const val CODE_CHALLENGE: String = "code_challenge"
    public const val CODE_CHALLENGE_METHOD: String = "code_challenge_method"
    public const val CODE_VERIFIER: String = "code_verifier"
    public const val DISPLAY: String = "display"
    public const val GRANT_TYPE: String = "grant_type"
    public const val ID_TOKEN_HINT: String = "id_token_hint"
    public const val LOGIN_HINT: String = "login_hint"
    public const val LOGOUT_HINT: String = "logout_hint"
    public const val MAX_AGE: String = "max_age"
    public const val NONCE: String = "nonce"
    public const val POST_LOGOUT_REDIRECT_URI: String = "post_logout_redirect_uri"
    public const val PROMPT: String = "prompt"
    public const val REDIRECT_URI: String = "redirect_uri"
    public const val REFRESH_TOKEN: String = "refresh_token"
    public const val RESPONSE_TYPE: String = "response_type"
    public const val SCOPE: String = "scope"
    public const val STATE: String = "state"
    public const val UI_LOCALES: String = "ui_locales"

    // Response
    public const val CODE: String = "code"
    public const val ERROR: String = "error"
    public const val ERROR_DESCRIPTION: String = "error_description"
    public const val ERROR_URI: String = "error_uri"

    internal val KNOWN_PARAMETERS =
        listOf(
            CLIENT_ID,
            CODE,
            CODE_CHALLENGE,
            CODE_CHALLENGE_METHOD,
            CODE_VERIFIER,
            DISPLAY,
            ERROR,
            ERROR_DESCRIPTION,
            ERROR_URI,
            GRANT_TYPE,
            ID_TOKEN_HINT,
            LOGIN_HINT,
            LOGOUT_HINT,
            MAX_AGE,
            NONCE,
            POST_LOGOUT_REDIRECT_URI,
            PROMPT,
            REDIRECT_URI,
            REFRESH_TOKEN,
            RESPONSE_TYPE,
            SCOPE,
            STATE,
            UI_LOCALES,
        )
}
