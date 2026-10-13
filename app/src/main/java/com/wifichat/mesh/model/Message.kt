package com.wifichat.mesh.model

/** A message as presented to the UI. Payload is always plaintext here. */
data class Message(
    val messageId: String,
    val conversationId: String,
    val senderId: String,
    val recipientId: String,
    val body: String,
    val createdAt: Long,
    val receivedAt: Long? = null,
    val status: DeliveryState = DeliveryState.PENDING,
    val outgoing: Boolean = false,
    val hopCount: Int = 0,
    val expiresAt: Long = 0L,
    val replyToId: String? = null
)
