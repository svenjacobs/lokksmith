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
import dev.lokksmith.desktop.ResponseHtml
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.time.Duration.Companion.seconds

class LokksmithJvmTest {

    private val tempDir = createTempDirectory("lokksmith-test-").toFile()

    @AfterTest
    fun cleanUp() {
        tempDir.deleteRecursively()
    }

    @Test
    fun `createLokksmith uses default DesktopOptions when none supplied`() {
        val lokksmith = createLokksmith(dataDirectory = DataDirectory.Custom(tempDir))
        try {
            val container = assertIs<JvmContainer>(lokksmith.container)
            assertEquals(DesktopOptions(), container.desktopOptions)
        } finally {
            lokksmith.dispose()
        }
    }

    @Test
    fun `createLokksmith exposes supplied DesktopOptions on the container`() {
        val authorizationHtml = ResponseHtml { "<html><body>authorization done</body></html>" }
        val endSessionHtml = ResponseHtml { "<html><body>session ended</body></html>" }
        val customBrowser = BrowserLauncher { /* no-op */ }
        val desktop =
            DesktopOptions(
                redirectPath = "/auth-callback",
                authorizationResponseHtml = authorizationHtml,
                endSessionResponseHtml = endSessionHtml,
                browserLauncher = customBrowser,
                redirectTimeout = 30.seconds,
            )

        val lokksmith =
            createLokksmith(
                dataDirectory = DataDirectory.Custom(tempDir),
                options = Lokksmith.Options(userAgent = "Test/1.0"),
                desktop = desktop,
            )
        try {
            val container = assertIs<JvmContainer>(lokksmith.container)
            assertEquals("/auth-callback", container.desktopOptions.redirectPath)
            assertSame(authorizationHtml, container.desktopOptions.authorizationResponseHtml)
            assertSame(endSessionHtml, container.desktopOptions.endSessionResponseHtml)
            assertSame(customBrowser, container.desktopOptions.browserLauncher)
            assertEquals(30.seconds, container.desktopOptions.redirectTimeout)
        } finally {
            lokksmith.dispose()
        }
    }
}
