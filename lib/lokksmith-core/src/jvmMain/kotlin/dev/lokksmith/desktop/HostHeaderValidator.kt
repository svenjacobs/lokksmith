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

/**
 * Validates that an HTTP `Host` header refers to the loopback address+port that
 * [LoopbackRedirectServer] is bound to.
 *
 * The comparison is byte-equal on `InetAddress`, so different textual forms of the same IP — for
 * example `[::1]:PORT` and `[0:0:0:0:0:0:0:1]:PORT` — are treated as equivalent. DNS hostnames
 * (such as `localhost`) are rejected: accepting them would defeat the DNS-rebinding defence because
 * an attacker can rebind any hostname to a loopback IP.
 */
internal class HostHeaderValidator(private val bound: InetSocketAddress) {

    /** Returns `true` iff [hostHeader] names the bound IP literal and port. */
    fun isValid(hostHeader: String?): Boolean {
        if (hostHeader == null) return false
        val parsed = parseHostHeader(hostHeader) ?: return false
        if (parsed.port != bound.port) return false
        return parsed.address == bound.address
    }

    private fun parseHostHeader(host: String): InetSocketAddress? {
        val (ipPart, portPart) =
            if (host.startsWith("[")) {
                val end = host.indexOf(']')
                if (end < 0 || end + 1 >= host.length || host[end + 1] != ':') return null
                host.substring(1, end) to host.substring(end + 2)
            } else {
                val colon = host.lastIndexOf(':')
                if (colon < 0) return null
                host.substring(0, colon) to host.substring(colon + 1)
            }
        val port = portPart.toIntOrNull() ?: return null
        if (port !in MIN_TCP_PORT..MAX_TCP_PORT) return null
        val address = parseIpLiteral(ipPart) ?: return null
        return InetSocketAddress(address, port)
    }

    private fun parseIpLiteral(s: String): InetAddress? {
        if (!isIpLiteral(s)) return null
        return runCatching { InetAddress.getByName(s) }.getOrNull()
    }

    private fun isIpLiteral(s: String): Boolean {
        if (':' in s) return true // IPv6 literal — only IP literals contain ':' in a Host header.
        val parts = s.split('.')
        if (parts.size != IPV4_OCTET_COUNT) return false
        return parts.all { p ->
            p.isNotEmpty() && p.all { it.isDigit() } && p.toInt() in MIN_OCTET..MAX_OCTET
        }
    }

    private companion object {
        const val MIN_TCP_PORT = 1
        const val MAX_TCP_PORT = 65535
        const val IPV4_OCTET_COUNT = 4
        const val MIN_OCTET = 0
        const val MAX_OCTET = 255
    }
}
