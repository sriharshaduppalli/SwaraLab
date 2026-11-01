package com.swaralab.domain.usecase

import com.swaralab.domain.auth.AccountRules
import com.swaralab.domain.identity.IdGenerator
import com.swaralab.domain.model.Account
import com.swaralab.domain.port.AccountRepository
import com.swaralab.domain.port.Registration
import com.swaralab.domain.port.SignInAttempt

sealed interface AccountResult {
    data class Ready(val account: Account) : AccountResult
    data class Rejected(val reason: AccountReject) : AccountResult
}

enum class AccountReject {
    EMAIL_INVALID,
    PASSWORD_TOO_SHORT,
    NAME_MISSING,
    EMAIL_TAKEN,
    UNKNOWN_EMAIL,
    WRONG_PASSWORD,
}

class RegisterAccount(
    private val accounts: AccountRepository,
    private val ids: IdGenerator,
) {
    suspend operator fun invoke(displayName: String, email: String, password: String): AccountResult {
        val name = AccountRules.displayName(displayName)
            ?: return AccountResult.Rejected(AccountReject.NAME_MISSING)
        val normalized = AccountRules.email(email)
            ?: return AccountResult.Rejected(AccountReject.EMAIL_INVALID)
        if (!AccountRules.password(password)) {
            return AccountResult.Rejected(AccountReject.PASSWORD_TOO_SHORT)
        }
        return when (val stored = accounts.register(ids.newId(), normalized, name, password)) {
            is Registration.Created -> AccountResult.Ready(stored.account)
            Registration.EmailTaken -> AccountResult.Rejected(AccountReject.EMAIL_TAKEN)
        }
    }
}

class SignIn(
    private val accounts: AccountRepository,
) {
    suspend operator fun invoke(email: String, password: String): AccountResult {
        val normalized = AccountRules.email(email)
            ?: return AccountResult.Rejected(AccountReject.EMAIL_INVALID)
        if (!AccountRules.password(password)) {
            return AccountResult.Rejected(AccountReject.PASSWORD_TOO_SHORT)
        }
        return when (val attempt = accounts.signIn(normalized, password)) {
            is SignInAttempt.SignedIn -> AccountResult.Ready(attempt.account)
            SignInAttempt.UnknownEmail -> AccountResult.Rejected(AccountReject.UNKNOWN_EMAIL)
            SignInAttempt.WrongPassword -> AccountResult.Rejected(AccountReject.WRONG_PASSWORD)
        }
    }
}

class SignOut(
    private val accounts: AccountRepository,
) {
    suspend operator fun invoke() {
        accounts.signOut()
    }
}

class CurrentAccount(
    private val accounts: AccountRepository,
) {
    suspend operator fun invoke(): Account? = accounts.current()
}
