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
package dev.lokksmith.client.usecase

import dev.lokksmith.client.Client
import dev.lokksmith.client.Client.Tokens
import dev.lokksmith.client.request.OAuthError
import dev.lokksmith.client.request.OAuthResponseException

/**
 * Executes [Client.refresh] and logs out the client locally if the OpenID provider considers the
 * tokens invalid (e.g., returns `invalid_grant`).
 *
 * This use case wraps [Client.refresh], attempting to refresh the [Tokens]. If the OpenID provider
 * responds with an OAuth error matching one of [errors] (by default, only `invalid_grant`), the
 * client’s local session is reset via [Client.resetTokens], effectively logging out the user on
 * this device. Other exceptions or errors are not handled and will be rethrown.
 *
 * Typical usage is to ensure that if the user's session is revoked or otherwise invalidated on the
 * provider, the local client state is kept in sync by removing tokens and requiring
 * re-authentication.
 *
 * @param client The client instance to operate on.
 * @param errors List of OAuth error values that should trigger a local logout. Defaults to
 *   `invalid_grant`.
 * @see Client.refreshOrReset
 * @see Client.refresh
 * @see Client.resetTokens
 * @see RunWithTokensOrResetUseCase
 */
public class RefreshTokensOrResetUseCase(
    private val client: Client,
    private val errors: List<OAuthError> = DEFAULT_ERRORS,
) {

    /**
     * See [class documentation][RefreshTokensOrResetUseCase] for details.
     *
     * @return `true` if refresh was executed successfully; `false` if the client was logged out
     *   locally.
     * @see RefreshTokensOrResetUseCase
     */
    public suspend operator fun invoke(): Boolean {
        try {
            client.refresh()
            return true
        } catch (e: OAuthResponseException) {
            if (errors.contains(e.error)) {
                client.resetTokens()
                return false
            }
            throw e
        }
    }
}

/**
 * Convenience function for [RefreshTokensOrResetUseCase].
 *
 * @return `true` if refresh was executed successfully; `false` if the client was logged out
 *   locally.
 * @see RefreshTokensOrResetUseCase.invoke
 */
public suspend fun Client.refreshOrReset(errors: List<OAuthError> = DEFAULT_ERRORS): Boolean =
    RefreshTokensOrResetUseCase(this, errors)()
