package com.wifichat.mesh.util

import java.security.SecureRandom
import java.util.UUID

/** Central source of random identifiers. Uses SecureRandom, not Math.random. */
object Ids {

    private val random = SecureRandom()

    /** A collision-resistant 128-bit message id, rendered as a UUID string. */
    fun newMessageId(): String = UUID.randomUUID().toString()

    /** A persistent, locally generated device/peer id. Not MAC-derived. */
    fun newPeerId(): String = UUID.randomUUID().toString()

    fun newConversationId(): String = UUID.randomUUID().toString()

    fun newGroupKeyId(): String = UUID.randomUUID().toString()

    fun randomBytes(n: Int): ByteArray {
        val b = ByteArray(n)
        random.nextBytes(b)
        return b
    }

    /** A short, human-readable device tag derived from a peerId (not the id). */
    fun shortTag(peerId: String): String =
        peerId.replace("-", "").takeLast(6).uppercase()
}
