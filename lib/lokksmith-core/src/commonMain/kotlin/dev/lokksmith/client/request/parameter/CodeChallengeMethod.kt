package dev.lokksmith.client.request.parameter

/**
 * @see <a href="v">Proof Key for Code Exchange by OAuth Public Clients</a>
 */
public enum class CodeChallengeMethod(internal val value: String) {
    SHA256("S256");

    override fun toString(): String = value
}