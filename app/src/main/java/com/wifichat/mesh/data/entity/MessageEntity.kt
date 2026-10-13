package com.wifichat.mesh.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "messages",
    indices = [Index("conversationId"), Index("createdAt")]
)
data class MessageEntity(
    @PrimaryKey val messageId: String,
    val conversationId: String,
    val senderId: String,
    val recipientId: String,
    val body: String,
    val createdAt: Long,
    val receivedAt: Long?,
    val status: String,        // DeliveryState.name
    val outgoing: Boolean,
    val hopCount: Int,
    val expiresAt: Long,
    val replyToId: String?
)
