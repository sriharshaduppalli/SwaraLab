package com.swaralab.session

import com.swaralab.domain.auth.PasswordHasher
import com.swaralab.domain.codec.AccountCodec
import com.swaralab.domain.port.Registration
import com.swaralab.domain.port.SignInAttempt
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class FileAccountRepositoryTest {
    @Test
    fun registerSignsInAndTheFileDoesNotKeepThePassword() = runBlocking {
        val folder = Files.createTempDirectory("swaralab-accounts").toFile()
        try {
            val file = File(folder, "accounts.json")
            val repository = FileAccountRepository(file, AccountCodec(), PasswordHasher(iterations = 1_000))
            val created = repository.register("acct-1", "asha@example.com", "Asha", "sing-well") as Registration.Created
            assertEquals("Asha", repository.current()?.displayName)
            assertFalse(file.readText().contains("sing-well"))
            assertFalse(File(folder, "accounts.json.tmp").exists())

            repository.signOut()
            assertNull(repository.current())
            val signedIn = repository.signIn("asha@example.com", "sing-well")
            assertTrue(signedIn is SignInAttempt.SignedIn)
            assertEquals(created.account, repository.current())
            assertTrue(repository.signIn("asha@example.com", "nope") is SignInAttempt.WrongPassword)
            assertEquals(created.account, repository.current())
            assertTrue(
                repository.register("acct-2", "asha@example.com", "Other", "sing-well") is Registration.EmailTaken,
            )
        } finally {
            folder.deleteRecursively()
        }
    }
}
