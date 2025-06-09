package dev.lokksmith.client

import kotlinx.datetime.Clock

internal typealias InstantProvider = () -> Long

internal val DefaultInstantProvider: InstantProvider = { Clock.System.now().epochSeconds }
