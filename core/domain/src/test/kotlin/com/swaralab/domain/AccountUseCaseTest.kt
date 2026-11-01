package com.swaralab.domain

import com.swaralab.domain.identity.IdGenerator
import com.swaralab.domain.model.Account
import com.swaralab.domain.port.AccountRepository
import com.swaralab.domain.port.Registration
import com.swaralab.domain.port.SignInAttempt
import com.swaralab.domain.usecase.AccountReject
import com.swaralab.domain.usecase.AccountResult
import com.swaralab.domain.usecase.CurrentAccount
import com.swaralab.domain.usecase.RegisterAccount
import com.swaralab.domain.usecase.SignIn
import com.swaralab.domain.usecase.SignOut
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountUseCaseTest {
    @Test
    fun registerSignsInAndRejectsASecondCopyOfTheSameEmail() = runBlocking {
        val store = MemoryAccounts()
        val register = RegisterAccount(store, IdGenerator { "acct-1" })
        val created = register("  Asha   Rao  ", "Asha@Example.com", "sing-well")
        val account = (created as AccountResult.Ready).account
        assertEquals("acct-1", account.id)
        assertEquals("asha@example.com", account.email)
        assertEquals("Asha Rao", account.displayName)
        assertEquals(account, CurrentAccount(store)())
        assertEquals("sing-well", store.passwords.single())

        val again = RegisterAccount(store, IdGenerator { "acct-2" })("Asha", "asha@example.com", "another-one")
        assertEquals(AccountReject.EMAIL_TAKEN, (again as AccountResult.Rejected).reason)
        assertEquals(account, CurrentAccount(store)())
    }

    @Test
    fun signInRejectsAnUnknownEmailAndAWrongPassword() = runBlocking {
        val store = MemoryAccounts()
        RegisterAccount(store, IdGenerator { "acct-1" })("Asha", "asha@example.com", "sing-well")
        SignOut(store)()
        assertNull(CurrentAccount(store)())

        val signIn = SignIn(store)
        assertEquals(AccountReject.UNKNOWN_EMAIL, (signIn("other@example.com", "sing-well") as AccountResult.Rejected).reason)
        assertEquals(AccountReject.WRONG_PASSWORD, (signIn("asha@example.com", "not-the-one") as AccountResult.Rejected).reason)
        assertNull(CurrentAccount(store)())

        val ready = signIn("  Asha@Example.com ", "sing-well") as AccountResult.Ready
        assertEquals("asha@example.com", ready.account.email)
        assertEquals(ready.account, CurrentAccount(store)())
    }

    @Test
    fun blankNameShortPasswordAndBadEmailNeverReachTheStore() = runBlocking {
        val store = MemoryAccounts()
        val register = RegisterAccount(store, IdGenerator { "acct-1" })
        assertEquals(AccountReject.NAME_MISSING, (register("   ", "asha@example.com", "sing-well") as AccountResult.Rejected).reason)
        assertEquals(AccountReject.EMAIL_INVALID, (register("Asha", "asha", "sing-well") as AccountResult.Rejected).reason)
        assertEquals(AccountReject.PASSWORD_TOO_SHORT, (register("Asha", "asha@example.com", "short") as AccountResult.Rejected).reason)
        assertTrue(store.passwords.isEmpty())
        assertNull(CurrentAccount(store)())
    }
}

private class MemoryAccounts : AccountRepository {
    val passwords = mutableListOf<String>()
    private val accounts = mutableListOf<Account>()
    private var currentId: String? = null

    override suspend fun register(id: String, email: String, displayName: String, password: String): Registration {
        if (accounts.any { it.email == email }) return Registration.EmailTaken
        val account = Account(id, email, displayName)
        accounts += account
        passwords += password
        currentId = id
        return Registration.Created(account)
    }

    override suspend fun signIn(email: String, password: String): SignInAttempt {
        val account = accounts.firstOrNull { it.email == email } ?: return SignInAttempt.UnknownEmail
        val index = accounts.indexOf(account)
        if (passwords[index] != password) return SignInAttempt.WrongPassword
        currentId = account.id
        return SignInAttempt.SignedIn(account)
    }

    override suspend fun current(): Account? = accounts.firstOrNull { it.id == currentId }

    override suspend fun signOut() {
        currentId = null
    }
}
