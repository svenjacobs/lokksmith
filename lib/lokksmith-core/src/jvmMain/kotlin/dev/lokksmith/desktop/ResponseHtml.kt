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

import org.intellij.lang.annotations.Language

/**
 * Renders the HTML page shown to the user after the OAuth provider redirects back to the loopback
 * server. This is the only page the user sees as part of the desktop flow, so it's worth branding
 * (RFC 8252 §8.4 recommends using this opportunity to instruct the user to close the tab and return
 * to the app).
 *
 * Implementations must return a complete HTML document. Use [Default] for a minimal "you can close
 * this window" page, or supply a custom implementation for branding/styling.
 */
public fun interface ResponseHtml {

    /**
     * Renders the post-authentication HTML. Suspending so implementations can do IO (read from
     * resources, fetch templates, etc.). Called exactly once per server lifecycle on the calling
     * coroutine's context.
     */
    public suspend fun render(): String

    public companion object {
        /** Minimal default HTML shown after the Authorization Code Flow redirect. */
        public val defaultAuthorization: ResponseHtml = ResponseHtml {
            defaultHtml(title = "Sign-in completed", heading = "Sign-in completed")
        }

        /** Minimal default HTML shown after the End Session (RP-Initiated Logout) redirect. */
        public val defaultEndSession: ResponseHtml = ResponseHtml {
            defaultHtml(title = "Sign-out completed", heading = "Sign-out completed")
        }

        @Language("HTML")
        private fun defaultHtml(title: String, heading: String) =
            """
            <!DOCTYPE html>
            <html lang="en">
              <head>
                <meta charset="utf-8">
                <meta name="viewport" content="width=device-width, initial-scale=1">
                <meta name="color-scheme" content="light dark">
                <title>$title</title>
                <style>
                  :root {
                    color-scheme: light dark;
                    --bg: #faf8f5;
                    --fg: #1a1a1a;
                  }
                  @media (prefers-color-scheme: dark) {
                    :root {
                      --bg: #2d2d2d;
                      --fg: #f0f0f0;
                    }
                  }
                  html, body { margin: 0; padding: 0; }
                  body {
                    background: var(--bg);
                    color: var(--fg);
                    min-height: 100vh;
                    display: grid;
                    place-items: center;
                    font-family: system-ui, -apple-system, "Segoe UI", Roboto, sans-serif;
                    padding: 1.5rem;
                    text-align: center;
                  }
                  h1 { margin: 0 0 .5rem; font-size: 1.5rem; font-weight: 600; }
                  p  { margin: 0; font-size: .9rem; opacity: .75; }
                </style>
              </head>
              <body>
                <main>
                  <h1>$heading</h1>
                  <p>You can close this window and return to the app.</p>
                </main>
              </body>
            </html>
            """
                .trimIndent()
    }
}
