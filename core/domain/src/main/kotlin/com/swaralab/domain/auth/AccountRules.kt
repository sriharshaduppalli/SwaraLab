package com.swaralab.domain.auth

object AccountRules {
    const val MIN_PASSWORD_LENGTH = 8

    private val email = Regex("^[a-z0-9._%+-]+@[a-z0-9.-]+\\.[a-z]{2,}$")

    fun email(raw: String): String? {
        val normalized = raw.trim().lowercase()
        if (!email.matches(normalized)) return null
        return normalized
    }

    fun displayName(raw: String): String? {
        val name = raw.trim().replace(Regex("\\s+"), " ")
        if (name.isEmpty() || name.length > 40) return null
        return name
    }

    fun password(raw: String): Boolean = raw.length >= MIN_PASSWORD_LENGTH
}
