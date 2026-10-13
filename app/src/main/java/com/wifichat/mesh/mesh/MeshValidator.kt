package com.wifichat.mesh.mesh

import com.wifichat.mesh.model.MeshPacket
import com.wifichat.mesh.util.Constants

/**
 * Pure routing rules, extracted so they can be unit-tested without a device,
 * a database or a radio. MeshRouter and MessageQueue both delegate here, which
 * keeps the rules in one place.
 */
object MeshValidator {

    /**
     * A packet is expired once [MeshPacket.expiresAt] has passed. A value of 0
     * means "no expiry" and is never expired.
     */
    fun isExpired(packet: MeshPacket, now: Long): Boolean =
        packet.expiresAt in 1..now

    /**
     * A packet may be forwarded only while it is under its hop budget. This is
     * the guard that prevents endless forwarding loops in the mesh.
     */
    fun isForwardable(packet: MeshPacket): Boolean =
        packet.hopCount < packet.maxHops

    /** Clamps a configured hop limit into the allowed range. */
    fun clampHops(requested: Int): Int =
        requested.coerceIn(1, Constants.MAX_HOP_LIMIT)
}
