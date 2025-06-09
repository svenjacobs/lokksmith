package dev.lokksmith.android

import dev.lokksmith.Lokksmith
import kotlinx.coroutines.CoroutineScope

public class LokksmithContext(
    /**
     * Singleton instance of [Lokksmith].
     */
    public val lokksmith: Lokksmith,

    /**
     * A coroutine scope where [Lokksmith] can operate in when handling incoming responses.
     */
    public val coroutineScope: CoroutineScope,
)
