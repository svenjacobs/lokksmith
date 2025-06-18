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
package dev.lokksmith.client

import dev.lokksmith.client.Client.Tokens

val SAMPLE_TOKENS = Tokens(
    accessToken = Tokens.AccessToken(
        token = "62ygBBhY",
        expiresAt = TEST_INSTANT + 600,
    ),
    refreshToken = Tokens.RefreshToken(
        token = "bMGysPYch",
        expiresAt = null,
    ),
    idToken = Tokens.IdToken(
        issuer = "issuer",
        subject = "8582ce26-3994-42e7-afb0-39d42e18fd1f",
        audiences = listOf("clientId"),
        expiration = TEST_INSTANT + 600,
        issuedAt = TEST_INSTANT,
        raw = "rawIdToken",
    ),
)
