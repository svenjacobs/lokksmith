package dev.lokksmith.client

import kotlinx.serialization.Serializable

@Serializable
public expect value class Key(public val value: String)

public fun String.asKey(): Key = Key(this)