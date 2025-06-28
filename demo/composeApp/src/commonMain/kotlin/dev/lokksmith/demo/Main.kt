package dev.lokksmith.demo

import dev.lokksmith.Lokksmith
import dev.lokksmith.SingletonLokksmithProvider

fun setupApp(lokksmith: Lokksmith) {
    SingletonLokksmithProvider.set(lokksmith)
}
