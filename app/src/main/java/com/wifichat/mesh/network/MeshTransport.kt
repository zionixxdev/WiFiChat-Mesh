package com.wifichat.mesh.network

import com.wifichat.mesh.model.NetworkStatus
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * A live RFCOMM link to a neighbour. [address] is the Bluetooth MAC used to
 * route at the transport level; [peerId] is filled in once the neighbour's
 * ANNOUNCE packet resolves the MAC to a stable mesh identity.
 */
data class PeerLink(
    val address: String,
    val peerId: String?,
    val connectedAt: Long,
    val outgoing: Boolean
)

/** A raw frame plus the transport address it arrived from. */
class InboundFrame(
    val address: String,
    val bytes: ByteArray
)

/**
 * The transport abstraction. The rest of the app talks to the mesh only through
 * this interface, so the underlying radio (Bluetooth Classic today, Wi-Fi Direct
 * later) can be swapped without touching routing, storage or UI.
 *
 * Implementations must be safe to call from a background coroutine; none of
 * these methods block the caller.
 */
interface MeshTransport {

    /** Currently live links. */
    val links: StateFlow<List<PeerLink>>

    /** Raw frames arriving from neighbours. */
    val inbound: SharedFlow<InboundFrame>

    /** Aggregate counters for the Mesh dashboard. */
    val status: StateFlow<NetworkStatus>

    fun start()

    fun stop()

    /** Attempts an outgoing RFCOMM connection to [address]. */
    fun connect(address: String)

    fun disconnect(address: String)

    /** Sends a frame to one neighbour. Returns false if not connected. */
    fun sendTo(address: String, frame: ByteArray): Boolean

    /** Sends a frame to every live neighbour; returns how many it reached. */
    fun sendToAll(frame: ByteArray): Int

    fun connectedAddresses(): List<String>

    /** Associates a MAC address with a resolved mesh peer id after ANNOUNCE. */
    fun resolvePeerId(address: String, peerId: String)
}
