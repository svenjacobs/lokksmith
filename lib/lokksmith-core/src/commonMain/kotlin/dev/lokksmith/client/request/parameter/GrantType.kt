package dev.lokksmith.client.request.parameter

public enum class GrantType(internal val value: String) {
    AuthorizationCode("authorization_code"),
    RefreshToken("refresh_token");

    override fun toString(): String = value
}