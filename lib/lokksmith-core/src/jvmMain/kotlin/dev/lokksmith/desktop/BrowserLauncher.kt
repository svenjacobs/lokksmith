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

import java.awt.Desktop
import java.awt.GraphicsEnvironment
import java.io.IOException
import java.net.URI

/**
 * Opens a URL in the user's external system browser.
 *
 * RFC 8252 §8.12 forbids embedded user-agents (WebViews) for the OAuth flow on native apps —
 * implementations of this interface MUST spawn an external browser, never embed one.
 *
 * Default: [System].
 */
public fun interface BrowserLauncher {

    /** Opens [url] in a browser. Throws [IllegalStateException] if no browser can be launched. */
    public fun open(url: String)

    public companion object {
        /**
         * Default implementation backed by [java.awt.Desktop.browse]. Works on macOS and Windows
         * out of the box, and on Linux desktops with a registered URL handler.
         *
         * If [Desktop.browse] is unavailable (headless JVM, or a Linux setup without AWT desktop
         * integration), throws [IllegalStateException] from [open]. For supporting cases beyond
         * default JVM implementation a custom [BrowserLauncher] can be provided.
         */
        public val System: BrowserLauncher = SystemBrowserLauncher()
    }
}

private class SystemBrowserLauncher : BrowserLauncher {
    override fun open(url: String) {
        val desktop =
            systemDesktop()
                ?: throw IllegalStateException(
                    "java.awt.Desktop.browse is not available (headless or unsupported). " +
                        "Provide a custom BrowserLauncher to launch the browser via another mechanism."
                )
        try {
            desktop.browse(URI(url))
        } catch (e: IOException) {
            throw IllegalStateException("Could not launch system browser for '$url'.", e)
        }
    }
}

private fun systemDesktop(): Desktop? =
    runCatching {
            when {
                GraphicsEnvironment.isHeadless() -> null
                !Desktop.isDesktopSupported() -> null
                else -> Desktop.getDesktop().takeIf { it.isSupported(Desktop.Action.BROWSE) }
            }
        }
        .getOrNull()
