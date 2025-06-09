package dev.lokksmith.client

import kotlinx.serialization.Serializable

@Serializable
@JvmInline
public actual value class Id(public val value: String)