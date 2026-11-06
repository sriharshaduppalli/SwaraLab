package com.swaralab.domain.port

/** Name and booth activity cursor on this phone. Not a social profile. */
/** Last song opened on this phone. Not a cloud project pointer. */
interface OpenSessionPointer {
    fun remember(sessionId: String, playheadMs: Long)

    fun sessionId(): String?

    fun playheadMs(): Long
}

interface BoothProfile {
    fun displayName(): String

    fun bio(): String

    fun setProfile(name: String, bio: String)

    fun activitySeenAt(): Long

    fun markActivitySeen()
}

data class BoothEvent(
    val id: String,
    val kind: String,
    val body: String,
    val sessionId: String,
    val createdAt: Long,
)
