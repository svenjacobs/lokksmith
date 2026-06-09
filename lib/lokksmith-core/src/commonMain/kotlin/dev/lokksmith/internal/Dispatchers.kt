/*
 * Copyright 2026 Sven Jacobs
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
package dev.lokksmith.internal

import kotlinx.coroutines.CoroutineDispatcher

// Platform-specific CoroutineDispatcher declarations. Centralizing them here lets platforms that
// lack a particular dispatcher substitute an appropriate alternative. Add further dispatchers below
// as needed.

/**
 * Dispatcher used for blocking I/O work such as network requests.
 *
 * Maps to `Dispatchers.IO` on platforms that have a dedicated I/O dispatcher (Android, JVM,
 * native). On the Web (Kotlin/Wasm), which is single-threaded and has no `Dispatchers.IO`, it falls
 * back to `Dispatchers.Default`; network requests there are non-blocking (fetch) so no dedicated
 * I/O thread pool is required.
 */
internal expect val ioDispatcher: CoroutineDispatcher
