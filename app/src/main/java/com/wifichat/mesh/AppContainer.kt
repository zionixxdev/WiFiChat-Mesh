package com.wifichat.mesh

import android.content.Context
import com.wifichat.mesh.crypto.BouncyCastleCryptoManager
import com.wifichat.mesh.crypto.CryptoManager
import com.wifichat.mesh.crypto.IdentityKeyStore
import com.wifichat.mesh.crypto.StoredIdentity
import com.wifichat.mesh.data.db.AppDatabase
import com.wifichat.mesh.mesh.MeshDeps
import com.wifichat.mesh.mesh.MeshRouter
import com.wifichat.mesh.network.BluetoothClassicTransport
import com.wifichat.mesh.network.DiscoveryManager
import com.wifichat.mesh.network.MeshTransport
import com.wifichat.mesh.network.NetworkStatusManager
import com.wifichat.mesh.repository.ConversationRepository
import com.wifichat.mesh.repository.EventLogRepository
import com.wifichat.mesh.repository.MessageRepository
import com.wifichat.mesh.repository.PeerRepository
import com.wifichat.mesh.repository.QueueRepository
import com.wifichat.mesh.repository.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Hand-rolled dependency container. Deliberately not Hilt/Dagger: a low-RAM
 * target and a phone-only build both favour fewer dependencies and less build
 * machinery. Everything is a lazily-created singleton for the app lifetime.
 */
class AppContainer(context: Context) {

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val settings = SettingsStore(context)

    private val db: AppDatabase by lazy { AppDatabase.get(context) }

    val messages = MessageRepository(db.messageDao(), db.seenDao())
    val conversations = ConversationRepository(db.conversationDao(), db.messageDao())
    val peers = PeerRepository(db.peerDao())
    val queue = QueueRepository(db.queueDao())
    val events = EventLogRepository(db.eventLogDao())

    val crypto: CryptoManager = BouncyCastleCryptoManager()
    val keyStore = IdentityKeyStore(context, crypto)

    val status = NetworkStatusManager()

    /** Identity is created on first access; name may still be blank. */
    val identity: StoredIdentity by lazy {
        keyStore.ensureIdentity(settings.displayName.value)
    }

    val transport: MeshTransport by lazy {
        BluetoothClassicTransport(context, status, appScope)
    }

    val discovery: DiscoveryManager by lazy {
        DiscoveryManager(context, status, onDeviceFound = {})
    }

    val router: MeshRouter by lazy {
        MeshRouter(
            MeshDeps(
                transport = transport,
                messages = messages,
                conversations = conversations,
                peers = peers,
                queue = queue,
                events = events,
                crypto = crypto,
                identity = identity,
                myPeerId = settings.peerId,
                settings = settings,
                status = status
            ),
            appScope
        )
    }

    /** Wipes all local state. Used by Settings -> Clear local data. */
    suspend fun clearAllData() {
        messages.clearAllMessages()
        messages.clearAllSeen()
        conversations.deleteAll()
        peers.deleteAll()
        queue.clearAll()
        events.clear()
        keyStore.wipe()
        settings.clear()
    }
}
