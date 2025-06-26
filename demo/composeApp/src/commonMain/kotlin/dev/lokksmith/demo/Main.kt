package dev.lokksmith.demo

import dev.lokksmith.Lokksmith
import dev.lokksmith.SingletonLokksmithProvider
import kotlinx.coroutines.MainScope

fun setupApp(lokksmith: Lokksmith) {
    SingletonLokksmithProvider.set(
        lokksmith = lokksmith,
        coroutineScope = MainScope(),
    )
}
