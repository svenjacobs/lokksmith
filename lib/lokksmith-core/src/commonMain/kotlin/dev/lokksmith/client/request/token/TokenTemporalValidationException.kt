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
package dev.lokksmith.client.request.token

/**
 * Thrown when validation of a temporal claim in a token fails.
 *
 * This exception indicates that a time-based property such as `exp` (expiration), `iat` (issued
 * at), or `nbf` (not before) in a token is invalid or does not satisfy the expected constraints
 * during validation.
 *
 * @see dev.lokksmith.client.Client.Options.leewaySeconds
 */
public class TokenTemporalValidationException
internal constructor(message: String? = null, cause: Throwable? = null) :
    TokenValidationException(message = message, cause = cause)
