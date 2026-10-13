package com.wifichat.mesh.model

/**
 * Packet kinds carried by the mesh. DATA and GROUP carry a user message;
 * the ACK_* kinds carry delivery/read state; ANNOUNCE carries peer metadata.
 */
enum class PacketType(val code: Int) {
    ANNOUNCE(0x01),      // peer hello: display name, public key, protocol version
    DATA(0x02),          // 1:1 message
    GROUP(0x03),         // group message
    ACK_DELIVERED(0x04), // destination device -> sender: received
    ACK_READ(0x05),      // recipient -> sender: read
    KEY_EXCHANGE(0x06);  // X25519 public key material for session setup

    companion object {
        fun fromCode(code: Int): PacketType? = entries.firstOrNull { it.code == code }
    }
}
