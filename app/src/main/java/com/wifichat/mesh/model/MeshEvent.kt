package com.wifichat.mesh.model

/** One entry in the user-visible mesh event log. Never contains plaintext. */
data class MeshEvent(
    val id: Long = 0L,
    val type: EventType,
    val timestamp: Long,
    val detail: String
)

enum class EventType {
    PEER_DISCOVERED,
    CONNECTION_ESTABLISHED,
    CONNECTION_LOST,
    MESSAGE_QUEUED,
    MESSAGE_RELAYED,
    ACK_RECEIVED,
    MESSAGE_EXPIRED,
    ERROR
}
