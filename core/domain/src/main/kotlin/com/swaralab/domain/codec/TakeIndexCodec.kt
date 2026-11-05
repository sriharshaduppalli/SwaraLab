package com.swaralab.domain.codec

import com.swaralab.domain.model.TakeIndex
import kotlinx.serialization.json.Json

class TakeIndexCodec(
    private val json: Json = Json {
        prettyPrint = true
        encodeDefaults = true
        ignoreUnknownKeys = true
        explicitNulls = false
    },
) {
    fun encode(index: TakeIndex): String = json.encodeToString(TakeIndex.serializer(), index)

    fun decode(text: String): TakeIndex =
        runCatching { json.decodeFromString(TakeIndex.serializer(), text) }.getOrDefault(TakeIndex())
}
