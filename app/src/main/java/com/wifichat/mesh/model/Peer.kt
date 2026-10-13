package com.wifichat.mesh.model

/**
 * A remote device we know about. [hopCount] is 0 for a directly connected
 * neighbour and > 0 for a peer learned about through the mesh.
 */
data class Peer(
    val peerId: String,
    val displayName: String,
    val publicKey: ByteArray?,
    val lastSeen: Long,
    val connected: Boolean,
    val hopCount: Int = 0,
    val rssi: Int? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Peer) return false
        return peerId == other.peerId &&
            displayName == other.displayName &&
            lastSeen == other.lastSeen &&
            connected == other.connected &&
            hopCount == other.hopCount
    }

    override fun hashCode(): Int {
        var result = peerId.hashCode()
        result = 31 * result + displayName.hashCode()
        result = 31 * result + lastSeen.hashCode()
        result = 31 * result + connected.hashCode()
        result = 31 * result + hopCount
        return result
    }
}
