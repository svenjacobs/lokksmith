package dev.lokksmith.client.request

import dev.whyoleg.cryptography.random.CryptographyRandom

internal class Random(
    private val random: CryptographyRandom = CryptographyRandom.Default,
) {

    fun randomAsciiString(length: Int): String {
        require(length > 0) { "length must be greater than 0" }
        return randomString(length, asciiChars)
    }

    /**
     * @see <a href="https://www.rfc-editor.org/rfc/rfc7636.html#section-4.1">PKCE 4.1 "Client Creates a Code Verifier"</a>
     */
    fun randomCodeVerifier(length: Int): String {
        require(length in 43..128) { "length must be between 43 and 128" }
        return randomString(length, codeVerifierChars)
    }

    private fun randomString(length: Int, chars: List<Char>) =
        (0 until length)
            .map { chars[random.nextInt(chars.size)] }
            .joinToString("")

    internal companion object {
        internal val asciiChars = ('0'..'9') + ('A'..'Z') + ('a'..'z')
        internal val codeVerifierChars = asciiChars + '-' + '.' + '_' + '~'
    }
}