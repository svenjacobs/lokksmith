package dev.lokksmith.android

import android.content.Context
import dev.lokksmith.Lokksmith

/**
 * Must be implemented by the app's [android.app.Application] class to provide a [LokksmithContext]
 * which contains the singleton instance of [dev.lokksmith.Lokksmith] as well as a
 * [kotlin.coroutines.CoroutineContext] for Lokksmith to handle incoming responses.
 *
 * The context is required for handling results from different authorization and authentication
 * flows.
 */
public interface LokksmithContextProvider {

    /**
     * Must return a [LokksmithContext] which holds the singleton instance of
     * [dev.lokksmith.Lokksmith] used in this app as well as a [kotlin.coroutines.CoroutineContext]
     * for Lokksmith to handle incoming responses.
     */
    public val lokksmithContext: LokksmithContext
}

/**
 * Retrieves the singleton instance of [dev.lokksmith.Lokksmith] from the given [Context].
 *
 * This function expects that the app's [android.app.Application] class implements
 * [LokksmithContextProvider].
 *
 * @return The singleton [dev.lokksmith.Lokksmith] instance used by the application.
 * @throws kotlin.IllegalArgumentException if the application context does not implement [LokksmithContextProvider].
 */
public val Context.lokksmith: Lokksmith
    get() = requireLokksmithContext(this).lokksmith

internal fun requireLokksmithContext(context: Context): LokksmithContext {
    val context = context.applicationContext
    require(context is LokksmithContextProvider) { "Application must implement LokksmithContextProvider" }
    return context.lokksmithContext
}
