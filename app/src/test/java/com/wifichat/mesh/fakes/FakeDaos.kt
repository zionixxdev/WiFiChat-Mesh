package com.wifichat.mesh.fakes

import com.wifichat.mesh.data.dao.MessageDao
import com.wifichat.mesh.data.dao.QueueDao
import com.wifichat.mesh.data.dao.SeenDao
import com.wifichat.mesh.data.entity.MessageEntity
import com.wifichat.mesh.data.entity.QueueEntity
import com.wifichat.mesh.data.entity.SeenMessageEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * In-memory DAO doubles for JVM unit tests. They implement the same contracts
 * as the Room DAOs so repository logic (dedupe, queue bounds, expiry) can be
 * tested without an emulator.
 */

class FakeMessageDao : MessageDao {
    private val rows = LinkedHashMap<String, MessageEntity>()
    private val flow = MutableStateFlow<List<MessageEntity>>(emptyList())

    private fun emit() { flow.value = rows.values.sortedBy { it.createdAt } }

    override suspend fun upsert(message: MessageEntity) { rows[message.messageId] = message; emit() }

    override fun observeConversation(conversationId: String): Flow<List<MessageEntity>> =
        flow.map { list -> list.filter { it.conversationId == conversationId } }

    override suspend fun byId(messageId: String): MessageEntity? = rows[messageId]

    override suspend fun updateStatus(messageId: String, status: String) {
        rows[messageId]?.let { rows[messageId] = it.copy(status = status); emit() }
    }

    override suspend fun delete(messageId: String) { rows.remove(messageId); emit() }

    override suspend fun deleteConversation(conversationId: String) {
        rows.values.removeIf { it.conversationId == conversationId }; emit()
    }

    override suspend fun expired(now: Long): List<MessageEntity> =
        rows.values.filter { it.expiresAt in 1..now }

    override suspend fun deleteExpired(now: Long) {
        rows.values.removeIf { it.expiresAt in 1..now }; emit()
    }

    override suspend fun count(): Int = rows.size

    override suspend fun deleteAll() { rows.clear(); emit() }
}

class FakeSeenDao : SeenDao {
    private val rows = LinkedHashMap<String, SeenMessageEntity>()

    override suspend fun markSeen(row: SeenMessageEntity): Long {
        if (rows.containsKey(row.messageId)) return -1
        rows[row.messageId] = row
        return 1
    }

    override suspend fun isSeen(messageId: String): Int = if (rows.containsKey(messageId)) 1 else 0

    override suspend fun count(): Int = rows.size

    override suspend fun prune(keep: Int) {
        if (rows.size <= keep) return
        val sorted = rows.values.sortedByDescending { it.seenAt }
        val keepSet = sorted.take(keep).map { it.messageId }.toHashSet()
        rows.keys.retainAll(keepSet)
    }

    override suspend fun clear() = rows.clear()
}

class FakeQueueDao : QueueDao {
    private val rows = LinkedHashMap<String, QueueEntity>()

    override suspend fun upsert(item: QueueEntity) { rows[item.messageId] = item }

    override fun observeAll(): Flow<List<QueueEntity>> =
        MutableStateFlow(rows.values.sortedBy { it.nextAttemptAt })

    override suspend fun due(now: Long): List<QueueEntity> =
        rows.values.filter { it.nextAttemptAt <= now }.sortedBy { it.nextAttemptAt }

    override suspend fun byId(messageId: String): QueueEntity? = rows[messageId]

    override suspend fun delete(messageId: String) { rows.remove(messageId) }

    override suspend fun deleteExpired(now: Long) { rows.values.removeIf { it.expiresAt in 1..now } }

    override suspend fun count(): Int = rows.size

    override fun observeCount(): Flow<Int> = MutableStateFlow(rows.size)

    override suspend fun deleteAll() = rows.clear()
}
