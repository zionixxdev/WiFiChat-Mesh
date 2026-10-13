package com.wifichat.mesh.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "peers")
data class PeerEntity(
    @PrimaryKey val peerId: String,
    val displayName: String,
    val publicKey: ByteArray?,
    val lastSeen: Long,
    val hopCount: Int
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PeerEntity) return false
        return peerId == other.peerId && displayName == other.displayName &&
            lastSeen == other.lastSeen && hopCount == other.hopCount
    }

    override fun hashCode(): Int {
        var r = peerId.hashCode()
        r = 31 * r + displayName.hashCode()
        r = 31 * r + lastSeen.hashCode()
        r = 31 * r + hopCount
        return r
    }
}
