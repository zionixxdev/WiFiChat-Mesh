package com.wifichat.mesh.mesh

import com.wifichat.mesh.crypto.CryptoManager
import com.wifichat.mesh.crypto.StoredIdentity
import com.wifichat.mesh.network.MeshTransport
import com.wifichat.mesh.network.NetworkStatusManager
import com.wifichat.mesh.repository.ConversationRepository
import com.wifichat.mesh.repository.EventLogRepository
import com.wifichat.mesh.repository.MessageRepository
import com.wifichat.mesh.repository.PeerRepository
import com.wifichat.mesh.repository.QueueRepository
import com.wifichat.mesh.repository.SettingsStore

/**
 * Everything the mesh layer needs, passed as one object so the router, queue
 * and packet factory do not each take eight constructor arguments.
 */
class MeshDeps(
    val transport: MeshTransport,
    val messages: MessageRepository,
    val conversations: ConversationRepository,
    val peers: PeerRepository,
    val queue: QueueRepository,
    val events: EventLogRepository,
    val crypto: CryptoManager,
    val identity: StoredIdentity,
    val myPeerId: String,
    val settings: SettingsStore,
    val status: NetworkStatusManager
)
