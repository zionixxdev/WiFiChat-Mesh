package com.wifichat.mesh.model

/**
 * A conversation is either a 1:1 chat ([isGroup] = false, [peerId] set) or a
 * group chat ([isGroup] = true, [groupKeyId] identifies the group key).
 */
data class Conversation(
    val conversationId: String,
    val title: String,
    val peerId: String?,
    val isGroup: Boolean = false,
    val groupKeyId: String? = null,
    val lastMessagePreview: String? = null,
    val lastMessageAt: Long = 0L,
    val unreadCount: Int = 0,
    val pinned: Boolean = false
)
