package com.wifichat.mesh.repository

import com.wifichat.mesh.data.dao.ConversationDao
import com.wifichat.mesh.data.dao.MessageDao
import com.wifichat.mesh.data.toEntity
import com.wifichat.mesh.data.toModel
import com.wifichat.mesh.model.Conversation
import com.wifichat.mesh.util.Ids
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ConversationRepository(
    private val conversationDao: ConversationDao,
    private val messageDao: MessageDao
) {

    fun observeAll(): Flow<List<Conversation>> =
        conversationDao.observeAll().map { list -> list.map { it.toModel() } }

    suspend fun byId(id: String): Conversation? = conversationDao.byId(id)?.toModel()

    suspend fun byPeer(peerId: String): Conversation? =
        conversationDao.byPeer(peerId)?.toModel()

    /** Returns the existing 1:1 conversation with [peerId], creating one if needed. */
    suspend fun getOrCreateDirect(peerId: String, title: String): Conversation {
        val existing = byPeer(peerId)
        if (existing != null) return existing
        val created = Conversation(
            conversationId = Ids.newConversationId(),
            title = title,
            peerId = peerId,
            isGroup = false
        )
        conversationDao.upsert(created.toEntity())
        return created
    }

    suspend fun updateLastMessage(conversationId: String, preview: String, at: Long) =
        conversationDao.updateLastMessage(conversationId, preview, at)

    suspend fun incrementUnread(conversationId: String) =
        conversationDao.incrementUnread(conversationId)

    suspend fun clearUnread(conversationId: String) =
        conversationDao.clearUnread(conversationId)

    suspend fun setPinned(conversationId: String, pinned: Boolean) =
        conversationDao.setPinned(conversationId, pinned)

    suspend fun deleteConversation(conversationId: String) {
        messageDao.deleteConversation(conversationId)
    }

    suspend fun deleteAll() = conversationDao.deleteAll()
}
