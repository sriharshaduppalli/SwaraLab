package com.swaralab.session

import com.swaralab.domain.auth.PasswordHasher
import com.swaralab.domain.codec.AccountCodec
import com.swaralab.domain.model.Account
import com.swaralab.domain.model.AccountFile
import com.swaralab.domain.model.AccountRecord
import com.swaralab.domain.port.AccountRepository
import com.swaralab.domain.port.Registration
import com.swaralab.domain.port.SignInAttempt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * One account file on the phone. The password is hashed before it is written.
 * A crash mid-save leaves the previous file in place.
 */
class FileAccountRepository(
    private val file: File,
    private val codec: AccountCodec = AccountCodec(),
    private val hasher: PasswordHasher = PasswordHasher(),
) : AccountRepository {
    private val lock = Any()

    override suspend fun register(
        id: String,
        email: String,
        displayName: String,
        password: String,
    ): Registration = withContext(Dispatchers.IO) {
        synchronized(lock) {
            val current = read()
            if (current.accounts.any { it.account.email == email }) {
                return@synchronized Registration.EmailTaken
            }
            val account = Account(id = id, email = email, displayName = displayName)
            val salt = hasher.salt()
            val next = current.copy(
                accounts = current.accounts + AccountRecord(
                    account = account,
                    passwordSalt = salt,
                    passwordHash = hasher.hash(password, salt),
                ),
                currentId = account.id,
            )
            write(next)
            Registration.Created(account)
        }
    }

    override suspend fun signIn(email: String, password: String): SignInAttempt = withContext(Dispatchers.IO) {
        synchronized(lock) {
            val current = read()
            val record = current.accounts.firstOrNull { it.account.email == email }
                ?: return@synchronized SignInAttempt.UnknownEmail
            if (!hasher.matches(password, record.passwordSalt, record.passwordHash)) {
                return@synchronized SignInAttempt.WrongPassword
            }
            write(current.copy(currentId = record.account.id))
            SignInAttempt.SignedIn(record.account)
        }
    }

    override suspend fun current(): Account? = withContext(Dispatchers.IO) {
        synchronized(lock) {
            val stored = read()
            stored.accounts.firstOrNull { it.account.id == stored.currentId }?.account
        }
    }

    override suspend fun signOut() = withContext(Dispatchers.IO) {
        synchronized(lock) {
            write(read().copy(currentId = null))
        }
    }

    private fun read(): AccountFile {
        if (!file.isFile) return AccountFile()
        return codec.decode(file.readText(Charsets.UTF_8))
    }

    private fun write(stored: AccountFile) {
        file.parentFile?.mkdirs()
        val temporary = File(file.parentFile, "${file.name}.tmp")
        temporary.writeText(codec.encode(stored), Charsets.UTF_8)
        try {
            Files.move(
                temporary.toPath(),
                file.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE,
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(
                temporary.toPath(),
                file.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
            )
        }
    }
}
