package com.swaralab.domain

import com.swaralab.domain.codec.AccountCodec
import com.swaralab.domain.format.AccountFormat
import com.swaralab.domain.model.Account
import com.swaralab.domain.model.AccountFile
import com.swaralab.domain.model.AccountRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class AccountCodecTest {
    private val codec = AccountCodec()

    @Test
    fun roundTripKeepsTheSignedInAccount() {
        val file = AccountFile(
            accounts = listOf(
                AccountRecord(
                    account = Account("acct-1", "asha@example.com", "Asha"),
                    passwordSalt = "salt",
                    passwordHash = "hash",
                ),
            ),
            currentId = "acct-1",
        )
        val restored = codec.decode(codec.encode(file))
        assertEquals(AccountFormat.NAME, restored.format)
        assertEquals(AccountFormat.VERSION, restored.version)
        assertEquals(file.accounts, restored.accounts)
        assertEquals("acct-1", restored.currentId)
    }

    @Test
    fun unknownFieldsAreIgnoredAndANewerFileIsRefused() {
        val encoded = codec.encode(AccountFile()).replaceFirst("{", "{\n  \"futureField\": true,")
        assertEquals(AccountFormat.NAME, codec.decode(encoded).format)
        val newer = encoded.replace("\"version\": 1", "\"version\": 2")
        assertThrows(IllegalArgumentException::class.java) { codec.decode(newer) }
    }
}
