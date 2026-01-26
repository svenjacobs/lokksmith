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
package dev.lokksmith.client.request.parameter

/**
 * @see <a href="https://openid.net/specs/openid-connect-core-1_0.html#ScopeClaims">Requesting
 *   Claims using Scope Values</a>
 */
public sealed class Scope(internal val value: String) {
    public data object OpenId : Scope("openid")

    public data object Profile : Scope("profile")

    public data object Email : Scope("email")

    public data object Address : Scope("address")

    public data object Phone : Scope("phone")

    public data class Custom(val scope: String) : Scope(scope)
}
