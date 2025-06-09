package dev.lokksmith.serialization

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonTransformingSerializer
import kotlinx.serialization.serializer

internal class StringListSerializer : JsonTransformingSerializer<List<String>>(
    serializer<List<String>>()
) {

    /**
     * Always returns a [JsonArray] by wrapping single JSON string elements in an array.
     */
    override fun transformDeserialize(element: JsonElement): JsonElement =
        element as? JsonArray ?: JsonArray(listOf(element))
}