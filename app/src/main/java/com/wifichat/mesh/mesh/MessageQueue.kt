package com.wifichat.mesh.mesh

import com.wifichat.mesh.data.entity.QueueEntity
import com.wifichat.mesh.model.DeliveryState
import com.wifichat.mesh.model.EventType
import com.wifichat.mesh.model.MeshPacket
import com.wifichat.mesh.util.MeshLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Store-and-forward queue.
 *
 * Routing strategy for the first release is epidemic forwarding with a hop
 * limit and duplicate suppression: an outgoing packet is offered to every
 * directly connected neighbour, each of which does the same, until it reaches
 * the destination or exhausts its hop budget. This is simple, needs no routing
 * table, and is correct for small nearby meshes. It is explicitly NOT a
 * shortest-path protocol — see docs/KNOWN_LIMITATIONS.md.
 *
 * The queue survives restarts because entries are rows in `forward_queue`.
 * Retries use exponential backoff with jitter; an entry is removed only when
 * the destination acknowledges it, or it expires, or retries are exhausted.
 */
class MessageQueue(
    private val deps: MeshDeps,
    parentScope: CoroutineScope
) {

    private val scope = CoroutineScope(parentScope.coroutineContext + SupervisorJob() + Dispatchers.IO)
    private var loopJob: Job? = null

    /** Serialises and enqueues a packet for delivery to [targetPeerId]. */
    suspend fun enqueue(packet: MeshPacket, targetPeerId: String): Boolean {
        val blob = PacketCodec.encode(packet)
        val now = System.currentTimeMillis()
        val item = QueueEntity(
            messageId = packet.messageId,
            packetBlob = blob,
            targetPeerId = targetPeerId,
            nextAttemptAt = now,
            attempts = 0,
            createdAt = now,
            expiresAt = packet.expiresAt
        )
        val stored = deps.queue.enqueue(item)
        if (stored) {
            deps.messages.updateStatus(packet.messageId, DeliveryState.QUEUED)
            deps.events.log(EventType.MESSAGE_QUEUED, "queued ${packet.messageId.take(8)} for $targetPeerId")
        } else {
            deps.messages.updateStatus(packet.messageId, DeliveryState.FAILED)
            deps.events.log(EventType.ERROR, "queue full, dropped ${packet.messageId.take(8)}")
        }
        return stored
    }

    fun start() {
        if (loopJob?.isActive == true) return
        loopJob = scope.launch { loop() }
    }

    fun stop() {
        loopJob?.cancel()
        loopJob = null
    }

    private suspend fun loop() {
        while (scope.isActive) {
            try {
                val now = System.currentTimeMillis()
                deps.queue.pruneExpired(now)
                val due = deps.queue.due(now)
                for (item in due) attempt(item)
                deps.status.setPendingMessages(deps.queue.count())
            } catch (t: Throwable) {
                MeshLog.e("Queue loop error", t)
            }
            delay(TICK_MS)
        }
    }

    private suspend fun attempt(item: QueueEntity) {
        val now = System.currentTimeMillis()
        if (item.expiresAt in 1..now) {
            deps.queue.remove(item.messageId)
            deps.messages.updateStatus(item.messageId, DeliveryState.EXPIRED)
            deps.events.log(EventType.MESSAGE_EXPIRED, "expired ${item.messageId.take(8)}")
            return
        }

        val sent = try {
            deps.transport.sendToAll(item.packetBlob)
        } catch (t: Throwable) {
            MeshLog.w("attempt send failed: ${t.message}")
            0
        }

        if (sent > 0) {
            deps.messages.updateStatus(item.messageId, DeliveryState.TRANSMITTED)
        }

        val attempts = item.attempts + 1
        if (RetryPolicy.exhausted(attempts)) {
            deps.queue.remove(item.messageId)
            deps.messages.updateStatus(item.messageId, DeliveryState.FAILED)
            deps.status.incrementFailed()
            deps.events.log(EventType.ERROR, "retries exhausted ${item.messageId.take(8)}")
        } else {
            deps.queue.enqueue(item.copy(attempts = attempts, nextAttemptAt = now + backoff(attempts)))
        }
    }

    /** Called by the router when a delivery/read ack arrives. */
    suspend fun onAcknowledged(messageId: String, read: Boolean) {
        deps.queue.remove(messageId)
        deps.messages.updateStatus(
            messageId,
            if (read) DeliveryState.READ else DeliveryState.DELIVERED_TO_DEVICE
        )
        deps.status.setPendingMessages(deps.queue.count())
    }

    /** Exponential backoff with +/-20% jitter, capped. Delegates to RetryPolicy. */
    fun backoff(attempt: Int): Long = RetryPolicy.backoff(attempt)

    companion object {
        private const val TICK_MS = 4_000L
    }
}
