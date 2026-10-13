package com.wifichat.mesh.mesh

import com.google.common.truth.Truth.assertThat
import com.wifichat.mesh.model.MeshPacket
import com.wifichat.mesh.model.PacketType
import com.wifichat.mesh.util.Constants
import org.junit.Test

class MeshValidatorTest {

    private fun packet(hop: Int, max: Int, expiresAt: Long) = MeshPacket(
        protocolVersion = 1,
        type = PacketType.DATA,
        messageId = "m",
        senderId = "s",
        recipientId = "r",
        createdAt = 0,
        expiresAt = expiresAt,
        hopCount = hop,
        maxHops = max,
        nonce = ByteArray(12),
        payload = ByteArray(0),
        signature = ByteArray(0)
    )

    @Test
    fun expiredWhenPastExpiry() {
        assertThat(MeshValidator.isExpired(packet(0, 5, 1000), now = 2000)).isTrue()
    }

    @Test
    fun notExpiredBeforeExpiry() {
        assertThat(MeshValidator.isExpired(packet(0, 5, 5000), now = 2000)).isFalse()
    }

    @Test
    fun zeroExpiryNeverExpires() {
        assertThat(MeshValidator.isExpired(packet(0, 5, 0), now = Long.MAX_VALUE / 2)).isFalse()
    }

    @Test
    fun forwardableUnderHopLimit() {
        assertThat(MeshValidator.isForwardable(packet(hop = 3, max = 5, expiresAt = 0))).isTrue()
    }

    @Test
    fun notForwardableAtHopLimit() {
        // Prevents endless loops: at hopCount == maxHops the packet stops.
        assertThat(MeshValidator.isForwardable(packet(hop = 5, max = 5, expiresAt = 0))).isFalse()
    }

    @Test
    fun clampHopsEnforcesBounds() {
        assertThat(MeshValidator.clampHops(0)).isEqualTo(1)
        assertThat(MeshValidator.clampHops(4)).isEqualTo(4)
        assertThat(MeshValidator.clampHops(999)).isEqualTo(Constants.MAX_HOP_LIMIT)
    }
}
