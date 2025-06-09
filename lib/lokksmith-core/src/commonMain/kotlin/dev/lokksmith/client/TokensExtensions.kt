package dev.lokksmith.client

import dev.lokksmith.client.Client.Tokens

internal fun Tokens.areExpired(
    preemptiveRefreshSeconds: Int,
    leewaySeconds: Int,
    instantProvider: InstantProvider,
): Boolean = accessToken.isExpired(preemptiveRefreshSeconds, leewaySeconds, instantProvider) ||
    idToken.isExpired(preemptiveRefreshSeconds, leewaySeconds, instantProvider)

internal fun Tokens.Token.isExpired(
    preemptiveRefreshSeconds: Int,
    leewaySeconds: Int,
    instantProvider: InstantProvider,
): Boolean = expiresAt?.let { expiresAt ->
    instantProvider() >= expiresAt - preemptiveRefreshSeconds + leewaySeconds
} == true

internal fun Tokens.IdToken.isExpired(
    preemptiveRefreshSeconds: Int,
    leewaySeconds: Int,
    instantProvider: InstantProvider,
): Boolean = instantProvider() >= expiration - preemptiveRefreshSeconds + leewaySeconds
