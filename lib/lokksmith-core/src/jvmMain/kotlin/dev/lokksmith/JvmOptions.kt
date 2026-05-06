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
package dev.lokksmith

import dev.lokksmith.desktop.BrowserLauncher
import dev.lokksmith.desktop.DEFAULT_REDIRECT_PATH
import dev.lokksmith.desktop.ResponseHtml
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * JVM/Desktop configuration for [createLokksmith].
 *
 * Bundles the cross-platform [Lokksmith.Options] together with desktop-specific configuration so
 * the JVM `createLokksmith` overload takes a single `options` parameter.
 *
 * @property core Cross-platform Lokksmith configuration (persistence, HTTP, time, etc.).
 * @property desktop Desktop-specific configuration controlling the loopback redirect server and
 *   browser launching. See [DesktopOptions].
 */
public data class JvmOptions(
    val core: Lokksmith.Options = Lokksmith.Options(),
    val desktop: DesktopOptions = DesktopOptions(),
)

/**
 * JVM/Desktop-specific configuration applied when creating a [Lokksmith] instance.
 *
 * On desktop, the redirect URI is a loopback HTTP URL (RFC 8252 §7.3) bound to an ephemeral port by
 * Lokksmith itself, so the consumer-supplied `redirectUri` in
 * [dev.lokksmith.client.request.flow.AuthFlow.Request] is overridden at flow start. These options
 * configure how that loopback server is set up and how the browser is launched.
 *
 * @property redirectPath Path bound on the loopback server. Must start with `/`. The OpenID
 *   provider's allowed redirect URIs must include `http://127.0.0.1:*<redirectPath>` (RFC 8252 §7.3
 *   wildcard port).
 * @property responseHtml HTML page rendered to the browser tab after a successful redirect. RFC
 *   8252 §8.4 recommends instructing the user to close the tab and return to the app.
 * @property browserLauncher How to open the system browser. Defaults to [BrowserLauncher.System]
 *   which uses `java.awt.Desktop.browse`.
 * @property redirectTimeout How long to wait for the provider redirect before failing the flow.
 */
public data class DesktopOptions(
    val redirectPath: String = DEFAULT_REDIRECT_PATH,
    val responseHtml: ResponseHtml = ResponseHtml.Default,
    val browserLauncher: BrowserLauncher = BrowserLauncher.System,
    val redirectTimeout: Duration = 5.minutes,
)
