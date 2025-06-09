package dev.lokksmith.client

import kotlinx.serialization.Serializable

@Serializable
@JvmInline
public actual value class Key(public val value: String)