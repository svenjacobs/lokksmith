package dev.lokksmith.demo

import dev.lokksmith.Lokksmith
import kotlinx.coroutines.MainScope

object Container {

    /**
     * This property needs to be set up by the platform code before the application (UI) runs
     */
    lateinit var lokksmith: Lokksmith

    val mainScope = MainScope()
}
