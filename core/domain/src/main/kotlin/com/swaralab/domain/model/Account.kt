package com.swaralab.domain.model

import com.swaralab.domain.format.AccountFormat
import kotlinx.serialization.Serializable

@Serializable
data class Account(
    val id: String,
    val email: String,
    val displayName: String,
)

@Serializable
data class AccountRecord(
    val account: Account,
    val passwordSalt: String,
    val passwordHash: String,
)

@Serializable
data class AccountFile(
    val format: String = AccountFormat.NAME,
    val version: Int = AccountFormat.VERSION,
    val accounts: List<AccountRecord> = emptyList(),
    val currentId: String? = null,
)
