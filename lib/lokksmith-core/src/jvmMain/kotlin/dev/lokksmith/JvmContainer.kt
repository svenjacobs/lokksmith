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

import dev.lokksmith.client.InternalClient
import dev.lokksmith.client.request.flow.RedirectUriHandler
import dev.lokksmith.desktop.JvmRedirectUriHandler
import kotlinx.coroutines.CoroutineScope

/**
 * JVM/Desktop-specific [Container] that exposes [DesktopOptions] alongside the standard container
 * dependencies.
 *
 * Other Lokksmith JVM modules (notably the Compose JVM platform launcher and the JVM
 * `RedirectUriHandler`) cast `lokksmith.container` to this interface to read desktop-specific
 * configuration. Application code must not interact with this interface directly.
 */
public interface JvmContainer : Container {
    public val desktopOptions: DesktopOptions
}

internal class JvmContainerImpl(
    private val delegate: Container,
    override val desktopOptions: DesktopOptions,
) : JvmContainer, Container by delegate {

    override val clientProviderFactory: () -> InternalClient.Provider = {
        JvmProvider(
            base = delegate.clientProviderFactory(),
            scope = delegate.coroutineScope,
            desktopOptions = desktopOptions,
        )
    }
}

private class JvmProvider(
    base: InternalClient.Provider,
    private val scope: CoroutineScope,
    private val desktopOptions: DesktopOptions,
) : InternalClient.Provider by base {
    override val redirectUriHandler: (InternalClient) -> RedirectUriHandler = { client ->
        JvmRedirectUriHandler(client = client, scope = scope, options = desktopOptions)
    }
}
