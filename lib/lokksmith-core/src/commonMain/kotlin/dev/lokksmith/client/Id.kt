package dev.lokksmith.client

import kotlinx.serialization.Serializable

@Serializable
public expect value class Id(public val value: String)

public fun String.asId(): Id = Id(this)
