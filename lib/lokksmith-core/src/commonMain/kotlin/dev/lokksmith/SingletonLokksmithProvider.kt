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

import dev.lokksmith.SingletonLokksmithProvider.set
import kotlinx.coroutines.CoroutineScope

/**
 * Provides a singleton [Lokksmith] instance and a [CoroutineScope] which for example are used by
 * Lokksmith in response handlers. This object must be initialized with [set] as soon as possible
 * during application startup, e.g. in the `Application` class of an Android app.
 *
 * @see [set]
 */
public object SingletonLokksmithProvider {

    private var _lokksmith: Lokksmith? = null
    private var _coroutineScope: CoroutineScope? = null

    public val lokksmith: Lokksmith
        get() =
            checkNotNull(_lokksmith) {
                "SingletonLokksmithProvider must be initialized via SingletonLokksmithProvider.set()"
            }

    public val coroutineScope: CoroutineScope
        get() =
            checkNotNull(_coroutineScope) {
                "SingletonLokksmithProvider must be initialized via SingletonLokksmithProvider.set()"
            }

    public fun set(lokksmith: Lokksmith, coroutineScope: CoroutineScope) {
        _lokksmith = lokksmith
        _coroutineScope = coroutineScope
    }
}
