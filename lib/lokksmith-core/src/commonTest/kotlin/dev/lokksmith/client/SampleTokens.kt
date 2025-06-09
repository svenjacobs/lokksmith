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
