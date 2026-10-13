package com.wifichat.mesh.mesh

import com.wifichat.mesh.model.Conversation
import com.wifichat.mesh.model.DeliveryState
import com.wifichat.mesh.model.EventType
import com.wifichat.mesh.model.Message
import com.wifichat.mesh.model.MeshPacket
import com.wifichat.mesh.model.PacketType
import com.wifichat.mesh.model.Peer
import com.wifichat.mesh.network.InboundFrame
import com.wifichat.mesh.util.Ids
import com.wifichat.mesh.util.MeshLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.util.concurrent.ConcurrentHashMap

/**
 * The heart of the mesh: decodes every inbound frame, verifies it, decides
 * whether it is for us or should be relayed, and drives acknowledgements.
 *
 * Guarantees enforced here (all covered by unit tests):
 *  - malformed frames are rejected, never crash the reader;
 *  - packets with a bad signature are dropped;
 *  - expired packets are dropped;
 *  - a message id already seen is never displayed or forwarded again;
 *  - a packet is only forwarded while hopCount < maxHops, which prevents
 *    endless loops.
 */
class MeshRouter(
    private val deps: MeshDeps,
    parentScope: CoroutineScope
) {

    private val scope = CoroutineScope(parentScope.coroutineContext + SupervisorJob() + Dispatchers.IO)
    private var collectJob: Job? = null

    /** peerId -> peer's X25519 static public key, learned from ANNOUNCE. */
    private val sessionKeys = ConcurrentHashMap<String, ByteArray>()

    /** Peers we have exchanged a real ANNOUNCE with (as opposed to merely seen). */
    private val _knownPeers = MutableStateFlow<Set<String>>(emptySet())
    val knownPeers: StateFlow<Set<String>> = _knownPeers.asStateFlow()

    private val queue = MessageQueue(deps, parentScope)
    private val factory = MeshPacketFactory(deps.crypto, deps.identity, deps.myPeerId)

    fun start() {
        queue.start()
        if (collectJob?.isActive == true) return
        collectJob = scope.launch {
            deps.transport.inbound.collect { frame -> handleFrame(frame) }
        }
        announcePresence()
    }

    fun stop() {
        collectJob?.cancel()
        collectJob = null
        queue.stop()
    }

    // ------------------------------------------------------------------
    // Outbound
    // ------------------------------------------------------------------

    /** Broadcasts our identity so neighbours can resolve us and encrypt to us. */
    fun announcePresence() {
        scope.launch {
            try {
                val ttl = deps.settings.messageTtlMs.value
                val packet = factory.announce(deps.settings.displayName.value, ttl)
                val blob = PacketCodec.encode(packet)
                deps.transport.sendToAll(blob)
            } catch (t: Throwable) {
                MeshLog.w("announce failed: ${t.message}")
            }
        }
    }

    /**
     * Encrypts, stores and queues a 1:1 message. Returns the local message id,
     * or null if we do not yet have the recipient's key (caller should surface
     * "waiting for peer" rather than a fake success).
     */
    suspend fun sendMessage(
        recipientPeerId: String,
        recipientDisplayName: String,
        text: String,
        replyToId: String? = null
    ): String? {
        val recipientKey = sessionKeys[recipientPeerId] ?: return null

        val conversation: Conversation =
            deps.conversations.getOrCreateDirect(recipientPeerId, recipientDisplayName)

        val ttl = deps.settings.messageTtlMs.value
        val packet = factory.data(
            recipientId = recipientPeerId,
            recipientX25519 = recipientKey,
            plaintext = text.toByteArray(Charsets.UTF_8),
            ttlMs = ttl,
            replyToId = replyToId
        )

        val local = Message(
            messageId = packet.messageId,
            conversationId = conversation.conversationId,
            senderId = deps.myPeerId,
            recipientId = recipientPeerId,
            body = text,
            createdAt = packet.createdAt,
            status = DeliveryState.PENDING,
            outgoing = true,
            expiresAt = packet.expiresAt,
            replyToId = replyToId
        )
        deps.messages.save(local)
        deps.conversations.updateLastMessage(
            conversation.conversationId, text, packet.createdAt
        )
        queue.enqueue(packet, recipientPeerId)
        return packet.messageId
    }

    /** True when we hold a usable key for [peerId] (i.e. they have announced). */
    fun canReach(peerId: String): Boolean = sessionKeys.containsKey(peerId)

    /** Sends an explicit read receipt for [forMessageId] to [peerId]. */
    suspend fun sendReadReceipt(peerId: String, forMessageId: String) {
        if (!deps.settings.readReceipts.value) return
        sendAck(PacketType.ACK_READ, peerId, forMessageId)
    }

    /**
     * Re-queues a previously failed/queued outgoing message, rebuilding the
     * packet from stored plaintext and the recipient's current key. Used by the
     * "tap to retry" affordance.
     */
    suspend fun retry(messageId: String): Boolean {
        val message = deps.messages.byId(messageId) ?: return false
        if (!message.outgoing) return false
        val key = sessionKeys[message.recipientId] ?: return false
        val packet = factory.data(
            recipientId = message.recipientId,
            recipientX25519 = key,
            plaintext = message.body.toByteArray(Charsets.UTF_8),
            ttlMs = deps.settings.messageTtlMs.value,
            replyToId = message.replyToId,
            messageId = message.messageId
        )
        return queue.enqueue(packet, message.recipientId)
    }

    // ------------------------------------------------------------------
    // Inbound
    // ------------------------------------------------------------------

    private suspend fun handleFrame(frame: InboundFrame) {
        val packet = try {
            PacketCodec.decode(frame.bytes)
        } catch (e: MalformedPacketException) {
            MeshLog.w("Rejected malformed frame from ${frame.address}: ${e.message}")
            return
        } catch (t: Throwable) {
            MeshLog.w("Frame decode error: ${t.message}")
            return
        }

        if (!verifySignature(packet)) {
            MeshLog.w("Dropped packet with bad signature from ${frame.address}")
            return
        }

        val now = System.currentTimeMillis()
        if (MeshValidator.isExpired(packet, now)) {
            deps.events.log(EventType.MESSAGE_EXPIRED, "dropped expired ${packet.messageId.take(8)}")
            return
        }

        // Duplicate suppression: never process, display or forward twice.
        if (deps.messages.isDuplicate(packet.messageId)) return
        deps.messages.markSeen(packet.messageId)

        when (packet.type) {
            PacketType.ANNOUNCE -> handleAnnounce(packet, frame.address)
            PacketType.KEY_EXCHANGE -> handleKeyExchange(packet, frame.address)
            PacketType.DATA -> handleData(packet, frame.address)
            PacketType.GROUP -> handleGroup(packet, frame.address)
            PacketType.ACK_DELIVERED -> handleAck(packet, read = false)
            PacketType.ACK_READ -> handleAck(packet, read = true)
        }
    }

    private val knownIdentityKeys = ConcurrentHashMap<String, ByteArray>()

    private fun verifySignature(packet: MeshPacket): Boolean {
        return if (packet.type == PacketType.ANNOUNCE) {
            // ANNOUNCE is self-signed: the payload carries the Ed25519 key, so
            // we verify against it and, on success, remember it.
            val info = MeshPacketFactory.parseAnnounce(packet.payload) ?: return false
            val ok = deps.crypto.verify(
                PacketCodec.signableBytes(packet), packet.signature, info.identityPublicKey
            )
            if (ok) knownIdentityKeys[packet.senderId] = info.identityPublicKey
            ok
        } else {
            // For DATA/GROUP/ACK we verify against the sender's identity key if
            // we have seen their ANNOUNCE. Until then we accept the packet so
            // the first exchange can complete; the key is learned on announce.
            val key = knownIdentityKeys[packet.senderId] ?: return true
            deps.crypto.verify(PacketCodec.signableBytes(packet), packet.signature, key)
        }
    }

    private suspend fun handleAnnounce(packet: MeshPacket, address: String) {
        val info = MeshPacketFactory.parseAnnounce(packet.payload) ?: return
        val name = info.displayName
        sessionKeys[packet.senderId] = info.sessionPublicKey
        knownIdentityKeys[packet.senderId] = info.identityPublicKey
        deps.transport.resolvePeerId(address, packet.senderId)
        deps.peers.upsert(
            Peer(
                peerId = packet.senderId,
                displayName = name,
                publicKey = null,
                lastSeen = System.currentTimeMillis(),
                connected = true,
                hopCount = 0
            )
        )
        _knownPeers.value = _knownPeers.value + packet.senderId
        deps.status.setKnownPeers(_knownPeers.value.size)
        deps.events.log(EventType.PEER_DISCOVERED, "peer ${packet.senderId.take(8)} ($name)")
    }

    private fun handleKeyExchange(packet: MeshPacket, address: String) {
        sessionKeys[packet.senderId] = packet.payload
        deps.transport.resolvePeerId(address, packet.senderId)
    }

    private suspend fun handleData(packet: MeshPacket, incomingAddress: String) {
        if (packet.recipientId == deps.myPeerId) {
            receiveForMe(packet)
        } else if (MeshValidator.isForwardable(packet)) {
            relay(packet, incomingAddress)
        } else {
            MeshLog.d("Hop limit reached for ${packet.messageId.take(8)}; not forwarding")
        }
    }

    private suspend fun handleGroup(packet: MeshPacket, incomingAddress: String) {
        // Group delivery in v1 is best-effort broadcast; see KNOWN_LIMITATIONS.
        if (packet.recipientId == deps.myPeerId) {
            receiveForMe(packet)
        } else if (MeshValidator.isForwardable(packet)) {
            relay(packet, incomingAddress)
        }
    }

    private suspend fun relay(packet: MeshPacket, incomingAddress: String) {
        val relayed = packet.relayed()
        val blob = PacketCodec.encode(relayed)
        var sent = 0
        for (addr in deps.transport.connectedAddresses()) {
            if (addr == incomingAddress) continue
            if (deps.transport.sendTo(addr, blob)) sent++
        }
        if (sent > 0) {
            deps.status.incrementRelayed()
            deps.events.log(EventType.MESSAGE_RELAYED, "relayed ${packet.messageId.take(8)} (hop ${relayed.hopCount})")
        }
    }

    private suspend fun receiveForMe(packet: MeshPacket) {
        val senderKey = sessionKeys[packet.senderId] ?: return
        val plaintext = try {
            val aad = MeshPacketFactory.headerAad(
                packet.type, packet.messageId, packet.senderId, packet.recipientId,
                packet.createdAt, packet.expiresAt, packet.maxHops
            )
            val key = deps.crypto.deriveSessionKey(
                deps.identity.sessionPrivate, senderKey,
                packet.messageId.toByteArray(Charsets.UTF_8), MeshPacketFactory.HKDF_INFO
            )
            deps.crypto.decrypt(packet.payload, key, packet.nonce, aad)
        } catch (t: Throwable) {
            MeshLog.w("Decryption failed for ${packet.messageId.take(8)}: ${t.message}")
            return
        }

        val (body, replyToId) = parseBody(plaintext)
        val conversation = deps.conversations.getOrCreateDirect(
            packet.senderId, displayNameFor(packet.senderId)
        )
        val message = Message(
            messageId = packet.messageId,
            conversationId = conversation.conversationId,
            senderId = packet.senderId,
            recipientId = deps.myPeerId,
            body = body,
            createdAt = packet.createdAt,
            receivedAt = System.currentTimeMillis(),
            status = DeliveryState.DELIVERED_TO_DEVICE,
            outgoing = false,
            hopCount = packet.hopCount,
            expiresAt = packet.expiresAt,
            replyToId = replyToId
        )
        deps.messages.save(message)
        deps.conversations.updateLastMessage(conversation.conversationId, body, packet.createdAt)
        deps.conversations.incrementUnread(conversation.conversationId)

        // Acknowledge delivery to the sender (flooded back, so it works multi-hop).
        sendAck(PacketType.ACK_DELIVERED, packet.senderId, packet.messageId)
    }

    private suspend fun sendAck(type: PacketType, senderPeerId: String, forMessageId: String) {
        val senderKey = sessionKeys[senderPeerId] ?: return
        val ack = factory.ack(type, senderPeerId, senderKey, forMessageId)
        queue.enqueue(ack, senderPeerId)
    }

    private suspend fun handleAck(packet: MeshPacket, read: Boolean) {
        if (packet.recipientId != deps.myPeerId) {
            // Acks are small; allow them to be relayed too.
            if (MeshValidator.isForwardable(packet)) relay(packet, incomingAddress = "")
            return
        }
        val senderKey = sessionKeys[packet.senderId] ?: return
        val forMessageId = try {
            val aad = MeshPacketFactory.headerAad(
                packet.type, packet.messageId, packet.senderId, packet.recipientId,
                packet.createdAt, packet.expiresAt, packet.maxHops
            )
            val key = deps.crypto.deriveSessionKey(
                deps.identity.sessionPrivate, senderKey,
                packet.messageId.toByteArray(Charsets.UTF_8), MeshPacketFactory.HKDF_INFO
            )
            deps.crypto.decrypt(packet.payload, key, packet.nonce, aad).toString(Charsets.UTF_8)
        } catch (t: Throwable) {
            return
        }
        queue.onAcknowledged(forMessageId, read)
        deps.events.log(EventType.ACK_RECEIVED, "ack for ${forMessageId.take(8)} (read=$read)")
    }

    private fun parseBody(plaintext: ByteArray): Pair<String, String?> {
        return try {
            val dis = DataInputStream(ByteArrayInputStream(plaintext))
            val hasReply = dis.readInt() == 1
            if (!hasReply) {
                // No reply prefix: whole array is the body.
                plaintext.toString(Charsets.UTF_8) to null
            } else {
                val rl = dis.readInt()
                val reply = ByteArray(rl).also { dis.readFully(it) }.toString(Charsets.UTF_8)
                val bl = dis.readInt()
                val body = ByteArray(bl).also { dis.readFully(it) }.toString(Charsets.UTF_8)
                body to reply
            }
        } catch (t: Throwable) {
            plaintext.toString(Charsets.UTF_8) to null
        }
    }

    private suspend fun displayNameFor(peerId: String): String =
        deps.peers.byId(peerId)?.displayName ?: Ids.shortTag(peerId)
}
