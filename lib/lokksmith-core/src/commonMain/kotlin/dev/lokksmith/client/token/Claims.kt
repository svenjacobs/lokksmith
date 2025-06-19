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
package dev.lokksmith.client.token

import dev.lokksmith.client.Client.Tokens.IdToken
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * Standard Claims
 *
 * @see <a href="https://openid.net/specs/openid-connect-core-1_0.html#StandardClaims">Standard
 *   Claim</a>
 * @see [IdToken]
 * @see [IdToken.claims]
 */
public class Claims(private val idToken: IdToken) {
    /**
     * @see <a href="https://openid.net/specs/openid-connect-core-1_0.html#AddressClaim">Address
     *   Claim</a>
     */
    public data class Address(
        val formatted: String?,
        val streetAddress: String?,
        val locality: String?,
        val region: String?,
        val country: String?,
    )

    public val name: String?
        get() = idToken.extra["name"]?.jsonPrimitive?.contentOrNull

    public val givenName: String?
        get() = idToken.extra["given_name"]?.jsonPrimitive?.contentOrNull

    public val familyName: String?
        get() = idToken.extra["family_name"]?.jsonPrimitive?.contentOrNull

    public val middleName: String?
        get() = idToken.extra["middle_name"]?.jsonPrimitive?.contentOrNull

    public val nickname: String?
        get() = idToken.extra["nickname"]?.jsonPrimitive?.contentOrNull

    public val preferredUsername: String?
        get() = idToken.extra["preferred_username"]?.jsonPrimitive?.contentOrNull

    public val profile: String?
        get() = idToken.extra["profile"]?.jsonPrimitive?.contentOrNull

    public val picture: String?
        get() = idToken.extra["picture"]?.jsonPrimitive?.contentOrNull

    public val website: String?
        get() = idToken.extra["website"]?.jsonPrimitive?.contentOrNull

    public val email: String?
        get() = idToken.extra["email"]?.jsonPrimitive?.contentOrNull

    public val emailVerified: Boolean?
        get() = idToken.extra["email_verified"]?.jsonPrimitive?.booleanOrNull

    public val gender: String?
        get() = idToken.extra["gender"]?.jsonPrimitive?.contentOrNull

    public val birthdate: String?
        get() = idToken.extra["birthdate"]?.jsonPrimitive?.contentOrNull

    public val zoneInfo: String?
        get() = idToken.extra["zoneinfo"]?.jsonPrimitive?.contentOrNull

    public val locale: String?
        get() = idToken.extra["locale"]?.jsonPrimitive?.contentOrNull

    public val phoneNumber: String?
        get() = idToken.extra["phone_number"]?.jsonPrimitive?.contentOrNull

    public val phoneNumberVerified: Boolean?
        get() = idToken.extra["phone_number_verified"]?.jsonPrimitive?.booleanOrNull

    public val address: Address?
        get() =
            idToken.extra["address"]?.jsonObject?.let { adr ->
                Address(
                    formatted = adr["formatted"]?.jsonPrimitive?.contentOrNull,
                    streetAddress = adr["street_address"]?.jsonPrimitive?.contentOrNull,
                    locality = adr["locality"]?.jsonPrimitive?.contentOrNull,
                    region = adr["region"]?.jsonPrimitive?.contentOrNull,
                    country = adr["country"]?.jsonPrimitive?.contentOrNull,
                )
            }

    public val updatedAt: Long?
        get() = idToken.extra["updated_at"]?.jsonPrimitive?.longOrNull
}

public val IdToken.claims: Claims
    get() = Claims(this)
