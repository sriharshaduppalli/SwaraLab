package com.swaralab.domain.codec

import com.swaralab.domain.format.AccountFormat
import com.swaralab.domain.model.AccountFile
import kotlinx.serialization.json.Json

class AccountCodec(
    private val json: Json = Json {
        prettyPrint = true
        encodeDefaults = true
        ignoreUnknownKeys = true
        explicitNulls = false
    },
) {
    fun encode(file: AccountFile): String = json.encodeToString(AccountFile.serializer(), file)

    fun decode(text: String): AccountFile {
        val file = json.decodeFromString(AccountFile.serializer(), text)
        if (file.format != AccountFormat.NAME) {
            throw IllegalArgumentException("Expected ${AccountFormat.NAME} and found ${file.format}.")
        }
        if (file.version > AccountFormat.VERSION) {
            throw IllegalArgumentException(
                "This build reads account version ${AccountFormat.VERSION}. The file is version ${file.version}.",
            )
        }
        return file
    }
}
