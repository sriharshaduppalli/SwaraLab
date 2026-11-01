package com.swaralab.domain.codec

import com.swaralab.domain.format.SessionFormat
import com.swaralab.domain.model.SessionDocument
import kotlinx.serialization.json.Json

class SessionCodec(
    private val json: Json = Json {
        prettyPrint = true
        encodeDefaults = true
        ignoreUnknownKeys = true
        explicitNulls = false
    },
) {
    fun encode(document: SessionDocument): String = json.encodeToString(SessionDocument.serializer(), document)

    fun decode(text: String): SessionDocument {
        val document = json.decodeFromString(SessionDocument.serializer(), text)
        if (document.format != SessionFormat.NAME) {
            throw IllegalArgumentException("Expected ${SessionFormat.NAME} and found ${document.format}.")
        }
        if (document.version > SessionFormat.VERSION) {
            throw IllegalArgumentException(
                "This build reads session version ${SessionFormat.VERSION}. The file is version ${document.version}.",
            )
        }
        return document
    }
}
