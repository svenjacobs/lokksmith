package dev.lokksmith.client.request.parameter

public enum class ResponseType(internal val value: String) {
    Code("code");

    override fun toString(): String = value
}