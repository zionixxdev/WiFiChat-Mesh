package com.wifichat.mesh.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One row in the store-and-forward queue. [packetBlob] is the fully serialised,
 * signed MeshPacket — it survives process death and reboot so pending messages
 * can be forwarded when a route appears later.
 */
@Entity(tableName = "forward_queue")
data class QueueEntity(
    @PrimaryKey val messageId: String,
    val packetBlob: ByteArray,
    val targetPeerId: String,
    val nextAttemptAt: Long,
    val attempts: Int,
    val createdAt: Long,
    val expiresAt: Long
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is QueueEntity) return false
        return messageId == other.messageId && targetPeerId == other.targetPeerId &&
            nextAttemptAt == other.nextAttemptAt && attempts == other.attempts
    }

    override fun hashCode(): Int {
        var r = messageId.hashCode()
        r = 31 * r + targetPeerId.hashCode()
        r = 31 * r + nextAttemptAt.hashCode()
        r = 31 * r + attempts
        return r
    }
}
