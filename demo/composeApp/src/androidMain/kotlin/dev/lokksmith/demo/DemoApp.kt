package dev.lokksmith.demo

import android.app.Application
import dev.lokksmith.createLokksmith

class DemoApp : Application() {

    override fun onCreate() {
        super.onCreate()

        setupApp(
            lokksmith = createLokksmith(this)
        )
    }
}
