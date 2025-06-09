package dev.lokksmith.client.request.parameter

/**
 * @see <a href="https://openid.net/specs/openid-connect-core-1_0.html#ScopeClaims">Requesting Claims using Scope Values</a>
 */
public enum class Scope(internal val value: String) {
    OpenId("openid"),
    Profile("profile"),
    Email("email"),
    Address("address"),
    Phone("phone");

    override fun toString(): String = value
}