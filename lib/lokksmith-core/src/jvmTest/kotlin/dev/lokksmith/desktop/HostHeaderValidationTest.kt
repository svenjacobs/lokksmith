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
package dev.lokksmith.desktop

import java.net.InetAddress
import java.net.InetSocketAddress
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HostHeaderValidationTest {

    @Test
    fun `accepts exact IPv4 literal`() {
        val validator = validatorFor("127.0.0.1", 8080)
        assertTrue(validator.isValid("127.0.0.1:8080"))
    }

    @Test
    fun `accepts compressed IPv6 literal`() {
        val validator = validatorFor("::1", 8080)
        assertTrue(validator.isValid("[::1]:8080"))
    }

    @Test
    fun `accepts uncompressed IPv6 literal as equivalent to compressed`() {
        val validator = validatorFor("::1", 8080)
        assertTrue(validator.isValid("[0:0:0:0:0:0:0:1]:8080"))
    }

    @Test
    fun `rejects localhost hostname`() {
        // `localhost` resolves to loopback but is exactly the DNS-rebinding vector.
        val validator = validatorFor("127.0.0.1", 8080)
        assertFalse(validator.isValid("localhost:8080"))
    }

    @Test
    fun `rejects arbitrary hostname`() {
        val validator = validatorFor("127.0.0.1", 8080)
        assertFalse(validator.isValid("evil.example.com:8080"))
    }

    @Test
    fun `rejects mismatched port`() {
        val validator = validatorFor("127.0.0.1", 8080)
        assertFalse(validator.isValid("127.0.0.1:9090"))
    }

    @Test
    fun `rejects different IPv4 address`() {
        val validator = validatorFor("127.0.0.1", 8080)
        assertFalse(validator.isValid("127.0.0.2:8080"))
    }

    @Test
    fun `rejects null Host header`() {
        val validator = validatorFor("127.0.0.1", 8080)
        assertFalse(validator.isValid(null))
    }

    @Test
    fun `rejects malformed Host header`() {
        val validator = validatorFor("127.0.0.1", 8080)
        assertFalse(validator.isValid("not a host"))
        assertFalse(validator.isValid("127.0.0.1")) // missing port
        assertFalse(validator.isValid("[::1]")) // missing port
        assertFalse(validator.isValid("[::1]:abc")) // non-numeric port
        assertFalse(validator.isValid(":8080")) // empty host
        assertFalse(validator.isValid(""))
    }

    @Test
    fun `rejects port out of range`() {
        val validator = validatorFor("127.0.0.1", 8080)
        assertFalse(validator.isValid("127.0.0.1:0"))
        assertFalse(validator.isValid("127.0.0.1:65536"))
        assertFalse(validator.isValid("127.0.0.1:-1"))
    }

    @Test
    fun `rejects IPv4-shaped string with out-of-range octet`() {
        val validator = validatorFor("127.0.0.1", 8080)
        assertFalse(validator.isValid("999.0.0.1:8080"))
    }

    private fun validatorFor(host: String, port: Int): HostHeaderValidator =
        HostHeaderValidator(InetSocketAddress(InetAddress.getByName(host), port))
}
