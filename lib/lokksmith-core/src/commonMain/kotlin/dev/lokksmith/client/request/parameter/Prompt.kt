package dev.lokksmith.client.request.parameter

/**
 * @see <a href="https://openid.net/specs/openid-connect-core-1_0.html#AuthRequest">Authentication Request</a>
 */
public enum class Prompt(internal val value: String) {
    None("none"),
    Login("login"),
    Consent("consent"),
    SelectAccount("select_account");

    override fun toString(): String = value
}