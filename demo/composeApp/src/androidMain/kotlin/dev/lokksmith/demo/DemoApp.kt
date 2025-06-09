package dev.lokksmith.demo

import android.app.Application
import dev.lokksmith.android.LokksmithContext
import dev.lokksmith.android.LokksmithContextProvider
import dev.lokksmith.createLokksmith
import kotlinx.coroutines.MainScope

class DemoApp : Application(), LokksmithContextProvider {

    /**
     * Provides the necessary context for Lokksmith to handle responses in
     * [dev.lokksmith.android.LokksmithAuthFlowActivity].
     */
    override val lokksmithContext = LokksmithContext(
        lokksmith = createLokksmith(this),
        coroutineScope = MainScope(),
    )

    override fun onCreate() {
        super.onCreate()

        // Also pass Lokksmith instance to container which is required for common code.
        // In a plain Android application this wouldn't be necessary and the Lokksmith instance
        // could be retrieved via the application `Context`.
        Container.lokksmith = lokksmithContext.lokksmith
    }
}
