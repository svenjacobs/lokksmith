package dev.lokksmith.client.token

import dev.lokksmith.client.jwt.JwtDecoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals

@Suppress("SpellCheckingInspection")
class JwtToIdTokenMapperTest {

    private val mapper = JwtToIdTokenMapper()
    private val jwtDecoder = JwtDecoder(Json)

    @Test
    fun `invoke() should successfully map JWT payload to an ID Token`() {
        val raw =
            "eyJ0eXAiOiJKV1QiLCJhbGciOiJSUzI1NiIsImtpZCI6IjFMVE16YWtpaGlSbGFfOHoyQkVKVlhlV01xbyJ9.eyJ2ZXIiOiIyLjAiLCJpc3MiOiJodHRwczovL2xvZ2luLm1pY3Jvc29mdG9ubGluZS5jb20vOTEyMjA0MGQtNmM2Ny00YzViLWIxMTItMzZhMzA0YjY2ZGFkL3YyLjAiLCJzdWIiOiJBQUFBQUFBQUFBQUFBQUFBQUFBQUFJa3pxRlZyU2FTYUZIeTc4MmJidGFRIiwiYXVkIjoiNmNiMDQwMTgtYTNmNS00NmE3LWI5OTUtOTQwYzc4ZjVhZWYzIiwiZXhwIjoxNTM2MzYxNDExLCJpYXQiOjE1MzYyNzQ3MTEsIm5iZiI6MTUzNjI3NDcxMSwibmFtZSI6IkFiZSBMaW5jb2xuIiwicHJlZmVycmVkX3VzZXJuYW1lIjoiQWJlTGlAbWljcm9zb2Z0LmNvbSIsIm9pZCI6IjAwMDAwMDAwLTAwMDAtMDAwMC02NmYzLTMzMzJlY2E3ZWE4MSIsInRpZCI6IjkxMjIwNDBkLTZjNjctNGM1Yi1iMTEyLTM2YTMwNGI2NmRhZCIsIm5vbmNlIjoiMTIzNTIzIiwiYWlvIjoiRGYyVVZYTDFpeCFsTUNXTVNPSkJjRmF0emNHZnZGR2hqS3Y4cTVnMHg3MzJkUjVNQjVCaXN2R1FPN1lXQnlqZDhpUURMcSFlR2JJRGFreXA1bW5PcmNkcUhlWVNubHRlcFFtUnA2QUlaOGpZIn0.1AFWW-Ck5nROwSlltm7GzZvDwUkqvhSQpm55TQsmVo9Y59cLhRXpvB8n-55HCr9Z6G_31_UbeUkoz612I2j_Sm9FFShSDDjoaLQr54CreGIJvjtmS3EkK9a7SJBbcpL1MpUtlfygow39tFjY7EVNW9plWUvRrTgVk7lYLprvfzw-CIqw3gHC-T7IK_m_xkr08INERBtaecwhTeN4chPC4W3jdmw_lIxzC48YoQ0dB1L9-ImX98Egypfrlbm0IBL5spFzL6JDZIRRJOu8vecJvj1mq-IUhGt0MacxX8jdxYLP-KUu2d9MbNKpCKJuZ7p8gwTL5B7NlUdh_dmSviPWrw"

        val jwt = jwtDecoder.decode(raw)
        val idToken = mapper(jwt, raw)

        assertEquals(
            "https://login.microsoftonline.com/9122040d-6c67-4c5b-b112-36a304b66dad/v2.0",
            idToken.issuer,
        )
        assertEquals(
            "AAAAAAAAAAAAAAAAAAAAAIkzqFVrSaSaFHy782bbtaQ",
            idToken.subject,
        )
        assertEquals(
            listOf("6cb04018-a3f5-46a7-b995-940c78f5aef3"),
            idToken.audiences,
        )
        assertEquals(
            1536361411,
            idToken.expiration,
        )
        assertEquals(
            1536274711,
            idToken.issuedAt,
        )
        assertEquals(
            1536274711,
            idToken.notBefore,
        )
        assertEquals(
            "123523",
            idToken.nonce,
        )
        assertEquals(
            raw,
            idToken.raw,
        )
        assertEquals(
            "2.0",
            idToken.extra["ver"]?.jsonPrimitive?.content,
        )
        assertEquals(
            "Abe Lincoln",
            idToken.claims.name,
        )
        assertEquals(
            "AbeLi@microsoft.com",
            idToken.claims.preferredUsername,
        )
        assertEquals(
            "00000000-0000-0000-66f3-3332eca7ea81",
            idToken.extra["oid"]?.jsonPrimitive?.content,
        )
        assertEquals(
            "9122040d-6c67-4c5b-b112-36a304b66dad",
            idToken.extra["tid"]?.jsonPrimitive?.content,
        )
        assertEquals(
            "Df2UVXL1ix!lMCWMSOJBcFatzcGfvFGhjKv8q5g0x732dR5MB5BisvGQO7YWByjd8iQDLq!eGbIDakyp5mnOrcdqHeYSnltepQmRp6AIZ8jY",
            idToken.extra["aio"]?.jsonPrimitive?.content,
        )
    }
}
