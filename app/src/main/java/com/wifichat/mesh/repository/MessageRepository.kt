package com.wifichat.mesh.repository

import com.wifichat.mesh.data.dao.MessageDao
import com.wifichat.mesh.data.dao.SeenDao
import com.wifichat.mesh.data.entity.SeenMessageEntity
import com.wifichat.mesh.data.toEntity
import com.wifichat.mesh.data.toModel
import com.wifichat.mesh.model.DeliveryState
import com.wifichat.mesh.model.Message
import com.wifichat.mesh.util.Constants
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Owns message persistence and the duplicate-suppression ledger.
 *
 * Duplicate suppression is two-layered:
 *  1. a fast in-memory ring of recent ids (cheap for the hot RFCOMM read loop);
 *  2. the persistent `seen_ids` table, which survives restarts.
 * A packet is "new" only if BOTH say it is unseen.
 */
class MessageRepository(
    private val messageDao: MessageDao,
    private val seenDao: SeenDao
) {

    private val recentIds = ArrayDeque<String>()
    private val recentSet = HashSet<String>()

    fun observeConversation(conversationId: String): Flow<List<Message>> =
        messageDao.observeConversation(conversationId).map { list -> list.map { it.toModel() } }

    suspend fun save(message: Message) {
        messageDao.upsert(message.toEntity())
    }

    suspend fun byId(messageId: String): Message? = messageDao.byId(messageId)?.toModel()

    suspend fun updateStatus(messageId: String, status: DeliveryState) {
        messageDao.updateStatus(messageId, status.name)
    }

    suspend fun delete(messageId: String) = messageDao.delete(messageId)

    suspend fun clearConversation(conversationId: String) =
        messageDao.deleteConversation(conversationId)

    /** Removes messages whose expiry has passed and returns how many went. */
    suspend fun pruneExpired(now: Long = System.currentTimeMillis()): Int {
        val expired = messageDao.expired(now)
        messageDao.deleteExpired(now)
        return expired.size
    }

    // ---- duplicate suppression ----

    /** True if this message id has already been processed. */
    suspend fun isDuplicate(messageId: String): Boolean {
        if (recentSet.contains(messageId)) return true
        return seenDao.isSeen(messageId) > 0
    }

    /** Records an id as processed in both layers and prunes the ledger. */
    suspend fun markSeen(messageId: String) {
        recentSet.add(messageId)
        recentIds.addLast(messageId)
        while (recentIds.size > RECENT_WINDOW) {
            val evicted = recentIds.removeFirst()
            recentSet.remove(evicted)
        }
        seenDao.markSeen(SeenMessageEntity(messageId, System.currentTimeMillis()))
        if (recentIds.size % PRUNE_EVERY == 0) {
            seenDao.prune(Constants.MAX_SEEN_IDS)
        }
    }

    suspend fun clearAllSeen() = seenDao.clear()

    suspend fun clearAllMessages() = messageDao.deleteAll()

    companion object {
        private const val RECENT_WINDOW = 512
        private const val PRUNE_EVERY = 64
    }
}
