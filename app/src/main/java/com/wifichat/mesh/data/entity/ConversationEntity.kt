package com.wifichat.mesh.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey val conversationId: String,
    val title: String,
    val peerId: String?,
    val isGroup: Boolean,
    val groupKeyId: String?,
    val lastMessagePreview: String?,
    val lastMessageAt: Long,
    val unreadCount: Int,
    val pinned: Boolean
)
