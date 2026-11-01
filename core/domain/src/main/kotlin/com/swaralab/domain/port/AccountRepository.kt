package com.swaralab.domain.port

import com.swaralab.domain.model.Account

sealed interface Registration {
    data class Created(val account: Account) : Registration
    data object EmailTaken : Registration
}

sealed interface SignInAttempt {
    data class SignedIn(val account: Account) : SignInAttempt
    data object UnknownEmail : SignInAttempt
    data object WrongPassword : SignInAttempt
}

/**
 * Account storage. The current adapter is a file on the phone.
 * A later server adapter can replace it. Screens never see a password hash.
 */
interface AccountRepository {
    suspend fun register(id: String, email: String, displayName: String, password: String): Registration
    suspend fun signIn(email: String, password: String): SignInAttempt
    suspend fun current(): Account?
    suspend fun signOut()
}
