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
import dev.lokksmith.client.usecase.RunWithTokensOrResetUseCase.RunWithTokensOrResetScope

/**
 * Executes a block with valid tokens, and logs out the client locally if the OpenID provider
 * considers the tokens invalid (e.g., returns `invalid_grant`).
 *
 * This use case wraps [Client.runWithTokens], attempting to execute the provided lambda with fresh
 * and valid [Tokens]. If the OpenID provider responds with an OAuth error matching one of [errors]
 * (by default, only `invalid_grant`), the client’s local session is reset via [Client.resetTokens],
 * effectively logging out the user on this device. Other exceptions or errors are not handled and
 * will be rethrown.
 *
 * **Note:** Server-side invalidation is only detected when a token refresh is required. If the
 * tokens are still valid and do not need to be refreshed, the body of [invoke] is executed and no
 * invalidation check occurs.
 *
 * Typical usage is to ensure that if the user's session is revoked or otherwise invalidated on the
 * provider, the local client state is kept in sync by removing tokens and requiring
 * re-authentication.
 *
 * @param client The client instance to operate on.
 * @param errors List of OAuth error values that should trigger a local logout. Defaults to
 *   `invalid_grant`.
 * @see Client.runWithTokensOrReset
 * @see Client.runWithTokens
 * @see Client.resetTokens
 */
public class RunWithTokensOrResetUseCase(
    private val client: Client,
    private val errors: List<OAuthError> = DEFAULT_ERRORS,
) {

    public interface RunWithTokensOrResetScope {

        public suspend fun resetTokens()
    }

    private inner class RunWithTokensOrResetScopeImpl : RunWithTokensOrResetScope {

        var didCallResetTokens = false

        override suspend fun resetTokens() {
            didCallResetTokens = true
            client.resetTokens()
        }
    }

    /**
     * See [class documentation][RunWithTokensOrResetUseCase] for details.
     *
     * Token- or session-related exceptions that occur in your code provided via [body] cannot be
     * detected automatically. To force a reset from your own code, use
     * [RunWithTokensOrResetScope.resetTokens] provided as the context of [body].
     *
     * @return `true` if [body] was executed successfully; `false` if the client was logged out
     *   locally.
     * @see RunWithTokensOrResetUseCase
     */
    public suspend operator fun invoke(
        body: suspend RunWithTokensOrResetScope.(Tokens) -> Unit
    ): Boolean {
        val scope = RunWithTokensOrResetScopeImpl()

        try {
            client.runWithTokens { tokens -> scope.body(tokens) }
            return !scope.didCallResetTokens
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
 * Convenience function for [RunWithTokensOrResetUseCase].
 *
 * @see RunWithTokensOrResetUseCase.invoke
 */
public suspend fun Client.runWithTokensOrReset(
    errors: List<OAuthError> = DEFAULT_ERRORS,
    body: suspend RunWithTokensOrResetScope.(Tokens) -> Unit,
): Boolean = RunWithTokensOrResetUseCase(this, errors)(body)

private val DEFAULT_ERRORS = listOf(OAuthError.InvalidGrant)
