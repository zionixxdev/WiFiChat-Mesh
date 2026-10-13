package com.wifichat.mesh.data

import com.wifichat.mesh.data.entity.ConversationEntity
import com.wifichat.mesh.data.entity.EventLogEntity
import com.wifichat.mesh.data.entity.MessageEntity
import com.wifichat.mesh.data.entity.PeerEntity
import com.wifichat.mesh.model.Conversation
import com.wifichat.mesh.model.DeliveryState
import com.wifichat.mesh.model.EventType
import com.wifichat.mesh.model.Message
import com.wifichat.mesh.model.MeshEvent
import com.wifichat.mesh.model.Peer

/** Entity <-> domain-model mapping. Kept in one place so it stays consistent. */

fun MessageEntity.toModel(): Message = Message(
    messageId = messageId,
    conversationId = conversationId,
    senderId = senderId,
    recipientId = recipientId,
    body = body,
    createdAt = createdAt,
    receivedAt = receivedAt,
    status = runCatching { DeliveryState.valueOf(status) }.getOrDefault(DeliveryState.PENDING),
    outgoing = outgoing,
    hopCount = hopCount,
    expiresAt = expiresAt,
    replyToId = replyToId
)

fun Message.toEntity(): MessageEntity = MessageEntity(
    messageId = messageId,
    conversationId = conversationId,
    senderId = senderId,
    recipientId = recipientId,
    body = body,
    createdAt = createdAt,
    receivedAt = receivedAt,
    status = status.name,
    outgoing = outgoing,
    hopCount = hopCount,
    expiresAt = expiresAt,
    replyToId = replyToId
)

fun ConversationEntity.toModel(): Conversation = Conversation(
    conversationId = conversationId,
    title = title,
    peerId = peerId,
    isGroup = isGroup,
    groupKeyId = groupKeyId,
    lastMessagePreview = lastMessagePreview,
    lastMessageAt = lastMessageAt,
    unreadCount = unreadCount,
    pinned = pinned
)

fun Conversation.toEntity(): ConversationEntity = ConversationEntity(
    conversationId = conversationId,
    title = title,
    peerId = peerId,
    isGroup = isGroup,
    groupKeyId = groupKeyId,
    lastMessagePreview = lastMessagePreview,
    lastMessageAt = lastMessageAt,
    unreadCount = unreadCount,
    pinned = pinned
)

fun PeerEntity.toModel(): Peer = Peer(
    peerId = peerId,
    displayName = displayName,
    publicKey = publicKey,
    lastSeen = lastSeen,
    connected = false,   // liveness comes from ConnectionManager, not the DB
    hopCount = hopCount
)

fun EventLogEntity.toModel(): MeshEvent = MeshEvent(
    id = id,
    type = runCatching { EventType.valueOf(type) }.getOrDefault(EventType.ERROR),
    timestamp = timestamp,
    detail = detail
)
