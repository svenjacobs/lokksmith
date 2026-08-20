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
package dev.lokksmith.swift

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job

/** A handle to an ongoing observation. Cancel it to stop receiving callbacks. */
public class LokksmithCancellable internal constructor(private val job: Job) {

    /** `true` once [cancel] has been called, or the observation ended on its own. */
    public val isCancelled: Boolean
        get() = !job.isActive

    /** Stops the observation. Calling this more than once has no effect. */
    public fun cancel() {
        job.cancel()
    }
}

internal fun mainDispatcher(): CoroutineDispatcher = Dispatchers.Main
